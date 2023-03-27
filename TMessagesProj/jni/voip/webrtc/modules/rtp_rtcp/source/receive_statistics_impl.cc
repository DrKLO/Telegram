/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/receive_statistics_impl.h"

#include <cmath>
#include <cstdlib>
#include <memory>
#include <utility>
#include <vector>

#include "api/units/time_delta.h"
#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "modules/rtp_rtcp/source/rtcp_packet/report_block.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace {
constexpr int64_t kStatisticsTimeoutMs = 8000;
constexpr int64_t kStatisticsProcessIntervalMs = 1000;
}  // namespace

StreamStatistician::~StreamStatistician() {}

StreamStatisticianImpl::StreamStatisticianImpl(uint32_t ssrc, Clock* clock,
                                               int max_reordering_threshold)
    : ssrc_(ssrc),
      clock_(clock),
      delta_internal_unix_epoch_ms_(clock_->CurrentNtpInMilliseconds() -
                                    clock_->TimeInMilliseconds() -
                                    rtc::kNtpJan1970Millisecs),
      incoming_bitrate_(kStatisticsProcessIntervalMs,
                        RateStatistics::kBpsScale),
      max_reordering_threshold_(max_reordering_threshold),
      enable_retransmit_detection_(false),
      cumulative_loss_is_capped_(false),
      jitter_q4_(0),
      cumulative_loss_(0),
      cumulative_loss_rtcp_offset_(0),
      last_receive_time_ms_(0),
      last_received_timestamp_(0),
      received_seq_first_(-1),
      received_seq_max_(-1),
      last_report_cumulative_loss_(0),
      last_report_seq_max_(-1),
      last_payload_type_frequency_(0) {}

StreamStatisticianImpl::~StreamStatisticianImpl() = default;

bool StreamStatisticianImpl::UpdateOutOfOrder(const RtpPacketReceived& packet,
                                              int64_t sequence_number,
                                              int64_t now_ms) {
  // Check if `packet` is second packet of a stream restart.
  if (received_seq_out_of_order_) {
    // Count the previous packet as a received; it was postponed below.
    --cumulative_loss_;

    uint16_t expected_sequence_number = *received_seq_out_of_order_ + 1;
    received_seq_out_of_order_ = absl::nullopt;
    if (packet.SequenceNumber() == expected_sequence_number) {
      // Ignore sequence number gap caused by stream restart for packet loss
      // calculation, by setting received_seq_max_ to the sequence number just
      // before the out-of-order seqno. This gives a net zero change of
      // `cumulative_loss_`, for the two packets interpreted as a stream reset.
      //
      // Fraction loss for the next report may get a bit off, since we don't
      // update last_report_seq_max_ and last_report_cumulative_loss_ in a
      // consistent way.
      last_report_seq_max_ = sequence_number - 2;
      received_seq_max_ = sequence_number - 2;
      return false;
    }
  }

  if (std::abs(sequence_number - received_seq_max_) >
      max_reordering_threshold_) {
    // Sequence number gap looks too large, wait until next packet to check
    // for a stream restart.
    received_seq_out_of_order_ = packet.SequenceNumber();
    // Postpone counting this as a received packet until we know how to update
    // `received_seq_max_`, otherwise we temporarily decrement
    // `cumulative_loss_`. The
    // ReceiveStatisticsTest.StreamRestartDoesntCountAsLoss test expects
    // `cumulative_loss_` to be unchanged by the reception of the first packet
    // after stream reset.
    ++cumulative_loss_;
    return true;
  }

  if (sequence_number > received_seq_max_)
    return false;

  // Old out of order packet, may be retransmit.
  if (enable_retransmit_detection_ && IsRetransmitOfOldPacket(packet, now_ms))
    receive_counters_.retransmitted.AddPacket(packet);
  return true;
}

