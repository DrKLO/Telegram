/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/remote_estimator_proxy.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <utility>

#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

// Impossible to request feedback older than what can be represented by 15 bits.
const int RemoteEstimatorProxy::kMaxNumberOfPackets = (1 << 15);

// The maximum allowed value for a timestamp in milliseconds. This is lower
// than the numerical limit since we often convert to microseconds.
static constexpr int64_t kMaxTimeMs =
    std::numeric_limits<int64_t>::max() / 1000;

RemoteEstimatorProxy::RemoteEstimatorProxy(
    Clock* clock,
    TransportFeedbackSenderInterface* feedback_sender,
    const WebRtcKeyValueConfig* key_value_config,
    NetworkStateEstimator* network_state_estimator)
    : clock_(clock),
      feedback_sender_(feedback_sender),
      send_config_(key_value_config),
      last_process_time_ms_(-1),
      network_state_estimator_(network_state_estimator),
      media_ssrc_(0),
      feedback_packet_count_(0),
      send_interval_ms_(send_config_.default_interval->ms()),
      send_periodic_feedback_(true),
      previous_abs_send_time_(0),
      abs_send_timestamp_(clock->CurrentTime()) {
  RTC_LOG(LS_INFO)
      << "Maximum interval between transport feedback RTCP messages (ms): "
      << send_config_.max_interval->ms();
}

RemoteEstimatorProxy::~RemoteEstimatorProxy() {}

void RemoteEstimatorProxy::IncomingPacket(int64_t arrival_time_ms,
                                          size_t payload_size,
                                          const RTPHeader& header) {
  if (arrival_time_ms < 0 || arrival_time_ms > kMaxTimeMs) {
    RTC_LOG(LS_WARNING) << "Arrival time out of bounds: " << arrival_time_ms;
    return;
  }
  MutexLock lock(&lock_);
  media_ssrc_ = header.ssrc;
  int64_t seq = 0;

  if (header.extension.hasTransportSequenceNumber) {
    seq = unwrapper_.Unwrap(header.extension.transportSequenceNumber);

    if (send_periodic_feedback_) {
      if (periodic_window_start_seq_ &&
          packet_arrival_times_.lower_bound(*periodic_window_start_seq_) ==
              packet_arrival_times_.end()) {
        // Start new feedback packet, cull old packets.
        for (auto it = packet_arrival_times_.begin();
             it != packet_arrival_times_.end() && it->first < seq &&
             arrival_time_ms - it->second >= send_config_.back_window->ms();) {
          it = packet_arrival_times_.erase(it);
        }
      }
      if (!periodic_window_start_seq_ || seq < *periodic_window_start_seq_) {
        periodic_window_start_seq_ = seq;
      }
    }

    // We are only interested in the first time a packet is received.
    if (packet_arrival_times_.find(seq) != packet_arrival_times_.end())
      return;

    packet_arrival_times_[seq] = arrival_time_ms;

    // Limit the range of sequence numbers to send feedback for.
    auto first_arrival_time_to_keep = packet_arrival_times_.lower_bound(
        packet_arrival_times_.rbegin()->first - kMaxNumberOfPackets);
    if (first_arrival_time_to_keep != packet_arrival_times_.begin()) {
      packet_arrival_times_.erase(packet_arrival_times_.begin(),
                                  first_arrival_time_to_keep);
      if (send_periodic_feedback_) {
        // |packet_arrival_times_| cannot be empty since we just added one
        // element and the last element is not deleted.
        RTC_DCHECK(!packet_arrival_times_.empty());
        periodic_window_start_seq_ = packet_arrival_times_.begin()->first;
      }
    }

    if (header.extension.feedback_request) {
      // Send feedback packet immediately.
      SendFeedbackOnRequest(seq, header.extension.feedback_request.value());
    }
  }

  if (network_state_estimator_ && header.extension.hasAbsoluteSendTime) {
    PacketResult packet_result;
    packet_result.receive_time = Timestamp::Millis(arrival_time_ms);
    // Ignore reordering of packets and assume they have approximately the same
    // send time.
    abs_send_timestamp_ += std::max(
        header.extension.GetAbsoluteSendTimeDelta(previous_abs_send_time_),
        TimeDelta::Millis(0));
    previous_abs_send_time_ = header.extension.absoluteSendTime;
    packet_result.sent_packet.send_time = abs_send_timestamp_;
    // TODO(webrtc:10742): Take IP header and transport overhead into account.
    packet_result.sent_packet.size =
        DataSize::Bytes(header.headerLength + payload_size);
    packet_result.sent_packet.sequence_number = seq;
    network_state_estimator_->OnReceivedPacket(packet_result);
  }
}

