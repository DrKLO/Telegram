/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/pitch_search_internal.h"

#include <stdlib.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <numeric>

#include "modules/audio_processing/agc2/rnn_vad/common.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {
namespace {

// Converts a lag to an inverted lag (only for 24kHz).
size_t GetInvertedLag(size_t lag) {
  RTC_DCHECK_LE(lag, kMaxPitch24kHz);
  return kMaxPitch24kHz - lag;
}

float ComputeAutoCorrelationCoeff(rtc::ArrayView<const float> pitch_buf,
                                  size_t inv_lag,
                                  size_t max_pitch_period) {
  RTC_DCHECK_LT(inv_lag, pitch_buf.size());
  RTC_DCHECK_LT(max_pitch_period, pitch_buf.size());
  RTC_DCHECK_LE(inv_lag, max_pitch_period);
  // TODO(bugs.webrtc.org/9076): Maybe optimize using vectorization.
  return std::inner_product(pitch_buf.begin() + max_pitch_period,
                            pitch_buf.end(), pitch_buf.begin() + inv_lag, 0.f);
}

// Computes a pseudo-interpolation offset for an estimated pitch period |lag| by
// looking at the auto-correlation coefficients in the neighborhood of |lag|.
// (namely, |prev_auto_corr|, |lag_auto_corr| and |next_auto_corr|). The output
// is a lag in {-1, 0, +1}.
// TODO(bugs.webrtc.org/9076): Consider removing pseudo-i since it
// is relevant only if the spectral analysis works at a sample rate that is
// twice as that of the pitch buffer (not so important instead for the estimated
// pitch period feature fed into the RNN).
int GetPitchPseudoInterpolationOffset(size_t lag,
                                      float prev_auto_corr,
                                      float lag_auto_corr,
                                      float next_auto_corr) {
  const float& a = prev_auto_corr;
  const float& b = lag_auto_corr;
  const float& c = next_auto_corr;

  int offset = 0;
  if ((c - a) > 0.7f * (b - a)) {
    offset = 1;  // |c| is the largest auto-correlation coefficient.
  } else if ((a - c) > 0.7f * (b - c)) {
    offset = -1;  // |a| is the largest auto-correlation coefficient.
  }
  return offset;
}

// Refines a pitch period |lag| encoded as lag with pseudo-interpolation. The
// output sample rate is twice as that of |lag|.
size_t PitchPseudoInterpolationLagPitchBuf(
    size_t lag,
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buf) {
  int offset = 0;
  // Cannot apply pseudo-interpolation at the boundaries.
  if (lag > 0 && lag < kMaxPitch24kHz) {
    offset = GetPitchPseudoInterpolationOffset(
        lag,
        ComputeAutoCorrelationCoeff(pitch_buf, GetInvertedLag(lag - 1),
                                    kMaxPitch24kHz),
        ComputeAutoCorrelationCoeff(pitch_buf, GetInvertedLag(lag),
                                    kMaxPitch24kHz),
        ComputeAutoCorrelationCoeff(pitch_buf, GetInvertedLag(lag + 1),
                                    kMaxPitch24kHz));
  }
  return 2 * lag + offset;
}

// Refines a pitch period |inv_lag| encoded as inverted lag with
// pseudo-interpolation. The output sample rate is twice as that of
// |inv_lag|.
size_t PitchPseudoInterpolationInvLagAutoCorr(
    size_t inv_lag,
    rtc::ArrayView<const float> auto_corr) {
  int offset = 0;
  // Cannot apply pseudo-interpolation at the boundaries.
  if (inv_lag > 0 && inv_lag < auto_corr.size() - 1) {
    offset = GetPitchPseudoInterpolationOffset(inv_lag, auto_corr[inv_lag + 1],
                                               auto_corr[inv_lag],
                                               auto_corr[inv_lag - 1]);
  }
  // TODO(bugs.webrtc.org/9076): When retraining, check if |offset| below should
  // be subtracted since |inv_lag| is an inverted lag but offset is a lag.
  return 2 * inv_lag + offset;
}

// Integer multipliers used in CheckLowerPitchPeriodsAndComputePitchGain() when
// looking for sub-harmonics.
// The values have been chosen to serve the following algorithm. Given the
// initial pitch period T, we examine whether one of its harmonics is the true
// fundamental frequency. We consider T/k with k in {2, ..., 15}. For each of
// these harmonics, in addition to the pitch gain of itself, we choose one
// multiple of its pitch period, n*T/k, to validate it (by averaging their pitch
// gains). The multiplier n is chosen so that n*T/k is used only one time over
// all k. When for example k = 4, we should also expect a peak at 3*T/4. When
// k = 8 instead we don't want to look at 2*T/8, since we have already checked
// T/4 before. Instead, we look at T*3/8.
// The array can be generate in Python as follows:
//   from fractions import Fraction
//   # Smallest positive integer not in X.
//   def mex(X):
//     for i in range(1, int(max(X)+2)):
//       if i not in X:
//         return i
//   # Visited multiples of the period.
//   S = {1}
//   for n in range(2, 16):
//     sn = mex({n * i for i in S} | {1})
//     S = S | {Fraction(1, n), Fraction(sn, n)}
//     print(sn, end=', ')
constexpr std::array<int, 14> kSubHarmonicMultipliers = {
    {3, 2, 3, 2, 5, 2, 3, 2, 3, 2, 5, 2, 3, 2}};

// Initial pitch period candidate thresholds for ComputePitchGainThreshold() for
// a sample rate of 24 kHz. Computed as [5*k*k for k in range(16)].
constexpr std::array<int, 14> kInitialPitchPeriodThresholds = {
    {20, 45, 80, 125, 180, 245, 320, 405, 500, 605, 720, 845, 980, 1125}};

}  // namespace

