/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_MATCHED_FILTER_LAG_AGGREGATOR_H_
#define MODULES_AUDIO_PROCESSING_AEC3_MATCHED_FILTER_LAG_AGGREGATOR_H_

#include <vector>

#include "absl/types/optional.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/delay_estimate.h"
#include "modules/audio_processing/aec3/matched_filter.h"

namespace webrtc {

class ApmDataDumper;

// Aggregates lag estimates produced by the MatchedFilter class into a single
// reliable combined lag estimate.
class MatchedFilterLagAggregator {
 public:
  MatchedFilterLagAggregator(
      ApmDataDumper* data_dumper,
      size_t max_filter_lag,
      const EchoCanceller3Config::Delay::DelaySelectionThresholds& thresholds);

  MatchedFilterLagAggregator() = delete;
  MatchedFilterLagAggregator(const MatchedFilterLagAggregator&) = delete;
  MatchedFilterLagAggregator& operator=(const MatchedFilterLagAggregator&) =
      delete;

  ~MatchedFilterLagAggregator();

  // Resets the aggregator.
  void Reset(bool hard_reset);

  // Aggregates the provided lag estimates.
  absl::optional<DelayEstimate> Aggregate(
      rtc::ArrayView<const MatchedFilter::LagEstimate> lag_estimates);

  // Returns whether a reliable delay estimate has been found.
  bool ReliableDelayFound() const { return significant_candidate_found_; }

 private:
  ApmDataDumper* const data_dumper_;
  std::vector<int> histogram_;
  std::array<int, 250> histogram_data_;
  int histogram_data_index_ = 0;
  bool significant_candidate_found_ = false;
  const EchoCanceller3Config::Delay::DelaySelectionThresholds thresholds_;
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_MATCHED_FILTER_LAG_AGGREGATOR_H_
