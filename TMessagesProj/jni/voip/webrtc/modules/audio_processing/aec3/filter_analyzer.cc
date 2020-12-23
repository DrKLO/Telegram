/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/filter_analyzer.h"

#include <math.h>

#include <algorithm>
#include <array>
#include <numeric>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/render_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

size_t FindPeakIndex(rtc::ArrayView<const float> filter_time_domain,
                     size_t peak_index_in,
                     size_t start_sample,
                     size_t end_sample) {
  size_t peak_index_out = peak_index_in;
  float max_h2 =
      filter_time_domain[peak_index_out] * filter_time_domain[peak_index_out];
  for (size_t k = start_sample; k <= end_sample; ++k) {
    float tmp = filter_time_domain[k] * filter_time_domain[k];
    if (tmp > max_h2) {
      peak_index_out = k;
      max_h2 = tmp;
    }
  }

  return peak_index_out;
}

}  // namespace

int FilterAnalyzer::instance_count_ = 0;

FilterAnalyzer::FilterAnalyzer(const EchoCanceller3Config& config,
                               size_t num_capture_channels)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      bounded_erl_(config.ep_strength.bounded_erl),
      default_gain_(config.ep_strength.default_gain),
      h_highpass_(num_capture_channels,
                  std::vector<float>(
                      GetTimeDomainLength(config.filter.refined.length_blocks),
                      0.f)),
      filter_analysis_states_(num_capture_channels,
                              FilterAnalysisState(config)),
      filter_delays_blocks_(num_capture_channels, 0) {
  Reset();
}

FilterAnalyzer::~FilterAnalyzer() = default;

void FilterAnalyzer::Reset() {
  blocks_since_reset_ = 0;
  ResetRegion();
  for (auto& state : filter_analysis_states_) {
    state.Reset(default_gain_);
  }
  std::fill(filter_delays_blocks_.begin(), filter_delays_blocks_.end(), 0);
}

void FilterAnalyzer::Update(
    rtc::ArrayView<const std::vector<float>> filters_time_domain,
    const RenderBuffer& render_buffer,
    bool* any_filter_consistent,
    float* max_echo_path_gain) {
  RTC_DCHECK(any_filter_consistent);
  RTC_DCHECK(max_echo_path_gain);
  RTC_DCHECK_EQ(filters_time_domain.size(), filter_analysis_states_.size());
  RTC_DCHECK_EQ(filters_time_domain.size(), h_highpass_.size());

  ++blocks_since_reset_;
  SetRegionToAnalyze(filters_time_domain[0].size());
  AnalyzeRegion(filters_time_domain, render_buffer);

  // Aggregate the results for all capture channels.
  auto& st_ch0 = filter_analysis_states_[0];
  *any_filter_consistent = st_ch0.consistent_estimate;
  *max_echo_path_gain = st_ch0.gain;
  min_filter_delay_blocks_ = filter_delays_blocks_[0];
  for (size_t ch = 1; ch < filters_time_domain.size(); ++ch) {
    auto& st_ch = filter_analysis_states_[ch];
    *any_filter_consistent =
        *any_filter_consistent || st_ch.consistent_estimate;
    *max_echo_path_gain = std::max(*max_echo_path_gain, st_ch.gain);
    min_filter_delay_blocks_ =
        std::min(min_filter_delay_blocks_, filter_delays_blocks_[ch]);
  }
}

void FilterAnalyzer::AnalyzeRegion(
    rtc::ArrayView<const std::vector<float>> filters_time_domain,
    const RenderBuffer& render_buffer) {
  // Preprocess the filter to avoid issues with low-frequency components in the
  // filter.
  PreProcessFilters(filters_time_domain);
  data_dumper_->DumpRaw("aec3_linear_filter_processed_td", h_highpass_[0]);

  constexpr float kOneByBlockSize = 1.f / kBlockSize;
  for (size_t ch = 0; ch < filters_time_domain.size(); ++ch) {
    RTC_DCHECK_LT(region_.start_sample_, filters_time_domain[ch].size());
    RTC_DCHECK_LT(region_.end_sample_, filters_time_domain[ch].size());

    auto& st_ch = filter_analysis_states_[ch];
    RTC_DCHECK_EQ(h_highpass_[ch].size(), filters_time_domain[ch].size());
    RTC_DCHECK_GT(h_highpass_[ch].size(), 0);
    st_ch.peak_index = std::min(st_ch.peak_index, h_highpass_[ch].size() - 1);

    st_ch.peak_index =
        FindPeakIndex(h_highpass_[ch], st_ch.peak_index, region_.start_sample_,
                      region_.end_sample_);
    filter_delays_blocks_[ch] = st_ch.peak_index >> kBlockSizeLog2;
    UpdateFilterGain(h_highpass_[ch], &st_ch);
    st_ch.filter_length_blocks =
        filters_time_domain[ch].size() * kOneByBlockSize;

    st_ch.consistent_estimate = st_ch.consistent_filter_detector.Detect(
        h_highpass_[ch], region_,
        render_buffer.Block(-filter_delays_blocks_[ch])[0], st_ch.peak_index,
        filter_delays_blocks_[ch]);
  }
}

void FilterAnalyzer::UpdateFilterGain(
    rtc::ArrayView<const float> filter_time_domain,
    FilterAnalysisState* st) {
  bool sufficient_time_to_converge =
      blocks_since_reset_ > 5 * kNumBlocksPerSecond;

  if (sufficient_time_to_converge && st->consistent_estimate) {
    st->gain = fabsf(filter_time_domain[st->peak_index]);
  } else {
    // TODO(peah): Verify whether this check against a float is ok.
    if (st->gain) {
      st->gain = std::max(st->gain, fabsf(filter_time_domain[st->peak_index]));
    }
  }

  if (bounded_erl_ && st->gain) {
    st->gain = std::max(st->gain, 0.01f);
  }
}

