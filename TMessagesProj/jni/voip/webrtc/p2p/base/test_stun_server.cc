/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/test_stun_server.h"

#include "rtc_base/async_socket.h"
#include "rtc_base/socket_server.h"

namespace cricket {

TestStunServer* TestStunServer::Create(rtc::Thread* thread,
                                       const rtc::SocketAddress& addr) {
  rtc::AsyncSocket* socket =
      thread->socketserver()->CreateAsyncSocket(addr.family(), SOCK_DGRAM);
  rtc::AsyncUDPSocket* udp_socket = rtc::AsyncUDPSocket::Create(socket, addr);

  return new TestStunServer(udp_socket);
}

void TestStunServer::OnBindingRequest(StunMessage* msg,
                                      const rtc::SocketAddress& remote_addr) {
  if (fake_stun_addr_.IsNil()) {
    StunServer::OnBindingRequest(msg, remote_addr);
  } else {
    StunMessage response;
    GetStunBindResponse(msg, fake_stun_addr_, &response);
    SendResponse(response, remote_addr);
  }
}

}  // namespace cricket
