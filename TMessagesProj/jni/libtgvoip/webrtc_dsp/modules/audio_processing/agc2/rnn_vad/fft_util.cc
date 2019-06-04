/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/fft_util.h"

#include <stddef.h>
#include <cmath>

#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {
namespace {

constexpr size_t kHalfFrameSize = kFrameSize20ms24kHz / 2;

// Computes the first half of the Vorbis window.
std::array<float, kHalfFrameSize> ComputeHalfVorbisWindow() {
  std::array<float, kHalfFrameSize> half_window{};
  for (size_t i = 0; i < kHalfFrameSize; ++i) {
    half_window[i] =
        std::sin(0.5 * kPi * std::sin(0.5 * kPi * (i + 0.5) / kHalfFrameSize) *
                 std::sin(0.5 * kPi * (i + 0.5) / kHalfFrameSize));
  }
  return half_window;
}

}  // namespace

BandAnalysisFft::BandAnalysisFft()
    : half_window_(ComputeHalfVorbisWindow()),
      fft_(static_cast<int>(input_buf_.size())) {}

BandAnalysisFft::~BandAnalysisFft() = default;

void BandAnalysisFft::ForwardFft(rtc::ArrayView<const float> samples,
                                 rtc::ArrayView<std::complex<float>> dst) {
  RTC_DCHECK_EQ(input_buf_.size(), samples.size());
  RTC_DCHECK_EQ(samples.size(), dst.size());
  // Apply windowing.
  RTC_DCHECK_EQ(input_buf_.size(), 2 * half_window_.size());
  for (size_t i = 0; i < input_buf_.size() / 2; ++i) {
    input_buf_[i].real(samples[i] * half_window_[i]);
    size_t j = kFrameSize20ms24kHz - i - 1;
    input_buf_[j].real(samples[j] * half_window_[i]);
  }
  fft_.ForwardFft(kFrameSize20ms24kHz, input_buf_.data(), kFrameSize20ms24kHz,
                  dst.data());
}

}  // namespace rnn_vad
}  // namespace webrtc
