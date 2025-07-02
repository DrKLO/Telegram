/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/input_volume_controller.h"

#include <algorithm>
#include <cmath>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/gain_map_internal.h"
#include "modules/audio_processing/agc2/input_volume_stats_reporter.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

// Amount of error we tolerate in the microphone input volume (presumably due to
// OS quantization) before we assume the user has manually adjusted the volume.
constexpr int kVolumeQuantizationSlack = 25;

constexpr int kMaxInputVolume = 255;
static_assert(kGainMapSize > kMaxInputVolume, "gain map too small");

// Maximum absolute RMS error.
constexpr int KMaxAbsRmsErrorDbfs = 15;
static_assert(KMaxAbsRmsErrorDbfs > 0, "");

using Agc1ClippingPredictorConfig = AudioProcessing::Config::GainController1::
    AnalogGainController::ClippingPredictor;

// TODO(webrtc:7494): Hardcode clipping predictor parameters and remove this
// function after no longer needed in the ctor.
Agc1ClippingPredictorConfig CreateClippingPredictorConfig(bool enabled) {
  Agc1ClippingPredictorConfig config;
  config.enabled = enabled;

  return config;
}

// Returns an input volume in the [`min_input_volume`, `kMaxInputVolume`] range
// that reduces `gain_error_db`, which is a gain error estimated when
// `input_volume` was applied, according to a fixed gain map.
int ComputeVolumeUpdate(int gain_error_db,
                        int input_volume,
                        int min_input_volume) {
  RTC_DCHECK_GE(input_volume, 0);
  RTC_DCHECK_LE(input_volume, kMaxInputVolume);
  if (gain_error_db == 0) {
    return input_volume;
  }

  int new_volume = input_volume;
  if (gain_error_db > 0) {
    while (kGainMap[new_volume] - kGainMap[input_volume] < gain_error_db &&
           new_volume < kMaxInputVolume) {
      ++new_volume;
    }
  } else {
    while (kGainMap[new_volume] - kGainMap[input_volume] > gain_error_db &&
           new_volume > min_input_volume) {
      --new_volume;
    }
  }
  return new_volume;
}

// Returns the proportion of samples in the buffer which are at full-scale
// (and presumably clipped).
float ComputeClippedRatio(const float* const* audio,
                          size_t num_channels,
                          size_t samples_per_channel) {
  RTC_DCHECK_GT(samples_per_channel, 0);
  int num_clipped = 0;
  for (size_t ch = 0; ch < num_channels; ++ch) {
    int num_clipped_in_ch = 0;
    for (size_t i = 0; i < samples_per_channel; ++i) {
      RTC_DCHECK(audio[ch]);
      if (audio[ch][i] >= 32767.0f || audio[ch][i] <= -32768.0f) {
        ++num_clipped_in_ch;
      }
    }
    num_clipped = std::max(num_clipped, num_clipped_in_ch);
  }
  return static_cast<float>(num_clipped) / (samples_per_channel);
}

void LogClippingMetrics(int clipping_rate) {
  RTC_LOG(LS_INFO) << "[AGC2] Input clipping rate: " << clipping_rate << "%";
  RTC_HISTOGRAM_COUNTS_LINEAR(/*name=*/"WebRTC.Audio.Agc.InputClippingRate",
                              /*sample=*/clipping_rate, /*min=*/0, /*max=*/100,
                              /*bucket_count=*/50);
}

// Compares `speech_level_dbfs` to the [`target_range_min_dbfs`,
// `target_range_max_dbfs`] range and returns the error to be compensated via
// input volume adjustment. Returns a positive value when the level is below
// the range, a negative value when the level is above the range, zero
// otherwise.
int GetSpeechLevelRmsErrorDb(float speech_level_dbfs,
                             int target_range_min_dbfs,
                             int target_range_max_dbfs) {
  constexpr float kMinSpeechLevelDbfs = -90.0f;
  constexpr float kMaxSpeechLevelDbfs = 30.0f;
  RTC_DCHECK_GE(speech_level_dbfs, kMinSpeechLevelDbfs);
  RTC_DCHECK_LE(speech_level_dbfs, kMaxSpeechLevelDbfs);
  speech_level_dbfs = rtc::SafeClamp<float>(
      speech_level_dbfs, kMinSpeechLevelDbfs, kMaxSpeechLevelDbfs);

  int rms_error_db = 0;
  if (speech_level_dbfs > target_range_max_dbfs) {
    rms_error_db = std::round(target_range_max_dbfs - speech_level_dbfs);
  } else if (speech_level_dbfs < target_range_min_dbfs) {
    rms_error_db = std::round(target_range_min_dbfs - speech_level_dbfs);
  }

  return rms_error_db;
}

}  // namespace

