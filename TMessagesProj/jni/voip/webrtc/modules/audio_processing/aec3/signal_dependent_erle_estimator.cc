/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/signal_dependent_erle_estimator.h"

#include <algorithm>
#include <functional>
#include <numeric>

#include "modules/audio_processing/aec3/spectrum_buffer.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

namespace {

constexpr std::array<size_t, SignalDependentErleEstimator::kSubbands + 1>
    kBandBoundaries = {1, 8, 16, 24, 32, 48, kFftLengthBy2Plus1};

std::array<size_t, kFftLengthBy2Plus1> FormSubbandMap() {
  std::array<size_t, kFftLengthBy2Plus1> map_band_to_subband;
  size_t subband = 1;
  for (size_t k = 0; k < map_band_to_subband.size(); ++k) {
    RTC_DCHECK_LT(subband, kBandBoundaries.size());
    if (k >= kBandBoundaries[subband]) {
      subband++;
      RTC_DCHECK_LT(k, kBandBoundaries[subband]);
    }
    map_band_to_subband[k] = subband - 1;
  }
  return map_band_to_subband;
}

// Defines the size in blocks of the sections that are used for dividing the
// linear filter. The sections are split in a non-linear manner so that lower
// sections that typically represent the direct path have a larger resolution
// than the higher sections which typically represent more reverberant acoustic
// paths.
std::vector<size_t> DefineFilterSectionSizes(size_t delay_headroom_blocks,
                                             size_t num_blocks,
                                             size_t num_sections) {
  size_t filter_length_blocks = num_blocks - delay_headroom_blocks;
  std::vector<size_t> section_sizes(num_sections);
  size_t remaining_blocks = filter_length_blocks;
  size_t remaining_sections = num_sections;
  size_t estimator_size = 2;
  size_t idx = 0;
  while (remaining_sections > 1 &&
         remaining_blocks > estimator_size * remaining_sections) {
    RTC_DCHECK_LT(idx, section_sizes.size());
    section_sizes[idx] = estimator_size;
    remaining_blocks -= estimator_size;
    remaining_sections--;
    estimator_size *= 2;
    idx++;
  }

  size_t last_groups_size = remaining_blocks / remaining_sections;
  for (; idx < num_sections; idx++) {
    section_sizes[idx] = last_groups_size;
  }
  section_sizes[num_sections - 1] +=
      remaining_blocks - last_groups_size * remaining_sections;
  return section_sizes;
}

// Forms the limits in blocks for each filter section. Those sections
// are used for analyzing the echo estimates and investigating which
// linear filter sections contribute most to the echo estimate energy.
std::vector<size_t> SetSectionsBoundaries(size_t delay_headroom_blocks,
                                          size_t num_blocks,
                                          size_t num_sections) {
  std::vector<size_t> estimator_boundaries_blocks(num_sections + 1);
  if (estimator_boundaries_blocks.size() == 2) {
    estimator_boundaries_blocks[0] = 0;
    estimator_boundaries_blocks[1] = num_blocks;
    return estimator_boundaries_blocks;
  }
  RTC_DCHECK_GT(estimator_boundaries_blocks.size(), 2);
  const std::vector<size_t> section_sizes =
      DefineFilterSectionSizes(delay_headroom_blocks, num_blocks,
                               estimator_boundaries_blocks.size() - 1);

  size_t idx = 0;
  size_t current_size_block = 0;
  RTC_DCHECK_EQ(section_sizes.size() + 1, estimator_boundaries_blocks.size());
  estimator_boundaries_blocks[0] = delay_headroom_blocks;
  for (size_t k = delay_headroom_blocks; k < num_blocks; ++k) {
    current_size_block++;
    if (current_size_block >= section_sizes[idx]) {
      idx = idx + 1;
      if (idx == section_sizes.size()) {
        break;
      }
      estimator_boundaries_blocks[idx] = k + 1;
      current_size_block = 0;
    }
  }
  estimator_boundaries_blocks[section_sizes.size()] = num_blocks;
  return estimator_boundaries_blocks;
}

std::array<float, SignalDependentErleEstimator::kSubbands>
SetMaxErleSubbands(float max_erle_l, float max_erle_h, size_t limit_subband_l) {
  std::array<float, SignalDependentErleEstimator::kSubbands> max_erle;
  std::fill(max_erle.begin(), max_erle.begin() + limit_subband_l, max_erle_l);
  std::fill(max_erle.begin() + limit_subband_l, max_erle.end(), max_erle_h);
  return max_erle;
}

}  // namespace

