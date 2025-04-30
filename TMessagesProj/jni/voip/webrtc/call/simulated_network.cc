/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/simulated_network.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <utility>

#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Calculate the time (in microseconds) that takes to send N `bits` on a
// network with link capacity equal to `capacity_kbps` starting at time
// `start_time_us`.
int64_t CalculateArrivalTimeUs(int64_t start_time_us,
                               int64_t bits,
                               int capacity_kbps) {
  // If capacity is 0, the link capacity is assumed to be infinite.
  if (capacity_kbps == 0) {
    return start_time_us;
  }
  // Adding `capacity - 1` to the numerator rounds the extra delay caused by
  // capacity constraints up to an integral microsecond. Sending 0 bits takes 0
  // extra time, while sending 1 bit gets rounded up to 1 (the multiplication by
  // 1000 is because capacity is in kbps).
  // The factor 1000 comes from 10^6 / 10^3, where 10^6 is due to the time unit
  // being us and 10^3 is due to the rate unit being kbps.
  return start_time_us + ((1000 * bits + capacity_kbps - 1) / capacity_kbps);
}

}  // namespace

SimulatedNetwork::SimulatedNetwork(Config config, uint64_t random_seed)
    : random_(random_seed),
      bursting_(false),
      last_enqueue_time_us_(0),
      last_capacity_link_exit_time_(0) {
  SetConfig(config);
}

SimulatedNetwork::~SimulatedNetwork() = default;

void SimulatedNetwork::SetConfig(const Config& config) {
  MutexLock lock(&config_lock_);
  config_state_.config = config;  // Shallow copy of the struct.
  double prob_loss = config.loss_percent / 100.0;
  if (config_state_.config.avg_burst_loss_length == -1) {
    // Uniform loss
    config_state_.prob_loss_bursting = prob_loss;
    config_state_.prob_start_bursting = prob_loss;
  } else {
    // Lose packets according to a gilbert-elliot model.
    int avg_burst_loss_length = config.avg_burst_loss_length;
    int min_avg_burst_loss_length = std::ceil(prob_loss / (1 - prob_loss));

    RTC_CHECK_GT(avg_burst_loss_length, min_avg_burst_loss_length)
        << "For a total packet loss of " << config.loss_percent
        << "%% then"
           " avg_burst_loss_length must be "
        << min_avg_burst_loss_length + 1 << " or higher.";

    config_state_.prob_loss_bursting = (1.0 - 1.0 / avg_burst_loss_length);
    config_state_.prob_start_bursting =
        prob_loss / (1 - prob_loss) / avg_burst_loss_length;
  }
}

void SimulatedNetwork::UpdateConfig(
    std::function<void(BuiltInNetworkBehaviorConfig*)> config_modifier) {
  MutexLock lock(&config_lock_);
  config_modifier(&config_state_.config);
}

void SimulatedNetwork::PauseTransmissionUntil(int64_t until_us) {
  MutexLock lock(&config_lock_);
  config_state_.pause_transmission_until_us = until_us;
}

bool SimulatedNetwork::EnqueuePacket(PacketInFlightInfo packet) {
  RTC_DCHECK_RUNS_SERIALIZED(&process_checker_);

  // Check that old packets don't get enqueued, the SimulatedNetwork expect that
  // the packets' send time is monotonically increasing. The tolerance for
  // non-monotonic enqueue events is 0.5 ms because on multi core systems
  // clock_gettime(CLOCK_MONOTONIC) can show non-monotonic behaviour between
  // theads running on different cores.
  // TODO(bugs.webrtc.org/14525): Open a bug on this with the goal to re-enable
  // the DCHECK.
  // At the moment, we see more than 130ms between non-monotonic events, which
  // is more than expected.
  // RTC_DCHECK_GE(packet.send_time_us - last_enqueue_time_us_, -2000);

  ConfigState state = GetConfigState();

  // If the network config requires packet overhead, let's apply it as early as
  // possible.
  packet.size += state.config.packet_overhead;

  // If `queue_length_packets` is 0, the queue size is infinite.
  if (state.config.queue_length_packets > 0 &&
      capacity_link_.size() >= state.config.queue_length_packets) {
    // Too many packet on the link, drop this one.
    return false;
  }

  // If the packet has been sent before the previous packet in the network left
  // the capacity queue, let's ensure the new packet will start its trip in the
  // network after the last bit of the previous packet has left it.
  int64_t packet_send_time_us = packet.send_time_us;
  if (!capacity_link_.empty()) {
    packet_send_time_us =
        std::max(packet_send_time_us, capacity_link_.back().arrival_time_us);
  }
  capacity_link_.push({.packet = packet,
                       .arrival_time_us = CalculateArrivalTimeUs(
                           packet_send_time_us, packet.size * 8,
                           state.config.link_capacity_kbps)});

  // Only update `next_process_time_us_` if not already set (if set, there is no
  // way that a new packet will make the `next_process_time_us_` change).
  if (!next_process_time_us_) {
    RTC_DCHECK_EQ(capacity_link_.size(), 1);
    next_process_time_us_ = capacity_link_.front().arrival_time_us;
  }

  last_enqueue_time_us_ = packet.send_time_us;
  return true;
}

absl::optional<int64_t> SimulatedNetwork::NextDeliveryTimeUs() const {
  RTC_DCHECK_RUNS_SERIALIZED(&process_checker_);
  return next_process_time_us_;
}

