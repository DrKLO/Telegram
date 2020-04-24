/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/vector_float_frame.h"

namespace webrtc {

namespace {

std::vector<float*> ConstructChannelPointers(
    std::vector<std::vector<float>>* x) {
  std::vector<float*> channel_ptrs;
  for (auto& v : *x) {
    channel_ptrs.push_back(v.data());
  }
  return channel_ptrs;
}
}  // namespace

VectorFloatFrame::VectorFloatFrame(int num_channels,
                                   int samples_per_channel,
                                   float start_value)
    : channels_(num_channels,
                std::vector<float>(samples_per_channel, start_value)),
      channel_ptrs_(ConstructChannelPointers(&channels_)),
      float_frame_view_(channel_ptrs_.data(),
                        channels_.size(),
                        samples_per_channel) {}

VectorFloatFrame::~VectorFloatFrame() = default;

}  // namespace webrtc
