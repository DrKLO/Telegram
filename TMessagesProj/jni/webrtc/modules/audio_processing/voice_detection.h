/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_VOICE_DETECTION_H_
#define MODULES_AUDIO_PROCESSING_VOICE_DETECTION_H_

#include <stddef.h>

#include <memory>

#include "modules/audio_processing/include/audio_processing.h"

namespace webrtc {

class AudioBuffer;

// The voice activity detection (VAD) component analyzes the stream to
// determine if voice is present.
class VoiceDetection {
 public:
  // Specifies the likelihood that a frame will be declared to contain voice.
  // A higher value makes it more likely that speech will not be clipped, at
  // the expense of more noise being detected as voice.
  enum Likelihood {
    kVeryLowLikelihood,
    kLowLikelihood,
    kModerateLikelihood,
    kHighLikelihood
  };

  VoiceDetection(int sample_rate_hz, Likelihood likelihood);
  ~VoiceDetection();

  VoiceDetection(VoiceDetection&) = delete;
  VoiceDetection& operator=(VoiceDetection&) = delete;

  // Returns true if voice is detected in the current frame.
  bool ProcessCaptureAudio(AudioBuffer* audio);

  Likelihood likelihood() const { return likelihood_; }

 private:
  class Vad;

  int sample_rate_hz_;
  size_t frame_size_samples_;
  Likelihood likelihood_;
  std::unique_ptr<Vad> vad_;
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_VOICE_DETECTION_H_
