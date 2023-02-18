/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_FAKE_NETWORK_PIPE_H_
#define CALL_FAKE_NETWORK_PIPE_H_

#include <deque>
#include <map>
#include <memory>
#include <queue>
#include <set>
#include <string>
#include <vector>

#include "api/call/transport.h"
#include "api/test/simulated_network.h"
#include "call/call.h"
#include "call/simulated_packet_receiver.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class Clock;
class PacketReceiver;
enum class MediaType;

class NetworkPacket {
 public:
  NetworkPacket(rtc::CopyOnWriteBuffer packet,
                int64_t send_time,
                int64_t arrival_time,
                absl::optional<PacketOptions> packet_options,
                bool is_rtcp,
                MediaType media_type,
                absl::optional<int64_t> packet_time_us,
                Transport* transport);

  // Disallow copy constructor and copy assignment (no deep copies of `data_`).
  NetworkPacket(const NetworkPacket&) = delete;
  ~NetworkPacket();
  NetworkPacket& operator=(const NetworkPacket&) = delete;
  // Allow move constructor/assignment, so that we can use in stl containers.
  NetworkPacket(NetworkPacket&&);
  NetworkPacket& operator=(NetworkPacket&&);

  const uint8_t* data() const { return packet_.data(); }
  size_t data_length() const { return packet_.size(); }
  rtc::CopyOnWriteBuffer* raw_packet() { return &packet_; }
  int64_t send_time() const { return send_time_; }
  int64_t arrival_time() const { return arrival_time_; }
  void IncrementArrivalTime(int64_t extra_delay) {
    arrival_time_ += extra_delay;
  }
  PacketOptions packet_options() const {
    return packet_options_.value_or(PacketOptions());
  }
  bool is_rtcp() const { return is_rtcp_; }
  MediaType media_type() const { return media_type_; }
  absl::optional<int64_t> packet_time_us() const { return packet_time_us_; }
  Transport* transport() const { return transport_; }

 private:
  rtc::CopyOnWriteBuffer packet_;
  // The time the packet was sent out on the network.
  int64_t send_time_;
  // The time the packet should arrive at the receiver.
  int64_t arrival_time_;
  // If using a Transport for outgoing degradation, populate with
  // PacketOptions (transport-wide sequence number) for RTP.
  absl::optional<PacketOptions> packet_options_;
  bool is_rtcp_;
  // If using a PacketReceiver for incoming degradation, populate with
  // appropriate MediaType and packet time. This type/timing will be kept and
  // forwarded. The packet time might be altered to reflect time spent in fake
  // network pipe.
  MediaType media_type_;
  absl::optional<int64_t> packet_time_us_;
  Transport* transport_;
};

// Class faking a network link, internally is uses an implementation of a
// SimulatedNetworkInterface to simulate network behavior.
class FakeNetworkPipe : public SimulatedPacketReceiverInterface {
 public:
  // Will keep `network_behavior` alive while pipe is alive itself.
  FakeNetworkPipe(Clock* clock,
                  std::unique_ptr<NetworkBehaviorInterface> network_behavior);
  FakeNetworkPipe(Clock* clock,
                  std::unique_ptr<NetworkBehaviorInterface> network_behavior,
                  PacketReceiver* receiver);
  FakeNetworkPipe(Clock* clock,
                  std::unique_ptr<NetworkBehaviorInterface> network_behavior,
                  PacketReceiver* receiver,
                  uint64_t seed);

  // Use this constructor if you plan to insert packets using SendRt[c?]p().
  FakeNetworkPipe(Clock* clock,
                  std::unique_ptr<NetworkBehaviorInterface> network_behavior,
                  Transport* transport);

  ~FakeNetworkPipe() override;

  FakeNetworkPipe(const FakeNetworkPipe&) = delete;
  FakeNetworkPipe& operator=(const FakeNetworkPipe&) = delete;

  void SetClockOffset(int64_t offset_ms);

  // Must not be called in parallel with DeliverPacket or Process.
  void SetReceiver(PacketReceiver* receiver) override;

  // Adds/subtracts references to Transport instances. If a Transport is
  // destroyed we cannot use to forward a potential delayed packet, these
  // methods are used to maintain a map of which instances are live.
  void AddActiveTransport(Transport* transport);
  void RemoveActiveTransport(Transport* transport);

