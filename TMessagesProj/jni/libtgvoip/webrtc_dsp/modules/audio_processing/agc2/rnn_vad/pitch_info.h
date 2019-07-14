/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_PITCH_INFO_H_
#define MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_PITCH_INFO_H_

namespace webrtc {
namespace rnn_vad {

// Stores pitch period and gain information. The pitch gain measures the
// strength of the pitch (the higher, the stronger).
struct PitchInfo {
  PitchInfo() : period(0), gain(0.f) {}
  PitchInfo(size_t p, float g) : period(p), gain(g) {}
  size_t period;
  float gain;
};

}  // namespace rnn_vad
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_PITCH_INFO_H_
