/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/scoped_thread_desktop.h"

#include "modules/desktop_capture/win/desktop.h"

namespace webrtc {

ScopedThreadDesktop::ScopedThreadDesktop()
    : initial_(Desktop::GetThreadDesktop()) {}

ScopedThreadDesktop::~ScopedThreadDesktop() {
  Revert();
}

bool ScopedThreadDesktop::IsSame(const Desktop& desktop) {
  if (assigned_.get() != NULL) {
    return assigned_->IsSame(desktop);
  } else {
    return initial_->IsSame(desktop);
  }
}

void ScopedThreadDesktop::Revert() {
  if (assigned_.get() != NULL) {
    initial_->SetThreadDesktop();
    assigned_.reset();
  }
}

bool ScopedThreadDesktop::SetThreadDesktop(Desktop* desktop) {
  Revert();

  std::unique_ptr<Desktop> scoped_desktop(desktop);

  if (initial_->IsSame(*desktop))
    return true;

  if (!desktop->SetThreadDesktop())
    return false;

  assigned_.reset(scoped_desktop.release());
  return true;
}

}  // namespace webrtc