bool RemoteEstimatorProxy::LatestEstimate(std::vector<unsigned int>* ssrcs,
                                          unsigned int* bitrate_bps) const {
  return false;
}

int64_t RemoteEstimatorProxy::TimeUntilNextProcess() {
  MutexLock lock(&lock_);
  if (!send_periodic_feedback_) {
    // Wait a day until next process.
    return 24 * 60 * 60 * 1000;
  } else if (last_process_time_ms_ != -1) {
    int64_t now = clock_->TimeInMilliseconds();
    if (now - last_process_time_ms_ < send_interval_ms_)
      return last_process_time_ms_ + send_interval_ms_ - now;
  }
  return 0;
}

void RemoteEstimatorProxy::Process() {
  MutexLock lock(&lock_);
  if (!send_periodic_feedback_) {
    return;
  }
  last_process_time_ms_ = clock_->TimeInMilliseconds();

  SendPeriodicFeedbacks();
}

void RemoteEstimatorProxy::OnBitrateChanged(int bitrate_bps) {
  // TwccReportSize = Ipv4(20B) + UDP(8B) + SRTP(10B) +
  // AverageTwccReport(30B)
  // TwccReport size at 50ms interval is 24 byte.
  // TwccReport size at 250ms interval is 36 byte.
  // AverageTwccReport = (TwccReport(50ms) + TwccReport(250ms)) / 2
  constexpr int kTwccReportSize = 20 + 8 + 10 + 30;
  const double kMinTwccRate =
      kTwccReportSize * 8.0 * 1000.0 / send_config_.max_interval->ms();
  const double kMaxTwccRate =
      kTwccReportSize * 8.0 * 1000.0 / send_config_.min_interval->ms();

  // Let TWCC reports occupy 5% of total bandwidth.
  MutexLock lock(&lock_);
  send_interval_ms_ = static_cast<int>(
      0.5 + kTwccReportSize * 8.0 * 1000.0 /
                rtc::SafeClamp(send_config_.bandwidth_fraction * bitrate_bps,
                               kMinTwccRate, kMaxTwccRate));
}

void RemoteEstimatorProxy::SetSendPeriodicFeedback(
    bool send_periodic_feedback) {
  MutexLock lock(&lock_);
  send_periodic_feedback_ = send_periodic_feedback;
}

