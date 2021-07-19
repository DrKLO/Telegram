/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/downsampled_render_buffer.h"

#include <algorithm>

namespace webrtc {

DownsampledRenderBuffer::DownsampledRenderBuffer(size_t downsampled_buffer_size)
    : size(static_cast<int>(downsampled_buffer_size)),
      buffer(downsampled_buffer_size, 0.f) {
  std::fill(buffer.begin(), buffer.end(), 0.f);
}

DownsampledRenderBuffer::~DownsampledRenderBuffer() = default;

}  // namespace webrtc
