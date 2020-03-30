/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_DOWN_SAMPLER_H_
#define MODULES_AUDIO_PROCESSING_AGC2_DOWN_SAMPLER_H_

#include "api/array_view.h"
#include "modules/audio_processing/agc2/biquad_filter.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class ApmDataDumper;

class DownSampler {
 public:
  explicit DownSampler(ApmDataDumper* data_dumper);
  void Initialize(int sample_rate_hz);

  void DownSample(rtc::ArrayView<const float> in, rtc::ArrayView<float> out);

 private:
  ApmDataDumper* data_dumper_;
  int sample_rate_hz_;
  int down_sampling_factor_;
  BiQuadFilter low_pass_filter_;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(DownSampler);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_DOWN_SAMPLER_H_
