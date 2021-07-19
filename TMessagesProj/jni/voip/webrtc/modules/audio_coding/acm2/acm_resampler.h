/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_ACM2_ACM_RESAMPLER_H_
#define MODULES_AUDIO_CODING_ACM2_ACM_RESAMPLER_H_

#include <stddef.h>
#include <stdint.h>

#include "common_audio/resampler/include/push_resampler.h"

namespace webrtc {
namespace acm2 {

class ACMResampler {
 public:
  ACMResampler();
  ~ACMResampler();

  int Resample10Msec(const int16_t* in_audio,
                     int in_freq_hz,
                     int out_freq_hz,
                     size_t num_audio_channels,
                     size_t out_capacity_samples,
                     int16_t* out_audio);

 private:
  PushResampler<int16_t> resampler_;
};

}  // namespace acm2
}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_ACM2_ACM_RESAMPLER_H_
