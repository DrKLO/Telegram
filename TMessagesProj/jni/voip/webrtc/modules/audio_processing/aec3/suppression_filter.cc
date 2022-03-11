/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/suppression_filter.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <functional>
#include <iterator>

#include "modules/audio_processing/aec3/vector_math.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

// Hanning window from Matlab command win = sqrt(hanning(128)).
const float kSqrtHanning[kFftLength] = {
    0.00000000000000f, 0.02454122852291f, 0.04906767432742f, 0.07356456359967f,
    0.09801714032956f, 0.12241067519922f, 0.14673047445536f, 0.17096188876030f,
    0.19509032201613f, 0.21910124015687f, 0.24298017990326f, 0.26671275747490f,
    0.29028467725446f, 0.31368174039889f, 0.33688985339222f, 0.35989503653499f,
    0.38268343236509f, 0.40524131400499f, 0.42755509343028f, 0.44961132965461f,
    0.47139673682600f, 0.49289819222978f, 0.51410274419322f, 0.53499761988710f,
    0.55557023301960f, 0.57580819141785f, 0.59569930449243f, 0.61523159058063f,
    0.63439328416365f, 0.65317284295378f, 0.67155895484702f, 0.68954054473707f,
    0.70710678118655f, 0.72424708295147f, 0.74095112535496f, 0.75720884650648f,
    0.77301045336274f, 0.78834642762661f, 0.80320753148064f, 0.81758481315158f,
    0.83146961230255f, 0.84485356524971f, 0.85772861000027f, 0.87008699110871f,
    0.88192126434835f, 0.89322430119552f, 0.90398929312344f, 0.91420975570353f,
    0.92387953251129f, 0.93299279883474f, 0.94154406518302f, 0.94952818059304f,
    0.95694033573221f, 0.96377606579544f, 0.97003125319454f, 0.97570213003853f,
    0.98078528040323f, 0.98527764238894f, 0.98917650996478f, 0.99247953459871f,
    0.99518472667220f, 0.99729045667869f, 0.99879545620517f, 0.99969881869620f,
    1.00000000000000f, 0.99969881869620f, 0.99879545620517f, 0.99729045667869f,
    0.99518472667220f, 0.99247953459871f, 0.98917650996478f, 0.98527764238894f,
    0.98078528040323f, 0.97570213003853f, 0.97003125319454f, 0.96377606579544f,
    0.95694033573221f, 0.94952818059304f, 0.94154406518302f, 0.93299279883474f,
    0.92387953251129f, 0.91420975570353f, 0.90398929312344f, 0.89322430119552f,
    0.88192126434835f, 0.87008699110871f, 0.85772861000027f, 0.84485356524971f,
    0.83146961230255f, 0.81758481315158f, 0.80320753148064f, 0.78834642762661f,
    0.77301045336274f, 0.75720884650648f, 0.74095112535496f, 0.72424708295147f,
    0.70710678118655f, 0.68954054473707f, 0.67155895484702f, 0.65317284295378f,
    0.63439328416365f, 0.61523159058063f, 0.59569930449243f, 0.57580819141785f,
    0.55557023301960f, 0.53499761988710f, 0.51410274419322f, 0.49289819222978f,
    0.47139673682600f, 0.44961132965461f, 0.42755509343028f, 0.40524131400499f,
    0.38268343236509f, 0.35989503653499f, 0.33688985339222f, 0.31368174039889f,
    0.29028467725446f, 0.26671275747490f, 0.24298017990326f, 0.21910124015687f,
    0.19509032201613f, 0.17096188876030f, 0.14673047445536f, 0.12241067519922f,
    0.09801714032956f, 0.07356456359967f, 0.04906767432742f, 0.02454122852291f};

}  // namespace

SuppressionFilter::SuppressionFilter(Aec3Optimization optimization,
                                     int sample_rate_hz,
                                     size_t num_capture_channels)
    : optimization_(optimization),
      sample_rate_hz_(sample_rate_hz),
      num_capture_channels_(num_capture_channels),
      fft_(),
      e_output_old_(NumBandsForRate(sample_rate_hz_),
                    std::vector<std::array<float, kFftLengthBy2>>(
                        num_capture_channels_)) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz_));
  for (size_t b = 0; b < e_output_old_.size(); ++b) {
    for (size_t ch = 0; ch < e_output_old_[b].size(); ++ch) {
      e_output_old_[b][ch].fill(0.f);
    }
  }
}

