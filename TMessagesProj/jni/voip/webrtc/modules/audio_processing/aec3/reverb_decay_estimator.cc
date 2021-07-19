/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/reverb_decay_estimator.h"

#include <stddef.h>

#include <algorithm>
#include <cmath>
#include <numeric>

#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

constexpr int kEarlyReverbMinSizeBlocks = 3;
constexpr int kBlocksPerSection = 6;
// Linear regression approach assumes symmetric index around 0.
constexpr float kEarlyReverbFirstPointAtLinearRegressors =
    -0.5f * kBlocksPerSection * kFftLengthBy2 + 0.5f;

// Averages the values in a block of size kFftLengthBy2;
float BlockAverage(rtc::ArrayView<const float> v, size_t block_index) {
  constexpr float kOneByFftLengthBy2 = 1.f / kFftLengthBy2;
  const int i = block_index * kFftLengthBy2;
  RTC_DCHECK_GE(v.size(), i + kFftLengthBy2);
  const float sum =
      std::accumulate(v.begin() + i, v.begin() + i + kFftLengthBy2, 0.f);
  return sum * kOneByFftLengthBy2;
}

// Analyzes the gain in a block.
void AnalyzeBlockGain(const std::array<float, kFftLengthBy2>& h2,
                      float floor_gain,
                      float* previous_gain,
                      bool* block_adapting,
                      bool* decaying_gain) {
  float gain = std::max(BlockAverage(h2, 0), 1e-32f);
  *block_adapting =
      *previous_gain > 1.1f * gain || *previous_gain < 0.9f * gain;
  *decaying_gain = gain > floor_gain;
  *previous_gain = gain;
}

// Arithmetic sum of $2 \sum_{i=0.5}^{(N-1)/2}i^2$ calculated directly.
constexpr float SymmetricArithmetricSum(int N) {
  return N * (N * N - 1.0f) * (1.f / 12.f);
}

// Returns the peak energy of an impulse response.
float BlockEnergyPeak(rtc::ArrayView<const float> h, int peak_block) {
  RTC_DCHECK_LE((peak_block + 1) * kFftLengthBy2, h.size());
  RTC_DCHECK_GE(peak_block, 0);
  float peak_value =
      *std::max_element(h.begin() + peak_block * kFftLengthBy2,
                        h.begin() + (peak_block + 1) * kFftLengthBy2,
                        [](float a, float b) { return a * a < b * b; });
  return peak_value * peak_value;
}

// Returns the average energy of an impulse response block.
float BlockEnergyAverage(rtc::ArrayView<const float> h, int block_index) {
  RTC_DCHECK_LE((block_index + 1) * kFftLengthBy2, h.size());
  RTC_DCHECK_GE(block_index, 0);
  constexpr float kOneByFftLengthBy2 = 1.f / kFftLengthBy2;
  const auto sum_of_squares = [](float a, float b) { return a + b * b; };
  return std::accumulate(h.begin() + block_index * kFftLengthBy2,
                         h.begin() + (block_index + 1) * kFftLengthBy2, 0.f,
                         sum_of_squares) *
         kOneByFftLengthBy2;
}

}  // namespace

ReverbDecayEstimator::ReverbDecayEstimator(const EchoCanceller3Config& config)
    : filter_length_blocks_(config.filter.refined.length_blocks),
      filter_length_coefficients_(GetTimeDomainLength(filter_length_blocks_)),
      use_adaptive_echo_decay_(config.ep_strength.default_len < 0.f),
      early_reverb_estimator_(config.filter.refined.length_blocks -
                              kEarlyReverbMinSizeBlocks),
      late_reverb_start_(kEarlyReverbMinSizeBlocks),
      late_reverb_end_(kEarlyReverbMinSizeBlocks),
      previous_gains_(config.filter.refined.length_blocks, 0.f),
      decay_(std::fabs(config.ep_strength.default_len)) {
  RTC_DCHECK_GT(config.filter.refined.length_blocks,
                static_cast<size_t>(kEarlyReverbMinSizeBlocks));
}

ReverbDecayEstimator::~ReverbDecayEstimator() = default;

