/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/include/audio_util.h"

namespace webrtc {

void FloatToS16(const float* src, size_t size, int16_t* dest) {
  for (size_t i = 0; i < size; ++i)
    dest[i] = FloatToS16(src[i]);
}

void S16ToFloat(const int16_t* src, size_t size, float* dest) {
  for (size_t i = 0; i < size; ++i)
    dest[i] = S16ToFloat(src[i]);
}

void FloatS16ToS16(const float* src, size_t size, int16_t* dest) {
  for (size_t i = 0; i < size; ++i)
    dest[i] = FloatS16ToS16(src[i]);
}

void FloatToFloatS16(const float* src, size_t size, float* dest) {
  for (size_t i = 0; i < size; ++i)
    dest[i] = FloatToFloatS16(src[i]);
}

void FloatS16ToFloat(const float* src, size_t size, float* dest) {
  for (size_t i = 0; i < size; ++i)
    dest[i] = FloatS16ToFloat(src[i]);
}

template <>
void DownmixInterleavedToMono<int16_t>(const int16_t* interleaved,
                                       size_t num_frames,
                                       int num_channels,
                                       int16_t* deinterleaved) {
  DownmixInterleavedToMonoImpl<int16_t, int32_t>(interleaved, num_frames,
                                                 num_channels, deinterleaved);
}

}  // namespace webrtc
