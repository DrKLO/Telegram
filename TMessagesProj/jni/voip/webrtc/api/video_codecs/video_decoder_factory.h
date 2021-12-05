/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_CODECS_VIDEO_DECODER_FACTORY_H_
#define API_VIDEO_CODECS_VIDEO_DECODER_FACTORY_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/video_codecs/sdp_video_format.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class VideoDecoder;

// A factory that creates VideoDecoders.
// NOTE: This class is still under development and may change without notice.
class RTC_EXPORT VideoDecoderFactory {
 public:
  struct CodecSupport {
    bool is_supported = false;
    bool is_power_efficient = false;
  };

  // Returns a list of supported video formats in order of preference, to use
  // for signaling etc.
  virtual std::vector<SdpVideoFormat> GetSupportedFormats() const = 0;

  // Query whether the specifed format is supported or not and if it will be
  // power efficient, which is currently interpreted as if there is support for
  // hardware acceleration.
  // See https://w3c.github.io/webrtc-svc/#scalabilitymodes* for a specification
  // of valid values for |scalability_mode|.
  // NOTE: QueryCodecSupport is currently an experimental feature that is
  // subject to change without notice.
  virtual CodecSupport QueryCodecSupport(
      const SdpVideoFormat& format,
      absl::optional<std::string> scalability_mode) const {
    // Default implementation, query for supported formats and check if the
    // specified format is supported. Returns false if scalability_mode is
    // specified.
    CodecSupport codec_support;
    if (!scalability_mode) {
      codec_support.is_supported = format.IsCodecInList(GetSupportedFormats());
    }
    return codec_support;
  }

  // Creates a VideoDecoder for the specified format.
  virtual std::unique_ptr<VideoDecoder> CreateVideoDecoder(
      const SdpVideoFormat& format) = 0;

  virtual ~VideoDecoderFactory() {}
};

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_VIDEO_DECODER_FACTORY_H_
