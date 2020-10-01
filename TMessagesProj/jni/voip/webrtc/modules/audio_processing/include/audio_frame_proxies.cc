/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/include/audio_frame_proxies.h"

#include "api/audio/audio_frame.h"
#include "modules/audio_processing/include/audio_processing.h"

namespace webrtc {

int ProcessAudioFrame(AudioProcessing* ap, AudioFrame* frame) {
  if (!frame || !ap) {
    return AudioProcessing::Error::kNullPointerError;
  }

  StreamConfig input_config(frame->sample_rate_hz_, frame->num_channels_,
                            /*has_keyboard=*/false);
  StreamConfig output_config(frame->sample_rate_hz_, frame->num_channels_,
                             /*has_keyboard=*/false);
  RTC_DCHECK_EQ(frame->samples_per_channel(), input_config.num_frames());

  int result = ap->ProcessStream(frame->data(), input_config, output_config,
                                 frame->mutable_data());

  AudioProcessingStats stats = ap->GetStatistics();

  if (stats.voice_detected) {
    frame->vad_activity_ = *stats.voice_detected
                               ? AudioFrame::VADActivity::kVadActive
                               : AudioFrame::VADActivity::kVadPassive;
  }

  return result;
}

int ProcessReverseAudioFrame(AudioProcessing* ap, AudioFrame* frame) {
  if (!frame || !ap) {
    return AudioProcessing::Error::kNullPointerError;
  }

  // Must be a native rate.
  if (frame->sample_rate_hz_ != AudioProcessing::NativeRate::kSampleRate8kHz &&
      frame->sample_rate_hz_ != AudioProcessing::NativeRate::kSampleRate16kHz &&
      frame->sample_rate_hz_ != AudioProcessing::NativeRate::kSampleRate32kHz &&
      frame->sample_rate_hz_ != AudioProcessing::NativeRate::kSampleRate48kHz) {
    return AudioProcessing::Error::kBadSampleRateError;
  }

  if (frame->num_channels_ <= 0) {
    return AudioProcessing::Error::kBadNumberChannelsError;
  }

  StreamConfig input_config(frame->sample_rate_hz_, frame->num_channels_,
                            /*has_keyboard=*/false);
  StreamConfig output_config(frame->sample_rate_hz_, frame->num_channels_,
                             /*has_keyboard=*/false);

  int result = ap->ProcessReverseStream(frame->data(), input_config,
                                        output_config, frame->mutable_data());
  return result;
}

}  // namespace webrtc
