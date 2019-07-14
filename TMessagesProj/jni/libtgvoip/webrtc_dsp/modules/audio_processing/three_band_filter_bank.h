/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_THREE_BAND_FILTER_BANK_H_
#define MODULES_AUDIO_PROCESSING_THREE_BAND_FILTER_BANK_H_

#include <cstring>
#include <memory>
#include <vector>

#include "common_audio/sparse_fir_filter.h"

namespace webrtc {

// An implementation of a 3-band FIR filter-bank with DCT modulation, similar to
// the proposed in "Multirate Signal Processing for Communication Systems" by
// Fredric J Harris.
// The low-pass filter prototype has these characteristics:
// * Pass-band ripple = 0.3dB
// * Pass-band frequency = 0.147 (7kHz at 48kHz)
// * Stop-band attenuation = 40dB
// * Stop-band frequency = 0.192 (9.2kHz at 48kHz)
// * Delay = 24 samples (500us at 48kHz)
// * Linear phase
// This filter bank does not satisfy perfect reconstruction. The SNR after
// analysis and synthesis (with no processing in between) is approximately 9.5dB
// depending on the input signal after compensating for the delay.
class ThreeBandFilterBank final {
 public:
  explicit ThreeBandFilterBank(size_t length);
  ~ThreeBandFilterBank();

  // Splits |in| into 3 downsampled frequency bands in |out|.
  // |length| is the |in| length. Each of the 3 bands of |out| has to have a
  // length of |length| / 3.
  void Analysis(const float* in, size_t length, float* const* out);

  // Merges the 3 downsampled frequency bands in |in| into |out|.
  // |split_length| is the length of each band of |in|. |out| has to have at
  // least a length of 3 * |split_length|.
  void Synthesis(const float* const* in, size_t split_length, float* out);

 private:
  void DownModulate(const float* in,
                    size_t split_length,
                    size_t offset,
                    float* const* out);
  void UpModulate(const float* const* in,
                  size_t split_length,
                  size_t offset,
                  float* out);

  std::vector<float> in_buffer_;
  std::vector<float> out_buffer_;
  std::vector<std::unique_ptr<SparseFIRFilter>> analysis_filters_;
  std::vector<std::unique_ptr<SparseFIRFilter>> synthesis_filters_;
  std::vector<std::vector<float>> dct_modulation_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_THREE_BAND_FILTER_BANK_H_
