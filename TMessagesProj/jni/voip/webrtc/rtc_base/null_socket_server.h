/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_NULL_SOCKET_SERVER_H_
#define RTC_BASE_NULL_SOCKET_SERVER_H_

#include "rtc_base/event.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/system/rtc_export.h"

namespace rtc {

class RTC_EXPORT NullSocketServer : public SocketServer {
 public:
  NullSocketServer();
  ~NullSocketServer() override;

  bool Wait(webrtc::TimeDelta max_wait_duration, bool process_io) override;
  void WakeUp() override;

  Socket* CreateSocket(int family, int type) override;

 private:
  Event event_;
};

}  // namespace rtc

#endif  // RTC_BASE_NULL_SOCKET_SERVER_H_