MonoInputVolumeController::MonoInputVolumeController(
    int min_input_volume_after_clipping,
    int min_input_volume,
    int update_input_volume_wait_frames,
    float speech_probability_threshold,
    float speech_ratio_threshold)
    : min_input_volume_(min_input_volume),
      min_input_volume_after_clipping_(min_input_volume_after_clipping),
      max_input_volume_(kMaxInputVolume),
      update_input_volume_wait_frames_(
          std::max(update_input_volume_wait_frames, 1)),
      speech_probability_threshold_(speech_probability_threshold),
      speech_ratio_threshold_(speech_ratio_threshold) {
  RTC_DCHECK_GE(min_input_volume_, 0);
  RTC_DCHECK_LE(min_input_volume_, 255);
  RTC_DCHECK_GE(min_input_volume_after_clipping_, 0);
  RTC_DCHECK_LE(min_input_volume_after_clipping_, 255);
  RTC_DCHECK_GE(max_input_volume_, 0);
  RTC_DCHECK_LE(max_input_volume_, 255);
  RTC_DCHECK_GE(update_input_volume_wait_frames_, 0);
  RTC_DCHECK_GE(speech_probability_threshold_, 0.0f);
  RTC_DCHECK_LE(speech_probability_threshold_, 1.0f);
  RTC_DCHECK_GE(speech_ratio_threshold_, 0.0f);
  RTC_DCHECK_LE(speech_ratio_threshold_, 1.0f);
}

MonoInputVolumeController::~MonoInputVolumeController() = default;

void MonoInputVolumeController::Initialize() {
  max_input_volume_ = kMaxInputVolume;
  capture_output_used_ = true;
  check_volume_on_next_process_ = true;
  frames_since_update_input_volume_ = 0;
  speech_frames_since_update_input_volume_ = 0;
  is_first_frame_ = true;
}

// A speeh segment is considered active if at least
// `update_input_volume_wait_frames_` new frames have been processed since the
// previous update and the ratio of non-silence frames (i.e., frames with a
// `speech_probability` higher than `speech_probability_threshold_`) is at least
// `speech_ratio_threshold_`.
void MonoInputVolumeController::Process(absl::optional<int> rms_error_db,
                                        float speech_probability) {
  if (check_volume_on_next_process_) {
    check_volume_on_next_process_ = false;
    // We have to wait until the first process call to check the volume,
    // because Chromium doesn't guarantee it to be valid any earlier.
    CheckVolumeAndReset();
  }

  // Count frames with a high speech probability as speech.
  if (speech_probability >= speech_probability_threshold_) {
    ++speech_frames_since_update_input_volume_;
  }

  // Reset the counters and maybe update the input volume.
  if (++frames_since_update_input_volume_ >= update_input_volume_wait_frames_) {
    const float speech_ratio =
        static_cast<float>(speech_frames_since_update_input_volume_) /
        static_cast<float>(update_input_volume_wait_frames_);

    // Always reset the counters regardless of whether the volume changes or
    // not.
    frames_since_update_input_volume_ = 0;
    speech_frames_since_update_input_volume_ = 0;

    // Update the input volume if allowed.
    if (!is_first_frame_ && speech_ratio >= speech_ratio_threshold_ &&
        rms_error_db.has_value()) {
      UpdateInputVolume(*rms_error_db);
    }
  }

  is_first_frame_ = false;
}

void MonoInputVolumeController::HandleClipping(int clipped_level_step) {
  RTC_DCHECK_GT(clipped_level_step, 0);
  // Always decrease the maximum input volume, even if the current input volume
  // is below threshold.
  SetMaxLevel(std::max(min_input_volume_after_clipping_,
                       max_input_volume_ - clipped_level_step));
  if (log_to_histograms_) {
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.AgcClippingAdjustmentAllowed",
                          last_recommended_input_volume_ - clipped_level_step >=
                              min_input_volume_after_clipping_);
  }
  if (last_recommended_input_volume_ > min_input_volume_after_clipping_) {
    // Don't try to adjust the input volume if we're already below the limit. As
    // a consequence, if the user has brought the input volume above the limit,
    // we will still not react until the postproc updates the input volume.
    SetInputVolume(
        std::max(min_input_volume_after_clipping_,
                 last_recommended_input_volume_ - clipped_level_step));
    frames_since_update_input_volume_ = 0;
    speech_frames_since_update_input_volume_ = 0;
    is_first_frame_ = false;
  }
}

