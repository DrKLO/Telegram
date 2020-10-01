/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TEST_ECHO_SERVER_H_
#define RTC_BASE_TEST_ECHO_SERVER_H_

#include <stddef.h>
#include <stdint.h>

#include <list>
#include <memory>

#include "absl/algorithm/container.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/async_socket.h"
#include "rtc_base/async_tcp_socket.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"

namespace rtc {

// A test echo server, echoes back any packets sent to it.
// Useful for unit tests.
class TestEchoServer : public sigslot::has_slots<> {
 public:
  TestEchoServer(Thread* thread, const SocketAddress& addr);
  ~TestEchoServer() override;

  SocketAddress address() const { return server_socket_->GetLocalAddress(); }

 private:
  void OnAccept(AsyncSocket* socket) {
    AsyncSocket* raw_socket = socket->Accept(nullptr);
    if (raw_socket) {
      AsyncTCPSocket* packet_socket = new AsyncTCPSocket(raw_socket, false);
      packet_socket->SignalReadPacket.connect(this, &TestEchoServer::OnPacket);
      packet_socket->SignalClose.connect(this, &TestEchoServer::OnClose);
      client_sockets_.push_back(packet_socket);
    }
  }
  void OnPacket(AsyncPacketSocket* socket,
                const char* buf,
                size_t size,
                const SocketAddress& remote_addr,
                const int64_t& /* packet_time_us */) {
    rtc::PacketOptions options;
    socket->Send(buf, size, options);
  }
  void OnClose(AsyncPacketSocket* socket, int err) {
    ClientList::iterator it = absl::c_find(client_sockets_, socket);
    client_sockets_.erase(it);
    Thread::Current()->Dispose(socket);
  }

  typedef std::list<AsyncTCPSocket*> ClientList;
  std::unique_ptr<AsyncSocket> server_socket_;
  ClientList client_sockets_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TestEchoServer);
};

}  // namespace rtc

#endif  // RTC_BASE_TEST_ECHO_SERVER_H_
