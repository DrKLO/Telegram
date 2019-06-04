/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_FFT_UTIL_H_
#define MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_FFT_UTIL_H_

#include <array>
#include <complex>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/rnn_vad/common.h"
#include "third_party/rnnoise/src/kiss_fft.h"

namespace webrtc {
namespace rnn_vad {

// FFT implementation wrapper for the band-wise analysis step in which 20 ms
// frames at 24 kHz are analyzed in the frequency domain. The goal of this class
// are (i) making easy to switch to another FFT implementation, (ii) own the
// input buffer for the FFT and (iii) apply a windowing function before
// computing the FFT.
class BandAnalysisFft {
 public:
  BandAnalysisFft();
  BandAnalysisFft(const BandAnalysisFft&) = delete;
  BandAnalysisFft& operator=(const BandAnalysisFft&) = delete;
  ~BandAnalysisFft();
  // Applies a windowing function to |samples|, computes the real forward FFT
  // and writes the result in |dst|.
  void ForwardFft(rtc::ArrayView<const float> samples,
                  rtc::ArrayView<std::complex<float>> dst);

 private:
  static_assert((kFrameSize20ms24kHz & 1) == 0,
                "kFrameSize20ms24kHz must be even.");
  const std::array<float, kFrameSize20ms24kHz / 2> half_window_;
  std::array<std::complex<float>, kFrameSize20ms24kHz> input_buf_{};
  rnnoise::KissFft fft_;
};

}  // namespace rnn_vad
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_FFT_UTIL_H_
