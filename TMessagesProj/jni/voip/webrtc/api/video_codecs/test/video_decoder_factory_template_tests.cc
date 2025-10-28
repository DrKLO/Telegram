/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/mock_video_decoder.h"
#include "api/video_codecs/video_decoder_factory_template.h"
#include "api/video_codecs/video_decoder_factory_template_dav1d_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_open_h264_adapter.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::Contains;
using ::testing::Each;
using ::testing::Eq;
using ::testing::Field;
using ::testing::IsEmpty;
using ::testing::Ne;
using ::testing::Not;
using ::testing::UnorderedElementsAre;

namespace webrtc {
namespace {
const SdpVideoFormat kFooSdp("Foo");
const SdpVideoFormat kBarLowSdp("Bar", {{"profile", "low"}});
const SdpVideoFormat kBarHighSdp("Bar", {{"profile", "high"}});

struct FooDecoderTemplateAdapter {
  static std::vector<SdpVideoFormat> SupportedFormats() { return {kFooSdp}; }

  static std::unique_ptr<VideoDecoder> CreateDecoder(
      const SdpVideoFormat& format) {
    auto decoder = std::make_unique<testing::StrictMock<MockVideoDecoder>>();
    EXPECT_CALL(*decoder, Destruct);
    return decoder;
  }
};

struct BarDecoderTemplateAdapter {
  static std::vector<SdpVideoFormat> SupportedFormats() {
    return {kBarLowSdp, kBarHighSdp};
  }

  static std::unique_ptr<VideoDecoder> CreateDecoder(
      const SdpVideoFormat& format) {
    auto decoder = std::make_unique<testing::StrictMock<MockVideoDecoder>>();
    EXPECT_CALL(*decoder, Destruct);
    return decoder;
  }
};

TEST(VideoDecoderFactoryTemplate, OneTemplateAdapterCreateDecoder) {
  VideoDecoderFactoryTemplate<FooDecoderTemplateAdapter> factory;
  EXPECT_THAT(factory.GetSupportedFormats(), UnorderedElementsAre(kFooSdp));
  EXPECT_THAT(factory.CreateVideoDecoder(kFooSdp), Ne(nullptr));
  EXPECT_THAT(factory.CreateVideoDecoder(SdpVideoFormat("FooX")), Eq(nullptr));
}

TEST(VideoDecoderFactoryTemplate, TwoTemplateAdaptersNoDuplicates) {
  VideoDecoderFactoryTemplate<FooDecoderTemplateAdapter,
                              FooDecoderTemplateAdapter>
      factory;
  EXPECT_THAT(factory.GetSupportedFormats(), UnorderedElementsAre(kFooSdp));
}

TEST(VideoDecoderFactoryTemplate, TwoTemplateAdaptersCreateDecoders) {
  VideoDecoderFactoryTemplate<FooDecoderTemplateAdapter,
                              BarDecoderTemplateAdapter>
      factory;
  EXPECT_THAT(factory.GetSupportedFormats(),
              UnorderedElementsAre(kFooSdp, kBarLowSdp, kBarHighSdp));
  EXPECT_THAT(factory.CreateVideoDecoder(kFooSdp), Ne(nullptr));
  EXPECT_THAT(factory.CreateVideoDecoder(kBarLowSdp), Ne(nullptr));
  EXPECT_THAT(factory.CreateVideoDecoder(kBarHighSdp), Ne(nullptr));
  EXPECT_THAT(factory.CreateVideoDecoder(SdpVideoFormat("FooX")), Eq(nullptr));
  EXPECT_THAT(factory.CreateVideoDecoder(SdpVideoFormat("Bar")), Eq(nullptr));
}

TEST(VideoDecoderFactoryTemplate, LibvpxVp8) {
  VideoDecoderFactoryTemplate<LibvpxVp8DecoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats.size(), 1);
  EXPECT_THAT(formats[0], Field(&SdpVideoFormat::name, "VP8"));
  EXPECT_THAT(factory.CreateVideoDecoder(formats[0]), Ne(nullptr));
}

TEST(VideoDecoderFactoryTemplate, LibvpxVp9) {
  VideoDecoderFactoryTemplate<LibvpxVp9DecoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats, Not(IsEmpty()));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::name, "VP9")));
  EXPECT_THAT(factory.CreateVideoDecoder(formats[0]), Ne(nullptr));
}

// TODO(bugs.webrtc.org/13573): When OpenH264 is no longer a conditional build
//                              target remove this #ifdef.
#if defined(WEBRTC_USE_H264)
TEST(VideoDecoderFactoryTemplate, OpenH264) {
  VideoDecoderFactoryTemplate<OpenH264DecoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats, Not(IsEmpty()));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::name, "H264")));
  EXPECT_THAT(factory.CreateVideoDecoder(formats[0]), Ne(nullptr));
}
#endif  // defined(WEBRTC_USE_H264)

TEST(VideoDecoderFactoryTemplate, Dav1d) {
  VideoDecoderFactoryTemplate<Dav1dDecoderTemplateAdapter> factory;
  auto formats = factory.GetSupportedFormats();
  EXPECT_THAT(formats, Not(IsEmpty()));
  EXPECT_THAT(formats, Each(Field(&SdpVideoFormat::name, "AV1")));
  EXPECT_THAT(factory.CreateVideoDecoder(formats[0]), Ne(nullptr));
}

}  // namespace
}  // namespace webrtc
