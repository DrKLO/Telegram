/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SOCKET_ADAPTERS_H_
#define RTC_BASE_SOCKET_ADAPTERS_H_

#include <string>

#include "api/array_view.h"
#include "rtc_base/async_socket.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/crypt_string.h"

namespace rtc {

struct HttpAuthContext;
class ByteBufferReader;
class ByteBufferWriter;

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that can buffer and process data internally,
// as in the case of connecting to a proxy, where you must speak the proxy
// protocol before commencing normal socket behavior.
class BufferedReadAdapter : public AsyncSocketAdapter {
 public:
  BufferedReadAdapter(Socket* socket, size_t buffer_size);
  ~BufferedReadAdapter() override;

  int Send(const void* pv, size_t cb) override;
  int Recv(void* pv, size_t cb, int64_t* timestamp) override;

 protected:
  int DirectSend(const void* pv, size_t cb) {
    return AsyncSocketAdapter::Send(pv, cb);
  }

  void BufferInput(bool on = true);
  virtual void ProcessInput(char* data, size_t* len) = 0;

  void OnReadEvent(Socket* socket) override;

 private:
  char* buffer_;
  size_t buffer_size_, data_len_;
  bool buffering_;
  RTC_DISALLOW_COPY_AND_ASSIGN(BufferedReadAdapter);
};

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that performs the client side of a
// fake SSL handshake. Used for "ssltcp" P2P functionality.
class AsyncSSLSocket : public BufferedReadAdapter {
 public:
  static ArrayView<const uint8_t> SslClientHello();
  static ArrayView<const uint8_t> SslServerHello();

  explicit AsyncSSLSocket(Socket* socket);

  int Connect(const SocketAddress& addr) override;

 protected:
  void OnConnectEvent(Socket* socket) override;
  void ProcessInput(char* data, size_t* len) override;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncSSLSocket);
};

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that speaks the HTTP/S proxy protocol.
class AsyncHttpsProxySocket : public BufferedReadAdapter {
 public:
  AsyncHttpsProxySocket(Socket* socket,
                        const std::string& user_agent,
                        const SocketAddress& proxy,
                        const std::string& username,
                        const CryptString& password);
  ~AsyncHttpsProxySocket() override;

  // If connect is forced, the adapter will always issue an HTTP CONNECT to the
  // target address.  Otherwise, it will connect only if the destination port
  // is not port 80.
  void SetForceConnect(bool force) { force_connect_ = force; }

  int Connect(const SocketAddress& addr) override;
  SocketAddress GetRemoteAddress() const override;
  int Close() override;
  ConnState GetState() const override;

 protected:
  void OnConnectEvent(Socket* socket) override;
  void OnCloseEvent(Socket* socket, int err) override;
  void ProcessInput(char* data, size_t* len) override;

  bool ShouldIssueConnect() const;
  void SendRequest();
  void ProcessLine(char* data, size_t len);
  void EndResponse();
  void Error(int error);

 private:
  SocketAddress proxy_, dest_;
  std::string agent_, user_, headers_;
  CryptString pass_;
  bool force_connect_;
  size_t content_length_;
  int defer_error_;
  bool expect_close_;
  enum ProxyState {
    PS_INIT,
    PS_LEADER,
    PS_AUTHENTICATE,
    PS_SKIP_HEADERS,
    PS_ERROR_HEADERS,
    PS_TUNNEL_HEADERS,
    PS_SKIP_BODY,
    PS_TUNNEL,
    PS_WAIT_CLOSE,
    PS_ERROR
  } state_;
  HttpAuthContext* context_;
  std::string unknown_mechanisms_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncHttpsProxySocket);
};

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that speaks the SOCKS proxy protocol.
class AsyncSocksProxySocket : public BufferedReadAdapter {
 public:
  AsyncSocksProxySocket(Socket* socket,
                        const SocketAddress& proxy,
                        const std::string& username,
                        const CryptString& password);
  ~AsyncSocksProxySocket() override;

  int Connect(const SocketAddress& addr) override;
  SocketAddress GetRemoteAddress() const override;
  int Close() override;
  ConnState GetState() const override;

 protected:
  void OnConnectEvent(Socket* socket) override;
  void ProcessInput(char* data, size_t* len) override;

  void SendHello();
  void SendConnect();
  void SendAuth();
  void Error(int error);

 private:
  enum State { SS_INIT, SS_HELLO, SS_AUTH, SS_CONNECT, SS_TUNNEL, SS_ERROR };
  State state_;
  SocketAddress proxy_, dest_;
  std::string user_;
  CryptString pass_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncSocksProxySocket);
};

}  // namespace rtc

#endif  // RTC_BASE_SOCKET_ADAPTERS_H_
