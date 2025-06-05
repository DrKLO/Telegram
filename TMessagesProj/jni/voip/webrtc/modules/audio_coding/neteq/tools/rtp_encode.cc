/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>

#ifdef WIN32
#include <winsock2.h>
#endif
#if defined(WEBRTC_LINUX) || defined(WEBRTC_FUCHSIA)
#include <netinet/in.h>
#endif

#include <iostream>
#include <map>
#include <string>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/memory/memory.h"
#include "api/audio/audio_frame.h"
#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/audio_codecs/g711/audio_encoder_g711.h"
#include "api/audio_codecs/g722/audio_encoder_g722.h"
#include "api/audio_codecs/ilbc/audio_encoder_ilbc.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "modules/audio_coding/codecs/cng/audio_encoder_cng.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "modules/audio_coding/neteq/tools/input_audio_file.h"
#include "rtc_base/numerics/safe_conversions.h"

ABSL_FLAG(bool, list_codecs, false, "Enumerate all codecs");
ABSL_FLAG(std::string, codec, "opus", "Codec to use");
ABSL_FLAG(int,
          frame_len,
          0,
          "Frame length in ms; 0 indicates codec default value");
ABSL_FLAG(int, bitrate, 0, "Bitrate in bps; 0 indicates codec default value");
ABSL_FLAG(int,
          payload_type,
          -1,
          "RTP payload type; -1 indicates codec default value");
ABSL_FLAG(int,
          cng_payload_type,
          -1,
          "RTP payload type for CNG; -1 indicates default value");
ABSL_FLAG(int, ssrc, 0, "SSRC to write to the RTP header");
ABSL_FLAG(bool, dtx, false, "Use DTX/CNG");
ABSL_FLAG(int, sample_rate, 48000, "Sample rate of the input file");
ABSL_FLAG(bool, fec, false, "Use Opus FEC");
ABSL_FLAG(int, expected_loss, 0, "Expected packet loss percentage");

namespace webrtc {
namespace test {
namespace {

// Add new codecs here, and to the map below.
enum class CodecType {
  kOpus,
  kPcmU,
  kPcmA,
  kG722,
  kPcm16b8,
  kPcm16b16,
  kPcm16b32,
  kPcm16b48,
  kIlbc,
};

struct CodecTypeAndInfo {
  CodecType type;
  int default_payload_type;
  bool internal_dtx;
};

// List all supported codecs here. This map defines the command-line parameter
// value (the key string) for selecting each codec, together with information
// whether it is using internal or external DTX/CNG.
const std::map<std::string, CodecTypeAndInfo>& CodecList() {
  static const auto* const codec_list =
      new std::map<std::string, CodecTypeAndInfo>{
          {"opus", {CodecType::kOpus, 111, true}},
          {"pcmu", {CodecType::kPcmU, 0, false}},
          {"pcma", {CodecType::kPcmA, 8, false}},
          {"g722", {CodecType::kG722, 9, false}},
          {"pcm16b_8", {CodecType::kPcm16b8, 93, false}},
          {"pcm16b_16", {CodecType::kPcm16b16, 94, false}},
          {"pcm16b_32", {CodecType::kPcm16b32, 95, false}},
          {"pcm16b_48", {CodecType::kPcm16b48, 96, false}},
          {"ilbc", {CodecType::kIlbc, 102, false}}};
  return *codec_list;
}

// This class will receive callbacks from ACM when a packet is ready, and write
// it to the output file.
class Packetizer : public AudioPacketizationCallback {
 public:
  Packetizer(FILE* out_file, uint32_t ssrc, int timestamp_rate_hz)
      : out_file_(out_file),
        ssrc_(ssrc),
        timestamp_rate_hz_(timestamp_rate_hz) {}

