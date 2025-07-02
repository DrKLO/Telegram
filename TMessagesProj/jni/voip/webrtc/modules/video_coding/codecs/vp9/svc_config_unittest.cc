/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/vp9/svc_config.h"

#include <cstddef>
#include <vector>

#include "api/video_codecs/video_encoder.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::ElementsAre;
using ::testing::Field;

namespace webrtc {
TEST(SvcConfig, NumSpatialLayers) {
  const size_t max_num_spatial_layers = 6;
  const size_t first_active_layer = 0;
  const size_t num_spatial_layers = 2;

  std::vector<SpatialLayer> spatial_layers = GetSvcConfig(
      kMinVp9SpatialLayerLongSideLength << (num_spatial_layers - 1),
      kMinVp9SpatialLayerShortSideLength << (num_spatial_layers - 1), 30,
      first_active_layer, max_num_spatial_layers, 1, false);

  EXPECT_EQ(spatial_layers.size(), num_spatial_layers);
}

TEST(SvcConfig, NumSpatialLayersPortrait) {
  const size_t max_num_spatial_layers = 6;
  const size_t first_active_layer = 0;
  const size_t num_spatial_layers = 2;

  std::vector<SpatialLayer> spatial_layers = GetSvcConfig(
      kMinVp9SpatialLayerShortSideLength << (num_spatial_layers - 1),
      kMinVp9SpatialLayerLongSideLength << (num_spatial_layers - 1), 30,
      first_active_layer, max_num_spatial_layers, 1, false);

  EXPECT_EQ(spatial_layers.size(), num_spatial_layers);
}

TEST(SvcConfig, NumSpatialLayersWithScalabilityMode) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 960;
  codec.height = 540;
  codec.SetScalabilityMode(ScalabilityMode::kL3T3_KEY);

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers, ElementsAre(Field(&SpatialLayer::height, 135),
                                          Field(&SpatialLayer::height, 270),
                                          Field(&SpatialLayer::height, 540)));
  EXPECT_THAT(spatial_layers,
              ElementsAre(Field(&SpatialLayer::numberOfTemporalLayers, 3),
                          Field(&SpatialLayer::numberOfTemporalLayers, 3),
                          Field(&SpatialLayer::numberOfTemporalLayers, 3)));
  EXPECT_EQ(codec.GetScalabilityMode(), ScalabilityMode::kL3T3_KEY);
}

TEST(SvcConfig, UpdatesInterLayerPredModeBasedOnScalabilityMode) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 1280;
  codec.height = 720;
  codec.SetScalabilityMode(ScalabilityMode::kL3T3_KEY);

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_EQ(codec.VP9()->interLayerPred, InterLayerPredMode::kOnKeyPic);

  codec.SetScalabilityMode(ScalabilityMode::kL3T3);
  spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_EQ(codec.VP9()->interLayerPred, InterLayerPredMode::kOn);

  codec.SetScalabilityMode(ScalabilityMode::kS3T3);
  spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_EQ(codec.VP9()->interLayerPred, InterLayerPredMode::kOff);
}

TEST(SvcConfig, NumSpatialLayersLimitedWithScalabilityMode) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 480;
  codec.height = 270;
  codec.SetScalabilityMode(ScalabilityMode::kL3T3_KEY);

  // Scalability mode updated.
  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers, ElementsAre(Field(&SpatialLayer::height, 135),
                                          Field(&SpatialLayer::height, 270)));
  EXPECT_THAT(spatial_layers,
              ElementsAre(Field(&SpatialLayer::numberOfTemporalLayers, 3),
                          Field(&SpatialLayer::numberOfTemporalLayers, 3)));
  EXPECT_EQ(codec.GetScalabilityMode(), ScalabilityMode::kL2T3_KEY);
}

TEST(SvcConfig, NumSpatialLayersLimitedWithScalabilityModePortrait) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 270;
  codec.height = 480;
  codec.SetScalabilityMode(ScalabilityMode::kL3T1);

  // Scalability mode updated.
  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers, ElementsAre(Field(&SpatialLayer::width, 135),
                                          Field(&SpatialLayer::width, 270)));
  EXPECT_THAT(spatial_layers,
              ElementsAre(Field(&SpatialLayer::numberOfTemporalLayers, 1),
                          Field(&SpatialLayer::numberOfTemporalLayers, 1)));
  EXPECT_EQ(codec.GetScalabilityMode(), ScalabilityMode::kL2T1);
}

