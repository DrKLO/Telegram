/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/render_delay_buffer.h"

#include <string.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <numeric>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/aec3_fft.h"
#include "modules/audio_processing/aec3/alignment_mixer.h"
#include "modules/audio_processing/aec3/block_buffer.h"
#include "modules/audio_processing/aec3/decimator.h"
#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/aec3/fft_buffer.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/aec3/render_buffer.h"
#include "modules/audio_processing/aec3/spectrum_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

bool UpdateCaptureCallCounterOnSkippedBlocks() {
  return !field_trial::IsEnabled(
      "WebRTC-Aec3RenderBufferCallCounterUpdateKillSwitch");
}

class RenderDelayBufferImpl final : public RenderDelayBuffer {
 public:
  RenderDelayBufferImpl(const EchoCanceller3Config& config,
                        int sample_rate_hz,
                        size_t num_render_channels);
  RenderDelayBufferImpl() = delete;
  ~RenderDelayBufferImpl() override;

  void Reset() override;
  BufferingEvent Insert(
      const std::vector<std::vector<std::vector<float>>>& block) override;
  BufferingEvent PrepareCaptureProcessing() override;
  void HandleSkippedCaptureProcessing() override;
  bool AlignFromDelay(size_t delay) override;
  void AlignFromExternalDelay() override;
  size_t Delay() const override { return ComputeDelay(); }
  size_t MaxDelay() const override {
    return blocks_.buffer.size() - 1 - buffer_headroom_;
  }
  RenderBuffer* GetRenderBuffer() override { return &echo_remover_buffer_; }

  const DownsampledRenderBuffer& GetDownsampledRenderBuffer() const override {
    return low_rate_;
  }

  int BufferLatency() const;
  void SetAudioBufferDelay(int delay_ms) override;
  bool HasReceivedBufferDelay() override;

 private:
  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const Aec3Optimization optimization_;
  const EchoCanceller3Config config_;
  const bool update_capture_call_counter_on_skipped_blocks_;
  const float render_linear_amplitude_gain_;
  const rtc::LoggingSeverity delay_log_level_;
  size_t down_sampling_factor_;
  const int sub_block_size_;
  BlockBuffer blocks_;
  SpectrumBuffer spectra_;
  FftBuffer ffts_;
  absl::optional<size_t> delay_;
  RenderBuffer echo_remover_buffer_;
  DownsampledRenderBuffer low_rate_;
  AlignmentMixer render_mixer_;
  Decimator render_decimator_;
  const Aec3Fft fft_;
  std::vector<float> render_ds_;
  const int buffer_headroom_;
  bool last_call_was_render_ = false;
  int num_api_calls_in_a_row_ = 0;
  int max_observed_jitter_ = 1;
  int64_t capture_call_counter_ = 0;
  int64_t render_call_counter_ = 0;
  bool render_activity_ = false;
  size_t render_activity_counter_ = 0;
  absl::optional<int> external_audio_buffer_delay_;
  bool external_audio_buffer_delay_verified_after_reset_ = false;
  size_t min_latency_blocks_ = 0;
  size_t excess_render_detection_counter_ = 0;

  int MapDelayToTotalDelay(size_t delay) const;
  int ComputeDelay() const;
  void ApplyTotalDelay(int delay);
  void InsertBlock(const std::vector<std::vector<std::vector<float>>>& block,
                   int previous_write);
  bool DetectActiveRender(rtc::ArrayView<const float> x) const;
  bool DetectExcessRenderBlocks();
  void IncrementWriteIndices();
  void IncrementLowRateReadIndices();
  void IncrementReadIndices();
  bool RenderOverrun();
  bool RenderUnderrun();
};

int RenderDelayBufferImpl::instance_count_ = 0;

