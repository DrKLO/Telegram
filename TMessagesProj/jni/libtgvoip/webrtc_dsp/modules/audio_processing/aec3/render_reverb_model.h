/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_RENDER_REVERB_MODEL_H_
#define MODULES_AUDIO_PROCESSING_AEC3_RENDER_REVERB_MODEL_H_

#include "api/array_view.h"
#include "modules/audio_processing/aec3/reverb_model.h"
#include "modules/audio_processing/aec3/vector_buffer.h"

namespace webrtc {

// The RenderReverbModel class applies an exponential reverberant model over the
// render spectrum.
class RenderReverbModel {
 public:
  RenderReverbModel();
  ~RenderReverbModel();

  // Resets the state.
  void Reset();

  // Applies the reverberation model over the render spectrum. It also returns
  // the reverberation render power spectrum in the array reverb_power_spectrum.
  void Apply(const VectorBuffer& spectrum_buffer,
             int delay_blocks,
             float reverb_decay,
             rtc::ArrayView<float> reverb_power_spectrum);

  // Gets the reverberation spectrum that was added to the render spectrum for
  // computing the reverberation render spectrum.
  rtc::ArrayView<const float> GetReverbContributionPowerSpectrum() const {
    return render_reverb_.GetPowerSpectrum();
  }

 private:
  ReverbModel render_reverb_;
};

}  // namespace webrtc.

#endif  // MODULES_AUDIO_PROCESSING_AEC3_RENDER_REVERB_MODEL_H_
