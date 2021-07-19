/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/accelerate.h"

#include <assert.h>

#include "api/array_view.h"
#include "modules/audio_coding/neteq/audio_multi_vector.h"

namespace webrtc {

Accelerate::ReturnCodes Accelerate::Process(const int16_t* input,
                                            size_t input_length,
                                            bool fast_accelerate,
                                            AudioMultiVector* output,
                                            size_t* length_change_samples) {
  // Input length must be (almost) 30 ms.
  static const size_t k15ms = 120;  // 15 ms = 120 samples at 8 kHz sample rate.
  if (num_channels_ == 0 ||
      input_length / num_channels_ < (2 * k15ms - 1) * fs_mult_) {
    // Length of input data too short to do accelerate. Simply move all data
    // from input to output.
    output->PushBackInterleaved(
        rtc::ArrayView<const int16_t>(input, input_length));
    return kError;
  }
  return TimeStretch::Process(input, input_length, fast_accelerate, output,
                              length_change_samples);
}

void Accelerate::SetParametersForPassiveSpeech(size_t /*len*/,
                                               int16_t* best_correlation,
                                               size_t* /*peak_index*/) const {
  // When the signal does not contain any active speech, the correlation does
  // not matter. Simply set it to zero.
  *best_correlation = 0;
}

Accelerate::ReturnCodes Accelerate::CheckCriteriaAndStretch(
    const int16_t* input,
    size_t input_length,
    size_t peak_index,
    int16_t best_correlation,
    bool active_speech,
    bool fast_mode,
    AudioMultiVector* output) const {
  // Check for strong correlation or passive speech.
  // Use 8192 (0.5 in Q14) in fast mode.
  const int correlation_threshold = fast_mode ? 8192 : kCorrelationThreshold;
  if ((best_correlation > correlation_threshold) || !active_speech) {
    // Do accelerate operation by overlap add.

    // Pre-calculate common multiplication with |fs_mult_|.
    // 120 corresponds to 15 ms.
    size_t fs_mult_120 = fs_mult_ * 120;

    if (fast_mode) {
      // Fit as many multiples of |peak_index| as possible in fs_mult_120.
      // TODO(henrik.lundin) Consider finding multiple correlation peaks and
      // pick the one with the longest correlation lag in this case.
      peak_index = (fs_mult_120 / peak_index) * peak_index;
    }

    assert(fs_mult_120 >= peak_index);  // Should be handled in Process().
    // Copy first part; 0 to 15 ms.
    output->PushBackInterleaved(
        rtc::ArrayView<const int16_t>(input, fs_mult_120 * num_channels_));
    // Copy the |peak_index| starting at 15 ms to |temp_vector|.
    AudioMultiVector temp_vector(num_channels_);
    temp_vector.PushBackInterleaved(rtc::ArrayView<const int16_t>(
        &input[fs_mult_120 * num_channels_], peak_index * num_channels_));
    // Cross-fade |temp_vector| onto the end of |output|.
    output->CrossFade(temp_vector, peak_index);
    // Copy the last unmodified part, 15 ms + pitch period until the end.
    output->PushBackInterleaved(rtc::ArrayView<const int16_t>(
        &input[(fs_mult_120 + peak_index) * num_channels_],
        input_length - (fs_mult_120 + peak_index) * num_channels_));

    if (active_speech) {
      return kSuccess;
    } else {
      return kSuccessLowEnergy;
    }
  } else {
    // Accelerate not allowed. Simply move all data from decoded to outData.
    output->PushBackInterleaved(
        rtc::ArrayView<const int16_t>(input, input_length));
    return kNoStretch;
  }
}

Accelerate* AccelerateFactory::Create(
    int sample_rate_hz,
    size_t num_channels,
    const BackgroundNoise& background_noise) const {
  return new Accelerate(sample_rate_hz, num_channels, background_noise);
}

}  // namespace webrtc
