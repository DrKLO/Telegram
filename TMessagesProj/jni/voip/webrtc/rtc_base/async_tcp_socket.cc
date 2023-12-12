/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/async_tcp_socket.h"

#include <stdint.h>
#include <string.h>

#include <algorithm>
#include <memory>

#include "api/array_view.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/time_utils.h"  // for TimeMillis

#if defined(WEBRTC_POSIX)
#include <errno.h>
#endif  // WEBRTC_POSIX

namespace rtc {

static const size_t kMaxPacketSize = 64 * 1024;

typedef uint16_t PacketLength;
static const size_t kPacketLenSize = sizeof(PacketLength);

static const size_t kBufSize = kMaxPacketSize + kPacketLenSize;

// The input buffer will be resized so that at least kMinimumRecvSize bytes can
// be received (but it will not grow above the maximum size passed to the
// constructor).
static const size_t kMinimumRecvSize = 128;

static const int kListenBacklog = 5;

// Binds and connects `socket`
Socket* AsyncTCPSocketBase::ConnectSocket(
    rtc::Socket* socket,
    const rtc::SocketAddress& bind_address,
    const rtc::SocketAddress& remote_address) {
  std::unique_ptr<rtc::Socket> owned_socket(socket);
  if (socket->Bind(bind_address) < 0) {
    RTC_LOG(LS_ERROR) << "Bind() failed with error " << socket->GetError();
    return nullptr;
  }
  if (socket->Connect(remote_address) < 0) {
    RTC_LOG(LS_ERROR) << "Connect() failed with error " << socket->GetError();
    return nullptr;
  }
  return owned_socket.release();
}

AsyncTCPSocketBase::AsyncTCPSocketBase(Socket* socket,
                                       size_t max_packet_size)
    : socket_(socket),
      max_insize_(max_packet_size),
      max_outsize_(max_packet_size) {
  inbuf_.EnsureCapacity(kMinimumRecvSize);

  socket_->SignalConnectEvent.connect(this,
                                      &AsyncTCPSocketBase::OnConnectEvent);
  socket_->SignalReadEvent.connect(this, &AsyncTCPSocketBase::OnReadEvent);
  socket_->SignalWriteEvent.connect(this, &AsyncTCPSocketBase::OnWriteEvent);
  socket_->SignalCloseEvent.connect(this, &AsyncTCPSocketBase::OnCloseEvent);
}

AsyncTCPSocketBase::~AsyncTCPSocketBase() {}

SocketAddress AsyncTCPSocketBase::GetLocalAddress() const {
  return socket_->GetLocalAddress();
}

SocketAddress AsyncTCPSocketBase::GetRemoteAddress() const {
  return socket_->GetRemoteAddress();
}

int AsyncTCPSocketBase::Close() {
  return socket_->Close();
}

AsyncTCPSocket::State AsyncTCPSocketBase::GetState() const {
  switch (socket_->GetState()) {
    case Socket::CS_CLOSED:
      return STATE_CLOSED;
    case Socket::CS_CONNECTING:
      return STATE_CONNECTING;
    case Socket::CS_CONNECTED:
      return STATE_CONNECTED;
    default:
      RTC_DCHECK_NOTREACHED();
      return STATE_CLOSED;
  }
}

int AsyncTCPSocketBase::GetOption(Socket::Option opt, int* value) {
  return socket_->GetOption(opt, value);
}

int AsyncTCPSocketBase::SetOption(Socket::Option opt, int value) {
  return socket_->SetOption(opt, value);
}

int AsyncTCPSocketBase::GetError() const {
  return socket_->GetError();
}

void AsyncTCPSocketBase::SetError(int error) {
  return socket_->SetError(error);
}

int AsyncTCPSocketBase::SendTo(const void* pv,
                               size_t cb,
                               const SocketAddress& addr,
                               const rtc::PacketOptions& options) {
  const SocketAddress& remote_address = GetRemoteAddress();
  if (addr == remote_address)
    return Send(pv, cb, options);
  // Remote address may be empty if there is a sudden network change.
  RTC_DCHECK(remote_address.IsNil());
  socket_->SetError(ENOTCONN);
  return -1;
}

int AsyncTCPSocketBase::FlushOutBuffer() {
  RTC_DCHECK_GT(outbuf_.size(), 0);
  rtc::ArrayView<uint8_t> view = outbuf_;
  int res;
  while (view.size() > 0) {
    res = socket_->Send(view.data(), view.size());
    if (res <= 0) {
      break;
    }
    if (static_cast<size_t>(res) > view.size()) {
      RTC_DCHECK_NOTREACHED();
      res = -1;
      break;
    }
    view = view.subview(res);
  }
  if (res > 0) {
    // The output buffer may have been written out over multiple partial Send(),
    // so reconstruct the total written length.
    RTC_DCHECK_EQ(view.size(), 0);
    res = outbuf_.size();
    outbuf_.Clear();
  } else {
    // There was an error when calling Send(), so there will still be data left
    // to send at a later point.
    RTC_DCHECK_GT(view.size(), 0);
    // In the special case of EWOULDBLOCK, signal that we had a partial write.
    if (socket_->GetError() == EWOULDBLOCK) {
      res = outbuf_.size() - view.size();
    }
    if (view.size() < outbuf_.size()) {
      memmove(outbuf_.data(), view.data(), view.size());
      outbuf_.SetSize(view.size());
    }
  }
  return res;
}

void AsyncTCPSocketBase::AppendToOutBuffer(const void* pv, size_t cb) {
  RTC_DCHECK(outbuf_.size() + cb <= max_outsize_);
  outbuf_.AppendData(static_cast<const uint8_t*>(pv), cb);
}

void AsyncTCPSocketBase::OnConnectEvent(Socket* socket) {
  SignalConnect(this);
}

void AsyncTCPSocketBase::OnReadEvent(Socket* socket) {
  RTC_DCHECK(socket_.get() == socket);

  size_t total_recv = 0;
  while (true) {
    size_t free_size = inbuf_.capacity() - inbuf_.size();
    if (free_size < kMinimumRecvSize && inbuf_.capacity() < max_insize_) {
      inbuf_.EnsureCapacity(std::min(max_insize_, inbuf_.capacity() * 2));
      free_size = inbuf_.capacity() - inbuf_.size();
    }

    int len = socket_->Recv(inbuf_.data() + inbuf_.size(), free_size, nullptr);
    if (len < 0) {
      // TODO(stefan): Do something better like forwarding the error to the
      // user.
      if (!socket_->IsBlocking()) {
        RTC_LOG(LS_ERROR) << "Recv() returned error: " << socket_->GetError();
      }
      break;
    }

    total_recv += len;
    inbuf_.SetSize(inbuf_.size() + len);
    if (!len || static_cast<size_t>(len) < free_size) {
      break;
    }
  }

  if (!total_recv) {
    return;
  }

  size_t size = inbuf_.size();
  ProcessInput(inbuf_.data<char>(), &size);

  if (size > inbuf_.size()) {
    RTC_LOG(LS_ERROR) << "input buffer overflow";
    RTC_DCHECK_NOTREACHED();
    inbuf_.Clear();
  } else {
    inbuf_.SetSize(size);
  }
}

void AsyncTCPSocketBase::OnWriteEvent(Socket* socket) {
  RTC_DCHECK(socket_.get() == socket);

  if (outbuf_.size() > 0) {
    FlushOutBuffer();
  }

  if (outbuf_.size() == 0) {
    SignalReadyToSend(this);
  }
}

void AsyncTCPSocketBase::OnCloseEvent(Socket* socket, int error) {
  NotifyClosed(error);
}

// AsyncTCPSocket
// Binds and connects `socket` and creates AsyncTCPSocket for
// it. Takes ownership of `socket`. Returns null if bind() or
// connect() fail (`socket` is destroyed in that case).
AsyncTCPSocket* AsyncTCPSocket::Create(Socket* socket,
                                       const SocketAddress& bind_address,
                                       const SocketAddress& remote_address) {
  return new AsyncTCPSocket(
      AsyncTCPSocketBase::ConnectSocket(socket, bind_address, remote_address));
}

AsyncTCPSocket::AsyncTCPSocket(Socket* socket)
    : AsyncTCPSocketBase(socket, kBufSize) {}

int AsyncTCPSocket::Send(const void* pv,
                         size_t cb,
                         const rtc::PacketOptions& options) {
  if (cb > kBufSize) {
    SetError(EMSGSIZE);
    return -1;
  }

  // If we are blocking on send, then silently drop this packet
  if (!IsOutBufferEmpty())
    return static_cast<int>(cb);

  PacketLength pkt_len = HostToNetwork16(static_cast<PacketLength>(cb));
  AppendToOutBuffer(&pkt_len, kPacketLenSize);
  AppendToOutBuffer(pv, cb);

  int res = FlushOutBuffer();
  if (res <= 0) {
    // drop packet if we made no progress
    ClearOutBuffer();
    return res;
  }

  rtc::SentPacket sent_packet(options.packet_id, rtc::TimeMillis(),
                              options.info_signaled_after_sent);
  CopySocketInformationToPacketInfo(cb, *this, false, &sent_packet.info);
  SignalSentPacket(this, sent_packet);

  // We claim to have sent the whole thing, even if we only sent partial
  return static_cast<int>(cb);
}

void AsyncTCPSocket::ProcessInput(char* data, size_t* len) {
  SocketAddress remote_addr(GetRemoteAddress());

  while (true) {
    if (*len < kPacketLenSize)
      return;

    PacketLength pkt_len = rtc::GetBE16(data);
    if (*len < kPacketLenSize + pkt_len)
      return;

    SignalReadPacket(this, data + kPacketLenSize, pkt_len, remote_addr,
                     TimeMicros());

    *len -= kPacketLenSize + pkt_len;
    if (*len > 0) {
      memmove(data, data + kPacketLenSize + pkt_len, *len);
    }
  }
}

AsyncTcpListenSocket::AsyncTcpListenSocket(std::unique_ptr<Socket> socket)
    : socket_(std::move(socket)) {
  RTC_DCHECK(socket_.get() != nullptr);
  socket_->SignalReadEvent.connect(this, &AsyncTcpListenSocket::OnReadEvent);
  if (socket_->Listen(kListenBacklog) < 0) {
    RTC_LOG(LS_ERROR) << "Listen() failed with error " << socket_->GetError();
  }
}

AsyncTcpListenSocket::State AsyncTcpListenSocket::GetState() const {
  switch (socket_->GetState()) {
    case Socket::CS_CLOSED:
      return State::kClosed;
    case Socket::CS_CONNECTING:
      return State::kBound;
    default:
      RTC_DCHECK_NOTREACHED();
      return State::kClosed;
  }
}

SocketAddress AsyncTcpListenSocket::GetLocalAddress() const {
  return socket_->GetLocalAddress();
}

void AsyncTcpListenSocket::OnReadEvent(Socket* socket) {
  RTC_DCHECK(socket_.get() == socket);

  rtc::SocketAddress address;
  rtc::Socket* new_socket = socket->Accept(&address);
  if (!new_socket) {
    // TODO(stefan): Do something better like forwarding the error
    // to the user.
    RTC_LOG(LS_ERROR) << "TCP accept failed with error " << socket_->GetError();
    return;
  }

  HandleIncomingConnection(new_socket);

  // Prime a read event in case data is waiting.
  new_socket->SignalReadEvent(new_socket);
}

void AsyncTcpListenSocket::HandleIncomingConnection(Socket* socket) {
  SignalNewConnection(this, new AsyncTCPSocket(socket));
}

}  // namespace rtc