void Decimate2x(rtc::ArrayView<const float, kBufSize24kHz> src,
                rtc::ArrayView<float, kBufSize12kHz> dst) {
  // TODO(bugs.webrtc.org/9076): Consider adding anti-aliasing filter.
  static_assert(2 * dst.size() == src.size(), "");
  for (size_t i = 0; i < dst.size(); ++i) {
    dst[i] = src[2 * i];
  }
}

float ComputePitchGainThreshold(int candidate_pitch_period,
                                int pitch_period_ratio,
                                int initial_pitch_period,
                                float initial_pitch_gain,
                                int prev_pitch_period,
                                float prev_pitch_gain) {
  // Map arguments to more compact aliases.
  const int& t1 = candidate_pitch_period;
  const int& k = pitch_period_ratio;
  const int& t0 = initial_pitch_period;
  const float& g0 = initial_pitch_gain;
  const int& t_prev = prev_pitch_period;
  const float& g_prev = prev_pitch_gain;

  // Validate input.
  RTC_DCHECK_GE(t1, 0);
  RTC_DCHECK_GE(k, 2);
  RTC_DCHECK_GE(t0, 0);
  RTC_DCHECK_GE(t_prev, 0);

  // Compute a term that lowers the threshold when |t1| is close to the last
  // estimated period |t_prev| - i.e., pitch tracking.
  float lower_threshold_term = 0;
  if (abs(t1 - t_prev) <= 1) {
    // The candidate pitch period is within 1 sample from the previous one.
    // Make the candidate at |t1| very easy to be accepted.
    lower_threshold_term = g_prev;
  } else if (abs(t1 - t_prev) == 2 &&
             t0 > kInitialPitchPeriodThresholds[k - 2]) {
    // The candidate pitch period is 2 samples far from the previous one and the
    // period |t0| (from which |t1| has been derived) is greater than a
    // threshold. Make |t1| easy to be accepted.
    lower_threshold_term = 0.5f * g_prev;
  }
  // Set the threshold based on the gain of the initial estimate |t0|. Also
  // reduce the chance of false positives caused by a bias towards high
  // frequencies (originating from short-term correlations).
  float threshold = std::max(0.3f, 0.7f * g0 - lower_threshold_term);
  if (static_cast<size_t>(t1) < 3 * kMinPitch24kHz) {
    // High frequency.
    threshold = std::max(0.4f, 0.85f * g0 - lower_threshold_term);
  } else if (static_cast<size_t>(t1) < 2 * kMinPitch24kHz) {
    // Even higher frequency.
    threshold = std::max(0.5f, 0.9f * g0 - lower_threshold_term);
  }
  return threshold;
}

void ComputeSlidingFrameSquareEnergies(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buf,
    rtc::ArrayView<float, kMaxPitch24kHz + 1> yy_values) {
  float yy =
      ComputeAutoCorrelationCoeff(pitch_buf, kMaxPitch24kHz, kMaxPitch24kHz);
  yy_values[0] = yy;
  for (size_t i = 1; i < yy_values.size(); ++i) {
    RTC_DCHECK_LE(i, kMaxPitch24kHz + kFrameSize20ms24kHz);
    RTC_DCHECK_LE(i, kMaxPitch24kHz);
    const float old_coeff = pitch_buf[kMaxPitch24kHz + kFrameSize20ms24kHz - i];
    const float new_coeff = pitch_buf[kMaxPitch24kHz - i];
    yy -= old_coeff * old_coeff;
    yy += new_coeff * new_coeff;
    yy = std::max(0.f, yy);
    yy_values[i] = yy;
  }
}

