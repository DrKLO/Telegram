/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/vector_buffer.h"

#include <algorithm>

namespace webrtc {

VectorBuffer::VectorBuffer(size_t size, size_t height)
    : size(static_cast<int>(size)),
      buffer(size, std::vector<float>(height, 0.f)) {
  for (auto& c : buffer) {
    std::fill(c.begin(), c.end(), 0.f);
  }
}

VectorBuffer::~VectorBuffer() = default;

}  // namespace webrtc
