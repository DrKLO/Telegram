/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_H_

#include <stddef.h>
#include <stdint.h>
#include <memory>

#include "rtc_base/gtest_prod_util.h"

namespace webrtc {

class TransientDetector;

// Detects transients in an audio stream and suppress them using a simple
// restoration algorithm that attenuates unexpected spikes in the spectrum.
class TransientSuppressor {
 public:
  TransientSuppressor();
  ~TransientSuppressor();

  int Initialize(int sample_rate_hz, int detector_rate_hz, int num_channels);

  // Processes a |data| chunk, and returns it with keystrokes suppressed from
  // it. The float format is assumed to be int16 ranged. If there are more than
  // one channel, the chunks are concatenated one after the other in |data|.
  // |data_length| must be equal to |data_length_|.
  // |num_channels| must be equal to |num_channels_|.
  // A sub-band, ideally the higher, can be used as |detection_data|. If it is
  // NULL, |data| is used for the detection too. The |detection_data| is always
  // assumed mono.
  // If a reference signal (e.g. keyboard microphone) is available, it can be
  // passed in as |reference_data|. It is assumed mono and must have the same
  // length as |data|. NULL is accepted if unavailable.
  // This suppressor performs better if voice information is available.
  // |voice_probability| is the probability of voice being present in this chunk
  // of audio. If voice information is not available, |voice_probability| must
  // always be set to 1.
  // |key_pressed| determines if a key was pressed on this audio chunk.
  // Returns 0 on success and -1 otherwise.
  int Suppress(float* data,
               size_t data_length,
               int num_channels,
               const float* detection_data,
               size_t detection_length,
               const float* reference_data,
               size_t reference_length,
               float voice_probability,
               bool key_pressed);

 private:
  FRIEND_TEST_ALL_PREFIXES(TransientSuppressorTest,
                           TypingDetectionLogicWorksAsExpectedForMono);
  void Suppress(float* in_ptr, float* spectral_mean, float* out_ptr);

  void UpdateKeypress(bool key_pressed);
  void UpdateRestoration(float voice_probability);

  void UpdateBuffers(float* data);

  void HardRestoration(float* spectral_mean);
  void SoftRestoration(float* spectral_mean);

  std::unique_ptr<TransientDetector> detector_;

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

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_SUPPRESSOR_H_
