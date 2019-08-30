/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_REVERB_MODEL_H_
#define MODULES_AUDIO_PROCESSING_AEC3_REVERB_MODEL_H_

#include <array>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"

namespace webrtc {

// The ReverbModel class describes an exponential reverberant model
// that can be applied over power spectrums.
class ReverbModel {
 public:
  ReverbModel();
  ~ReverbModel();

  // Resets the state.
  void Reset();

  // The methods AddReverbNoFreqShaping and AddReverb add the reverberation
  // contribution to an input/output power spectrum
  // Before applying the exponential reverberant model, the input power spectrum
  // is pre-scaled. Use the method AddReverb when a different scaling should be
  // applied per frequency and AddReverb_no_freq_shape if the same scaling
  // should be used for all the frequencies.
  void AddReverbNoFreqShaping(rtc::ArrayView<const float> power_spectrum,
                              float power_spectrum_scaling,
                              float reverb_decay,
                              rtc::ArrayView<float> reverb_power_spectrum);

  void AddReverb(rtc::ArrayView<const float> power_spectrum,
                 rtc::ArrayView<const float> freq_response_tail,
                 float reverb_decay,
                 rtc::ArrayView<float> reverb_power_spectrum);

  // Updates the reverberation contributions without applying any shaping of the
  // spectrum.
  void UpdateReverbContributionsNoFreqShaping(
      rtc::ArrayView<const float> power_spectrum,
      float power_spectrum_scaling,
      float reverb_decay);

  // Returns the current power spectrum reverberation contributions.
  rtc::ArrayView<const float> GetPowerSpectrum() const { return reverb_; }

 private:
  // Updates the reverberation contributions.
  void UpdateReverbContributions(rtc::ArrayView<const float>& power_spectrum,
                                 rtc::ArrayView<const float>& freq_resp_tail,
                                 float reverb_decay);

  std::array<float, kFftLengthBy2Plus1> reverb_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_REVERB_MODEL_H_
