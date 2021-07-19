/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_REMIX_RESAMPLE_H_
#define AUDIO_REMIX_RESAMPLE_H_

#include "api/audio/audio_frame.h"
#include "common_audio/resampler/include/push_resampler.h"

namespace webrtc {
namespace voe {

// Upmix or downmix and resample the audio to |dst_frame|. Expects |dst_frame|
// to have its sample rate and channels members set to the desired values.
// Updates the |samples_per_channel_| member accordingly.
//
// This version has an AudioFrame |src_frame| as input and sets the output
// |timestamp_|, |elapsed_time_ms_| and |ntp_time_ms_| members equals to the
// input ones.
void RemixAndResample(const AudioFrame& src_frame,
                      PushResampler<int16_t>* resampler,
                      AudioFrame* dst_frame);

// This version has a pointer to the samples |src_data| as input and receives
// |samples_per_channel|, |num_channels| and |sample_rate_hz| of the data as
// parameters.
void RemixAndResample(const int16_t* src_data,
                      size_t samples_per_channel,
                      size_t num_channels,
                      int sample_rate_hz,
                      PushResampler<int16_t>* resampler,
                      AudioFrame* dst_frame);

}  // namespace voe
}  // namespace webrtc

#endif  // AUDIO_REMIX_RESAMPLE_H_