std::array<size_t, 2> FindBestPitchPeriods(
    rtc::ArrayView<const float> auto_corr,
    rtc::ArrayView<const float> pitch_buf,
    size_t max_pitch_period) {
  // Stores a pitch candidate period and strength information.
  struct PitchCandidate {
    // Pitch period encoded as inverted lag.
    size_t period_inverted_lag = 0;
    // Pitch strength encoded as a ratio.
    float strength_numerator = -1.f;
    float strength_denominator = 0.f;
    // Compare the strength of two pitch candidates.
    bool HasStrongerPitchThan(const PitchCandidate& b) const {
      // Comparing the numerator/denominator ratios without using divisions.
      return strength_numerator * b.strength_denominator >
             b.strength_numerator * strength_denominator;
    }
  };

  RTC_DCHECK_GT(max_pitch_period, auto_corr.size());
  RTC_DCHECK_LT(max_pitch_period, pitch_buf.size());
  const size_t frame_size = pitch_buf.size() - max_pitch_period;
  // TODO(bugs.webrtc.org/9076): Maybe optimize using vectorization.
  float yy =
      std::inner_product(pitch_buf.begin(), pitch_buf.begin() + frame_size + 1,
                         pitch_buf.begin(), 1.f);
  // Search best and second best pitches by looking at the scaled
  // auto-correlation.
  PitchCandidate candidate;
  PitchCandidate best;
  PitchCandidate second_best;
  second_best.period_inverted_lag = 1;
  for (size_t inv_lag = 0; inv_lag < auto_corr.size(); ++inv_lag) {
    // A pitch candidate must have positive correlation.
    if (auto_corr[inv_lag] > 0) {
      candidate.period_inverted_lag = inv_lag;
      candidate.strength_numerator = auto_corr[inv_lag] * auto_corr[inv_lag];
      candidate.strength_denominator = yy;
      if (candidate.HasStrongerPitchThan(second_best)) {
        if (candidate.HasStrongerPitchThan(best)) {
          second_best = best;
          best = candidate;
        } else {
          second_best = candidate;
        }
      }
    }
    // Update |squared_energy_y| for the next inverted lag.
    const float old_coeff = pitch_buf[inv_lag];
    const float new_coeff = pitch_buf[inv_lag + frame_size];
    yy -= old_coeff * old_coeff;
    yy += new_coeff * new_coeff;
    yy = std::max(0.f, yy);
  }
  return {{best.period_inverted_lag, second_best.period_inverted_lag}};
}

size_t RefinePitchPeriod48kHz(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buf,
    rtc::ArrayView<const size_t, 2> inv_lags) {
  // Compute the auto-correlation terms only for neighbors of the given pitch
  // candidates (similar to what is done in ComputePitchAutoCorrelation(), but
  // for a few lag values).
  std::array<float, kNumInvertedLags24kHz> auto_corr;
  auto_corr.fill(0.f);  // Zeros become ignored lags in FindBestPitchPeriods().
  auto is_neighbor = [](size_t i, size_t j) {
    return ((i > j) ? (i - j) : (j - i)) <= 2;
  };
  for (size_t inv_lag = 0; inv_lag < auto_corr.size(); ++inv_lag) {
    if (is_neighbor(inv_lag, inv_lags[0]) || is_neighbor(inv_lag, inv_lags[1]))
      auto_corr[inv_lag] =
          ComputeAutoCorrelationCoeff(pitch_buf, inv_lag, kMaxPitch24kHz);
  }
  // Find best pitch at 24 kHz.
  const auto pitch_candidates_inv_lags = FindBestPitchPeriods(
      {auto_corr.data(), auto_corr.size()},
      {pitch_buf.data(), pitch_buf.size()}, kMaxPitch24kHz);
  const auto inv_lag = pitch_candidates_inv_lags[0];  // Refine the best.
  // Pseudo-interpolation.
  return PitchPseudoInterpolationInvLagAutoCorr(inv_lag, auto_corr);
}