RenderDelayBufferImpl::RenderDelayBufferImpl(const EchoCanceller3Config& config,
                                             int sample_rate_hz,
                                             size_t num_render_channels)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      optimization_(DetectOptimization()),
      config_(config),
      update_capture_call_counter_on_skipped_blocks_(
          UpdateCaptureCallCounterOnSkippedBlocks()),
      render_linear_amplitude_gain_(
          std::pow(10.0f, config_.render_levels.render_power_gain_db / 20.f)),
      delay_log_level_(config_.delay.log_warning_on_delay_changes
                           ? rtc::LS_WARNING
                           : rtc::LS_VERBOSE),
      down_sampling_factor_(config.delay.down_sampling_factor),
      sub_block_size_(static_cast<int>(down_sampling_factor_ > 0
                                           ? kBlockSize / down_sampling_factor_
                                           : kBlockSize)),
      blocks_(GetRenderDelayBufferSize(down_sampling_factor_,
                                       config.delay.num_filters,
                                       config.filter.refined.length_blocks),
              NumBandsForRate(sample_rate_hz),
              num_render_channels,
              kBlockSize),
      spectra_(blocks_.buffer.size(), num_render_channels),
      ffts_(blocks_.buffer.size(), num_render_channels),
      delay_(config_.delay.default_delay),
      echo_remover_buffer_(&blocks_, &spectra_, &ffts_),
      low_rate_(GetDownSampledBufferSize(down_sampling_factor_,
                                         config.delay.num_filters)),
      render_mixer_(num_render_channels, config.delay.render_alignment_mixing),
      render_decimator_(down_sampling_factor_),
      fft_(),
      render_ds_(sub_block_size_, 0.f),
      buffer_headroom_(config.filter.refined.length_blocks) {
  RTC_DCHECK_EQ(blocks_.buffer.size(), ffts_.buffer.size());
  RTC_DCHECK_EQ(spectra_.buffer.size(), ffts_.buffer.size());
  for (size_t i = 0; i < blocks_.buffer.size(); ++i) {
    RTC_DCHECK_EQ(blocks_.buffer[i][0].size(), ffts_.buffer[i].size());
    RTC_DCHECK_EQ(spectra_.buffer[i].size(), ffts_.buffer[i].size());
  }

  Reset();
}

RenderDelayBufferImpl::~RenderDelayBufferImpl() = default;

// Resets the buffer delays and clears the reported delays.
void RenderDelayBufferImpl::Reset() {
  last_call_was_render_ = false;
  num_api_calls_in_a_row_ = 1;
  min_latency_blocks_ = 0;
  excess_render_detection_counter_ = 0;

  // Initialize the read index to one sub-block before the write index.
  low_rate_.read = low_rate_.OffsetIndex(low_rate_.write, sub_block_size_);

  // Check for any external audio buffer delay and whether it is feasible.
  if (external_audio_buffer_delay_) {
    const int headroom = 2;
    size_t audio_buffer_delay_to_set;
    // Minimum delay is 1 (like the low-rate render buffer).
    if (*external_audio_buffer_delay_ <= headroom) {
      audio_buffer_delay_to_set = 1;
    } else {
      audio_buffer_delay_to_set = *external_audio_buffer_delay_ - headroom;
    }

    audio_buffer_delay_to_set = std::min(audio_buffer_delay_to_set, MaxDelay());

    // When an external delay estimate is available, use that delay as the
    // initial render buffer delay.
    ApplyTotalDelay(audio_buffer_delay_to_set);
    delay_ = ComputeDelay();

    external_audio_buffer_delay_verified_after_reset_ = false;
  } else {
    // If an external delay estimate is not available, use that delay as the
    // initial delay. Set the render buffer delays to the default delay.
    ApplyTotalDelay(config_.delay.default_delay);

    // Unset the delays which are set by AlignFromDelay.
    delay_ = absl::nullopt;
  }
}

