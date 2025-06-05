/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_TEST_STUN_SERVER_H_
#define P2P_BASE_TEST_STUN_SERVER_H_

#include <memory>

#include "api/transport/stun.h"
#include "p2p/base/stun_server.h"
#include "rtc_base/async_udp_socket.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/thread.h"

namespace cricket {

// A test STUN server. Useful for unit tests.
class TestStunServer : StunServer {
 public:
  using StunServerPtr =
      std::unique_ptr<TestStunServer, std::function<void(TestStunServer*)>>;
  static StunServerPtr Create(rtc::SocketServer* ss,
                              const rtc::SocketAddress& addr,
                              rtc::Thread& network_thread);

  // Set a fake STUN address to return to the client.
  void set_fake_stun_addr(const rtc::SocketAddress& addr) {
    fake_stun_addr_ = addr;
  }

 private:
  static void DeleteOnNetworkThread(TestStunServer* server);

  TestStunServer(rtc::AsyncUDPSocket* socket, rtc::Thread& network_thread)
      : StunServer(socket), network_thread_(network_thread) {}

  void OnBindingRequest(StunMessage* msg,
                        const rtc::SocketAddress& remote_addr) override;

 private:
  rtc::SocketAddress fake_stun_addr_;
  rtc::Thread& network_thread_;
};

}  // namespace cricket

#endif  // P2P_BASE_TEST_STUN_SERVER_H_