SuppressionFilter::~SuppressionFilter() = default;

void SuppressionFilter::ApplyGain(
    rtc::ArrayView<const FftData> comfort_noise,
    rtc::ArrayView<const FftData> comfort_noise_high_band,
    const std::array<float, kFftLengthBy2Plus1>& suppression_gain,
    float high_bands_gain,
    rtc::ArrayView<const FftData> E_lowest_band,
    std::vector<std::vector<std::vector<float>>>* e) {
  RTC_DCHECK(e);
  RTC_DCHECK_EQ(e->size(), NumBandsForRate(sample_rate_hz_));

  // Comfort noise gain is sqrt(1-g^2), where g is the suppression gain.
  std::array<float, kFftLengthBy2Plus1> noise_gain;
  for (size_t i = 0; i < kFftLengthBy2Plus1; ++i) {
    noise_gain[i] = 1.f - suppression_gain[i] * suppression_gain[i];
  }
  aec3::VectorMath(optimization_).Sqrt(noise_gain);

  const float high_bands_noise_scaling =
      0.4f * std::sqrt(1.f - high_bands_gain * high_bands_gain);

  for (size_t ch = 0; ch < num_capture_channels_; ++ch) {
    FftData E;

    // Analysis filterbank.
    E.Assign(E_lowest_band[ch]);

    for (size_t i = 0; i < kFftLengthBy2Plus1; ++i) {
      // Apply suppression gains.
      float E_real = E.re[i] * suppression_gain[i];
      float E_imag = E.im[i] * suppression_gain[i];

      // Scale and add the comfort noise.
      E.re[i] = E_real + noise_gain[i] * comfort_noise[ch].re[i];
      E.im[i] = E_imag + noise_gain[i] * comfort_noise[ch].im[i];
    }

    // Synthesis filterbank.
    std::array<float, kFftLength> e_extended;
    constexpr float kIfftNormalization = 2.f / kFftLength;
    fft_.Ifft(E, &e_extended);

    float* e0 = (*e)[0][ch].data();
    float* e0_old = e_output_old_[0][ch].data();

    // Window and add the first half of e_extended with the second half of
    // e_extended from the previous block.
    for (size_t i = 0; i < kFftLengthBy2; ++i) {
      float e0_i = e0_old[i] * kSqrtHanning[kFftLengthBy2 + i];
      e0_i += e_extended[i] * kSqrtHanning[i];
      e0[i] = e0_i * kIfftNormalization;
    }

    // The second half of e_extended is stored for the succeeding frame.
    std::copy(e_extended.begin() + kFftLengthBy2,
              e_extended.begin() + kFftLength,
              std::begin(e_output_old_[0][ch]));

    // Apply suppression gain to upper bands.
    for (size_t b = 1; b < e->size(); ++b) {
      float* e_band = (*e)[b][ch].data();
      for (size_t i = 0; i < kFftLengthBy2; ++i) {
        e_band[i] *= high_bands_gain;
      }
    }

    // Add comfort noise to band 1.
    if (e->size() > 1) {
      E.Assign(comfort_noise_high_band[ch]);
      std::array<float, kFftLength> time_domain_high_band_noise;
      fft_.Ifft(E, &time_domain_high_band_noise);

      float* e1 = (*e)[1][ch].data();
      const float gain = high_bands_noise_scaling * kIfftNormalization;
      for (size_t i = 0; i < kFftLengthBy2; ++i) {
        e1[i] += time_domain_high_band_noise[i] * gain;
      }
    }

    // Delay upper bands to match the delay of the filter bank.
    for (size_t b = 1; b < e->size(); ++b) {
      float* e_band = (*e)[b][ch].data();
      float* e_band_old = e_output_old_[b][ch].data();
      for (size_t i = 0; i < kFftLengthBy2; ++i) {
        std::swap(e_band[i], e_band_old[i]);
      }
    }

    // Clamp output of all bands.
    for (size_t b = 0; b < e->size(); ++b) {
      float* e_band = (*e)[b][ch].data();
      for (size_t i = 0; i < kFftLengthBy2; ++i) {
        e_band[i] = rtc::SafeClamp(e_band[i], -32768.f, 32767.f);
      }
    }
  }
}

}  // namespace webrtc
