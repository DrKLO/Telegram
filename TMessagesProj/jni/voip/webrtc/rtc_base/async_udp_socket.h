/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_UDP_SOCKET_H_
#define RTC_BASE_ASYNC_UDP_SOCKET_H_

#include <stddef.h>

#include <cstdint>
#include <memory>

#include "absl/types/optional.h"
#include "api/sequence_checker.h"
#include "api/units/time_delta.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/thread_annotations.h"

namespace rtc {

// Provides the ability to receive packets asynchronously.  Sends are not
// buffered since it is acceptable to drop packets under high load.
class AsyncUDPSocket : public AsyncPacketSocket {
 public:
  // Binds `socket` and creates AsyncUDPSocket for it. Takes ownership
  // of `socket`. Returns null if bind() fails (`socket` is destroyed
  // in that case).
  static AsyncUDPSocket* Create(Socket* socket,
                                const SocketAddress& bind_address);
  // Creates a new socket for sending asynchronous UDP packets using an
  // asynchronous socket from the given factory.
  static AsyncUDPSocket* Create(SocketFactory* factory,
                                const SocketAddress& bind_address);
  explicit AsyncUDPSocket(Socket* socket);
  ~AsyncUDPSocket() = default;

  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;
  int Send(const void* pv,
           size_t cb,
           const rtc::PacketOptions& options) override;
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

 private:
  // Called when the underlying socket is ready to be read from.
  void OnReadEvent(Socket* socket);
  // Called when the underlying socket is ready to send.
  void OnWriteEvent(Socket* socket);

  RTC_NO_UNIQUE_ADDRESS webrtc::SequenceChecker sequence_checker_;
  std::unique_ptr<Socket> socket_;
  rtc::Buffer buffer_ RTC_GUARDED_BY(sequence_checker_);
  absl::optional<webrtc::TimeDelta> socket_time_offset_
      RTC_GUARDED_BY(sequence_checker_);
};

}  // namespace rtc

#endif  // RTC_BASE_ASYNC_UDP_SOCKET_H_
