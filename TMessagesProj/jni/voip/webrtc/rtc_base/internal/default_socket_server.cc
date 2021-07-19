/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/internal/default_socket_server.h"

#include <memory>

#include "rtc_base/socket_server.h"

#if defined(__native_client__)
#include "rtc_base/null_socket_server.h"
#else
#include "rtc_base/physical_socket_server.h"
#endif

namespace rtc {

std::unique_ptr<SocketServer> CreateDefaultSocketServer() {
#if defined(__native_client__)
  return std::unique_ptr<SocketServer>(new rtc::NullSocketServer);
#else
  return std::unique_ptr<SocketServer>(new rtc::PhysicalSocketServer);
#endif
}

}  // namespace rtc
