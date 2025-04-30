/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/portal/pipewire_utils.h"

#include <pipewire/pipewire.h>

#include "rtc_base/sanitizer.h"

#if defined(WEBRTC_DLOPEN_PIPEWIRE)
#include "modules/portal/pipewire_stubs.h"
#endif  // defined(WEBRTC_DLOPEN_PIPEWIRE)

namespace webrtc {

RTC_NO_SANITIZE("cfi-icall")
bool InitializePipeWire() {
#if defined(WEBRTC_DLOPEN_PIPEWIRE)
  static constexpr char kPipeWireLib[] = "libpipewire-0.3.so.0";

  using modules_portal::InitializeStubs;
  using modules_portal::kModulePipewire;

  modules_portal::StubPathMap paths;

  // Check if the PipeWire library is available.
  paths[kModulePipewire].push_back(kPipeWireLib);

  static bool result = InitializeStubs(paths);

  return result;
#else
  return true;
#endif  // defined(WEBRTC_DLOPEN_PIPEWIRE)
}

PipeWireThreadLoopLock::PipeWireThreadLoopLock(pw_thread_loop* loop)
    : loop_(loop) {
  pw_thread_loop_lock(loop_);
}

PipeWireThreadLoopLock::~PipeWireThreadLoopLock() {
  pw_thread_loop_unlock(loop_);
}

}  // namespace webrtc