void RemoteEstimatorProxy::SendPeriodicFeedbacks() {
  // |periodic_window_start_seq_| is the first sequence number to include in the
  // current feedback packet. Some older may still be in the map, in case a
  // reordering happens and we need to retransmit them.
  if (!periodic_window_start_seq_)
    return;

  std::unique_ptr<rtcp::RemoteEstimate> remote_estimate;
  if (network_state_estimator_) {
    absl::optional<NetworkStateEstimate> state_estimate =
        network_state_estimator_->GetCurrentEstimate();
    if (state_estimate) {
      remote_estimate = std::make_unique<rtcp::RemoteEstimate>();
      remote_estimate->SetEstimate(state_estimate.value());
    }
  }

  for (auto begin_iterator =
           packet_arrival_times_.lower_bound(*periodic_window_start_seq_);
       begin_iterator != packet_arrival_times_.cend();
       begin_iterator =
           packet_arrival_times_.lower_bound(*periodic_window_start_seq_)) {
    auto feedback_packet = std::make_unique<rtcp::TransportFeedback>();
    periodic_window_start_seq_ = BuildFeedbackPacket(
        feedback_packet_count_++, media_ssrc_, *periodic_window_start_seq_,
        begin_iterator, packet_arrival_times_.cend(), feedback_packet.get());

    RTC_DCHECK(feedback_sender_ != nullptr);

    std::vector<std::unique_ptr<rtcp::RtcpPacket>> packets;
    if (remote_estimate) {
      packets.push_back(std::move(remote_estimate));
    }
    packets.push_back(std::move(feedback_packet));

    feedback_sender_->SendCombinedRtcpPacket(std::move(packets));
    // Note: Don't erase items from packet_arrival_times_ after sending, in case
    // they need to be re-sent after a reordering. Removal will be handled
    // by OnPacketArrival once packets are too old.
  }
}

void RemoteEstimatorProxy::SendFeedbackOnRequest(
    int64_t sequence_number,
    const FeedbackRequest& feedback_request) {
  if (feedback_request.sequence_count == 0) {
    return;
  }

  auto feedback_packet = std::make_unique<rtcp::TransportFeedback>(
      feedback_request.include_timestamps);

  int64_t first_sequence_number =
      sequence_number - feedback_request.sequence_count + 1;
  auto begin_iterator =
      packet_arrival_times_.lower_bound(first_sequence_number);
  auto end_iterator = packet_arrival_times_.upper_bound(sequence_number);

  BuildFeedbackPacket(feedback_packet_count_++, media_ssrc_,
                      first_sequence_number, begin_iterator, end_iterator,
                      feedback_packet.get());

  // Clear up to the first packet that is included in this feedback packet.
  packet_arrival_times_.erase(packet_arrival_times_.begin(), begin_iterator);

  RTC_DCHECK(feedback_sender_ != nullptr);
  std::vector<std::unique_ptr<rtcp::RtcpPacket>> packets;
  packets.push_back(std::move(feedback_packet));
  feedback_sender_->SendCombinedRtcpPacket(std::move(packets));
}

int64_t RemoteEstimatorProxy::BuildFeedbackPacket(
    uint8_t feedback_packet_count,
    uint32_t media_ssrc,
    int64_t base_sequence_number,
    std::map<int64_t, int64_t>::const_iterator begin_iterator,
    std::map<int64_t, int64_t>::const_iterator end_iterator,
    rtcp::TransportFeedback* feedback_packet) {
  RTC_DCHECK(begin_iterator != end_iterator);

  // TODO(sprang): Measure receive times in microseconds and remove the
  // conversions below.
  feedback_packet->SetMediaSsrc(media_ssrc);
  // Base sequence number is the expected first sequence number. This is known,
  // but we might not have actually received it, so the base time shall be the
  // time of the first received packet in the feedback.
  feedback_packet->SetBase(static_cast<uint16_t>(base_sequence_number & 0xFFFF),
                           begin_iterator->second * 1000);
  feedback_packet->SetFeedbackSequenceNumber(feedback_packet_count);
  int64_t next_sequence_number = base_sequence_number;
  for (auto it = begin_iterator; it != end_iterator; ++it) {
    if (!feedback_packet->AddReceivedPacket(
            static_cast<uint16_t>(it->first & 0xFFFF), it->second * 1000)) {
      // If we can't even add the first seq to the feedback packet, we won't be
      // able to build it at all.
      RTC_CHECK(begin_iterator != it);

      // Could not add timestamp, feedback packet might be full. Return and
      // try again with a fresh packet.
      break;
    }
    next_sequence_number = it->first + 1;
  }
  return next_sequence_number;
}

}  // namespace webrtc
