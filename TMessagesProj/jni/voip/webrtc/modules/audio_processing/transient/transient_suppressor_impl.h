/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_IMPL_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_IMPL_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>

#include "modules/audio_processing/transient/transient_suppressor.h"
#include "modules/audio_processing/transient/voice_probability_delay_unit.h"
#include "rtc_base/gtest_prod_util.h"

namespace webrtc {

class TransientDetector;

// Detects transients in an audio stream and suppress them using a simple
// restoration algorithm that attenuates unexpected spikes in the spectrum.
class TransientSuppressorImpl : public TransientSuppressor {
 public:
  TransientSuppressorImpl(VadMode vad_mode,
                          int sample_rate_hz,
                          int detector_rate_hz,
                          int num_channels);
  ~TransientSuppressorImpl() override;

  void Initialize(int sample_rate_hz,
                  int detector_rate_hz,
                  int num_channels) override;

  float Suppress(float* data,
                 size_t data_length,
                 int num_channels,
                 const float* detection_data,
                 size_t detection_length,
                 const float* reference_data,
                 size_t reference_length,
                 float voice_probability,
                 bool key_pressed) override;

 private:
  FRIEND_TEST_ALL_PREFIXES(TransientSuppressorVadModeParametrization,
                           TypingDetectionLogicWorksAsExpectedForMono);
  void Suppress(float* in_ptr, float* spectral_mean, float* out_ptr);

  void UpdateKeypress(bool key_pressed);
  void UpdateRestoration(float voice_probability);

  void UpdateBuffers(float* data);

  void HardRestoration(float* spectral_mean);
  void SoftRestoration(float* spectral_mean);

  const VadMode vad_mode_;
  VoiceProbabilityDelayUnit voice_probability_delay_unit_;

  std::unique_ptr<TransientDetector> detector_;

  bool analyzed_audio_is_silent_;

  size_t data_length_;
  size_t detection_length_;
  size_t analysis_length_;
  size_t buffer_delay_;
  size_t complex_analysis_length_;
  int num_channels_;
  // Input buffer where the original samples are stored.
  std::unique_ptr<float[]> in_buffer_;
  std::unique_ptr<float[]> detection_buffer_;
  // Output buffer where the restored samples are stored.
  std::unique_ptr<float[]> out_buffer_;

  // Arrays for fft.
  std::unique_ptr<size_t[]> ip_;
  std::unique_ptr<float[]> wfft_;

  std::unique_ptr<float[]> spectral_mean_;

  // Stores the data for the fft.
  std::unique_ptr<float[]> fft_buffer_;

  std::unique_ptr<float[]> magnitudes_;

  const float* window_;

  std::unique_ptr<float[]> mean_factor_;

  float detector_smoothed_;

  int keypress_counter_;
  int chunks_since_keypress_;
  bool detection_enabled_;
  bool suppression_enabled_;

  bool use_hard_restoration_;
  int chunks_since_voice_change_;

  uint32_t seed_;

  bool using_reference_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_IMPL_H_