  // Implements Transport interface. When/if packets are delivered, they will
  // be passed to the transport instance given in SetReceiverTransport(). These
  // methods should only be called if a Transport instance was provided in the
  // constructor.
  bool SendRtp(const uint8_t* packet,
               size_t length,
               const PacketOptions& options);
  bool SendRtcp(const uint8_t* packet, size_t length);

  // Methods for use with Transport interface. When/if packets are delivered,
  // they will be passed to the instance specified by the `transport` parameter.
  // Note that that instance must be in the map of active transports.
  bool SendRtp(const uint8_t* packet,
               size_t length,
               const PacketOptions& options,
               Transport* transport);
  bool SendRtcp(const uint8_t* packet, size_t length, Transport* transport);

  // Implements the PacketReceiver interface. When/if packets are delivered,
  // they will be passed directly to the receiver instance given in
  // SetReceiver(), without passing through a Demuxer. The receive time
  // will be increased by the amount of time the packet spent in the
  // fake network pipe.
  PacketReceiver::DeliveryStatus DeliverPacket(MediaType media_type,
                                               rtc::CopyOnWriteBuffer packet,
                                               int64_t packet_time_us) override;

  // TODO(bugs.webrtc.org/9584): Needed to inherit the alternative signature for
  // this method.
  using PacketReceiver::DeliverPacket;

  // Processes the network queues and trigger PacketReceiver::IncomingPacket for
  // packets ready to be delivered.
  void Process() override;
  absl::optional<int64_t> TimeUntilNextProcess() override;

  // Get statistics.
  float PercentageLoss();
  int AverageDelay() override;
  size_t DroppedPackets();
  size_t SentPackets();
  void ResetStats();

 protected:
  void DeliverPacketWithLock(NetworkPacket* packet);
  int64_t GetTimeInMicroseconds() const;
  bool ShouldProcess(int64_t time_now_us) const;
  void SetTimeToNextProcess(int64_t skip_us);

 private:
  struct StoredPacket {
    NetworkPacket packet;
    bool removed = false;
    explicit StoredPacket(NetworkPacket&& packet);
    StoredPacket(StoredPacket&&) = default;
    StoredPacket(const StoredPacket&) = delete;
    StoredPacket& operator=(const StoredPacket&) = delete;
    StoredPacket() = delete;
  };

  // Returns true if enqueued, or false if packet was dropped. Use this method
  // when enqueueing packets that should be received by PacketReceiver instance.
  bool EnqueuePacket(rtc::CopyOnWriteBuffer packet,
                     absl::optional<PacketOptions> options,
                     bool is_rtcp,
                     MediaType media_type,
                     absl::optional<int64_t> packet_time_us);

  // Returns true if enqueued, or false if packet was dropped. Use this method
  // when enqueueing packets that should be received by Transport instance.
  bool EnqueuePacket(rtc::CopyOnWriteBuffer packet,
                     absl::optional<PacketOptions> options,
                     bool is_rtcp,
                     Transport* transport);

  bool EnqueuePacket(NetworkPacket&& net_packet)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(process_lock_);

  void DeliverNetworkPacket(NetworkPacket* packet)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(config_lock_);
  bool HasReceiver() const;

  Clock* const clock_;
  // `config_lock` guards the mostly constant things like the callbacks.
  mutable Mutex config_lock_;
  const std::unique_ptr<NetworkBehaviorInterface> network_behavior_;
  PacketReceiver* receiver_ RTC_GUARDED_BY(config_lock_);
  Transport* const global_transport_;

  // `process_lock` guards the data structures involved in delay and loss
  // processes, such as the packet queues.
  Mutex process_lock_;
  // Packets  are added at the back of the deque, this makes the deque ordered
  // by increasing send time. The common case when removing packets from the
  // deque is removing early packets, which will be close to the front of the
  // deque. This makes finding the packets in the deque efficient in the common
  // case.
  std::deque<StoredPacket> packets_in_flight_ RTC_GUARDED_BY(process_lock_);

  int64_t clock_offset_ms_ RTC_GUARDED_BY(config_lock_);

  // Statistics.
  size_t dropped_packets_ RTC_GUARDED_BY(process_lock_);
  size_t sent_packets_ RTC_GUARDED_BY(process_lock_);
  int64_t total_packet_delay_us_ RTC_GUARDED_BY(process_lock_);
  int64_t last_log_time_us_;

  std::map<Transport*, size_t> active_transports_ RTC_GUARDED_BY(config_lock_);
};

}  // namespace webrtc

#endif  // CALL_FAKE_NETWORK_PIPE_H_
