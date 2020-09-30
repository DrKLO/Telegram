/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/adaptive_mode_level_estimator_agc.h"

#include <cmath>
#include <vector>

#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/include/audio_frame_view.h"

namespace webrtc {

AdaptiveModeLevelEstimatorAgc::AdaptiveModeLevelEstimatorAgc(
    ApmDataDumper* apm_data_dumper)
    : level_estimator_(apm_data_dumper) {
  set_target_level_dbfs(kDefaultAgc2LevelHeadroomDbfs);
}

// |audio| must be mono; in a multi-channel stream, provide the first (usually
// left) channel.
void AdaptiveModeLevelEstimatorAgc::Process(const int16_t* audio,
                                            size_t length,
                                            int sample_rate_hz) {
  std::vector<float> float_audio_frame(audio, audio + length);
  const float* const first_channel = &float_audio_frame[0];
  AudioFrameView<const float> frame_view(&first_channel, 1 /* num channels */,
                                         length);
  const auto vad_prob = agc2_vad_.AnalyzeFrame(frame_view);
  latest_voice_probability_ = vad_prob.speech_probability;
  if (latest_voice_probability_ > kVadConfidenceThreshold) {
    time_in_ms_since_last_estimate_ += kFrameDurationMs;
  }
  level_estimator_.UpdateEstimation(vad_prob);
}

// Retrieves the difference between the target RMS level and the current
// signal RMS level in dB. Returns true if an update is available and false
// otherwise, in which case |error| should be ignored and no action taken.
bool AdaptiveModeLevelEstimatorAgc::GetRmsErrorDb(int* error) {
  if (time_in_ms_since_last_estimate_ <= kTimeUntilConfidentMs) {
    return false;
  }
  *error = std::floor(target_level_dbfs() -
                      level_estimator_.LatestLevelEstimate() + 0.5f);
  time_in_ms_since_last_estimate_ = 0;
  return true;
}

void AdaptiveModeLevelEstimatorAgc::Reset() {
  level_estimator_.Reset();
}

float AdaptiveModeLevelEstimatorAgc::voice_probability() const {
  return latest_voice_probability_;
}

}  // namespace webrtc