  int32_t SendData(AudioFrameType frame_type,
                   uint8_t payload_type,
                   uint32_t timestamp,
                   const uint8_t* payload_data,
                   size_t payload_len_bytes,
                   int64_t absolute_capture_timestamp_ms) override {
    if (payload_len_bytes == 0) {
      return 0;
    }

    constexpr size_t kRtpHeaderLength = 12;
    constexpr size_t kRtpDumpHeaderLength = 8;
    const uint16_t length = htons(rtc::checked_cast<uint16_t>(
        kRtpHeaderLength + kRtpDumpHeaderLength + payload_len_bytes));
    const uint16_t plen = htons(
        rtc::checked_cast<uint16_t>(kRtpHeaderLength + payload_len_bytes));
    const uint32_t offset = htonl(timestamp / (timestamp_rate_hz_ / 1000));
    RTC_CHECK_EQ(fwrite(&length, sizeof(uint16_t), 1, out_file_), 1);
    RTC_CHECK_EQ(fwrite(&plen, sizeof(uint16_t), 1, out_file_), 1);
    RTC_CHECK_EQ(fwrite(&offset, sizeof(uint32_t), 1, out_file_), 1);

    const uint8_t rtp_header[] = {0x80,
                                  static_cast<uint8_t>(payload_type & 0x7F),
                                  static_cast<uint8_t>(sequence_number_ >> 8),
                                  static_cast<uint8_t>(sequence_number_),
                                  static_cast<uint8_t>(timestamp >> 24),
                                  static_cast<uint8_t>(timestamp >> 16),
                                  static_cast<uint8_t>(timestamp >> 8),
                                  static_cast<uint8_t>(timestamp),
                                  static_cast<uint8_t>(ssrc_ >> 24),
                                  static_cast<uint8_t>(ssrc_ >> 16),
                                  static_cast<uint8_t>(ssrc_ >> 8),
                                  static_cast<uint8_t>(ssrc_)};
    static_assert(sizeof(rtp_header) == kRtpHeaderLength, "");
    RTC_CHECK_EQ(
        fwrite(rtp_header, sizeof(uint8_t), kRtpHeaderLength, out_file_),
        kRtpHeaderLength);
    ++sequence_number_;  // Intended to wrap on overflow.

    RTC_CHECK_EQ(
        fwrite(payload_data, sizeof(uint8_t), payload_len_bytes, out_file_),
        payload_len_bytes);

    return 0;
  }

