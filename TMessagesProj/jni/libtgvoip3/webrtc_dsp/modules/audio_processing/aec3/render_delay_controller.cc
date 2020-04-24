/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/render_delay_controller.h"

#include <stdlib.h>
#include <algorithm>
#include <memory>
#include <vector>

#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/echo_path_delay_estimator.h"
#include "modules/audio_processing/aec3/render_delay_controller_metrics.h"
#include "modules/audio_processing/aec3/skew_estimator.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

int GetSkewHysteresis(const EchoCanceller3Config& config) {
  if (field_trial::IsEnabled("WebRTC-Aec3EnforceSkewHysteresis1")) {
    return 1;
  }
  if (field_trial::IsEnabled("WebRTC-Aec3EnforceSkewHysteresis2")) {
    return 2;
  }

  return static_cast<int>(config.delay.skew_hysteresis_blocks);
}

bool UseOffsetBlocks() {
  return field_trial::IsEnabled("WebRTC-Aec3UseOffsetBlocks");
}

bool UseEarlyDelayDetection() {
  return !field_trial::IsEnabled("WebRTC-Aec3EarlyDelayDetectionKillSwitch");
}

constexpr int kSkewHistorySizeLog2 = 8;

class RenderDelayControllerImpl final : public RenderDelayController {
 public:
  RenderDelayControllerImpl(const EchoCanceller3Config& config,
                            int non_causal_offset,
                            int sample_rate_hz);
  ~RenderDelayControllerImpl() override;
  void Reset(bool reset_delay_confidence) override;
  void LogRenderCall() override;
  absl::optional<DelayEstimate> GetDelay(
      const DownsampledRenderBuffer& render_buffer,
      size_t render_delay_buffer_delay,
      const absl::optional<int>& echo_remover_delay,
      rtc::ArrayView<const float> capture) override;

 private:
  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const bool use_early_delay_detection_;
  const int delay_headroom_blocks_;
  const int hysteresis_limit_1_blocks_;
  const int hysteresis_limit_2_blocks_;
  const int skew_hysteresis_blocks_;
  const bool use_offset_blocks_;
  absl::optional<DelayEstimate> delay_;
  EchoPathDelayEstimator delay_estimator_;
  std::vector<float> delay_buf_;
  int delay_buf_index_ = 0;
  RenderDelayControllerMetrics metrics_;
  SkewEstimator skew_estimator_;
  absl::optional<DelayEstimate> delay_samples_;
  absl::optional<int> skew_;
  int previous_offset_blocks_ = 0;
  int skew_shift_reporting_counter_ = 0;
  size_t capture_call_counter_ = 0;
  int delay_change_counter_ = 0;
  size_t soft_reset_counter_ = 0;
  DelayEstimate::Quality last_delay_estimate_quality_;
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(RenderDelayControllerImpl);
};

DelayEstimate ComputeBufferDelay(
    const absl::optional<DelayEstimate>& current_delay,
    int delay_headroom_blocks,
    int hysteresis_limit_1_blocks,
    int hysteresis_limit_2_blocks,
    int offset_blocks,
    DelayEstimate estimated_delay) {
  // The below division is not exact and the truncation is intended.
  const int echo_path_delay_blocks = estimated_delay.delay >> kBlockSizeLog2;

  // Compute the buffer delay increase required to achieve the desired latency.
  size_t new_delay_blocks = std::max(
      echo_path_delay_blocks + offset_blocks - delay_headroom_blocks, 0);

  // Add hysteresis.
  if (current_delay) {
    size_t current_delay_blocks = current_delay->delay;
    if (new_delay_blocks > current_delay_blocks) {
      if (new_delay_blocks <=
          current_delay_blocks + hysteresis_limit_1_blocks) {
        new_delay_blocks = current_delay_blocks;
      }
    } else if (new_delay_blocks < current_delay_blocks) {
      size_t hysteresis_limit = std::max(
          static_cast<int>(current_delay_blocks) - hysteresis_limit_2_blocks,
          0);
      if (new_delay_blocks >= hysteresis_limit) {
        new_delay_blocks = current_delay_blocks;
      }
    }
  }

  DelayEstimate new_delay = estimated_delay;
  new_delay.delay = new_delay_blocks;
  return new_delay;
}