void FilterAnalyzer::PreProcessFilters(
    rtc::ArrayView<const std::vector<float>> filters_time_domain) {
  for (size_t ch = 0; ch < filters_time_domain.size(); ++ch) {
    RTC_DCHECK_LT(region_.start_sample_, filters_time_domain[ch].size());
    RTC_DCHECK_LT(region_.end_sample_, filters_time_domain[ch].size());

    RTC_DCHECK_GE(h_highpass_[ch].capacity(), filters_time_domain[ch].size());
    h_highpass_[ch].resize(filters_time_domain[ch].size());
    // Minimum phase high-pass filter with cutoff frequency at about 600 Hz.
    constexpr std::array<float, 3> h = {
        {0.7929742f, -0.36072128f, -0.47047766f}};

    std::fill(h_highpass_[ch].begin() + region_.start_sample_,
              h_highpass_[ch].begin() + region_.end_sample_ + 1, 0.f);
    for (size_t k = std::max(h.size() - 1, region_.start_sample_);
         k <= region_.end_sample_; ++k) {
      for (size_t j = 0; j < h.size(); ++j) {
        h_highpass_[ch][k] += filters_time_domain[ch][k - j] * h[j];
      }
    }
  }
}

void FilterAnalyzer::ResetRegion() {
  region_.start_sample_ = 0;
  region_.end_sample_ = 0;
}

void FilterAnalyzer::SetRegionToAnalyze(size_t filter_size) {
  constexpr size_t kNumberBlocksToUpdate = 1;
  auto& r = region_;
  r.start_sample_ = r.end_sample_ >= filter_size - 1 ? 0 : r.end_sample_ + 1;
  r.end_sample_ =
      std::min(r.start_sample_ + kNumberBlocksToUpdate * kBlockSize - 1,
               filter_size - 1);

  // Check range.
  RTC_DCHECK_LT(r.start_sample_, filter_size);
  RTC_DCHECK_LT(r.end_sample_, filter_size);
  RTC_DCHECK_LE(r.start_sample_, r.end_sample_);
}

FilterAnalyzer::ConsistentFilterDetector::ConsistentFilterDetector(
    const EchoCanceller3Config& config)
    : active_render_threshold_(config.render_levels.active_render_limit *
                               config.render_levels.active_render_limit *
                               kFftLengthBy2) {
  Reset();
}

void FilterAnalyzer::ConsistentFilterDetector::Reset() {
  significant_peak_ = false;
  filter_floor_accum_ = 0.f;
  filter_secondary_peak_ = 0.f;
  filter_floor_low_limit_ = 0;
  filter_floor_high_limit_ = 0;
  consistent_estimate_counter_ = 0;
  consistent_delay_reference_ = -10;
}

bool FilterAnalyzer::ConsistentFilterDetector::Detect(
    rtc::ArrayView<const float> filter_to_analyze,
    const FilterRegion& region,
    rtc::ArrayView<const std::vector<float>> x_block,
    size_t peak_index,
    int delay_blocks) {
  if (region.start_sample_ == 0) {
    filter_floor_accum_ = 0.f;
    filter_secondary_peak_ = 0.f;
    filter_floor_low_limit_ = peak_index < 64 ? 0 : peak_index - 64;
    filter_floor_high_limit_ =
        peak_index > filter_to_analyze.size() - 129 ? 0 : peak_index + 128;
  }

  for (size_t k = region.start_sample_;
       k < std::min(region.end_sample_ + 1, filter_floor_low_limit_); ++k) {
    float abs_h = fabsf(filter_to_analyze[k]);
    filter_floor_accum_ += abs_h;
    filter_secondary_peak_ = std::max(filter_secondary_peak_, abs_h);
  }

  for (size_t k = std::max(filter_floor_high_limit_, region.start_sample_);
       k <= region.end_sample_; ++k) {
    float abs_h = fabsf(filter_to_analyze[k]);
    filter_floor_accum_ += abs_h;
    filter_secondary_peak_ = std::max(filter_secondary_peak_, abs_h);
  }

  if (region.end_sample_ == filter_to_analyze.size() - 1) {
    float filter_floor = filter_floor_accum_ /
                         (filter_floor_low_limit_ + filter_to_analyze.size() -
                          filter_floor_high_limit_);

    float abs_peak = fabsf(filter_to_analyze[peak_index]);
    significant_peak_ = abs_peak > 10.f * filter_floor &&
                        abs_peak > 2.f * filter_secondary_peak_;
  }

  if (significant_peak_) {
    bool active_render_block = false;
    for (auto& x_channel : x_block) {
      const float x_energy = std::inner_product(
          x_channel.begin(), x_channel.end(), x_channel.begin(), 0.f);
      if (x_energy > active_render_threshold_) {
        active_render_block = true;
        break;
      }
    }

    if (consistent_delay_reference_ == delay_blocks) {
      if (active_render_block) {
        ++consistent_estimate_counter_;
      }
    } else {
      consistent_estimate_counter_ = 0;
      consistent_delay_reference_ = delay_blocks;
    }
  }
  return consistent_estimate_counter_ > 1.5f * kNumBlocksPerSecond;
}

}  // namespace webrtc