void StreamStatisticianImpl::UpdateCounters(const RtpPacketReceived& packet) {
  RTC_DCHECK_EQ(ssrc_, packet.Ssrc());
  int64_t now_ms = clock_->TimeInMilliseconds();

  incoming_bitrate_.Update(packet.size(), now_ms);
  receive_counters_.last_packet_received_timestamp_ms = now_ms;
  receive_counters_.transmitted.AddPacket(packet);
  --cumulative_loss_;

  int64_t sequence_number =
      seq_unwrapper_.UnwrapWithoutUpdate(packet.SequenceNumber());

  if (!ReceivedRtpPacket()) {
    received_seq_first_ = sequence_number;
    last_report_seq_max_ = sequence_number - 1;
    received_seq_max_ = sequence_number - 1;
    receive_counters_.first_packet_time_ms = now_ms;
  } else if (UpdateOutOfOrder(packet, sequence_number, now_ms)) {
    return;
  }
  // In order packet.
  cumulative_loss_ += sequence_number - received_seq_max_;
  received_seq_max_ = sequence_number;
  seq_unwrapper_.UpdateLast(sequence_number);

  // If new time stamp and more than one in-order packet received, calculate
  // new jitter statistics.
  if (packet.Timestamp() != last_received_timestamp_ &&
      (receive_counters_.transmitted.packets -
       receive_counters_.retransmitted.packets) > 1) {
    UpdateJitter(packet, now_ms);
  }
  last_received_timestamp_ = packet.Timestamp();
  last_receive_time_ms_ = now_ms;
}

void StreamStatisticianImpl::UpdateJitter(const RtpPacketReceived& packet,
                                          int64_t receive_time_ms) {
  int64_t receive_diff_ms = receive_time_ms - last_receive_time_ms_;
  RTC_DCHECK_GE(receive_diff_ms, 0);
  uint32_t receive_diff_rtp = static_cast<uint32_t>(
      (receive_diff_ms * packet.payload_type_frequency()) / 1000);
  int32_t time_diff_samples =
      receive_diff_rtp - (packet.Timestamp() - last_received_timestamp_);

  time_diff_samples = std::abs(time_diff_samples);

  ReviseFrequencyAndJitter(packet.payload_type_frequency());

  // lib_jingle sometimes deliver crazy jumps in TS for the same stream.
  // If this happens, don't update jitter value. Use 5 secs video frequency
  // as the threshold.
  if (time_diff_samples < 450000) {
    // Note we calculate in Q4 to avoid using float.
    int32_t jitter_diff_q4 = (time_diff_samples << 4) - jitter_q4_;
    jitter_q4_ += ((jitter_diff_q4 + 8) >> 4);
  }
}

void StreamStatisticianImpl::ReviseFrequencyAndJitter(
    int payload_type_frequency) {
  if (payload_type_frequency == last_payload_type_frequency_) {
    return;
  }

  if (payload_type_frequency != 0) {
    if (last_payload_type_frequency_ != 0) {
      // Value in "jitter_q4_" variable is a number of samples.
      // I.e. jitter = timestamp (ms) * frequency (kHz).
      // Since the frequency has changed we have to update the number of samples
      // accordingly. The new value should rely on a new frequency.

      // If we don't do such procedure we end up with the number of samples that
      // cannot be converted into milliseconds correctly
      // (i.e. jitter_ms = jitter_q4_ >> 4 / (payload_type_frequency / 1000)).
      // In such case, the number of samples has a "mix".

      // Doing so we pretend that everything prior and including the current
      // packet were computed on packet's frequency.
      jitter_q4_ = static_cast<int>(static_cast<uint64_t>(jitter_q4_) *
                                    payload_type_frequency /
                                    last_payload_type_frequency_);
    }
    // If last_payload_type_frequency_ is not present, the jitter_q4_
    // variable has its initial value.

    // Keep last_payload_type_frequency_ up to date and non-zero (set).
    last_payload_type_frequency_ = payload_type_frequency;
  }
}

void StreamStatisticianImpl::SetMaxReorderingThreshold(
    int max_reordering_threshold) {
  max_reordering_threshold_ = max_reordering_threshold;
}

void StreamStatisticianImpl::EnableRetransmitDetection(bool enable) {
  enable_retransmit_detection_ = enable;
}

RtpReceiveStats StreamStatisticianImpl::GetStats() const {
  RtpReceiveStats stats;
  stats.packets_lost = cumulative_loss_;
  // Note: internal jitter value is in Q4 and needs to be scaled by 1/16.
  stats.jitter = jitter_q4_ >> 4;
  if (last_payload_type_frequency_ > 0) {
    // Divide value in fractional seconds by frequency to get jitter in
    // fractional seconds.
    stats.interarrival_jitter =
        webrtc::TimeDelta::Seconds(stats.jitter) / last_payload_type_frequency_;
  }
  if (receive_counters_.last_packet_received_timestamp_ms.has_value()) {
    stats.last_packet_received_timestamp_ms =
        *receive_counters_.last_packet_received_timestamp_ms +
        delta_internal_unix_epoch_ms_;
  }
  stats.packet_counter = receive_counters_.transmitted;
  return stats;
}

