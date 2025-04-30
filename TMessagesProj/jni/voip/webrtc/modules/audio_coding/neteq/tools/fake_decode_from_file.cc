/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/fake_decode_from_file.h"

#include "modules/rtp_rtcp/source/byte_io.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace test {

namespace {

class FakeEncodedFrame : public AudioDecoder::EncodedAudioFrame {
 public:
  FakeEncodedFrame(FakeDecodeFromFile* decoder,
                   uint32_t timestamp,
                   size_t duration,
                   bool is_dtx)
      : decoder_(decoder),
        timestamp_(timestamp),
        duration_(duration),
        is_dtx_(is_dtx) {}

  size_t Duration() const override { return duration_; }

  absl::optional<DecodeResult> Decode(
      rtc::ArrayView<int16_t> decoded) const override {
    if (is_dtx_) {
      std::fill_n(decoded.data(), duration_, 0);
      return DecodeResult{duration_, AudioDecoder::kComfortNoise};
    }

    decoder_->ReadFromFile(timestamp_, duration_, decoded.data());
    return DecodeResult{Duration(), AudioDecoder::kSpeech};
  }

  bool IsDtxPacket() const override { return is_dtx_; }

 private:
  FakeDecodeFromFile* const decoder_;
  const uint32_t timestamp_;
  const size_t duration_;
  const bool is_dtx_;
};

}  // namespace

void FakeDecodeFromFile::ReadFromFile(uint32_t timestamp,
                                      size_t samples,
                                      int16_t* destination) {
  if (next_timestamp_from_input_ && timestamp != *next_timestamp_from_input_) {
    // A gap in the timestamp sequence is detected. Skip the same number of
    // samples from the file.
    uint32_t jump = timestamp - *next_timestamp_from_input_;
    RTC_CHECK(input_->Seek(jump));
  }

  next_timestamp_from_input_ = timestamp + samples;
  RTC_CHECK(input_->Read(static_cast<size_t>(samples), destination));

  if (stereo_) {
    InputAudioFile::DuplicateInterleaved(destination, samples, 2, destination);
  }
}

int FakeDecodeFromFile::DecodeInternal(const uint8_t* encoded,
                                       size_t encoded_len,
                                       int sample_rate_hz,
                                       int16_t* decoded,
                                       SpeechType* speech_type) {
  // This call is only used to produce codec-internal comfort noise.
  RTC_DCHECK_EQ(sample_rate_hz, SampleRateHz());
  RTC_DCHECK_EQ(encoded_len, 0);
  RTC_DCHECK(!encoded);  // NetEq always sends nullptr in this case.

  const int samples_to_decode = rtc::CheckedDivExact(SampleRateHz(), 100);
  const int total_samples_to_decode = samples_to_decode * (stereo_ ? 2 : 1);
  std::fill_n(decoded, total_samples_to_decode, 0);
  *speech_type = kComfortNoise;
  return rtc::dchecked_cast<int>(total_samples_to_decode);
}

void FakeDecodeFromFile::PrepareEncoded(uint32_t timestamp,
                                        size_t samples,
                                        size_t original_payload_size_bytes,
                                        rtc::ArrayView<uint8_t> encoded) {
  RTC_CHECK_GE(encoded.size(), 12);
  ByteWriter<uint32_t>::WriteLittleEndian(&encoded[0], timestamp);
  ByteWriter<uint32_t>::WriteLittleEndian(&encoded[4],
                                          rtc::checked_cast<uint32_t>(samples));
  ByteWriter<uint32_t>::WriteLittleEndian(
      &encoded[8], rtc::checked_cast<uint32_t>(original_payload_size_bytes));
}

std::vector<AudioDecoder::ParseResult> FakeDecodeFromFile::ParsePayload(
    rtc::Buffer&& payload,
    uint32_t timestamp) {
  RTC_CHECK_GE(payload.size(), 12);
  // Parse payload encoded in PrepareEncoded.
  RTC_CHECK_EQ(timestamp, ByteReader<uint32_t>::ReadLittleEndian(&payload[0]));
  size_t samples = ByteReader<uint32_t>::ReadLittleEndian(&payload[4]);
  size_t original_payload_size_bytes =
      ByteReader<uint32_t>::ReadLittleEndian(&payload[8]);
  bool opus_dtx = original_payload_size_bytes <= 2;
  std::vector<ParseResult> results;
  results.emplace_back(
      timestamp, 0,
      std::make_unique<FakeEncodedFrame>(this, timestamp, samples, opus_dtx));
  return results;
}

}  // namespace test
}  // namespace webrtc
