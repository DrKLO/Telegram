/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_VAD_WITH_LEVEL_H_
#define MODULES_AUDIO_PROCESSING_AGC2_VAD_WITH_LEVEL_H_

#include "common_audio/resampler/include/push_resampler.h"
#include "modules/audio_processing/agc2/rnn_vad/features_extraction.h"
#include "modules/audio_processing/agc2/rnn_vad/rnn.h"
#include "modules/audio_processing/include/audio_frame_view.h"

namespace webrtc {
class VadWithLevel {
 public:
  struct LevelAndProbability {
    constexpr LevelAndProbability(float prob, float rms, float peak)
        : speech_probability(prob),
          speech_rms_dbfs(rms),
          speech_peak_dbfs(peak) {}
    LevelAndProbability() = default;
    float speech_probability = 0;
    float speech_rms_dbfs = 0;  // Root mean square in decibels to full-scale.
    float speech_peak_dbfs = 0;
  };

  VadWithLevel();
  ~VadWithLevel();

  LevelAndProbability AnalyzeFrame(AudioFrameView<const float> frame);

 private:
  void SetSampleRate(int sample_rate_hz);

  rnn_vad::RnnBasedVad rnn_vad_;
  rnn_vad::FeaturesExtractor features_extractor_;
  PushResampler<float> resampler_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_VAD_WITH_LEVEL_H_
