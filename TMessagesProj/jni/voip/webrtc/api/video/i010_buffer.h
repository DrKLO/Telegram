/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_I010_BUFFER_H_
#define API_VIDEO_I010_BUFFER_H_

#include <stdint.h>

#include <memory>

#include "api/scoped_refptr.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "rtc_base/memory/aligned_malloc.h"

namespace webrtc {

// Plain I010 buffer in standard memory.
class I010Buffer : public I010BufferInterface {
 public:
  // Create a new buffer.
  static rtc::scoped_refptr<I010Buffer> Create(int width, int height);

  // Create a new buffer and copy the pixel data.
  static rtc::scoped_refptr<I010Buffer> Copy(const I010BufferInterface& buffer);

  // Convert and put I420 buffer into a new buffer.
  static rtc::scoped_refptr<I010Buffer> Copy(const I420BufferInterface& buffer);

  // Return a rotated copy of `src`.
  static rtc::scoped_refptr<I010Buffer> Rotate(const I010BufferInterface& src,
                                               VideoRotation rotation);

  // VideoFrameBuffer implementation.
  rtc::scoped_refptr<I420BufferInterface> ToI420() override;

  // PlanarYuv16BBuffer implementation.
  int width() const override;
  int height() const override;
  const uint16_t* DataY() const override;
  const uint16_t* DataU() const override;
  const uint16_t* DataV() const override;
  int StrideY() const override;
  int StrideU() const override;
  int StrideV() const override;

  uint16_t* MutableDataY();
  uint16_t* MutableDataU();
  uint16_t* MutableDataV();

  // Scale the cropped area of `src` to the size of `this` buffer, and
  // write the result into `this`.
  void CropAndScaleFrom(const I010BufferInterface& src,
                        int offset_x,
                        int offset_y,
                        int crop_width,
                        int crop_height);

  // Scale all of `src` to the size of `this` buffer, with no cropping.
  void ScaleFrom(const I010BufferInterface& src);

 protected:
  I010Buffer(int width, int height, int stride_y, int stride_u, int stride_v);
  ~I010Buffer() override;

 private:
  const int width_;
  const int height_;
  const int stride_y_;
  const int stride_u_;
  const int stride_v_;
  const std::unique_ptr<uint16_t, AlignedFreeDeleter> data_;
};

}  // namespace webrtc

#endif  // API_VIDEO_I010_BUFFER_H_
