/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_SIMULATED_NETWORK_H_
#define API_TEST_SIMULATED_NETWORK_H_

#include <stddef.h>
#include <stdint.h>

#include <deque>
#include <queue>
#include <vector>

#include "absl/types/optional.h"
#include "rtc_base/random.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

struct PacketInFlightInfo {
  PacketInFlightInfo(size_t size, int64_t send_time_us, uint64_t packet_id)
      : size(size), send_time_us(send_time_us), packet_id(packet_id) {}

  size_t size;
  int64_t send_time_us;
  // Unique identifier for the packet in relation to other packets in flight.
  uint64_t packet_id;
};

struct PacketDeliveryInfo {
  static constexpr int kNotReceived = -1;
  PacketDeliveryInfo(PacketInFlightInfo source, int64_t receive_time_us)
      : receive_time_us(receive_time_us), packet_id(source.packet_id) {}
  int64_t receive_time_us;
  uint64_t packet_id;
};

// BuiltInNetworkBehaviorConfig is a built-in network behavior configuration
// for built-in network behavior that will be used by WebRTC if no custom
// NetworkBehaviorInterface is provided.
struct BuiltInNetworkBehaviorConfig {
  //  Queue length in number of packets.
  size_t queue_length_packets = 0;
  // Delay in addition to capacity induced delay.
  int queue_delay_ms = 0;
  // Standard deviation of the extra delay.
  int delay_standard_deviation_ms = 0;
  // Link capacity in kbps.
  int link_capacity_kbps = 0;
  // Random packet loss.
  int loss_percent = 0;
  // If packets are allowed to be reordered.
  bool allow_reordering = false;
  // The average length of a burst of lost packets.
  int avg_burst_loss_length = -1;
  // Additional bytes to add to packet size.
  int packet_overhead = 0;
  // Enable CoDel active queue management.
  bool codel_active_queue_management = false;
};

class NetworkBehaviorInterface {
 public:
  virtual bool EnqueuePacket(PacketInFlightInfo packet_info) = 0;
  // Retrieves all packets that should be delivered by the given receive time.
  virtual std::vector<PacketDeliveryInfo> DequeueDeliverablePackets(
      int64_t receive_time_us) = 0;
  // Returns time in microseconds when caller should call
  // DequeueDeliverablePackets to get next set of packets to deliver.
  virtual absl::optional<int64_t> NextDeliveryTimeUs() const = 0;
  virtual ~NetworkBehaviorInterface() = default;
};

// Class simulating a network link. This is a simple and naive solution just
// faking capacity and adding an extra transport delay in addition to the
// capacity introduced delay.
class SimulatedNetworkInterface : public NetworkBehaviorInterface {
 public:
  // Sets a new configuration. This won't affect packets already in the pipe.
  virtual void SetConfig(const BuiltInNetworkBehaviorConfig& config) = 0;
  virtual void UpdateConfig(
      std::function<void(BuiltInNetworkBehaviorConfig*)> config_modifier) = 0;
  virtual void PauseTransmissionUntil(int64_t until_us) = 0;
};

}  // namespace webrtc

#endif  // API_TEST_SIMULATED_NETWORK_H_
