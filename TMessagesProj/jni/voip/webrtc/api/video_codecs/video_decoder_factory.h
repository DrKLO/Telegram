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

#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class VideoDecoder;
struct SdpVideoFormat;

// A factory that creates VideoDecoders.
// NOTE: This class is still under development and may change without notice.
class RTC_EXPORT VideoDecoderFactory {
 public:
  // Returns a list of supported video formats in order of preference, to use
  // for signaling etc.
  virtual std::vector<SdpVideoFormat> GetSupportedFormats() const = 0;

  // Creates a VideoDecoder for the specified format.
  virtual std::unique_ptr<VideoDecoder> CreateVideoDecoder(
      const SdpVideoFormat& format) = 0;

  // Note: Do not call or override this method! This method is a legacy
  // workaround and is scheduled for removal without notice.
  virtual std::unique_ptr<VideoDecoder> LegacyCreateVideoDecoder(
      const SdpVideoFormat& format,
      const std::string& receive_stream_id);

  virtual ~VideoDecoderFactory() {}
};

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_VIDEO_DECODER_FACTORY_H_
