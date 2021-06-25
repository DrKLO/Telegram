/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_INTERNAL_DEFAULT_SOCKET_SERVER_H_
#define RTC_BASE_INTERNAL_DEFAULT_SOCKET_SERVER_H_

#include <memory>

#include "rtc_base/socket_server.h"

namespace rtc {

std::unique_ptr<SocketServer> CreateDefaultSocketServer();

}  // namespace rtc

#endif  // RTC_BASE_INTERNAL_DEFAULT_SOCKET_SERVER_H_
