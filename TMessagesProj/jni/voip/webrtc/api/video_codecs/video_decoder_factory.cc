/*
 *  Copyright (c) 2024 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/video_decoder_factory.h"

#include <memory>

#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder.h"
#include "rtc_base/checks.h"

namespace webrtc {

VideoDecoderFactory::CodecSupport VideoDecoderFactory::QueryCodecSupport(
    const SdpVideoFormat& format,
    bool reference_scaling) const {
  // Default implementation, query for supported formats and check if the
  // specified format is supported. Returns false if `reference_scaling` is
  // true.
  return {.is_supported = !reference_scaling &&
                          format.IsCodecInList(GetSupportedFormats())};
}

std::unique_ptr<VideoDecoder> VideoDecoderFactory::Create(
    const Environment& env,
    const SdpVideoFormat& format) {
  return CreateVideoDecoder(format);
}

std::unique_ptr<VideoDecoder> VideoDecoderFactory::CreateVideoDecoder(
    const SdpVideoFormat& format) {
  // Newer code shouldn't call this function,
  // Older code should implement it in derived classes.
  RTC_CHECK_NOTREACHED();
  return nullptr;
}

}  // namespace webrtc
