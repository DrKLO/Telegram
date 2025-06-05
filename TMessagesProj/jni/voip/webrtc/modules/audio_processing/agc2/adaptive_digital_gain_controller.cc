/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/adaptive_digital_gain_controller.h"

#include <algorithm>

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {

using AdaptiveDigitalConfig =
    AudioProcessing::Config::GainController2::AdaptiveDigital;

constexpr int kHeadroomHistogramMin = 0;
constexpr int kHeadroomHistogramMax = 50;
constexpr int kGainDbHistogramMax = 30;

// Computes the gain for `input_level_dbfs` to reach `-config.headroom_db`.
// Clamps the gain in [0, `config.max_gain_db`]. `config.headroom_db` is a
// safety margin to allow transient peaks to exceed the target peak level
// without clipping.
float ComputeGainDb(float input_level_dbfs,
                    const AdaptiveDigitalConfig& config) {
  // If the level is very low, apply the maximum gain.
  if (input_level_dbfs < -(config.headroom_db + config.max_gain_db)) {
    return config.max_gain_db;
  }
  // We expect to end up here most of the time: the level is below
  // -headroom, but we can boost it to -headroom.
  if (input_level_dbfs < -config.headroom_db) {
    return -config.headroom_db - input_level_dbfs;
  }
  // The level is too high and we can't boost.
  RTC_DCHECK_GE(input_level_dbfs, -config.headroom_db);
  return 0.0f;
}

// Returns `target_gain_db` if applying such a gain to `input_noise_level_dbfs`
// does not exceed `max_output_noise_level_dbfs`. Otherwise lowers and returns
// `target_gain_db` so that the output noise level equals
// `max_output_noise_level_dbfs`.
float LimitGainByNoise(float target_gain_db,
                       float input_noise_level_dbfs,
                       float max_output_noise_level_dbfs,
                       ApmDataDumper& apm_data_dumper) {
  const float max_allowed_gain_db =
      max_output_noise_level_dbfs - input_noise_level_dbfs;
  apm_data_dumper.DumpRaw("agc2_adaptive_gain_applier_max_allowed_gain_db",
                          max_allowed_gain_db);
  return std::min(target_gain_db, std::max(max_allowed_gain_db, 0.0f));
}

float LimitGainByLowConfidence(float target_gain_db,
                               float last_gain_db,
                               float limiter_audio_level_dbfs,
                               bool estimate_is_confident) {
  if (estimate_is_confident ||
      limiter_audio_level_dbfs <= kLimiterThresholdForAgcGainDbfs) {
    return target_gain_db;
  }
  const float limiter_level_dbfs_before_gain =
      limiter_audio_level_dbfs - last_gain_db;

  // Compute a new gain so that `limiter_level_dbfs_before_gain` +
  // `new_target_gain_db` is not great than `kLimiterThresholdForAgcGainDbfs`.
  const float new_target_gain_db = std::max(
      kLimiterThresholdForAgcGainDbfs - limiter_level_dbfs_before_gain, 0.0f);
  return std::min(new_target_gain_db, target_gain_db);
}

// Computes how the gain should change during this frame.
// Return the gain difference in db to 'last_gain_db'.
float ComputeGainChangeThisFrameDb(float target_gain_db,
                                   float last_gain_db,
                                   bool gain_increase_allowed,
                                   float max_gain_decrease_db,
                                   float max_gain_increase_db) {
  RTC_DCHECK_GT(max_gain_decrease_db, 0);
  RTC_DCHECK_GT(max_gain_increase_db, 0);
  float target_gain_difference_db = target_gain_db - last_gain_db;
  if (!gain_increase_allowed) {
    target_gain_difference_db = std::min(target_gain_difference_db, 0.0f);
  }
  return rtc::SafeClamp(target_gain_difference_db, -max_gain_decrease_db,
                        max_gain_increase_db);
}

}  // namespace

AdaptiveDigitalGainController::AdaptiveDigitalGainController(
    ApmDataDumper* apm_data_dumper,
    const AudioProcessing::Config::GainController2::AdaptiveDigital& config,
    int adjacent_speech_frames_threshold)
    : apm_data_dumper_(apm_data_dumper),
      gain_applier_(
          /*hard_clip_samples=*/false,
          /*initial_gain_factor=*/DbToRatio(config.initial_gain_db)),
      config_(config),
      adjacent_speech_frames_threshold_(adjacent_speech_frames_threshold),
      max_gain_change_db_per_10ms_(config_.max_gain_change_db_per_second *
                                   kFrameDurationMs / 1000.0f),
      calls_since_last_gain_log_(0),
      frames_to_gain_increase_allowed_(adjacent_speech_frames_threshold),
      last_gain_db_(config_.initial_gain_db) {
  RTC_DCHECK_GT(max_gain_change_db_per_10ms_, 0.0f);
  RTC_DCHECK_GE(frames_to_gain_increase_allowed_, 1);
  RTC_DCHECK_GE(config_.max_output_noise_level_dbfs, -90.0f);
  RTC_DCHECK_LE(config_.max_output_noise_level_dbfs, 0.0f);
}

