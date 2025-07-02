/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/socket_stream.h"

#include "rtc_base/checks.h"
#include "rtc_base/socket.h"

namespace rtc {

SocketStream::SocketStream(Socket* socket) : socket_(nullptr) {
  Attach(socket);
}

SocketStream::~SocketStream() {
  delete socket_;
}

void SocketStream::Attach(Socket* socket) {
  if (socket_)
    delete socket_;
  socket_ = socket;
  if (socket_) {
    socket_->SignalConnectEvent.connect(this, &SocketStream::OnConnectEvent);
    socket_->SignalReadEvent.connect(this, &SocketStream::OnReadEvent);
    socket_->SignalWriteEvent.connect(this, &SocketStream::OnWriteEvent);
    socket_->SignalCloseEvent.connect(this, &SocketStream::OnCloseEvent);
  }
}

Socket* SocketStream::Detach() {
  Socket* socket = socket_;
  if (socket_) {
    socket_->SignalConnectEvent.disconnect(this);
    socket_->SignalReadEvent.disconnect(this);
    socket_->SignalWriteEvent.disconnect(this);
    socket_->SignalCloseEvent.disconnect(this);
    socket_ = nullptr;
  }
  return socket;
}

StreamState SocketStream::GetState() const {
  RTC_DCHECK(socket_ != nullptr);
  switch (socket_->GetState()) {
    case Socket::CS_CONNECTED:
      return SS_OPEN;
    case Socket::CS_CONNECTING:
      return SS_OPENING;
    case Socket::CS_CLOSED:
    default:
      return SS_CLOSED;
  }
}

StreamResult SocketStream::Read(rtc::ArrayView<uint8_t> buffer,
                                size_t& read,
                                int& error) {
  RTC_DCHECK(socket_ != nullptr);
  int result = socket_->Recv(buffer.data(), buffer.size(), nullptr);
  if (result < 0) {
    if (socket_->IsBlocking())
      return SR_BLOCK;
    error = socket_->GetError();
    return SR_ERROR;
  }
  if ((result > 0) || (buffer.size() == 0)) {
    read = result;
    return SR_SUCCESS;
  }
  return SR_EOS;
}

StreamResult SocketStream::Write(rtc::ArrayView<const uint8_t> data,
                                 size_t& written,
                                 int& error) {
  RTC_DCHECK(socket_ != nullptr);
  int result = socket_->Send(data.data(), data.size());
  if (result < 0) {
    if (socket_->IsBlocking())
      return SR_BLOCK;
    error = socket_->GetError();
    return SR_ERROR;
  }
  written = result;
  return SR_SUCCESS;
}

void SocketStream::Close() {
  RTC_DCHECK(socket_ != nullptr);
  socket_->Close();
}

void SocketStream::OnConnectEvent(Socket* socket) {
  RTC_DCHECK(socket == socket_);
  SignalEvent(this, SE_OPEN | SE_READ | SE_WRITE, 0);
}

void SocketStream::OnReadEvent(Socket* socket) {
  RTC_DCHECK(socket == socket_);
  SignalEvent(this, SE_READ, 0);
}

void SocketStream::OnWriteEvent(Socket* socket) {
  RTC_DCHECK(socket == socket_);
  SignalEvent(this, SE_WRITE, 0);
}

void SocketStream::OnCloseEvent(Socket* socket, int err) {
  RTC_DCHECK(socket == socket_);
  SignalEvent(this, SE_CLOSE, err);
}

}  // namespace rtc
