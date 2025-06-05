/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_VOICE_PROBABILITY_DELAY_UNIT_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_VOICE_PROBABILITY_DELAY_UNIT_H_

#include <array>

namespace webrtc {

// Iteratively produces a sequence of delayed voice probability values given a
// fixed delay between 0 and 20 ms and given a sequence of voice probability
// values observed every 10 ms. Supports fractional delays, that are delays
// which are not a multiple integer of 10 ms. Applies interpolation with
// fractional delays; otherwise, returns a previously observed value according
// to the given fixed delay.
class VoiceProbabilityDelayUnit {
 public:
  // Ctor. `delay_num_samples` is the delay in number of samples and it must be
  // non-negative and less than 20 ms.
  VoiceProbabilityDelayUnit(int delay_num_samples, int sample_rate_hz);

  // Handles delay and sample rate changes and resets the delay unit.
  void Initialize(int delay_num_samples, int sample_rate_hz);

  // Observes `voice_probability` and returns a delayed voice probability.
  float Delay(float voice_probability);

 private:
  std::array<float, 3> weights_;
  std::array<float, 2> last_probabilities_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_VOICE_PROBABILITY_DELAY_UNIT_H_