PitchInfo CheckLowerPitchPeriodsAndComputePitchGain(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buf,
    int initial_pitch_period_48kHz,
    PitchInfo prev_pitch_48kHz) {
  RTC_DCHECK_LE(kMinPitch48kHz, initial_pitch_period_48kHz);
  RTC_DCHECK_LE(initial_pitch_period_48kHz, kMaxPitch48kHz);
  // Stores information for a refined pitch candidate.
  struct RefinedPitchCandidate {
    RefinedPitchCandidate() {}
    RefinedPitchCandidate(int period_24kHz, float gain, float xy, float yy)
        : period_24kHz(period_24kHz), gain(gain), xy(xy), yy(yy) {}
    int period_24kHz;
    // Pitch strength information.
    float gain;
    // Additional pitch strength information used for the final estimation of
    // pitch gain.
    float xy;  // Cross-correlation.
    float yy;  // Auto-correlation.
  };

  // Initialize.
  std::array<float, kMaxPitch24kHz + 1> yy_values;
  ComputeSlidingFrameSquareEnergies(pitch_buf,
                                    {yy_values.data(), yy_values.size()});
  const float xx = yy_values[0];
  // Helper lambdas.
  const auto pitch_gain = [](float xy, float yy, float xx) {
    RTC_DCHECK_LE(0.f, xx * yy);
    return xy / std::sqrt(1.f + xx * yy);
  };
  // Initial pitch candidate gain.
  RefinedPitchCandidate best_pitch;
  best_pitch.period_24kHz = std::min(initial_pitch_period_48kHz / 2,
                                     static_cast<int>(kMaxPitch24kHz - 1));
  best_pitch.xy = ComputeAutoCorrelationCoeff(
      pitch_buf, GetInvertedLag(best_pitch.period_24kHz), kMaxPitch24kHz);
  best_pitch.yy = yy_values[best_pitch.period_24kHz];
  best_pitch.gain = pitch_gain(best_pitch.xy, best_pitch.yy, xx);

  // Store the initial pitch period information.
  const size_t initial_pitch_period = best_pitch.period_24kHz;
  const float initial_pitch_gain = best_pitch.gain;

  // Given the initial pitch estimation, check lower periods (i.e., harmonics).
  const auto alternative_period = [](int period, int k, int n) -> int {
    RTC_DCHECK_GT(k, 0);
    return (2 * n * period + k) / (2 * k);  // Same as round(n*period/k).
  };
  for (int k = 2; k < static_cast<int>(kSubHarmonicMultipliers.size() + 2);
       ++k) {
    int candidate_pitch_period = alternative_period(initial_pitch_period, k, 1);
    if (static_cast<size_t>(candidate_pitch_period) < kMinPitch24kHz) {
      break;
    }
    // When looking at |candidate_pitch_period|, we also look at one of its
    // sub-harmonics. |kSubHarmonicMultipliers| is used to know where to look.
    // |k| == 2 is a special case since |candidate_pitch_secondary_period| might
    // be greater than the maximum pitch period.
    int candidate_pitch_secondary_period = alternative_period(
        initial_pitch_period, k, kSubHarmonicMultipliers[k - 2]);
    RTC_DCHECK_GT(candidate_pitch_secondary_period, 0);
    if (k == 2 &&
        candidate_pitch_secondary_period > static_cast<int>(kMaxPitch24kHz)) {
      candidate_pitch_secondary_period = initial_pitch_period;
    }
    RTC_DCHECK_NE(candidate_pitch_period, candidate_pitch_secondary_period)
        << "The lower pitch period and the additional sub-harmonic must not "
           "coincide.";
    // Compute an auto-correlation score for the primary pitch candidate
    // |candidate_pitch_period| by also looking at its possible sub-harmonic
    // |candidate_pitch_secondary_period|.
    float xy_primary_period = ComputeAutoCorrelationCoeff(
        pitch_buf, GetInvertedLag(candidate_pitch_period), kMaxPitch24kHz);
    float xy_secondary_period = ComputeAutoCorrelationCoeff(
        pitch_buf, GetInvertedLag(candidate_pitch_secondary_period),
        kMaxPitch24kHz);
    float xy = 0.5f * (xy_primary_period + xy_secondary_period);
    float yy = 0.5f * (yy_values[candidate_pitch_period] +
                       yy_values[candidate_pitch_secondary_period]);
    float candidate_pitch_gain = pitch_gain(xy, yy, xx);

    // Maybe update best period.
    float threshold = ComputePitchGainThreshold(
        candidate_pitch_period, k, initial_pitch_period, initial_pitch_gain,
        prev_pitch_48kHz.period / 2, prev_pitch_48kHz.gain);
    if (candidate_pitch_gain > threshold) {
      best_pitch = {candidate_pitch_period, candidate_pitch_gain, xy, yy};
    }
  }

  // Final pitch gain and period.
  best_pitch.xy = std::max(0.f, best_pitch.xy);
  RTC_DCHECK_LE(0.f, best_pitch.yy);
  float final_pitch_gain = (best_pitch.yy <= best_pitch.xy)
                               ? 1.f
                               : best_pitch.xy / (best_pitch.yy + 1.f);
  final_pitch_gain = std::min(best_pitch.gain, final_pitch_gain);
  int final_pitch_period_48kHz = std::max(
      kMinPitch48kHz,
      PitchPseudoInterpolationLagPitchBuf(best_pitch.period_24kHz, pitch_buf));

  return {final_pitch_period_48kHz, final_pitch_gain};
}

}  // namespace rnn_vad
}  // namespace webrtc