void StreamStatisticianImpl::MaybeAppendReportBlockAndReset(
    std::vector<rtcp::ReportBlock>& report_blocks) {
  int64_t now_ms = clock_->TimeInMilliseconds();
  if (now_ms - last_receive_time_ms_ >= kStatisticsTimeoutMs) {
    // Not active.
    return;
  }
  if (!ReceivedRtpPacket()) {
    return;
  }

  report_blocks.emplace_back();
  rtcp::ReportBlock& stats = report_blocks.back();
  stats.SetMediaSsrc(ssrc_);
  // Calculate fraction lost.
  int64_t exp_since_last = received_seq_max_ - last_report_seq_max_;
  RTC_DCHECK_GE(exp_since_last, 0);

  int32_t lost_since_last = cumulative_loss_ - last_report_cumulative_loss_;
  if (exp_since_last > 0 && lost_since_last > 0) {
    // Scale 0 to 255, where 255 is 100% loss.
    stats.SetFractionLost(255 * lost_since_last / exp_since_last);
  }

  int packets_lost = cumulative_loss_ + cumulative_loss_rtcp_offset_;
  if (packets_lost < 0) {
    // Clamp to zero. Work around to accomodate for senders that misbehave with
    // negative cumulative loss.
    packets_lost = 0;
    cumulative_loss_rtcp_offset_ = -cumulative_loss_;
  }
  if (packets_lost > 0x7fffff) {
    // Packets lost is a 24 bit signed field, and thus should be clamped, as
    // described in https://datatracker.ietf.org/doc/html/rfc3550#appendix-A.3
    if (!cumulative_loss_is_capped_) {
      cumulative_loss_is_capped_ = true;
      RTC_LOG(LS_WARNING) << "Cumulative loss reached maximum value for ssrc "
                          << ssrc_;
    }
    packets_lost = 0x7fffff;
  }
  stats.SetCumulativeLost(packets_lost);
  stats.SetExtHighestSeqNum(received_seq_max_);
  // Note: internal jitter value is in Q4 and needs to be scaled by 1/16.
  stats.SetJitter(jitter_q4_ >> 4);

  // Only for report blocks in RTCP SR and RR.
  last_report_cumulative_loss_ = cumulative_loss_;
  last_report_seq_max_ = received_seq_max_;
  BWE_TEST_LOGGING_PLOT_WITH_SSRC(1, "cumulative_loss_pkts", now_ms,
                                  cumulative_loss_, ssrc_);
  BWE_TEST_LOGGING_PLOT_WITH_SSRC(1, "received_seq_max_pkts", now_ms,
                                  (received_seq_max_ - received_seq_first_),
                                  ssrc_);
}

absl::optional<int> StreamStatisticianImpl::GetFractionLostInPercent() const {
  if (!ReceivedRtpPacket()) {
    return absl::nullopt;
  }
  int64_t expected_packets = 1 + received_seq_max_ - received_seq_first_;
  if (expected_packets <= 0) {
    return absl::nullopt;
  }
  if (cumulative_loss_ <= 0) {
    return 0;
  }
  return 100 * static_cast<int64_t>(cumulative_loss_) / expected_packets;
}

StreamDataCounters StreamStatisticianImpl::GetReceiveStreamDataCounters()
    const {
  return receive_counters_;
}

uint32_t StreamStatisticianImpl::BitrateReceived() const {
  return incoming_bitrate_.Rate(clock_->TimeInMilliseconds()).value_or(0);
}

bool StreamStatisticianImpl::IsRetransmitOfOldPacket(
    const RtpPacketReceived& packet,
    int64_t now_ms) const {
  uint32_t frequency_khz = packet.payload_type_frequency() / 1000;
  RTC_DCHECK_GT(frequency_khz, 0);

  int64_t time_diff_ms = now_ms - last_receive_time_ms_;

  // Diff in time stamp since last received in order.
  uint32_t timestamp_diff = packet.Timestamp() - last_received_timestamp_;
  uint32_t rtp_time_stamp_diff_ms = timestamp_diff / frequency_khz;

  int64_t max_delay_ms = 0;

  // Jitter standard deviation in samples.
  float jitter_std = std::sqrt(static_cast<float>(jitter_q4_ >> 4));

  // 2 times the standard deviation => 95% confidence.
  // And transform to milliseconds by dividing by the frequency in kHz.
  max_delay_ms = static_cast<int64_t>((2 * jitter_std) / frequency_khz);

  // Min max_delay_ms is 1.
  if (max_delay_ms == 0) {
    max_delay_ms = 1;
  }
  return time_diff_ms > rtp_time_stamp_diff_ms + max_delay_ms;
}

