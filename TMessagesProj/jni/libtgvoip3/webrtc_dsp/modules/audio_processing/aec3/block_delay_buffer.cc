/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/block_delay_buffer.h"

#include "rtc_base/checks.h"

namespace webrtc {

BlockDelayBuffer::BlockDelayBuffer(size_t num_bands,
                                   size_t frame_length,
                                   size_t delay_samples)
    : frame_length_(frame_length),
      delay_(delay_samples),
      buf_(num_bands, std::vector<float>(delay_, 0.f)) {}

BlockDelayBuffer::~BlockDelayBuffer() = default;

void BlockDelayBuffer::DelaySignal(AudioBuffer* frame) {
  RTC_DCHECK_EQ(1, frame->num_channels());
  RTC_DCHECK_EQ(buf_.size(), frame->num_bands());
  if (delay_ == 0) {
    return;
  }

  const size_t i_start = last_insert_;
  size_t i = 0;
  for (size_t j = 0; j < buf_.size(); ++j) {
    i = i_start;
    for (size_t k = 0; k < frame_length_; ++k) {
      const float tmp = buf_[j][i];
      buf_[j][i] = frame->split_bands_f(0)[j][k];
      frame->split_bands_f(0)[j][k] = tmp;
      i = i < buf_[0].size() - 1 ? i + 1 : 0;
    }
  }

  last_insert_ = i;
}

}  // namespace webrtc
