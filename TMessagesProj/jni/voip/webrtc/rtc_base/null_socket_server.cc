/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/null_socket_server.h"

#include "api/units/time_delta.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/socket_server.h"

namespace rtc {

NullSocketServer::NullSocketServer() = default;
NullSocketServer::~NullSocketServer() {}

bool NullSocketServer::Wait(webrtc::TimeDelta max_wait_duration,
                            bool process_io) {
  // Wait with the given timeout. Do not log a warning if we end up waiting for
  // a long time; that just means no one has any work for us, which is perfectly
  // legitimate.
  event_.Wait(max_wait_duration, /*warn_after=*/Event::kForever);
  return true;
}

void NullSocketServer::WakeUp() {
  event_.Set();
}

rtc::Socket* NullSocketServer::CreateSocket(int /* family */, int /* type */) {
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

}  // namespace rtc
