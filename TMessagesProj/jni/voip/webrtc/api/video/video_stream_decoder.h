/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_VIDEO_STREAM_DECODER_H_
#define API_VIDEO_VIDEO_STREAM_DECODER_H_

#include <map>
#include <memory>
#include <utility>

#include "api/units/time_delta.h"
#include "api/video/encoded_frame.h"
#include "api/video/video_frame.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder_factory.h"

namespace webrtc {
// NOTE: This class is still under development and may change without notice.
class VideoStreamDecoderInterface {
 public:
  class Callbacks {
   public:
    virtual ~Callbacks() = default;

    // Called when the VideoStreamDecoder enters a non-decodable state.
    virtual void OnNonDecodableState() = 0;

    // Called with the last continuous frame.
    virtual void OnContinuousUntil(
        const video_coding::VideoLayerFrameId& key) = 0;

    // Called with the decoded frame.
    virtual void OnDecodedFrame(VideoFrame decodedImage,
                                absl::optional<int> decode_time_ms,
                                absl::optional<int> qp) = 0;
  };

  virtual ~VideoStreamDecoderInterface() = default;

  virtual void OnFrame(std::unique_ptr<video_coding::EncodedFrame> frame) = 0;

  virtual void SetMinPlayoutDelay(TimeDelta min_delay) = 0;
  virtual void SetMaxPlayoutDelay(TimeDelta max_delay) = 0;
};

}  // namespace webrtc

#endif  // API_VIDEO_VIDEO_STREAM_DECODER_H_
