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
                                     int sample_rate_hz)
    : optimization_(optimization),
      sample_rate_hz_(sample_rate_hz),
      fft_(),
      e_output_old_(NumBandsForRate(sample_rate_hz_)) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz_));
  std::for_each(e_output_old_.begin(), e_output_old_.end(),
                [](std::array<float, kFftLengthBy2>& a) { a.fill(0.f); });
}

SuppressionFilter::~SuppressionFilter() = default;

void SuppressionFilter::ApplyGain(
    const FftData& comfort_noise,
    const FftData& comfort_noise_high_band,
    const std::array<float, kFftLengthBy2Plus1>& suppression_gain,
    float high_bands_gain,
    const FftData& E_lowest_band,
    std::vector<std::vector<float>>* e) {
  RTC_DCHECK(e);
  RTC_DCHECK_EQ(e->size(), NumBandsForRate(sample_rate_hz_));
  FftData E;

  // Analysis filterbank.
  E.Assign(E_lowest_band);

  // Apply gain.
  std::transform(suppression_gain.begin(), suppression_gain.end(), E.re.begin(),
                 E.re.begin(), std::multiplies<float>());
  std::transform(suppression_gain.begin(), suppression_gain.end(), E.im.begin(),
                 E.im.begin(), std::multiplies<float>());

  // Comfort noise gain is sqrt(1-g^2), where g is the suppression gain.
  std::array<float, kFftLengthBy2Plus1> noise_gain;
  std::transform(suppression_gain.begin(), suppression_gain.end(),
                 noise_gain.begin(), [](float g) { return 1.f - g * g; });
  aec3::VectorMath(optimization_).Sqrt(noise_gain);

  // Scale and add the comfort noise.
  for (size_t k = 0; k < kFftLengthBy2Plus1; k++) {
    E.re[k] += noise_gain[k] * comfort_noise.re[k];
    E.im[k] += noise_gain[k] * comfort_noise.im[k];
  }

  // Synthesis filterbank.
  std::array<float, kFftLength> e_extended;
  constexpr float kIfftNormalization = 2.f / kFftLength;

  fft_.Ifft(E, &e_extended);
  std::transform(e_output_old_[0].begin(), e_output_old_[0].end(),
                 std::begin(kSqrtHanning) + kFftLengthBy2, (*e)[0].begin(),
                 [&](float a, float b) { return kIfftNormalization * a * b; });
  std::transform(e_extended.begin(), e_extended.begin() + kFftLengthBy2,
                 std::begin(kSqrtHanning), e_extended.begin(),
                 [&](float a, float b) { return kIfftNormalization * a * b; });
  std::transform((*e)[0].begin(), (*e)[0].end(), e_extended.begin(),
                 (*e)[0].begin(), std::plus<float>());
  std::for_each((*e)[0].begin(), (*e)[0].end(), [](float& x_k) {
    x_k = rtc::SafeClamp(x_k, -32768.f, 32767.f);
  });
  std::copy(e_extended.begin() + kFftLengthBy2, e_extended.begin() + kFftLength,
            std::begin(e_output_old_[0]));

  if (e->size() > 1) {
    // Form time-domain high-band noise.
    std::array<float, kFftLength> time_domain_high_band_noise;
    std::transform(comfort_noise_high_band.re.begin(),
                   comfort_noise_high_band.re.end(), E.re.begin(),
                   [&](float a) { return kIfftNormalization * a; });
    std::transform(comfort_noise_high_band.im.begin(),
                   comfort_noise_high_band.im.end(), E.im.begin(),
                   [&](float a) { return kIfftNormalization * a; });
    fft_.Ifft(E, &time_domain_high_band_noise);

    // Scale and apply the noise to the signals.
    const float high_bands_noise_scaling =
        0.4f * std::sqrt(1.f - high_bands_gain * high_bands_gain);

    std::transform(
        (*e)[1].begin(), (*e)[1].end(), time_domain_high_band_noise.begin(),
        (*e)[1].begin(), [&](float a, float b) {
          return std::max(
              std::min(b * high_bands_noise_scaling + high_bands_gain * a,
                       32767.0f),
              -32768.0f);
        });

    if (e->size() > 2) {
      RTC_DCHECK_EQ(3, e->size());
      std::for_each((*e)[2].begin(), (*e)[2].end(), [&](float& a) {
        a = rtc::SafeClamp(a * high_bands_gain, -32768.f, 32767.f);
      });
    }

    std::array<float, kFftLengthBy2> tmp;
    for (size_t k = 1; k < e->size(); ++k) {
      std::copy((*e)[k].begin(), (*e)[k].end(), tmp.begin());
      std::copy(e_output_old_[k].begin(), e_output_old_[k].end(),
                (*e)[k].begin());
      std::copy(tmp.begin(), tmp.end(), e_output_old_[k].begin());
    }
  }
}

}  // namespace webrtc