int RenderDelayControllerImpl::instance_count_ = 0;

RenderDelayControllerImpl::RenderDelayControllerImpl(
    const EchoCanceller3Config& config,
    int non_causal_offset,
    int sample_rate_hz)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      use_early_delay_detection_(UseEarlyDelayDetection()),
      delay_headroom_blocks_(
          static_cast<int>(config.delay.delay_headroom_blocks)),
      hysteresis_limit_1_blocks_(
          static_cast<int>(config.delay.hysteresis_limit_1_blocks)),
      hysteresis_limit_2_blocks_(
          static_cast<int>(config.delay.hysteresis_limit_2_blocks)),
      skew_hysteresis_blocks_(GetSkewHysteresis(config)),
      use_offset_blocks_(UseOffsetBlocks()),
      delay_estimator_(data_dumper_.get(), config),
      delay_buf_(kBlockSize * non_causal_offset, 0.f),
      skew_estimator_(kSkewHistorySizeLog2),
      last_delay_estimate_quality_(DelayEstimate::Quality::kCoarse) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz));
  delay_estimator_.LogDelayEstimationProperties(sample_rate_hz,
                                                delay_buf_.size());
}

RenderDelayControllerImpl::~RenderDelayControllerImpl() = default;

void RenderDelayControllerImpl::Reset(bool reset_delay_confidence) {
  delay_ = absl::nullopt;
  delay_samples_ = absl::nullopt;
  skew_ = absl::nullopt;
  previous_offset_blocks_ = 0;
  std::fill(delay_buf_.begin(), delay_buf_.end(), 0.f);
  delay_estimator_.Reset(reset_delay_confidence);
  skew_estimator_.Reset();
  delay_change_counter_ = 0;
  soft_reset_counter_ = 0;
  if (reset_delay_confidence) {
    last_delay_estimate_quality_ = DelayEstimate::Quality::kCoarse;
  }
}

void RenderDelayControllerImpl::LogRenderCall() {
  skew_estimator_.LogRenderCall();
}

