/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/adaptive_digital_gain_applier.h"

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

// This function maps input level to desired applied gain. We want to
// boost the signal so that peaks are at -kHeadroomDbfs. We can't
// apply more than kMaxGainDb gain.
float ComputeGainDb(float input_level_dbfs) {
  // If the level is very low, boost it as much as we can.
  if (input_level_dbfs < -(kHeadroomDbfs + kMaxGainDb)) {
    return kMaxGainDb;
  }

  // We expect to end up here most of the time: the level is below
  // -headroom, but we can boost it to -headroom.
  if (input_level_dbfs < -kHeadroomDbfs) {
    return -kHeadroomDbfs - input_level_dbfs;
  }

  // Otherwise, the level is too high and we can't boost. The
  // LevelEstimator is responsible for not reporting bogus gain
  // values.
  RTC_DCHECK_LE(input_level_dbfs, 0.f);
  return 0.f;
}

// Returns `target_gain` if the output noise level is below
// `max_output_noise_level_dbfs`; otherwise returns a capped gain so that the
// output noise level equals `max_output_noise_level_dbfs`.
float LimitGainByNoise(float target_gain,
                       float input_noise_level_dbfs,
                       float max_output_noise_level_dbfs,
                       ApmDataDumper& apm_data_dumper) {
  const float noise_headroom_db =
      max_output_noise_level_dbfs - input_noise_level_dbfs;
  apm_data_dumper.DumpRaw("agc2_noise_headroom_db", noise_headroom_db);
  return std::min(target_gain, std::max(noise_headroom_db, 0.f));
}

float LimitGainByLowConfidence(float target_gain,
                               float last_gain,
                               float limiter_audio_level_dbfs,
                               bool estimate_is_confident) {
  if (estimate_is_confident ||
      limiter_audio_level_dbfs <= kLimiterThresholdForAgcGainDbfs) {
    return target_gain;
  }
  const float limiter_level_before_gain = limiter_audio_level_dbfs - last_gain;

  // Compute a new gain so that limiter_level_before_gain + new_gain <=
  // kLimiterThreshold.
  const float new_target_gain = std::max(
      kLimiterThresholdForAgcGainDbfs - limiter_level_before_gain, 0.f);
  return std::min(new_target_gain, target_gain);
}

// Computes how the gain should change during this frame.
// Return the gain difference in db to 'last_gain_db'.
float ComputeGainChangeThisFrameDb(float target_gain_db,
                                   float last_gain_db,
                                   bool gain_increase_allowed,
                                   float max_gain_change_db) {
  float target_gain_difference_db = target_gain_db - last_gain_db;
  if (!gain_increase_allowed) {
    target_gain_difference_db = std::min(target_gain_difference_db, 0.f);
  }
  return rtc::SafeClamp(target_gain_difference_db, -max_gain_change_db,
                        max_gain_change_db);
}

}  // namespace

AdaptiveDigitalGainApplier::AdaptiveDigitalGainApplier(
    ApmDataDumper* apm_data_dumper,
    int adjacent_speech_frames_threshold,
    float max_gain_change_db_per_second,
    float max_output_noise_level_dbfs)
    : apm_data_dumper_(apm_data_dumper),
      gain_applier_(
          /*hard_clip_samples=*/false,
          /*initial_gain_factor=*/DbToRatio(kInitialAdaptiveDigitalGainDb)),
      adjacent_speech_frames_threshold_(adjacent_speech_frames_threshold),
      max_gain_change_db_per_10ms_(max_gain_change_db_per_second *
                                   kFrameDurationMs / 1000.f),
      max_output_noise_level_dbfs_(max_output_noise_level_dbfs),
      calls_since_last_gain_log_(0),
      frames_to_gain_increase_allowed_(adjacent_speech_frames_threshold_),
      last_gain_db_(kInitialAdaptiveDigitalGainDb) {
  RTC_DCHECK_GT(max_gain_change_db_per_second, 0.f);
  RTC_DCHECK_GE(frames_to_gain_increase_allowed_, 1);
  RTC_DCHECK_GE(max_output_noise_level_dbfs_, -90.f);
  RTC_DCHECK_LE(max_output_noise_level_dbfs_, 0.f);
}

void AdaptiveDigitalGainApplier::Process(const FrameInfo& info,
                                         AudioFrameView<float> frame) {
  RTC_DCHECK_GE(info.input_level_dbfs, -150.f);
  RTC_DCHECK_GE(frame.num_channels(), 1);
  RTC_DCHECK(
      frame.samples_per_channel() == 80 || frame.samples_per_channel() == 160 ||
      frame.samples_per_channel() == 320 || frame.samples_per_channel() == 480)
      << "`frame` does not look like a 10 ms frame for an APM supported sample "
         "rate";

  const float target_gain_db = LimitGainByLowConfidence(
      LimitGainByNoise(ComputeGainDb(std::min(info.input_level_dbfs, 0.f)),
                       info.input_noise_level_dbfs,
                       max_output_noise_level_dbfs_, *apm_data_dumper_),
      last_gain_db_, info.limiter_envelope_dbfs, info.estimate_is_confident);

  // Forbid increasing the gain until enough adjacent speech frames are
  // observed.
  if (info.vad_result.speech_probability < kVadConfidenceThreshold) {
    frames_to_gain_increase_allowed_ = adjacent_speech_frames_threshold_;
  } else if (frames_to_gain_increase_allowed_ > 0) {
    frames_to_gain_increase_allowed_--;
  }

  const float gain_change_this_frame_db = ComputeGainChangeThisFrameDb(
      target_gain_db, last_gain_db_,
      /*gain_increase_allowed=*/frames_to_gain_increase_allowed_ == 0,
      max_gain_change_db_per_10ms_);

  apm_data_dumper_->DumpRaw("agc2_want_to_change_by_db",
                            target_gain_db - last_gain_db_);
  apm_data_dumper_->DumpRaw("agc2_will_change_by_db",
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
  apm_data_dumper_->DumpRaw("agc2_applied_gain_db", last_gain_db_);

  // Log every 10 seconds.
  calls_since_last_gain_log_++;
  if (calls_since_last_gain_log_ == 1000) {
    calls_since_last_gain_log_ = 0;
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc2.DigitalGainApplied",
                                last_gain_db_, 0, kMaxGainDb, kMaxGainDb + 1);
    RTC_HISTOGRAM_COUNTS_LINEAR(
        "WebRTC.Audio.Agc2.EstimatedSpeechPlusNoiseLevel",
        -info.input_level_dbfs, 0, 100, 101);
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.Agc2.EstimatedNoiseLevel",
                                -info.input_noise_level_dbfs, 0, 100, 101);
    RTC_LOG(LS_INFO) << "AGC2 adaptive digital"
                     << " | speech_plus_noise_dbfs: " << info.input_level_dbfs
                     << " | noise_dbfs: " << info.input_noise_level_dbfs
                     << " | gain_db: " << last_gain_db_;
  }
}
}  // namespace webrtc
