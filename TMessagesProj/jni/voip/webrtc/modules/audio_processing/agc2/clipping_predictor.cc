/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/clipping_predictor.h"

#include <algorithm>
#include <memory>

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc2/clipping_predictor_level_buffer.h"
#include "modules/audio_processing/agc2/gain_map_internal.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

constexpr int kClippingPredictorMaxGainChange = 15;

// Estimates the new level from the gain error; a copy of the function
// `LevelFromGainError` in agc_manager_direct.cc.
int LevelFromGainError(int gain_error,
                       int level,
                       int min_mic_level,
                       int max_mic_level) {
  RTC_DCHECK_GE(level, 0);
  RTC_DCHECK_LE(level, max_mic_level);
  if (gain_error == 0) {
    return level;
  }
  int new_level = level;
  if (gain_error > 0) {
    while (kGainMap[new_level] - kGainMap[level] < gain_error &&
           new_level < max_mic_level) {
      ++new_level;
    }
  } else {
    while (kGainMap[new_level] - kGainMap[level] > gain_error &&
           new_level > min_mic_level) {
      --new_level;
    }
  }
  return new_level;
}

float ComputeCrestFactor(const ClippingPredictorLevelBuffer::Level& level) {
  const float crest_factor =
      FloatS16ToDbfs(level.max) - FloatS16ToDbfs(std::sqrt(level.average));
  return crest_factor;
}

// Crest factor-based clipping prediction and clipped level step estimation.
class ClippingEventPredictor : public ClippingPredictor {
 public:
  // ClippingEventPredictor with `num_channels` channels (limited to values
  // higher than zero); window size `window_length` and reference window size
  // `reference_window_length` (both referring to the number of frames in the
  // respective sliding windows and limited to values higher than zero);
  // reference window delay `reference_window_delay` (delay in frames, limited
  // to values zero and higher with an additional requirement of
  // `window_length` < `reference_window_length` + reference_window_delay`);
  // and an estimation peak threshold `clipping_threshold` and a crest factor
  // drop threshold `crest_factor_margin` (both in dB).
  ClippingEventPredictor(int num_channels,
                         int window_length,
                         int reference_window_length,
                         int reference_window_delay,
                         float clipping_threshold,
                         float crest_factor_margin)
      : window_length_(window_length),
        reference_window_length_(reference_window_length),
        reference_window_delay_(reference_window_delay),
        clipping_threshold_(clipping_threshold),
        crest_factor_margin_(crest_factor_margin) {
    RTC_DCHECK_GT(num_channels, 0);
    RTC_DCHECK_GT(window_length, 0);
    RTC_DCHECK_GT(reference_window_length, 0);
    RTC_DCHECK_GE(reference_window_delay, 0);
    RTC_DCHECK_GT(reference_window_length + reference_window_delay,
                  window_length);
    const int buffer_length = GetMinFramesProcessed();
    RTC_DCHECK_GT(buffer_length, 0);
    for (int i = 0; i < num_channels; ++i) {
      ch_buffers_.push_back(
          std::make_unique<ClippingPredictorLevelBuffer>(buffer_length));
    }
  }

  ClippingEventPredictor(const ClippingEventPredictor&) = delete;
  ClippingEventPredictor& operator=(const ClippingEventPredictor&) = delete;
  ~ClippingEventPredictor() {}

  void Reset() {
    const int num_channels = ch_buffers_.size();
    for (int i = 0; i < num_channels; ++i) {
      ch_buffers_[i]->Reset();
    }
  }

  // Analyzes a frame of audio and stores the framewise metrics in
  // `ch_buffers_`.
  void Analyze(const AudioFrameView<const float>& frame) {
    const int num_channels = frame.num_channels();
    RTC_DCHECK_EQ(num_channels, ch_buffers_.size());
    const int samples_per_channel = frame.samples_per_channel();
    RTC_DCHECK_GT(samples_per_channel, 0);
    for (int channel = 0; channel < num_channels; ++channel) {
      float sum_squares = 0.0f;
      float peak = 0.0f;
      for (const auto& sample : frame.channel(channel)) {
        sum_squares += sample * sample;
        peak = std::max(std::fabs(sample), peak);
      }
      ch_buffers_[channel]->Push(
          {sum_squares / static_cast<float>(samples_per_channel), peak});
    }
  }

