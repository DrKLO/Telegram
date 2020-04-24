/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/matrix_buffer.h"

#include <algorithm>

namespace webrtc {

MatrixBuffer::MatrixBuffer(size_t size, size_t height, size_t width)
    : size(static_cast<int>(size)),
      buffer(size,
             std::vector<std::vector<float>>(height,
                                             std::vector<float>(width, 0.f))) {
  for (auto& c : buffer) {
    for (auto& b : c) {
      std::fill(b.begin(), b.end(), 0.f);
    }
  }
}

MatrixBuffer::~MatrixBuffer() = default;

}  // namespace webrtc
