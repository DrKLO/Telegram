/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#include "modules/video_coding/codecs/h264/h264_encoder_impl.h"

#include "api/video_codecs/video_encoder.h"
#include "test/gtest.h"

namespace webrtc {

namespace {

const int kMaxPayloadSize = 1024;
const int kNumCores = 1;

const VideoEncoder::Capabilities kCapabilities(false);
const VideoEncoder::Settings kSettings(kCapabilities,
                                       kNumCores,
                                       kMaxPayloadSize);

void SetDefaultSettings(VideoCodec* codec_settings) {
  codec_settings->codecType = kVideoCodecH264;
  codec_settings->maxFramerate = 60;
  codec_settings->width = 640;
  codec_settings->height = 480;
  // If frame dropping is false, we get a warning that bitrate can't
  // be controlled for RC_QUALITY_MODE; RC_BITRATE_MODE and RC_TIMESTAMP_MODE
  codec_settings->SetFrameDropEnabled(true);
  codec_settings->startBitrate = 2000;
  codec_settings->maxBitrate = 4000;
}

TEST(H264EncoderImplTest, CanInitializeWithDefaultParameters) {
  H264EncoderImpl encoder(cricket::CreateVideoCodec("H264"));
  VideoCodec codec_settings;
  SetDefaultSettings(&codec_settings);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(H264PacketizationMode::NonInterleaved,
            encoder.PacketizationModeForTesting());
}

TEST(H264EncoderImplTest, CanInitializeWithNonInterleavedModeExplicitly) {
  cricket::VideoCodec codec = cricket::CreateVideoCodec("H264");
  codec.SetParam(cricket::kH264FmtpPacketizationMode, "1");
  H264EncoderImpl encoder(codec);
  VideoCodec codec_settings;
  SetDefaultSettings(&codec_settings);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(H264PacketizationMode::NonInterleaved,
            encoder.PacketizationModeForTesting());
}

TEST(H264EncoderImplTest, CanInitializeWithSingleNalUnitModeExplicitly) {
  cricket::VideoCodec codec = cricket::CreateVideoCodec("H264");
  codec.SetParam(cricket::kH264FmtpPacketizationMode, "0");
  H264EncoderImpl encoder(codec);
  VideoCodec codec_settings;
  SetDefaultSettings(&codec_settings);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(H264PacketizationMode::SingleNalUnit,
            encoder.PacketizationModeForTesting());
}

TEST(H264EncoderImplTest, CanInitializeWithRemovedParameter) {
  cricket::VideoCodec codec = cricket::CreateVideoCodec("H264");
  codec.RemoveParam(cricket::kH264FmtpPacketizationMode);
  H264EncoderImpl encoder(codec);
  VideoCodec codec_settings;
  SetDefaultSettings(&codec_settings);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(H264PacketizationMode::SingleNalUnit,
            encoder.PacketizationModeForTesting());
}

}  // anonymous namespace

}  // namespace webrtc
