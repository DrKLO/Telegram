/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/adaptive_mode_level_estimator.h"

#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

using LevelEstimatorType =
    AudioProcessing::Config::GainController2::LevelEstimator;

float ClampLevelEstimateDbfs(float level_estimate_dbfs) {
  return rtc::SafeClamp<float>(level_estimate_dbfs, -90.f, 30.f);
}

}  // namespace

bool AdaptiveModeLevelEstimator::LevelEstimatorState::operator==(
    const AdaptiveModeLevelEstimator::LevelEstimatorState& b) const {
  return time_to_confidence_ms == b.time_to_confidence_ms &&
         level_dbfs.numerator == b.level_dbfs.numerator &&
         level_dbfs.denominator == b.level_dbfs.denominator;
}

float AdaptiveModeLevelEstimator::LevelEstimatorState::Ratio::GetRatio() const {
  RTC_DCHECK_NE(denominator, 0.f);
  return numerator / denominator;
}

AdaptiveModeLevelEstimator::AdaptiveModeLevelEstimator(
    ApmDataDumper* apm_data_dumper)
    : AdaptiveModeLevelEstimator(
          apm_data_dumper,
          kDefaultLevelEstimatorAdjacentSpeechFramesThreshold) {}

AdaptiveModeLevelEstimator::AdaptiveModeLevelEstimator(
    ApmDataDumper* apm_data_dumper,
    int adjacent_speech_frames_threshold)
    : apm_data_dumper_(apm_data_dumper),
      adjacent_speech_frames_threshold_(adjacent_speech_frames_threshold),
      level_dbfs_(ClampLevelEstimateDbfs(kInitialSpeechLevelEstimateDbfs)) {
  RTC_DCHECK(apm_data_dumper_);
  RTC_DCHECK_GE(adjacent_speech_frames_threshold_, 1);
  Reset();
}

void AdaptiveModeLevelEstimator::Update(
    const VadLevelAnalyzer::Result& vad_level) {
  RTC_DCHECK_GT(vad_level.rms_dbfs, -150.f);
  RTC_DCHECK_LT(vad_level.rms_dbfs, 50.f);
  RTC_DCHECK_GT(vad_level.peak_dbfs, -150.f);
  RTC_DCHECK_LT(vad_level.peak_dbfs, 50.f);
  RTC_DCHECK_GE(vad_level.speech_probability, 0.f);
  RTC_DCHECK_LE(vad_level.speech_probability, 1.f);
  if (vad_level.speech_probability < kVadConfidenceThreshold) {
    // Not a speech frame.
    if (adjacent_speech_frames_threshold_ > 1) {
      // When two or more adjacent speech frames are required in order to update
      // the state, we need to decide whether to discard or confirm the updates
      // based on the speech sequence length.
      if (num_adjacent_speech_frames_ >= adjacent_speech_frames_threshold_) {
        // First non-speech frame after a long enough sequence of speech frames.
        // Update the reliable state.
        reliable_state_ = preliminary_state_;
      } else if (num_adjacent_speech_frames_ > 0) {
        // First non-speech frame after a too short sequence of speech frames.
        // Reset to the last reliable state.
        preliminary_state_ = reliable_state_;
      }
    }
    num_adjacent_speech_frames_ = 0;
  } else {
    // Speech frame observed.
    num_adjacent_speech_frames_++;

    // Update preliminary level estimate.
    RTC_DCHECK_GE(preliminary_state_.time_to_confidence_ms, 0);
    const bool buffer_is_full = preliminary_state_.time_to_confidence_ms == 0;
    if (!buffer_is_full) {
      preliminary_state_.time_to_confidence_ms -= kFrameDurationMs;
    }
    // Weighted average of levels with speech probability as weight.
    RTC_DCHECK_GT(vad_level.speech_probability, 0.f);
    const float leak_factor = buffer_is_full ? kLevelEstimatorLeakFactor : 1.f;
    preliminary_state_.level_dbfs.numerator =
        preliminary_state_.level_dbfs.numerator * leak_factor +
        vad_level.rms_dbfs * vad_level.speech_probability;
    preliminary_state_.level_dbfs.denominator =
        preliminary_state_.level_dbfs.denominator * leak_factor +
        vad_level.speech_probability;

    const float level_dbfs = preliminary_state_.level_dbfs.GetRatio();

    if (num_adjacent_speech_frames_ >= adjacent_speech_frames_threshold_) {
      // `preliminary_state_` is now reliable. Update the last level estimation.
      level_dbfs_ = ClampLevelEstimateDbfs(level_dbfs);
    }
  }
  DumpDebugData();
}

bool AdaptiveModeLevelEstimator::IsConfident() const {
  if (adjacent_speech_frames_threshold_ == 1) {
    // Ignore `reliable_state_` when a single frame is enough to update the
    // level estimate (because it is not used).
    return preliminary_state_.time_to_confidence_ms == 0;
  }
  // Once confident, it remains confident.
  RTC_DCHECK(reliable_state_.time_to_confidence_ms != 0 ||
             preliminary_state_.time_to_confidence_ms == 0);
  // During the first long enough speech sequence, `reliable_state_` must be
  // ignored since `preliminary_state_` is used.
  return reliable_state_.time_to_confidence_ms == 0 ||
         (num_adjacent_speech_frames_ >= adjacent_speech_frames_threshold_ &&
          preliminary_state_.time_to_confidence_ms == 0);
}

void AdaptiveModeLevelEstimator::Reset() {
  ResetLevelEstimatorState(preliminary_state_);
  ResetLevelEstimatorState(reliable_state_);
  level_dbfs_ = ClampLevelEstimateDbfs(kInitialSpeechLevelEstimateDbfs);
  num_adjacent_speech_frames_ = 0;
}

void AdaptiveModeLevelEstimator::ResetLevelEstimatorState(
    LevelEstimatorState& state) const {
  state.time_to_confidence_ms = kLevelEstimatorTimeToConfidenceMs;
  state.level_dbfs.numerator = kInitialSpeechLevelEstimateDbfs;
  state.level_dbfs.denominator = 1.0f;
}

void AdaptiveModeLevelEstimator::DumpDebugData() const {
  apm_data_dumper_->DumpRaw(
      "agc2_adaptive_level_estimator_num_adjacent_speech_frames",
      num_adjacent_speech_frames_);
  apm_data_dumper_->DumpRaw(
      "agc2_adaptive_level_estimator_preliminary_level_estimate_num",
      preliminary_state_.level_dbfs.numerator);
  apm_data_dumper_->DumpRaw(
      "agc2_adaptive_level_estimator_preliminary_level_estimate_den",
      preliminary_state_.level_dbfs.denominator);
  apm_data_dumper_->DumpRaw(
      "agc2_adaptive_level_estimator_preliminary_time_to_confidence_ms",
      preliminary_state_.time_to_confidence_ms);
  apm_data_dumper_->DumpRaw(
      "agc2_adaptive_level_estimator_reliable_time_to_confidence_ms",
      reliable_state_.time_to_confidence_ms);
}

}  // namespace webrtc
