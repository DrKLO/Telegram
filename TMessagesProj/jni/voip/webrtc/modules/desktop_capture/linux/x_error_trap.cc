/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/x_error_trap.h"

#include <assert.h>
#include <stddef.h>

#if defined(TOOLKIT_GTK)
#include <gdk/gdk.h>
#endif  // !defined(TOOLKIT_GTK)

namespace webrtc {

namespace {

#if !defined(TOOLKIT_GTK)

// TODO(sergeyu): This code is not thread safe. Fix it. Bug 2202.
static bool g_xserver_error_trap_enabled = false;
static int g_last_xserver_error_code = 0;

int XServerErrorHandler(Display* display, XErrorEvent* error_event) {
  assert(g_xserver_error_trap_enabled);
  g_last_xserver_error_code = error_event->error_code;
  return 0;
}

#endif  // !defined(TOOLKIT_GTK)

}  // namespace

XErrorTrap::XErrorTrap(Display* display)
    : original_error_handler_(NULL), enabled_(true) {
#if defined(TOOLKIT_GTK)
  gdk_error_trap_push();
#else   // !defined(TOOLKIT_GTK)
  assert(!g_xserver_error_trap_enabled);
  original_error_handler_ = XSetErrorHandler(&XServerErrorHandler);
  g_xserver_error_trap_enabled = true;
  g_last_xserver_error_code = 0;
#endif  // !defined(TOOLKIT_GTK)
}

int XErrorTrap::GetLastErrorAndDisable() {
  enabled_ = false;
#if defined(TOOLKIT_GTK)
  return gdk_error_trap_push();
#else   // !defined(TOOLKIT_GTK)
  assert(g_xserver_error_trap_enabled);
  XSetErrorHandler(original_error_handler_);
  g_xserver_error_trap_enabled = false;
  return g_last_xserver_error_code;
#endif  // !defined(TOOLKIT_GTK)
}

XErrorTrap::~XErrorTrap() {
  if (enabled_)
    GetLastErrorAndDisable();
}

}  // namespace webrtc