void SimulatedNetwork::UpdateCapacityQueue(ConfigState state,
                                           int64_t time_now_us) {
  // If there is at least one packet in the `capacity_link_`, let's update its
  // arrival time to take into account changes in the network configuration
  // since the last call to UpdateCapacityQueue.
  if (!capacity_link_.empty()) {
    capacity_link_.front().arrival_time_us = CalculateArrivalTimeUs(
        std::max(capacity_link_.front().packet.send_time_us,
                 last_capacity_link_exit_time_),
        capacity_link_.front().packet.size * 8,
        state.config.link_capacity_kbps);
  }

  // The capacity link is empty or the first packet is not expected to exit yet.
  if (capacity_link_.empty() ||
      time_now_us < capacity_link_.front().arrival_time_us) {
    return;
  }
  bool reorder_packets = false;

  do {
    // Time to get this packet (the original or just updated arrival_time_us is
    // smaller or equal to time_now_us).
    PacketInfo packet = capacity_link_.front();
    capacity_link_.pop();

    // If the network is paused, the pause will be implemented as an extra delay
    // to be spent in the `delay_link_` queue.
    if (state.pause_transmission_until_us > packet.arrival_time_us) {
      packet.arrival_time_us = state.pause_transmission_until_us;
    }

    // Store the original arrival time, before applying packet loss or extra
    // delay. This is needed to know when it is the first available time the
    // next packet in the `capacity_link_` queue can start transmitting.
    last_capacity_link_exit_time_ = packet.arrival_time_us;

    // Drop packets at an average rate of `state.config.loss_percent` with
    // and average loss burst length of `state.config.avg_burst_loss_length`.
    if ((bursting_ && random_.Rand<double>() < state.prob_loss_bursting) ||
        (!bursting_ && random_.Rand<double>() < state.prob_start_bursting)) {
      bursting_ = true;
      packet.arrival_time_us = PacketDeliveryInfo::kNotReceived;
    } else {
      // If packets are not dropped, apply extra delay as configured.
      bursting_ = false;
      int64_t arrival_time_jitter_us = std::max(
          random_.Gaussian(state.config.queue_delay_ms * 1000,
                           state.config.delay_standard_deviation_ms * 1000),
          0.0);

      // If reordering is not allowed then adjust arrival_time_jitter
      // to make sure all packets are sent in order.
      int64_t last_arrival_time_us =
          delay_link_.empty() ? -1 : delay_link_.back().arrival_time_us;
      if (!state.config.allow_reordering && !delay_link_.empty() &&
          packet.arrival_time_us + arrival_time_jitter_us <
              last_arrival_time_us) {
        arrival_time_jitter_us = last_arrival_time_us - packet.arrival_time_us;
      }
      packet.arrival_time_us += arrival_time_jitter_us;

      // Optimization: Schedule a reorder only when a packet will exit before
      // the one in front.
      if (last_arrival_time_us > packet.arrival_time_us) {
        reorder_packets = true;
      }
    }
    delay_link_.emplace_back(packet);

    // If there are no packets in the queue, there is nothing else to do.
    if (capacity_link_.empty()) {
      break;
    }
    // If instead there is another packet in the `capacity_link_` queue, let's
    // calculate its arrival_time_us based on the latest config (which might
    // have been changed since it was enqueued).
    int64_t next_start = std::max(last_capacity_link_exit_time_,
                                  capacity_link_.front().packet.send_time_us);
    capacity_link_.front().arrival_time_us = CalculateArrivalTimeUs(
        next_start, capacity_link_.front().packet.size * 8,
        state.config.link_capacity_kbps);
    // And if the next packet in the queue needs to exit, let's dequeue it.
  } while (capacity_link_.front().arrival_time_us <= time_now_us);

  if (state.config.allow_reordering && reorder_packets) {
    // Packets arrived out of order and since the network config allows
    // reordering, let's sort them per arrival_time_us to make so they will also
    // be delivered out of order.
    std::stable_sort(delay_link_.begin(), delay_link_.end(),
                     [](const PacketInfo& p1, const PacketInfo& p2) {
                       return p1.arrival_time_us < p2.arrival_time_us;
                     });
  }
}

SimulatedNetwork::ConfigState SimulatedNetwork::GetConfigState() const {
  MutexLock lock(&config_lock_);
  return config_state_;
}

std::vector<PacketDeliveryInfo> SimulatedNetwork::DequeueDeliverablePackets(
    int64_t receive_time_us) {
  RTC_DCHECK_RUNS_SERIALIZED(&process_checker_);

  UpdateCapacityQueue(GetConfigState(), receive_time_us);
  std::vector<PacketDeliveryInfo> packets_to_deliver;

  // Check the extra delay queue.
  while (!delay_link_.empty() &&
         receive_time_us >= delay_link_.front().arrival_time_us) {
    PacketInfo packet_info = delay_link_.front();
    packets_to_deliver.emplace_back(
        PacketDeliveryInfo(packet_info.packet, packet_info.arrival_time_us));
    delay_link_.pop_front();
  }

  if (!delay_link_.empty()) {
    next_process_time_us_ = delay_link_.front().arrival_time_us;
  } else if (!capacity_link_.empty()) {
    next_process_time_us_ = capacity_link_.front().arrival_time_us;
  } else {
    next_process_time_us_.reset();
  }
  return packets_to_deliver;
}

}  // namespace webrtc
