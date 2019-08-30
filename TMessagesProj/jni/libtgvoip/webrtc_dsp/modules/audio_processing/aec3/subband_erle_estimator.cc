/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/subband_erle_estimator.h"

#include <algorithm>
#include <memory>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {
constexpr int kPointsToAccumulate = 6;
constexpr float kX2BandEnergyThreshold = 44015068.0f;
constexpr int kErleHold = 100;
constexpr int kBlocksForOnsetDetection = kErleHold + 150;

bool EnableAdaptErleOnLowRender() {
  return !field_trial::IsEnabled("WebRTC-Aec3AdaptErleOnLowRenderKillSwitch");
}

}  // namespace

SubbandErleEstimator::SubbandErleEstimator(float min_erle,
                                           float max_erle_lf,
                                           float max_erle_hf)
    : min_erle_(min_erle),
      max_erle_lf_(max_erle_lf),
      max_erle_hf_(max_erle_hf),
      adapt_on_low_render_(EnableAdaptErleOnLowRender()) {
  Reset();
}

SubbandErleEstimator::~SubbandErleEstimator() = default;

void SubbandErleEstimator::Reset() {
  erle_.fill(min_erle_);
  erle_onsets_.fill(min_erle_);
  hold_counters_.fill(0);
  coming_onset_.fill(true);
}

void SubbandErleEstimator::Update(rtc::ArrayView<const float> X2,
                                  rtc::ArrayView<const float> Y2,
                                  rtc::ArrayView<const float> E2,
                                  bool converged_filter,
                                  bool onset_detection) {
  if (converged_filter) {
    // Note that the use of the converged_filter flag already imposed
    // a minimum of the erle that can be estimated as that flag would
    // be false if the filter is performing poorly.
    constexpr size_t kFftLengthBy4 = kFftLengthBy2 / 2;
    UpdateBands(X2, Y2, E2, 1, kFftLengthBy4, max_erle_lf_, onset_detection);
    UpdateBands(X2, Y2, E2, kFftLengthBy4, kFftLengthBy2, max_erle_hf_,
                onset_detection);
  }

  if (onset_detection) {
    DecreaseErlePerBandForLowRenderSignals();
  }

  erle_[0] = erle_[1];
  erle_[kFftLengthBy2] = erle_[kFftLengthBy2 - 1];
}

void SubbandErleEstimator::Dump(
    const std::unique_ptr<ApmDataDumper>& data_dumper) const {
  data_dumper->DumpRaw("aec3_erle", Erle());
  data_dumper->DumpRaw("aec3_erle_onset", ErleOnsets());
}

void SubbandErleEstimator::UpdateBands(rtc::ArrayView<const float> X2,
                                       rtc::ArrayView<const float> Y2,
                                       rtc::ArrayView<const float> E2,
                                       size_t start,
                                       size_t stop,
                                       float max_erle,
                                       bool onset_detection) {
  auto erle_band_update = [](float erle_band, float new_erle,
                             bool low_render_energy, float alpha_inc,
                             float alpha_dec, float min_erle, float max_erle) {
    if (new_erle < erle_band && low_render_energy) {
      // Decreases are not allowed if low render energy signals were used for
      // the erle computation.
      return erle_band;
    }
    float alpha = new_erle > erle_band ? alpha_inc : alpha_dec;
    float erle_band_out = erle_band;
    erle_band_out = erle_band + alpha * (new_erle - erle_band);
    erle_band_out = rtc::SafeClamp(erle_band_out, min_erle, max_erle);
    return erle_band_out;
  };

  for (size_t k = start; k < stop; ++k) {
    if (adapt_on_low_render_ || X2[k] > kX2BandEnergyThreshold) {
      bool low_render_energy = false;
      absl::optional<float> new_erle = instantaneous_erle_.Update(
          X2[k], Y2[k], E2[k], k, &low_render_energy);
      if (new_erle) {
        RTC_DCHECK(adapt_on_low_render_ || !low_render_energy);
        if (onset_detection && !low_render_energy) {
          if (coming_onset_[k]) {
            coming_onset_[k] = false;
            erle_onsets_[k] = erle_band_update(
                erle_onsets_[k], new_erle.value(), low_render_energy, 0.15f,
                0.3f, min_erle_, max_erle);
          }
          hold_counters_[k] = kBlocksForOnsetDetection;
        }

        erle_[k] =
            erle_band_update(erle_[k], new_erle.value(), low_render_energy,
                             0.05f, 0.1f, min_erle_, max_erle);
      }
    }
  }
}

void SubbandErleEstimator::DecreaseErlePerBandForLowRenderSignals() {
  for (size_t k = 1; k < kFftLengthBy2; ++k) {
    hold_counters_[k]--;
    if (hold_counters_[k] <= (kBlocksForOnsetDetection - kErleHold)) {
      if (erle_[k] > erle_onsets_[k]) {
        erle_[k] = std::max(erle_onsets_[k], 0.97f * erle_[k]);
        RTC_DCHECK_LE(min_erle_, erle_[k]);
      }
      if (hold_counters_[k] <= 0) {
        coming_onset_[k] = true;
        hold_counters_[k] = 0;
      }
    }
  }
}

SubbandErleEstimator::ErleInstantaneous::ErleInstantaneous() {
  Reset();
}

SubbandErleEstimator::ErleInstantaneous::~ErleInstantaneous() = default;

absl::optional<float> SubbandErleEstimator::ErleInstantaneous::Update(
    float X2,
    float Y2,
    float E2,
    size_t band,
    bool* low_render_energy) {
  absl::optional<float> erle_instantaneous = absl::nullopt;
  RTC_DCHECK_LT(band, kFftLengthBy2Plus1);
  Y2_acum_[band] += Y2;
  E2_acum_[band] += E2;
  low_render_energy_[band] =
      low_render_energy_[band] || X2 < kX2BandEnergyThreshold;
  if (++num_points_[band] == kPointsToAccumulate) {
    if (E2_acum_[band]) {
      erle_instantaneous = Y2_acum_[band] / E2_acum_[band];
    }
    *low_render_energy = low_render_energy_[band];
    num_points_[band] = 0;
    Y2_acum_[band] = 0.f;
    E2_acum_[band] = 0.f;
    low_render_energy_[band] = false;
  }

  return erle_instantaneous;
}

void SubbandErleEstimator::ErleInstantaneous::Reset() {
  Y2_acum_.fill(0.f);
  E2_acum_.fill(0.f);
  low_render_energy_.fill(false);
  num_points_.fill(0);
}

}  // namespace webrtc
