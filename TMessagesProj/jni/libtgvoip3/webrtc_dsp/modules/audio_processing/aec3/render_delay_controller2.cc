/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <stddef.h>
#include <algorithm>
#include <memory>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/delay_estimate.h"
#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/aec3/echo_path_delay_estimator.h"
#include "modules/audio_processing/aec3/render_delay_controller.h"
#include "modules/audio_processing/aec3/render_delay_controller_metrics.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

bool UseEarlyDelayDetection() {
  return !field_trial::IsEnabled("WebRTC-Aec3EarlyDelayDetectionKillSwitch");
}

class RenderDelayControllerImpl2 final : public RenderDelayController {
 public:
  RenderDelayControllerImpl2(const EchoCanceller3Config& config,
                             int sample_rate_hz);
  ~RenderDelayControllerImpl2() override;
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
  absl::optional<DelayEstimate> delay_;
  EchoPathDelayEstimator delay_estimator_;
  RenderDelayControllerMetrics metrics_;
  absl::optional<DelayEstimate> delay_samples_;
  size_t capture_call_counter_ = 0;
  int delay_change_counter_ = 0;
  DelayEstimate::Quality last_delay_estimate_quality_;
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(RenderDelayControllerImpl2);
};

DelayEstimate ComputeBufferDelay(
    const absl::optional<DelayEstimate>& current_delay,
    int delay_headroom_blocks,
    int hysteresis_limit_1_blocks,
    int hysteresis_limit_2_blocks,
    DelayEstimate estimated_delay) {
  // The below division is not exact and the truncation is intended.
  const int echo_path_delay_blocks = estimated_delay.delay >> kBlockSizeLog2;

  // Compute the buffer delay increase required to achieve the desired latency.
  size_t new_delay_blocks =
      std::max(echo_path_delay_blocks - delay_headroom_blocks, 0);

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

int RenderDelayControllerImpl2::instance_count_ = 0;

RenderDelayControllerImpl2::RenderDelayControllerImpl2(
    const EchoCanceller3Config& config,
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
      delay_estimator_(data_dumper_.get(), config),
      last_delay_estimate_quality_(DelayEstimate::Quality::kCoarse) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz));
  delay_estimator_.LogDelayEstimationProperties(sample_rate_hz, 0);
}

RenderDelayControllerImpl2::~RenderDelayControllerImpl2() = default;

void RenderDelayControllerImpl2::Reset(bool reset_delay_confidence) {
  delay_ = absl::nullopt;
  delay_samples_ = absl::nullopt;
  delay_estimator_.Reset(reset_delay_confidence);
  delay_change_counter_ = 0;
  if (reset_delay_confidence) {
    last_delay_estimate_quality_ = DelayEstimate::Quality::kCoarse;
  }
}

void RenderDelayControllerImpl2::LogRenderCall() {}

absl::optional<DelayEstimate> RenderDelayControllerImpl2::GetDelay(
    const DownsampledRenderBuffer& render_buffer,
    size_t render_delay_buffer_delay,
    const absl::optional<int>& echo_remover_delay,
    rtc::ArrayView<const float> capture) {
  RTC_DCHECK_EQ(kBlockSize, capture.size());
  ++capture_call_counter_;

  auto delay_samples = delay_estimator_.EstimateDelay(render_buffer, capture);

  // Overrule the delay estimator delay if the echo remover reports a delay.
  if (echo_remover_delay) {
    int total_echo_remover_delay_samples =
        (render_delay_buffer_delay + *echo_remover_delay) * kBlockSize;
    delay_samples = DelayEstimate(DelayEstimate::Quality::kRefined,
                                  total_echo_remover_delay_samples);
  }

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
  }

  if (delay_samples_) {
    // Compute the render delay buffer delay.
    const bool use_hysteresis =
        last_delay_estimate_quality_ == DelayEstimate::Quality::kRefined &&
        delay_samples_->quality == DelayEstimate::Quality::kRefined;
    delay_ = ComputeBufferDelay(delay_, delay_headroom_blocks_,
                                use_hysteresis ? hysteresis_limit_1_blocks_ : 0,
                                use_hysteresis ? hysteresis_limit_2_blocks_ : 0,
                                *delay_samples_);
    last_delay_estimate_quality_ = delay_samples_->quality;
  }

  metrics_.Update(delay_samples_ ? absl::optional<size_t>(delay_samples_->delay)
                                 : absl::nullopt,
                  delay_ ? delay_->delay : 0, 0);

  data_dumper_->DumpRaw("aec3_render_delay_controller_delay",
                        delay_samples ? delay_samples->delay : 0);
  data_dumper_->DumpRaw("aec3_render_delay_controller_buffer_delay",
                        delay_ ? delay_->delay : 0);

  return delay_;
}

}  // namespace

RenderDelayController* RenderDelayController::Create2(
    const EchoCanceller3Config& config,
    int sample_rate_hz) {
  return new RenderDelayControllerImpl2(config, sample_rate_hz);
}

}  // namespace webrtc
