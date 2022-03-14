/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_PACKET_TRANSPORT_INTERNAL_H_
#define P2P_BASE_PACKET_TRANSPORT_INTERNAL_H_

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "p2p/base/port.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/network_route.h"
#include "rtc_base/socket.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace rtc {
struct PacketOptions;
struct SentPacket;

class RTC_EXPORT PacketTransportInternal : public sigslot::has_slots<> {
 public:
  virtual const std::string& transport_name() const = 0;

  // The transport has been established.
  virtual bool writable() const = 0;

  // The transport has received a packet in the last X milliseconds, here X is
  // configured by each implementation.
  virtual bool receiving() const = 0;

  // Attempts to send the given packet.
  // The return value is < 0 on failure. The return value in failure case is not
  // descriptive. Depending on failure cause and implementation details
  // GetError() returns an descriptive errno.h error value.
  // This mimics posix socket send() or sendto() behavior.
  // TODO(johan): Reliable, meaningful, consistent error codes for all
  // implementations would be nice.
  // TODO(johan): Remove the default argument once channel code is updated.
  virtual int SendPacket(const char* data,
                         size_t len,
                         const rtc::PacketOptions& options,
                         int flags = 0) = 0;

  // Sets a socket option. Note that not all options are
  // supported by all transport types.
  virtual int SetOption(rtc::Socket::Option opt, int value) = 0;

  // TODO(pthatcher): Once Chrome's MockPacketTransportInterface implements
  // this, remove the default implementation.
  virtual bool GetOption(rtc::Socket::Option opt, int* value);

  // Returns the most recent error that occurred on this channel.
  virtual int GetError() = 0;

  // Returns the current network route with transport overhead.
  // TODO(zhihuang): Make it pure virtual once the Chrome/remoting is updated.
  virtual absl::optional<NetworkRoute> network_route() const;

  // Emitted when the writable state, represented by `writable()`, changes.
  sigslot::signal1<PacketTransportInternal*> SignalWritableState;

  //  Emitted when the PacketTransportInternal is ready to send packets. "Ready
  //  to send" is more sensitive than the writable state; a transport may be
  //  writable, but temporarily not able to send packets. For example, the
  //  underlying transport's socket buffer may be full, as indicated by
  //  SendPacket's return code and/or GetError.
  sigslot::signal1<PacketTransportInternal*> SignalReadyToSend;

  // Emitted when receiving state changes to true.
  sigslot::signal1<PacketTransportInternal*> SignalReceivingState;

  // Signalled each time a packet is received on this channel.
  sigslot::signal5<PacketTransportInternal*,
                   const char*,
                   size_t,
                   // TODO(bugs.webrtc.org/9584): Change to passing the int64_t
                   // timestamp by value.
                   const int64_t&,
                   int>
      SignalReadPacket;

  // Signalled each time a packet is sent on this channel.
  sigslot::signal2<PacketTransportInternal*, const rtc::SentPacket&>
      SignalSentPacket;

  // Signalled when the current network route has changed.
  sigslot::signal1<absl::optional<rtc::NetworkRoute>> SignalNetworkRouteChanged;

  // Signalled when the transport is closed.
  sigslot::signal1<PacketTransportInternal*> SignalClosed;

 protected:
  PacketTransportInternal();
  ~PacketTransportInternal() override;
};

}  // namespace rtc

#endif  // P2P_BASE_PACKET_TRANSPORT_INTERNAL_H_
