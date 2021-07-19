/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/g711/audio_decoder_pcm.h"

#include <utility>

#include "modules/audio_coding/codecs/g711/g711_interface.h"
#include "modules/audio_coding/codecs/legacy_encoded_audio_frame.h"

namespace webrtc {

void AudioDecoderPcmU::Reset() {}

std::vector<AudioDecoder::ParseResult> AudioDecoderPcmU::ParsePayload(
    rtc::Buffer&& payload,
    uint32_t timestamp) {
  return LegacyEncodedAudioFrame::SplitBySamples(
      this, std::move(payload), timestamp, 8 * num_channels_, 8);
}

int AudioDecoderPcmU::SampleRateHz() const {
  return 8000;
}

size_t AudioDecoderPcmU::Channels() const {
  return num_channels_;
}

int AudioDecoderPcmU::DecodeInternal(const uint8_t* encoded,
                                     size_t encoded_len,
                                     int sample_rate_hz,
                                     int16_t* decoded,
                                     SpeechType* speech_type) {
  RTC_DCHECK_EQ(SampleRateHz(), sample_rate_hz);
  int16_t temp_type = 1;  // Default is speech.
  size_t ret = WebRtcG711_DecodeU(encoded, encoded_len, decoded, &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return static_cast<int>(ret);
}

int AudioDecoderPcmU::PacketDuration(const uint8_t* encoded,
                                     size_t encoded_len) const {
  // One encoded byte per sample per channel.
  return static_cast<int>(encoded_len / Channels());
}

void AudioDecoderPcmA::Reset() {}

std::vector<AudioDecoder::ParseResult> AudioDecoderPcmA::ParsePayload(
    rtc::Buffer&& payload,
    uint32_t timestamp) {
  return LegacyEncodedAudioFrame::SplitBySamples(
      this, std::move(payload), timestamp, 8 * num_channels_, 8);
}

int AudioDecoderPcmA::SampleRateHz() const {
  return 8000;
}

size_t AudioDecoderPcmA::Channels() const {
  return num_channels_;
}

int AudioDecoderPcmA::DecodeInternal(const uint8_t* encoded,
                                     size_t encoded_len,
                                     int sample_rate_hz,
                                     int16_t* decoded,
                                     SpeechType* speech_type) {
  RTC_DCHECK_EQ(SampleRateHz(), sample_rate_hz);
  int16_t temp_type = 1;  // Default is speech.
  size_t ret = WebRtcG711_DecodeA(encoded, encoded_len, decoded, &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return static_cast<int>(ret);
}

int AudioDecoderPcmA::PacketDuration(const uint8_t* encoded,
                                     size_t encoded_len) const {
  // One encoded byte per sample per channel.
  return static_cast<int>(encoded_len / Channels());
}

}  // namespace webrtc
