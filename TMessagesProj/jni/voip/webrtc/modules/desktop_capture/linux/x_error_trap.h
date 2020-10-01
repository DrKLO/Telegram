/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_LINUX_X_ERROR_TRAP_H_
#define MODULES_DESKTOP_CAPTURE_LINUX_X_ERROR_TRAP_H_

#include <X11/Xlib.h>

#include "rtc_base/constructor_magic.h"

namespace webrtc {

// Helper class that registers X Window error handler. Caller can use
// GetLastErrorAndDisable() to get the last error that was caught, if any.
class XErrorTrap {
 public:
  explicit XErrorTrap(Display* display);
  ~XErrorTrap();

  // Returns last error and removes unregisters the error handler.
  int GetLastErrorAndDisable();

 private:
  XErrorHandler original_error_handler_;
  bool enabled_;

  RTC_DISALLOW_COPY_AND_ASSIGN(XErrorTrap);
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_LINUX_X_ERROR_TRAP_H_
