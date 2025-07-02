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

#include <memory>

#include "rtc_base/socket.h"
#include "rtc_base/socket_server.h"

namespace cricket {

std::unique_ptr<TestStunServer, std::function<void(TestStunServer*)>>
TestStunServer::Create(rtc::SocketServer* ss,
                       const rtc::SocketAddress& addr,
                       rtc::Thread& network_thread) {
  rtc::Socket* socket = ss->CreateSocket(addr.family(), SOCK_DGRAM);
  rtc::AsyncUDPSocket* udp_socket = rtc::AsyncUDPSocket::Create(socket, addr);
  TestStunServer* server = nullptr;
  network_thread.BlockingCall(
      [&]() { server = new TestStunServer(udp_socket, network_thread); });
  std::unique_ptr<TestStunServer, std::function<void(TestStunServer*)>> result(
      server, [&](TestStunServer* server) {
        network_thread.BlockingCall([server]() { delete server; });
      });
  return result;
}

void TestStunServer::OnBindingRequest(StunMessage* msg,
                                      const rtc::SocketAddress& remote_addr) {
  RTC_DCHECK_RUN_ON(&network_thread_);
  if (fake_stun_addr_.IsNil()) {
    StunServer::OnBindingRequest(msg, remote_addr);
  } else {
    StunMessage response(STUN_BINDING_RESPONSE, msg->transaction_id());
    GetStunBindResponse(msg, fake_stun_addr_, &response);
    SendResponse(response, remote_addr);
  }
}

}  // namespace cricket
