/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_PACKET_SOCKET_H_
#define RTC_BASE_ASYNC_PACKET_SOCKET_H_

#include <vector>

#include "rtc_base/constructor_magic.h"
#include "rtc_base/dscp.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/socket.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/time_utils.h"

namespace rtc {

// This structure holds the info needed to update the packet send time header
// extension, including the information needed to update the authentication tag
// after changing the value.
struct PacketTimeUpdateParams {
  PacketTimeUpdateParams();
  PacketTimeUpdateParams(const PacketTimeUpdateParams& other);
  ~PacketTimeUpdateParams();

  int rtp_sendtime_extension_id = -1;  // extension header id present in packet.
  std::vector<char> srtp_auth_key;     // Authentication key.
  int srtp_auth_tag_len = -1;          // Authentication tag length.
  int64_t srtp_packet_index = -1;  // Required for Rtp Packet authentication.
};

// This structure holds meta information for the packet which is about to send
// over network.
struct RTC_EXPORT PacketOptions {
  PacketOptions();
  explicit PacketOptions(DiffServCodePoint dscp);
  PacketOptions(const PacketOptions& other);
  ~PacketOptions();

  DiffServCodePoint dscp = DSCP_NO_CHANGE;
  // When used with RTP packets (for example, webrtc::PacketOptions), the value
  // should be 16 bits. A value of -1 represents "not set".
  int64_t packet_id = -1;
  PacketTimeUpdateParams packet_time_params;
  // PacketInfo is passed to SentPacket when signaling this packet is sent.
  PacketInfo info_signaled_after_sent;
};

// Provides the ability to receive packets asynchronously. Sends are not
// buffered since it is acceptable to drop packets under high load.
class RTC_EXPORT AsyncPacketSocket : public sigslot::has_slots<> {
 public:
  enum State {
    STATE_CLOSED,
    STATE_BINDING,
    STATE_BOUND,
    STATE_CONNECTING,
    STATE_CONNECTED
  };

  AsyncPacketSocket();
  ~AsyncPacketSocket() override;

  // Returns current local address. Address may be set to null if the
  // socket is not bound yet (GetState() returns STATE_BINDING).
  virtual SocketAddress GetLocalAddress() const = 0;

  // Returns remote address. Returns zeroes if this is not a client TCP socket.
  virtual SocketAddress GetRemoteAddress() const = 0;

  // Send a packet.
  virtual int Send(const void* pv, size_t cb, const PacketOptions& options) = 0;
  virtual int SendTo(const void* pv,
                     size_t cb,
                     const SocketAddress& addr,
                     const PacketOptions& options) = 0;

  // Close the socket.
  virtual int Close() = 0;

  // Returns current state of the socket.
  virtual State GetState() const = 0;

  // Get/set options.
  virtual int GetOption(Socket::Option opt, int* value) = 0;
  virtual int SetOption(Socket::Option opt, int value) = 0;

  // Get/Set current error.
  // TODO: Remove SetError().
  virtual int GetError() const = 0;
  virtual void SetError(int error) = 0;

  // Emitted each time a packet is read. Used only for UDP and
  // connected TCP sockets.
  sigslot::signal5<AsyncPacketSocket*,
                   const char*,
                   size_t,
                   const SocketAddress&,
                   // TODO(bugs.webrtc.org/9584): Change to passing the int64_t
                   // timestamp by value.
                   const int64_t&>
      SignalReadPacket;

  // Emitted each time a packet is sent.
  sigslot::signal2<AsyncPacketSocket*, const SentPacket&> SignalSentPacket;

  // Emitted when the socket is currently able to send.
  sigslot::signal1<AsyncPacketSocket*> SignalReadyToSend;

  // Emitted after address for the socket is allocated, i.e. binding
  // is finished. State of the socket is changed from BINDING to BOUND
  // (for UDP and server TCP sockets) or CONNECTING (for client TCP
  // sockets).
  sigslot::signal2<AsyncPacketSocket*, const SocketAddress&> SignalAddressReady;

  // Emitted for client TCP sockets when state is changed from
  // CONNECTING to CONNECTED.
  sigslot::signal1<AsyncPacketSocket*> SignalConnect;

  // Emitted for client TCP sockets when state is changed from
  // CONNECTED to CLOSED.
  sigslot::signal2<AsyncPacketSocket*, int> SignalClose;

  // Used only for listening TCP sockets.
  sigslot::signal2<AsyncPacketSocket*, AsyncPacketSocket*> SignalNewConnection;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncPacketSocket);
};

void CopySocketInformationToPacketInfo(size_t packet_size_bytes,
                                       const AsyncPacketSocket& socket_from,
                                       bool is_connectionless,
                                       rtc::PacketInfo* info);

}  // namespace rtc

#endif  // RTC_BASE_ASYNC_PACKET_SOCKET_H_
