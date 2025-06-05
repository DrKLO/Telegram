/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/server_socket_adapters.h"

#include <string>

#include "rtc_base/byte_buffer.h"

namespace rtc {

AsyncProxyServerSocket::AsyncProxyServerSocket(Socket* socket,
                                               size_t buffer_size)
    : BufferedReadAdapter(socket, buffer_size) {}

AsyncProxyServerSocket::~AsyncProxyServerSocket() = default;

AsyncSSLServerSocket::AsyncSSLServerSocket(Socket* socket)
    : BufferedReadAdapter(socket, 1024) {
  BufferInput(true);
}

void AsyncSSLServerSocket::ProcessInput(char* data, size_t* len) {
  // We only accept client hello messages.
  const ArrayView<const uint8_t> client_hello =
      AsyncSSLSocket::SslClientHello();
  if (*len < client_hello.size()) {
    return;
  }

  if (memcmp(client_hello.data(), data, client_hello.size()) != 0) {
    Close();
    SignalCloseEvent(this, 0);
    return;
  }

  *len -= client_hello.size();

  // Clients should not send more data until the handshake is completed.
  RTC_DCHECK(*len == 0);

  const ArrayView<const uint8_t> server_hello =
      AsyncSSLSocket::SslServerHello();
  // Send a server hello back to the client.
  DirectSend(server_hello.data(), server_hello.size());

  // Handshake completed for us, redirect input to our parent.
  BufferInput(false);
}

}  // namespace rtc
