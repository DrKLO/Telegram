/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/internal_encoder_factory.h"

#include <string>

#include "absl/strings/match.h"
#include "api/video_codecs/sdp_video_format.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/av1/libaom_av1_encoder.h"
#include "modules/video_coding/codecs/h264/include/h264.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "rtc_base/logging.h"

namespace webrtc {

std::vector<SdpVideoFormat> InternalEncoderFactory::SupportedFormats() {
  std::vector<SdpVideoFormat> supported_codecs;
  supported_codecs.push_back(SdpVideoFormat(cricket::kVp8CodecName));
  for (const webrtc::SdpVideoFormat& format : webrtc::SupportedVP9Codecs())
    supported_codecs.push_back(format);
  for (const webrtc::SdpVideoFormat& format : webrtc::SupportedH264Codecs())
    supported_codecs.push_back(format);
  if (kIsLibaomAv1EncoderSupported)
    supported_codecs.push_back(SdpVideoFormat(cricket::kAv1CodecName));
  return supported_codecs;
}

std::vector<SdpVideoFormat> InternalEncoderFactory::GetSupportedFormats()
    const {
  return SupportedFormats();
}

std::unique_ptr<VideoEncoder> InternalEncoderFactory::CreateVideoEncoder(
    const SdpVideoFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, cricket::kVp8CodecName))
    return VP8Encoder::Create();
  if (absl::EqualsIgnoreCase(format.name, cricket::kVp9CodecName))
    return VP9Encoder::Create(cricket::VideoCodec(format));
  if (absl::EqualsIgnoreCase(format.name, cricket::kH264CodecName))
    return H264Encoder::Create(cricket::VideoCodec(format));
  if (kIsLibaomAv1EncoderSupported &&
      absl::EqualsIgnoreCase(format.name, cricket::kAv1CodecName))
    return CreateLibaomAv1Encoder();
  RTC_LOG(LS_ERROR) << "Trying to created encoder of unsupported format "
                    << format.name;
  return nullptr;
}

VideoEncoderFactory::CodecSupport InternalEncoderFactory::QueryCodecSupport(
    const SdpVideoFormat& format,
    absl::optional<std::string> scalability_mode) const {
  // Query for supported formats and check if the specified format is supported.
  // Begin with filtering out unsupported scalability modes.
  if (scalability_mode) {
    bool scalability_mode_supported = false;
    if (absl::EqualsIgnoreCase(format.name, cricket::kVp8CodecName)) {
      scalability_mode_supported =
          VP8Encoder::SupportsScalabilityMode(*scalability_mode);
    } else if (absl::EqualsIgnoreCase(format.name, cricket::kVp9CodecName)) {
      scalability_mode_supported =
          VP9Encoder::SupportsScalabilityMode(*scalability_mode);
    } else if (absl::EqualsIgnoreCase(format.name, cricket::kH264CodecName)) {
      scalability_mode_supported =
          H264Encoder::SupportsScalabilityMode(*scalability_mode);
    } else if (kIsLibaomAv1EncoderSupported &&
               absl::EqualsIgnoreCase(format.name, cricket::kAv1CodecName)) {
      scalability_mode_supported =
          LibaomAv1EncoderSupportsScalabilityMode(*scalability_mode);
    }

    static constexpr VideoEncoderFactory::CodecSupport kUnsupported = {
        /*is_supported=*/false, /*is_power_efficient=*/false};
    if (!scalability_mode_supported) {
      return kUnsupported;
    }
  }

  CodecSupport codec_support;
  codec_support.is_supported = format.IsCodecInList(GetSupportedFormats());
  return codec_support;
}

}  // namespace webrtc
