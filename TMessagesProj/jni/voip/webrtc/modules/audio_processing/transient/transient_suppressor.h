/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_H_

#include <cstddef>

namespace webrtc {

// Detects transients in an audio stream and suppress them using a simple
// restoration algorithm that attenuates unexpected spikes in the spectrum.
class TransientSuppressor {
 public:
  // Type of VAD used by the caller to compute the `voice_probability` argument
  // `Suppress()`.
  enum class VadMode {
    // By default, `TransientSuppressor` assumes that `voice_probability` is
    // computed by `AgcManagerDirect`.
    kDefault = 0,
    // Use this mode when `TransientSuppressor` must assume that
    // `voice_probability` is computed by the RNN VAD.
    kRnnVad,
    // Use this mode to let `TransientSuppressor::Suppressor()` ignore
    // `voice_probability` and behave as if voice information is unavailable
    // (regardless of the passed value).
    kNoVad,
  };

  virtual ~TransientSuppressor() {}

  virtual void Initialize(int sample_rate_hz,
                          int detector_rate_hz,
                          int num_channels) = 0;

  // Processes a `data` chunk, and returns it with keystrokes suppressed from
  // it. The float format is assumed to be int16 ranged. If there are more than
  // one channel, the chunks are concatenated one after the other in `data`.
  // `data_length` must be equal to `data_length_`.
  // `num_channels` must be equal to `num_channels_`.
  // A sub-band, ideally the higher, can be used as `detection_data`. If it is
  // NULL, `data` is used for the detection too. The `detection_data` is always
  // assumed mono.
  // If a reference signal (e.g. keyboard microphone) is available, it can be
  // passed in as `reference_data`. It is assumed mono and must have the same
  // length as `data`. NULL is accepted if unavailable.
  // This suppressor performs better if voice information is available.
  // `voice_probability` is the probability of voice being present in this chunk
  // of audio. If voice information is not available, `voice_probability` must
  // always be set to 1.
  // `key_pressed` determines if a key was pressed on this audio chunk.
  // Returns a delayed version of `voice_probability` according to the
  // algorithmic delay introduced by this method. In this way, the modified
  // `data` and the returned voice probability will be temporally aligned.
  virtual float Suppress(float* data,
                         size_t data_length,
                         int num_channels,
                         const float* detection_data,
                         size_t detection_length,
                         const float* reference_data,
                         size_t reference_length,
                         float voice_probability,
                         bool key_pressed) = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_H_
