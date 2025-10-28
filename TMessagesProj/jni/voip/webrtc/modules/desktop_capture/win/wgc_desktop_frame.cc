/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/wgc_desktop_frame.h"

#include <utility>

namespace webrtc {

WgcDesktopFrame::WgcDesktopFrame(DesktopSize size,
                                 int stride,
                                 std::vector<uint8_t>&& image_data)
    : DesktopFrame(size, stride, image_data.data(), nullptr),
      image_data_(std::move(image_data)) {}

WgcDesktopFrame::~WgcDesktopFrame() = default;

}  // namespace webrtc
