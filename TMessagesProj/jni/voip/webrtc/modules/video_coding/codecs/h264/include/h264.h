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

#ifndef MODULES_VIDEO_CODING_CODECS_H264_INCLUDE_H264_H_
#define MODULES_VIDEO_CODING_CODECS_H264_INCLUDE_H264_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/video_codecs/h264_profile_level_id.h"
#include "media/base/codec.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

struct SdpVideoFormat;

// Creates an H264 SdpVideoFormat entry with specified paramters.
RTC_EXPORT SdpVideoFormat
CreateH264Format(H264Profile profile,
                 H264Level level,
                 const std::string& packetization_mode);

// Set to disable the H.264 encoder/decoder implementations that are provided if
// `rtc_use_h264` build flag is true (if false, this function does nothing).
// This function should only be called before or during WebRTC initialization
// and is not thread-safe.
RTC_EXPORT void DisableRtcUseH264();

// Returns a vector with all supported internal H264 encode profiles that we can
// negotiate in SDP, in order of preference.
std::vector<SdpVideoFormat> SupportedH264Codecs();

// Returns a vector with all supported internal H264 decode profiles that we can
// negotiate in SDP, in order of preference. This will be available for receive
// only connections.
std::vector<SdpVideoFormat> SupportedH264DecoderCodecs();

class RTC_EXPORT H264Encoder : public VideoEncoder {
 public:
  static std::unique_ptr<H264Encoder> Create(const cricket::VideoCodec& codec);
  // If H.264 is supported (any implementation).
  static bool IsSupported();
  static bool SupportsScalabilityMode(absl::string_view scalability_mode);

  ~H264Encoder() override {}
};

class RTC_EXPORT H264Decoder : public VideoDecoder {
 public:
  static std::unique_ptr<H264Decoder> Create();
  static bool IsSupported();

  ~H264Decoder() override {}
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_H264_INCLUDE_H264_H_
