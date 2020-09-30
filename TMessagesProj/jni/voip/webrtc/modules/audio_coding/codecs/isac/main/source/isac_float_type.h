/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ISAC_FLOAT_TYPE_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ISAC_FLOAT_TYPE_H_

#include "modules/audio_coding/codecs/isac/main/include/isac.h"

namespace webrtc {

struct IsacFloat {
  using instance_type = ISACStruct;
  static const bool has_swb = true;
  static inline int16_t Control(instance_type* inst,
                                int32_t rate,
                                int framesize) {
    return WebRtcIsac_Control(inst, rate, framesize);
  }
  static inline int16_t ControlBwe(instance_type* inst,
                                   int32_t rate_bps,
                                   int frame_size_ms,
                                   int16_t enforce_frame_size) {
    return WebRtcIsac_ControlBwe(inst, rate_bps, frame_size_ms,
                                 enforce_frame_size);
  }
  static inline int16_t Create(instance_type** inst) {
    return WebRtcIsac_Create(inst);
  }
  static inline int DecodeInternal(instance_type* inst,
                                   const uint8_t* encoded,
                                   size_t len,
                                   int16_t* decoded,
                                   int16_t* speech_type) {
    return WebRtcIsac_Decode(inst, encoded, len, decoded, speech_type);
  }
  static inline size_t DecodePlc(instance_type* inst,
                                 int16_t* decoded,
                                 size_t num_lost_frames) {
    return WebRtcIsac_DecodePlc(inst, decoded, num_lost_frames);
  }

  static inline void DecoderInit(instance_type* inst) {
    WebRtcIsac_DecoderInit(inst);
  }
  static inline int Encode(instance_type* inst,
                           const int16_t* speech_in,
                           uint8_t* encoded) {
    return WebRtcIsac_Encode(inst, speech_in, encoded);
  }
  static inline int16_t EncoderInit(instance_type* inst, int16_t coding_mode) {
    return WebRtcIsac_EncoderInit(inst, coding_mode);
  }
  static inline uint16_t EncSampRate(instance_type* inst) {
    return WebRtcIsac_EncSampRate(inst);
  }

  static inline int16_t Free(instance_type* inst) {
    return WebRtcIsac_Free(inst);
  }
  static inline int16_t GetErrorCode(instance_type* inst) {
    return WebRtcIsac_GetErrorCode(inst);
  }

  static inline int16_t GetNewFrameLen(instance_type* inst) {
    return WebRtcIsac_GetNewFrameLen(inst);
  }
  static inline int16_t SetDecSampRate(instance_type* inst,
                                       uint16_t sample_rate_hz) {
    return WebRtcIsac_SetDecSampRate(inst, sample_rate_hz);
  }
  static inline int16_t SetEncSampRate(instance_type* inst,
                                       uint16_t sample_rate_hz) {
    return WebRtcIsac_SetEncSampRate(inst, sample_rate_hz);
  }
  static inline void SetEncSampRateInDecoder(instance_type* inst,
                                             uint16_t sample_rate_hz) {
    WebRtcIsac_SetEncSampRateInDecoder(inst, sample_rate_hz);
  }
  static inline void SetInitialBweBottleneck(instance_type* inst,
                                             int bottleneck_bits_per_second) {
    WebRtcIsac_SetInitialBweBottleneck(inst, bottleneck_bits_per_second);
  }
  static inline int16_t SetMaxPayloadSize(instance_type* inst,
                                          int16_t max_payload_size_bytes) {
    return WebRtcIsac_SetMaxPayloadSize(inst, max_payload_size_bytes);
  }
  static inline int16_t SetMaxRate(instance_type* inst, int32_t max_bit_rate) {
    return WebRtcIsac_SetMaxRate(inst, max_bit_rate);
  }
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_ISAC_FLOAT_TYPE_H_
