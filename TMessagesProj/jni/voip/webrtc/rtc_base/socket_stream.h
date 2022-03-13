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

#include "rtc_base/constructor_magic.h"
#include "rtc_base/socket.h"
#include "rtc_base/stream.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////

class SocketStream : public StreamInterface, public sigslot::has_slots<> {
 public:
  explicit SocketStream(Socket* socket);
  ~SocketStream() override;

  void Attach(Socket* socket);
  Socket* Detach();

  Socket* GetSocket() { return socket_; }

  StreamState GetState() const override;

  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;

  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;

  void Close() override;

 private:
  void OnConnectEvent(Socket* socket);
  void OnReadEvent(Socket* socket);
  void OnWriteEvent(Socket* socket);
  void OnCloseEvent(Socket* socket, int err);

  Socket* socket_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SocketStream);
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // RTC_BASE_SOCKET_STREAM_H_
