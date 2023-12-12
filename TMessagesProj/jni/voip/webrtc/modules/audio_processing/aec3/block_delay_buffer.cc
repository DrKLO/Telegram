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

#include "api/array_view.h"
#include "rtc_base/checks.h"

namespace webrtc {

BlockDelayBuffer::BlockDelayBuffer(size_t num_channels,
                                   size_t num_bands,
                                   size_t frame_length,
                                   size_t delay_samples)
    : frame_length_(frame_length),
      delay_(delay_samples),
      buf_(num_channels,
           std::vector<std::vector<float>>(num_bands,
                                           std::vector<float>(delay_, 0.f))) {}

BlockDelayBuffer::~BlockDelayBuffer() = default;

void BlockDelayBuffer::DelaySignal(AudioBuffer* frame) {
  RTC_DCHECK_EQ(buf_.size(), frame->num_channels());
  if (delay_ == 0) {
    return;
  }

  const size_t num_bands = buf_[0].size();
  const size_t num_channels = buf_.size();

  const size_t i_start = last_insert_;
  size_t i = 0;
  for (size_t ch = 0; ch < num_channels; ++ch) {
    RTC_DCHECK_EQ(buf_[ch].size(), frame->num_bands());
    RTC_DCHECK_EQ(buf_[ch].size(), num_bands);
    rtc::ArrayView<float* const> frame_ch(frame->split_bands(ch), num_bands);
    const size_t delay = delay_;

    for (size_t band = 0; band < num_bands; ++band) {
      RTC_DCHECK_EQ(delay_, buf_[ch][band].size());
      i = i_start;

      // Offloading these pointers and class variables to local variables allows
      // the compiler to optimize the below loop when compiling with
      // '-fno-strict-aliasing'.
      float* buf_ch_band = buf_[ch][band].data();
      float* frame_ch_band = frame_ch[band];

      for (size_t k = 0, frame_length = frame_length_; k < frame_length; ++k) {
        const float tmp = buf_ch_band[i];
        buf_ch_band[i] = frame_ch_band[k];
        frame_ch_band[k] = tmp;

        i = i < delay - 1 ? i + 1 : 0;
      }
    }
  }

  last_insert_ = i;
}

}  // namespace webrtc
