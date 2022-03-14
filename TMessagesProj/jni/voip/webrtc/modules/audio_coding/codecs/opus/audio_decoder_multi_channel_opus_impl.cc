/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/audio_decoder_multi_channel_opus_impl.h"

#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "modules/audio_coding/codecs/opus/audio_coder_opus_common.h"
#include "rtc_base/string_to_number.h"

namespace webrtc {

std::unique_ptr<AudioDecoderMultiChannelOpusImpl>
AudioDecoderMultiChannelOpusImpl::MakeAudioDecoder(
    AudioDecoderMultiChannelOpusConfig config) {
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }
  // Fill the pointer with a working decoder through the C interface. This
  // allocates memory.
  OpusDecInst* dec_state = nullptr;
  const int error = WebRtcOpus_MultistreamDecoderCreate(
      &dec_state, config.num_channels, config.num_streams,
      config.coupled_streams, config.channel_mapping.data());
  if (error != 0) {
    return nullptr;
  }

  // Pass the ownership to DecoderImpl. Not using 'make_unique' because the
  // c-tor is private.
  return std::unique_ptr<AudioDecoderMultiChannelOpusImpl>(
      new AudioDecoderMultiChannelOpusImpl(dec_state, config));
}

AudioDecoderMultiChannelOpusImpl::AudioDecoderMultiChannelOpusImpl(
    OpusDecInst* dec_state,
    AudioDecoderMultiChannelOpusConfig config)
    : dec_state_(dec_state), config_(config) {
  RTC_DCHECK(dec_state);
  WebRtcOpus_DecoderInit(dec_state_);
}

AudioDecoderMultiChannelOpusImpl::~AudioDecoderMultiChannelOpusImpl() {
  WebRtcOpus_DecoderFree(dec_state_);
}

absl::optional<AudioDecoderMultiChannelOpusConfig>
AudioDecoderMultiChannelOpusImpl::SdpToConfig(const SdpAudioFormat& format) {
  AudioDecoderMultiChannelOpusConfig config;
  config.num_channels = format.num_channels;
  auto num_streams = GetFormatParameter<int>(format, "num_streams");
  if (!num_streams.has_value()) {
    return absl::nullopt;
  }
  config.num_streams = *num_streams;

  auto coupled_streams = GetFormatParameter<int>(format, "coupled_streams");
  if (!coupled_streams.has_value()) {
    return absl::nullopt;
  }
  config.coupled_streams = *coupled_streams;

  auto channel_mapping =
      GetFormatParameter<std::vector<unsigned char>>(format, "channel_mapping");
  if (!channel_mapping.has_value()) {
    return absl::nullopt;
  }
  config.channel_mapping = *channel_mapping;
  if (!config.IsOk()) {
    return absl::nullopt;
  }
  return config;
}

std::vector<AudioDecoder::ParseResult>
AudioDecoderMultiChannelOpusImpl::ParsePayload(rtc::Buffer&& payload,
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

int AudioDecoderMultiChannelOpusImpl::DecodeInternal(const uint8_t* encoded,
                                                     size_t encoded_len,
                                                     int sample_rate_hz,
                                                     int16_t* decoded,
                                                     SpeechType* speech_type) {
  RTC_DCHECK_EQ(sample_rate_hz, 48000);
  int16_t temp_type = 1;  // Default is speech.
  int ret =
      WebRtcOpus_Decode(dec_state_, encoded, encoded_len, decoded, &temp_type);
  if (ret > 0)
    ret *= static_cast<int>(
        config_.num_channels);  // Return total number of samples.
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

int AudioDecoderMultiChannelOpusImpl::DecodeRedundantInternal(
    const uint8_t* encoded,
    size_t encoded_len,
    int sample_rate_hz,
    int16_t* decoded,
    SpeechType* speech_type) {
  if (!PacketHasFec(encoded, encoded_len)) {
    // This packet is a RED packet.
    return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                          speech_type);
  }

  RTC_DCHECK_EQ(sample_rate_hz, 48000);
  int16_t temp_type = 1;  // Default is speech.
  int ret = WebRtcOpus_DecodeFec(dec_state_, encoded, encoded_len, decoded,
                                 &temp_type);
  if (ret > 0)
    ret *= static_cast<int>(
        config_.num_channels);  // Return total number of samples.
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

void AudioDecoderMultiChannelOpusImpl::Reset() {
  WebRtcOpus_DecoderInit(dec_state_);
}

int AudioDecoderMultiChannelOpusImpl::PacketDuration(const uint8_t* encoded,
                                                     size_t encoded_len) const {
  return WebRtcOpus_DurationEst(dec_state_, encoded, encoded_len);
}

int AudioDecoderMultiChannelOpusImpl::PacketDurationRedundant(
    const uint8_t* encoded,
    size_t encoded_len) const {
  if (!PacketHasFec(encoded, encoded_len)) {
    // This packet is a RED packet.
    return PacketDuration(encoded, encoded_len);
  }

  return WebRtcOpus_FecDurationEst(encoded, encoded_len, 48000);
}

bool AudioDecoderMultiChannelOpusImpl::PacketHasFec(const uint8_t* encoded,
                                                    size_t encoded_len) const {
  int fec;
  fec = WebRtcOpus_PacketHasFec(encoded, encoded_len);
  return (fec == 1);
}

int AudioDecoderMultiChannelOpusImpl::SampleRateHz() const {
  return 48000;
}

size_t AudioDecoderMultiChannelOpusImpl::Channels() const {
  return config_.num_channels;
}

}  // namespace webrtc
