/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_ASYNC_STUN_TCP_SOCKET_H_
#define P2P_BASE_ASYNC_STUN_TCP_SOCKET_H_

#include <stddef.h>

#include "rtc_base/async_packet_socket.h"
#include "rtc_base/async_tcp_socket.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_address.h"

namespace cricket {

class AsyncStunTCPSocket : public rtc::AsyncTCPSocketBase {
 public:
  // Binds and connects `socket` and creates AsyncTCPSocket for
  // it. Takes ownership of `socket`. Returns NULL if bind() or
  // connect() fail (`socket` is destroyed in that case).
  static AsyncStunTCPSocket* Create(rtc::Socket* socket,
                                    const rtc::SocketAddress& bind_address,
                                    const rtc::SocketAddress& remote_address);

  explicit AsyncStunTCPSocket(rtc::Socket* socket);

  int Send(const void* pv,
           size_t cb,
           const rtc::PacketOptions& options) override;
  void ProcessInput(char* data, size_t* len) override;

 private:
  // This method returns the message hdr + length written in the header.
  // This method also returns the number of padding bytes needed/added to the
  // turn message. `pad_bytes` should be used only when `is_turn` is true.
  size_t GetExpectedLength(const void* data, size_t len, int* pad_bytes);

  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncStunTCPSocket);
};

}  // namespace cricket

#endif  // P2P_BASE_ASYNC_STUN_TCP_SOCKET_H_
