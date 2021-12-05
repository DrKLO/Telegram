/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_FAKE_VIDEO_CODEC_FACTORY_H_
#define MEDIA_ENGINE_FAKE_VIDEO_CODEC_FACTORY_H_

#include <memory>
#include <vector>

#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// Provides a fake video encoder instance that produces frames large enough for
// the given bitrate constraints.
class RTC_EXPORT FakeVideoEncoderFactory : public VideoEncoderFactory {
 public:
  FakeVideoEncoderFactory();

  static std::unique_ptr<VideoEncoder> CreateVideoEncoder();

  // VideoEncoderFactory implementation
  std::vector<SdpVideoFormat> GetSupportedFormats() const override;
  std::unique_ptr<VideoEncoder> CreateVideoEncoder(
      const SdpVideoFormat& format) override;
};

// Provides a fake video decoder instance that ignores the given bitstream and
// produces frames.
class RTC_EXPORT FakeVideoDecoderFactory : public VideoDecoderFactory {
 public:
  FakeVideoDecoderFactory();

  static std::unique_ptr<VideoDecoder> CreateVideoDecoder();

  // VideoDecoderFactory implementation
  std::vector<SdpVideoFormat> GetSupportedFormats() const override;
  std::unique_ptr<VideoDecoder> CreateVideoDecoder(
      const SdpVideoFormat& format) override;
};

}  // namespace webrtc

#endif  // MEDIA_ENGINE_FAKE_VIDEO_CODEC_FACTORY_H_
