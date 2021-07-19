/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/utility/audio_frame_operations.h"

#include <string.h>

#include <algorithm>
#include <cstdint>
#include <utility>

#include "common_audio/include/audio_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace {

// 2.7ms @ 48kHz, 4ms @ 32kHz, 8ms @ 16kHz.
const size_t kMuteFadeFrames = 128;
const float kMuteFadeInc = 1.0f / kMuteFadeFrames;

}  // namespace

void AudioFrameOperations::Add(const AudioFrame& frame_to_add,
                               AudioFrame* result_frame) {
  // Sanity check.
  RTC_DCHECK(result_frame);
  RTC_DCHECK_GT(result_frame->num_channels_, 0);
  RTC_DCHECK_EQ(result_frame->num_channels_, frame_to_add.num_channels_);

  bool no_previous_data = result_frame->muted();
  if (result_frame->samples_per_channel_ != frame_to_add.samples_per_channel_) {
    // Special case we have no data to start with.
    RTC_DCHECK_EQ(result_frame->samples_per_channel_, 0);
    result_frame->samples_per_channel_ = frame_to_add.samples_per_channel_;
    no_previous_data = true;
  }

  if (result_frame->vad_activity_ == AudioFrame::kVadActive ||
      frame_to_add.vad_activity_ == AudioFrame::kVadActive) {
    result_frame->vad_activity_ = AudioFrame::kVadActive;
  } else if (result_frame->vad_activity_ == AudioFrame::kVadUnknown ||
             frame_to_add.vad_activity_ == AudioFrame::kVadUnknown) {
    result_frame->vad_activity_ = AudioFrame::kVadUnknown;
  }

  if (result_frame->speech_type_ != frame_to_add.speech_type_)
    result_frame->speech_type_ = AudioFrame::kUndefined;

  if (!frame_to_add.muted()) {
    const int16_t* in_data = frame_to_add.data();
    int16_t* out_data = result_frame->mutable_data();
    size_t length =
        frame_to_add.samples_per_channel_ * frame_to_add.num_channels_;
    if (no_previous_data) {
      std::copy(in_data, in_data + length, out_data);
    } else {
      for (size_t i = 0; i < length; i++) {
        const int32_t wrap_guard = static_cast<int32_t>(out_data[i]) +
                                   static_cast<int32_t>(in_data[i]);
        out_data[i] = rtc::saturated_cast<int16_t>(wrap_guard);
      }
    }
  }
}

int AudioFrameOperations::MonoToStereo(AudioFrame* frame) {
  if (frame->num_channels_ != 1) {
    return -1;
  }
  UpmixChannels(2, frame);
  return 0;
}

int AudioFrameOperations::StereoToMono(AudioFrame* frame) {
  if (frame->num_channels_ != 2) {
    return -1;
  }
  DownmixChannels(1, frame);
  return frame->num_channels_ == 1 ? 0 : -1;
}

void AudioFrameOperations::QuadToStereo(const int16_t* src_audio,
                                        size_t samples_per_channel,
                                        int16_t* dst_audio) {
  for (size_t i = 0; i < samples_per_channel; i++) {
    dst_audio[i * 2] =
        (static_cast<int32_t>(src_audio[4 * i]) + src_audio[4 * i + 1]) >> 1;
    dst_audio[i * 2 + 1] =
        (static_cast<int32_t>(src_audio[4 * i + 2]) + src_audio[4 * i + 3]) >>
        1;
  }
}

int AudioFrameOperations::QuadToStereo(AudioFrame* frame) {
  if (frame->num_channels_ != 4) {
    return -1;
  }

  RTC_DCHECK_LE(frame->samples_per_channel_ * 4,
                AudioFrame::kMaxDataSizeSamples);

  if (!frame->muted()) {
    QuadToStereo(frame->data(), frame->samples_per_channel_,
                 frame->mutable_data());
  }
  frame->num_channels_ = 2;

  return 0;
}

void AudioFrameOperations::DownmixChannels(const int16_t* src_audio,
                                           size_t src_channels,
                                           size_t samples_per_channel,
                                           size_t dst_channels,
                                           int16_t* dst_audio) {
  if (src_channels > 1 && dst_channels == 1) {
    DownmixInterleavedToMono(src_audio, samples_per_channel, src_channels,
                             dst_audio);
    return;
  } else if (src_channels == 4 && dst_channels == 2) {
    QuadToStereo(src_audio, samples_per_channel, dst_audio);
    return;
  }

  RTC_NOTREACHED() << "src_channels: " << src_channels
                   << ", dst_channels: " << dst_channels;
}

void AudioFrameOperations::DownmixChannels(size_t dst_channels,
                                           AudioFrame* frame) {
  RTC_DCHECK_LE(frame->samples_per_channel_ * frame->num_channels_,
                AudioFrame::kMaxDataSizeSamples);
  if (frame->num_channels_ > 1 && dst_channels == 1) {
    if (!frame->muted()) {
      DownmixInterleavedToMono(frame->data(), frame->samples_per_channel_,
                               frame->num_channels_, frame->mutable_data());
    }
    frame->num_channels_ = 1;
  } else if (frame->num_channels_ == 4 && dst_channels == 2) {
    int err = QuadToStereo(frame);
    RTC_DCHECK_EQ(err, 0);
  } else {
    RTC_NOTREACHED() << "src_channels: " << frame->num_channels_
                     << ", dst_channels: " << dst_channels;
  }
}

