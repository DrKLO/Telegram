/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_INTERNAL_ENCODER_FACTORY_H_
#define MEDIA_ENGINE_INTERNAL_ENCODER_FACTORY_H_

#include <memory>
#include <string>
#include <vector>

#include "api/video_codecs/video_encoder_factory.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {
class RTC_EXPORT InternalEncoderFactory : public VideoEncoderFactory {
 public:
  std::vector<SdpVideoFormat> GetSupportedFormats() const override;
  CodecSupport QueryCodecSupport(
      const SdpVideoFormat& format,
      absl::optional<std::string> scalability_mode) const override;
  std::unique_ptr<VideoEncoder> CreateVideoEncoder(
      const SdpVideoFormat& format) override;
};

}  // namespace webrtc

#endif  // MEDIA_ENGINE_INTERNAL_ENCODER_FACTORY_H_