 private:
  FILE* const out_file_;
  const uint32_t ssrc_;
  const int timestamp_rate_hz_;
  uint16_t sequence_number_ = 0;
};

void SetFrameLenIfFlagIsPositive(int* config_frame_len) {
  if (absl::GetFlag(FLAGS_frame_len) > 0) {
    *config_frame_len = absl::GetFlag(FLAGS_frame_len);
  }
}

template <typename T>
typename T::Config GetCodecConfig() {
  typename T::Config config;
  SetFrameLenIfFlagIsPositive(&config.frame_size_ms);
  RTC_CHECK(config.IsOk());
  return config;
}

AudioEncoderL16::Config Pcm16bConfig(CodecType codec_type) {
  auto config = GetCodecConfig<AudioEncoderL16>();
  switch (codec_type) {
    case CodecType::kPcm16b8:
      config.sample_rate_hz = 8000;
      return config;
    case CodecType::kPcm16b16:
      config.sample_rate_hz = 16000;
      return config;
    case CodecType::kPcm16b32:
      config.sample_rate_hz = 32000;
      return config;
    case CodecType::kPcm16b48:
      config.sample_rate_hz = 48000;
      return config;
    default:
      RTC_DCHECK_NOTREACHED();
      return config;
  }
}

std::unique_ptr<AudioEncoder> CreateEncoder(CodecType codec_type,
                                            int payload_type) {
  switch (codec_type) {
    case CodecType::kOpus: {
      AudioEncoderOpus::Config config = GetCodecConfig<AudioEncoderOpus>();
      if (absl::GetFlag(FLAGS_bitrate) > 0) {
        config.bitrate_bps = absl::GetFlag(FLAGS_bitrate);
      }
      config.dtx_enabled = absl::GetFlag(FLAGS_dtx);
      config.fec_enabled = absl::GetFlag(FLAGS_fec);
      RTC_CHECK(config.IsOk());
      return AudioEncoderOpus::MakeAudioEncoder(config, payload_type);
    }

    case CodecType::kPcmU:
    case CodecType::kPcmA: {
      AudioEncoderG711::Config config = GetCodecConfig<AudioEncoderG711>();
      config.type = codec_type == CodecType::kPcmU
                        ? AudioEncoderG711::Config::Type::kPcmU
                        : AudioEncoderG711::Config::Type::kPcmA;
      RTC_CHECK(config.IsOk());
      return AudioEncoderG711::MakeAudioEncoder(config, payload_type);
    }

    case CodecType::kG722: {
      return AudioEncoderG722::MakeAudioEncoder(
          GetCodecConfig<AudioEncoderG722>(), payload_type);
    }

    case CodecType::kPcm16b8:
    case CodecType::kPcm16b16:
    case CodecType::kPcm16b32:
    case CodecType::kPcm16b48: {
      return AudioEncoderL16::MakeAudioEncoder(Pcm16bConfig(codec_type),
                                               payload_type);
    }

    case CodecType::kIlbc: {
      return AudioEncoderIlbc::MakeAudioEncoder(
          GetCodecConfig<AudioEncoderIlbc>(), payload_type);
    }
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

AudioEncoderCngConfig GetCngConfig(int sample_rate_hz) {
  AudioEncoderCngConfig cng_config;
  const auto default_payload_type = [&] {
    switch (sample_rate_hz) {
      case 8000:
        return 13;
      case 16000:
        return 98;
      case 32000:
        return 99;
      case 48000:
        return 100;
      default:
        RTC_DCHECK_NOTREACHED();
    }
    return 0;
  };
  cng_config.payload_type = absl::GetFlag(FLAGS_cng_payload_type) != -1
                                ? absl::GetFlag(FLAGS_cng_payload_type)
                                : default_payload_type();
  return cng_config;
}

int RunRtpEncode(int argc, char* argv[]) {
  std::vector<char*> args = absl::ParseCommandLine(argc, argv);
  const std::string usage =
      "Tool for generating an RTP dump file from audio input.\n"
      "Example usage:\n"
      "./rtp_encode input.pcm output.rtp --codec=[codec] "
      "--frame_len=[frame_len] --bitrate=[bitrate]\n\n";
  if (!absl::GetFlag(FLAGS_list_codecs) && args.size() != 3) {
    printf("%s", usage.c_str());
    return 1;
  }

  if (absl::GetFlag(FLAGS_list_codecs)) {
    printf("The following arguments are valid --codec parameters:\n");
    for (const auto& c : CodecList()) {
      printf("  %s\n", c.first.c_str());
    }
    return 0;
  }

  const auto codec_it = CodecList().find(absl::GetFlag(FLAGS_codec));
  if (codec_it == CodecList().end()) {
    printf("%s is not a valid codec name.\n",
           absl::GetFlag(FLAGS_codec).c_str());
    printf("Use argument --list_codecs to see all valid codec names.\n");
    return 1;
  }

  // Create the codec.
  const int payload_type = absl::GetFlag(FLAGS_payload_type) == -1
                               ? codec_it->second.default_payload_type
                               : absl::GetFlag(FLAGS_payload_type);
  std::unique_ptr<AudioEncoder> codec =
      CreateEncoder(codec_it->second.type, payload_type);

  // Create an external VAD/CNG encoder if needed.
  if (absl::GetFlag(FLAGS_dtx) && !codec_it->second.internal_dtx) {
    AudioEncoderCngConfig cng_config = GetCngConfig(codec->SampleRateHz());
    RTC_DCHECK(codec);
    cng_config.speech_encoder = std::move(codec);
    codec = CreateComfortNoiseEncoder(std::move(cng_config));
  }
  RTC_DCHECK(codec);

  // Set up ACM.
  const int timestamp_rate_hz = codec->RtpTimestampRateHz();
  auto acm(AudioCodingModule::Create());
  acm->SetEncoder(std::move(codec));
  acm->SetPacketLossRate(absl::GetFlag(FLAGS_expected_loss));

  // Open files.
  printf("Input file: %s\n", args[1]);
  InputAudioFile input_file(args[1], false);  // Open input in non-looping mode.
  FILE* out_file = fopen(args[2], "wb");
  RTC_CHECK(out_file) << "Could not open file " << args[2] << " for writing";
  printf("Output file: %s\n", args[2]);
  fprintf(out_file, "#!rtpplay1.0 \n");  //,
  // Write 3 32-bit values followed by 2 16-bit values, all set to 0. This means
  // a total of 16 bytes.
  const uint8_t file_header[16] = {0};
  RTC_CHECK_EQ(fwrite(file_header, sizeof(file_header), 1, out_file), 1);

  // Create and register the packetizer, which will write the packets to file.
  Packetizer packetizer(out_file, absl::GetFlag(FLAGS_ssrc), timestamp_rate_hz);
  RTC_DCHECK_EQ(acm->RegisterTransportCallback(&packetizer), 0);

  AudioFrame audio_frame;
  audio_frame.samples_per_channel_ =
      absl::GetFlag(FLAGS_sample_rate) / 100;  // 10 ms
  audio_frame.sample_rate_hz_ = absl::GetFlag(FLAGS_sample_rate);
  audio_frame.num_channels_ = 1;

  while (input_file.Read(audio_frame.samples_per_channel_,
                         audio_frame.mutable_data())) {
    RTC_CHECK_GE(acm->Add10MsData(audio_frame), 0);
    audio_frame.timestamp_ += audio_frame.samples_per_channel_;
  }

  return 0;
}

}  // namespace
}  // namespace test
}  // namespace webrtc

int main(int argc, char* argv[]) {
  return webrtc::test::RunRtpEncode(argc, argv);
}
