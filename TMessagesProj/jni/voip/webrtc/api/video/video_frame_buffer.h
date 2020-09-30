/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_VIDEO_FRAME_BUFFER_H_
#define API_VIDEO_VIDEO_FRAME_BUFFER_H_

#include <stdint.h>

#include "api/scoped_refptr.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class I420BufferInterface;
class I420ABufferInterface;
class I444BufferInterface;
class I010BufferInterface;

// Base class for frame buffers of different types of pixel format and storage.
// The tag in type() indicates how the data is represented, and each type is
// implemented as a subclass. To access the pixel data, call the appropriate
// GetXXX() function, where XXX represents the type. There is also a function
// ToI420() that returns a frame buffer in I420 format, converting from the
// underlying representation if necessary. I420 is the most widely accepted
// format and serves as a fallback for video sinks that can only handle I420,
// e.g. the internal WebRTC software encoders. A special enum value 'kNative' is
// provided for external clients to implement their own frame buffer
// representations, e.g. as textures. The external client can produce such
// native frame buffers from custom video sources, and then cast it back to the
// correct subclass in custom video sinks. The purpose of this is to improve
// performance by providing an optimized path without intermediate conversions.
// Frame metadata such as rotation and timestamp are stored in
// webrtc::VideoFrame, and not here.
class RTC_EXPORT VideoFrameBuffer : public rtc::RefCountInterface {
 public:
  // New frame buffer types will be added conservatively when there is an
  // opportunity to optimize the path between some pair of video source and
  // video sink.
  enum class Type {
    kNative,
    kI420,
    kI420A,
    kI444,
    kI010,
  };

  // This function specifies in what pixel format the data is stored in.
  virtual Type type() const = 0;

  // The resolution of the frame in pixels. For formats where some planes are
  // subsampled, this is the highest-resolution plane.
  virtual int width() const = 0;
  virtual int height() const = 0;

  // Returns a memory-backed frame buffer in I420 format. If the pixel data is
  // in another format, a conversion will take place. All implementations must
  // provide a fallback to I420 for compatibility with e.g. the internal WebRTC
  // software encoders.
  virtual rtc::scoped_refptr<I420BufferInterface> ToI420() = 0;

  // GetI420() methods should return I420 buffer if conversion is trivial, i.e
  // no change for binary data is needed. Otherwise these methods should return
  // nullptr. One example of buffer with that property is
  // WebrtcVideoFrameAdapter in Chrome - it's I420 buffer backed by a shared
  // memory buffer. Therefore it must have type kNative. Yet, ToI420()
  // doesn't affect binary data at all. Another example is any I420A buffer.
  virtual const I420BufferInterface* GetI420() const;

  // These functions should only be called if type() is of the correct type.
  // Calling with a different type will result in a crash.
  const I420ABufferInterface* GetI420A() const;
  const I444BufferInterface* GetI444() const;
  const I010BufferInterface* GetI010() const;

 protected:
  ~VideoFrameBuffer() override {}
};

// This interface represents planar formats.
class PlanarYuvBuffer : public VideoFrameBuffer {
 public:
  virtual int ChromaWidth() const = 0;
  virtual int ChromaHeight() const = 0;

  // Returns the number of steps(in terms of Data*() return type) between
  // successive rows for a given plane.
  virtual int StrideY() const = 0;
  virtual int StrideU() const = 0;
  virtual int StrideV() const = 0;

 protected:
  ~PlanarYuvBuffer() override {}
};

// This interface represents 8-bit color depth formats: Type::kI420,
// Type::kI420A and Type::kI444.
class PlanarYuv8Buffer : public PlanarYuvBuffer {
 public:
  // Returns pointer to the pixel data for a given plane. The memory is owned by
  // the VideoFrameBuffer object and must not be freed by the caller.
  virtual const uint8_t* DataY() const = 0;
  virtual const uint8_t* DataU() const = 0;
  virtual const uint8_t* DataV() const = 0;

 protected:
  ~PlanarYuv8Buffer() override {}
};

class RTC_EXPORT I420BufferInterface : public PlanarYuv8Buffer {
 public:
  Type type() const override;

  int ChromaWidth() const final;
  int ChromaHeight() const final;

  rtc::scoped_refptr<I420BufferInterface> ToI420() final;
  const I420BufferInterface* GetI420() const final;

 protected:
  ~I420BufferInterface() override {}
};

class RTC_EXPORT I420ABufferInterface : public I420BufferInterface {
 public:
  Type type() const final;
  virtual const uint8_t* DataA() const = 0;
  virtual int StrideA() const = 0;

 protected:
  ~I420ABufferInterface() override {}
};

class I444BufferInterface : public PlanarYuv8Buffer {
 public:
  Type type() const final;

  int ChromaWidth() const final;
  int ChromaHeight() const final;

 protected:
  ~I444BufferInterface() override {}
};

// This interface represents 8-bit to 16-bit color depth formats: Type::kI010.
class PlanarYuv16BBuffer : public PlanarYuvBuffer {
 public:
  // Returns pointer to the pixel data for a given plane. The memory is owned by
  // the VideoFrameBuffer object and must not be freed by the caller.
  virtual const uint16_t* DataY() const = 0;
  virtual const uint16_t* DataU() const = 0;
  virtual const uint16_t* DataV() const = 0;

 protected:
  ~PlanarYuv16BBuffer() override {}
};

// Represents Type::kI010, allocates 16 bits per pixel and fills 10 least
// significant bits with color information.
class I010BufferInterface : public PlanarYuv16BBuffer {
 public:
  Type type() const override;

  int ChromaWidth() const final;
  int ChromaHeight() const final;

 protected:
  ~I010BufferInterface() override {}
};

}  // namespace webrtc

#endif  // API_VIDEO_VIDEO_FRAME_BUFFER_H_