std::unique_ptr<ReceiveStatistics> ReceiveStatistics::Create(Clock* clock) {
  return std::make_unique<ReceiveStatisticsLocked>(
      clock, [](uint32_t ssrc, Clock* clock, int max_reordering_threshold) {
        return std::make_unique<StreamStatisticianLocked>(
            ssrc, clock, max_reordering_threshold);
      });
}

std::unique_ptr<ReceiveStatistics> ReceiveStatistics::CreateThreadCompatible(
    Clock* clock) {
  return std::make_unique<ReceiveStatisticsImpl>(
      clock, [](uint32_t ssrc, Clock* clock, int max_reordering_threshold) {
        return std::make_unique<StreamStatisticianImpl>(
            ssrc, clock, max_reordering_threshold);
      });
}

ReceiveStatisticsImpl::ReceiveStatisticsImpl(
    Clock* clock,
    std::function<std::unique_ptr<StreamStatisticianImplInterface>(
        uint32_t ssrc,
        Clock* clock,
        int max_reordering_threshold)> stream_statistician_factory)
    : clock_(clock),
      stream_statistician_factory_(std::move(stream_statistician_factory)),
      last_returned_ssrc_idx_(0),
      max_reordering_threshold_(kDefaultMaxReorderingThreshold) {}

void ReceiveStatisticsImpl::OnRtpPacket(const RtpPacketReceived& packet) {
  // StreamStatisticianImpl instance is created once and only destroyed when
  // this whole ReceiveStatisticsImpl is destroyed. StreamStatisticianImpl has
  // it's own locking so don't hold receive_statistics_lock_ (potential
  // deadlock).
  GetOrCreateStatistician(packet.Ssrc())->UpdateCounters(packet);
}

StreamStatistician* ReceiveStatisticsImpl::GetStatistician(
    uint32_t ssrc) const {
  const auto& it = statisticians_.find(ssrc);
  if (it == statisticians_.end())
    return nullptr;
  return it->second.get();
}

StreamStatisticianImplInterface* ReceiveStatisticsImpl::GetOrCreateStatistician(
    uint32_t ssrc) {
  std::unique_ptr<StreamStatisticianImplInterface>& impl = statisticians_[ssrc];
  if (impl == nullptr) {  // new element
    impl =
        stream_statistician_factory_(ssrc, clock_, max_reordering_threshold_);
    all_ssrcs_.push_back(ssrc);
  }
  return impl.get();
}

void ReceiveStatisticsImpl::SetMaxReorderingThreshold(
    int max_reordering_threshold) {
  max_reordering_threshold_ = max_reordering_threshold;
  for (auto& statistician : statisticians_) {
    statistician.second->SetMaxReorderingThreshold(max_reordering_threshold);
  }
}

void ReceiveStatisticsImpl::SetMaxReorderingThreshold(
    uint32_t ssrc,
    int max_reordering_threshold) {
  GetOrCreateStatistician(ssrc)->SetMaxReorderingThreshold(
      max_reordering_threshold);
}

void ReceiveStatisticsImpl::EnableRetransmitDetection(uint32_t ssrc,
                                                      bool enable) {
  GetOrCreateStatistician(ssrc)->EnableRetransmitDetection(enable);
}

std::vector<rtcp::ReportBlock> ReceiveStatisticsImpl::RtcpReportBlocks(
    size_t max_blocks) {
  std::vector<rtcp::ReportBlock> result;
  result.reserve(std::min(max_blocks, all_ssrcs_.size()));

  size_t ssrc_idx = 0;
  for (size_t i = 0; i < all_ssrcs_.size() && result.size() < max_blocks; ++i) {
    ssrc_idx = (last_returned_ssrc_idx_ + i + 1) % all_ssrcs_.size();
    const uint32_t media_ssrc = all_ssrcs_[ssrc_idx];
    auto statistician_it = statisticians_.find(media_ssrc);
    RTC_DCHECK(statistician_it != statisticians_.end());
    statistician_it->second->MaybeAppendReportBlockAndReset(result);
  }
  last_returned_ssrc_idx_ = ssrc_idx;
  return result;
}

}  // namespace webrtc
