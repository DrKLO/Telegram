/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/block_buffer.h"

#include <algorithm>

namespace webrtc {

BlockBuffer::BlockBuffer(size_t size, size_t num_bands, size_t num_channels)
    : size(static_cast<int>(size)),
      buffer(size, Block(num_bands, num_channels)) {}

BlockBuffer::~BlockBuffer() = default;

}  // namespace webrtc
