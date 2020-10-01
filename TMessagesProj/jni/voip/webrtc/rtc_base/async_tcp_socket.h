/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_TCP_SOCKET_H_
#define RTC_BASE_ASYNC_TCP_SOCKET_H_

#include <stddef.h>

#include <memory>

#include "rtc_base/async_packet_socket.h"
#include "rtc_base/async_socket.h"
#include "rtc_base/buffer.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_address.h"

namespace rtc {

// Simulates UDP semantics over TCP.  Send and Recv packet sizes
// are preserved, and drops packets silently on Send, rather than
// buffer them in user space.
class AsyncTCPSocketBase : public AsyncPacketSocket {
 public:
  AsyncTCPSocketBase(AsyncSocket* socket, bool listen, size_t max_packet_size);
  ~AsyncTCPSocketBase() override;

  // Pure virtual methods to send and recv data.
  int Send(const void* pv,
           size_t cb,
           const rtc::PacketOptions& options) override = 0;
  virtual void ProcessInput(char* data, size_t* len) = 0;
  // Signals incoming connection.
  virtual void HandleIncomingConnection(AsyncSocket* socket) = 0;

  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;
  int SendTo(const void* pv,
             size_t cb,
             const SocketAddress& addr,
             const rtc::PacketOptions& options) override;
  int Close() override;

  State GetState() const override;
  int GetOption(Socket::Option opt, int* value) override;
  int SetOption(Socket::Option opt, int value) override;
  int GetError() const override;
  void SetError(int error) override;

 protected:
  // Binds and connects |socket| and creates AsyncTCPSocket for
  // it. Takes ownership of |socket|. Returns null if bind() or
  // connect() fail (|socket| is destroyed in that case).
  static AsyncSocket* ConnectSocket(AsyncSocket* socket,
                                    const SocketAddress& bind_address,
                                    const SocketAddress& remote_address);
  int FlushOutBuffer();
  // Add data to |outbuf_|.
  void AppendToOutBuffer(const void* pv, size_t cb);

  // Helper methods for |outpos_|.
  bool IsOutBufferEmpty() const { return outbuf_.size() == 0; }
  void ClearOutBuffer() { outbuf_.Clear(); }

 private:
  // Called by the underlying socket
  void OnConnectEvent(AsyncSocket* socket);
  void OnReadEvent(AsyncSocket* socket);
  void OnWriteEvent(AsyncSocket* socket);
  void OnCloseEvent(AsyncSocket* socket, int error);

  std::unique_ptr<AsyncSocket> socket_;
  bool listen_;
  Buffer inbuf_;
  Buffer outbuf_;
  size_t max_insize_;
  size_t max_outsize_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncTCPSocketBase);
};

class AsyncTCPSocket : public AsyncTCPSocketBase {
 public:
  // Binds and connects |socket| and creates AsyncTCPSocket for
  // it. Takes ownership of |socket|. Returns null if bind() or
  // connect() fail (|socket| is destroyed in that case).
  static AsyncTCPSocket* Create(AsyncSocket* socket,
                                const SocketAddress& bind_address,
                                const SocketAddress& remote_address);
  AsyncTCPSocket(AsyncSocket* socket, bool listen);
  ~AsyncTCPSocket() override {}

  int Send(const void* pv,
           size_t cb,
           const rtc::PacketOptions& options) override;
  void ProcessInput(char* data, size_t* len) override;
  void HandleIncomingConnection(AsyncSocket* socket) override;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncTCPSocket);
};

}  // namespace rtc

#endif  // RTC_BASE_ASYNC_TCP_SOCKET_H_