  // Estimates the analog gain adjustment for channel `channel` using a
  // sliding window over the frame-wise metrics in `ch_buffers_`. Returns an
  // estimate for the clipped level step equal to `default_clipped_level_step_`
  // if at least `GetMinFramesProcessed()` frames have been processed since the
  // last reset and a clipping event is predicted. `level`, `min_mic_level`, and
  // `max_mic_level` are limited to [0, 255] and `default_step` to [1, 255].
  absl::optional<int> EstimateClippedLevelStep(int channel,
                                               int level,
                                               int default_step,
                                               int min_mic_level,
                                               int max_mic_level) const {
    RTC_CHECK_GE(channel, 0);
    RTC_CHECK_LT(channel, ch_buffers_.size());
    RTC_DCHECK_GE(level, 0);
    RTC_DCHECK_LE(level, 255);
    RTC_DCHECK_GT(default_step, 0);
    RTC_DCHECK_LE(default_step, 255);
    RTC_DCHECK_GE(min_mic_level, 0);
    RTC_DCHECK_LE(min_mic_level, 255);
    RTC_DCHECK_GE(max_mic_level, 0);
    RTC_DCHECK_LE(max_mic_level, 255);
    if (level <= min_mic_level) {
      return absl::nullopt;
    }
    if (PredictClippingEvent(channel)) {
      const int new_level =
          rtc::SafeClamp(level - default_step, min_mic_level, max_mic_level);
      const int step = level - new_level;
      if (step > 0) {
        return step;
      }
    }
    return absl::nullopt;
  }

 private:
  int GetMinFramesProcessed() const {
    return reference_window_delay_ + reference_window_length_;
  }

  // Predicts clipping events based on the processed audio frames. Returns
  // true if a clipping event is likely.
  bool PredictClippingEvent(int channel) const {
    const auto metrics =
        ch_buffers_[channel]->ComputePartialMetrics(0, window_length_);
    if (!metrics.has_value() ||
        !(FloatS16ToDbfs(metrics.value().max) > clipping_threshold_)) {
      return false;
    }
    const auto reference_metrics = ch_buffers_[channel]->ComputePartialMetrics(
        reference_window_delay_, reference_window_length_);
    if (!reference_metrics.has_value()) {
      return false;
    }
    const float crest_factor = ComputeCrestFactor(metrics.value());
    const float reference_crest_factor =
        ComputeCrestFactor(reference_metrics.value());
    if (crest_factor < reference_crest_factor - crest_factor_margin_) {
      return true;
    }
    return false;
  }

  std::vector<std::unique_ptr<ClippingPredictorLevelBuffer>> ch_buffers_;
  const int window_length_;
  const int reference_window_length_;
  const int reference_window_delay_;
  const float clipping_threshold_;
  const float crest_factor_margin_;
};

// Performs crest factor-based clipping peak prediction.
class ClippingPeakPredictor : public ClippingPredictor {
 public:
  // Ctor. ClippingPeakPredictor with `num_channels` channels (limited to values
  // higher than zero); window size `window_length` and reference window size
  // `reference_window_length` (both referring to the number of frames in the
  // respective sliding windows and limited to values higher than zero);
  // reference window delay `reference_window_delay` (delay in frames, limited
  // to values zero and higher with an additional requirement of
  // `window_length` < `reference_window_length` + reference_window_delay`);
  // and a clipping prediction threshold `clipping_threshold` (in dB). Adaptive
  // clipped level step estimation is used if `adaptive_step_estimation` is
  // true.
  explicit ClippingPeakPredictor(int num_channels,
                                 int window_length,
                                 int reference_window_length,
                                 int reference_window_delay,
                                 int clipping_threshold,
                                 bool adaptive_step_estimation)
      : window_length_(window_length),
        reference_window_length_(reference_window_length),
        reference_window_delay_(reference_window_delay),
        clipping_threshold_(clipping_threshold),
        adaptive_step_estimation_(adaptive_step_estimation) {
    RTC_DCHECK_GT(num_channels, 0);
    RTC_DCHECK_GT(window_length, 0);
    RTC_DCHECK_GT(reference_window_length, 0);
    RTC_DCHECK_GE(reference_window_delay, 0);
    RTC_DCHECK_GT(reference_window_length + reference_window_delay,
                  window_length);
    const int buffer_length = GetMinFramesProcessed();
    RTC_DCHECK_GT(buffer_length, 0);
    for (int i = 0; i < num_channels; ++i) {
      ch_buffers_.push_back(
          std::make_unique<ClippingPredictorLevelBuffer>(buffer_length));
    }
  }

  ClippingPeakPredictor(const ClippingPeakPredictor&) = delete;
  ClippingPeakPredictor& operator=(const ClippingPeakPredictor&) = delete;
  ~ClippingPeakPredictor() {}

  void Reset() {
    const int num_channels = ch_buffers_.size();
    for (int i = 0; i < num_channels; ++i) {
      ch_buffers_[i]->Reset();
    }
  }

  // Analyzes a frame of audio and stores the framewise metrics in
  // `ch_buffers_`.
  void Analyze(const AudioFrameView<const float>& frame) {
    const int num_channels = frame.num_channels();
    RTC_DCHECK_EQ(num_channels, ch_buffers_.size());
    const int samples_per_channel = frame.samples_per_channel();
    RTC_DCHECK_GT(samples_per_channel, 0);
    for (int channel = 0; channel < num_channels; ++channel) {
      float sum_squares = 0.0f;
      float peak = 0.0f;
      for (const auto& sample : frame.channel(channel)) {
        sum_squares += sample * sample;
        peak = std::max(std::fabs(sample), peak);
      }
      ch_buffers_[channel]->Push(
          {sum_squares / static_cast<float>(samples_per_channel), peak});
    }
  }

