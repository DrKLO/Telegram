/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/auto_correlation.h"

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {
namespace {

constexpr int kAutoCorrelationFftOrder = 9;  // Length-512 FFT.
static_assert(1 << kAutoCorrelationFftOrder >
                  kNumInvertedLags12kHz + kBufSize12kHz - kMaxPitch12kHz,
              "");

}  // namespace

AutoCorrelationCalculator::AutoCorrelationCalculator()
    : fft_(1 << kAutoCorrelationFftOrder, Pffft::FftType::kReal),
      tmp_(fft_.CreateBuffer()),
      X_(fft_.CreateBuffer()),
      H_(fft_.CreateBuffer()) {}

AutoCorrelationCalculator::~AutoCorrelationCalculator() = default;

// The auto-correlations coefficients are computed as follows:
// |.........|...........|  <- pitch buffer
//           [ x (fixed) ]
// [   y_0   ]
//         [ y_{m-1} ]
// x and y are sub-array of equal length; x is never moved, whereas y slides.
// The cross-correlation between y_0 and x corresponds to the auto-correlation
// for the maximum pitch period. Hence, the first value in |auto_corr| has an
// inverted lag equal to 0 that corresponds to a lag equal to the maximum
// pitch period.
void AutoCorrelationCalculator::ComputeOnPitchBuffer(
    rtc::ArrayView<const float, kBufSize12kHz> pitch_buf,
    rtc::ArrayView<float, kNumInvertedLags12kHz> auto_corr) {
  RTC_DCHECK_LT(auto_corr.size(), kMaxPitch12kHz);
  RTC_DCHECK_GT(pitch_buf.size(), kMaxPitch12kHz);
  constexpr size_t kFftFrameSize = 1 << kAutoCorrelationFftOrder;
  constexpr size_t kConvolutionLength = kBufSize12kHz - kMaxPitch12kHz;
  static_assert(kConvolutionLength == kFrameSize20ms12kHz,
                "Mismatch between pitch buffer size, frame size and maximum "
                "pitch period.");
  static_assert(kFftFrameSize > kNumInvertedLags12kHz + kConvolutionLength,
                "The FFT length is not sufficiently big to avoid cyclic "
                "convolution errors.");
  auto tmp = tmp_->GetView();

  // Compute the FFT for the reversed reference frame - i.e.,
  // pitch_buf[-kConvolutionLength:].
  std::reverse_copy(pitch_buf.end() - kConvolutionLength, pitch_buf.end(),
                    tmp.begin());
  std::fill(tmp.begin() + kConvolutionLength, tmp.end(), 0.f);
  fft_.ForwardTransform(*tmp_, H_.get(), /*ordered=*/false);

  // Compute the FFT for the sliding frames chunk. The sliding frames are
  // defined as pitch_buf[i:i+kConvolutionLength] where i in
  // [0, kNumInvertedLags12kHz). The chunk includes all of them, hence it is
  // defined as pitch_buf[:kNumInvertedLags12kHz+kConvolutionLength].
  std::copy(pitch_buf.begin(),
            pitch_buf.begin() + kConvolutionLength + kNumInvertedLags12kHz,
            tmp.begin());
  std::fill(tmp.begin() + kNumInvertedLags12kHz + kConvolutionLength, tmp.end(),
            0.f);
  fft_.ForwardTransform(*tmp_, X_.get(), /*ordered=*/false);

  // Convolve in the frequency domain.
  constexpr float kScalingFactor = 1.f / static_cast<float>(kFftFrameSize);
  std::fill(tmp.begin(), tmp.end(), 0.f);
  fft_.FrequencyDomainConvolve(*X_, *H_, tmp_.get(), kScalingFactor);
  fft_.BackwardTransform(*tmp_, tmp_.get(), /*ordered=*/false);

  // Extract the auto-correlation coefficients.
  std::copy(tmp.begin() + kConvolutionLength - 1,
            tmp.begin() + kConvolutionLength + kNumInvertedLags12kHz - 1,
            auto_corr.begin());
}

}  // namespace rnn_vad
}  // namespace webrtc
