/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/utility/channel_mixer.h"

#include "audio/utility/channel_mixing_matrix.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

ChannelMixer::ChannelMixer(ChannelLayout input_layout,
                           ChannelLayout output_layout)
    : input_layout_(input_layout),
      output_layout_(output_layout),
      input_channels_(ChannelLayoutToChannelCount(input_layout)),
      output_channels_(ChannelLayoutToChannelCount(output_layout)) {
  // Create the transformation matrix.
  ChannelMixingMatrix matrix_builder(input_layout_, input_channels_,
                                     output_layout_, output_channels_);
  remapping_ = matrix_builder.CreateTransformationMatrix(&matrix_);
}

ChannelMixer::~ChannelMixer() = default;

void ChannelMixer::Transform(AudioFrame* frame) {
  RTC_DCHECK(frame);
  RTC_DCHECK_EQ(matrix_[0].size(), static_cast<size_t>(input_channels_));
  RTC_DCHECK_EQ(matrix_.size(), static_cast<size_t>(output_channels_));

  // Leave the audio frame intact if the channel layouts for in and out are
  // identical.
  if (input_layout_ == output_layout_) {
    return;
  }

  if (IsUpMixing()) {
    RTC_CHECK_LE(frame->samples_per_channel() * output_channels_,
                 frame->max_16bit_samples());
  }

  // Only change the number of output channels if the audio frame is muted.
  if (frame->muted()) {
    frame->num_channels_ = output_channels_;
    frame->channel_layout_ = output_layout_;
    return;
  }

  const int16_t* in_audio = frame->data();

  // Only allocate fresh memory at first access or if the required size has
  // increased.
  // TODO(henrika): we might be able to do downmixing in-place and thereby avoid
  // extra memory allocation and a memcpy.
  const size_t num_elements = frame->samples_per_channel() * output_channels_;
  if (audio_vector_ == nullptr || num_elements > audio_vector_size_) {
    audio_vector_.reset(new int16_t[num_elements]);
    audio_vector_size_ = num_elements;
  }
  int16_t* out_audio = audio_vector_.get();

  // Modify the number of channels by creating a weighted sum of input samples
  // where the weights (scale factors) for each output sample are given by the
  // transformation matrix.
  for (size_t i = 0; i < frame->samples_per_channel(); i++) {
    for (size_t output_ch = 0; output_ch < output_channels_; ++output_ch) {
      float acc_value = 0.0f;
      for (size_t input_ch = 0; input_ch < input_channels_; ++input_ch) {
        const float scale = matrix_[output_ch][input_ch];
        // Scale should always be positive.
        RTC_DCHECK_GE(scale, 0);
        // Each output sample is a weighted sum of input samples.
        acc_value += scale * in_audio[i * input_channels_ + input_ch];
      }
      const size_t index = output_channels_ * i + output_ch;
      RTC_CHECK_LE(index, audio_vector_size_);
      out_audio[index] = rtc::saturated_cast<int16_t>(acc_value);
    }
  }

  // Update channel information.
  frame->num_channels_ = output_channels_;
  frame->channel_layout_ = output_layout_;

  // Copy the output result to the audio frame in |frame|.
  memcpy(
      frame->mutable_data(), out_audio,
      sizeof(int16_t) * frame->samples_per_channel() * frame->num_channels());
}

}  // namespace webrtc
