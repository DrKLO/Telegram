/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/screen_drawer.h"

namespace webrtc {

namespace {
std::unique_ptr<ScreenDrawerLock> g_screen_drawer_lock;
}  // namespace

ScreenDrawerLock::ScreenDrawerLock() = default;
ScreenDrawerLock::~ScreenDrawerLock() = default;

ScreenDrawer::ScreenDrawer() {
  g_screen_drawer_lock = ScreenDrawerLock::Create();
}

ScreenDrawer::~ScreenDrawer() {
  g_screen_drawer_lock.reset();
}

}  // namespace webrtc