SignalDependentErleEstimator::SignalDependentErleEstimator(
    const EchoCanceller3Config& config,
    size_t num_capture_channels)
    : min_erle_(config.erle.min),
      num_sections_(config.erle.num_sections),
      num_blocks_(config.filter.refined.length_blocks),
      delay_headroom_blocks_(config.delay.delay_headroom_samples / kBlockSize),
      band_to_subband_(FormSubbandMap()),
      max_erle_(SetMaxErleSubbands(config.erle.max_l,
                                   config.erle.max_h,
                                   band_to_subband_[kFftLengthBy2 / 2])),
      section_boundaries_blocks_(SetSectionsBoundaries(delay_headroom_blocks_,
                                                       num_blocks_,
                                                       num_sections_)),
      use_onset_detection_(config.erle.onset_detection),
      erle_(num_capture_channels),
      erle_onset_compensated_(num_capture_channels),
      S2_section_accum_(
          num_capture_channels,
          std::vector<std::array<float, kFftLengthBy2Plus1>>(num_sections_)),
      erle_estimators_(
          num_capture_channels,
          std::vector<std::array<float, kSubbands>>(num_sections_)),
      erle_ref_(num_capture_channels),
      correction_factors_(
          num_capture_channels,
          std::vector<std::array<float, kSubbands>>(num_sections_)),
      num_updates_(num_capture_channels),
      n_active_sections_(num_capture_channels) {
  RTC_DCHECK_LE(num_sections_, num_blocks_);
  RTC_DCHECK_GE(num_sections_, 1);
  Reset();
}

SignalDependentErleEstimator::~SignalDependentErleEstimator() = default;

void SignalDependentErleEstimator::Reset() {
  for (size_t ch = 0; ch < erle_.size(); ++ch) {
    erle_[ch].fill(min_erle_);
    erle_onset_compensated_[ch].fill(min_erle_);
    for (auto& erle_estimator : erle_estimators_[ch]) {
      erle_estimator.fill(min_erle_);
    }
    erle_ref_[ch].fill(min_erle_);
    for (auto& factor : correction_factors_[ch]) {
      factor.fill(1.0f);
    }
    num_updates_[ch].fill(0);
    n_active_sections_[ch].fill(0);
  }
}

// Updates the Erle estimate by analyzing the current input signals. It takes
// the render buffer and the filter frequency response in order to do an
// estimation of the number of sections of the linear filter that are needed
// for getting the majority of the energy in the echo estimate. Based on that
// number of sections, it updates the erle estimation by introducing a
// correction factor to the erle that is given as an input to this method.
void SignalDependentErleEstimator::Update(
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const std::vector<std::array<float, kFftLengthBy2Plus1>>>
        filter_frequency_responses,
    rtc::ArrayView<const float, kFftLengthBy2Plus1> X2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> Y2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> E2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> average_erle,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        average_erle_onset_compensated,
    const std::vector<bool>& converged_filters) {
  RTC_DCHECK_GT(num_sections_, 1);

  // Gets the number of filter sections that are needed for achieving 90 %
  // of the power spectrum energy of the echo estimate.
  ComputeNumberOfActiveFilterSections(render_buffer,
                                      filter_frequency_responses);

  // Updates the correction factors that is used for correcting the erle and
  // adapt it to the particular characteristics of the input signal.
  UpdateCorrectionFactors(X2, Y2, E2, converged_filters);

  // Applies the correction factor to the input erle for getting a more refined
  // erle estimation for the current input signal.
  for (size_t ch = 0; ch < erle_.size(); ++ch) {
    for (size_t k = 0; k < kFftLengthBy2; ++k) {
      RTC_DCHECK_GT(correction_factors_[ch].size(), n_active_sections_[ch][k]);
      float correction_factor =
          correction_factors_[ch][n_active_sections_[ch][k]]
                             [band_to_subband_[k]];
      erle_[ch][k] = rtc::SafeClamp(average_erle[ch][k] * correction_factor,
                                    min_erle_, max_erle_[band_to_subband_[k]]);
      if (use_onset_detection_) {
        erle_onset_compensated_[ch][k] = rtc::SafeClamp(
            average_erle_onset_compensated[ch][k] * correction_factor,
            min_erle_, max_erle_[band_to_subband_[k]]);
      }
    }
  }
}

void SignalDependentErleEstimator::Dump(
    const std::unique_ptr<ApmDataDumper>& data_dumper) const {
  for (auto& erle : erle_estimators_[0]) {
    data_dumper->DumpRaw("aec3_all_erle", erle);
  }
  data_dumper->DumpRaw("aec3_ref_erle", erle_ref_[0]);
  for (auto& factor : correction_factors_[0]) {
    data_dumper->DumpRaw("aec3_erle_correction_factor", factor);
  }
}

