/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ISAC_FIX_TYPE_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ISAC_FIX_TYPE_H_

#include "modules/audio_coding/codecs/isac/fix/include/isacfix.h"
#include "rtc_base/checks.h"

namespace webrtc {

class IsacFix {
 public:
  using instance_type = ISACFIX_MainStruct;
  static const bool has_swb = false;
  static inline int16_t Control(instance_type* inst,
                                int32_t rate,
                                int framesize) {
    return WebRtcIsacfix_Control(inst, rate, framesize);
  }
  static inline int16_t ControlBwe(instance_type* inst,
                                   int32_t rate_bps,
                                   int frame_size_ms,
                                   int16_t enforce_frame_size) {
    return WebRtcIsacfix_ControlBwe(inst, rate_bps, frame_size_ms,
                                    enforce_frame_size);
  }
  static inline int16_t Create(instance_type** inst) {
    return WebRtcIsacfix_Create(inst);
  }
  static inline int DecodeInternal(instance_type* inst,
                                   const uint8_t* encoded,
                                   size_t len,
                                   int16_t* decoded,
                                   int16_t* speech_type) {
    return WebRtcIsacfix_Decode(inst, encoded, len, decoded, speech_type);
  }
  static inline size_t DecodePlc(instance_type* inst,
                                 int16_t* decoded,
                                 size_t num_lost_frames) {
    return WebRtcIsacfix_DecodePlc(inst, decoded, num_lost_frames);
  }
  static inline void DecoderInit(instance_type* inst) {
    WebRtcIsacfix_DecoderInit(inst);
  }
  static inline int Encode(instance_type* inst,
                           const int16_t* speech_in,
                           uint8_t* encoded) {
    return WebRtcIsacfix_Encode(inst, speech_in, encoded);
  }
  static inline int16_t EncoderInit(instance_type* inst, int16_t coding_mode) {
    return WebRtcIsacfix_EncoderInit(inst, coding_mode);
  }
  static inline uint16_t EncSampRate(instance_type* inst) {
    return kFixSampleRate;
  }

  static inline int16_t Free(instance_type* inst) {
    return WebRtcIsacfix_Free(inst);
  }
  static inline int16_t GetErrorCode(instance_type* inst) {
    return WebRtcIsacfix_GetErrorCode(inst);
  }

  static inline int16_t GetNewFrameLen(instance_type* inst) {
    return WebRtcIsacfix_GetNewFrameLen(inst);
  }
  static inline int16_t SetDecSampRate(instance_type* inst,
                                       uint16_t sample_rate_hz) {
    RTC_DCHECK_EQ(sample_rate_hz, kFixSampleRate);
    return 0;
  }
  static inline int16_t SetEncSampRate(instance_type* inst,
                                       uint16_t sample_rate_hz) {
    RTC_DCHECK_EQ(sample_rate_hz, kFixSampleRate);
    return 0;
  }
  static inline void SetEncSampRateInDecoder(instance_type* inst,
                                             uint16_t sample_rate_hz) {
    RTC_DCHECK_EQ(sample_rate_hz, kFixSampleRate);
  }
  static inline void SetInitialBweBottleneck(instance_type* inst,
                                             int bottleneck_bits_per_second) {
    WebRtcIsacfix_SetInitialBweBottleneck(inst, bottleneck_bits_per_second);
  }
  static inline int16_t SetMaxPayloadSize(instance_type* inst,
                                          int16_t max_payload_size_bytes) {
    return WebRtcIsacfix_SetMaxPayloadSize(inst, max_payload_size_bytes);
  }
  static inline int16_t SetMaxRate(instance_type* inst, int32_t max_bit_rate) {
    return WebRtcIsacfix_SetMaxRate(inst, max_bit_rate);
  }

 private:
  enum { kFixSampleRate = 16000 };
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ISAC_FIX_TYPE_H_
