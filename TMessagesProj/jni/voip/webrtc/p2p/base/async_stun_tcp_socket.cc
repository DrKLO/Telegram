/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/async_stun_tcp_socket.h"

#include <errno.h>
#include <stdint.h>
#include <string.h>

#include "api/transport/stun.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/time_utils.h"

namespace cricket {

static const size_t kMaxPacketSize = 64 * 1024;

typedef uint16_t PacketLength;
static const size_t kPacketLenSize = sizeof(PacketLength);
static const size_t kPacketLenOffset = 2;
static const size_t kBufSize = kMaxPacketSize + kStunHeaderSize;
static const size_t kTurnChannelDataHdrSize = 4;

inline bool IsStunMessage(uint16_t msg_type) {
  // The first two bits of a channel data message are 0b01.
  return (msg_type & 0xC000) ? false : true;
}

// AsyncStunTCPSocket
// Binds and connects `socket` and creates AsyncTCPSocket for
// it. Takes ownership of `socket`. Returns NULL if bind() or
// connect() fail (`socket` is destroyed in that case).
AsyncStunTCPSocket* AsyncStunTCPSocket::Create(
    rtc::Socket* socket,
    const rtc::SocketAddress& bind_address,
    const rtc::SocketAddress& remote_address) {
  return new AsyncStunTCPSocket(
      AsyncTCPSocketBase::ConnectSocket(socket, bind_address, remote_address));
}

AsyncStunTCPSocket::AsyncStunTCPSocket(rtc::Socket* socket)
    : rtc::AsyncTCPSocketBase(socket, kBufSize) {}

int AsyncStunTCPSocket::Send(const void* pv,
                             size_t cb,
                             const rtc::PacketOptions& options) {
  if (cb > kBufSize || cb < kPacketLenSize + kPacketLenOffset) {
    SetError(EMSGSIZE);
    return -1;
  }

  // If we are blocking on send, then silently drop this packet
  if (!IsOutBufferEmpty())
    return static_cast<int>(cb);

  int pad_bytes;
  size_t expected_pkt_len = GetExpectedLength(pv, cb, &pad_bytes);

  // Accepts only complete STUN/ChannelData packets.
  if (cb != expected_pkt_len)
    return -1;

  AppendToOutBuffer(pv, cb);

  RTC_DCHECK(pad_bytes < 4);
  char padding[4] = {0};
  AppendToOutBuffer(padding, pad_bytes);

  int res = FlushOutBuffer();
  if (res <= 0) {
    // drop packet if we made no progress
    ClearOutBuffer();
    return res;
  }

  rtc::SentPacket sent_packet(options.packet_id, rtc::TimeMillis());
  SignalSentPacket(this, sent_packet);

  // We claim to have sent the whole thing, even if we only sent partial
  return static_cast<int>(cb);
}

void AsyncStunTCPSocket::ProcessInput(char* data, size_t* len) {
  rtc::SocketAddress remote_addr(GetRemoteAddress());
  // STUN packet - First 4 bytes. Total header size is 20 bytes.
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |0 0|     STUN Message Type     |         Message Length        |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

  // TURN ChannelData
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |         Channel Number        |            Length             |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

  while (true) {
    // We need at least 4 bytes to read the STUN or ChannelData packet length.
    if (*len < kPacketLenOffset + kPacketLenSize)
      return;

    int pad_bytes;
    size_t expected_pkt_len = GetExpectedLength(data, *len, &pad_bytes);
    size_t actual_length = expected_pkt_len + pad_bytes;

    if (*len < actual_length) {
      return;
    }

    SignalReadPacket(this, data, expected_pkt_len, remote_addr,
                     rtc::TimeMicros());

    *len -= actual_length;
    if (*len > 0) {
      memmove(data, data + actual_length, *len);
    }
  }
}

size_t AsyncStunTCPSocket::GetExpectedLength(const void* data,
                                             size_t len,
                                             int* pad_bytes) {
  *pad_bytes = 0;
  PacketLength pkt_len =
      rtc::GetBE16(static_cast<const char*>(data) + kPacketLenOffset);
  size_t expected_pkt_len;
  uint16_t msg_type = rtc::GetBE16(data);
  if (IsStunMessage(msg_type)) {
    // STUN message.
    expected_pkt_len = kStunHeaderSize + pkt_len;
  } else {
    // TURN ChannelData message.
    expected_pkt_len = kTurnChannelDataHdrSize + pkt_len;
    // From RFC 5766 section 11.5
    // Over TCP and TLS-over-TCP, the ChannelData message MUST be padded to
    // a multiple of four bytes in order to ensure the alignment of
    // subsequent messages.  The padding is not reflected in the length
    // field of the ChannelData message, so the actual size of a ChannelData
    // message (including padding) is (4 + Length) rounded up to the nearest
    // multiple of 4.  Over UDP, the padding is not required but MAY be
    // included.
    if (expected_pkt_len % 4)
      *pad_bytes = 4 - (expected_pkt_len % 4);
  }
  return expected_pkt_len;
}

}  // namespace cricket
