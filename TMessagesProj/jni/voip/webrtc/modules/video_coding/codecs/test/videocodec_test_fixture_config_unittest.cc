/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>

#include "api/test/videocodec_test_fixture.h"
#include "api/video_codecs/video_codec.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/video_codec_settings.h"

using ::testing::ElementsAre;

namespace webrtc {
namespace test {

using Config = VideoCodecTestFixture::Config;

namespace {
const size_t kNumTemporalLayers = 2;
}  // namespace

TEST(Config, NumberOfCoresWithUseSingleCore) {
  Config config;
  config.use_single_core = true;
  EXPECT_EQ(1u, config.NumberOfCores());
}

TEST(Config, NumberOfCoresWithoutUseSingleCore) {
  Config config;
  config.use_single_core = false;
  EXPECT_GE(config.NumberOfCores(), 1u);
}

TEST(Config, NumberOfTemporalLayersIsOne) {
  Config config;
  webrtc::test::CodecSettings(kVideoCodecH264, &config.codec_settings);
  EXPECT_EQ(1u, config.NumberOfTemporalLayers());
}

TEST(Config, NumberOfTemporalLayers_Vp8) {
  Config config;
  webrtc::test::CodecSettings(kVideoCodecVP8, &config.codec_settings);
  config.codec_settings.VP8()->numberOfTemporalLayers = kNumTemporalLayers;
  EXPECT_EQ(kNumTemporalLayers, config.NumberOfTemporalLayers());
}

TEST(Config, NumberOfTemporalLayers_Vp9) {
  Config config;
  webrtc::test::CodecSettings(kVideoCodecVP9, &config.codec_settings);
  config.codec_settings.VP9()->numberOfTemporalLayers = kNumTemporalLayers;
  EXPECT_EQ(kNumTemporalLayers, config.NumberOfTemporalLayers());
}

}  // namespace test
}  // namespace webrtc
