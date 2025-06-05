/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef CALL_SIMULATED_NETWORK_H_
#define CALL_SIMULATED_NETWORK_H_

#include <stdint.h>

#include <deque>
#include <queue>
#include <vector>

#include "absl/types/optional.h"
#include "api/sequence_checker.h"
#include "api/test/simulated_network.h"
#include "api/units/data_size.h"
#include "api/units/timestamp.h"
#include "rtc_base/race_checker.h"
#include "rtc_base/random.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

// Class simulating a network link.
//
// This is a basic implementation of NetworkBehaviorInterface that supports:
// - Packet loss
// - Capacity delay
// - Extra delay with or without packets reorder
// - Packet overhead
// - Queue max capacity
class RTC_EXPORT SimulatedNetwork : public SimulatedNetworkInterface {
 public:
  using Config = BuiltInNetworkBehaviorConfig;
  explicit SimulatedNetwork(Config config, uint64_t random_seed = 1);
  ~SimulatedNetwork() override;

  // Sets a new configuration. This will affect packets that will be sent with
  // EnqueuePacket but also packets in the network that have not left the
  // network emulation. Packets that are ready to be retrieved by
  // DequeueDeliverablePackets are not affected by the new configuration.
  // TODO(bugs.webrtc.org/14525): Fix SetConfig and make it apply only to the
  // part of the packet that is currently being sent (instead of applying to
  // all of it).
  void SetConfig(const Config& config) override;
  void UpdateConfig(std::function<void(BuiltInNetworkBehaviorConfig*)>
                        config_modifier) override;
  void PauseTransmissionUntil(int64_t until_us) override;

  // NetworkBehaviorInterface
  bool EnqueuePacket(PacketInFlightInfo packet) override;
  std::vector<PacketDeliveryInfo> DequeueDeliverablePackets(
      int64_t receive_time_us) override;

  absl::optional<int64_t> NextDeliveryTimeUs() const override;

 private:
  struct PacketInfo {
    PacketInFlightInfo packet;
    // Time when the packet has left (or will leave) the network.
    int64_t arrival_time_us;
  };
  // Contains current configuration state.
  struct ConfigState {
    // Static link configuration.
    Config config;
    // The probability to drop the packet if we are currently dropping a
    // burst of packet
    double prob_loss_bursting;
    // The probability to drop a burst of packets.
    double prob_start_bursting;
    // Used for temporary delay spikes.
    int64_t pause_transmission_until_us = 0;
  };

  // Moves packets from capacity- to delay link.
  void UpdateCapacityQueue(ConfigState state, int64_t time_now_us)
      RTC_RUN_ON(&process_checker_);
  ConfigState GetConfigState() const;

  mutable Mutex config_lock_;

  // Guards the data structures involved in delay and loss processing, such as
  // the packet queues.
  rtc::RaceChecker process_checker_;
  // Models the capacity of the network by rejecting packets if the queue is
  // full and keeping them in the queue until they are ready to exit (according
  // to the link capacity, which cannot be violated, e.g. a 1 kbps link will
  // only be able to deliver 1000 bits per second).
  //
  // Invariant:
  // The head of the `capacity_link_` has arrival_time_us correctly set to the
  // time when the packet is supposed to be delivered (without accounting
  // potential packet loss or potential extra delay and without accounting for a
  // new configuration of the network, which requires a re-computation of the
  // arrival_time_us).
  std::queue<PacketInfo> capacity_link_ RTC_GUARDED_BY(process_checker_);
  // Models the extra delay of the network (see `queue_delay_ms`
  // and `delay_standard_deviation_ms` in BuiltInNetworkBehaviorConfig), packets
  // in the `delay_link_` have technically already left the network and don't
  // use its capacity but they are not delivered yet.
  std::deque<PacketInfo> delay_link_ RTC_GUARDED_BY(process_checker_);
  // Represents the next moment in time when the network is supposed to deliver
  // packets to the client (either by pulling them from `delay_link_` or
  // `capacity_link_` or both).
  absl::optional<int64_t> next_process_time_us_
      RTC_GUARDED_BY(process_checker_);

  ConfigState config_state_ RTC_GUARDED_BY(config_lock_);

  Random random_ RTC_GUARDED_BY(process_checker_);
  // Are we currently dropping a burst of packets?
  bool bursting_;

  // The send time of the last enqueued packet, this is only used to check that
  // the send time of enqueued packets is monotonically increasing.
  int64_t last_enqueue_time_us_;

  // The last time a packet left the capacity_link_ (used to enforce
  // the capacity of the link and avoid packets starts to get sent before
  // the link it free).
  int64_t last_capacity_link_exit_time_;
};

}  // namespace webrtc

#endif  // CALL_SIMULATED_NETWORK_H_
