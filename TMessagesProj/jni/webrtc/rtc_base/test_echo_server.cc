/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/test_echo_server.h"

#include "rtc_base/socket_server.h"

namespace rtc {

TestEchoServer::TestEchoServer(Thread* thread, const SocketAddress& addr)
    : server_socket_(thread->socketserver()->CreateAsyncSocket(addr.family(),
                                                               SOCK_STREAM)) {
  server_socket_->Bind(addr);
  server_socket_->Listen(5);
  server_socket_->SignalReadEvent.connect(this, &TestEchoServer::OnAccept);
}

TestEchoServer::~TestEchoServer() {
  for (ClientList::iterator it = client_sockets_.begin();
       it != client_sockets_.end(); ++it) {
    delete *it;
  }
}

}  // namespace rtc
