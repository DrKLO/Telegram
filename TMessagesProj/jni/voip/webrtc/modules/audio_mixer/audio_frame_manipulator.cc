/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_mixer/audio_frame_manipulator.h"

#include "audio/utility/audio_frame_operations.h"
#include "audio/utility/channel_mixer.h"
#include "rtc_base/checks.h"

namespace webrtc {

uint32_t AudioMixerCalculateEnergy(const AudioFrame& audio_frame) {
  if (audio_frame.muted()) {
    return 0;
  }

  uint32_t energy = 0;
  const int16_t* frame_data = audio_frame.data();
  for (size_t position = 0;
       position < audio_frame.samples_per_channel_ * audio_frame.num_channels_;
       position++) {
    // TODO(aleloi): This can overflow. Convert to floats.
    energy += frame_data[position] * frame_data[position];
  }
  return energy;
}

void Ramp(float start_gain, float target_gain, AudioFrame* audio_frame) {
  RTC_DCHECK(audio_frame);
  RTC_DCHECK_GE(start_gain, 0.0f);
  RTC_DCHECK_GE(target_gain, 0.0f);
  if (start_gain == target_gain || audio_frame->muted()) {
    return;
  }

  size_t samples = audio_frame->samples_per_channel_;
  RTC_DCHECK_LT(0, samples);
  float increment = (target_gain - start_gain) / samples;
  float gain = start_gain;
  int16_t* frame_data = audio_frame->mutable_data();
  for (size_t i = 0; i < samples; ++i) {
    // If the audio is interleaved of several channels, we want to
    // apply the same gain change to the ith sample of every channel.
    for (size_t ch = 0; ch < audio_frame->num_channels_; ++ch) {
      frame_data[audio_frame->num_channels_ * i + ch] *= gain;
    }
    gain += increment;
  }
}

void RemixFrame(size_t target_number_of_channels, AudioFrame* frame) {
  RTC_DCHECK_GE(target_number_of_channels, 1);
  // TODO(bugs.webrtc.org/10783): take channel layout into account as well.
  if (frame->num_channels() == target_number_of_channels) {
    return;
  }

  // Use legacy components for the most simple cases (mono <-> stereo) to ensure
  // that native WebRTC clients are not affected when support for multi-channel
  // audio is added to Chrome.
  // TODO(bugs.webrtc.org/10783): utilize channel mixer for mono/stereo as well.
  if (target_number_of_channels < 3 && frame->num_channels() < 3) {
    if (frame->num_channels() > target_number_of_channels) {
      AudioFrameOperations::DownmixChannels(target_number_of_channels, frame);
    } else {
      AudioFrameOperations::UpmixChannels(target_number_of_channels, frame);
    }
  } else {
    // Use generic channel mixer when the number of channels for input our
    // output is larger than two. E.g. stereo -> 5.1 channel up-mixing.
    // TODO(bugs.webrtc.org/10783): ensure that actual channel layouts are used
    // instead of guessing based on number of channels.
    const ChannelLayout output_layout(
        GuessChannelLayout(target_number_of_channels));
    ChannelMixer mixer(GuessChannelLayout(frame->num_channels()),
                       output_layout);
    mixer.Transform(frame);
    RTC_DCHECK_EQ(frame->channel_layout(), output_layout);
  }
  RTC_DCHECK_EQ(frame->num_channels(), target_number_of_channels)
      << "Wrong number of channels, " << frame->num_channels() << " vs "
      << target_number_of_channels;
}

}  // namespace webrtc
