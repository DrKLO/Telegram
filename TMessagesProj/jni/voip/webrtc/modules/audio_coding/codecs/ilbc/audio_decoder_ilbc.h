/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_DECODER_ILBC_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_DECODER_ILBC_H_

#include <stddef.h>
#include <stdint.h>

#include <vector>

#include "api/audio_codecs/audio_decoder.h"
#include "rtc_base/buffer.h"
#include "rtc_base/constructor_magic.h"

typedef struct iLBC_decinst_t_ IlbcDecoderInstance;

namespace webrtc {

class AudioDecoderIlbcImpl final : public AudioDecoder {
 public:
  AudioDecoderIlbcImpl();
  ~AudioDecoderIlbcImpl() override;
  bool HasDecodePlc() const override;
  size_t DecodePlc(size_t num_frames, int16_t* decoded) override;
  void Reset() override;
  std::vector<ParseResult> ParsePayload(rtc::Buffer&& payload,
                                        uint32_t timestamp) override;
  int SampleRateHz() const override;
  size_t Channels() const override;

 protected:
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override;

 private:
  IlbcDecoderInstance* dec_state_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioDecoderIlbcImpl);
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_DECODER_ILBC_H_
