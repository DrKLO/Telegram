/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_CODECS_VIDEO_DECODER_H_
#define API_VIDEO_CODECS_VIDEO_DECODER_H_

#include <memory>
#include <string>
#include <vector>

#include "api/video/encoded_image.h"
#include "api/video/video_frame.h"
#include "api/video_codecs/video_codec.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class RTC_EXPORT DecodedImageCallback {
 public:
  virtual ~DecodedImageCallback() {}

  virtual int32_t Decoded(VideoFrame& decodedImage) = 0;
  // Provides an alternative interface that allows the decoder to specify the
  // decode time excluding waiting time for any previous pending frame to
  // return. This is necessary for breaking positive feedback in the delay
  // estimation when the decoder has a single output buffer.
  virtual int32_t Decoded(VideoFrame& decodedImage, int64_t decode_time_ms);

  // TODO(sakal): Remove other implementations when upstream projects have been
  // updated.
  virtual void Decoded(VideoFrame& decodedImage,
                       absl::optional<int32_t> decode_time_ms,
                       absl::optional<uint8_t> qp);
};

class RTC_EXPORT VideoDecoder {
 public:
  virtual ~VideoDecoder() {}

  virtual int32_t InitDecode(const VideoCodec* codec_settings,
                             int32_t number_of_cores) = 0;

  virtual int32_t Decode(const EncodedImage& input_image,
                         bool missing_frames,
                         int64_t render_time_ms) = 0;

  virtual int32_t RegisterDecodeCompleteCallback(
      DecodedImageCallback* callback) = 0;

  virtual int32_t Release() = 0;

  // Returns true if the decoder prefer to decode frames late.
  // That is, it can not decode infinite number of frames before the decoded
  // frame is consumed.
  virtual bool PrefersLateDecoding() const;

  virtual const char* ImplementationName() const;
};

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_VIDEO_DECODER_H_
