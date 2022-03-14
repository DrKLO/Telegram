/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TEST_NETWORK_EMULATION_NETWORK_EMULATION_INTERFACES_H_
#define API_TEST_NETWORK_EMULATION_NETWORK_EMULATION_INTERFACES_H_

#include <map>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/numerics/samples_stats_counter.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/timestamp.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/socket_address.h"

namespace webrtc {

struct EmulatedIpPacket {
 public:
  EmulatedIpPacket(const rtc::SocketAddress& from,
                   const rtc::SocketAddress& to,
                   rtc::CopyOnWriteBuffer data,
                   Timestamp arrival_time,
                   uint16_t application_overhead = 0);
  ~EmulatedIpPacket() = default;
  // This object is not copyable or assignable.
  EmulatedIpPacket(const EmulatedIpPacket&) = delete;
  EmulatedIpPacket& operator=(const EmulatedIpPacket&) = delete;
  // This object is only moveable.
  EmulatedIpPacket(EmulatedIpPacket&&) = default;
  EmulatedIpPacket& operator=(EmulatedIpPacket&&) = default;

  size_t size() const { return data.size(); }
  const uint8_t* cdata() const { return data.cdata(); }

  size_t ip_packet_size() const { return size() + headers_size; }
  rtc::SocketAddress from;
  rtc::SocketAddress to;
  // Holds the UDP payload.
  rtc::CopyOnWriteBuffer data;
  uint16_t headers_size;
  Timestamp arrival_time;
};

// Interface for handling IP packets from an emulated network. This is used with
// EmulatedEndpoint to receive packets on a specific port.
class EmulatedNetworkReceiverInterface {
 public:
  virtual ~EmulatedNetworkReceiverInterface() = default;

  virtual void OnPacketReceived(EmulatedIpPacket packet) = 0;
};

class EmulatedNetworkOutgoingStats {
 public:
  virtual ~EmulatedNetworkOutgoingStats() = default;

  virtual int64_t PacketsSent() const = 0;

  virtual DataSize BytesSent() const = 0;

  // Returns the timestamped sizes of all sent packets if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& SentPacketsSizeCounter() const = 0;

  virtual DataSize FirstSentPacketSize() const = 0;

  // Returns time of the first packet sent or infinite value if no packets were
  // sent.
  virtual Timestamp FirstPacketSentTime() const = 0;

  // Returns time of the last packet sent or infinite value if no packets were
  // sent.
  virtual Timestamp LastPacketSentTime() const = 0;

  // Returns average send rate. Requires that at least 2 packets were sent.
  virtual DataRate AverageSendRate() const = 0;
};

class EmulatedNetworkIncomingStats {
 public:
  virtual ~EmulatedNetworkIncomingStats() = default;

  // Total amount of packets received with or without destination.
  virtual int64_t PacketsReceived() const = 0;
  // Total amount of bytes in received packets.
  virtual DataSize BytesReceived() const = 0;
  // Returns the timestamped sizes of all received packets if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& ReceivedPacketsSizeCounter() const = 0;
  // Total amount of packets that were received, but no destination was found.
  virtual int64_t PacketsDropped() const = 0;
  // Total amount of bytes in dropped packets.
  virtual DataSize BytesDropped() const = 0;
  // Returns the timestamped sizes of all packets that were received,
  // but no destination was found if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& DroppedPacketsSizeCounter() const = 0;

  virtual DataSize FirstReceivedPacketSize() const = 0;

  // Returns time of the first packet received or infinite value if no packets
  // were received.
  virtual Timestamp FirstPacketReceivedTime() const = 0;

  // Returns time of the last packet received or infinite value if no packets
  // were received.
  virtual Timestamp LastPacketReceivedTime() const = 0;

  virtual DataRate AverageReceiveRate() const = 0;
};

class EmulatedNetworkStats {
 public:
  virtual ~EmulatedNetworkStats() = default;

  // List of IP addresses that were used to send data considered in this stats
  // object.
  virtual std::vector<rtc::IPAddress> LocalAddresses() const = 0;

  virtual int64_t PacketsSent() const = 0;

  virtual DataSize BytesSent() const = 0;
  // Returns the timestamped sizes of all sent packets if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& SentPacketsSizeCounter() const = 0;
  // Returns the timestamped duration between packet was received on
  // network interface and was dispatched to the network in microseconds if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& SentPacketsQueueWaitTimeUs() const = 0;

  virtual DataSize FirstSentPacketSize() const = 0;
  // Returns time of the first packet sent or infinite value if no packets were
  // sent.
  virtual Timestamp FirstPacketSentTime() const = 0;
  // Returns time of the last packet sent or infinite value if no packets were
  // sent.
  virtual Timestamp LastPacketSentTime() const = 0;

