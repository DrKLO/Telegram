/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_FRAME_VIEW_H_
#define MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_FRAME_VIEW_H_

#include "api/array_view.h"

namespace webrtc {

// Class to pass audio data in T** format, where T is a numeric type.
template <class T>
class AudioFrameView {
 public:
  // `num_channels` and `channel_size` describe the T**
  // `audio_samples`. `audio_samples` is assumed to point to a
  // two-dimensional |num_channels * channel_size| array of floats.
  AudioFrameView(T* const* audio_samples, int num_channels, int channel_size)
      : audio_samples_(audio_samples),
        num_channels_(num_channels),
        channel_size_(channel_size) {
    RTC_DCHECK_GE(num_channels_, 0);
    RTC_DCHECK_GE(channel_size_, 0);
  }

  // Implicit cast to allow converting Frame<float> to
  // Frame<const float>.
  template <class U>
  AudioFrameView(AudioFrameView<U> other)
      : audio_samples_(other.data()),
        num_channels_(other.num_channels()),
        channel_size_(other.samples_per_channel()) {}

  AudioFrameView() = delete;

  int num_channels() const { return num_channels_; }

  int samples_per_channel() const { return channel_size_; }

  rtc::ArrayView<T> channel(int idx) {
    RTC_DCHECK_LE(0, idx);
    RTC_DCHECK_LE(idx, num_channels_);
    return rtc::ArrayView<T>(audio_samples_[idx], channel_size_);
  }

  rtc::ArrayView<const T> channel(int idx) const {
    RTC_DCHECK_LE(0, idx);
    RTC_DCHECK_LE(idx, num_channels_);
    return rtc::ArrayView<const T>(audio_samples_[idx], channel_size_);
  }

  T* const* data() { return audio_samples_; }

 private:
  T* const* audio_samples_;
  int num_channels_;
  int channel_size_;
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_FRAME_VIEW_H_
