/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/audio_decoder.h"

#include <assert.h>

#include <memory>
#include <utility>

#include "api/array_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/sanitizer.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {

class OldStyleEncodedFrame final : public AudioDecoder::EncodedAudioFrame {
 public:
  OldStyleEncodedFrame(AudioDecoder* decoder, rtc::Buffer&& payload)
      : decoder_(decoder), payload_(std::move(payload)) {}

  size_t Duration() const override {
    const int ret = decoder_->PacketDuration(payload_.data(), payload_.size());
    return ret < 0 ? 0 : static_cast<size_t>(ret);
  }

  absl::optional<DecodeResult> Decode(
      rtc::ArrayView<int16_t> decoded) const override {
    auto speech_type = AudioDecoder::kSpeech;
    const int ret = decoder_->Decode(
        payload_.data(), payload_.size(), decoder_->SampleRateHz(),
        decoded.size() * sizeof(int16_t), decoded.data(), &speech_type);
    return ret < 0 ? absl::nullopt
                   : absl::optional<DecodeResult>(
                         {static_cast<size_t>(ret), speech_type});
  }

 private:
  AudioDecoder* const decoder_;
  const rtc::Buffer payload_;
};

}  // namespace

bool AudioDecoder::EncodedAudioFrame::IsDtxPacket() const {
  return false;
}

AudioDecoder::ParseResult::ParseResult() = default;
AudioDecoder::ParseResult::ParseResult(ParseResult&& b) = default;
AudioDecoder::ParseResult::ParseResult(uint32_t timestamp,
                                       int priority,
                                       std::unique_ptr<EncodedAudioFrame> frame)
    : timestamp(timestamp), priority(priority), frame(std::move(frame)) {
  RTC_DCHECK_GE(priority, 0);
}

AudioDecoder::ParseResult::~ParseResult() = default;

AudioDecoder::ParseResult& AudioDecoder::ParseResult::operator=(
    ParseResult&& b) = default;

std::vector<AudioDecoder::ParseResult> AudioDecoder::ParsePayload(
    rtc::Buffer&& payload,
    uint32_t timestamp) {
  std::vector<ParseResult> results;
  std::unique_ptr<EncodedAudioFrame> frame(
      new OldStyleEncodedFrame(this, std::move(payload)));
  results.emplace_back(timestamp, 0, std::move(frame));
  return results;
}

int AudioDecoder::Decode(const uint8_t* encoded,
                         size_t encoded_len,
                         int sample_rate_hz,
                         size_t max_decoded_bytes,
                         int16_t* decoded,
                         SpeechType* speech_type) {
  TRACE_EVENT0("webrtc", "AudioDecoder::Decode");
  rtc::MsanCheckInitialized(rtc::MakeArrayView(encoded, encoded_len));
  int duration = PacketDuration(encoded, encoded_len);
  if (duration >= 0 &&
      duration * Channels() * sizeof(int16_t) > max_decoded_bytes) {
    return -1;
  }
  return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                        speech_type);
}

int AudioDecoder::DecodeRedundant(const uint8_t* encoded,
                                  size_t encoded_len,
                                  int sample_rate_hz,
                                  size_t max_decoded_bytes,
                                  int16_t* decoded,
                                  SpeechType* speech_type) {
  TRACE_EVENT0("webrtc", "AudioDecoder::DecodeRedundant");
  rtc::MsanCheckInitialized(rtc::MakeArrayView(encoded, encoded_len));
  int duration = PacketDurationRedundant(encoded, encoded_len);
  if (duration >= 0 &&
      duration * Channels() * sizeof(int16_t) > max_decoded_bytes) {
    return -1;
  }
  return DecodeRedundantInternal(encoded, encoded_len, sample_rate_hz, decoded,
                                 speech_type);
}

int AudioDecoder::DecodeRedundantInternal(const uint8_t* encoded,
                                          size_t encoded_len,
                                          int sample_rate_hz,
                                          int16_t* decoded,
                                          SpeechType* speech_type) {
  return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                        speech_type);
}

bool AudioDecoder::HasDecodePlc() const {
  return false;
}

size_t AudioDecoder::DecodePlc(size_t num_frames, int16_t* decoded) {
  return 0;
}

// TODO(bugs.webrtc.org/9676): Remove default implementation.
void AudioDecoder::GeneratePlc(size_t /*requested_samples_per_channel*/,
                               rtc::BufferT<int16_t>* /*concealment_audio*/) {}

int AudioDecoder::ErrorCode() {
  return 0;
}

int AudioDecoder::PacketDuration(const uint8_t* encoded,
                                 size_t encoded_len) const {
  return kNotImplemented;
}

int AudioDecoder::PacketDurationRedundant(const uint8_t* encoded,
                                          size_t encoded_len) const {
  return kNotImplemented;
}

bool AudioDecoder::PacketHasFec(const uint8_t* encoded,
                                size_t encoded_len) const {
  return false;
}

AudioDecoder::SpeechType AudioDecoder::ConvertSpeechType(int16_t type) {
  switch (type) {
    case 0:  // TODO(hlundin): Both iSAC and Opus return 0 for speech.
    case 1:
      return kSpeech;
    case 2:
      return kComfortNoise;
    default:
      assert(false);
      return kSpeech;
  }
}

}  // namespace webrtc
