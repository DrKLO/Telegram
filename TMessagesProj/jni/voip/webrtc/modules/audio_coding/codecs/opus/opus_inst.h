/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_OPUS_OPUS_INST_H_
#define MODULES_AUDIO_CODING_CODECS_OPUS_OPUS_INST_H_

#include <stddef.h>

#include "rtc_base/ignore_wundef.h"

RTC_PUSH_IGNORING_WUNDEF()
#include "opus.h"
#include "opus_multistream.h"
RTC_POP_IGNORING_WUNDEF()

struct WebRtcOpusEncInst {
  OpusEncoder* encoder;
  OpusMSEncoder* multistream_encoder;
  size_t channels;
  int in_dtx_mode;
  bool avoid_noise_pumping_during_dtx;
  int sample_rate_hz;
  float smooth_energy_non_active_frames;
};

struct WebRtcOpusDecInst {
  OpusDecoder* decoder;
  OpusMSDecoder* multistream_decoder;
  int prev_decoded_samples;
  bool plc_use_prev_decoded_samples;
  size_t channels;
  int in_dtx_mode;
  int sample_rate_hz;
};

#endif  // MODULES_AUDIO_CODING_CODECS_OPUS_OPUS_INST_H_