// Estimates for each band the smallest number of sections in the filter that
// together constitute 90% of the estimated echo energy.
void SignalDependentErleEstimator::ComputeNumberOfActiveFilterSections(
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const std::vector<std::array<float, kFftLengthBy2Plus1>>>
        filter_frequency_responses) {
  RTC_DCHECK_GT(num_sections_, 1);
  // Computes an approximation of the power spectrum if the filter would have
  // been limited to a certain number of filter sections.
  ComputeEchoEstimatePerFilterSection(render_buffer,
                                      filter_frequency_responses);
  // For each band, computes the number of filter sections that are needed for
  // achieving the 90 % energy in the echo estimate.
  ComputeActiveFilterSections();
}

void SignalDependentErleEstimator::UpdateCorrectionFactors(
    rtc::ArrayView<const float, kFftLengthBy2Plus1> X2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> Y2,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> E2,
    const std::vector<bool>& converged_filters) {
  for (size_t ch = 0; ch < converged_filters.size(); ++ch) {
    if (converged_filters[ch]) {
      constexpr float kX2BandEnergyThreshold = 44015068.0f;
      constexpr float kSmthConstantDecreases = 0.1f;
      constexpr float kSmthConstantIncreases = kSmthConstantDecreases / 2.f;
      auto subband_powers = [](rtc::ArrayView<const float> power_spectrum,
                               rtc::ArrayView<float> power_spectrum_subbands) {
        for (size_t subband = 0; subband < kSubbands; ++subband) {
          RTC_DCHECK_LE(kBandBoundaries[subband + 1], power_spectrum.size());
          power_spectrum_subbands[subband] = std::accumulate(
              power_spectrum.begin() + kBandBoundaries[subband],
              power_spectrum.begin() + kBandBoundaries[subband + 1], 0.f);
        }
      };

      std::array<float, kSubbands> X2_subbands, E2_subbands, Y2_subbands;
      subband_powers(X2, X2_subbands);
      subband_powers(E2[ch], E2_subbands);
      subband_powers(Y2[ch], Y2_subbands);
      std::array<size_t, kSubbands> idx_subbands;
      for (size_t subband = 0; subband < kSubbands; ++subband) {
        // When aggregating the number of active sections in the filter for
        // different bands we choose to take the minimum of all of them. As an
        // example, if for one of the bands it is the direct path its refined
        // contributor to the final echo estimate, we consider the direct path
        // is as well the refined contributor for the subband that contains that
        // particular band. That aggregate number of sections will be later used
        // as the identifier of the erle estimator that needs to be updated.
        RTC_DCHECK_LE(kBandBoundaries[subband + 1],
                      n_active_sections_[ch].size());
        idx_subbands[subband] = *std::min_element(
            n_active_sections_[ch].begin() + kBandBoundaries[subband],
            n_active_sections_[ch].begin() + kBandBoundaries[subband + 1]);
      }

      std::array<float, kSubbands> new_erle;
      std::array<bool, kSubbands> is_erle_updated;
      is_erle_updated.fill(false);
      new_erle.fill(0.f);
      for (size_t subband = 0; subband < kSubbands; ++subband) {
        if (X2_subbands[subband] > kX2BandEnergyThreshold &&
            E2_subbands[subband] > 0) {
          new_erle[subband] = Y2_subbands[subband] / E2_subbands[subband];
          RTC_DCHECK_GT(new_erle[subband], 0);
          is_erle_updated[subband] = true;
          ++num_updates_[ch][subband];
        }
      }

      for (size_t subband = 0; subband < kSubbands; ++subband) {
        const size_t idx = idx_subbands[subband];
        RTC_DCHECK_LT(idx, erle_estimators_[ch].size());
        float alpha = new_erle[subband] > erle_estimators_[ch][idx][subband]
                          ? kSmthConstantIncreases
                          : kSmthConstantDecreases;
        alpha = static_cast<float>(is_erle_updated[subband]) * alpha;
        erle_estimators_[ch][idx][subband] +=
            alpha * (new_erle[subband] - erle_estimators_[ch][idx][subband]);
        erle_estimators_[ch][idx][subband] = rtc::SafeClamp(
            erle_estimators_[ch][idx][subband], min_erle_, max_erle_[subband]);
      }

      for (size_t subband = 0; subband < kSubbands; ++subband) {
        float alpha = new_erle[subband] > erle_ref_[ch][subband]
                          ? kSmthConstantIncreases
                          : kSmthConstantDecreases;
        alpha = static_cast<float>(is_erle_updated[subband]) * alpha;
        erle_ref_[ch][subband] +=
            alpha * (new_erle[subband] - erle_ref_[ch][subband]);
        erle_ref_[ch][subband] = rtc::SafeClamp(erle_ref_[ch][subband],
                                                min_erle_, max_erle_[subband]);
      }

      for (size_t subband = 0; subband < kSubbands; ++subband) {
        constexpr int kNumUpdateThr = 50;
        if (is_erle_updated[subband] &&
            num_updates_[ch][subband] > kNumUpdateThr) {
          const size_t idx = idx_subbands[subband];
          RTC_DCHECK_GT(erle_ref_[ch][subband], 0.f);
          // Computes the ratio between the erle that is updated using all the
          // points and the erle that is updated only on signals that share the
          // same number of active filter sections.
          float new_correction_factor =
              erle_estimators_[ch][idx][subband] / erle_ref_[ch][subband];

          correction_factors_[ch][idx][subband] +=
              0.1f *
              (new_correction_factor - correction_factors_[ch][idx][subband]);
        }
      }
    }
  }
}

