/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <vector>

#include "api/test/create_videocodec_test_fixture.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/test/videocodec_test_fixture_impl.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace test {

namespace {
// Codec settings.
const int kCifWidth = 352;
const int kCifHeight = 288;
const int kNumFrames = 100;

VideoCodecTestFixture::Config CreateConfig() {
  VideoCodecTestFixture::Config config;
  config.filename = "foreman_cif";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = kNumFrames;
  // Only allow encoder/decoder to use single core, for predictability.
  config.use_single_core = true;
  return config;
}
}  // namespace

TEST(VideoCodecTestOpenH264, ConstantHighBitrate) {
  auto frame_checker =
      std::make_unique<VideoCodecTestFixtureImpl::H264KeyframeChecker>();
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kH264CodecName, 1, 1, 1, false, true, false,
                          kCifWidth, kCifHeight);
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 0.1, 0.2, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{37, 35, 0.93, 0.91}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

// H264: Enable SingleNalUnit packetization mode. Encoder should split
// large frames into multiple slices and limit length of NAL units.
TEST(VideoCodecTestOpenH264, SingleNalUnit) {
  auto frame_checker =
      std::make_unique<VideoCodecTestFixtureImpl::H264KeyframeChecker>();
  auto config = CreateConfig();
  config.h264_codec_settings.packetization_mode =
      H264PacketizationMode::SingleNalUnit;
  config.max_payload_size_bytes = 500;
  config.SetCodecSettings(cricket::kH264CodecName, 1, 1, 1, false, true, false,
                          kCifWidth, kCifHeight);
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 0.1, 0.2, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{37, 35, 0.93, 0.91}};

  BitstreamThresholds bs_thresholds = {config.max_payload_size_bytes};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds,
                   &bs_thresholds);
}

}  // namespace test
}  // namespace webrtc
