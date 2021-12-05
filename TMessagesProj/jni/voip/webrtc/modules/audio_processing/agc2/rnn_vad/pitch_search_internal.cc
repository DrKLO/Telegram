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
#include "modules/audio_processing/agc2/rnn_vad/vector_math.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_compare.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/system/arch.h"

namespace webrtc {
namespace rnn_vad {
namespace {

float ComputeAutoCorrelation(
    int inverted_lag,
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    const VectorMath& vector_math) {
  RTC_DCHECK_LT(inverted_lag, kBufSize24kHz);
  RTC_DCHECK_LT(inverted_lag, kRefineNumLags24kHz);
  static_assert(kMaxPitch24kHz < kBufSize24kHz, "");
  return vector_math.DotProduct(
      pitch_buffer.subview(/*offset=*/kMaxPitch24kHz),
      pitch_buffer.subview(inverted_lag, kFrameSize20ms24kHz));
}

// Given an auto-correlation coefficient `curr_auto_correlation` and its
// neighboring values `prev_auto_correlation` and `next_auto_correlation`
// computes a pseudo-interpolation offset to be applied to the pitch period
// associated to `curr`. The output is a lag in {-1, 0, +1}.
// TODO(bugs.webrtc.org/9076): Consider removing this method.
// `GetPitchPseudoInterpolationOffset()` it is relevant only if the spectral
// analysis works at a sample rate that is twice as that of the pitch buffer;
// In particular, it is not relevant for the estimated pitch period feature fed
// into the RNN.
int GetPitchPseudoInterpolationOffset(float prev_auto_correlation,
                                      float curr_auto_correlation,
                                      float next_auto_correlation) {
  if ((next_auto_correlation - prev_auto_correlation) >
      0.7f * (curr_auto_correlation - prev_auto_correlation)) {
    return 1;  // |next_auto_correlation| is the largest auto-correlation
               // coefficient.
  } else if ((prev_auto_correlation - next_auto_correlation) >
             0.7f * (curr_auto_correlation - next_auto_correlation)) {
    return -1;  // |prev_auto_correlation| is the largest auto-correlation
                // coefficient.
  }
  return 0;
}

// Refines a pitch period |lag| encoded as lag with pseudo-interpolation. The
// output sample rate is twice as that of |lag|.
int PitchPseudoInterpolationLagPitchBuf(
    int lag,
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    const VectorMath& vector_math) {
  int offset = 0;
  // Cannot apply pseudo-interpolation at the boundaries.
  if (lag > 0 && lag < kMaxPitch24kHz) {
    const int inverted_lag = kMaxPitch24kHz - lag;
    offset = GetPitchPseudoInterpolationOffset(
        ComputeAutoCorrelation(inverted_lag + 1, pitch_buffer, vector_math),
        ComputeAutoCorrelation(inverted_lag, pitch_buffer, vector_math),
        ComputeAutoCorrelation(inverted_lag - 1, pitch_buffer, vector_math));
  }
  return 2 * lag + offset;
}

// Integer multipliers used in ComputeExtendedPitchPeriod48kHz() when
// looking for sub-harmonics.
// The values have been chosen to serve the following algorithm. Given the
// initial pitch period T, we examine whether one of its harmonics is the true
// fundamental frequency. We consider T/k with k in {2, ..., 15}. For each of
// these harmonics, in addition to the pitch strength of itself, we choose one
// multiple of its pitch period, n*T/k, to validate it (by averaging their pitch
// strengths). The multiplier n is chosen so that n*T/k is used only one time
// over all k. When for example k = 4, we should also expect a peak at 3*T/4.
// When k = 8 instead we don't want to look at 2*T/8, since we have already
// checked T/4 before. Instead, we look at T*3/8.
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

struct Range {
  int min;
  int max;
};

// Number of analyzed pitches to the left(right) of a pitch candidate.
constexpr int kPitchNeighborhoodRadius = 2;

// Creates a pitch period interval centered in `inverted_lag` with hard-coded
// radius. Clipping is applied so that the interval is always valid for a 24 kHz
// pitch buffer.
Range CreateInvertedLagRange(int inverted_lag) {
  return {std::max(inverted_lag - kPitchNeighborhoodRadius, 0),
          std::min(inverted_lag + kPitchNeighborhoodRadius,
                   kInitialNumLags24kHz - 1)};
}

constexpr int kNumPitchCandidates = 2;  // Best and second best.
// Maximum number of analyzed pitch periods.
constexpr int kMaxPitchPeriods24kHz =
    kNumPitchCandidates * (2 * kPitchNeighborhoodRadius + 1);

// Collection of inverted lags.
class InvertedLagsIndex {
 public:
  InvertedLagsIndex() : num_entries_(0) {}
  // Adds an inverted lag to the index. Cannot add more than
  // `kMaxPitchPeriods24kHz` values.
  void Append(int inverted_lag) {
    RTC_DCHECK_LT(num_entries_, kMaxPitchPeriods24kHz);
    inverted_lags_[num_entries_++] = inverted_lag;
  }
  const int* data() const { return inverted_lags_.data(); }
  int size() const { return num_entries_; }