TEST(SvcConfig, NumSpatialLayersWithScalabilityModeResolutionRatio1_5) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 270;
  codec.height = 480;
  codec.SetScalabilityMode(ScalabilityMode::kL2T1h);  // 1.5:1

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers, ElementsAre(Field(&SpatialLayer::width, 180),
                                          Field(&SpatialLayer::width, 270)));
  EXPECT_THAT(spatial_layers,
              ElementsAre(Field(&SpatialLayer::numberOfTemporalLayers, 1),
                          Field(&SpatialLayer::numberOfTemporalLayers, 1)));
  EXPECT_EQ(codec.GetScalabilityMode(), ScalabilityMode::kL2T1h);
}

TEST(SvcConfig, NumSpatialLayersLimitedWithScalabilityModeResolutionRatio1_5) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 320;
  codec.height = 180;
  codec.SetScalabilityMode(ScalabilityMode::kL3T1h);  // 1.5:1

  // Scalability mode updated.
  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers, ElementsAre(Field(&SpatialLayer::width, 320)));
  EXPECT_THAT(spatial_layers,
              ElementsAre(Field(&SpatialLayer::numberOfTemporalLayers, 1)));
  EXPECT_EQ(codec.GetScalabilityMode(), ScalabilityMode::kL1T1);
}

TEST(SvcConfig, AlwaysSendsAtLeastOneLayer) {
  const size_t max_num_spatial_layers = 6;
  const size_t first_active_layer = 5;

  std::vector<SpatialLayer> spatial_layers = GetSvcConfig(
      kMinVp9SpatialLayerLongSideLength, kMinVp9SpatialLayerShortSideLength, 30,
      first_active_layer, max_num_spatial_layers, 1, false);
  EXPECT_EQ(spatial_layers.size(), 1u);
  EXPECT_EQ(spatial_layers.back().width, kMinVp9SpatialLayerLongSideLength);
}

TEST(SvcConfig, AlwaysSendsAtLeastOneLayerPortrait) {
  const size_t max_num_spatial_layers = 6;
  const size_t first_active_layer = 5;

  std::vector<SpatialLayer> spatial_layers = GetSvcConfig(
      kMinVp9SpatialLayerShortSideLength, kMinVp9SpatialLayerLongSideLength, 30,
      first_active_layer, max_num_spatial_layers, 1, false);
  EXPECT_EQ(spatial_layers.size(), 1u);
  EXPECT_EQ(spatial_layers.back().width, kMinVp9SpatialLayerShortSideLength);
}

TEST(SvcConfig, EnforcesMinimalRequiredParity) {
  const size_t max_num_spatial_layers = 3;
  const size_t kOddSize = 1023;

  std::vector<SpatialLayer> spatial_layers =
      GetSvcConfig(kOddSize, kOddSize, 30,
                   /*first_active_layer=*/1, max_num_spatial_layers, 1, false);
  // Since there are 2 layers total (1, 2), divisiblity by 2 is required.
  EXPECT_EQ(spatial_layers.back().width, kOddSize - 1);
  EXPECT_EQ(spatial_layers.back().width, kOddSize - 1);

  spatial_layers =
      GetSvcConfig(kOddSize, kOddSize, 30,
                   /*first_active_layer=*/0, max_num_spatial_layers, 1, false);
  // Since there are 3 layers total (0, 1, 2), divisiblity by 4 is required.
  EXPECT_EQ(spatial_layers.back().width, kOddSize - 3);
  EXPECT_EQ(spatial_layers.back().width, kOddSize - 3);

  spatial_layers =
      GetSvcConfig(kOddSize, kOddSize, 30,
                   /*first_active_layer=*/2, max_num_spatial_layers, 1, false);
  // Since there is only 1 layer active (2), divisiblity by 1 is required.
  EXPECT_EQ(spatial_layers.back().width, kOddSize);
  EXPECT_EQ(spatial_layers.back().width, kOddSize);
}

TEST(SvcConfig, EnforcesMinimalRequiredParityWithScalabilityMode) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 1023;
  codec.height = 1023;
  codec.SetScalabilityMode(ScalabilityMode::kL3T1);

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers,  // Divisiblity by 4 required.
              ElementsAre(Field(&SpatialLayer::width, 255),
                          Field(&SpatialLayer::width, 510),
                          Field(&SpatialLayer::width, 1020)));

  codec.SetScalabilityMode(ScalabilityMode::kL2T1);
  spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers,  // Divisiblity by 2 required.
              ElementsAre(Field(&SpatialLayer::width, 511),
                          Field(&SpatialLayer::width, 1022)));

  codec.SetScalabilityMode(ScalabilityMode::kL1T1);
  spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers,  // Divisiblity by 1 required.
              ElementsAre(Field(&SpatialLayer::width, 1023)));
}

