/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_RECORDABLE_ENCODED_FRAME_H_
#define API_VIDEO_RECORDABLE_ENCODED_FRAME_H_

#include "api/array_view.h"
#include "api/scoped_refptr.h"
#include "api/units/timestamp.h"
#include "api/video/color_space.h"
#include "api/video/encoded_image.h"
#include "api/video/video_codec_type.h"
#include "rtc_base/ref_count.h"

namespace webrtc {

// Interface for accessing recordable elements of an encoded frame.
class RecordableEncodedFrame {
 public:
  // Encoded resolution in pixels
  struct EncodedResolution {
    bool empty() const { return width == 0 && height == 0; }

    unsigned width = 0;
    unsigned height = 0;
  };

  virtual ~RecordableEncodedFrame() = default;

  // Provides access to encoded data
  virtual rtc::scoped_refptr<const EncodedImageBufferInterface> encoded_buffer()
      const = 0;

  // Optionally returns the colorspace of the encoded frame. This can differ
  // from the eventually decoded frame's colorspace.
  virtual absl::optional<webrtc::ColorSpace> color_space() const = 0;

  // Returns the codec of the encoded frame
  virtual VideoCodecType codec() const = 0;

  // Returns whether the encoded frame is a key frame
  virtual bool is_key_frame() const = 0;

  // Returns the frame's encoded resolution. May be 0x0 if the frame
  // doesn't contain resolution information
  virtual EncodedResolution resolution() const = 0;

  // Returns the computed render time
  virtual Timestamp render_time() const = 0;
};

}  // namespace webrtc

#endif  // API_VIDEO_RECORDABLE_ENCODED_FRAME_H_
