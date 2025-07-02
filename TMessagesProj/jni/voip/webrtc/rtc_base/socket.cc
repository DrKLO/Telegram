/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/socket.h"

#include <cstdint>

#include "rtc_base/buffer.h"

namespace rtc {

int Socket::RecvFrom(ReceiveBuffer& buffer) {
  static constexpr int BUF_SIZE = 64 * 1024;
  int64_t timestamp = -1;
  buffer.payload.EnsureCapacity(BUF_SIZE);
  int len = RecvFrom(buffer.payload.data(), buffer.payload.capacity(),
                     &buffer.source_address, &timestamp);
  buffer.payload.SetSize(len > 0 ? len : 0);
  if (len > 0 && timestamp != -1) {
    buffer.arrival_time = webrtc::Timestamp::Micros(timestamp);
  }

  return len;
}

}  // namespace rtc