void AudioFrameOperations::UpmixChannels(size_t target_number_of_channels,
                                         AudioFrame* frame) {
  RTC_DCHECK_EQ(frame->num_channels_, 1);
  RTC_DCHECK_LE(frame->samples_per_channel_ * target_number_of_channels,
                AudioFrame::kMaxDataSizeSamples);

  if (frame->num_channels_ != 1 ||
      frame->samples_per_channel_ * target_number_of_channels >
          AudioFrame::kMaxDataSizeSamples) {
    return;
  }

  if (!frame->muted()) {
    // Up-mixing done in place. Going backwards through the frame ensure nothing
    // is irrevocably overwritten.
    int16_t* frame_data = frame->mutable_data();
    for (int i = frame->samples_per_channel_ - 1; i >= 0; i--) {
      for (size_t j = 0; j < target_number_of_channels; ++j) {
        frame_data[target_number_of_channels * i + j] = frame_data[i];
      }
    }
  }
  frame->num_channels_ = target_number_of_channels;
}

void AudioFrameOperations::SwapStereoChannels(AudioFrame* frame) {
  RTC_DCHECK(frame);
  if (frame->num_channels_ != 2 || frame->muted()) {
    return;
  }

  int16_t* frame_data = frame->mutable_data();
  for (size_t i = 0; i < frame->samples_per_channel_ * 2; i += 2) {
    std::swap(frame_data[i], frame_data[i + 1]);
  }
}

void AudioFrameOperations::Mute(AudioFrame* frame,
                                bool previous_frame_muted,
                                bool current_frame_muted) {
  RTC_DCHECK(frame);
  if (!previous_frame_muted && !current_frame_muted) {
    // Not muted, don't touch.
  } else if (previous_frame_muted && current_frame_muted) {
    // Frame fully muted.
    size_t total_samples = frame->samples_per_channel_ * frame->num_channels_;
    RTC_DCHECK_GE(AudioFrame::kMaxDataSizeSamples, total_samples);
    frame->Mute();
  } else {
    // Fade is a no-op on a muted frame.
    if (frame->muted()) {
      return;
    }

    // Limit number of samples to fade, if frame isn't long enough.
    size_t count = kMuteFadeFrames;
    float inc = kMuteFadeInc;
    if (frame->samples_per_channel_ < kMuteFadeFrames) {
      count = frame->samples_per_channel_;
      if (count > 0) {
        inc = 1.0f / count;
      }
    }

    size_t start = 0;
    size_t end = count;
    float start_g = 0.0f;
    if (current_frame_muted) {
      // Fade out the last |count| samples of frame.
      RTC_DCHECK(!previous_frame_muted);
      start = frame->samples_per_channel_ - count;
      end = frame->samples_per_channel_;
      start_g = 1.0f;
      inc = -inc;
    } else {
      // Fade in the first |count| samples of frame.
      RTC_DCHECK(previous_frame_muted);
    }

    // Perform fade.
    int16_t* frame_data = frame->mutable_data();
    size_t channels = frame->num_channels_;
    for (size_t j = 0; j < channels; ++j) {
      float g = start_g;
      for (size_t i = start * channels; i < end * channels; i += channels) {
        g += inc;
        frame_data[i + j] *= g;
      }
    }
  }
}

void AudioFrameOperations::Mute(AudioFrame* frame) {
  Mute(frame, true, true);
}

void AudioFrameOperations::ApplyHalfGain(AudioFrame* frame) {
  RTC_DCHECK(frame);
  RTC_DCHECK_GT(frame->num_channels_, 0);
  if (frame->num_channels_ < 1 || frame->muted()) {
    return;
  }

  int16_t* frame_data = frame->mutable_data();
  for (size_t i = 0; i < frame->samples_per_channel_ * frame->num_channels_;
       i++) {
    frame_data[i] = frame_data[i] >> 1;
  }
}

int AudioFrameOperations::Scale(float left, float right, AudioFrame* frame) {
  if (frame->num_channels_ != 2) {
    return -1;
  } else if (frame->muted()) {
    return 0;
  }

  int16_t* frame_data = frame->mutable_data();
  for (size_t i = 0; i < frame->samples_per_channel_; i++) {
    frame_data[2 * i] = static_cast<int16_t>(left * frame_data[2 * i]);
    frame_data[2 * i + 1] = static_cast<int16_t>(right * frame_data[2 * i + 1]);
  }
  return 0;
}

int AudioFrameOperations::ScaleWithSat(float scale, AudioFrame* frame) {
  if (frame->muted()) {
    return 0;
  }

  int16_t* frame_data = frame->mutable_data();
  for (size_t i = 0; i < frame->samples_per_channel_ * frame->num_channels_;
       i++) {
    frame_data[i] = rtc::saturated_cast<int16_t>(scale * frame_data[i]);
  }
  return 0;
}
}  // namespace webrtc