  virtual DataRate AverageSendRate() const = 0;
  // Total amount of packets received regardless of the destination address.
  virtual int64_t PacketsReceived() const = 0;
  // Total amount of bytes in received packets.
  virtual DataSize BytesReceived() const = 0;
  // Returns the timestamped sizes of all received packets if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& ReceivedPacketsSizeCounter() const = 0;
  // Total amount of packets that were received, but no destination was found.
  virtual int64_t PacketsDropped() const = 0;
  // Total amount of bytes in dropped packets.
  virtual DataSize BytesDropped() const = 0;
  // Returns counter with timestamped sizes of all packets that were received,
  // but no destination was found if
  // EmulatedEndpointConfig::stats_gatherming_mode was set to
  // StatsGatheringMode::kDebug; otherwise, the returned value will be empty.
  // Returned reference is valid until the next call to a non-const method.
  virtual const SamplesStatsCounter& DroppedPacketsSizeCounter() const = 0;

  virtual DataSize FirstReceivedPacketSize() const = 0;
  // Returns time of the first packet received or infinite value if no packets
  // were received.
  virtual Timestamp FirstPacketReceivedTime() const = 0;
  // Returns time of the last packet received or infinite value if no packets
  // were received.
  virtual Timestamp LastPacketReceivedTime() const = 0;

  virtual DataRate AverageReceiveRate() const = 0;

  virtual std::map<rtc::IPAddress,
                   std::unique_ptr<EmulatedNetworkOutgoingStats>>
  OutgoingStatsPerDestination() const = 0;

  virtual std::map<rtc::IPAddress,
                   std::unique_ptr<EmulatedNetworkIncomingStats>>
  IncomingStatsPerSource() const = 0;
};

// EmulatedEndpoint is an abstraction for network interface on device. Instances
// of this are created by NetworkEmulationManager::CreateEndpoint and
// thread safe.
class EmulatedEndpoint : public EmulatedNetworkReceiverInterface {
 public:
  // Send packet into network.
  // `from` will be used to set source address for the packet in destination
  // socket.
  // `to` will be used for routing verification and picking right socket by port
  // on destination endpoint.
  virtual void SendPacket(const rtc::SocketAddress& from,
                          const rtc::SocketAddress& to,
                          rtc::CopyOnWriteBuffer packet_data,
                          uint16_t application_overhead = 0) = 0;

  // Binds receiver to this endpoint to send and receive data.
  // `desired_port` is a port that should be used. If it is equal to 0,
  // endpoint will pick the first available port starting from
  // `kFirstEphemeralPort`.
  //
  // Returns the port, that should be used (it will be equals to desired, if
  // `desired_port` != 0 and is free or will be the one, selected by endpoint)
  // or absl::nullopt if desired_port in used. Also fails if there are no more
  // free ports to bind to.
  //
  // The Bind- and Unbind-methods must not be called from within a bound
  // receiver's OnPacketReceived method.
  virtual absl::optional<uint16_t> BindReceiver(
      uint16_t desired_port,
      EmulatedNetworkReceiverInterface* receiver) = 0;
  // Unbinds receiver from the specified port. Do nothing if no receiver was
  // bound before. After this method returns, no more packets can be delivered
  // to the receiver, and it is safe to destroy it.
  virtual void UnbindReceiver(uint16_t port) = 0;
  // Binds receiver that will accept all packets which arrived on any port
  // for which there are no bound receiver.
  virtual void BindDefaultReceiver(
      EmulatedNetworkReceiverInterface* receiver) = 0;
  // Unbinds default receiver. Do nothing if no default receiver was bound
  // before.
  virtual void UnbindDefaultReceiver() = 0;
  virtual rtc::IPAddress GetPeerLocalAddress() const = 0;

 private:
  // Ensure that there can be no other subclass than EmulatedEndpointImpl. This
  // means that it's always safe to downcast EmulatedEndpoint instances to
  // EmulatedEndpointImpl.
  friend class EmulatedEndpointImpl;
  EmulatedEndpoint() = default;
};

// Simulates a TCP connection, this roughly implements the Reno algorithm. In
// difference from TCP this only support sending messages with a fixed length,
// no streaming. This is useful to simulate signaling and cross traffic using
// message based protocols such as HTTP. It differs from UDP messages in that
// they are guranteed to be delivered eventually, even on lossy networks.
class TcpMessageRoute {
 public:
  // Sends a TCP message of the given `size` over the route, `on_received` is
  // called when the message has been delivered. Note that the connection
  // parameters are reset iff there's no currently pending message on the route.
  virtual void SendMessage(size_t size, std::function<void()> on_received) = 0;

 protected:
  ~TcpMessageRoute() = default;
};
}  // namespace webrtc

#endif  // API_TEST_NETWORK_EMULATION_NETWORK_EMULATION_INTERFACES_H_
