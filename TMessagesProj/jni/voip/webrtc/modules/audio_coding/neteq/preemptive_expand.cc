/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/preemptive_expand.h"

#include <algorithm>

#include "api/array_view.h"
#include "modules/audio_coding/neteq/audio_multi_vector.h"
#include "modules/audio_coding/neteq/time_stretch.h"

namespace webrtc {

PreemptiveExpand::ReturnCodes PreemptiveExpand::Process(
    const int16_t* input,
    size_t input_length,
    size_t old_data_length,
    AudioMultiVector* output,
    size_t* length_change_samples) {
  old_data_length_per_channel_ = old_data_length;
  // Input length must be (almost) 30 ms.
  // Also, the new part must be at least |overlap_samples_| elements.
  static const size_t k15ms = 120;  // 15 ms = 120 samples at 8 kHz sample rate.
  if (num_channels_ == 0 ||
      input_length / num_channels_ < (2 * k15ms - 1) * fs_mult_ ||
      old_data_length >= input_length / num_channels_ - overlap_samples_) {
    // Length of input data too short to do preemptive expand. Simply move all
    // data from input to output.
    output->PushBackInterleaved(
        rtc::ArrayView<const int16_t>(input, input_length));
    return kError;
  }
  const bool kFastMode = false;  // Fast mode is not available for PE Expand.
  return TimeStretch::Process(input, input_length, kFastMode, output,
                              length_change_samples);
}

void PreemptiveExpand::SetParametersForPassiveSpeech(size_t len,
                                                     int16_t* best_correlation,
                                                     size_t* peak_index) const {
  // When the signal does not contain any active speech, the correlation does
  // not matter. Simply set it to zero.
  *best_correlation = 0;

  // For low energy expansion, the new data can be less than 15 ms,
  // but we must ensure that best_correlation is not larger than the length of
  // the new data.
  // but we must ensure that best_correlation is not larger than the new data.
  *peak_index = std::min(*peak_index, len - old_data_length_per_channel_);
}

PreemptiveExpand::ReturnCodes PreemptiveExpand::CheckCriteriaAndStretch(
    const int16_t* input,
    size_t input_length,
    size_t peak_index,
    int16_t best_correlation,
    bool active_speech,
    bool /*fast_mode*/,
    AudioMultiVector* output) const {
  // Pre-calculate common multiplication with |fs_mult_|.
  // 120 corresponds to 15 ms.
  size_t fs_mult_120 = static_cast<size_t>(fs_mult_ * 120);
  // Check for strong correlation (>0.9 in Q14) and at least 15 ms new data,
  // or passive speech.
  if (((best_correlation > kCorrelationThreshold) &&
       (old_data_length_per_channel_ <= fs_mult_120)) ||
      !active_speech) {
    // Do accelerate operation by overlap add.

    // Set length of the first part, not to be modified.
    size_t unmodified_length =
        std::max(old_data_length_per_channel_, fs_mult_120);
    // Copy first part, including cross-fade region.
    output->PushBackInterleaved(rtc::ArrayView<const int16_t>(
        input, (unmodified_length + peak_index) * num_channels_));
    // Copy the last |peak_index| samples up to 15 ms to |temp_vector|.
    AudioMultiVector temp_vector(num_channels_);
    temp_vector.PushBackInterleaved(rtc::ArrayView<const int16_t>(
        &input[(unmodified_length - peak_index) * num_channels_],
        peak_index * num_channels_));
    // Cross-fade |temp_vector| onto the end of |output|.
    output->CrossFade(temp_vector, peak_index);
    // Copy the last unmodified part, 15 ms + pitch period until the end.
    output->PushBackInterleaved(rtc::ArrayView<const int16_t>(
        &input[unmodified_length * num_channels_],
        input_length - unmodified_length * num_channels_));

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

PreemptiveExpand* PreemptiveExpandFactory::Create(
    int sample_rate_hz,
    size_t num_channels,
    const BackgroundNoise& background_noise,
    size_t overlap_samples) const {
  return new PreemptiveExpand(sample_rate_hz, num_channels, background_noise,
                              overlap_samples);
}

}  // namespace webrtc
