/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/av1/av1_svc_config.h"

#include "api/video_codecs/video_codec.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {
constexpr int kDontCare = 0;

VideoCodec GetDefaultVideoCodec() {
  VideoCodec video_codec;
  video_codec.codecType = kVideoCodecAV1;
  video_codec.width = 1280;
  video_codec.height = 720;
  return video_codec;
}

TEST(Av1SvcConfigTest, TreatsEmptyAsL1T1) {
  VideoCodec video_codec = GetDefaultVideoCodec();

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_TRUE(video_codec.spatialLayers[0].active);
  EXPECT_EQ(video_codec.spatialLayers[0].numberOfTemporalLayers, 1);
  EXPECT_FALSE(video_codec.spatialLayers[1].active);
}

TEST(Av1SvcConfigTest, ScalabilityModeFromNumberOfTemporalLayers) {
  VideoCodec video_codec = GetDefaultVideoCodec();

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/3,
                              /*num_spatial_layers=*/1));
  EXPECT_EQ(video_codec.spatialLayers[0].numberOfTemporalLayers, 3);
}

TEST(Av1SvcConfigTest, ScalabilityModeFromNumberOfSpatialLayers) {
  VideoCodec video_codec = GetDefaultVideoCodec();

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/3,
                              /*num_spatial_layers=*/2));
  EXPECT_EQ(video_codec.spatialLayers[0].numberOfTemporalLayers, 3);
  EXPECT_TRUE(video_codec.spatialLayers[0].active);
  EXPECT_TRUE(video_codec.spatialLayers[1].active);
  EXPECT_FALSE(video_codec.spatialLayers[2].active);
}

TEST(Av1SvcConfigTest, SetsActiveSpatialLayersFromScalabilityMode) {
  VideoCodec video_codec = GetDefaultVideoCodec();
  video_codec.SetScalabilityMode(ScalabilityMode::kL2T1);

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_TRUE(video_codec.spatialLayers[0].active);
  EXPECT_TRUE(video_codec.spatialLayers[1].active);
  EXPECT_FALSE(video_codec.spatialLayers[2].active);
}

TEST(Av1SvcConfigTest, ConfiguresDobuleResolutionRatioFromScalabilityMode) {
  VideoCodec video_codec;
  video_codec.codecType = kVideoCodecAV1;
  video_codec.SetScalabilityMode(ScalabilityMode::kL2T1);
  video_codec.width = 1200;
  video_codec.height = 800;

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(video_codec.spatialLayers[0].width, 600);
  EXPECT_EQ(video_codec.spatialLayers[0].height, 400);
  EXPECT_EQ(video_codec.spatialLayers[1].width, 1200);
  EXPECT_EQ(video_codec.spatialLayers[1].height, 800);
}

TEST(Av1SvcConfigTest, ConfiguresSmallResolutionRatioFromScalabilityMode) {
  VideoCodec video_codec;
  video_codec.codecType = kVideoCodecAV1;
  // h mode uses 1.5:1 ratio
  video_codec.SetScalabilityMode(ScalabilityMode::kL2T1h);
  video_codec.width = 1500;
  video_codec.height = 900;

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(video_codec.spatialLayers[0].width, 1000);
  EXPECT_EQ(video_codec.spatialLayers[0].height, 600);
  EXPECT_EQ(video_codec.spatialLayers[1].width, 1500);
  EXPECT_EQ(video_codec.spatialLayers[1].height, 900);
}

TEST(Av1SvcConfigTest, CopiesFramrate) {
  VideoCodec video_codec = GetDefaultVideoCodec();
  video_codec.SetScalabilityMode(ScalabilityMode::kL2T1);
  video_codec.maxFramerate = 27;

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(video_codec.spatialLayers[0].maxFramerate, 27);
  EXPECT_EQ(video_codec.spatialLayers[1].maxFramerate, 27);
}

TEST(Av1SvcConfigTest, SetsNumberOfTemporalLayers) {
  VideoCodec video_codec = GetDefaultVideoCodec();
  video_codec.SetScalabilityMode(ScalabilityMode::kL1T3);

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(video_codec.spatialLayers[0].numberOfTemporalLayers, 3);
}

TEST(Av1SvcConfigTest, CopiesMinMaxBitrateForSingleSpatialLayer) {
  VideoCodec video_codec;
  video_codec.codecType = kVideoCodecAV1;
  video_codec.SetScalabilityMode(ScalabilityMode::kL1T3);
  video_codec.minBitrate = 100;
  video_codec.maxBitrate = 500;

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(video_codec.spatialLayers[0].minBitrate, 100u);
  EXPECT_EQ(video_codec.spatialLayers[0].maxBitrate, 500u);
  EXPECT_LE(video_codec.spatialLayers[0].minBitrate,
            video_codec.spatialLayers[0].targetBitrate);
  EXPECT_LE(video_codec.spatialLayers[0].targetBitrate,
            video_codec.spatialLayers[0].maxBitrate);
}

TEST(Av1SvcConfigTest, SetsBitratesForMultipleSpatialLayers) {
  VideoCodec video_codec;
  video_codec.codecType = kVideoCodecAV1;
  video_codec.width = 640;
  video_codec.height = 360;
  video_codec.SetScalabilityMode(ScalabilityMode::kL2T2);

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(video_codec.spatialLayers[0].minBitrate, 20u);
  EXPECT_EQ(video_codec.spatialLayers[0].maxBitrate, 142u);

  EXPECT_EQ(video_codec.spatialLayers[1].minBitrate, 135u);
  EXPECT_EQ(video_codec.spatialLayers[1].maxBitrate, 418u);
}

TEST(Av1SvcConfigTest, ReduceSpatialLayersOnInsufficentInputResolution) {
  VideoCodec video_codec = GetDefaultVideoCodec();
  video_codec.width = 640;
  video_codec.height = 360;
  video_codec.SetScalabilityMode(ScalabilityMode::kL3T3);

  EXPECT_TRUE(SetAv1SvcConfig(video_codec, /*num_temporal_layers=*/kDontCare,
                              /*num_spatial_layers=*/kDontCare));

  EXPECT_EQ(*video_codec.GetScalabilityMode(), ScalabilityMode::kL2T3);
}

}  // namespace
}  // namespace webrtc
