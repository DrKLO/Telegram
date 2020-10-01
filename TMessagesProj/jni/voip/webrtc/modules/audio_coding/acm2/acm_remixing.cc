/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/acm_remixing.h"

#include "rtc_base/checks.h"

namespace webrtc {

void DownMixFrame(const AudioFrame& input, rtc::ArrayView<int16_t> output) {
  RTC_DCHECK_EQ(input.num_channels_, 2);
  RTC_DCHECK_EQ(output.size(), input.samples_per_channel_);

  if (input.muted()) {
    std::fill(output.begin(), output.begin() + input.samples_per_channel_, 0);
  } else {
    const int16_t* const input_data = input.data();
    for (size_t n = 0; n < input.samples_per_channel_; ++n) {
      output[n] = rtc::dchecked_cast<int16_t>(
          (int32_t{input_data[2 * n]} + int32_t{input_data[2 * n + 1]}) >> 1);
    }
  }
}

void ReMixFrame(const AudioFrame& input,
                size_t num_output_channels,
                std::vector<int16_t>* output) {
  const size_t output_size = num_output_channels * input.samples_per_channel_;
  RTC_DCHECK(!(input.num_channels_ == 0 && num_output_channels > 0 &&
               input.samples_per_channel_ > 0));

  if (output->size() != output_size) {
    output->resize(output_size);
  }

  // For muted frames, fill the frame with zeros.
  if (input.muted()) {
    std::fill(output->begin(), output->end(), 0);
    return;
  }

  // Ensure that the special case of zero input channels is handled correctly
  // (zero samples per channel is already handled correctly in the code below).
  if (input.num_channels_ == 0) {
    return;
  }

  const int16_t* const input_data = input.data();
  size_t out_index = 0;

  // When upmixing is needed and the input is mono copy the left channel
  // into the left and right channels, and set any remaining channels to zero.
  if (input.num_channels_ == 1 && input.num_channels_ < num_output_channels) {
    for (size_t k = 0; k < input.samples_per_channel_; ++k) {
      (*output)[out_index++] = input_data[k];
      (*output)[out_index++] = input_data[k];
      for (size_t j = 2; j < num_output_channels; ++j) {
        (*output)[out_index++] = 0;
      }
      RTC_DCHECK_EQ(out_index, (k + 1) * num_output_channels);
    }
    RTC_DCHECK_EQ(out_index, input.samples_per_channel_ * num_output_channels);
    return;
  }

  size_t in_index = 0;

  // When upmixing is needed and the output is surround, copy the available
  // channels directly, and set the remaining channels to zero.
  if (input.num_channels_ < num_output_channels) {
    for (size_t k = 0; k < input.samples_per_channel_; ++k) {
      for (size_t j = 0; j < input.num_channels_; ++j) {
        (*output)[out_index++] = input_data[in_index++];
      }
      for (size_t j = input.num_channels_; j < num_output_channels; ++j) {
        (*output)[out_index++] = 0;
      }
      RTC_DCHECK_EQ(in_index, (k + 1) * input.num_channels_);
      RTC_DCHECK_EQ(out_index, (k + 1) * num_output_channels);
    }
    RTC_DCHECK_EQ(in_index, input.samples_per_channel_ * input.num_channels_);
    RTC_DCHECK_EQ(out_index, input.samples_per_channel_ * num_output_channels);

    return;
  }

  // When downmixing is needed, and the input is stereo, average the channels.
  if (input.num_channels_ == 2) {
    for (size_t n = 0; n < input.samples_per_channel_; ++n) {
      (*output)[n] = rtc::dchecked_cast<int16_t>(
          (int32_t{input_data[2 * n]} + int32_t{input_data[2 * n + 1]}) >> 1);
    }
    return;
  }

  // When downmixing is needed, and the input is multichannel, drop the surplus
  // channels.
  const size_t num_channels_to_drop = input.num_channels_ - num_output_channels;
  for (size_t k = 0; k < input.samples_per_channel_; ++k) {
    for (size_t j = 0; j < num_output_channels; ++j) {
      (*output)[out_index++] = input_data[in_index++];
    }
    in_index += num_channels_to_drop;
  }
}

}  // namespace webrtc
