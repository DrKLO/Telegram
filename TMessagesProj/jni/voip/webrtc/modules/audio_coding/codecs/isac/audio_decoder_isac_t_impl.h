/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_IMPL_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_IMPL_H_

#include "rtc_base/checks.h"

namespace webrtc {

template <typename T>
bool AudioDecoderIsacT<T>::Config::IsOk() const {
  return (sample_rate_hz == 16000 || sample_rate_hz == 32000);
}

template <typename T>
AudioDecoderIsacT<T>::AudioDecoderIsacT(const Config& config)
    : sample_rate_hz_(config.sample_rate_hz) {
  RTC_CHECK(config.IsOk()) << "Unsupported sample rate "
                           << config.sample_rate_hz;
  RTC_CHECK_EQ(0, T::Create(&isac_state_));
  T::DecoderInit(isac_state_);
  RTC_CHECK_EQ(0, T::SetDecSampRate(isac_state_, sample_rate_hz_));
}

template <typename T>
AudioDecoderIsacT<T>::~AudioDecoderIsacT() {
  RTC_CHECK_EQ(0, T::Free(isac_state_));
}

template <typename T>
int AudioDecoderIsacT<T>::DecodeInternal(const uint8_t* encoded,
                                         size_t encoded_len,
                                         int sample_rate_hz,
                                         int16_t* decoded,
                                         SpeechType* speech_type) {
  RTC_CHECK_EQ(sample_rate_hz_, sample_rate_hz);
  int16_t temp_type = 1;  // Default is speech.
  int ret =
      T::DecodeInternal(isac_state_, encoded, encoded_len, decoded, &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

template <typename T>
bool AudioDecoderIsacT<T>::HasDecodePlc() const {
  return false;
}

template <typename T>
size_t AudioDecoderIsacT<T>::DecodePlc(size_t num_frames, int16_t* decoded) {
  return T::DecodePlc(isac_state_, decoded, num_frames);
}

template <typename T>
void AudioDecoderIsacT<T>::Reset() {
  T::DecoderInit(isac_state_);
}

template <typename T>
int AudioDecoderIsacT<T>::ErrorCode() {
  return T::GetErrorCode(isac_state_);
}

template <typename T>
int AudioDecoderIsacT<T>::SampleRateHz() const {
  return sample_rate_hz_;
}

template <typename T>
size_t AudioDecoderIsacT<T>::Channels() const {
  return 1;
}

}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_IMPL_H_
