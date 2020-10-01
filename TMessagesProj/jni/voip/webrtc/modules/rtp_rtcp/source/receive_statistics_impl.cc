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
#include <vector>

#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

const int64_t kStatisticsTimeoutMs = 8000;
const int64_t kStatisticsProcessIntervalMs = 1000;

StreamStatistician::~StreamStatistician() {}

StreamStatisticianImpl::StreamStatisticianImpl(uint32_t ssrc,
                                               Clock* clock,
                                               int max_reordering_threshold)
    : ssrc_(ssrc),
      clock_(clock),
      incoming_bitrate_(kStatisticsProcessIntervalMs,
                        RateStatistics::kBpsScale),
      max_reordering_threshold_(max_reordering_threshold),
      enable_retransmit_detection_(false),
      jitter_q4_(0),
      cumulative_loss_(0),
      cumulative_loss_rtcp_offset_(0),
      last_receive_time_ms_(0),
      last_received_timestamp_(0),
      received_seq_first_(-1),
      received_seq_max_(-1),
      last_report_cumulative_loss_(0),
      last_report_seq_max_(-1) {}

StreamStatisticianImpl::~StreamStatisticianImpl() = default;

bool StreamStatisticianImpl::UpdateOutOfOrder(const RtpPacketReceived& packet,
                                              int64_t sequence_number,
                                              int64_t now_ms) {
  // Check if |packet| is second packet of a stream restart.
  if (received_seq_out_of_order_) {
    // Count the previous packet as a received; it was postponed below.
    --cumulative_loss_;

    uint16_t expected_sequence_number = *received_seq_out_of_order_ + 1;
    received_seq_out_of_order_ = absl::nullopt;
    if (packet.SequenceNumber() == expected_sequence_number) {
      // Ignore sequence number gap caused by stream restart for packet loss
      // calculation, by setting received_seq_max_ to the sequence number just
      // before the out-of-order seqno. This gives a net zero change of
      // |cumulative_loss_|, for the two packets interpreted as a stream reset.
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
    // |received_seq_max_|, otherwise we temporarily decrement
    // |cumulative_loss_|. The
    // ReceiveStatisticsTest.StreamRestartDoesntCountAsLoss test expects
    // |cumulative_loss_| to be unchanged by the reception of the first packet
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
  MutexLock lock(&stream_lock_);
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

  // lib_jingle sometimes deliver crazy jumps in TS for the same stream.
  // If this happens, don't update jitter value. Use 5 secs video frequency
  // as the threshold.
  if (time_diff_samples < 450000) {
    // Note we calculate in Q4 to avoid using float.
    int32_t jitter_diff_q4 = (time_diff_samples << 4) - jitter_q4_;
    jitter_q4_ += ((jitter_diff_q4 + 8) >> 4);
  }
}

void StreamStatisticianImpl::SetMaxReorderingThreshold(
    int max_reordering_threshold) {
  MutexLock lock(&stream_lock_);
  max_reordering_threshold_ = max_reordering_threshold;
}

void StreamStatisticianImpl::EnableRetransmitDetection(bool enable) {
  MutexLock lock(&stream_lock_);
  enable_retransmit_detection_ = enable;
}

RtpReceiveStats StreamStatisticianImpl::GetStats() const {
  MutexLock lock(&stream_lock_);
  RtpReceiveStats stats;
  stats.packets_lost = cumulative_loss_;
  // TODO(nisse): Can we return a float instead?
  // Note: internal jitter value is in Q4 and needs to be scaled by 1/16.
  stats.jitter = jitter_q4_ >> 4;
  stats.last_packet_received_timestamp_ms =
      receive_counters_.last_packet_received_timestamp_ms;
  stats.packet_counter = receive_counters_.transmitted;
  return stats;
}

bool StreamStatisticianImpl::GetActiveStatisticsAndReset(
    RtcpStatistics* statistics) {
  MutexLock lock(&stream_lock_);
  if (clock_->TimeInMilliseconds() - last_receive_time_ms_ >=
      kStatisticsTimeoutMs) {
    // Not active.
    return false;
  }
  if (!ReceivedRtpPacket()) {
    return false;
  }

  *statistics = CalculateRtcpStatistics();

  return true;
}

RtcpStatistics StreamStatisticianImpl::CalculateRtcpStatistics() {
  RtcpStatistics stats;
  // Calculate fraction lost.
  int64_t exp_since_last = received_seq_max_ - last_report_seq_max_;
  RTC_DCHECK_GE(exp_since_last, 0);

  int32_t lost_since_last = cumulative_loss_ - last_report_cumulative_loss_;
  if (exp_since_last > 0 && lost_since_last > 0) {
    // Scale 0 to 255, where 255 is 100% loss.
    stats.fraction_lost =
        static_cast<uint8_t>(255 * lost_since_last / exp_since_last);
  } else {
    stats.fraction_lost = 0;
  }

  // TODO(danilchap): Ensure |stats.packets_lost| is clamped to fit in a signed
  // 24-bit value.
  stats.packets_lost = cumulative_loss_ + cumulative_loss_rtcp_offset_;
  if (stats.packets_lost < 0) {
    // Clamp to zero. Work around to accomodate for senders that misbehave with
    // negative cumulative loss.
    stats.packets_lost = 0;
    cumulative_loss_rtcp_offset_ = -cumulative_loss_;
  }
  stats.extended_highest_sequence_number =
      static_cast<uint32_t>(received_seq_max_);
  // Note: internal jitter value is in Q4 and needs to be scaled by 1/16.
  stats.jitter = jitter_q4_ >> 4;

  // Only for report blocks in RTCP SR and RR.
  last_report_cumulative_loss_ = cumulative_loss_;
  last_report_seq_max_ = received_seq_max_;
  BWE_TEST_LOGGING_PLOT_WITH_SSRC(1, "cumulative_loss_pkts",
                                  clock_->TimeInMilliseconds(),
                                  cumulative_loss_, ssrc_);
  BWE_TEST_LOGGING_PLOT_WITH_SSRC(
      1, "received_seq_max_pkts", clock_->TimeInMilliseconds(),
      (received_seq_max_ - received_seq_first_), ssrc_);

  return stats;
}

absl::optional<int> StreamStatisticianImpl::GetFractionLostInPercent() const {
  MutexLock lock(&stream_lock_);
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
  MutexLock lock(&stream_lock_);
  return receive_counters_;
}

uint32_t StreamStatisticianImpl::BitrateReceived() const {
  MutexLock lock(&stream_lock_);
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
  return std::make_unique<ReceiveStatisticsImpl>(clock);
}

ReceiveStatisticsImpl::ReceiveStatisticsImpl(Clock* clock)
    : clock_(clock),
      last_returned_ssrc_(0),
      max_reordering_threshold_(kDefaultMaxReorderingThreshold) {}

ReceiveStatisticsImpl::~ReceiveStatisticsImpl() {
  while (!statisticians_.empty()) {
    delete statisticians_.begin()->second;
    statisticians_.erase(statisticians_.begin());
  }
}

void ReceiveStatisticsImpl::OnRtpPacket(const RtpPacketReceived& packet) {
  // StreamStatisticianImpl instance is created once and only destroyed when
  // this whole ReceiveStatisticsImpl is destroyed. StreamStatisticianImpl has
  // it's own locking so don't hold receive_statistics_lock_ (potential
  // deadlock).
  GetOrCreateStatistician(packet.Ssrc())->UpdateCounters(packet);
}

StreamStatisticianImpl* ReceiveStatisticsImpl::GetStatistician(
    uint32_t ssrc) const {
  MutexLock lock(&receive_statistics_lock_);
  const auto& it = statisticians_.find(ssrc);
  if (it == statisticians_.end())
    return NULL;
  return it->second;
}

StreamStatisticianImpl* ReceiveStatisticsImpl::GetOrCreateStatistician(
    uint32_t ssrc) {
  MutexLock lock(&receive_statistics_lock_);
  StreamStatisticianImpl*& impl = statisticians_[ssrc];
  if (impl == nullptr) {  // new element
    impl = new StreamStatisticianImpl(ssrc, clock_, max_reordering_threshold_);
  }
  return impl;
}

void ReceiveStatisticsImpl::SetMaxReorderingThreshold(
    int max_reordering_threshold) {
  std::map<uint32_t, StreamStatisticianImpl*> statisticians;
  {
    MutexLock lock(&receive_statistics_lock_);
    max_reordering_threshold_ = max_reordering_threshold;
    statisticians = statisticians_;
  }
  for (auto& statistician : statisticians) {
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
  std::map<uint32_t, StreamStatisticianImpl*> statisticians;
  {
    MutexLock lock(&receive_statistics_lock_);
    statisticians = statisticians_;
  }
  std::vector<rtcp::ReportBlock> result;
  result.reserve(std::min(max_blocks, statisticians.size()));
  auto add_report_block = [&result](uint32_t media_ssrc,
                                    StreamStatisticianImpl* statistician) {
    // Do we have receive statistics to send?
    RtcpStatistics stats;
    if (!statistician->GetActiveStatisticsAndReset(&stats))
      return;
    result.emplace_back();
    rtcp::ReportBlock& block = result.back();
    block.SetMediaSsrc(media_ssrc);
    block.SetFractionLost(stats.fraction_lost);
    if (!block.SetCumulativeLost(stats.packets_lost)) {
      RTC_LOG(LS_WARNING) << "Cumulative lost is oversized.";
      result.pop_back();
      return;
    }
    block.SetExtHighestSeqNum(stats.extended_highest_sequence_number);
    block.SetJitter(stats.jitter);
  };

  const auto start_it = statisticians.upper_bound(last_returned_ssrc_);
  for (auto it = start_it;
       result.size() < max_blocks && it != statisticians.end(); ++it)
    add_report_block(it->first, it->second);
  for (auto it = statisticians.begin();
       result.size() < max_blocks && it != start_it; ++it)
    add_report_block(it->first, it->second);

  if (!result.empty())
    last_returned_ssrc_ = result.back().source_ssrc();
  return result;
}

}  // namespace webrtc
