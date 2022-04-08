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
// Implementation of the CoDel active queue management algorithm. Loosely based
// on CoDel pseudocode from ACMQueue. CoDel keeps queuing delays low by dropping
// packets when delay is high. For each packet ready for dequeue, call
// DropDequeuePacket with the packet parameters to update the CoDel state.
class CoDelSimulation {
 public:
  CoDelSimulation();
  ~CoDelSimulation();

  // Returns true if packet should be dropped.
  bool DropDequeuedPacket(Timestamp now,
                          Timestamp enqueing_time,
                          DataSize packet_size,
                          DataSize queue_size);

 private:
  enum State { kNormal, kPending, kDropping };
  Timestamp enter_drop_state_at_ = Timestamp::PlusInfinity();
  Timestamp last_drop_at_ = Timestamp::MinusInfinity();
  int drop_count_ = 0;
  int previous_drop_count_ = 0;
  State state_ = State::kNormal;
};

// Class simulating a network link. This is a simple and naive solution just
// faking capacity and adding an extra transport delay in addition to the
// capacity introduced delay.
class SimulatedNetwork : public SimulatedNetworkInterface {
 public:
  using Config = BuiltInNetworkBehaviorConfig;
  explicit SimulatedNetwork(Config config, uint64_t random_seed = 1);
  ~SimulatedNetwork() override;

  // Sets a new configuration. This won't affect packets already in the pipe.
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

  // `process_checker_` guards the data structures involved in delay and loss
  // processes, such as the packet queues.
  rtc::RaceChecker process_checker_;
  CoDelSimulation codel_controller_ RTC_GUARDED_BY(process_checker_);
  std::queue<PacketInfo> capacity_link_ RTC_GUARDED_BY(process_checker_);
  Random random_;

  std::deque<PacketInfo> delay_link_ RTC_GUARDED_BY(process_checker_);

  ConfigState config_state_ RTC_GUARDED_BY(config_lock_);

  // Are we currently dropping a burst of packets?
  bool bursting_;

  int64_t queue_size_bytes_ RTC_GUARDED_BY(process_checker_) = 0;
  int64_t pending_drain_bits_ RTC_GUARDED_BY(process_checker_) = 0;
  absl::optional<int64_t> last_capacity_link_visit_us_
      RTC_GUARDED_BY(process_checker_);
  absl::optional<int64_t> next_process_time_us_
      RTC_GUARDED_BY(process_checker_);
};

}  // namespace webrtc

#endif  // CALL_SIMULATED_NETWORK_H_
