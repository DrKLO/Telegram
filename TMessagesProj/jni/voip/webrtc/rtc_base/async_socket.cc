/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/async_socket.h"

#include "absl/memory/memory.h"
#include "rtc_base/checks.h"

namespace rtc {

AsyncSocketAdapter::AsyncSocketAdapter(Socket* socket)
    : socket_(absl::WrapUnique(socket)) {
  RTC_DCHECK(socket_);
  socket_->SignalConnectEvent.connect(this,
                                      &AsyncSocketAdapter::OnConnectEvent);
  socket_->SignalReadEvent.connect(this, &AsyncSocketAdapter::OnReadEvent);
  socket_->SignalWriteEvent.connect(this, &AsyncSocketAdapter::OnWriteEvent);
  socket_->SignalCloseEvent.connect(this, &AsyncSocketAdapter::OnCloseEvent);
}

SocketAddress AsyncSocketAdapter::GetLocalAddress() const {
  return socket_->GetLocalAddress();
}

SocketAddress AsyncSocketAdapter::GetRemoteAddress() const {
  return socket_->GetRemoteAddress();
}

int AsyncSocketAdapter::Bind(const SocketAddress& addr) {
  return socket_->Bind(addr);
}

int AsyncSocketAdapter::Connect(const SocketAddress& addr) {
  return socket_->Connect(addr);
}

int AsyncSocketAdapter::Send(const void* pv, size_t cb) {
  return socket_->Send(pv, cb);
}

int AsyncSocketAdapter::SendTo(const void* pv,
                               size_t cb,
                               const SocketAddress& addr) {
  return socket_->SendTo(pv, cb, addr);
}

int AsyncSocketAdapter::Recv(void* pv, size_t cb, int64_t* timestamp) {
  return socket_->Recv(pv, cb, timestamp);
}

int AsyncSocketAdapter::RecvFrom(void* pv,
                                 size_t cb,
                                 SocketAddress* paddr,
                                 int64_t* timestamp) {
  return socket_->RecvFrom(pv, cb, paddr, timestamp);
}

int AsyncSocketAdapter::Listen(int backlog) {
  return socket_->Listen(backlog);
}

Socket* AsyncSocketAdapter::Accept(SocketAddress* paddr) {
  return socket_->Accept(paddr);
}

int AsyncSocketAdapter::Close() {
  return socket_->Close();
}

int AsyncSocketAdapter::GetError() const {
  return socket_->GetError();
}

void AsyncSocketAdapter::SetError(int error) {
  return socket_->SetError(error);
}

Socket::ConnState AsyncSocketAdapter::GetState() const {
  return socket_->GetState();
}

int AsyncSocketAdapter::GetOption(Option opt, int* value) {
  return socket_->GetOption(opt, value);
}

int AsyncSocketAdapter::SetOption(Option opt, int value) {
  return socket_->SetOption(opt, value);
}

void AsyncSocketAdapter::OnConnectEvent(Socket* socket) {
  SignalConnectEvent(this);
}

void AsyncSocketAdapter::OnReadEvent(Socket* socket) {
  SignalReadEvent(this);
}

void AsyncSocketAdapter::OnWriteEvent(Socket* socket) {
  SignalWriteEvent(this);
}

void AsyncSocketAdapter::OnCloseEvent(Socket* socket, int err) {
  SignalCloseEvent(this, err);
}

}  // namespace rtc
