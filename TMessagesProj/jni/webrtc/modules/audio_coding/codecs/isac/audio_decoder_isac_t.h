/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_H_

#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_decoder.h"
#include "api/scoped_refptr.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

template <typename T>
class AudioDecoderIsacT final : public AudioDecoder {
 public:
  struct Config {
    bool IsOk() const;
    int sample_rate_hz = 16000;
  };
  explicit AudioDecoderIsacT(const Config& config);
  virtual ~AudioDecoderIsacT() override;

  bool HasDecodePlc() const override;
  size_t DecodePlc(size_t num_frames, int16_t* decoded) override;
  void Reset() override;
  int ErrorCode() override;
  int SampleRateHz() const override;
  size_t Channels() const override;
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override;

 private:
  typename T::instance_type* isac_state_;
  int sample_rate_hz_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AudioDecoderIsacT);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_H_
