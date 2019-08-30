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
#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio/echo_canceller3_config.h"
#include "api/audio/echo_control.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/block_processor.h"
#include "modules/audio_processing/aec3/block_processor_metrics.h"
#include "modules/audio_processing/aec3/delay_estimate.h"
#include "modules/audio_processing/aec3/echo_path_variability.h"
#include "modules/audio_processing/aec3/echo_remover.h"
#include "modules/audio_processing/aec3/render_delay_buffer.h"
#include "modules/audio_processing/aec3/render_delay_controller.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

enum class BlockProcessorApiCall { kCapture, kRender };

class BlockProcessorImpl2 final : public BlockProcessor {
 public:
  BlockProcessorImpl2(const EchoCanceller3Config& config,
                      int sample_rate_hz,
                      std::unique_ptr<RenderDelayBuffer> render_buffer,
                      std::unique_ptr<RenderDelayController> delay_controller,
                      std::unique_ptr<EchoRemover> echo_remover);

  BlockProcessorImpl2() = delete;

  ~BlockProcessorImpl2() override;

  void ProcessCapture(bool echo_path_gain_change,
                      bool capture_signal_saturation,
                      std::vector<std::vector<float>>* capture_block) override;

  void BufferRender(const std::vector<std::vector<float>>& block) override;

  void UpdateEchoLeakageStatus(bool leakage_detected) override;

  void GetMetrics(EchoControl::Metrics* metrics) const override;

  void SetAudioBufferDelay(size_t delay_ms) override;

 private:
  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const EchoCanceller3Config config_;
  bool capture_properly_started_ = false;
  bool render_properly_started_ = false;
  const size_t sample_rate_hz_;
  std::unique_ptr<RenderDelayBuffer> render_buffer_;
  std::unique_ptr<RenderDelayController> delay_controller_;
  std::unique_ptr<EchoRemover> echo_remover_;
  BlockProcessorMetrics metrics_;
  RenderDelayBuffer::BufferingEvent render_event_;
  size_t capture_call_counter_ = 0;
  absl::optional<DelayEstimate> estimated_delay_;
  absl::optional<int> echo_remover_delay_;
};

int BlockProcessorImpl2::instance_count_ = 0;

BlockProcessorImpl2::BlockProcessorImpl2(
    const EchoCanceller3Config& config,
    int sample_rate_hz,
    std::unique_ptr<RenderDelayBuffer> render_buffer,
    std::unique_ptr<RenderDelayController> delay_controller,
    std::unique_ptr<EchoRemover> echo_remover)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      config_(config),
      sample_rate_hz_(sample_rate_hz),
      render_buffer_(std::move(render_buffer)),
      delay_controller_(std::move(delay_controller)),
      echo_remover_(std::move(echo_remover)),
      render_event_(RenderDelayBuffer::BufferingEvent::kNone) {
  RTC_DCHECK(ValidFullBandRate(sample_rate_hz_));
}

BlockProcessorImpl2::~BlockProcessorImpl2() = default;

void BlockProcessorImpl2::ProcessCapture(
    bool echo_path_gain_change,
    bool capture_signal_saturation,
    std::vector<std::vector<float>>* capture_block) {
  RTC_DCHECK(capture_block);
  RTC_DCHECK_EQ(NumBandsForRate(sample_rate_hz_), capture_block->size());
  RTC_DCHECK_EQ(kBlockSize, (*capture_block)[0].size());

  capture_call_counter_++;

  data_dumper_->DumpRaw("aec3_processblock_call_order",
                        static_cast<int>(BlockProcessorApiCall::kCapture));
  data_dumper_->DumpWav("aec3_processblock_capture_input", kBlockSize,
                        &(*capture_block)[0][0],
                        LowestBandRate(sample_rate_hz_), 1);

  if (render_properly_started_) {
    if (!capture_properly_started_) {
      capture_properly_started_ = true;
      render_buffer_->Reset();
      delay_controller_->Reset(true);
    }
  } else {
    // If no render data has yet arrived, do not process the capture signal.
    return;
  }

  EchoPathVariability echo_path_variability(
      echo_path_gain_change, EchoPathVariability::DelayAdjustment::kNone,
      false);

  if (render_event_ == RenderDelayBuffer::BufferingEvent::kRenderOverrun &&
      render_properly_started_) {
    echo_path_variability.delay_change =
        EchoPathVariability::DelayAdjustment::kBufferFlush;
    delay_controller_->Reset(true);
    RTC_LOG(LS_WARNING) << "Reset due to render buffer overrun at block  "
                        << capture_call_counter_;
  }
  render_event_ = RenderDelayBuffer::BufferingEvent::kNone;

  // Update the render buffers with any newly arrived render blocks and prepare
  // the render buffers for reading the render data corresponding to the current
  // capture block.
  RenderDelayBuffer::BufferingEvent buffer_event =
      render_buffer_->PrepareCaptureProcessing();
  // Reset the delay controller at render buffer underrun.
  if (buffer_event == RenderDelayBuffer::BufferingEvent::kRenderUnderrun) {
    delay_controller_->Reset(false);
  }

  data_dumper_->DumpWav("aec3_processblock_capture_input2", kBlockSize,
                        &(*capture_block)[0][0],
                        LowestBandRate(sample_rate_hz_), 1);

  // Compute and and apply the render delay required to achieve proper signal
  // alignment.
  estimated_delay_ = delay_controller_->GetDelay(
      render_buffer_->GetDownsampledRenderBuffer(), render_buffer_->Delay(),
      echo_remover_delay_, (*capture_block)[0]);

  if (estimated_delay_) {
    bool delay_change = render_buffer_->SetDelay(estimated_delay_->delay);
    if (delay_change) {
      RTC_LOG(LS_WARNING) << "Delay changed to " << estimated_delay_->delay
                          << " at block " << capture_call_counter_;
      echo_path_variability.delay_change =
          EchoPathVariability::DelayAdjustment::kNewDetectedDelay;
    }
  }

  // Remove the echo from the capture signal.
  echo_remover_->ProcessCapture(
      echo_path_variability, capture_signal_saturation, estimated_delay_,
      render_buffer_->GetRenderBuffer(), capture_block);

  // Check to see if a refined delay estimate has been obtained from the echo
  // remover.
  echo_remover_delay_ = echo_remover_->Delay();

  // Update the metrics.
  metrics_.UpdateCapture(false);
}

