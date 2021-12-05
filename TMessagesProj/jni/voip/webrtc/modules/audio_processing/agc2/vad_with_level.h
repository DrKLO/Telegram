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

#include <memory>

#include "modules/audio_processing/agc2/cpu_features.h"
#include "modules/audio_processing/include/audio_frame_view.h"

namespace webrtc {

// Class to analyze voice activity and audio levels.
class VadLevelAnalyzer {
 public:
  struct Result {
    float speech_probability;  // Range: [0, 1].
    float rms_dbfs;            // Root mean square power (dBFS).
    float peak_dbfs;           // Peak power (dBFS).
  };

  // Voice Activity Detector (VAD) interface.
  class VoiceActivityDetector {
   public:
    virtual ~VoiceActivityDetector() = default;
    // Resets the internal state.
    virtual void Reset() = 0;
    // Analyzes an audio frame and returns the speech probability.
    virtual float ComputeProbability(AudioFrameView<const float> frame) = 0;
  };

  // Ctor. `vad_reset_period_ms` indicates the period in milliseconds to call
  // `VadLevelAnalyzer::Reset()`; it must be equal to or greater than the
  // duration of two frames. Uses `cpu_features` to instantiate the default VAD.
  VadLevelAnalyzer(int vad_reset_period_ms,
                   const AvailableCpuFeatures& cpu_features);
  // Ctor. Uses a custom `vad`.
  VadLevelAnalyzer(int vad_reset_period_ms,
                   std::unique_ptr<VoiceActivityDetector> vad);

  VadLevelAnalyzer(const VadLevelAnalyzer&) = delete;
  VadLevelAnalyzer& operator=(const VadLevelAnalyzer&) = delete;
  ~VadLevelAnalyzer();

  // Computes the speech probability and the level for `frame`.
  Result AnalyzeFrame(AudioFrameView<const float> frame);

 private:
  std::unique_ptr<VoiceActivityDetector> vad_;
  const int vad_reset_period_frames_;
  int time_to_vad_reset_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_VAD_WITH_LEVEL_H_
