/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/window_finder.h"

namespace webrtc {

WindowFinder::Options::Options() = default;
WindowFinder::Options::~Options() = default;
WindowFinder::Options::Options(const WindowFinder::Options& other) = default;
WindowFinder::Options::Options(WindowFinder::Options&& other) = default;

}  // namespace webrtc