void AdaptiveDigitalGainController::Process(const FrameInfo& info,
                                            AudioFrameView<float> frame) {
  RTC_DCHECK_GE(info.speech_level_dbfs, -150.0f);
  RTC_DCHECK_GE(frame.num_channels(), 1);
  RTC_DCHECK(
      frame.samples_per_channel() == 80 || frame.samples_per_channel() == 160 ||
      frame.samples_per_channel() == 320 || frame.samples_per_channel() == 480)
      << "`frame` does not look like a 10 ms frame for an APM supported sample "
         "rate";

  // Compute the input level used to select the desired gain.
  RTC_DCHECK_GT(info.headroom_db, 0.0f);
  const float input_level_dbfs = info.speech_level_dbfs + info.headroom_db;

  const float target_gain_db = LimitGainByLowConfidence(
      LimitGainByNoise(ComputeGainDb(input_level_dbfs, config_),
                       info.noise_rms_dbfs, config_.max_output_noise_level_dbfs,
                       *apm_data_dumper_),
      last_gain_db_, info.limiter_envelope_dbfs, info.speech_level_reliable);

  // Forbid increasing the gain until enough adjacent speech frames are
  // observed.
  bool first_confident_speech_frame = false;
  if (info.speech_probability < kVadConfidenceThreshold) {
    frames_to_gain_increase_allowed_ = adjacent_speech_frames_threshold_;
  } else if (frames_to_gain_increase_allowed_ > 0) {
    frames_to_gain_increase_allowed_--;
    first_confident_speech_frame = frames_to_gain_increase_allowed_ == 0;
  }
  apm_data_dumper_->DumpRaw(
      "agc2_adaptive_gain_applier_frames_to_gain_increase_allowed",
      frames_to_gain_increase_allowed_);

  const bool gain_increase_allowed = frames_to_gain_increase_allowed_ == 0;

  float max_gain_increase_db = max_gain_change_db_per_10ms_;
  if (first_confident_speech_frame) {
    // No gain increase happened while waiting for a long enough speech
    // sequence. Therefore, temporarily allow a faster gain increase.
    RTC_DCHECK(gain_increase_allowed);
    max_gain_increase_db *= adjacent_speech_frames_threshold_;
  }

  const float gain_change_this_frame_db = ComputeGainChangeThisFrameDb(
      target_gain_db, last_gain_db_, gain_increase_allowed,
      /*max_gain_decrease_db=*/max_gain_change_db_per_10ms_,
      max_gain_increase_db);

  apm_data_dumper_->DumpRaw("agc2_adaptive_gain_applier_want_to_change_by_db",
                            target_gain_db - last_gain_db_);
  apm_data_dumper_->DumpRaw("agc2_adaptive_gain_applier_will_change_by_db",
                            gain_change_this_frame_db);

  // Optimization: avoid calling math functions if gain does not
  // change.
  if (gain_change_this_frame_db != 0.f) {
    gain_applier_.SetGainFactor(
        DbToRatio(last_gain_db_ + gain_change_this_frame_db));
  }

  gain_applier_.ApplyGain(frame);

  // Remember that the gain has changed for the next iteration.
  last_gain_db_ = last_gain_db_ + gain_change_this_frame_db;
  apm_data_dumper_->DumpRaw("agc2_adaptive_gain_applier_applied_gain_db",
                            last_gain_db_);

  // Log every 10 seconds.
  calls_since_last_gain_log_++;
  if (calls_since_last_gain_log_ == 1000) {
    calls_since_last_gain_log_ = 0;
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc2.EstimatedSpeechLevel",
                                -info.speech_level_dbfs, 0, 100, 101);
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc2.EstimatedNoiseLevel",
                                -info.noise_rms_dbfs, 0, 100, 101);
    RTC_HISTOGRAM_COUNTS_LINEAR(
        "WebRTC.Audio.Agc2.Headroom", info.headroom_db, kHeadroomHistogramMin,
        kHeadroomHistogramMax,
        kHeadroomHistogramMax - kHeadroomHistogramMin + 1);
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc2.DigitalGainApplied",
                                last_gain_db_, 0, kGainDbHistogramMax,
                                kGainDbHistogramMax + 1);
    RTC_LOG(LS_INFO) << "AGC2 adaptive digital"
                     << " | speech_dbfs: " << info.speech_level_dbfs
                     << " | noise_dbfs: " << info.noise_rms_dbfs
                     << " | headroom_db: " << info.headroom_db
                     << " | gain_db: " << last_gain_db_;
  }
}

}  // namespace webrtc