// Inserts a new block into the render buffers.
RenderDelayBuffer::BufferingEvent RenderDelayBufferImpl::Insert(
    const std::vector<std::vector<std::vector<float>>>& block) {
  ++render_call_counter_;
  if (delay_) {
    if (!last_call_was_render_) {
      last_call_was_render_ = true;
      num_api_calls_in_a_row_ = 1;
    } else {
      if (++num_api_calls_in_a_row_ > max_observed_jitter_) {
        max_observed_jitter_ = num_api_calls_in_a_row_;
        RTC_LOG_V(delay_log_level_)
            << "New max number api jitter observed at render block "
            << render_call_counter_ << ":  " << num_api_calls_in_a_row_
            << " blocks";
      }
    }
  }

  // Increase the write indices to where the new blocks should be written.
  const int previous_write = blocks_.write;
  IncrementWriteIndices();

  // Allow overrun and do a reset when render overrun occurrs due to more render
  // data being inserted than capture data is received.
  BufferingEvent event =
      RenderOverrun() ? BufferingEvent::kRenderOverrun : BufferingEvent::kNone;

  // Detect and update render activity.
  if (!render_activity_) {
    render_activity_counter_ += DetectActiveRender(block[0][0]) ? 1 : 0;
    render_activity_ = render_activity_counter_ >= 20;
  }

  // Insert the new render block into the specified position.
  InsertBlock(block, previous_write);

  if (event != BufferingEvent::kNone) {
    Reset();
  }

  return event;
}

void RenderDelayBufferImpl::HandleSkippedCaptureProcessing() {
  if (update_capture_call_counter_on_skipped_blocks_) {
    ++capture_call_counter_;
  }
}

// Prepares the render buffers for processing another capture block.
RenderDelayBuffer::BufferingEvent
RenderDelayBufferImpl::PrepareCaptureProcessing() {
  RenderDelayBuffer::BufferingEvent event = BufferingEvent::kNone;
  ++capture_call_counter_;

  if (delay_) {
    if (last_call_was_render_) {
      last_call_was_render_ = false;
      num_api_calls_in_a_row_ = 1;
    } else {
      if (++num_api_calls_in_a_row_ > max_observed_jitter_) {
        max_observed_jitter_ = num_api_calls_in_a_row_;
        RTC_LOG_V(delay_log_level_)
            << "New max number api jitter observed at capture block "
            << capture_call_counter_ << ":  " << num_api_calls_in_a_row_
            << " blocks";
      }
    }
  }

  if (DetectExcessRenderBlocks()) {
    // Too many render blocks compared to capture blocks. Risk of delay ending
    // up before the filter used by the delay estimator.
    RTC_LOG_V(delay_log_level_)
        << "Excess render blocks detected at block " << capture_call_counter_;
    Reset();
    event = BufferingEvent::kRenderOverrun;
  } else if (RenderUnderrun()) {
    // Don't increment the read indices of the low rate buffer if there is a
    // render underrun.
    RTC_LOG_V(delay_log_level_)
        << "Render buffer underrun detected at block " << capture_call_counter_;
    IncrementReadIndices();
    // Incrementing the buffer index without increasing the low rate buffer
    // index means that the delay is reduced by one.
    if (delay_ && *delay_ > 0)
      delay_ = *delay_ - 1;
    event = BufferingEvent::kRenderUnderrun;
  } else {
    // Increment the read indices in the render buffers to point to the most
    // recent block to use in the capture processing.
    IncrementLowRateReadIndices();
    IncrementReadIndices();
  }

  echo_remover_buffer_.SetRenderActivity(render_activity_);
  if (render_activity_) {
    render_activity_counter_ = 0;
    render_activity_ = false;
  }

  return event;
}

// Sets the delay and returns a bool indicating whether the delay was changed.
bool RenderDelayBufferImpl::AlignFromDelay(size_t delay) {
  RTC_DCHECK(!config_.delay.use_external_delay_estimator);
  if (!external_audio_buffer_delay_verified_after_reset_ &&
      external_audio_buffer_delay_ && delay_) {
    int difference = static_cast<int>(delay) - static_cast<int>(*delay_);
    RTC_LOG_V(delay_log_level_)
        << "Mismatch between first estimated delay after reset "
           "and externally reported audio buffer delay: "
        << difference << " blocks";
    external_audio_buffer_delay_verified_after_reset_ = true;
  }
  if (delay_ && *delay_ == delay) {
    return false;
  }
  delay_ = delay;

  // Compute the total delay and limit the delay to the allowed range.
  int total_delay = MapDelayToTotalDelay(*delay_);
  total_delay =
      std::min(MaxDelay(), static_cast<size_t>(std::max(total_delay, 0)));

  // Apply the delay to the buffers.
  ApplyTotalDelay(total_delay);
  return true;
}

