/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/x_atom_cache.h"

#include "rtc_base/checks.h"

namespace webrtc {

XAtomCache::XAtomCache(::Display* display) : display_(display) {
  RTC_DCHECK(display_);
}

XAtomCache::~XAtomCache() = default;

::Display* XAtomCache::display() const {
  return display_;
}

Atom XAtomCache::WmState() {
  return CreateIfNotExist(&wm_state_, "WM_STATE");
}

Atom XAtomCache::WindowType() {
  return CreateIfNotExist(&window_type_, "_NET_WM_WINDOW_TYPE");
}

Atom XAtomCache::WindowTypeNormal() {
  return CreateIfNotExist(&window_type_normal_, "_NET_WM_WINDOW_TYPE_NORMAL");
}

Atom XAtomCache::IccProfile() {
  return CreateIfNotExist(&icc_profile_, "_ICC_PROFILE");
}

Atom XAtomCache::CreateIfNotExist(Atom* atom, const char* name) {
  RTC_DCHECK(atom);
  if (*atom == None) {
    *atom = XInternAtom(display(), name, True);
  }
  return *atom;
}

}  // namespace webrtc
