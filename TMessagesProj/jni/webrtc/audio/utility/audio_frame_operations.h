/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_UTILITY_AUDIO_FRAME_OPERATIONS_H_
#define AUDIO_UTILITY_AUDIO_FRAME_OPERATIONS_H_

#include <stddef.h>
#include <stdint.h>

#include "api/audio/audio_frame.h"
#include "rtc_base/deprecation.h"

namespace webrtc {

// TODO(andrew): consolidate this with utility.h and audio_frame_manipulator.h.
// Change reference parameters to pointers. Consider using a namespace rather
// than a class.
class AudioFrameOperations {
 public:
  // Add samples in |frame_to_add| with samples in |result_frame|
  // putting the results in |results_frame|.  The fields
  // |vad_activity_| and |speech_type_| of the result frame are
  // updated. If |result_frame| is empty (|samples_per_channel_|==0),
  // the samples in |frame_to_add| are added to it.  The number of
  // channels and number of samples per channel must match except when
  // |result_frame| is empty.
  static void Add(const AudioFrame& frame_to_add, AudioFrame* result_frame);

  // |frame.num_channels_| will be updated. This version checks for sufficient
  // buffer size and that |num_channels_| is mono. Use UpmixChannels
  // instead. TODO(bugs.webrtc.org/8649): remove.
  RTC_DEPRECATED static int MonoToStereo(AudioFrame* frame);

  // |frame.num_channels_| will be updated. This version checks that
  // |num_channels_| is stereo. Use DownmixChannels
  // instead. TODO(bugs.webrtc.org/8649): remove.
  RTC_DEPRECATED static int StereoToMono(AudioFrame* frame);

  // Downmixes 4 channels |src_audio| to stereo |dst_audio|. This is an in-place
  // operation, meaning |src_audio| and |dst_audio| may point to the same
  // buffer.
  static void QuadToStereo(const int16_t* src_audio,
                           size_t samples_per_channel,
                           int16_t* dst_audio);

  // |frame.num_channels_| will be updated. This version checks that
  // |num_channels_| is 4 channels.
  static int QuadToStereo(AudioFrame* frame);

  // Downmixes |src_channels| |src_audio| to |dst_channels| |dst_audio|.
  // This is an in-place operation, meaning |src_audio| and |dst_audio|
  // may point to the same buffer. Supported channel combinations are
  // Stereo to Mono, Quad to Mono, and Quad to Stereo.
  static void DownmixChannels(const int16_t* src_audio,
                              size_t src_channels,
                              size_t samples_per_channel,
                              size_t dst_channels,
                              int16_t* dst_audio);

  // |frame.num_channels_| will be updated. This version checks that
  // |num_channels_| and |dst_channels| are valid and performs relevant downmix.
  // Supported channel combinations are N channels to Mono, and Quad to Stereo.
  static void DownmixChannels(size_t dst_channels, AudioFrame* frame);

  // |frame.num_channels_| will be updated. This version checks that
  // |num_channels_| and |dst_channels| are valid and performs relevant
  // downmix. Supported channel combinations are Mono to N
  // channels. The single channel is replicated.
  static void UpmixChannels(size_t target_number_of_channels,
                            AudioFrame* frame);

  // Swap the left and right channels of |frame|. Fails silently if |frame| is
  // not stereo.
  static void SwapStereoChannels(AudioFrame* frame);

  // Conditionally zero out contents of |frame| for implementing audio mute:
  //  |previous_frame_muted| &&  |current_frame_muted| - Zero out whole frame.
  //  |previous_frame_muted| && !|current_frame_muted| - Fade-in at frame start.
  // !|previous_frame_muted| &&  |current_frame_muted| - Fade-out at frame end.
  // !|previous_frame_muted| && !|current_frame_muted| - Leave frame untouched.
  static void Mute(AudioFrame* frame,
                   bool previous_frame_muted,
                   bool current_frame_muted);

  // Zero out contents of frame.
  static void Mute(AudioFrame* frame);

  // Halve samples in |frame|.
  static void ApplyHalfGain(AudioFrame* frame);

  static int Scale(float left, float right, AudioFrame* frame);

  static int ScaleWithSat(float scale, AudioFrame* frame);
};

}  // namespace webrtc

#endif  // AUDIO_UTILITY_AUDIO_FRAME_OPERATIONS_H_