void RenderDelayBufferImpl::SetAudioBufferDelay(int delay_ms) {
  if (!external_audio_buffer_delay_) {
    RTC_LOG_V(delay_log_level_)
        << "Receiving a first externally reported audio buffer delay of "
        << delay_ms << " ms.";
  }

  // Convert delay from milliseconds to blocks (rounded down).
  external_audio_buffer_delay_ = delay_ms / 4;
}

bool RenderDelayBufferImpl::HasReceivedBufferDelay() {
  return external_audio_buffer_delay_.has_value();
}

// Maps the externally computed delay to the delay used internally.
int RenderDelayBufferImpl::MapDelayToTotalDelay(
    size_t external_delay_blocks) const {
  const int latency_blocks = BufferLatency();
  return latency_blocks + static_cast<int>(external_delay_blocks);
}

// Returns the delay (not including call jitter).
int RenderDelayBufferImpl::ComputeDelay() const {
  const int latency_blocks = BufferLatency();
  int internal_delay = spectra_.read >= spectra_.write
                           ? spectra_.read - spectra_.write
                           : spectra_.size + spectra_.read - spectra_.write;

  return internal_delay - latency_blocks;
}

// Set the read indices according to the delay.
void RenderDelayBufferImpl::ApplyTotalDelay(int delay) {
  RTC_LOG_V(delay_log_level_)
      << "Applying total delay of " << delay << " blocks.";
  blocks_.read = blocks_.OffsetIndex(blocks_.write, -delay);
  spectra_.read = spectra_.OffsetIndex(spectra_.write, delay);
  ffts_.read = ffts_.OffsetIndex(ffts_.write, delay);
}

void RenderDelayBufferImpl::AlignFromExternalDelay() {
  RTC_DCHECK(config_.delay.use_external_delay_estimator);
  if (external_audio_buffer_delay_) {
    const int64_t delay = render_call_counter_ - capture_call_counter_ +
                          *external_audio_buffer_delay_;
    const int64_t delay_with_headroom =
        delay - config_.delay.delay_headroom_samples / kBlockSize;
    ApplyTotalDelay(delay_with_headroom);
  }
}

// Inserts a block into the render buffers.
void RenderDelayBufferImpl::InsertBlock(
    const std::vector<std::vector<std::vector<float>>>& block,
    int previous_write) {
  auto& b = blocks_;
  auto& lr = low_rate_;
  auto& ds = render_ds_;
  auto& f = ffts_;
  auto& s = spectra_;
  const size_t num_bands = b.buffer[b.write].size();
  const size_t num_render_channels = b.buffer[b.write][0].size();
  RTC_DCHECK_EQ(block.size(), b.buffer[b.write].size());
  for (size_t band = 0; band < num_bands; ++band) {
    RTC_DCHECK_EQ(block[band].size(), num_render_channels);
    RTC_DCHECK_EQ(b.buffer[b.write][band].size(), num_render_channels);
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      RTC_DCHECK_EQ(block[band][ch].size(), b.buffer[b.write][band][ch].size());
      std::copy(block[band][ch].begin(), block[band][ch].end(),
                b.buffer[b.write][band][ch].begin());
    }
  }

  if (render_linear_amplitude_gain_ != 1.f) {
    for (size_t band = 0; band < num_bands; ++band) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        for (size_t k = 0; k < 64; ++k) {
          b.buffer[b.write][band][ch][k] *= render_linear_amplitude_gain_;
        }
      }
    }
  }

  std::array<float, kBlockSize> downmixed_render;
  render_mixer_.ProduceOutput(b.buffer[b.write][0], downmixed_render);
  render_decimator_.Decimate(downmixed_render, ds);
  data_dumper_->DumpWav("aec3_render_decimator_output", ds.size(), ds.data(),
                        16000 / down_sampling_factor_, 1);
  std::copy(ds.rbegin(), ds.rend(), lr.buffer.begin() + lr.write);
  for (size_t channel = 0; channel < b.buffer[b.write][0].size(); ++channel) {
    fft_.PaddedFft(b.buffer[b.write][0][channel],
                   b.buffer[previous_write][0][channel],
                   &f.buffer[f.write][channel]);
    f.buffer[f.write][channel].Spectrum(optimization_,
                                        s.buffer[s.write][channel]);
  }
}

