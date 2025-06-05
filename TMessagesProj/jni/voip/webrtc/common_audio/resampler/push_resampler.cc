/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/resampler/include/push_resampler.h"

#include <stdint.h>
#include <string.h>

#include <memory>

#include "common_audio/include/audio_util.h"
#include "common_audio/resampler/push_sinc_resampler.h"
#include "rtc_base/checks.h"

namespace webrtc {

template <typename T>
PushResampler<T>::PushResampler()
    : src_sample_rate_hz_(0), dst_sample_rate_hz_(0), num_channels_(0) {}

template <typename T>
PushResampler<T>::~PushResampler() {}

template <typename T>
int PushResampler<T>::InitializeIfNeeded(int src_sample_rate_hz,
                                         int dst_sample_rate_hz,
                                         size_t num_channels) {
  // These checks used to be factored out of this template function due to
  // Windows debug build issues with clang. http://crbug.com/615050
  RTC_DCHECK_GT(src_sample_rate_hz, 0);
  RTC_DCHECK_GT(dst_sample_rate_hz, 0);
  RTC_DCHECK_GT(num_channels, 0);

  if (src_sample_rate_hz == src_sample_rate_hz_ &&
      dst_sample_rate_hz == dst_sample_rate_hz_ &&
      num_channels == num_channels_) {
    // No-op if settings haven't changed.
    return 0;
  }

  if (src_sample_rate_hz <= 0 || dst_sample_rate_hz <= 0 || num_channels <= 0) {
    return -1;
  }

  src_sample_rate_hz_ = src_sample_rate_hz;
  dst_sample_rate_hz_ = dst_sample_rate_hz;
  num_channels_ = num_channels;

  const size_t src_size_10ms_mono =
      static_cast<size_t>(src_sample_rate_hz / 100);
  const size_t dst_size_10ms_mono =
      static_cast<size_t>(dst_sample_rate_hz / 100);
  channel_resamplers_.clear();
  for (size_t i = 0; i < num_channels; ++i) {
    channel_resamplers_.push_back(ChannelResampler());
    auto channel_resampler = channel_resamplers_.rbegin();
    channel_resampler->resampler = std::make_unique<PushSincResampler>(
        src_size_10ms_mono, dst_size_10ms_mono);
    channel_resampler->source.resize(src_size_10ms_mono);
    channel_resampler->destination.resize(dst_size_10ms_mono);
  }

  channel_data_array_.resize(num_channels_);

  return 0;
}

template <typename T>
int PushResampler<T>::Resample(const T* src,
                               size_t src_length,
                               T* dst,
                               size_t dst_capacity) {
  // These checks used to be factored out of this template function due to
  // Windows debug build issues with clang. http://crbug.com/615050
  const size_t src_size_10ms = (src_sample_rate_hz_ / 100) * num_channels_;
  const size_t dst_size_10ms = (dst_sample_rate_hz_ / 100) * num_channels_;
  RTC_DCHECK_EQ(src_length, src_size_10ms);
  RTC_DCHECK_GE(dst_capacity, dst_size_10ms);

  if (src_sample_rate_hz_ == dst_sample_rate_hz_) {
    // The old resampler provides this memcpy facility in the case of matching
    // sample rates, so reproduce it here for the sinc resampler.
    memcpy(dst, src, src_length * sizeof(T));
    return static_cast<int>(src_length);
  }

  const size_t src_length_mono = src_length / num_channels_;
  const size_t dst_capacity_mono = dst_capacity / num_channels_;

  for (size_t ch = 0; ch < num_channels_; ++ch) {
    channel_data_array_[ch] = channel_resamplers_[ch].source.data();
  }

  Deinterleave(src, src_length_mono, num_channels_, channel_data_array_.data());

  size_t dst_length_mono = 0;

  for (auto& resampler : channel_resamplers_) {
    dst_length_mono = resampler.resampler->Resample(
        resampler.source.data(), src_length_mono, resampler.destination.data(),
        dst_capacity_mono);
  }

  for (size_t ch = 0; ch < num_channels_; ++ch) {
    channel_data_array_[ch] = channel_resamplers_[ch].destination.data();
  }

  Interleave(channel_data_array_.data(), dst_length_mono, num_channels_, dst);
  return static_cast<int>(dst_length_mono * num_channels_);
}

// Explictly generate required instantiations.
template class PushResampler<int16_t>;
template class PushResampler<float>;

}  // namespace webrtc
