/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/ns_fft.h"

#include "common_audio/third_party/ooura/fft_size_256/fft4g.h"

namespace webrtc {

NrFft::NrFft() : bit_reversal_state_(kFftSize / 2), tables_(kFftSize / 2) {
  // Initialize WebRtc_rdt (setting (bit_reversal_state_[0] to 0 triggers
  // initialization)
  bit_reversal_state_[0] = 0.f;
  std::array<float, kFftSize> tmp_buffer;
  tmp_buffer.fill(0.f);
  WebRtc_rdft(kFftSize, 1, tmp_buffer.data(), bit_reversal_state_.data(),
              tables_.data());
}

void NrFft::Fft(rtc::ArrayView<float, kFftSize> time_data,
                rtc::ArrayView<float, kFftSize> real,
                rtc::ArrayView<float, kFftSize> imag) {
  WebRtc_rdft(kFftSize, 1, time_data.data(), bit_reversal_state_.data(),
              tables_.data());

  imag[0] = 0;
  real[0] = time_data[0];

  imag[kFftSizeBy2Plus1 - 1] = 0;
  real[kFftSizeBy2Plus1 - 1] = time_data[1];

  for (size_t i = 1; i < kFftSizeBy2Plus1 - 1; ++i) {
    real[i] = time_data[2 * i];
    imag[i] = time_data[2 * i + 1];
  }
}

void NrFft::Ifft(rtc::ArrayView<const float> real,
                 rtc::ArrayView<const float> imag,
                 rtc::ArrayView<float> time_data) {
  time_data[0] = real[0];
  time_data[1] = real[kFftSizeBy2Plus1 - 1];
  for (size_t i = 1; i < kFftSizeBy2Plus1 - 1; ++i) {
    time_data[2 * i] = real[i];
    time_data[2 * i + 1] = imag[i];
  }
  WebRtc_rdft(kFftSize, -1, time_data.data(), bit_reversal_state_.data(),
              tables_.data());

  // Scale the output
  constexpr float kScaling = 2.f / kFftSize;
  for (float& d : time_data) {
    d *= kScaling;
  }
}

}  // namespace webrtc
