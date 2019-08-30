/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_GENERATOR_H_
#define MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_GENERATOR_H_

#include "modules/audio_processing/include/audio_frame_view.h"

namespace webrtc {
// This class is used as input sink for the APM, for diagnostic purposes.
// Generates an infinite audio signal, [-1, 1] floating point values, in frames
// of fixed channel count and sample rate.
class AudioGenerator {
 public:
  virtual ~AudioGenerator() {}

  // Fill |audio| with the next samples of the audio signal.
  virtual void FillFrame(AudioFrameView<float> audio) = 0;

  // Return the number of channels output by the AudioGenerator.
  virtual size_t NumChannels() = 0;

  // Return the sample rate output by the AudioGenerator.
  virtual size_t SampleRateHz() = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_GENERATOR_H_