bool RenderDelayBufferImpl::DetectActiveRender(
    rtc::ArrayView<const float> x) const {
  const float x_energy = std::inner_product(x.begin(), x.end(), x.begin(), 0.f);
  return x_energy > (config_.render_levels.active_render_limit *
                     config_.render_levels.active_render_limit) *
                        kFftLengthBy2;
}

bool RenderDelayBufferImpl::DetectExcessRenderBlocks() {
  bool excess_render_detected = false;
  const size_t latency_blocks = static_cast<size_t>(BufferLatency());
  // The recently seen minimum latency in blocks. Should be close to 0.
  min_latency_blocks_ = std::min(min_latency_blocks_, latency_blocks);
  // After processing a configurable number of blocks the minimum latency is
  // checked.
  if (++excess_render_detection_counter_ >=
      config_.buffering.excess_render_detection_interval_blocks) {
    // If the minimum latency is not lower than the threshold there have been
    // more render than capture frames.
    excess_render_detected = min_latency_blocks_ >
                             config_.buffering.max_allowed_excess_render_blocks;
    // Reset the counter and let the minimum latency be the current latency.
    min_latency_blocks_ = latency_blocks;
    excess_render_detection_counter_ = 0;
  }

  data_dumper_->DumpRaw("aec3_latency_blocks", latency_blocks);
  data_dumper_->DumpRaw("aec3_min_latency_blocks", min_latency_blocks_);
  data_dumper_->DumpRaw("aec3_excess_render_detected", excess_render_detected);
  return excess_render_detected;
}

// Computes the latency in the buffer (the number of unread sub-blocks).
int RenderDelayBufferImpl::BufferLatency() const {
  const DownsampledRenderBuffer& l = low_rate_;
  int latency_samples = (l.buffer.size() + l.read - l.write) % l.buffer.size();
  int latency_blocks = latency_samples / sub_block_size_;
  return latency_blocks;
}

// Increments the write indices for the render buffers.
void RenderDelayBufferImpl::IncrementWriteIndices() {
  low_rate_.UpdateWriteIndex(-sub_block_size_);
  blocks_.IncWriteIndex();
  spectra_.DecWriteIndex();
  ffts_.DecWriteIndex();
}

// Increments the read indices of the low rate render buffers.
void RenderDelayBufferImpl::IncrementLowRateReadIndices() {
  low_rate_.UpdateReadIndex(-sub_block_size_);
}

// Increments the read indices for the render buffers.
void RenderDelayBufferImpl::IncrementReadIndices() {
  if (blocks_.read != blocks_.write) {
    blocks_.IncReadIndex();
    spectra_.DecReadIndex();
    ffts_.DecReadIndex();
  }
}

// Checks for a render buffer overrun.
bool RenderDelayBufferImpl::RenderOverrun() {
  return low_rate_.read == low_rate_.write || blocks_.read == blocks_.write;
}

// Checks for a render buffer underrun.
bool RenderDelayBufferImpl::RenderUnderrun() {
  return low_rate_.read == low_rate_.write;
}

}  // namespace

RenderDelayBuffer* RenderDelayBuffer::Create(const EchoCanceller3Config& config,
                                             int sample_rate_hz,
                                             size_t num_render_channels) {
  return new RenderDelayBufferImpl(config, sample_rate_hz, num_render_channels);
}

}  // namespace webrtc
