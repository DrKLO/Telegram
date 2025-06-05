/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef SDK_OBJC_NATIVE_SRC_OBJC_VIDEO_DECODER_FACTORY_H_
#define SDK_OBJC_NATIVE_SRC_OBJC_VIDEO_DECODER_FACTORY_H_

#import "base/RTCMacros.h"

#include "api/video_codecs/video_decoder_factory.h"
#include "media/base/codec.h"

@protocol RTC_OBJC_TYPE
(RTCVideoDecoderFactory);

namespace webrtc {

class CustomObjCVideoDecoderFactory : public VideoDecoderFactory {
 public:
  explicit CustomObjCVideoDecoderFactory(id<RTC_OBJC_TYPE(RTCVideoDecoderFactory)>);
  ~CustomObjCVideoDecoderFactory() override;

  id<RTC_OBJC_TYPE(RTCVideoDecoderFactory)> wrapped_decoder_factory() const;

  std::vector<SdpVideoFormat> GetSupportedFormats() const override;
  std::unique_ptr<VideoDecoder> CreateVideoDecoder(
      const SdpVideoFormat& format) override;

 private:
  id<RTC_OBJC_TYPE(RTCVideoDecoderFactory)> decoder_factory_;
};

}  // namespace webrtc

#endif  // SDK_OBJC_NATIVE_SRC_OBJC_VIDEO_DECODER_FACTORY_H_
