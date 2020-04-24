/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/residual_echo_detector.h"

#include <algorithm>
#include <numeric>

#include "absl/types/optional.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace {

float Power(rtc::ArrayView<const float> input) {
  if (input.empty()) {
    return 0.f;
  }
  return std::inner_product(input.begin(), input.end(), input.begin(), 0.f) /
         input.size();
}

constexpr size_t kLookbackFrames = 650;
// TODO(ivoc): Verify the size of this buffer.
constexpr size_t kRenderBufferSize = 30;
constexpr float kAlpha = 0.001f;
// 10 seconds of data, updated every 10 ms.
constexpr size_t kAggregationBufferSize = 10 * 100;

}  // namespace

namespace webrtc {

int ResidualEchoDetector::instance_count_ = 0;

ResidualEchoDetector::ResidualEchoDetector()
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      render_buffer_(kRenderBufferSize),
      render_power_(kLookbackFrames),
      render_power_mean_(kLookbackFrames),
      render_power_std_dev_(kLookbackFrames),
      covariances_(kLookbackFrames),
      recent_likelihood_max_(kAggregationBufferSize) {}

ResidualEchoDetector::~ResidualEchoDetector() = default;

void ResidualEchoDetector::AnalyzeRenderAudio(
    rtc::ArrayView<const float> render_audio) {
  // Dump debug data assuming 48 kHz sample rate (if this assumption is not
  // valid the dumped audio will need to be converted offline accordingly).
  data_dumper_->DumpWav("ed_render", render_audio.size(), render_audio.data(),
                        48000, 1);

  if (render_buffer_.Size() == 0) {
    frames_since_zero_buffer_size_ = 0;
  } else if (frames_since_zero_buffer_size_ >= kRenderBufferSize) {
    // This can happen in a few cases: at the start of a call, due to a glitch
    // or due to clock drift. The excess capture value will be ignored.
    // TODO(ivoc): Include how often this happens in APM stats.
    render_buffer_.Pop();
    frames_since_zero_buffer_size_ = 0;
  }
  ++frames_since_zero_buffer_size_;
  float power = Power(render_audio);
  render_buffer_.Push(power);
}

void ResidualEchoDetector::AnalyzeCaptureAudio(
    rtc::ArrayView<const float> capture_audio) {
  // Dump debug data assuming 48 kHz sample rate (if this assumption is not
  // valid the dumped audio will need to be converted offline accordingly).
  data_dumper_->DumpWav("ed_capture", capture_audio.size(),
                        capture_audio.data(), 48000, 1);

  if (first_process_call_) {
    // On the first process call (so the start of a call), we must flush the
    // render buffer, otherwise the render data will be delayed.
    render_buffer_.Clear();
    first_process_call_ = false;
  }

  // Get the next render value.
  const absl::optional<float> buffered_render_power = render_buffer_.Pop();
  if (!buffered_render_power) {
    // This can happen in a few cases: at the start of a call, due to a glitch
    // or due to clock drift. The excess capture value will be ignored.
    // TODO(ivoc): Include how often this happens in APM stats.
    return;
  }
  // Update the render statistics, and store the statistics in circular buffers.
  render_statistics_.Update(*buffered_render_power);
  RTC_DCHECK_LT(next_insertion_index_, kLookbackFrames);
  render_power_[next_insertion_index_] = *buffered_render_power;
  render_power_mean_[next_insertion_index_] = render_statistics_.mean();
  render_power_std_dev_[next_insertion_index_] =
      render_statistics_.std_deviation();

  // Get the next capture value, update capture statistics and add the relevant
  // values to the buffers.
  const float capture_power = Power(capture_audio);
  capture_statistics_.Update(capture_power);
  const float capture_mean = capture_statistics_.mean();
  const float capture_std_deviation = capture_statistics_.std_deviation();

  // Update the covariance values and determine the new echo likelihood.
  echo_likelihood_ = 0.f;
  size_t read_index = next_insertion_index_;

  int best_delay = -1;
  for (size_t delay = 0; delay < covariances_.size(); ++delay) {
    RTC_DCHECK_LT(read_index, render_power_.size());
    covariances_[delay].Update(capture_power, capture_mean,
                               capture_std_deviation, render_power_[read_index],
                               render_power_mean_[read_index],
                               render_power_std_dev_[read_index]);
    read_index = read_index > 0 ? read_index - 1 : kLookbackFrames - 1;

    if (covariances_[delay].normalized_cross_correlation() > echo_likelihood_) {
      echo_likelihood_ = covariances_[delay].normalized_cross_correlation();
      best_delay = static_cast<int>(delay);
    }
  }
  // This is a temporary log message to help find the underlying cause for echo
  // likelihoods > 1.0.
  // TODO(ivoc): Remove once the issue is resolved.
  if (echo_likelihood_ > 1.1f) {
    // Make sure we don't spam the log.
    if (log_counter_ < 5 && best_delay != -1) {
      size_t read_index = kLookbackFrames + next_insertion_index_ - best_delay;
      if (read_index >= kLookbackFrames) {
        read_index -= kLookbackFrames;
      }
      RTC_DCHECK_LT(read_index, render_power_.size());
      RTC_LOG_F(LS_ERROR) << "Echo detector internal state: {"
                             "Echo likelihood: "
                          << echo_likelihood_ << ", Best Delay: " << best_delay
                          << ", Covariance: "
                          << covariances_[best_delay].covariance()
                          << ", Last capture power: " << capture_power
                          << ", Capture mean: " << capture_mean
                          << ", Capture_standard deviation: "
                          << capture_std_deviation << ", Last render power: "
                          << render_power_[read_index]
                          << ", Render mean: " << render_power_mean_[read_index]
                          << ", Render standard deviation: "
                          << render_power_std_dev_[read_index]
                          << ", Reliability: " << reliability_ << "}";
      log_counter_++;
    }
  }
  RTC_DCHECK_LT(echo_likelihood_, 1.1f);

  reliability_ = (1.0f - kAlpha) * reliability_ + kAlpha * 1.0f;
  echo_likelihood_ *= reliability_;
  // This is a temporary fix to prevent echo likelihood values > 1.0.
  // TODO(ivoc): Find the root cause of this issue and fix it.
  echo_likelihood_ = std::min(echo_likelihood_, 1.0f);
  int echo_percentage = static_cast<int>(echo_likelihood_ * 100);
  RTC_HISTOGRAM_COUNTS("WebRTC.Audio.ResidualEchoDetector.EchoLikelihood",
                       echo_percentage, 0, 100, 100 /* number of bins */);

  // Update the buffer of recent likelihood values.
  recent_likelihood_max_.Update(echo_likelihood_);

  // Update the next insertion index.
  next_insertion_index_ = next_insertion_index_ < (kLookbackFrames - 1)
                              ? next_insertion_index_ + 1
                              : 0;
}

void ResidualEchoDetector::Initialize(int /*capture_sample_rate_hz*/,
                                      int /*num_capture_channels*/,
                                      int /*render_sample_rate_hz*/,
                                      int /*num_render_channels*/) {
  render_buffer_.Clear();
  std::fill(render_power_.begin(), render_power_.end(), 0.f);
  std::fill(render_power_mean_.begin(), render_power_mean_.end(), 0.f);
  std::fill(render_power_std_dev_.begin(), render_power_std_dev_.end(), 0.f);
  render_statistics_.Clear();
  capture_statistics_.Clear();
  recent_likelihood_max_.Clear();
  for (auto& cov : covariances_) {
    cov.Clear();
  }
  echo_likelihood_ = 0.f;
  next_insertion_index_ = 0;
  reliability_ = 0.f;
}

void EchoDetector::PackRenderAudioBuffer(AudioBuffer* audio,
                                         std::vector<float>* packed_buffer) {
  packed_buffer->clear();
  packed_buffer->insert(packed_buffer->end(), audio->channels_f()[0],
                        audio->channels_f()[0] + audio->num_frames());
}

EchoDetector::Metrics ResidualEchoDetector::GetMetrics() const {
  EchoDetector::Metrics metrics;
  metrics.echo_likelihood = echo_likelihood_;
  metrics.echo_likelihood_recent_max = recent_likelihood_max_.max();
  return metrics;
}
}  // namespace webrtc