void MonoInputVolumeController::SetInputVolume(int new_volume) {
  int applied_input_volume = recommended_input_volume_;
  if (applied_input_volume == 0) {
    RTC_DLOG(LS_INFO)
        << "[AGC2] The applied input volume is zero, taking no action.";
    return;
  }
  if (applied_input_volume < 0 || applied_input_volume > kMaxInputVolume) {
    RTC_LOG(LS_ERROR) << "[AGC2] Invalid value for the applied input volume: "
                      << applied_input_volume;
    return;
  }

  // Detect manual input volume adjustments by checking if the
  // `applied_input_volume` is outside of the `[last_recommended_input_volume_ -
  // kVolumeQuantizationSlack, last_recommended_input_volume_ +
  // kVolumeQuantizationSlack]` range.
  if (applied_input_volume >
          last_recommended_input_volume_ + kVolumeQuantizationSlack ||
      applied_input_volume <
          last_recommended_input_volume_ - kVolumeQuantizationSlack) {
    RTC_DLOG(LS_INFO)
        << "[AGC2] The input volume was manually adjusted. Updating "
           "stored input volume from "
        << last_recommended_input_volume_ << " to " << applied_input_volume;
    last_recommended_input_volume_ = applied_input_volume;
    // Always allow the user to increase the volume.
    if (last_recommended_input_volume_ > max_input_volume_) {
      SetMaxLevel(last_recommended_input_volume_);
    }
    // Take no action in this case, since we can't be sure when the volume
    // was manually adjusted.
    frames_since_update_input_volume_ = 0;
    speech_frames_since_update_input_volume_ = 0;
    is_first_frame_ = false;
    return;
  }

  new_volume = std::min(new_volume, max_input_volume_);
  if (new_volume == last_recommended_input_volume_) {
    return;
  }

  recommended_input_volume_ = new_volume;
  RTC_DLOG(LS_INFO) << "[AGC2] Applied input volume: " << applied_input_volume
                    << " | last recommended input volume: "
                    << last_recommended_input_volume_
                    << " | newly recommended input volume: " << new_volume;
  last_recommended_input_volume_ = new_volume;
}

void MonoInputVolumeController::SetMaxLevel(int input_volume) {
  RTC_DCHECK_GE(input_volume, min_input_volume_after_clipping_);
  max_input_volume_ = input_volume;
  RTC_DLOG(LS_INFO) << "[AGC2] Maximum input volume updated: "
                    << max_input_volume_;
}

void MonoInputVolumeController::HandleCaptureOutputUsedChange(
    bool capture_output_used) {
  if (capture_output_used_ == capture_output_used) {
    return;
  }
  capture_output_used_ = capture_output_used;

  if (capture_output_used) {
    // When we start using the output, we should reset things to be safe.
    check_volume_on_next_process_ = true;
  }
}

int MonoInputVolumeController::CheckVolumeAndReset() {
  int input_volume = recommended_input_volume_;
  // Reasons for taking action at startup:
  // 1) A person starting a call is expected to be heard.
  // 2) Independent of interpretation of `input_volume` == 0 we should raise it
  // so the AGC can do its job properly.
  if (input_volume == 0 && !startup_) {
    RTC_DLOG(LS_INFO)
        << "[AGC2] The applied input volume is zero, taking no action.";
    return 0;
  }
  if (input_volume < 0 || input_volume > kMaxInputVolume) {
    RTC_LOG(LS_ERROR) << "[AGC2] Invalid value for the applied input volume: "
                      << input_volume;
    return -1;
  }
  RTC_DLOG(LS_INFO) << "[AGC2] Initial input volume: " << input_volume;

  if (input_volume < min_input_volume_) {
    input_volume = min_input_volume_;
    RTC_DLOG(LS_INFO)
        << "[AGC2] The initial input volume is too low, raising to "
        << input_volume;
    recommended_input_volume_ = input_volume;
  }

  last_recommended_input_volume_ = input_volume;
  startup_ = false;
  frames_since_update_input_volume_ = 0;
  speech_frames_since_update_input_volume_ = 0;
  is_first_frame_ = true;

  return 0;
}

