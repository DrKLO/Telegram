/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/matched_filter_lag_aggregator.h"

#include <algorithm>
#include <iterator>

#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {
constexpr int kPreEchoHistogramDataNotUpdated = -1;

int GetDownSamplingBlockSizeLog2(int down_sampling_factor) {
  int down_sampling_factor_log2 = 0;
  down_sampling_factor >>= 1;
  while (down_sampling_factor > 0) {
    down_sampling_factor_log2++;
    down_sampling_factor >>= 1;
  }
  return static_cast<int>(kBlockSizeLog2) > down_sampling_factor_log2
             ? static_cast<int>(kBlockSizeLog2) - down_sampling_factor_log2
             : 0;
}
}  // namespace

MatchedFilterLagAggregator::MatchedFilterLagAggregator(
    ApmDataDumper* data_dumper,
    size_t max_filter_lag,
    const EchoCanceller3Config::Delay& delay_config)
    : data_dumper_(data_dumper),
      thresholds_(delay_config.delay_selection_thresholds),
      headroom_(static_cast<int>(delay_config.delay_headroom_samples /
                                 delay_config.down_sampling_factor)),
      highest_peak_aggregator_(max_filter_lag) {
  if (delay_config.detect_pre_echo) {
    pre_echo_lag_aggregator_ = std::make_unique<PreEchoLagAggregator>(
        max_filter_lag, delay_config.down_sampling_factor);
  }
  RTC_DCHECK(data_dumper);
  RTC_DCHECK_LE(thresholds_.initial, thresholds_.converged);
}

MatchedFilterLagAggregator::~MatchedFilterLagAggregator() = default;

void MatchedFilterLagAggregator::Reset(bool hard_reset) {
  highest_peak_aggregator_.Reset();
  if (pre_echo_lag_aggregator_ != nullptr) {
    pre_echo_lag_aggregator_->Reset();
  }
  if (hard_reset) {
    significant_candidate_found_ = false;
  }
}

absl::optional<DelayEstimate> MatchedFilterLagAggregator::Aggregate(
    const absl::optional<const MatchedFilter::LagEstimate>& lag_estimate) {
  if (lag_estimate && pre_echo_lag_aggregator_) {
    pre_echo_lag_aggregator_->Dump(data_dumper_);
    pre_echo_lag_aggregator_->Aggregate(
        std::max(0, static_cast<int>(lag_estimate->pre_echo_lag) - headroom_));
  }

  if (lag_estimate) {
    highest_peak_aggregator_.Aggregate(
        std::max(0, static_cast<int>(lag_estimate->lag) - headroom_));
    rtc::ArrayView<const int> histogram = highest_peak_aggregator_.histogram();
    int candidate = highest_peak_aggregator_.candidate();
    significant_candidate_found_ = significant_candidate_found_ ||
                                   histogram[candidate] > thresholds_.converged;
    if (histogram[candidate] > thresholds_.converged ||
        (histogram[candidate] > thresholds_.initial &&
         !significant_candidate_found_)) {
      DelayEstimate::Quality quality = significant_candidate_found_
                                           ? DelayEstimate::Quality::kRefined
                                           : DelayEstimate::Quality::kCoarse;
      int reported_delay = pre_echo_lag_aggregator_ != nullptr
                               ? pre_echo_lag_aggregator_->pre_echo_candidate()
                               : candidate;
      return DelayEstimate(quality, reported_delay);
    }
  }

  return absl::nullopt;
}

MatchedFilterLagAggregator::HighestPeakAggregator::HighestPeakAggregator(
    size_t max_filter_lag)
    : histogram_(max_filter_lag + 1, 0) {
  histogram_data_.fill(0);
}

void MatchedFilterLagAggregator::HighestPeakAggregator::Reset() {
  std::fill(histogram_.begin(), histogram_.end(), 0);
  histogram_data_.fill(0);
  histogram_data_index_ = 0;
}

void MatchedFilterLagAggregator::HighestPeakAggregator::Aggregate(int lag) {
  RTC_DCHECK_GT(histogram_.size(), histogram_data_[histogram_data_index_]);
  RTC_DCHECK_LE(0, histogram_data_[histogram_data_index_]);
  --histogram_[histogram_data_[histogram_data_index_]];
  histogram_data_[histogram_data_index_] = lag;
  RTC_DCHECK_GT(histogram_.size(), histogram_data_[histogram_data_index_]);
  RTC_DCHECK_LE(0, histogram_data_[histogram_data_index_]);
  ++histogram_[histogram_data_[histogram_data_index_]];
  histogram_data_index_ = (histogram_data_index_ + 1) % histogram_data_.size();
  candidate_ =
      std::distance(histogram_.begin(),
                    std::max_element(histogram_.begin(), histogram_.end()));
}

MatchedFilterLagAggregator::PreEchoLagAggregator::PreEchoLagAggregator(
    size_t max_filter_lag,
    size_t down_sampling_factor)
    : block_size_log2_(GetDownSamplingBlockSizeLog2(down_sampling_factor)),
      histogram_(
          ((max_filter_lag + 1) * down_sampling_factor) >> kBlockSizeLog2,
          0) {
  Reset();
}

void MatchedFilterLagAggregator::PreEchoLagAggregator::Reset() {
  std::fill(histogram_.begin(), histogram_.end(), 0);
  histogram_data_.fill(kPreEchoHistogramDataNotUpdated);
  histogram_data_index_ = 0;
  pre_echo_candidate_ = 0;
}

void MatchedFilterLagAggregator::PreEchoLagAggregator::Aggregate(
    int pre_echo_lag) {
  int pre_echo_block_size = pre_echo_lag >> block_size_log2_;
  RTC_DCHECK(pre_echo_block_size >= 0 &&
             pre_echo_block_size < static_cast<int>(histogram_.size()));
  pre_echo_block_size =
      rtc::SafeClamp(pre_echo_block_size, 0, histogram_.size() - 1);
  // Remove the oldest point from the `histogram_`, it ignores the initial
  // points where no updates have been done to the `histogram_data_` array.
  if (histogram_data_[histogram_data_index_] !=
      kPreEchoHistogramDataNotUpdated) {
    --histogram_[histogram_data_[histogram_data_index_]];
  }
  histogram_data_[histogram_data_index_] = pre_echo_block_size;
  ++histogram_[histogram_data_[histogram_data_index_]];
  histogram_data_index_ = (histogram_data_index_ + 1) % histogram_data_.size();
  int pre_echo_candidate_block_size =
      std::distance(histogram_.begin(),
                    std::max_element(histogram_.begin(), histogram_.end()));
  pre_echo_candidate_ = (pre_echo_candidate_block_size << block_size_log2_);
}

void MatchedFilterLagAggregator::PreEchoLagAggregator::Dump(
    ApmDataDumper* const data_dumper) {
  data_dumper->DumpRaw("aec3_pre_echo_delay_candidate", pre_echo_candidate_);
}

}  // namespace webrtc
