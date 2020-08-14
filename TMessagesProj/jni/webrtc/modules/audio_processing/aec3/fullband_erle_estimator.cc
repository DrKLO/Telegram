/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/fullband_erle_estimator.h"

#include <algorithm>
#include <memory>
#include <numeric>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

namespace {
constexpr float kEpsilon = 1e-3f;
constexpr float kX2BandEnergyThreshold = 44015068.0f;
constexpr int kBlocksToHoldErle = 100;
constexpr int kPointsToAccumulate = 6;
}  // namespace

FullBandErleEstimator::FullBandErleEstimator(
    const EchoCanceller3Config::Erle& config,
    size_t num_capture_channels)
    : min_erle_log2_(FastApproxLog2f(config.min + kEpsilon)),
      max_erle_lf_log2(FastApproxLog2f(config.max_l + kEpsilon)),
      hold_counters_time_domain_(num_capture_channels, 0),
      erle_time_domain_log2_(num_capture_channels, min_erle_log2_),
      instantaneous_erle_(num_capture_channels, ErleInstantaneous(config)),
      linear_filters_qualities_(num_capture_channels) {
  Reset();
}

FullBandErleEstimator::~FullBandErleEstimator() = default;

void FullBandErleEstimator::Reset() {
  for (auto& instantaneous_erle_ch : instantaneous_erle_) {
    instantaneous_erle_ch.Reset();
  }

  UpdateQualityEstimates();
  std::fill(erle_time_domain_log2_.begin(), erle_time_domain_log2_.end(),
            min_erle_log2_);
  std::fill(hold_counters_time_domain_.begin(),
            hold_counters_time_domain_.end(), 0);
}

void FullBandErleEstimator::Update(
    rtc::ArrayView<const float> X2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> Y2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> E2,
    const std::vector<bool>& converged_filters) {
  for (size_t ch = 0; ch < Y2.size(); ++ch) {
    if (converged_filters[ch]) {
      // Computes the fullband ERLE.
      const float X2_sum = std::accumulate(X2.begin(), X2.end(), 0.0f);
      if (X2_sum > kX2BandEnergyThreshold * X2.size()) {
        const float Y2_sum =
            std::accumulate(Y2[ch].begin(), Y2[ch].end(), 0.0f);
        const float E2_sum =
            std::accumulate(E2[ch].begin(), E2[ch].end(), 0.0f);
        if (instantaneous_erle_[ch].Update(Y2_sum, E2_sum)) {
          hold_counters_time_domain_[ch] = kBlocksToHoldErle;
          erle_time_domain_log2_[ch] +=
              0.1f * ((instantaneous_erle_[ch].GetInstErleLog2().value()) -
                      erle_time_domain_log2_[ch]);
          erle_time_domain_log2_[ch] = rtc::SafeClamp(
              erle_time_domain_log2_[ch], min_erle_log2_, max_erle_lf_log2);
        }
      }
    }
    --hold_counters_time_domain_[ch];
    if (hold_counters_time_domain_[ch] <= 0) {
      erle_time_domain_log2_[ch] =
          std::max(min_erle_log2_, erle_time_domain_log2_[ch] - 0.044f);
    }
    if (hold_counters_time_domain_[ch] == 0) {
      instantaneous_erle_[ch].ResetAccumulators();
    }
  }

  UpdateQualityEstimates();
}

void FullBandErleEstimator::Dump(
    const std::unique_ptr<ApmDataDumper>& data_dumper) const {
  data_dumper->DumpRaw("aec3_fullband_erle_log2", FullbandErleLog2());
  instantaneous_erle_[0].Dump(data_dumper);
}

void FullBandErleEstimator::UpdateQualityEstimates() {
  for (size_t ch = 0; ch < instantaneous_erle_.size(); ++ch) {
    linear_filters_qualities_[ch] =
        instantaneous_erle_[ch].GetQualityEstimate();
  }
}

FullBandErleEstimator::ErleInstantaneous::ErleInstantaneous(
    const EchoCanceller3Config::Erle& config)
    : clamp_inst_quality_to_zero_(config.clamp_quality_estimate_to_zero),
      clamp_inst_quality_to_one_(config.clamp_quality_estimate_to_one) {
  Reset();
}

FullBandErleEstimator::ErleInstantaneous::~ErleInstantaneous() = default;

bool FullBandErleEstimator::ErleInstantaneous::Update(const float Y2_sum,
                                                      const float E2_sum) {
  bool update_estimates = false;
  E2_acum_ += E2_sum;
  Y2_acum_ += Y2_sum;
  num_points_++;
  if (num_points_ == kPointsToAccumulate) {
    if (E2_acum_ > 0.f) {
      update_estimates = true;
      erle_log2_ = FastApproxLog2f(Y2_acum_ / E2_acum_ + kEpsilon);
    }
    num_points_ = 0;
    E2_acum_ = 0.f;
    Y2_acum_ = 0.f;
  }

  if (update_estimates) {
    UpdateMaxMin();
    UpdateQualityEstimate();
  }
  return update_estimates;
}

void FullBandErleEstimator::ErleInstantaneous::Reset() {
  ResetAccumulators();
  max_erle_log2_ = -10.f;  // -30 dB.
  min_erle_log2_ = 33.f;   // 100 dB.
  inst_quality_estimate_ = 0.f;
}

void FullBandErleEstimator::ErleInstantaneous::ResetAccumulators() {
  erle_log2_ = absl::nullopt;
  inst_quality_estimate_ = 0.f;
  num_points_ = 0;
  E2_acum_ = 0.f;
  Y2_acum_ = 0.f;
}

void FullBandErleEstimator::ErleInstantaneous::Dump(
    const std::unique_ptr<ApmDataDumper>& data_dumper) const {
  data_dumper->DumpRaw("aec3_fullband_erle_inst_log2",
                       erle_log2_ ? *erle_log2_ : -10.f);
  data_dumper->DumpRaw(
      "aec3_erle_instantaneous_quality",
      GetQualityEstimate() ? GetQualityEstimate().value() : 0.f);
  data_dumper->DumpRaw("aec3_fullband_erle_max_log2", max_erle_log2_);
  data_dumper->DumpRaw("aec3_fullband_erle_min_log2", min_erle_log2_);
}

void FullBandErleEstimator::ErleInstantaneous::UpdateMaxMin() {
  RTC_DCHECK(erle_log2_);
  if (erle_log2_.value() > max_erle_log2_) {
    max_erle_log2_ = erle_log2_.value();
  } else {
    max_erle_log2_ -= 0.0004;  // Forget factor, approx 1dB every 3 sec.
  }

  if (erle_log2_.value() < min_erle_log2_) {
    min_erle_log2_ = erle_log2_.value();
  } else {
    min_erle_log2_ += 0.0004;  // Forget factor, approx 1dB every 3 sec.
  }
}

void FullBandErleEstimator::ErleInstantaneous::UpdateQualityEstimate() {
  const float alpha = 0.07f;
  float quality_estimate = 0.f;
  RTC_DCHECK(erle_log2_);
  // TODO(peah): Currently, the estimate can become be less than 0; this should
  // be corrected.
  if (max_erle_log2_ > min_erle_log2_) {
    quality_estimate = (erle_log2_.value() - min_erle_log2_) /
                       (max_erle_log2_ - min_erle_log2_);
  }
  if (quality_estimate > inst_quality_estimate_) {
    inst_quality_estimate_ = quality_estimate;
  } else {
    inst_quality_estimate_ +=
        alpha * (quality_estimate - inst_quality_estimate_);
  }
}

}  // namespace webrtc