void MonoInputVolumeController::UpdateInputVolume(int rms_error_db) {
  RTC_DLOG(LS_INFO) << "[AGC2] RMS error: " << rms_error_db << " dB";
  // Prevent too large microphone input volume changes by clamping the RMS
  // error.
  rms_error_db =
      rtc::SafeClamp(rms_error_db, -KMaxAbsRmsErrorDbfs, KMaxAbsRmsErrorDbfs);
  if (rms_error_db == 0) {
    return;
  }
  SetInputVolume(ComputeVolumeUpdate(
      rms_error_db, last_recommended_input_volume_, min_input_volume_));
}

InputVolumeController::InputVolumeController(int num_capture_channels,
                                             const Config& config)
    : num_capture_channels_(num_capture_channels),
      min_input_volume_(config.min_input_volume),
      capture_output_used_(true),
      clipped_level_step_(config.clipped_level_step),
      clipped_ratio_threshold_(config.clipped_ratio_threshold),
      clipped_wait_frames_(config.clipped_wait_frames),
      clipping_predictor_(CreateClippingPredictor(
          num_capture_channels,
          CreateClippingPredictorConfig(config.enable_clipping_predictor))),
      use_clipping_predictor_step_(
          !!clipping_predictor_ &&
          CreateClippingPredictorConfig(config.enable_clipping_predictor)
              .use_predicted_step),
      frames_since_clipped_(config.clipped_wait_frames),
      clipping_rate_log_counter_(0),
      clipping_rate_log_(0.0f),
      target_range_max_dbfs_(config.target_range_max_dbfs),
      target_range_min_dbfs_(config.target_range_min_dbfs),
      channel_controllers_(num_capture_channels) {
  RTC_LOG(LS_INFO)
      << "[AGC2] Input volume controller enabled. Minimum input volume: "
      << min_input_volume_;

  for (auto& controller : channel_controllers_) {
    controller = std::make_unique<MonoInputVolumeController>(
        config.clipped_level_min, min_input_volume_,
        config.update_input_volume_wait_frames,
        config.speech_probability_threshold, config.speech_ratio_threshold);
  }

  RTC_DCHECK(!channel_controllers_.empty());
  RTC_DCHECK_GT(clipped_level_step_, 0);
  RTC_DCHECK_LE(clipped_level_step_, 255);
  RTC_DCHECK_GT(clipped_ratio_threshold_, 0.0f);
  RTC_DCHECK_LT(clipped_ratio_threshold_, 1.0f);
  RTC_DCHECK_GT(clipped_wait_frames_, 0);
  channel_controllers_[0]->ActivateLogging();
}

InputVolumeController::~InputVolumeController() {}

void InputVolumeController::Initialize() {
  for (auto& controller : channel_controllers_) {
    controller->Initialize();
  }
  capture_output_used_ = true;

  AggregateChannelLevels();
  clipping_rate_log_ = 0.0f;
  clipping_rate_log_counter_ = 0;

  applied_input_volume_ = absl::nullopt;
}