absl::optional<DelayEstimate> RenderDelayControllerImpl::GetDelay(
    const DownsampledRenderBuffer& render_buffer,
    size_t render_delay_buffer_delay,
    const absl::optional<int>& echo_remover_delay,
    rtc::ArrayView<const float> capture) {
  RTC_DCHECK_EQ(kBlockSize, capture.size());
  ++capture_call_counter_;

  // Estimate the delay with a delayed capture.
  RTC_DCHECK_LT(delay_buf_index_ + kBlockSize - 1, delay_buf_.size());
  rtc::ArrayView<const float> capture_delayed(&delay_buf_[delay_buf_index_],
                                              kBlockSize);
  auto delay_samples =
      delay_estimator_.EstimateDelay(render_buffer, capture_delayed);

  // Overrule the delay estimator delay if the echo remover reports a delay.
  if (echo_remover_delay) {
    int total_echo_remover_delay_samples =
        (render_delay_buffer_delay + *echo_remover_delay) * kBlockSize;
    delay_samples = DelayEstimate(DelayEstimate::Quality::kRefined,
                                  total_echo_remover_delay_samples);
  }

  std::copy(capture.begin(), capture.end(),
            delay_buf_.begin() + delay_buf_index_);
  delay_buf_index_ = (delay_buf_index_ + kBlockSize) % delay_buf_.size();

  // Compute the latest skew update.
  absl::optional<int> skew = skew_estimator_.GetSkewFromCapture();

  if (delay_samples) {
    if (!delay_samples_ || delay_samples->delay != delay_samples_->delay) {
      delay_change_counter_ = 0;
    }
    if (delay_samples_) {
      delay_samples_->blocks_since_last_change =
          delay_samples_->delay == delay_samples->delay
              ? delay_samples_->blocks_since_last_change + 1
              : 0;
      delay_samples_->blocks_since_last_update = 0;
      delay_samples_->delay = delay_samples->delay;
      delay_samples_->quality = delay_samples->quality;
    } else {
      delay_samples_ = delay_samples;
    }
  } else {
    if (delay_samples_) {
      ++delay_samples_->blocks_since_last_change;
      ++delay_samples_->blocks_since_last_update;
    }
  }

  if (delay_change_counter_ < 2 * kNumBlocksPerSecond) {
    ++delay_change_counter_;
    // If a new delay estimate is recently obtained, store the skew for that.
    skew_ = skew;
  } else {
    // A reliable skew should have been obtained after 2 seconds.
    RTC_DCHECK(skew_);
    RTC_DCHECK(skew);
  }

  ++soft_reset_counter_;
  int offset_blocks = 0;
  if (skew_ && skew && delay_samples_ &&
      delay_samples_->quality == DelayEstimate::Quality::kRefined) {
    // Compute the skew offset and add a margin.
    offset_blocks = *skew_ - *skew;
    if (abs(offset_blocks) <= skew_hysteresis_blocks_) {
      offset_blocks = 0;
    } else if (soft_reset_counter_ > 10 * kNumBlocksPerSecond) {
      // Soft reset the delay estimator if there is a significant offset
      // detected.
      delay_estimator_.Reset(false);
      soft_reset_counter_ = 0;
    }
  }
  if (!use_offset_blocks_)
    offset_blocks = 0;

  // Log any changes in the skew.
  skew_shift_reporting_counter_ =
      std::max(0, skew_shift_reporting_counter_ - 1);
  absl::optional<int> skew_shift =
      skew_shift_reporting_counter_ == 0 &&
              previous_offset_blocks_ != offset_blocks
          ? absl::optional<int>(offset_blocks - previous_offset_blocks_)
          : absl::nullopt;
  previous_offset_blocks_ = offset_blocks;
  if (skew_shift) {
    RTC_LOG(LS_WARNING) << "API call skew shift of " << *skew_shift
                        << " blocks detected at capture block "
                        << capture_call_counter_;
    skew_shift_reporting_counter_ = 3 * kNumBlocksPerSecond;
  }

  if (delay_samples_) {
    // Compute the render delay buffer delay.
    const bool use_hysteresis =
        last_delay_estimate_quality_ == DelayEstimate::Quality::kRefined &&
        delay_samples_->quality == DelayEstimate::Quality::kRefined;
    delay_ = ComputeBufferDelay(delay_, delay_headroom_blocks_,
                                use_hysteresis ? hysteresis_limit_1_blocks_ : 0,
                                use_hysteresis ? hysteresis_limit_2_blocks_ : 0,
                                offset_blocks, *delay_samples_);
    last_delay_estimate_quality_ = delay_samples_->quality;
  }

  metrics_.Update(delay_samples_ ? absl::optional<size_t>(delay_samples_->delay)
                                 : absl::nullopt,
                  delay_ ? delay_->delay : 0, skew_shift);

  data_dumper_->DumpRaw("aec3_render_delay_controller_delay",
                        delay_samples ? delay_samples->delay : 0);
  data_dumper_->DumpRaw("aec3_render_delay_controller_buffer_delay",
                        delay_ ? delay_->delay : 0);

  data_dumper_->DumpRaw("aec3_render_delay_controller_new_skew",
                        skew ? *skew : 0);
  data_dumper_->DumpRaw("aec3_render_delay_controller_old_skew",
                        skew_ ? *skew_ : 0);
  data_dumper_->DumpRaw("aec3_render_delay_controller_offset", offset_blocks);

  return delay_;
}

}  // namespace

RenderDelayController* RenderDelayController::Create(
    const EchoCanceller3Config& config,
    int non_causal_offset,
    int sample_rate_hz) {
  return new RenderDelayControllerImpl(config, non_causal_offset,
                                       sample_rate_hz);
}

}  // namespace webrtc