void BlockProcessorImpl2::BufferRender(
    const std::vector<std::vector<float>>& block) {
  RTC_DCHECK_EQ(NumBandsForRate(sample_rate_hz_), block.size());
  RTC_DCHECK_EQ(kBlockSize, block[0].size());
  data_dumper_->DumpRaw("aec3_processblock_call_order",
                        static_cast<int>(BlockProcessorApiCall::kRender));
  data_dumper_->DumpWav("aec3_processblock_render_input", kBlockSize,
                        &block[0][0], LowestBandRate(sample_rate_hz_), 1);
  data_dumper_->DumpWav("aec3_processblock_render_input2", kBlockSize,
                        &block[0][0], LowestBandRate(sample_rate_hz_), 1);

  render_event_ = render_buffer_->Insert(block);

  metrics_.UpdateRender(render_event_ !=
                        RenderDelayBuffer::BufferingEvent::kNone);

  render_properly_started_ = true;
  delay_controller_->LogRenderCall();
}

void BlockProcessorImpl2::UpdateEchoLeakageStatus(bool leakage_detected) {
  echo_remover_->UpdateEchoLeakageStatus(leakage_detected);
}

void BlockProcessorImpl2::GetMetrics(EchoControl::Metrics* metrics) const {
  echo_remover_->GetMetrics(metrics);
  const int block_size_ms = sample_rate_hz_ == 8000 ? 8 : 4;
  absl::optional<size_t> delay = render_buffer_->Delay();
  metrics->delay_ms = delay ? static_cast<int>(*delay) * block_size_ms : 0;
}

void BlockProcessorImpl2::SetAudioBufferDelay(size_t delay_ms) {
  render_buffer_->SetAudioBufferDelay(delay_ms);
}

}  // namespace

BlockProcessor* BlockProcessor::Create2(const EchoCanceller3Config& config,
                                        int sample_rate_hz) {
  std::unique_ptr<RenderDelayBuffer> render_buffer(
      RenderDelayBuffer::Create2(config, NumBandsForRate(sample_rate_hz)));
  std::unique_ptr<RenderDelayController> delay_controller(
      RenderDelayController::Create2(config, sample_rate_hz));
  std::unique_ptr<EchoRemover> echo_remover(
      EchoRemover::Create(config, sample_rate_hz));
  return Create2(config, sample_rate_hz, std::move(render_buffer),
                 std::move(delay_controller), std::move(echo_remover));
}

BlockProcessor* BlockProcessor::Create2(
    const EchoCanceller3Config& config,
    int sample_rate_hz,
    std::unique_ptr<RenderDelayBuffer> render_buffer) {
  std::unique_ptr<RenderDelayController> delay_controller(
      RenderDelayController::Create2(config, sample_rate_hz));
  std::unique_ptr<EchoRemover> echo_remover(
      EchoRemover::Create(config, sample_rate_hz));
  return Create2(config, sample_rate_hz, std::move(render_buffer),
                 std::move(delay_controller), std::move(echo_remover));
}

BlockProcessor* BlockProcessor::Create2(
    const EchoCanceller3Config& config,
    int sample_rate_hz,
    std::unique_ptr<RenderDelayBuffer> render_buffer,
    std::unique_ptr<RenderDelayController> delay_controller,
    std::unique_ptr<EchoRemover> echo_remover) {
  return new BlockProcessorImpl2(
      config, sample_rate_hz, std::move(render_buffer),
      std::move(delay_controller), std::move(echo_remover));
}

}  // namespace webrtc
