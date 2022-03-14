/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/proxy.h"

#include "rtc_base/trace_event.h"

namespace webrtc {
namespace proxy_internal {
ScopedTrace::ScopedTrace(const char* class_and_method_name)
    : class_and_method_name_(class_and_method_name) {
  TRACE_EVENT_BEGIN0("webrtc", class_and_method_name_);
}
ScopedTrace::~ScopedTrace() {
  TRACE_EVENT_END0("webrtc", class_and_method_name_);
}
}  // namespace proxy_internal
}  // namespace webrtc
