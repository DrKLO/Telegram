/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/ilbc/audio_decoder_ilbc.h"

#include <memory>
#include <utility>

#include "modules/audio_coding/codecs/ilbc/ilbc.h"
#include "modules/audio_coding/codecs/legacy_encoded_audio_frame.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

AudioDecoderIlbcImpl::AudioDecoderIlbcImpl() {
  WebRtcIlbcfix_DecoderCreate(&dec_state_);
  WebRtcIlbcfix_Decoderinit30Ms(dec_state_);
}

AudioDecoderIlbcImpl::~AudioDecoderIlbcImpl() {
  WebRtcIlbcfix_DecoderFree(dec_state_);
}

bool AudioDecoderIlbcImpl::HasDecodePlc() const {
  return true;
}

int AudioDecoderIlbcImpl::DecodeInternal(const uint8_t* encoded,
                                         size_t encoded_len,
                                         int sample_rate_hz,
                                         int16_t* decoded,
                                         SpeechType* speech_type) {
  RTC_DCHECK_EQ(sample_rate_hz, 8000);
  int16_t temp_type = 1;  // Default is speech.
  int ret = WebRtcIlbcfix_Decode(dec_state_, encoded, encoded_len, decoded,
                                 &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

size_t AudioDecoderIlbcImpl::DecodePlc(size_t num_frames, int16_t* decoded) {
  return WebRtcIlbcfix_NetEqPlc(dec_state_, decoded, num_frames);
}

void AudioDecoderIlbcImpl::Reset() {
  WebRtcIlbcfix_Decoderinit30Ms(dec_state_);
}

std::vector<AudioDecoder::ParseResult> AudioDecoderIlbcImpl::ParsePayload(
    rtc::Buffer&& payload,
    uint32_t timestamp) {
  std::vector<ParseResult> results;
  size_t bytes_per_frame;
  int timestamps_per_frame;
  if (payload.size() >= 950) {
    RTC_LOG(LS_WARNING)
        << "AudioDecoderIlbcImpl::ParsePayload: Payload too large";
    return results;
  }
  if (payload.size() % 38 == 0) {
    // 20 ms frames.
    bytes_per_frame = 38;
    timestamps_per_frame = 160;
  } else if (payload.size() % 50 == 0) {
    // 30 ms frames.
    bytes_per_frame = 50;
    timestamps_per_frame = 240;
  } else {
    RTC_LOG(LS_WARNING)
        << "AudioDecoderIlbcImpl::ParsePayload: Invalid payload";
    return results;
  }

  RTC_DCHECK_EQ(0, payload.size() % bytes_per_frame);
  if (payload.size() == bytes_per_frame) {
    std::unique_ptr<EncodedAudioFrame> frame(
        new LegacyEncodedAudioFrame(this, std::move(payload)));
    results.emplace_back(timestamp, 0, std::move(frame));
  } else {
    size_t byte_offset;
    uint32_t timestamp_offset;
    for (byte_offset = 0, timestamp_offset = 0; byte_offset < payload.size();
         byte_offset += bytes_per_frame,
        timestamp_offset += timestamps_per_frame) {
      std::unique_ptr<EncodedAudioFrame> frame(new LegacyEncodedAudioFrame(
          this, rtc::Buffer(payload.data() + byte_offset, bytes_per_frame)));
      results.emplace_back(timestamp + timestamp_offset, 0, std::move(frame));
    }
  }

  return results;
}

int AudioDecoderIlbcImpl::SampleRateHz() const {
  return 8000;
}

size_t AudioDecoderIlbcImpl::Channels() const {
  return 1;
}

}  // namespace webrtc
