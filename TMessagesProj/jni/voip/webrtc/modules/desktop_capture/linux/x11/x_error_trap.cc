/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/x11/x_error_trap.h"

#include <stddef.h>

#include <atomic>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {

static int g_last_xserver_error_code = 0;
static std::atomic<Display*> g_display_for_error_handler = nullptr;

Mutex* AcquireMutex() {
  static Mutex* mutex = new Mutex();
  return mutex;
}

int XServerErrorHandler(Display* display, XErrorEvent* error_event) {
  RTC_DCHECK_EQ(display, g_display_for_error_handler.load());
  g_last_xserver_error_code = error_event->error_code;
  return 0;
}

}  // namespace

XErrorTrap::XErrorTrap(Display* display) : mutex_lock_(AcquireMutex()) {
  // We don't expect this class to be used in a nested fashion so therefore
  // g_display_for_error_handler should never be valid here.
  RTC_DCHECK(!g_display_for_error_handler.load());
  RTC_DCHECK(display);
  g_display_for_error_handler.store(display);
  g_last_xserver_error_code = 0;
  original_error_handler_ = XSetErrorHandler(&XServerErrorHandler);
}

int XErrorTrap::GetLastErrorAndDisable() {
  g_display_for_error_handler.store(nullptr);
  XSetErrorHandler(original_error_handler_);
  return g_last_xserver_error_code;
}

XErrorTrap::~XErrorTrap() {
  if (g_display_for_error_handler.load() != nullptr)
    GetLastErrorAndDisable();
}

}  // namespace webrtc