TEST(SvcConfig, EnforcesMinimalRequiredParityWithScalabilityModeResRatio1_5) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 1280;
  codec.height = 1280;
  codec.SetScalabilityMode(ScalabilityMode::kL2T1h);  // 1.5:1

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers,  // Divisiblity by 3 required.
              ElementsAre(Field(&SpatialLayer::width, 852),
                          Field(&SpatialLayer::width, 1278)));
}

TEST(SvcConfig, SkipsInactiveLayers) {
  const size_t num_spatial_layers = 4;
  const size_t first_active_layer = 2;

  std::vector<SpatialLayer> spatial_layers = GetSvcConfig(
      kMinVp9SpatialLayerLongSideLength << (num_spatial_layers - 1),
      kMinVp9SpatialLayerShortSideLength << (num_spatial_layers - 1), 30,
      first_active_layer, num_spatial_layers, 1, false);
  EXPECT_EQ(spatial_layers.size(), 2u);
  EXPECT_EQ(spatial_layers.back().width,
            kMinVp9SpatialLayerLongSideLength << (num_spatial_layers - 1));
}

TEST(SvcConfig, BitrateThresholds) {
  const size_t first_active_layer = 0;
  const size_t num_spatial_layers = 3;
  std::vector<SpatialLayer> spatial_layers = GetSvcConfig(
      kMinVp9SpatialLayerLongSideLength << (num_spatial_layers - 1),
      kMinVp9SpatialLayerShortSideLength << (num_spatial_layers - 1), 30,
      first_active_layer, num_spatial_layers, 1, false);

  EXPECT_EQ(spatial_layers.size(), num_spatial_layers);

  for (const SpatialLayer& layer : spatial_layers) {
    EXPECT_LE(layer.minBitrate, layer.maxBitrate);
    EXPECT_LE(layer.minBitrate, layer.targetBitrate);
    EXPECT_LE(layer.targetBitrate, layer.maxBitrate);
  }
}

TEST(SvcConfig, BitrateThresholdsWithScalabilityMode) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.width = 960;
  codec.height = 540;
  codec.SetScalabilityMode(ScalabilityMode::kS3T3);

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_THAT(spatial_layers, ElementsAre(Field(&SpatialLayer::height, 135),
                                          Field(&SpatialLayer::height, 270),
                                          Field(&SpatialLayer::height, 540)));

  for (const SpatialLayer& layer : spatial_layers) {
    EXPECT_LE(layer.minBitrate, layer.maxBitrate);
    EXPECT_LE(layer.minBitrate, layer.targetBitrate);
    EXPECT_LE(layer.targetBitrate, layer.maxBitrate);
  }
}

TEST(SvcConfig, CopiesMinMaxBitrateForSingleSpatialLayer) {
  VideoCodec codec;
  codec.codecType = kVideoCodecVP9;
  codec.SetScalabilityMode(ScalabilityMode::kL1T3);
  codec.width = 1280;
  codec.height = 720;
  codec.minBitrate = 100;
  codec.maxBitrate = 500;

  std::vector<SpatialLayer> spatial_layers = GetVp9SvcConfig(codec);
  EXPECT_EQ(spatial_layers[0].minBitrate, 100u);
  EXPECT_EQ(spatial_layers[0].maxBitrate, 500u);
  EXPECT_LE(spatial_layers[0].targetBitrate, 500u);
}

TEST(SvcConfig, ScreenSharing) {
  std::vector<SpatialLayer> spatial_layers =
      GetSvcConfig(1920, 1080, 30, 1, 3, 3, true);

  EXPECT_EQ(spatial_layers.size(), 3UL);

  for (size_t i = 0; i < 3; ++i) {
    const SpatialLayer& layer = spatial_layers[i];
    EXPECT_EQ(layer.width, 1920);
    EXPECT_EQ(layer.height, 1080);
    EXPECT_EQ(layer.maxFramerate, (i < 1) ? 5 : (i < 2 ? 10 : 30));
    EXPECT_EQ(layer.numberOfTemporalLayers, 1);
    EXPECT_LE(layer.minBitrate, layer.maxBitrate);
    EXPECT_LE(layer.minBitrate, layer.targetBitrate);
    EXPECT_LE(layer.targetBitrate, layer.maxBitrate);
  }
}
}  // namespace webrtc
