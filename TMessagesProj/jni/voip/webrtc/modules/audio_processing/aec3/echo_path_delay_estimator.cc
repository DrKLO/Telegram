/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/echo_path_delay_estimator.h"

#include <array>

#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"

namespace webrtc {

EchoPathDelayEstimator::EchoPathDelayEstimator(
    ApmDataDumper* data_dumper,
    const EchoCanceller3Config& config,
    size_t num_capture_channels)
    : data_dumper_(data_dumper),
      down_sampling_factor_(config.delay.down_sampling_factor),
      sub_block_size_(down_sampling_factor_ != 0
                          ? kBlockSize / down_sampling_factor_
                          : kBlockSize),
      capture_mixer_(num_capture_channels,
                     config.delay.capture_alignment_mixing),
      capture_decimator_(down_sampling_factor_),
      matched_filter_(
          data_dumper_,
          DetectOptimization(),
          sub_block_size_,
          kMatchedFilterWindowSizeSubBlocks,
          config.delay.num_filters,
          kMatchedFilterAlignmentShiftSizeSubBlocks,
          config.delay.down_sampling_factor == 8
              ? config.render_levels.poor_excitation_render_limit_ds8
              : config.render_levels.poor_excitation_render_limit,
          config.delay.delay_estimate_smoothing,
          config.delay.delay_estimate_smoothing_delay_found,
          config.delay.delay_candidate_detection_threshold,
          config.delay.detect_pre_echo),
      matched_filter_lag_aggregator_(data_dumper_,
                                     matched_filter_.GetMaxFilterLag(),
                                     config.delay) {
  RTC_DCHECK(data_dumper);
  RTC_DCHECK(down_sampling_factor_ > 0);
}

EchoPathDelayEstimator::~EchoPathDelayEstimator() = default;

void EchoPathDelayEstimator::Reset(bool reset_delay_confidence) {
  Reset(true, reset_delay_confidence);
}

absl::optional<DelayEstimate> EchoPathDelayEstimator::EstimateDelay(
    const DownsampledRenderBuffer& render_buffer,
    const Block& capture) {
  std::array<float, kBlockSize> downsampled_capture_data;
  rtc::ArrayView<float> downsampled_capture(downsampled_capture_data.data(),
                                            sub_block_size_);

  std::array<float, kBlockSize> downmixed_capture;
  capture_mixer_.ProduceOutput(capture, downmixed_capture);
  capture_decimator_.Decimate(downmixed_capture, downsampled_capture);
  data_dumper_->DumpWav("aec3_capture_decimator_output",
                        downsampled_capture.size(), downsampled_capture.data(),
                        16000 / down_sampling_factor_, 1);
  matched_filter_.Update(render_buffer, downsampled_capture,
                         matched_filter_lag_aggregator_.ReliableDelayFound());

  absl::optional<DelayEstimate> aggregated_matched_filter_lag =
      matched_filter_lag_aggregator_.Aggregate(
          matched_filter_.GetBestLagEstimate());

  // Run clockdrift detection.
  if (aggregated_matched_filter_lag &&
      (*aggregated_matched_filter_lag).quality ==
          DelayEstimate::Quality::kRefined)
    clockdrift_detector_.Update(
        matched_filter_lag_aggregator_.GetDelayAtHighestPeak());

  // TODO(peah): Move this logging outside of this class once EchoCanceller3
  // development is done.
  data_dumper_->DumpRaw(
      "aec3_echo_path_delay_estimator_delay",
      aggregated_matched_filter_lag
          ? static_cast<int>(aggregated_matched_filter_lag->delay *
                             down_sampling_factor_)
          : -1);

  // Return the detected delay in samples as the aggregated matched filter lag
  // compensated by the down sampling factor for the signal being correlated.
  if (aggregated_matched_filter_lag) {
    aggregated_matched_filter_lag->delay *= down_sampling_factor_;
  }

  if (old_aggregated_lag_ && aggregated_matched_filter_lag &&
      old_aggregated_lag_->delay == aggregated_matched_filter_lag->delay) {
    ++consistent_estimate_counter_;
  } else {
    consistent_estimate_counter_ = 0;
  }
  old_aggregated_lag_ = aggregated_matched_filter_lag;
  constexpr size_t kNumBlocksPerSecondBy2 = kNumBlocksPerSecond / 2;
  if (consistent_estimate_counter_ > kNumBlocksPerSecondBy2) {
    Reset(false, false);
  }

  return aggregated_matched_filter_lag;
}

void EchoPathDelayEstimator::Reset(bool reset_lag_aggregator,
                                   bool reset_delay_confidence) {
  if (reset_lag_aggregator) {
    matched_filter_lag_aggregator_.Reset(reset_delay_confidence);
  }
  matched_filter_.Reset(/*full_reset=*/reset_lag_aggregator);
  old_aggregated_lag_ = absl::nullopt;
  consistent_estimate_counter_ = 0;
}
}  // namespace webrtc
