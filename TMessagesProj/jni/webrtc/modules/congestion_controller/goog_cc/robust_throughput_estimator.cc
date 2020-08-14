/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/robust_throughput_estimator.h"

#include <stddef.h>

#include <algorithm>
#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {

RobustThroughputEstimator::RobustThroughputEstimator(
    const RobustThroughputEstimatorSettings& settings)
    : settings_(settings) {
  RTC_DCHECK(settings.enabled);
}

RobustThroughputEstimator::~RobustThroughputEstimator() {}

void RobustThroughputEstimator::IncomingPacketFeedbackVector(
    const std::vector<PacketResult>& packet_feedback_vector) {
  RTC_DCHECK(std::is_sorted(packet_feedback_vector.begin(),
                            packet_feedback_vector.end(),
                            PacketResult::ReceiveTimeOrder()));
  for (const auto& packet : packet_feedback_vector) {
    // Insert the new packet.
    window_.push_back(packet);
    window_.back().sent_packet.prior_unacked_data =
        window_.back().sent_packet.prior_unacked_data *
        settings_.unacked_weight;
    // In most cases, receive timestamps should already be in order, but in the
    // rare case where feedback packets have been reordered, we do some swaps to
    // ensure that the window is sorted.
    for (size_t i = window_.size() - 1;
         i > 0 && window_[i].receive_time < window_[i - 1].receive_time; i--) {
      std::swap(window_[i], window_[i - 1]);
    }
    // Remove old packets.
    while (window_.size() > settings_.kMaxPackets ||
           (window_.size() > settings_.min_packets &&
            packet.receive_time - window_.front().receive_time >
                settings_.window_duration)) {
      window_.pop_front();
    }
  }
}

absl::optional<DataRate> RobustThroughputEstimator::bitrate() const {
  if (window_.size() < settings_.initial_packets)
    return absl::nullopt;

  TimeDelta largest_recv_gap(TimeDelta::Millis(0));
  TimeDelta second_largest_recv_gap(TimeDelta::Millis(0));
  for (size_t i = 1; i < window_.size(); i++) {
    // Find receive time gaps
    TimeDelta gap = window_[i].receive_time - window_[i - 1].receive_time;
    if (gap > largest_recv_gap) {
      second_largest_recv_gap = largest_recv_gap;
      largest_recv_gap = gap;
    } else if (gap > second_largest_recv_gap) {
      second_largest_recv_gap = gap;
    }
  }

  Timestamp min_send_time = window_[0].sent_packet.send_time;
  Timestamp max_send_time = window_[0].sent_packet.send_time;
  Timestamp min_recv_time = window_[0].receive_time;
  Timestamp max_recv_time = window_[0].receive_time;
  DataSize data_size = DataSize::Bytes(0);
  for (const auto& packet : window_) {
    min_send_time = std::min(min_send_time, packet.sent_packet.send_time);
    max_send_time = std::max(max_send_time, packet.sent_packet.send_time);
    min_recv_time = std::min(min_recv_time, packet.receive_time);
    max_recv_time = std::max(max_recv_time, packet.receive_time);
    data_size += packet.sent_packet.size;
    data_size += packet.sent_packet.prior_unacked_data;
  }

  // Suppose a packet of size S is sent every T milliseconds.
  // A window of N packets would contain N*S bytes, but the time difference
  // between the first and the last packet would only be (N-1)*T. Thus, we
  // need to remove one packet.
  DataSize recv_size = data_size;
  DataSize send_size = data_size;
  if (settings_.assume_shared_link) {
    // Depending on how the bottleneck queue is implemented, a large packet
    // may delay sending of sebsequent packets, so the delay between packets
    // i and i+1  depends on the size of both packets. In this case we minimize
    // the maximum error by removing half of both the first and last packet
    // size.
    DataSize first_last_average_size =
        (window_.front().sent_packet.size +
         window_.front().sent_packet.prior_unacked_data +
         window_.back().sent_packet.size +
         window_.back().sent_packet.prior_unacked_data) /
        2;
    recv_size -= first_last_average_size;
    send_size -= first_last_average_size;
  } else {
    // In the simpler case where the delay between packets i and i+1 only
    // depends on the size of packet i+1, the first packet doesn't give us
    // any information. Analogously, we assume that the start send time
    // for the last packet doesn't depend on the size of the packet.
    recv_size -= (window_.front().sent_packet.size +
                  window_.front().sent_packet.prior_unacked_data);
    send_size -= (window_.back().sent_packet.size +
                  window_.back().sent_packet.prior_unacked_data);
  }

  // Remove the largest gap by replacing it by the second largest gap
  // or the average gap.
  TimeDelta send_duration = max_send_time - min_send_time;
  TimeDelta recv_duration = (max_recv_time - min_recv_time) - largest_recv_gap;
  if (settings_.reduce_bias) {
    recv_duration += second_largest_recv_gap;
  } else {
    recv_duration += recv_duration / (window_.size() - 2);
  }

  send_duration = std::max(send_duration, TimeDelta::Millis(1));
  recv_duration = std::max(recv_duration, TimeDelta::Millis(1));
  return std::min(send_size / send_duration, recv_size / recv_duration);
}

}  // namespace webrtc
