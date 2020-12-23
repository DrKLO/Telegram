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

// Combines a level estimation with the saturation protector margins.
float ComputeLevelEstimateDbfs(float level_estimate_dbfs,
                               float saturation_margin_db,
                               float extra_saturation_margin_db) {
  return rtc::SafeClamp<float>(
      level_estimate_dbfs + saturation_margin_db + extra_saturation_margin_db,
      -90.f, 30.f);
}

// Returns the level of given type from `vad_level`.
float GetLevel(const VadLevelAnalyzer::Result& vad_level,
               LevelEstimatorType type) {
  switch (type) {
    case LevelEstimatorType::kRms:
      return vad_level.rms_dbfs;
      break;
    case LevelEstimatorType::kPeak:
      return vad_level.peak_dbfs;
      break;
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace

bool AdaptiveModeLevelEstimator::LevelEstimatorState::operator==(
    const AdaptiveModeLevelEstimator::LevelEstimatorState& b) const {
  return time_to_full_buffer_ms == b.time_to_full_buffer_ms &&
         level_dbfs.numerator == b.level_dbfs.numerator &&
         level_dbfs.denominator == b.level_dbfs.denominator &&
         saturation_protector == b.saturation_protector;
}

float AdaptiveModeLevelEstimator::LevelEstimatorState::Ratio::GetRatio() const {
  RTC_DCHECK_NE(denominator, 0.f);
  return numerator / denominator;
}

AdaptiveModeLevelEstimator::AdaptiveModeLevelEstimator(
    ApmDataDumper* apm_data_dumper)
    : AdaptiveModeLevelEstimator(
          apm_data_dumper,
          AudioProcessing::Config::GainController2::LevelEstimator::kRms,
          kDefaultLevelEstimatorAdjacentSpeechFramesThreshold,
          kDefaultInitialSaturationMarginDb,
          kDefaultExtraSaturationMarginDb) {}

AdaptiveModeLevelEstimator::AdaptiveModeLevelEstimator(
    ApmDataDumper* apm_data_dumper,
    AudioProcessing::Config::GainController2::LevelEstimator level_estimator,
    int adjacent_speech_frames_threshold,
    float initial_saturation_margin_db,
    float extra_saturation_margin_db)
    : apm_data_dumper_(apm_data_dumper),
      level_estimator_type_(level_estimator),
      adjacent_speech_frames_threshold_(adjacent_speech_frames_threshold),
      initial_saturation_margin_db_(initial_saturation_margin_db),
      extra_saturation_margin_db_(extra_saturation_margin_db),
      level_dbfs_(ComputeLevelEstimateDbfs(kInitialSpeechLevelEstimateDbfs,
                                           initial_saturation_margin_db_,
                                           extra_saturation_margin_db_)) {
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
  DumpDebugData();

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
    return;
  }

  // Speech frame observed.
  num_adjacent_speech_frames_++;

  // Update preliminary level estimate.
  RTC_DCHECK_GE(preliminary_state_.time_to_full_buffer_ms, 0);
  const bool buffer_is_full = preliminary_state_.time_to_full_buffer_ms == 0;
  if (!buffer_is_full) {
    preliminary_state_.time_to_full_buffer_ms -= kFrameDurationMs;
  }
  // Weighted average of levels with speech probability as weight.
  RTC_DCHECK_GT(vad_level.speech_probability, 0.f);
  const float leak_factor = buffer_is_full ? kFullBufferLeakFactor : 1.f;
  preliminary_state_.level_dbfs.numerator =
      preliminary_state_.level_dbfs.numerator * leak_factor +
      GetLevel(vad_level, level_estimator_type_) * vad_level.speech_probability;
  preliminary_state_.level_dbfs.denominator =
      preliminary_state_.level_dbfs.denominator * leak_factor +
      vad_level.speech_probability;

  const float level_dbfs = preliminary_state_.level_dbfs.GetRatio();

  UpdateSaturationProtectorState(vad_level.peak_dbfs, level_dbfs,
                                 preliminary_state_.saturation_protector);

  if (num_adjacent_speech_frames_ >= adjacent_speech_frames_threshold_) {
    // `preliminary_state_` is now reliable. Update the last level estimation.
    level_dbfs_ = ComputeLevelEstimateDbfs(
        level_dbfs, preliminary_state_.saturation_protector.margin_db,
        extra_saturation_margin_db_);
  }
}

bool AdaptiveModeLevelEstimator::IsConfident() const {
  if (adjacent_speech_frames_threshold_ == 1) {
    // Ignore `reliable_state_` when a single frame is enough to update the
    // level estimate (because it is not used).
    return preliminary_state_.time_to_full_buffer_ms == 0;
  }
  // Once confident, it remains confident.
  RTC_DCHECK(reliable_state_.time_to_full_buffer_ms != 0 ||
             preliminary_state_.time_to_full_buffer_ms == 0);
  // During the first long enough speech sequence, `reliable_state_` must be
  // ignored since `preliminary_state_` is used.
  return reliable_state_.time_to_full_buffer_ms == 0 ||
         (num_adjacent_speech_frames_ >= adjacent_speech_frames_threshold_ &&
          preliminary_state_.time_to_full_buffer_ms == 0);
}

void AdaptiveModeLevelEstimator::Reset() {
  ResetLevelEstimatorState(preliminary_state_);
  ResetLevelEstimatorState(reliable_state_);
  level_dbfs_ = ComputeLevelEstimateDbfs(kInitialSpeechLevelEstimateDbfs,
                                         initial_saturation_margin_db_,
                                         extra_saturation_margin_db_);
  num_adjacent_speech_frames_ = 0;
}

void AdaptiveModeLevelEstimator::ResetLevelEstimatorState(
    LevelEstimatorState& state) const {
  state.time_to_full_buffer_ms = kFullBufferSizeMs;
  state.level_dbfs.numerator = 0.f;
  state.level_dbfs.denominator = 0.f;
  ResetSaturationProtectorState(initial_saturation_margin_db_,
                                state.saturation_protector);
}

void AdaptiveModeLevelEstimator::DumpDebugData() const {
  apm_data_dumper_->DumpRaw("agc2_adaptive_level_estimate_dbfs", level_dbfs_);
  apm_data_dumper_->DumpRaw("agc2_adaptive_num_adjacent_speech_frames_",
                            num_adjacent_speech_frames_);
  apm_data_dumper_->DumpRaw("agc2_adaptive_preliminary_level_estimate_num",
                            preliminary_state_.level_dbfs.numerator);
  apm_data_dumper_->DumpRaw("agc2_adaptive_preliminary_level_estimate_den",
                            preliminary_state_.level_dbfs.denominator);
  apm_data_dumper_->DumpRaw("agc2_adaptive_preliminary_saturation_margin_db",
                            preliminary_state_.saturation_protector.margin_db);
}

}  // namespace webrtc
