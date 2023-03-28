/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/render_delay_controller.h"

#include <stddef.h>

#include <algorithm>
#include <atomic>
#include <memory>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/delay_estimate.h"
#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/aec3/echo_path_delay_estimator.h"
#include "modules/audio_processing/aec3/render_delay_controller_metrics.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

class RenderDelayControllerImpl final : public RenderDelayController {
 public:
  RenderDelayControllerImpl(const EchoCanceller3Config& config,
                            int sample_rate_hz,
                            size_t num_capture_channels);

  RenderDelayControllerImpl() = delete;
  RenderDelayControllerImpl(const RenderDelayControllerImpl&) = delete;
  RenderDelayControllerImpl& operator=(const RenderDelayControllerImpl&) =
      delete;

  ~RenderDelayControllerImpl() override;
  void Reset(bool reset_delay_confidence) override;
  void LogRenderCall() override;
  absl::optional<DelayEstimate> GetDelay(
      const DownsampledRenderBuffer& render_buffer,
      size_t render_delay_buffer_delay,
      const Block& capture) override;
  bool HasClockdrift() const override;

 private:
  static std::atomic<int> instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const int hysteresis_limit_blocks_;
  absl::optional<DelayEstimate> delay_;
  EchoPathDelayEstimator delay_estimator_;
  RenderDelayControllerMetrics metrics_;
  absl::optional<DelayEstimate> delay_samples_;
  size_t capture_call_counter_ = 0;
  int delay_change_counter_ = 0;
  DelayEstimate::Quality last_delay_estimate_quality_;
};

DelayEstimate ComputeBufferDelay(
    const absl::optional<DelayEstimate>& current_delay,
    int hysteresis_limit_blocks,
    DelayEstimate estimated_delay) {
  // Compute the buffer delay increase required to achieve the desired latency.
  size_t new_delay_blocks = estimated_delay.delay >> kBlockSizeLog2;
  // Add hysteresis.
  if (current_delay) {
    size_t current_delay_blocks = current_delay->delay;
    if (new_delay_blocks > current_delay_blocks &&
        new_delay_blocks <= current_delay_blocks + hysteresis_limit_blocks) {
      new_delay_blocks = current_delay_blocks;
    }
  }
  DelayEstimate new_delay = estimated_delay;
  new_delay.delay = new_delay_blocks;
  return new_delay;
}

std::atomic<int> RenderDelayControllerImpl::instance_count_(0);

RenderDelayControllerImpl::RenderDelayControllerImpl(
    const EchoCanceller3Config& config,
    int sample_rate_hz,
    size_t num_capture_channels)
    : data_dumper_(new ApmDataDumper(instance_count_.fetch_add(1) + 1)),
      hysteresis_limit_blocks_(
          static_cast<int>(config.delay.hysteresis_limit_blocks)),
      delay_estimator_(data_dumper_.get(), config, num_capture_channels),
      last_delay_estimate_quality_(DelayEstimate::Quality::kCoarse) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz));
  delay_estimator_.LogDelayEstimationProperties(sample_rate_hz, 0);
}

RenderDelayControllerImpl::~RenderDelayControllerImpl() = default;

void RenderDelayControllerImpl::Reset(bool reset_delay_confidence) {
  delay_ = absl::nullopt;
  delay_samples_ = absl::nullopt;
  delay_estimator_.Reset(reset_delay_confidence);
  delay_change_counter_ = 0;
  if (reset_delay_confidence) {
    last_delay_estimate_quality_ = DelayEstimate::Quality::kCoarse;
  }
}

void RenderDelayControllerImpl::LogRenderCall() {}

absl::optional<DelayEstimate> RenderDelayControllerImpl::GetDelay(
    const DownsampledRenderBuffer& render_buffer,
    size_t render_delay_buffer_delay,
    const Block& capture) {
  ++capture_call_counter_;

  auto delay_samples = delay_estimator_.EstimateDelay(render_buffer, capture);

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
    delay_ = ComputeBufferDelay(
        delay_, use_hysteresis ? hysteresis_limit_blocks_ : 0, *delay_samples_);
    last_delay_estimate_quality_ = delay_samples_->quality;
  }

  metrics_.Update(
      delay_samples_ ? absl::optional<size_t>(delay_samples_->delay)
                     : absl::nullopt,
      delay_ ? absl::optional<size_t>(delay_->delay) : absl::nullopt,
      delay_estimator_.Clockdrift());

  data_dumper_->DumpRaw("aec3_render_delay_controller_delay",
                        delay_samples ? delay_samples->delay : 0);
  data_dumper_->DumpRaw("aec3_render_delay_controller_buffer_delay",
                        delay_ ? delay_->delay : 0);

  return delay_;
}

bool RenderDelayControllerImpl::HasClockdrift() const {
  return delay_estimator_.Clockdrift() != ClockdriftDetector::Level::kNone;
}

}  // namespace

RenderDelayController* RenderDelayController::Create(
    const EchoCanceller3Config& config,
    int sample_rate_hz,
    size_t num_capture_channels) {
  return new RenderDelayControllerImpl(config, sample_rate_hz,
                                       num_capture_channels);
}

}  // namespace webrtc
