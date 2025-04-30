/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/mock_video_encoder.h"
#include "api/video_codecs/video_encoder_factory_template.h"
#include "api/video_codecs/video_encoder_factory_template_libaom_av1_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_open_h264_adapter.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::Contains;
using ::testing::Each;
using ::testing::Eq;
using ::testing::Field;
using ::testing::IsEmpty;
using ::testing::IsNull;
using ::testing::Not;
using ::testing::NotNull;
using ::testing::UnorderedElementsAre;

namespace webrtc {
namespace {
using CodecSupport = VideoEncoderFactory::CodecSupport;
const SdpVideoFormat kFooSdp("Foo");
const SdpVideoFormat kBarLowSdp("Bar", {{"profile", "low"}});
const SdpVideoFormat kBarHighSdp("Bar", {{"profile", "high"}});

struct FooEncoderTemplateAdapter {
  static std::vector<SdpVideoFormat> SupportedFormats() { return {kFooSdp}; }

  static std::unique_ptr<VideoEncoder> CreateEncoder(
      const SdpVideoFormat& format) {
    return std::make_unique<testing::StrictMock<MockVideoEncoder>>();
  }

  static bool IsScalabilityModeSupported(ScalabilityMode scalability_mode) {
    return scalability_mode == ScalabilityMode::kL1T2 ||
           scalability_mode == ScalabilityMode::kL1T3;
  }
};

struct BarEncoderTemplateAdapter {
  static std::vector<SdpVideoFormat> SupportedFormats() {
    return {kBarLowSdp, kBarHighSdp};
  }

  static std::unique_ptr<VideoEncoder> CreateEncoder(
      const SdpVideoFormat& format) {
    return std::make_unique<testing::StrictMock<MockVideoEncoder>>();
  }

  static bool IsScalabilityModeSupported(ScalabilityMode scalability_mode) {
    return scalability_mode == ScalabilityMode::kL1T2 ||
           scalability_mode == ScalabilityMode::kL1T3 ||
           scalability_mode == ScalabilityMode::kS2T1 ||
           scalability_mode == ScalabilityMode::kS3T3;
  }
};

TEST(VideoEncoderFactoryTemplate, OneTemplateAdapterCreateEncoder) {
  VideoEncoderFactoryTemplate<FooEncoderTemplateAdapter> factory;
  EXPECT_THAT(factory.GetSupportedFormats(), UnorderedElementsAre(kFooSdp));
  EXPECT_THAT(factory.CreateVideoEncoder(kFooSdp), NotNull());
  EXPECT_THAT(factory.CreateVideoEncoder(SdpVideoFormat("FooX")), IsNull());
}

TEST(VideoEncoderFactoryTemplate, OneTemplateAdapterCodecSupport) {
  VideoEncoderFactoryTemplate<FooEncoderTemplateAdapter> factory;
  EXPECT_THAT(factory.QueryCodecSupport(kFooSdp, absl::nullopt),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kFooSdp, "L1T2"),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kFooSdp, "S3T3"),
              Field(&CodecSupport::is_supported, false));
  EXPECT_THAT(factory.QueryCodecSupport(SdpVideoFormat("FooX"), absl::nullopt),
              Field(&CodecSupport::is_supported, false));
}

TEST(VideoEncoderFactoryTemplate, TwoTemplateAdaptersNoDuplicates) {
  VideoEncoderFactoryTemplate<FooEncoderTemplateAdapter,
                              FooEncoderTemplateAdapter>
      factory;
  EXPECT_THAT(factory.GetSupportedFormats(), UnorderedElementsAre(kFooSdp));
}

TEST(VideoEncoderFactoryTemplate, TwoTemplateAdaptersCreateEncoders) {
  VideoEncoderFactoryTemplate<FooEncoderTemplateAdapter,
                              BarEncoderTemplateAdapter>
      factory;
  EXPECT_THAT(factory.GetSupportedFormats(),
              UnorderedElementsAre(kFooSdp, kBarLowSdp, kBarHighSdp));
  EXPECT_THAT(factory.CreateVideoEncoder(kFooSdp), NotNull());
  EXPECT_THAT(factory.CreateVideoEncoder(kBarLowSdp), NotNull());
  EXPECT_THAT(factory.CreateVideoEncoder(kBarHighSdp), NotNull());
  EXPECT_THAT(factory.CreateVideoEncoder(SdpVideoFormat("FooX")), IsNull());
  EXPECT_THAT(factory.CreateVideoEncoder(SdpVideoFormat("Bar")), NotNull());
}

TEST(VideoEncoderFactoryTemplate, TwoTemplateAdaptersCodecSupport) {
  VideoEncoderFactoryTemplate<FooEncoderTemplateAdapter,
                              BarEncoderTemplateAdapter>
      factory;
  EXPECT_THAT(factory.QueryCodecSupport(kFooSdp, absl::nullopt),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kFooSdp, "L1T2"),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kFooSdp, "S3T3"),
              Field(&CodecSupport::is_supported, false));
  EXPECT_THAT(factory.QueryCodecSupport(kBarLowSdp, absl::nullopt),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kBarHighSdp, absl::nullopt),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kBarLowSdp, "S2T1"),
              Field(&CodecSupport::is_supported, true));
  EXPECT_THAT(factory.QueryCodecSupport(kBarHighSdp, "S3T2"),
              Field(&CodecSupport::is_supported, false));
}

TEST(VideoEncoderFactoryTemplate, LibvpxVp8) {
  VideoEncoderFactoryTemplate<LibvpxVp8EncoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats.size(), 1);
  EXPECT_THAT(formats[0], Field(&SdpVideoFormat::name, "VP8"));
  EXPECT_THAT(formats[0], Field(&SdpVideoFormat::scalability_modes,
                                Contains(ScalabilityMode::kL1T3)));
  EXPECT_THAT(factory.CreateVideoEncoder(formats[0]), NotNull());
}

TEST(VideoEncoderFactoryTemplate, LibvpxVp9) {
  VideoEncoderFactoryTemplate<LibvpxVp9EncoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats, Not(IsEmpty()));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::name, "VP9")));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::scalability_modes,
                                  Contains(ScalabilityMode::kL3T3_KEY))));
  EXPECT_THAT(factory.CreateVideoEncoder(formats[0]), NotNull());
}

// TODO(bugs.webrtc.org/13573): When OpenH264 is no longer a conditional build
//                              target remove this #ifdef.
#if defined(WEBRTC_USE_H264)
TEST(VideoEncoderFactoryTemplate, OpenH264) {
  VideoEncoderFactoryTemplate<OpenH264EncoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats, Not(IsEmpty()));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::name, "H264")));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::scalability_modes,
                                  Contains(ScalabilityMode::kL1T3))));
  EXPECT_THAT(factory.CreateVideoEncoder(formats[0]), NotNull());
}
#endif  // defined(WEBRTC_USE_H264)

TEST(VideoEncoderFactoryTemplate, LibaomAv1) {
  VideoEncoderFactoryTemplate<LibaomAv1EncoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats.size(), 1);
  EXPECT_THAT(formats[0], Field(&SdpVideoFormat::name, "AV1"));
  EXPECT_THAT(formats[0], Field(&SdpVideoFormat::scalability_modes,
                                Contains(ScalabilityMode::kL3T3_KEY)));
  EXPECT_THAT(factory.CreateVideoEncoder(formats[0]), NotNull());
}

}  // namespace
}  // namespace webrtc