 private:
  std::array<int, kMaxPitchPeriods24kHz> inverted_lags_;
  int num_entries_;
};

// Computes the auto correlation coefficients for the inverted lags in the
// closed interval `inverted_lags`. Updates `inverted_lags_index` by appending
// the inverted lags for the computed auto correlation values.
void ComputeAutoCorrelation(
    Range inverted_lags,
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    rtc::ArrayView<float, kInitialNumLags24kHz> auto_correlation,
    InvertedLagsIndex& inverted_lags_index,
    const VectorMath& vector_math) {
  // Check valid range.
  RTC_DCHECK_LE(inverted_lags.min, inverted_lags.max);
  // Trick to avoid zero initialization of `auto_correlation`.
  // Needed by the pseudo-interpolation.
  if (inverted_lags.min > 0) {
    auto_correlation[inverted_lags.min - 1] = 0.f;
  }
  if (inverted_lags.max < kInitialNumLags24kHz - 1) {
    auto_correlation[inverted_lags.max + 1] = 0.f;
  }
  // Check valid `inverted_lag` indexes.
  RTC_DCHECK_GE(inverted_lags.min, 0);
  RTC_DCHECK_LT(inverted_lags.max, kInitialNumLags24kHz);
  for (int inverted_lag = inverted_lags.min; inverted_lag <= inverted_lags.max;
       ++inverted_lag) {
    auto_correlation[inverted_lag] =
        ComputeAutoCorrelation(inverted_lag, pitch_buffer, vector_math);
    inverted_lags_index.Append(inverted_lag);
  }
}

// Searches the strongest pitch period at 24 kHz and returns its inverted lag at
// 48 kHz.
int ComputePitchPeriod48kHz(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    rtc::ArrayView<const int> inverted_lags,
    rtc::ArrayView<const float, kInitialNumLags24kHz> auto_correlation,
    rtc::ArrayView<const float, kRefineNumLags24kHz> y_energy,
    const VectorMath& vector_math) {
  static_assert(kMaxPitch24kHz > kInitialNumLags24kHz, "");
  static_assert(kMaxPitch24kHz < kBufSize24kHz, "");
  int best_inverted_lag = 0;     // Pitch period.
  float best_numerator = -1.f;   // Pitch strength numerator.
  float best_denominator = 0.f;  // Pitch strength denominator.
  for (int inverted_lag : inverted_lags) {
    // A pitch candidate must have positive correlation.
    if (auto_correlation[inverted_lag] > 0.f) {
      // Auto-correlation energy normalized by frame energy.
      const float numerator =
          auto_correlation[inverted_lag] * auto_correlation[inverted_lag];
      const float denominator = y_energy[inverted_lag];
      // Compare numerator/denominator ratios without using divisions.
      if (numerator * best_denominator > best_numerator * denominator) {
        best_inverted_lag = inverted_lag;
        best_numerator = numerator;
        best_denominator = denominator;
      }
    }
  }
  // Pseudo-interpolation to transform `best_inverted_lag` (24 kHz pitch) to a
  // 48 kHz pitch period.
  if (best_inverted_lag == 0 || best_inverted_lag >= kInitialNumLags24kHz - 1) {
    // Cannot apply pseudo-interpolation at the boundaries.
    return best_inverted_lag * 2;
  }
  int offset = GetPitchPseudoInterpolationOffset(
      auto_correlation[best_inverted_lag + 1],
      auto_correlation[best_inverted_lag],
      auto_correlation[best_inverted_lag - 1]);
  // TODO(bugs.webrtc.org/9076): When retraining, check if |offset| below should
  // be subtracted since |inverted_lag| is an inverted lag but offset is a lag.
  return 2 * best_inverted_lag + offset;
}

// Returns an alternative pitch period for `pitch_period` given a `multiplier`
// and a `divisor` of the period.
constexpr int GetAlternativePitchPeriod(int pitch_period,
                                        int multiplier,
                                        int divisor) {
  RTC_DCHECK_GT(divisor, 0);
  // Same as `round(multiplier * pitch_period / divisor)`.
  return (2 * multiplier * pitch_period + divisor) / (2 * divisor);
}

// Returns true if the alternative pitch period is stronger than the initial one
// given the last estimated pitch and the value of `period_divisor` used to
// compute the alternative pitch period via `GetAlternativePitchPeriod()`.
bool IsAlternativePitchStrongerThanInitial(PitchInfo last,
                                           PitchInfo initial,
                                           PitchInfo alternative,
                                           int period_divisor) {
  // Initial pitch period candidate thresholds for a sample rate of 24 kHz.
  // Computed as [5*k*k for k in range(16)].
  constexpr std::array<int, 14> kInitialPitchPeriodThresholds = {
      {20, 45, 80, 125, 180, 245, 320, 405, 500, 605, 720, 845, 980, 1125}};
  static_assert(
      kInitialPitchPeriodThresholds.size() == kSubHarmonicMultipliers.size(),
      "");
  RTC_DCHECK_GE(last.period, 0);
  RTC_DCHECK_GE(initial.period, 0);
  RTC_DCHECK_GE(alternative.period, 0);
  RTC_DCHECK_GE(period_divisor, 2);
  // Compute a term that lowers the threshold when |alternative.period| is close
  // to the last estimated period |last.period| - i.e., pitch tracking.
  float lower_threshold_term = 0.f;
  if (std::abs(alternative.period - last.period) <= 1) {
    // The candidate pitch period is within 1 sample from the last one.
    // Make the candidate at |alternative.period| very easy to be accepted.
    lower_threshold_term = last.strength;
  } else if (std::abs(alternative.period - last.period) == 2 &&
             initial.period >
                 kInitialPitchPeriodThresholds[period_divisor - 2]) {
    // The candidate pitch period is 2 samples far from the last one and the
    // period |initial.period| (from which |alternative.period| has been
    // derived) is greater than a threshold. Make |alternative.period| easy to
    // be accepted.
    lower_threshold_term = 0.5f * last.strength;
  }
  // Set the threshold based on the strength of the initial estimate
  // |initial.period|. Also reduce the chance of false positives caused by a
  // bias towards high frequencies (originating from short-term correlations).
  float threshold =
      std::max(0.3f, 0.7f * initial.strength - lower_threshold_term);
  if (alternative.period < 3 * kMinPitch24kHz) {
    // High frequency.
    threshold = std::max(0.4f, 0.85f * initial.strength - lower_threshold_term);
  } else if (alternative.period < 2 * kMinPitch24kHz) {
    // Even higher frequency.
    threshold = std::max(0.5f, 0.9f * initial.strength - lower_threshold_term);
  }
  return alternative.strength > threshold;
}

}  // namespace

void Decimate2x(rtc::ArrayView<const float, kBufSize24kHz> src,
                rtc::ArrayView<float, kBufSize12kHz> dst) {
  // TODO(bugs.webrtc.org/9076): Consider adding anti-aliasing filter.
  static_assert(2 * kBufSize12kHz == kBufSize24kHz, "");
  for (int i = 0; i < kBufSize12kHz; ++i) {
    dst[i] = src[2 * i];
  }
}

void ComputeSlidingFrameSquareEnergies24kHz(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    rtc::ArrayView<float, kRefineNumLags24kHz> y_energy,
    AvailableCpuFeatures cpu_features) {
  VectorMath vector_math(cpu_features);
  static_assert(kFrameSize20ms24kHz < kBufSize24kHz, "");
  const auto frame_20ms_view = pitch_buffer.subview(0, kFrameSize20ms24kHz);
  float yy = vector_math.DotProduct(frame_20ms_view, frame_20ms_view);
  y_energy[0] = yy;
  static_assert(kMaxPitch24kHz - 1 + kFrameSize20ms24kHz < kBufSize24kHz, "");
  static_assert(kMaxPitch24kHz < kRefineNumLags24kHz, "");
  for (int inverted_lag = 0; inverted_lag < kMaxPitch24kHz; ++inverted_lag) {
    yy -= pitch_buffer[inverted_lag] * pitch_buffer[inverted_lag];
    yy += pitch_buffer[inverted_lag + kFrameSize20ms24kHz] *
          pitch_buffer[inverted_lag + kFrameSize20ms24kHz];
    yy = std::max(1.f, yy);
    y_energy[inverted_lag + 1] = yy;
  }
}

CandidatePitchPeriods ComputePitchPeriod12kHz(
    rtc::ArrayView<const float, kBufSize12kHz> pitch_buffer,
    rtc::ArrayView<const float, kNumLags12kHz> auto_correlation,
    AvailableCpuFeatures cpu_features) {
  static_assert(kMaxPitch12kHz > kNumLags12kHz, "");
  static_assert(kMaxPitch12kHz < kBufSize12kHz, "");

  // Stores a pitch candidate period and strength information.
  struct PitchCandidate {
    // Pitch period encoded as inverted lag.
    int period_inverted_lag = 0;
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

  VectorMath vector_math(cpu_features);
  static_assert(kFrameSize20ms12kHz + 1 < kBufSize12kHz, "");
  const auto frame_view = pitch_buffer.subview(0, kFrameSize20ms12kHz + 1);
  float denominator = 1.f + vector_math.DotProduct(frame_view, frame_view);
  // Search best and second best pitches by looking at the scaled
  // auto-correlation.
  PitchCandidate best;
  PitchCandidate second_best;
  second_best.period_inverted_lag = 1;
  for (int inverted_lag = 0; inverted_lag < kNumLags12kHz; ++inverted_lag) {
    // A pitch candidate must have positive correlation.
    if (auto_correlation[inverted_lag] > 0.f) {
      PitchCandidate candidate{
          inverted_lag,
          auto_correlation[inverted_lag] * auto_correlation[inverted_lag],
          denominator};
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
    const float y_old = pitch_buffer[inverted_lag];
    const float y_new = pitch_buffer[inverted_lag + kFrameSize20ms12kHz];
    denominator -= y_old * y_old;
    denominator += y_new * y_new;
    denominator = std::max(0.f, denominator);
  }
  return {best.period_inverted_lag, second_best.period_inverted_lag};
}

int ComputePitchPeriod48kHz(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    rtc::ArrayView<const float, kRefineNumLags24kHz> y_energy,
    CandidatePitchPeriods pitch_candidates,
    AvailableCpuFeatures cpu_features) {
  // Compute the auto-correlation terms only for neighbors of the two pitch
  // candidates (best and second best).
  std::array<float, kInitialNumLags24kHz> auto_correlation;
  InvertedLagsIndex inverted_lags_index;
  // Create two inverted lag ranges so that `r1` precedes `r2`.
  const bool swap_candidates =
      pitch_candidates.best > pitch_candidates.second_best;
  const Range r1 = CreateInvertedLagRange(
      swap_candidates ? pitch_candidates.second_best : pitch_candidates.best);
  const Range r2 = CreateInvertedLagRange(
      swap_candidates ? pitch_candidates.best : pitch_candidates.second_best);
  // Check valid ranges.
  RTC_DCHECK_LE(r1.min, r1.max);
  RTC_DCHECK_LE(r2.min, r2.max);
  // Check `r1` precedes `r2`.
  RTC_DCHECK_LE(r1.min, r2.min);
  RTC_DCHECK_LE(r1.max, r2.max);
  VectorMath vector_math(cpu_features);
  if (r1.max + 1 >= r2.min) {
    // Overlapping or adjacent ranges.
    ComputeAutoCorrelation({r1.min, r2.max}, pitch_buffer, auto_correlation,
                           inverted_lags_index, vector_math);
  } else {
    // Disjoint ranges.
    ComputeAutoCorrelation(r1, pitch_buffer, auto_correlation,
                           inverted_lags_index, vector_math);
    ComputeAutoCorrelation(r2, pitch_buffer, auto_correlation,
                           inverted_lags_index, vector_math);
  }
  return ComputePitchPeriod48kHz(pitch_buffer, inverted_lags_index,
                                 auto_correlation, y_energy, vector_math);
}

PitchInfo ComputeExtendedPitchPeriod48kHz(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer,
    rtc::ArrayView<const float, kRefineNumLags24kHz> y_energy,
    int initial_pitch_period_48kHz,
    PitchInfo last_pitch_48kHz,
    AvailableCpuFeatures cpu_features) {
  RTC_DCHECK_LE(kMinPitch48kHz, initial_pitch_period_48kHz);
  RTC_DCHECK_LE(initial_pitch_period_48kHz, kMaxPitch48kHz);

  // Stores information for a refined pitch candidate.
  struct RefinedPitchCandidate {
    int period;
    float strength;
    // Additional strength data used for the final pitch estimation.
    float xy;        // Auto-correlation.
    float y_energy;  // Energy of the sliding frame `y`.
  };

  const float x_energy = y_energy[kMaxPitch24kHz];
  const auto pitch_strength = [x_energy](float xy, float y_energy) {
    RTC_DCHECK_GE(x_energy * y_energy, 0.f);
    return xy / std::sqrt(1.f + x_energy * y_energy);
  };
  VectorMath vector_math(cpu_features);

  // Initialize the best pitch candidate with `initial_pitch_period_48kHz`.
  RefinedPitchCandidate best_pitch;
  best_pitch.period =
      std::min(initial_pitch_period_48kHz / 2, kMaxPitch24kHz - 1);
  best_pitch.xy = ComputeAutoCorrelation(kMaxPitch24kHz - best_pitch.period,
                                         pitch_buffer, vector_math);
  best_pitch.y_energy = y_energy[kMaxPitch24kHz - best_pitch.period];
  best_pitch.strength = pitch_strength(best_pitch.xy, best_pitch.y_energy);
  // Keep a copy of the initial pitch candidate.
  const PitchInfo initial_pitch{best_pitch.period, best_pitch.strength};
  // 24 kHz version of the last estimated pitch.
  const PitchInfo last_pitch{last_pitch_48kHz.period / 2,
                             last_pitch_48kHz.strength};

  // Find `max_period_divisor` such that the result of
  // `GetAlternativePitchPeriod(initial_pitch_period, 1, max_period_divisor)`
  // equals `kMinPitch24kHz`.
  const int max_period_divisor =
      (2 * initial_pitch.period) / (2 * kMinPitch24kHz - 1);
  for (int period_divisor = 2; period_divisor <= max_period_divisor;
       ++period_divisor) {
    PitchInfo alternative_pitch;
    alternative_pitch.period = GetAlternativePitchPeriod(
        initial_pitch.period, /*multiplier=*/1, period_divisor);
    RTC_DCHECK_GE(alternative_pitch.period, kMinPitch24kHz);
    // When looking at |alternative_pitch.period|, we also look at one of its
    // sub-harmonics. |kSubHarmonicMultipliers| is used to know where to look.
    // |period_divisor| == 2 is a special case since |dual_alternative_period|
    // might be greater than the maximum pitch period.
    int dual_alternative_period = GetAlternativePitchPeriod(
        initial_pitch.period, kSubHarmonicMultipliers[period_divisor - 2],
        period_divisor);
    RTC_DCHECK_GT(dual_alternative_period, 0);
    if (period_divisor == 2 && dual_alternative_period > kMaxPitch24kHz) {
      dual_alternative_period = initial_pitch.period;
    }
    RTC_DCHECK_NE(alternative_pitch.period, dual_alternative_period)
        << "The lower pitch period and the additional sub-harmonic must not "
           "coincide.";
    // Compute an auto-correlation score for the primary pitch candidate
    // |alternative_pitch.period| by also looking at its possible sub-harmonic
    // |dual_alternative_period|.
    const float xy_primary_period = ComputeAutoCorrelation(
        kMaxPitch24kHz - alternative_pitch.period, pitch_buffer, vector_math);
    // TODO(webrtc:10480): Copy `xy_primary_period` if the secondary period is
    // equal to the primary one.
    const float xy_secondary_period = ComputeAutoCorrelation(
        kMaxPitch24kHz - dual_alternative_period, pitch_buffer, vector_math);
    const float xy = 0.5f * (xy_primary_period + xy_secondary_period);
    const float yy =
        0.5f * (y_energy[kMaxPitch24kHz - alternative_pitch.period] +
                y_energy[kMaxPitch24kHz - dual_alternative_period]);
    alternative_pitch.strength = pitch_strength(xy, yy);

    // Maybe update best period.
    if (IsAlternativePitchStrongerThanInitial(
            last_pitch, initial_pitch, alternative_pitch, period_divisor)) {
      best_pitch = {alternative_pitch.period, alternative_pitch.strength, xy,
                    yy};
    }
  }

  // Final pitch strength and period.
  best_pitch.xy = std::max(0.f, best_pitch.xy);
  RTC_DCHECK_LE(0.f, best_pitch.y_energy);
  float final_pitch_strength =
      (best_pitch.y_energy <= best_pitch.xy)
          ? 1.f
          : best_pitch.xy / (best_pitch.y_energy + 1.f);
  final_pitch_strength = std::min(best_pitch.strength, final_pitch_strength);
  int final_pitch_period_48kHz = std::max(
      kMinPitch48kHz, PitchPseudoInterpolationLagPitchBuf(
                          best_pitch.period, pitch_buffer, vector_math));

  return {final_pitch_period_48kHz, final_pitch_strength};
}

}  // namespace rnn_vad
}  // namespace webrtc
