/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/proxy_server.h"

#include <stddef.h>

#include <memory>
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/socket_factory.h"

namespace rtc {

// ProxyServer
ProxyServer::ProxyServer(SocketFactory* int_factory,
                         const SocketAddress& int_addr,
                         SocketFactory* ext_factory,
                         const SocketAddress& ext_ip)
    : ext_factory_(ext_factory),
      ext_ip_(ext_ip.ipaddr(), 0),  // strip off port
      server_socket_(
          int_factory->CreateSocket(int_addr.family(), SOCK_STREAM)) {
  RTC_DCHECK(server_socket_.get() != nullptr);
  RTC_DCHECK(int_addr.family() == AF_INET || int_addr.family() == AF_INET6);
  server_socket_->Bind(int_addr);
  server_socket_->Listen(5);
  server_socket_->SignalReadEvent.connect(this, &ProxyServer::OnAcceptEvent);
}

ProxyServer::~ProxyServer() = default;

SocketAddress ProxyServer::GetServerAddress() {
  return server_socket_->GetLocalAddress();
}

void ProxyServer::OnAcceptEvent(Socket* socket) {
  RTC_DCHECK(socket);
  RTC_DCHECK_EQ(socket, server_socket_.get());
  Socket* int_socket = socket->Accept(nullptr);
  AsyncProxyServerSocket* wrapped_socket = WrapSocket(int_socket);
  Socket* ext_socket =
      ext_factory_->CreateSocket(ext_ip_.family(), SOCK_STREAM);
  if (ext_socket) {
    ext_socket->Bind(ext_ip_);
    bindings_.emplace_back(
        std::make_unique<ProxyBinding>(wrapped_socket, ext_socket));
  } else {
    RTC_LOG(LS_ERROR)
        << "Unable to create external socket on proxy accept event";
  }
}

// ProxyBinding
ProxyBinding::ProxyBinding(AsyncProxyServerSocket* int_socket,
                           Socket* ext_socket)
    : int_socket_(int_socket),
      ext_socket_(ext_socket),
      connected_(false),
      out_buffer_(kBufferSize),
      in_buffer_(kBufferSize) {
  int_socket_->SignalConnectRequest.connect(this,
                                            &ProxyBinding::OnConnectRequest);
  int_socket_->SignalReadEvent.connect(this, &ProxyBinding::OnInternalRead);
  int_socket_->SignalWriteEvent.connect(this, &ProxyBinding::OnInternalWrite);
  int_socket_->SignalCloseEvent.connect(this, &ProxyBinding::OnInternalClose);
  ext_socket_->SignalConnectEvent.connect(this,
                                          &ProxyBinding::OnExternalConnect);
  ext_socket_->SignalReadEvent.connect(this, &ProxyBinding::OnExternalRead);
  ext_socket_->SignalWriteEvent.connect(this, &ProxyBinding::OnExternalWrite);
  ext_socket_->SignalCloseEvent.connect(this, &ProxyBinding::OnExternalClose);
}

ProxyBinding::~ProxyBinding() = default;

void ProxyBinding::OnConnectRequest(AsyncProxyServerSocket* socket,
                                    const SocketAddress& addr) {
  RTC_DCHECK(!connected_);
  RTC_DCHECK(ext_socket_);
  ext_socket_->Connect(addr);
  // TODO: handle errors here
}

void ProxyBinding::OnInternalRead(Socket* socket) {
  Read(int_socket_.get(), &out_buffer_);
  Write(ext_socket_.get(), &out_buffer_);
}

void ProxyBinding::OnInternalWrite(Socket* socket) {
  Write(int_socket_.get(), &in_buffer_);
}

void ProxyBinding::OnInternalClose(Socket* socket, int err) {
  Destroy();
}

void ProxyBinding::OnExternalConnect(Socket* socket) {
  RTC_DCHECK(socket != nullptr);
  connected_ = true;
  int_socket_->SendConnectResult(0, socket->GetRemoteAddress());
}

void ProxyBinding::OnExternalRead(Socket* socket) {
  Read(ext_socket_.get(), &in_buffer_);
  Write(int_socket_.get(), &in_buffer_);
}

void ProxyBinding::OnExternalWrite(Socket* socket) {
  Write(ext_socket_.get(), &out_buffer_);
}

void ProxyBinding::OnExternalClose(Socket* socket, int err) {
  if (!connected_) {
    int_socket_->SendConnectResult(err, SocketAddress());
  }
  Destroy();
}

void ProxyBinding::Read(Socket* socket, FifoBuffer* buffer) {
  // Only read if the buffer is empty.
  RTC_DCHECK(socket != nullptr);
  size_t size;
  int read;
  if (buffer->GetBuffered(&size) && size == 0) {
    void* p = buffer->GetWriteBuffer(&size);
    read = socket->Recv(p, size, nullptr);
    buffer->ConsumeWriteBuffer(std::max(read, 0));
  }
}

void ProxyBinding::Write(Socket* socket, FifoBuffer* buffer) {
  RTC_DCHECK(socket != nullptr);
  size_t size;
  int written;
  const void* p = buffer->GetReadData(&size);
  written = socket->Send(p, size);
  buffer->ConsumeReadData(std::max(written, 0));
}

void ProxyBinding::Destroy() {
  SignalDestroyed(this);
}

AsyncProxyServerSocket* SocksProxyServer::WrapSocket(Socket* socket) {
  return new AsyncSocksProxyServerSocket(socket);
}

}  // namespace rtc
