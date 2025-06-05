/*
 *  Copyright 2005 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SOCKET_STREAM_H_
#define RTC_BASE_SOCKET_STREAM_H_

#include <stddef.h>

#include "rtc_base/socket.h"
#include "rtc_base/stream.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

class SocketStream : public StreamInterface, public sigslot::has_slots<> {
 public:
  explicit SocketStream(Socket* socket);
  ~SocketStream() override;

  SocketStream(const SocketStream&) = delete;
  SocketStream& operator=(const SocketStream&) = delete;

  void Attach(Socket* socket);
  Socket* Detach();

  Socket* GetSocket() { return socket_; }

  StreamState GetState() const override;

  StreamResult Read(rtc::ArrayView<uint8_t> buffer,
                    size_t& read,
                    int& error) override;

  StreamResult Write(rtc::ArrayView<const uint8_t> data,
                     size_t& written,
                     int& error) override;

  void Close() override;

 private:
  void OnConnectEvent(Socket* socket);
  void OnReadEvent(Socket* socket);
  void OnWriteEvent(Socket* socket);
  void OnCloseEvent(Socket* socket, int err);

  Socket* socket_;
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // RTC_BASE_SOCKET_STREAM_H_
