/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec_dump/capture_stream_info.h"

namespace webrtc {

void CaptureStreamInfo::AddInput(const AudioFrameView<const float>& src) {
  auto* stream = event_->mutable_stream();

  for (int i = 0; i < src.num_channels(); ++i) {
    const auto& channel_view = src.channel(i);
    stream->add_input_channel(channel_view.begin(),
                              sizeof(float) * channel_view.size());
  }
}

void CaptureStreamInfo::AddOutput(const AudioFrameView<const float>& src) {
  auto* stream = event_->mutable_stream();

  for (int i = 0; i < src.num_channels(); ++i) {
    const auto& channel_view = src.channel(i);
    stream->add_output_channel(channel_view.begin(),
                               sizeof(float) * channel_view.size());
  }
}

void CaptureStreamInfo::AddInput(const int16_t* const data,
                                 int num_channels,
                                 int samples_per_channel) {
  auto* stream = event_->mutable_stream();
  const size_t data_size = sizeof(int16_t) * samples_per_channel * num_channels;
  stream->set_input_data(data, data_size);
}

void CaptureStreamInfo::AddOutput(const int16_t* const data,
                                  int num_channels,
                                  int samples_per_channel) {
  auto* stream = event_->mutable_stream();
  const size_t data_size = sizeof(int16_t) * samples_per_channel * num_channels;
  stream->set_output_data(data, data_size);
}

void CaptureStreamInfo::AddAudioProcessingState(
    const AecDump::AudioProcessingState& state) {
  auto* stream = event_->mutable_stream();
  stream->set_delay(state.delay);
  stream->set_drift(state.drift);
  if (state.applied_input_volume.has_value()) {
    stream->set_applied_input_volume(*state.applied_input_volume);
  }
  stream->set_keypress(state.keypress);
}
}  // namespace webrtc
