/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/version.h"

namespace webrtc {

// The timestamp is always in UTC.
const char* const kSourceTimestamp = "WebRTC source stamp 2024-02-18T04:06:34";

void LoadWebRTCVersionInRegister() {
  // Using volatile to instruct the compiler to not optimize `p` away even
  // if it looks unused.
  const char* volatile p = kSourceTimestamp;
  static_cast<void>(p);
}

}  // namespace webrtc