void ReverbDecayEstimator::Update(rtc::ArrayView<const float> filter,
                                  const absl::optional<float>& filter_quality,
                                  int filter_delay_blocks,
                                  bool usable_linear_filter,
                                  bool stationary_signal) {
  const int filter_size = static_cast<int>(filter.size());

  if (stationary_signal) {
    return;
  }

  bool estimation_feasible =
      filter_delay_blocks <=
      filter_length_blocks_ - kEarlyReverbMinSizeBlocks - 1;
  estimation_feasible =
      estimation_feasible && filter_size == filter_length_coefficients_;
  estimation_feasible = estimation_feasible && filter_delay_blocks > 0;
  estimation_feasible = estimation_feasible && usable_linear_filter;

  if (!estimation_feasible) {
    ResetDecayEstimation();
    return;
  }

  if (!use_adaptive_echo_decay_) {
    return;
  }

  const float new_smoothing = filter_quality ? *filter_quality * 0.2f : 0.f;
  smoothing_constant_ = std::max(new_smoothing, smoothing_constant_);
  if (smoothing_constant_ == 0.f) {
    return;
  }

  if (block_to_analyze_ < filter_length_blocks_) {
    // Analyze the filter and accumulate data for reverb estimation.
    AnalyzeFilter(filter);
    ++block_to_analyze_;
  } else {
    // When the filter is fully analyzed, estimate the reverb decay and reset
    // the block_to_analyze_ counter.
    EstimateDecay(filter, filter_delay_blocks);
  }
}

void ReverbDecayEstimator::ResetDecayEstimation() {
  early_reverb_estimator_.Reset();
  late_reverb_decay_estimator_.Reset(0);
  block_to_analyze_ = 0;
  estimation_region_candidate_size_ = 0;
  estimation_region_identified_ = false;
  smoothing_constant_ = 0.f;
  late_reverb_start_ = 0;
  late_reverb_end_ = 0;
}

void ReverbDecayEstimator::EstimateDecay(rtc::ArrayView<const float> filter,
                                         int peak_block) {
  auto& h = filter;
  RTC_DCHECK_EQ(0, h.size() % kFftLengthBy2);

  // Reset the block analysis counter.
  block_to_analyze_ =
      std::min(peak_block + kEarlyReverbMinSizeBlocks, filter_length_blocks_);

  // To estimate the reverb decay, the energy of the first filter section must
  // be substantially larger than the last. Also, the first filter section
  // energy must not deviate too much from the max peak.
  const float first_reverb_gain = BlockEnergyAverage(h, block_to_analyze_);
  const size_t h_size_blocks = h.size() >> kFftLengthBy2Log2;
  tail_gain_ = BlockEnergyAverage(h, h_size_blocks - 1);
  float peak_energy = BlockEnergyPeak(h, peak_block);
  const bool sufficient_reverb_decay = first_reverb_gain > 4.f * tail_gain_;
  const bool valid_filter =
      first_reverb_gain > 2.f * tail_gain_ && peak_energy < 100.f;

  // Estimate the size of the regions with early and late reflections.
  const int size_early_reverb = early_reverb_estimator_.Estimate();
  const int size_late_reverb =
      std::max(estimation_region_candidate_size_ - size_early_reverb, 0);

  // Only update the reverb decay estimate if the size of the identified late
  // reverb is sufficiently large.
  if (size_late_reverb >= 5) {
    if (valid_filter && late_reverb_decay_estimator_.EstimateAvailable()) {
      float decay = std::pow(
          2.0f, late_reverb_decay_estimator_.Estimate() * kFftLengthBy2);
      constexpr float kMaxDecay = 0.95f;  // ~1 sec min RT60.
      constexpr float kMinDecay = 0.02f;  // ~15 ms max RT60.
      decay = std::max(.97f * decay_, decay);
      decay = std::min(decay, kMaxDecay);
      decay = std::max(decay, kMinDecay);
      decay_ += smoothing_constant_ * (decay - decay_);
    }

    // Update length of decay. Must have enough data (number of sections) in
    // order to estimate decay rate.
    late_reverb_decay_estimator_.Reset(size_late_reverb * kFftLengthBy2);
    late_reverb_start_ =
        peak_block + kEarlyReverbMinSizeBlocks + size_early_reverb;
    late_reverb_end_ =
        block_to_analyze_ + estimation_region_candidate_size_ - 1;
  } else {
    late_reverb_decay_estimator_.Reset(0);
    late_reverb_start_ = 0;
    late_reverb_end_ = 0;
  }

  // Reset variables for the identification of the region for reverb decay
  // estimation.
  estimation_region_identified_ = !(valid_filter && sufficient_reverb_decay);
  estimation_region_candidate_size_ = 0;

  // Stop estimation of the decay until another good filter is received.
  smoothing_constant_ = 0.f;

  // Reset early reflections detector.
  early_reverb_estimator_.Reset();
}

