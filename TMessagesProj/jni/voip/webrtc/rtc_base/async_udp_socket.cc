/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/async_udp_socket.h"

#include "absl/types/optional.h"
#include "api/units/time_delta.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/network/received_packet.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"

namespace rtc {

// Returns true if the experiement "WebRTC-SCM-Timestamp" is explicitly
// disabled.
static bool IsScmTimeStampExperimentDisabled() {
  return webrtc::field_trial::IsDisabled("WebRTC-SCM-Timestamp");
}

AsyncUDPSocket* AsyncUDPSocket::Create(Socket* socket,
                                       const SocketAddress& bind_address) {
  std::unique_ptr<Socket> owned_socket(socket);
  if (socket->Bind(bind_address) < 0) {
    RTC_LOG(LS_ERROR) << "Bind() failed with error " << socket->GetError();
    return nullptr;
  }
  return new AsyncUDPSocket(owned_socket.release());
}

AsyncUDPSocket* AsyncUDPSocket::Create(SocketFactory* factory,
                                       const SocketAddress& bind_address) {
  Socket* socket = factory->CreateSocket(bind_address.family(), SOCK_DGRAM);
  if (!socket)
    return nullptr;
  return Create(socket, bind_address);
}

AsyncUDPSocket::AsyncUDPSocket(Socket* socket) : socket_(socket) {
  sequence_checker_.Detach();
  // The socket should start out readable but not writable.
  socket_->SignalReadEvent.connect(this, &AsyncUDPSocket::OnReadEvent);
  socket_->SignalWriteEvent.connect(this, &AsyncUDPSocket::OnWriteEvent);
}

SocketAddress AsyncUDPSocket::GetLocalAddress() const {
  return socket_->GetLocalAddress();
}

SocketAddress AsyncUDPSocket::GetRemoteAddress() const {
  return socket_->GetRemoteAddress();
}

int AsyncUDPSocket::Send(const void* pv,
                         size_t cb,
                         const rtc::PacketOptions& options) {
  rtc::SentPacket sent_packet(options.packet_id, rtc::TimeMillis(),
                              options.info_signaled_after_sent);
  CopySocketInformationToPacketInfo(cb, *this, false, &sent_packet.info);
  int ret = socket_->Send(pv, cb);
  SignalSentPacket(this, sent_packet);
  return ret;
}

int AsyncUDPSocket::SendTo(const void* pv,
                           size_t cb,
                           const SocketAddress& addr,
                           const rtc::PacketOptions& options) {
  rtc::SentPacket sent_packet(options.packet_id, rtc::TimeMillis(),
                              options.info_signaled_after_sent);
  CopySocketInformationToPacketInfo(cb, *this, true, &sent_packet.info);
  int ret = socket_->SendTo(pv, cb, addr);
  SignalSentPacket(this, sent_packet);
  return ret;
}

int AsyncUDPSocket::Close() {
  return socket_->Close();
}

AsyncUDPSocket::State AsyncUDPSocket::GetState() const {
  return STATE_BOUND;
}

int AsyncUDPSocket::GetOption(Socket::Option opt, int* value) {
  return socket_->GetOption(opt, value);
}

int AsyncUDPSocket::SetOption(Socket::Option opt, int value) {
  return socket_->SetOption(opt, value);
}

int AsyncUDPSocket::GetError() const {
  return socket_->GetError();
}

void AsyncUDPSocket::SetError(int error) {
  return socket_->SetError(error);
}

void AsyncUDPSocket::OnReadEvent(Socket* socket) {
  RTC_DCHECK(socket_.get() == socket);
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  Socket::ReceiveBuffer receive_buffer(buffer_);
  int len = socket_->RecvFrom(receive_buffer);
  if (len < 0) {
    // An error here typically means we got an ICMP error in response to our
    // send datagram, indicating the remote address was unreachable.
    // When doing ICE, this kind of thing will often happen.
    // TODO: Do something better like forwarding the error to the user.
    SocketAddress local_addr = socket_->GetLocalAddress();
    RTC_LOG(LS_INFO) << "AsyncUDPSocket[" << local_addr.ToSensitiveString()
                     << "] receive failed with error " << socket_->GetError();
    return;
  }
  if (len == 0) {
    // Spurios wakeup.
    return;
  }

  if (!receive_buffer.arrival_time) {
    // Timestamp from socket is not available.
    receive_buffer.arrival_time = webrtc::Timestamp::Micros(rtc::TimeMicros());
  } else {
    if (!socket_time_offset_) {
      // Estimate timestamp offset from first packet arrival time unless
      // disabled
      bool estimate_time_offset = !IsScmTimeStampExperimentDisabled();
      if (estimate_time_offset) {
        socket_time_offset_ = webrtc::Timestamp::Micros(rtc::TimeMicros()) -
                              *receive_buffer.arrival_time;
      } else {
        socket_time_offset_ = webrtc::TimeDelta::Micros(0);
      }
    }
    *receive_buffer.arrival_time += *socket_time_offset_;
  }
  NotifyPacketReceived(ReceivedPacket(receive_buffer.payload,
                                      receive_buffer.source_address,
                                      receive_buffer.arrival_time));
}

void AsyncUDPSocket::OnWriteEvent(Socket* socket) {
  SignalReadyToSend(this);
}

}  // namespace rtc
