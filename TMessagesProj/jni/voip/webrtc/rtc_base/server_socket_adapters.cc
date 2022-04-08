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

AsyncSocksProxyServerSocket::AsyncSocksProxyServerSocket(Socket* socket)
    : AsyncProxyServerSocket(socket, kBufferSize), state_(SS_HELLO) {
  BufferInput(true);
}

void AsyncSocksProxyServerSocket::ProcessInput(char* data, size_t* len) {
  RTC_DCHECK(state_ < SS_CONNECT_PENDING);

  ByteBufferReader response(data, *len);
  if (state_ == SS_HELLO) {
    HandleHello(&response);
  } else if (state_ == SS_AUTH) {
    HandleAuth(&response);
  } else if (state_ == SS_CONNECT) {
    HandleConnect(&response);
  }

  // Consume parsed data
  *len = response.Length();
  memmove(data, response.Data(), *len);
}

void AsyncSocksProxyServerSocket::DirectSend(const ByteBufferWriter& buf) {
  BufferedReadAdapter::DirectSend(buf.Data(), buf.Length());
}

void AsyncSocksProxyServerSocket::HandleHello(ByteBufferReader* request) {
  uint8_t ver, num_methods;
  if (!request->ReadUInt8(&ver) || !request->ReadUInt8(&num_methods)) {
    Error(0);
    return;
  }

  if (ver != 5) {
    Error(0);
    return;
  }

  // Handle either no-auth (0) or user/pass auth (2)
  uint8_t method = 0xFF;
  if (num_methods > 0 && !request->ReadUInt8(&method)) {
    Error(0);
    return;
  }

  SendHelloReply(method);
  if (method == 0) {
    state_ = SS_CONNECT;
  } else if (method == 2) {
    state_ = SS_AUTH;
  } else {
    state_ = SS_ERROR;
  }
}

void AsyncSocksProxyServerSocket::SendHelloReply(uint8_t method) {
  ByteBufferWriter response;
  response.WriteUInt8(5);       // Socks Version
  response.WriteUInt8(method);  // Auth method
  DirectSend(response);
}

void AsyncSocksProxyServerSocket::HandleAuth(ByteBufferReader* request) {
  uint8_t ver, user_len, pass_len;
  std::string user, pass;
  if (!request->ReadUInt8(&ver) || !request->ReadUInt8(&user_len) ||
      !request->ReadString(&user, user_len) || !request->ReadUInt8(&pass_len) ||
      !request->ReadString(&pass, pass_len)) {
    Error(0);
    return;
  }

  SendAuthReply(0);
  state_ = SS_CONNECT;
}

void AsyncSocksProxyServerSocket::SendAuthReply(uint8_t result) {
  ByteBufferWriter response;
  response.WriteUInt8(1);  // Negotiation Version
  response.WriteUInt8(result);
  DirectSend(response);
}

void AsyncSocksProxyServerSocket::HandleConnect(ByteBufferReader* request) {
  uint8_t ver, command, reserved, addr_type;
  uint32_t ip;
  uint16_t port;
  if (!request->ReadUInt8(&ver) || !request->ReadUInt8(&command) ||
      !request->ReadUInt8(&reserved) || !request->ReadUInt8(&addr_type) ||
      !request->ReadUInt32(&ip) || !request->ReadUInt16(&port)) {
    Error(0);
    return;
  }

  if (ver != 5 || command != 1 || reserved != 0 || addr_type != 1) {
    Error(0);
    return;
  }

  SignalConnectRequest(this, SocketAddress(ip, port));
  state_ = SS_CONNECT_PENDING;
}

void AsyncSocksProxyServerSocket::SendConnectResult(int result,
                                                    const SocketAddress& addr) {
  if (state_ != SS_CONNECT_PENDING)
    return;

  ByteBufferWriter response;
  response.WriteUInt8(5);              // Socks version
  response.WriteUInt8((result != 0));  // 0x01 is generic error
  response.WriteUInt8(0);              // reserved
  response.WriteUInt8(1);              // IPv4 address
  response.WriteUInt32(addr.ip());
  response.WriteUInt16(addr.port());
  DirectSend(response);
  BufferInput(false);
  state_ = SS_TUNNEL;
}

void AsyncSocksProxyServerSocket::Error(int error) {
  state_ = SS_ERROR;
  BufferInput(false);
  Close();
  SetError(SOCKET_EACCES);
  SignalCloseEvent(this, error);
}

}  // namespace rtc