void ReverbDecayEstimator::AnalyzeFilter(rtc::ArrayView<const float> filter) {
  auto h = rtc::ArrayView<const float>(
      filter.begin() + block_to_analyze_ * kFftLengthBy2, kFftLengthBy2);

  // Compute squared filter coeffiecients for the block to analyze_;
  std::array<float, kFftLengthBy2> h2;
  std::transform(h.begin(), h.end(), h2.begin(), [](float a) { return a * a; });

  // Map out the region for estimating the reverb decay.
  bool adapting;
  bool above_noise_floor;
  AnalyzeBlockGain(h2, tail_gain_, &previous_gains_[block_to_analyze_],
                   &adapting, &above_noise_floor);

  // Count consecutive number of "good" filter sections, where "good" means:
  // 1) energy is above noise floor.
  // 2) energy of current section has not changed too much from last check.
  estimation_region_identified_ =
      estimation_region_identified_ || adapting || !above_noise_floor;
  if (!estimation_region_identified_) {
    ++estimation_region_candidate_size_;
  }

  // Accumulate data for reverb decay estimation and for the estimation of early
  // reflections.
  if (block_to_analyze_ <= late_reverb_end_) {
    if (block_to_analyze_ >= late_reverb_start_) {
      for (float h2_k : h2) {
        float h2_log2 = FastApproxLog2f(h2_k + 1e-10);
        late_reverb_decay_estimator_.Accumulate(h2_log2);
        early_reverb_estimator_.Accumulate(h2_log2, smoothing_constant_);
      }
    } else {
      for (float h2_k : h2) {
        float h2_log2 = FastApproxLog2f(h2_k + 1e-10);
        early_reverb_estimator_.Accumulate(h2_log2, smoothing_constant_);
      }
    }
  }
}

void ReverbDecayEstimator::Dump(ApmDataDumper* data_dumper) const {
  data_dumper->DumpRaw("aec3_reverb_decay", decay_);
  data_dumper->DumpRaw("aec3_reverb_tail_energy", tail_gain_);
  data_dumper->DumpRaw("aec3_reverb_alpha", smoothing_constant_);
  data_dumper->DumpRaw("aec3_num_reverb_decay_blocks",
                       late_reverb_end_ - late_reverb_start_);
  data_dumper->DumpRaw("aec3_late_reverb_start", late_reverb_start_);
  data_dumper->DumpRaw("aec3_late_reverb_end", late_reverb_end_);
  early_reverb_estimator_.Dump(data_dumper);
}

void ReverbDecayEstimator::LateReverbLinearRegressor::Reset(
    int num_data_points) {
  RTC_DCHECK_LE(0, num_data_points);
  RTC_DCHECK_EQ(0, num_data_points % 2);
  const int N = num_data_points;
  nz_ = 0.f;
  // Arithmetic sum of $2 \sum_{i=0.5}^{(N-1)/2}i^2$ calculated directly.
  nn_ = SymmetricArithmetricSum(N);
  // The linear regression approach assumes symmetric index around 0.
  count_ = N > 0 ? -N * 0.5f + 0.5f : 0.f;
  N_ = N;
  n_ = 0;
}

void ReverbDecayEstimator::LateReverbLinearRegressor::Accumulate(float z) {
  nz_ += count_ * z;
  ++count_;
  ++n_;
}

float ReverbDecayEstimator::LateReverbLinearRegressor::Estimate() {
  RTC_DCHECK(EstimateAvailable());
  if (nn_ == 0.f) {
    RTC_NOTREACHED();
    return 0.f;
  }
  return nz_ / nn_;
}

ReverbDecayEstimator::EarlyReverbLengthEstimator::EarlyReverbLengthEstimator(
    int max_blocks)
    : numerators_smooth_(max_blocks - kBlocksPerSection, 0.f),
      numerators_(numerators_smooth_.size(), 0.f),
      coefficients_counter_(0) {
  RTC_DCHECK_LE(0, max_blocks);
}

ReverbDecayEstimator::EarlyReverbLengthEstimator::
    ~EarlyReverbLengthEstimator() = default;

void ReverbDecayEstimator::EarlyReverbLengthEstimator::Reset() {
  coefficients_counter_ = 0;
  std::fill(numerators_.begin(), numerators_.end(), 0.f);
  block_counter_ = 0;
}

