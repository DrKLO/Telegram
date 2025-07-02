/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/test/metrics/global_metrics_logger_and_exporter.h"
#include "api/test/metrics/metric.h"
#include "modules/audio_coding/neteq/tools/audio_loop.h"
#include "rtc_base/time_utils.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace {

using ::webrtc::test::GetGlobalMetricsLogger;
using ::webrtc::test::ImprovementDirection;
using ::webrtc::test::Unit;

int64_t RunComplexityTest(const AudioEncoderOpusConfig& config) {
  // Create encoder.
  constexpr int payload_type = 17;
  const auto encoder = AudioEncoderOpus::MakeAudioEncoder(config, payload_type);
  // Open speech file.
  const std::string kInputFileName =
      webrtc::test::ResourcePath("audio_coding/speech_mono_32_48kHz", "pcm");
  test::AudioLoop audio_loop;
  constexpr int kSampleRateHz = 48000;
  EXPECT_EQ(kSampleRateHz, encoder->SampleRateHz());
  constexpr size_t kMaxLoopLengthSamples =
      kSampleRateHz * 10;  // 10 second loop.
  constexpr size_t kInputBlockSizeSamples =
      10 * kSampleRateHz / 1000;  // 60 ms.
  EXPECT_TRUE(audio_loop.Init(kInputFileName, kMaxLoopLengthSamples,
                              kInputBlockSizeSamples));
  // Encode.
  const int64_t start_time_ms = rtc::TimeMillis();
  AudioEncoder::EncodedInfo info;
  rtc::Buffer encoded(500);
  uint32_t rtp_timestamp = 0u;
  for (size_t i = 0; i < 10000; ++i) {
    encoded.Clear();
    info = encoder->Encode(rtp_timestamp, audio_loop.GetNextBlock(), &encoded);
    rtp_timestamp += kInputBlockSizeSamples;
  }
  return rtc::TimeMillis() - start_time_ms;
}

// This test encodes an audio file using Opus twice with different bitrates
// (~11 kbps and 15.5 kbps). The runtime for each is measured, and the ratio
// between the two is calculated and tracked. This test explicitly sets the
// low_rate_complexity to 9. When running on desktop platforms, this is the same
// as the regular complexity, and the expectation is that the resulting ratio
// should be less than 100% (since the encoder runs faster at lower bitrates,
// given a fixed complexity setting). On the other hand, when running on
// mobiles, the regular complexity is 5, and we expect the resulting ratio to
// be higher, since we have explicitly asked for a higher complexity setting at
// the lower rate.
TEST(AudioEncoderOpusComplexityAdaptationTest, Adaptation_On) {
  // Create config.
  AudioEncoderOpusConfig config;
  // The limit -- including the hysteresis window -- at which the complexity
  // shuold be increased.
  config.bitrate_bps = 11000 - 1;
  config.low_rate_complexity = 9;
  int64_t runtime_10999bps = RunComplexityTest(config);

  config.bitrate_bps = 15500;
  int64_t runtime_15500bps = RunComplexityTest(config);

  GetGlobalMetricsLogger()->LogSingleValueMetric(
      "opus_encoding_complexity_ratio", "adaptation_on",
      100.0 * runtime_10999bps / runtime_15500bps, Unit::kPercent,
      ImprovementDirection::kNeitherIsBetter);
}

// This test is identical to the one above, but without the complexity
// adaptation enabled (neither on desktop, nor on mobile). The expectation is
// that the resulting ratio is less than 100% at all times.
TEST(AudioEncoderOpusComplexityAdaptationTest, Adaptation_Off) {
  // Create config.
  AudioEncoderOpusConfig config;
  // The limit -- including the hysteresis window -- at which the complexity
  // shuold be increased (but not in this test since complexity adaptation is
  // disabled).
  config.bitrate_bps = 11000 - 1;
  int64_t runtime_10999bps = RunComplexityTest(config);

  config.bitrate_bps = 15500;
  int64_t runtime_15500bps = RunComplexityTest(config);

  GetGlobalMetricsLogger()->LogSingleValueMetric(
      "opus_encoding_complexity_ratio", "adaptation_off",
      100.0 * runtime_10999bps / runtime_15500bps, Unit::kPercent,
      ImprovementDirection::kNeitherIsBetter);
}

}  // namespace
}  // namespace webrtc
