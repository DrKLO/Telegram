/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/audio_decoder_opus.h"

#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_coding/codecs/opus/audio_coder_opus_common.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

AudioDecoderOpusImpl::AudioDecoderOpusImpl(size_t num_channels,
                                           int sample_rate_hz)
    : channels_(num_channels),
      sample_rate_hz_(sample_rate_hz),
      generate_plc_(field_trial::IsEnabled("WebRTC-Audio-OpusGeneratePlc")) {
  RTC_DCHECK(num_channels == 1 || num_channels == 2);
  RTC_DCHECK(sample_rate_hz == 16000 || sample_rate_hz == 48000);
  const int error =
      WebRtcOpus_DecoderCreate(&dec_state_, channels_, sample_rate_hz_);
  RTC_DCHECK(error == 0);
  WebRtcOpus_DecoderInit(dec_state_);
}

AudioDecoderOpusImpl::~AudioDecoderOpusImpl() {
  WebRtcOpus_DecoderFree(dec_state_);
}

std::vector<AudioDecoder::ParseResult> AudioDecoderOpusImpl::ParsePayload(
    rtc::Buffer&& payload,
    uint32_t timestamp) {
  std::vector<ParseResult> results;

  if (PacketHasFec(payload.data(), payload.size())) {
    const int duration =
        PacketDurationRedundant(payload.data(), payload.size());
    RTC_DCHECK_GE(duration, 0);
    rtc::Buffer payload_copy(payload.data(), payload.size());
    std::unique_ptr<EncodedAudioFrame> fec_frame(
        new OpusFrame(this, std::move(payload_copy), false));
    results.emplace_back(timestamp - duration, 1, std::move(fec_frame));
  }
  std::unique_ptr<EncodedAudioFrame> frame(
      new OpusFrame(this, std::move(payload), true));
  results.emplace_back(timestamp, 0, std::move(frame));
  return results;
}

int AudioDecoderOpusImpl::DecodeInternal(const uint8_t* encoded,
                                         size_t encoded_len,
                                         int sample_rate_hz,
                                         int16_t* decoded,
                                         SpeechType* speech_type) {
  RTC_DCHECK_EQ(sample_rate_hz, sample_rate_hz_);
  int16_t temp_type = 1;  // Default is speech.
  int ret =
      WebRtcOpus_Decode(dec_state_, encoded, encoded_len, decoded, &temp_type);
  if (ret > 0)
    ret *= static_cast<int>(channels_);  // Return total number of samples.
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

int AudioDecoderOpusImpl::DecodeRedundantInternal(const uint8_t* encoded,
                                                  size_t encoded_len,
                                                  int sample_rate_hz,
                                                  int16_t* decoded,
                                                  SpeechType* speech_type) {
  if (!PacketHasFec(encoded, encoded_len)) {
    // This packet is a RED packet.
    return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                          speech_type);
  }

  RTC_DCHECK_EQ(sample_rate_hz, sample_rate_hz_);
  int16_t temp_type = 1;  // Default is speech.
  int ret = WebRtcOpus_DecodeFec(dec_state_, encoded, encoded_len, decoded,
                                 &temp_type);
  if (ret > 0)
    ret *= static_cast<int>(channels_);  // Return total number of samples.
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

void AudioDecoderOpusImpl::Reset() {
  WebRtcOpus_DecoderInit(dec_state_);
}

int AudioDecoderOpusImpl::PacketDuration(const uint8_t* encoded,
                                         size_t encoded_len) const {
  return WebRtcOpus_DurationEst(dec_state_, encoded, encoded_len);
}

int AudioDecoderOpusImpl::PacketDurationRedundant(const uint8_t* encoded,
                                                  size_t encoded_len) const {
  if (!PacketHasFec(encoded, encoded_len)) {
    // This packet is a RED packet.
    return PacketDuration(encoded, encoded_len);
  }

  return WebRtcOpus_FecDurationEst(encoded, encoded_len, sample_rate_hz_);
}

bool AudioDecoderOpusImpl::PacketHasFec(const uint8_t* encoded,
                                        size_t encoded_len) const {
  int fec;
  fec = WebRtcOpus_PacketHasFec(encoded, encoded_len);
  return (fec == 1);
}

int AudioDecoderOpusImpl::SampleRateHz() const {
  return sample_rate_hz_;
}

size_t AudioDecoderOpusImpl::Channels() const {
  return channels_;
}

void AudioDecoderOpusImpl::GeneratePlc(
    size_t requested_samples_per_channel,
    rtc::BufferT<int16_t>* concealment_audio) {
  if (!generate_plc_) {
    return;
  }
  int plc_size = WebRtcOpus_PlcDuration(dec_state_) * channels_;
  concealment_audio->AppendData(plc_size, [&](rtc::ArrayView<int16_t> decoded) {
    int16_t temp_type = 1;
    int ret =
        WebRtcOpus_Decode(dec_state_, nullptr, 0, decoded.data(), &temp_type);
    if (ret < 0) {
      return 0;
    }
    return ret;
  });
}

}  // namespace webrtc