void SignalDependentErleEstimator::ComputeEchoEstimatePerFilterSection(
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const std::vector<std::array<float, kFftLengthBy2Plus1>>>
        filter_frequency_responses) {
  const SpectrumBuffer& spectrum_render_buffer =
      render_buffer.GetSpectrumBuffer();
  const size_t num_render_channels = spectrum_render_buffer.buffer[0].size();
  const size_t num_capture_channels = S2_section_accum_.size();
  const float one_by_num_render_channels = 1.f / num_render_channels;

  RTC_DCHECK_EQ(S2_section_accum_.size(), filter_frequency_responses.size());

  for (size_t capture_ch = 0; capture_ch < num_capture_channels; ++capture_ch) {
    RTC_DCHECK_EQ(S2_section_accum_[capture_ch].size() + 1,
                  section_boundaries_blocks_.size());
    size_t idx_render = render_buffer.Position();
    idx_render = spectrum_render_buffer.OffsetIndex(
        idx_render, section_boundaries_blocks_[0]);

    for (size_t section = 0; section < num_sections_; ++section) {
      std::array<float, kFftLengthBy2Plus1> X2_section;
      std::array<float, kFftLengthBy2Plus1> H2_section;
      X2_section.fill(0.f);
      H2_section.fill(0.f);
      const size_t block_limit =
          std::min(section_boundaries_blocks_[section + 1],
                   filter_frequency_responses[capture_ch].size());
      for (size_t block = section_boundaries_blocks_[section];
           block < block_limit; ++block) {
        for (size_t render_ch = 0;
             render_ch < spectrum_render_buffer.buffer[idx_render].size();
             ++render_ch) {
          for (size_t k = 0; k < X2_section.size(); ++k) {
            X2_section[k] +=
                spectrum_render_buffer.buffer[idx_render][render_ch][k] *
                one_by_num_render_channels;
          }
        }
        std::transform(H2_section.begin(), H2_section.end(),
                       filter_frequency_responses[capture_ch][block].begin(),
                       H2_section.begin(), std::plus<float>());
        idx_render = spectrum_render_buffer.IncIndex(idx_render);
      }

      std::transform(X2_section.begin(), X2_section.end(), H2_section.begin(),
                     S2_section_accum_[capture_ch][section].begin(),
                     std::multiplies<float>());
    }

    for (size_t section = 1; section < num_sections_; ++section) {
      std::transform(S2_section_accum_[capture_ch][section - 1].begin(),
                     S2_section_accum_[capture_ch][section - 1].end(),
                     S2_section_accum_[capture_ch][section].begin(),
                     S2_section_accum_[capture_ch][section].begin(),
                     std::plus<float>());
    }
  }
}

void SignalDependentErleEstimator::ComputeActiveFilterSections() {
  for (size_t ch = 0; ch < n_active_sections_.size(); ++ch) {
    std::fill(n_active_sections_[ch].begin(), n_active_sections_[ch].end(), 0);
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      size_t section = num_sections_;
      float target = 0.9f * S2_section_accum_[ch][num_sections_ - 1][k];
      while (section > 0 && S2_section_accum_[ch][section - 1][k] >= target) {
        n_active_sections_[ch][k] = --section;
      }
    }
  }
}
}  // namespace webrtc