void ReverbDecayEstimator::EarlyReverbLengthEstimator::Accumulate(
    float value,
    float smoothing) {
  // Each section is composed by kBlocksPerSection blocks and each section
  // overlaps with the next one in (kBlocksPerSection - 1) blocks. For example,
  // the first section covers the blocks [0:5], the second covers the blocks
  // [1:6] and so on. As a result, for each value, kBlocksPerSection sections
  // need to be updated.
  int first_section_index = std::max(block_counter_ - kBlocksPerSection + 1, 0);
  int last_section_index =
      std::min(block_counter_, static_cast<int>(numerators_.size() - 1));
  float x_value = static_cast<float>(coefficients_counter_) +
                  kEarlyReverbFirstPointAtLinearRegressors;
  const float value_to_inc = kFftLengthBy2 * value;
  float value_to_add =
      x_value * value + (block_counter_ - last_section_index) * value_to_inc;
  for (int section = last_section_index; section >= first_section_index;
       --section, value_to_add += value_to_inc) {
    numerators_[section] += value_to_add;
  }

  // Check if this update was the last coefficient of the current block. In that
  // case, check if we are at the end of one of the sections and update the
  // numerator of the linear regressor that is computed in such section.
  if (++coefficients_counter_ == kFftLengthBy2) {
    if (block_counter_ >= (kBlocksPerSection - 1)) {
      size_t section = block_counter_ - (kBlocksPerSection - 1);
      RTC_DCHECK_GT(numerators_.size(), section);
      RTC_DCHECK_GT(numerators_smooth_.size(), section);
      numerators_smooth_[section] +=
          smoothing * (numerators_[section] - numerators_smooth_[section]);
      n_sections_ = section + 1;
    }
    ++block_counter_;
    coefficients_counter_ = 0;
  }
}

// Estimates the size in blocks of the early reverb. The estimation is done by
// comparing the tilt that is estimated in each section. As an optimization
// detail and due to the fact that all the linear regressors that are computed
// shared the same denominator, the comparison of the tilts is done by a
// comparison of the numerator of the linear regressors.
int ReverbDecayEstimator::EarlyReverbLengthEstimator::Estimate() {
  constexpr float N = kBlocksPerSection * kFftLengthBy2;
  constexpr float nn = SymmetricArithmetricSum(N);
  // numerator_11 refers to the quantity that the linear regressor needs in the
  // numerator for getting a decay equal to 1.1 (which is not a decay).
  // log2(1.1) * nn / kFftLengthBy2.
  constexpr float numerator_11 = 0.13750352374993502f * nn / kFftLengthBy2;
  // log2(0.8) *  nn / kFftLengthBy2.
  constexpr float numerator_08 = -0.32192809488736229f * nn / kFftLengthBy2;
  constexpr int kNumSectionsToAnalyze = 9;

  if (n_sections_ < kNumSectionsToAnalyze) {
    return 0;
  }

  // Estimation of the blocks that correspond to early reverberations. The
  // estimation is done by analyzing the impulse response. The portions of the
  // impulse response whose energy is not decreasing over its coefficients are
  // considered to be part of the early reverberations. Furthermore, the blocks
  // where the energy is decreasing faster than what it does at the end of the
  // impulse response are also considered to be part of the early
  // reverberations. The estimation is limited to the first
  // kNumSectionsToAnalyze sections.

  RTC_DCHECK_LE(n_sections_, numerators_smooth_.size());
  const float min_numerator_tail =
      *std::min_element(numerators_smooth_.begin() + kNumSectionsToAnalyze,
                        numerators_smooth_.begin() + n_sections_);
  int early_reverb_size_minus_1 = 0;
  for (int k = 0; k < kNumSectionsToAnalyze; ++k) {
    if ((numerators_smooth_[k] > numerator_11) ||
        (numerators_smooth_[k] < numerator_08 &&
         numerators_smooth_[k] < 0.9f * min_numerator_tail)) {
      early_reverb_size_minus_1 = k;
    }
  }

  return early_reverb_size_minus_1 == 0 ? 0 : early_reverb_size_minus_1 + 1;
}

void ReverbDecayEstimator::EarlyReverbLengthEstimator::Dump(
    ApmDataDumper* data_dumper) const {
  data_dumper->DumpRaw("aec3_er_acum_numerator", numerators_smooth_);
}

}  // namespace webrtc
