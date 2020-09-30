/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_SOCKET_H_
#define RTC_BASE_ASYNC_SOCKET_H_

#include <stddef.h>
#include <stdint.h>

#include "rtc_base/socket.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace rtc {

// TODO: Remove Socket and rename AsyncSocket to Socket.

// Provides the ability to perform socket I/O asynchronously.
class AsyncSocket : public Socket {
 public:
  AsyncSocket();
  ~AsyncSocket() override;

  AsyncSocket* Accept(SocketAddress* paddr) override = 0;

  // SignalReadEvent and SignalWriteEvent use multi_threaded_local to allow
  // access concurrently from different thread.
  // For example SignalReadEvent::connect will be called in AsyncUDPSocket ctor
  // but at the same time the SocketDispatcher maybe signaling the read event.
  // ready to read
  sigslot::signal1<AsyncSocket*, sigslot::multi_threaded_local> SignalReadEvent;
  // ready to write
  sigslot::signal1<AsyncSocket*, sigslot::multi_threaded_local>
      SignalWriteEvent;
  sigslot::signal1<AsyncSocket*> SignalConnectEvent;     // connected
  sigslot::signal2<AsyncSocket*, int> SignalCloseEvent;  // closed
};

class AsyncSocketAdapter : public AsyncSocket, public sigslot::has_slots<> {
 public:
  // The adapted socket may explicitly be null, and later assigned using Attach.
  // However, subclasses which support detached mode must override any methods
  // that will be called during the detached period (usually GetState()), to
  // avoid dereferencing a null pointer.
  explicit AsyncSocketAdapter(AsyncSocket* socket);
  ~AsyncSocketAdapter() override;
  void Attach(AsyncSocket* socket);
  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;
  int Bind(const SocketAddress& addr) override;
  int Connect(const SocketAddress& addr) override;
  int Send(const void* pv, size_t cb) override;
  int SendTo(const void* pv, size_t cb, const SocketAddress& addr) override;
  int Recv(void* pv, size_t cb, int64_t* timestamp) override;
  int RecvFrom(void* pv,
               size_t cb,
               SocketAddress* paddr,
               int64_t* timestamp) override;
  int Listen(int backlog) override;
  AsyncSocket* Accept(SocketAddress* paddr) override;
  int Close() override;
  int GetError() const override;
  void SetError(int error) override;
  ConnState GetState() const override;
  int GetOption(Option opt, int* value) override;
  int SetOption(Option opt, int value) override;

 protected:
  virtual void OnConnectEvent(AsyncSocket* socket);
  virtual void OnReadEvent(AsyncSocket* socket);
  virtual void OnWriteEvent(AsyncSocket* socket);
  virtual void OnCloseEvent(AsyncSocket* socket, int err);

  AsyncSocket* socket_;
};

}  // namespace rtc

#endif  // RTC_BASE_ASYNC_SOCKET_H_