void InputVolumeController::AnalyzeInputAudio(int applied_input_volume,
                                              const AudioBuffer& audio_buffer) {
  RTC_DCHECK_GE(applied_input_volume, 0);
  RTC_DCHECK_LE(applied_input_volume, 255);

  SetAppliedInputVolume(applied_input_volume);

  RTC_DCHECK_EQ(audio_buffer.num_channels(), channel_controllers_.size());
  const float* const* audio = audio_buffer.channels_const();
  size_t samples_per_channel = audio_buffer.num_frames();
  RTC_DCHECK(audio);

  AggregateChannelLevels();
  if (!capture_output_used_) {
    return;
  }

  if (!!clipping_predictor_) {
    AudioFrameView<const float> frame = AudioFrameView<const float>(
        audio, num_capture_channels_, static_cast<int>(samples_per_channel));
    clipping_predictor_->Analyze(frame);
  }

  // Check for clipped samples. We do this in the preprocessing phase in order
  // to catch clipped echo as well.
  //
  // If we find a sufficiently clipped frame, drop the current microphone
  // input volume and enforce a new maximum input volume, dropped the same
  // amount from the current maximum. This harsh treatment is an effort to avoid
  // repeated clipped echo events.
  float clipped_ratio =
      ComputeClippedRatio(audio, num_capture_channels_, samples_per_channel);
  clipping_rate_log_ = std::max(clipped_ratio, clipping_rate_log_);
  clipping_rate_log_counter_++;
  constexpr int kNumFramesIn30Seconds = 3000;
  if (clipping_rate_log_counter_ == kNumFramesIn30Seconds) {
    LogClippingMetrics(std::round(100.0f * clipping_rate_log_));
    clipping_rate_log_ = 0.0f;
    clipping_rate_log_counter_ = 0;
  }

  if (frames_since_clipped_ < clipped_wait_frames_) {
    ++frames_since_clipped_;
    return;
  }

  const bool clipping_detected = clipped_ratio > clipped_ratio_threshold_;
  bool clipping_predicted = false;
  int predicted_step = 0;
  if (!!clipping_predictor_) {
    for (int channel = 0; channel < num_capture_channels_; ++channel) {
      const auto step = clipping_predictor_->EstimateClippedLevelStep(
          channel, recommended_input_volume_, clipped_level_step_,
          channel_controllers_[channel]->min_input_volume_after_clipping(),
          kMaxInputVolume);
      if (step.has_value()) {
        predicted_step = std::max(predicted_step, step.value());
        clipping_predicted = true;
      }
    }
  }

  if (clipping_detected) {
    RTC_DLOG(LS_INFO) << "[AGC2] Clipping detected (ratio: " << clipped_ratio
                      << ")";
  }

  int step = clipped_level_step_;
  if (clipping_predicted) {
    predicted_step = std::max(predicted_step, clipped_level_step_);
    RTC_DLOG(LS_INFO) << "[AGC2] Clipping predicted (volume down step: "
                      << predicted_step << ")";
    if (use_clipping_predictor_step_) {
      step = predicted_step;
    }
  }

  if (clipping_detected ||
      (clipping_predicted && use_clipping_predictor_step_)) {
    for (auto& state_ch : channel_controllers_) {
      state_ch->HandleClipping(step);
    }
    frames_since_clipped_ = 0;
    if (!!clipping_predictor_) {
      clipping_predictor_->Reset();
    }
  }

  AggregateChannelLevels();
}

absl::optional<int> InputVolumeController::RecommendInputVolume(
    float speech_probability,
    absl::optional<float> speech_level_dbfs) {
  // Only process if applied input volume is set.
  if (!applied_input_volume_.has_value()) {
    RTC_LOG(LS_ERROR) << "[AGC2] Applied input volume not set.";
    return absl::nullopt;
  }

  AggregateChannelLevels();
  const int volume_after_clipping_handling = recommended_input_volume_;

  if (!capture_output_used_) {
    return applied_input_volume_;
  }

  absl::optional<int> rms_error_db;
  if (speech_level_dbfs.has_value()) {
    // Compute the error for all frames (both speech and non-speech frames).
    rms_error_db = GetSpeechLevelRmsErrorDb(
        *speech_level_dbfs, target_range_min_dbfs_, target_range_max_dbfs_);
  }

  for (auto& controller : channel_controllers_) {
    controller->Process(rms_error_db, speech_probability);
  }

  AggregateChannelLevels();
  if (volume_after_clipping_handling != recommended_input_volume_) {
    // The recommended input volume was adjusted in order to match the target
    // level.
    UpdateHistogramOnRecommendedInputVolumeChangeToMatchTarget(
        recommended_input_volume_);
  }

  applied_input_volume_ = absl::nullopt;
  return recommended_input_volume();
}

void InputVolumeController::HandleCaptureOutputUsedChange(
    bool capture_output_used) {
  for (auto& controller : channel_controllers_) {
    controller->HandleCaptureOutputUsedChange(capture_output_used);
  }

  capture_output_used_ = capture_output_used;
}

void InputVolumeController::SetAppliedInputVolume(int input_volume) {
  applied_input_volume_ = input_volume;

  for (auto& controller : channel_controllers_) {
    controller->set_stream_analog_level(input_volume);
  }

  AggregateChannelLevels();
}

void InputVolumeController::AggregateChannelLevels() {
  int new_recommended_input_volume =
      channel_controllers_[0]->recommended_analog_level();
  channel_controlling_gain_ = 0;
  for (size_t ch = 1; ch < channel_controllers_.size(); ++ch) {
    int input_volume = channel_controllers_[ch]->recommended_analog_level();
    if (input_volume < new_recommended_input_volume) {
      new_recommended_input_volume = input_volume;
      channel_controlling_gain_ = static_cast<int>(ch);
    }
  }

  // Enforce the minimum input volume when a recommendation is made.
  if (applied_input_volume_.has_value() && *applied_input_volume_ > 0) {
    new_recommended_input_volume =
        std::max(new_recommended_input_volume, min_input_volume_);
  }

  recommended_input_volume_ = new_recommended_input_volume;
}

}  // namespace webrtc
