/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/vad/vad_audio_proc.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#include "common_audio/third_party/ooura/fft_size_256/fft4g.h"
#include "modules/audio_processing/vad/pitch_internal.h"
#include "modules/audio_processing/vad/pole_zero_filter.h"
#include "modules/audio_processing/vad/vad_audio_proc_internal.h"
#include "rtc_base/checks.h"
extern "C" {
#include "modules/audio_coding/codecs/isac/main/source/filter_functions.h"
#include "modules/audio_coding/codecs/isac/main/source/isac_vad.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/structs.h"
}

namespace webrtc {

// The following structures are declared anonymous in iSAC's structs.h. To
// forward declare them, we use this derived class trick.
struct VadAudioProc::PitchAnalysisStruct : public ::PitchAnalysisStruct {};
struct VadAudioProc::PreFiltBankstr : public ::PreFiltBankstr {};

static constexpr float kFrequencyResolution =
    kSampleRateHz / static_cast<float>(VadAudioProc::kDftSize);
static constexpr int kSilenceRms = 5;

// TODO(turajs): Make a Create or Init for VadAudioProc.
VadAudioProc::VadAudioProc()
    : audio_buffer_(),
      num_buffer_samples_(kNumPastSignalSamples),
      log_old_gain_(-2),
      old_lag_(50),  // Arbitrary but valid as pitch-lag (in samples).
      pitch_analysis_handle_(new PitchAnalysisStruct),
      pre_filter_handle_(new PreFiltBankstr),
      high_pass_filter_(PoleZeroFilter::Create(kCoeffNumerator,
                                               kFilterOrder,
                                               kCoeffDenominator,
                                               kFilterOrder)) {
  static_assert(kNumPastSignalSamples + kNumSubframeSamples ==
                    sizeof(kLpcAnalWin) / sizeof(kLpcAnalWin[0]),
                "lpc analysis window incorrect size");
  static_assert(kLpcOrder + 1 == sizeof(kCorrWeight) / sizeof(kCorrWeight[0]),
                "correlation weight incorrect size");

  // TODO(turajs): Are we doing too much in the constructor?
  float data[kDftSize];
  // Make FFT to initialize.
  ip_[0] = 0;
  WebRtc_rdft(kDftSize, 1, data, ip_, w_fft_);
  // TODO(turajs): Need to initialize high-pass filter.

  // Initialize iSAC components.
  WebRtcIsac_InitPreFilterbank(pre_filter_handle_.get());
  WebRtcIsac_InitPitchAnalysis(pitch_analysis_handle_.get());
}

VadAudioProc::~VadAudioProc() {}

void VadAudioProc::ResetBuffer() {
  memcpy(audio_buffer_, &audio_buffer_[kNumSamplesToProcess],
         sizeof(audio_buffer_[0]) * kNumPastSignalSamples);
  num_buffer_samples_ = kNumPastSignalSamples;
}

int VadAudioProc::ExtractFeatures(const int16_t* frame,
                                  size_t length,
                                  AudioFeatures* features) {
  features->num_frames = 0;
  if (length != kNumSubframeSamples) {
    return -1;
  }

  // High-pass filter to remove the DC component and very low frequency content.
  // We have experienced that this high-pass filtering improves voice/non-voiced
  // classification.
  if (high_pass_filter_->Filter(frame, kNumSubframeSamples,
                                &audio_buffer_[num_buffer_samples_]) != 0) {
    return -1;
  }

  num_buffer_samples_ += kNumSubframeSamples;
  if (num_buffer_samples_ < kBufferLength) {
    return 0;
  }
  RTC_DCHECK_EQ(num_buffer_samples_, kBufferLength);
  features->num_frames = kNum10msSubframes;
  features->silence = false;

  Rms(features->rms, kMaxNumFrames);
  for (size_t i = 0; i < kNum10msSubframes; ++i) {
    if (features->rms[i] < kSilenceRms) {
      // PitchAnalysis can cause NaNs in the pitch gain if it's fed silence.
      // Bail out here instead.
      features->silence = true;
      ResetBuffer();
      return 0;
    }
  }

  PitchAnalysis(features->log_pitch_gain, features->pitch_lag_hz,
                kMaxNumFrames);
  FindFirstSpectralPeaks(features->spectral_peak, kMaxNumFrames);
  ResetBuffer();
  return 0;
}

// Computes |kLpcOrder + 1| correlation coefficients.
void VadAudioProc::SubframeCorrelation(double* corr,
                                       size_t length_corr,
                                       size_t subframe_index) {
  RTC_DCHECK_GE(length_corr, kLpcOrder + 1);
  double windowed_audio[kNumSubframeSamples + kNumPastSignalSamples];
  size_t buffer_index = subframe_index * kNumSubframeSamples;

  for (size_t n = 0; n < kNumSubframeSamples + kNumPastSignalSamples; n++)
    windowed_audio[n] = audio_buffer_[buffer_index++] * kLpcAnalWin[n];

  WebRtcIsac_AutoCorr(corr, windowed_audio,
                      kNumSubframeSamples + kNumPastSignalSamples, kLpcOrder);
}

// Compute |kNum10msSubframes| sets of LPC coefficients, one per 10 ms input.
// The analysis window is 15 ms long and it is centered on the first half of
// each 10ms sub-frame. This is equivalent to computing LPC coefficients for the
// first half of each 10 ms subframe.
void VadAudioProc::GetLpcPolynomials(double* lpc, size_t length_lpc) {
  RTC_DCHECK_GE(length_lpc, kNum10msSubframes * (kLpcOrder + 1));
  double corr[kLpcOrder + 1];
  double reflec_coeff[kLpcOrder];
  for (size_t i = 0, offset_lpc = 0; i < kNum10msSubframes;
       i++, offset_lpc += kLpcOrder + 1) {
    SubframeCorrelation(corr, kLpcOrder + 1, i);
    corr[0] *= 1.0001;
    // This makes Lev-Durb a bit more stable.
    for (size_t k = 0; k < kLpcOrder + 1; k++) {
      corr[k] *= kCorrWeight[k];
    }
    WebRtcIsac_LevDurb(&lpc[offset_lpc], reflec_coeff, corr, kLpcOrder);
  }
}

// Fit a second order curve to these 3 points and find the location of the
// extremum. The points are inverted before curve fitting.
static float QuadraticInterpolation(float prev_val,
                                    float curr_val,
                                    float next_val) {
  // Doing the interpolation in |1 / A(z)|^2.
  float fractional_index = 0;
  next_val = 1.0f / next_val;
  prev_val = 1.0f / prev_val;
  curr_val = 1.0f / curr_val;

  fractional_index =
      -(next_val - prev_val) * 0.5f / (next_val + prev_val - 2.f * curr_val);
  RTC_DCHECK_LT(fabs(fractional_index), 1);
  return fractional_index;
}

// 1 / A(z), where A(z) is defined by |lpc| is a model of the spectral envelope
// of the input signal. The local maximum of the spectral envelope corresponds
// with the local minimum of A(z). It saves complexity, as we save one
// inversion. Furthermore, we find the first local maximum of magnitude squared,
// to save on one square root.
void VadAudioProc::FindFirstSpectralPeaks(double* f_peak,
                                          size_t length_f_peak) {
  RTC_DCHECK_GE(length_f_peak, kNum10msSubframes);
  double lpc[kNum10msSubframes * (kLpcOrder + 1)];
  // For all sub-frames.
  GetLpcPolynomials(lpc, kNum10msSubframes * (kLpcOrder + 1));

  const size_t kNumDftCoefficients = kDftSize / 2 + 1;
  float data[kDftSize];

  for (size_t i = 0; i < kNum10msSubframes; i++) {
    // Convert to float with zero pad.
    memset(data, 0, sizeof(data));
    for (size_t n = 0; n < kLpcOrder + 1; n++) {
      data[n] = static_cast<float>(lpc[i * (kLpcOrder + 1) + n]);
    }
    // Transform to frequency domain.
    WebRtc_rdft(kDftSize, 1, data, ip_, w_fft_);

    size_t index_peak = 0;
    float prev_magn_sqr = data[0] * data[0];
    float curr_magn_sqr = data[2] * data[2] + data[3] * data[3];
    float next_magn_sqr;
    bool found_peak = false;
    for (size_t n = 2; n < kNumDftCoefficients - 1; n++) {
      next_magn_sqr =
          data[2 * n] * data[2 * n] + data[2 * n + 1] * data[2 * n + 1];
      if (curr_magn_sqr < prev_magn_sqr && curr_magn_sqr < next_magn_sqr) {
        found_peak = true;
        index_peak = n - 1;
        break;
      }
      prev_magn_sqr = curr_magn_sqr;
      curr_magn_sqr = next_magn_sqr;
    }
    float fractional_index = 0;
    if (!found_peak) {
      // Checking if |kNumDftCoefficients - 1| is the local minimum.
      next_magn_sqr = data[1] * data[1];
      if (curr_magn_sqr < prev_magn_sqr && curr_magn_sqr < next_magn_sqr) {
        index_peak = kNumDftCoefficients - 1;
      }
    } else {
      // A peak is found, do a simple quadratic interpolation to get a more
      // accurate estimate of the peak location.
      fractional_index =
          QuadraticInterpolation(prev_magn_sqr, curr_magn_sqr, next_magn_sqr);
    }
    f_peak[i] = (index_peak + fractional_index) * kFrequencyResolution;
  }
}

// Using iSAC functions to estimate pitch gains & lags.
void VadAudioProc::PitchAnalysis(double* log_pitch_gains,
                                 double* pitch_lags_hz,
                                 size_t length) {
  // TODO(turajs): This can be "imported" from iSAC & and the next two
  // constants.
  RTC_DCHECK_GE(length, kNum10msSubframes);
  const int kNumPitchSubframes = 4;
  double gains[kNumPitchSubframes];
  double lags[kNumPitchSubframes];

  const int kNumSubbandFrameSamples = 240;
  const int kNumLookaheadSamples = 24;

  float lower[kNumSubbandFrameSamples];
  float upper[kNumSubbandFrameSamples];
  double lower_lookahead[kNumSubbandFrameSamples];
  double upper_lookahead[kNumSubbandFrameSamples];
  double lower_lookahead_pre_filter[kNumSubbandFrameSamples +
                                    kNumLookaheadSamples];

  // Split signal to lower and upper bands
  WebRtcIsac_SplitAndFilterFloat(&audio_buffer_[kNumPastSignalSamples], lower,
                                 upper, lower_lookahead, upper_lookahead,
                                 pre_filter_handle_.get());
  WebRtcIsac_PitchAnalysis(lower_lookahead, lower_lookahead_pre_filter,
                           pitch_analysis_handle_.get(), lags, gains);

  // Lags are computed on lower-band signal with sampling rate half of the
  // input signal.
  GetSubframesPitchParameters(
      kSampleRateHz / 2, gains, lags, kNumPitchSubframes, kNum10msSubframes,
      &log_old_gain_, &old_lag_, log_pitch_gains, pitch_lags_hz);
}

void VadAudioProc::Rms(double* rms, size_t length_rms) {
  RTC_DCHECK_GE(length_rms, kNum10msSubframes);
  size_t offset = kNumPastSignalSamples;
  for (size_t i = 0; i < kNum10msSubframes; i++) {
    rms[i] = 0;
    for (size_t n = 0; n < kNumSubframeSamples; n++, offset++)
      rms[i] += audio_buffer_[offset] * audio_buffer_[offset];
    rms[i] = sqrt(rms[i] / kNumSubframeSamples);
  }
}

}  // namespace webrtc
