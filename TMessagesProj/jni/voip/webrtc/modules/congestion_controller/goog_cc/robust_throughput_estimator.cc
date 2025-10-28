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
#include <vector>

#include "absl/types/optional.h"
#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

RobustThroughputEstimator::RobustThroughputEstimator(
    const RobustThroughputEstimatorSettings& settings)
    : settings_(settings),
      latest_discarded_send_time_(Timestamp::MinusInfinity()) {
  RTC_DCHECK(settings.enabled);
}

RobustThroughputEstimator::~RobustThroughputEstimator() {}

bool RobustThroughputEstimator::FirstPacketOutsideWindow() {
  if (window_.empty())
    return false;
  if (window_.size() > settings_.max_window_packets)
    return true;
  TimeDelta current_window_duration =
      window_.back().receive_time - window_.front().receive_time;
  if (current_window_duration > settings_.max_window_duration)
    return true;
  if (window_.size() > settings_.window_packets &&
      current_window_duration > settings_.min_window_duration) {
    return true;
  }
  return false;
}

void RobustThroughputEstimator::IncomingPacketFeedbackVector(
    const std::vector<PacketResult>& packet_feedback_vector) {
  RTC_DCHECK(std::is_sorted(packet_feedback_vector.begin(),
                            packet_feedback_vector.end(),
                            PacketResult::ReceiveTimeOrder()));
  for (const auto& packet : packet_feedback_vector) {
    // Ignore packets without valid send or receive times.
    // (This should not happen in production since lost packets are filtered
    // out before passing the feedback vector to the throughput estimator.
    // However, explicitly handling this case makes the estimator more robust
    // and avoids a hard-to-detect bad state.)
    if (packet.receive_time.IsInfinite() ||
        packet.sent_packet.send_time.IsInfinite()) {
      continue;
    }

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
    constexpr TimeDelta kMaxReorderingTime = TimeDelta::Seconds(1);
    const TimeDelta receive_delta =
        (window_.back().receive_time - packet.receive_time);
    if (receive_delta > kMaxReorderingTime) {
      RTC_LOG(LS_WARNING)
          << "Severe packet re-ordering or timestamps offset changed: "
          << receive_delta;
      window_.clear();
      latest_discarded_send_time_ = Timestamp::MinusInfinity();
    }
  }

  // Remove old packets.
  while (FirstPacketOutsideWindow()) {
    latest_discarded_send_time_ = std::max(
        latest_discarded_send_time_, window_.front().sent_packet.send_time);
    window_.pop_front();
  }
}

absl::optional<DataRate> RobustThroughputEstimator::bitrate() const {
  if (window_.empty() || window_.size() < settings_.required_packets)
    return absl::nullopt;

  TimeDelta largest_recv_gap(TimeDelta::Zero());
  TimeDelta second_largest_recv_gap(TimeDelta::Zero());
  for (size_t i = 1; i < window_.size(); i++) {
    // Find receive time gaps.
    TimeDelta gap = window_[i].receive_time - window_[i - 1].receive_time;
    if (gap > largest_recv_gap) {
      second_largest_recv_gap = largest_recv_gap;
      largest_recv_gap = gap;
    } else if (gap > second_largest_recv_gap) {
      second_largest_recv_gap = gap;
    }
  }

  Timestamp first_send_time = Timestamp::PlusInfinity();
  Timestamp last_send_time = Timestamp::MinusInfinity();
  Timestamp first_recv_time = Timestamp::PlusInfinity();
  Timestamp last_recv_time = Timestamp::MinusInfinity();
  DataSize recv_size = DataSize::Bytes(0);
  DataSize send_size = DataSize::Bytes(0);
  DataSize first_recv_size = DataSize::Bytes(0);
  DataSize last_send_size = DataSize::Bytes(0);
  size_t num_sent_packets_in_window = 0;
  for (const auto& packet : window_) {
    if (packet.receive_time < first_recv_time) {
      first_recv_time = packet.receive_time;
      first_recv_size =
          packet.sent_packet.size + packet.sent_packet.prior_unacked_data;
    }
    last_recv_time = std::max(last_recv_time, packet.receive_time);
    recv_size += packet.sent_packet.size;
    recv_size += packet.sent_packet.prior_unacked_data;

    if (packet.sent_packet.send_time < latest_discarded_send_time_) {
      // If we have dropped packets from the window that were sent after
      // this packet, then this packet was reordered. Ignore it from
      // the send rate computation (since the send time may be very far
      // in the past, leading to underestimation of the send rate.)
      // However, ignoring packets creates a risk that we end up without
      // any packets left to compute a send rate.
      continue;
    }
    if (packet.sent_packet.send_time > last_send_time) {
      last_send_time = packet.sent_packet.send_time;
      last_send_size =
          packet.sent_packet.size + packet.sent_packet.prior_unacked_data;
    }
    first_send_time = std::min(first_send_time, packet.sent_packet.send_time);

    send_size += packet.sent_packet.size;
    send_size += packet.sent_packet.prior_unacked_data;
    ++num_sent_packets_in_window;
  }

  // Suppose a packet of size S is sent every T milliseconds.
  // A window of N packets would contain N*S bytes, but the time difference
  // between the first and the last packet would only be (N-1)*T. Thus, we
  // need to remove the size of one packet to get the correct rate of S/T.
  // Which packet to remove (if the packets have varying sizes),
  // depends on the network model.
  // Suppose that 2 packets with sizes s1 and s2, are received at times t1
  // and t2, respectively. If the packets were transmitted back to back over
  // a bottleneck with rate capacity r, then we'd expect t2 = t1 + r * s2.
  // Thus, r = (t2-t1) / s2, so the size of the first packet doesn't affect
  // the difference between t1 and t2.
  // Analoguously, if the first packet is sent at time t1 and the sender
  // paces the packets at rate r, then the second packet can be sent at time
  // t2 = t1 + r * s1. Thus, the send rate estimate r = (t2-t1) / s1 doesn't
  // depend on the size of the last packet.
  recv_size -= first_recv_size;
  send_size -= last_send_size;

  // Remove the largest gap by replacing it by the second largest gap.
  // This is to ensure that spurious "delay spikes" (i.e. when the
  // network stops transmitting packets for a short period, followed
  // by a burst of delayed packets), don't cause the estimate to drop.
  // This could cause an overestimation, which we guard against by
  // never returning an estimate above the send rate.
  RTC_DCHECK(first_recv_time.IsFinite());
  RTC_DCHECK(last_recv_time.IsFinite());
  TimeDelta recv_duration = (last_recv_time - first_recv_time) -
                            largest_recv_gap + second_largest_recv_gap;
  recv_duration = std::max(recv_duration, TimeDelta::Millis(1));

  if (num_sent_packets_in_window < settings_.required_packets) {
    // Too few send times to calculate a reliable send rate.
    return recv_size / recv_duration;
  }

  RTC_DCHECK(first_send_time.IsFinite());
  RTC_DCHECK(last_send_time.IsFinite());
  TimeDelta send_duration = last_send_time - first_send_time;
  send_duration = std::max(send_duration, TimeDelta::Millis(1));

  return std::min(send_size / send_duration, recv_size / recv_duration);
}

}  // namespace webrtc