  // Estimates the analog gain adjustment for channel `channel` using a
  // sliding window over the frame-wise metrics in `ch_buffers_`. Returns an
  // estimate for the clipped level step (equal to
  // `default_clipped_level_step_` if `adaptive_estimation_` is false) if at
  // least `GetMinFramesProcessed()` frames have been processed since the last
  // reset and a clipping event is predicted. `level`, `min_mic_level`, and
  // `max_mic_level` are limited to [0, 255] and `default_step` to [1, 255].
  absl::optional<int> EstimateClippedLevelStep(int channel,
                                               int level,
                                               int default_step,
                                               int min_mic_level,
                                               int max_mic_level) const {
    RTC_DCHECK_GE(channel, 0);
    RTC_DCHECK_LT(channel, ch_buffers_.size());
    RTC_DCHECK_GE(level, 0);
    RTC_DCHECK_LE(level, 255);
    RTC_DCHECK_GT(default_step, 0);
    RTC_DCHECK_LE(default_step, 255);
    RTC_DCHECK_GE(min_mic_level, 0);
    RTC_DCHECK_LE(min_mic_level, 255);
    RTC_DCHECK_GE(max_mic_level, 0);
    RTC_DCHECK_LE(max_mic_level, 255);
    if (level <= min_mic_level) {
      return absl::nullopt;
    }
    absl::optional<float> estimate_db = EstimatePeakValue(channel);
    if (estimate_db.has_value() && estimate_db.value() > clipping_threshold_) {
      int step = 0;
      if (!adaptive_step_estimation_) {
        step = default_step;
      } else {
        const int estimated_gain_change =
            rtc::SafeClamp(-static_cast<int>(std::ceil(estimate_db.value())),
                           -kClippingPredictorMaxGainChange, 0);
        step =
            std::max(level - LevelFromGainError(estimated_gain_change, level,
                                                min_mic_level, max_mic_level),
                     default_step);
      }
      const int new_level =
          rtc::SafeClamp(level - step, min_mic_level, max_mic_level);
      if (level > new_level) {
        return level - new_level;
      }
    }
    return absl::nullopt;
  }

 private:
  int GetMinFramesProcessed() {
    return reference_window_delay_ + reference_window_length_;
  }

  // Predicts clipping sample peaks based on the processed audio frames.
  // Returns the estimated peak value if clipping is predicted. Otherwise
  // returns absl::nullopt.
  absl::optional<float> EstimatePeakValue(int channel) const {
    const auto reference_metrics = ch_buffers_[channel]->ComputePartialMetrics(
        reference_window_delay_, reference_window_length_);
    if (!reference_metrics.has_value()) {
      return absl::nullopt;
    }
    const auto metrics =
        ch_buffers_[channel]->ComputePartialMetrics(0, window_length_);
    if (!metrics.has_value() ||
        !(FloatS16ToDbfs(metrics.value().max) > clipping_threshold_)) {
      return absl::nullopt;
    }
    const float reference_crest_factor =
        ComputeCrestFactor(reference_metrics.value());
    const float& mean_squares = metrics.value().average;
    const float projected_peak =
        reference_crest_factor + FloatS16ToDbfs(std::sqrt(mean_squares));
    return projected_peak;
  }

  std::vector<std::unique_ptr<ClippingPredictorLevelBuffer>> ch_buffers_;
  const int window_length_;
  const int reference_window_length_;
  const int reference_window_delay_;
  const int clipping_threshold_;
  const bool adaptive_step_estimation_;
};

}  // namespace

std::unique_ptr<ClippingPredictor> CreateClippingPredictor(
    int num_channels,
    const AudioProcessing::Config::GainController1::AnalogGainController::
        ClippingPredictor& config) {
  if (!config.enabled) {
    RTC_LOG(LS_INFO) << "[agc] Clipping prediction disabled.";
    return nullptr;
  }
  RTC_LOG(LS_INFO) << "[agc] Clipping prediction enabled.";
  using ClippingPredictorMode = AudioProcessing::Config::GainController1::
      AnalogGainController::ClippingPredictor::Mode;
  switch (config.mode) {
    case ClippingPredictorMode::kClippingEventPrediction:
      return std::make_unique<ClippingEventPredictor>(
          num_channels, config.window_length, config.reference_window_length,
          config.reference_window_delay, config.clipping_threshold,
          config.crest_factor_margin);
    case ClippingPredictorMode::kAdaptiveStepClippingPeakPrediction:
      return std::make_unique<ClippingPeakPredictor>(
          num_channels, config.window_length, config.reference_window_length,
          config.reference_window_delay, config.clipping_threshold,
          /*adaptive_step_estimation=*/true);
    case ClippingPredictorMode::kFixedStepClippingPeakPrediction:
      return std::make_unique<ClippingPeakPredictor>(
          num_channels, config.window_length, config.reference_window_length,
          config.reference_window_delay, config.clipping_threshold,
          /*adaptive_step_estimation=*/false);
  }
  RTC_DCHECK_NOTREACHED();
}

}  // namespace webrtc
