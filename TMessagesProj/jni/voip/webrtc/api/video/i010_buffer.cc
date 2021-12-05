/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/video/i010_buffer.h"

#include <utility>

#include "api/video/i420_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/ref_counted_object.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include "third_party/libyuv/include/libyuv/scale.h"

// Aligning pointer to 64 bytes for improved performance, e.g. use SIMD.
static const int kBufferAlignment = 64;
static const int kBytesPerPixel = 2;

namespace webrtc {

namespace {

int I010DataSize(int height, int stride_y, int stride_u, int stride_v) {
  return kBytesPerPixel *
         (stride_y * height + (stride_u + stride_v) * ((height + 1) / 2));
}

}  // namespace

I010Buffer::I010Buffer(int width,
                       int height,
                       int stride_y,
                       int stride_u,
                       int stride_v)
    : width_(width),
      height_(height),
      stride_y_(stride_y),
      stride_u_(stride_u),
      stride_v_(stride_v),
      data_(static_cast<uint16_t*>(
          AlignedMalloc(I010DataSize(height, stride_y, stride_u, stride_v),
                        kBufferAlignment))) {
  RTC_DCHECK_GT(width, 0);
  RTC_DCHECK_GT(height, 0);
  RTC_DCHECK_GE(stride_y, width);
  RTC_DCHECK_GE(stride_u, (width + 1) / 2);
  RTC_DCHECK_GE(stride_v, (width + 1) / 2);
}

I010Buffer::~I010Buffer() {}

// static
rtc::scoped_refptr<I010Buffer> I010Buffer::Create(int width, int height) {
  return rtc::make_ref_counted<I010Buffer>(width, height, width,
                                           (width + 1) / 2, (width + 1) / 2);
}

// static
rtc::scoped_refptr<I010Buffer> I010Buffer::Copy(
    const I010BufferInterface& source) {
  const int width = source.width();
  const int height = source.height();
  rtc::scoped_refptr<I010Buffer> buffer = Create(width, height);
  RTC_CHECK_EQ(
      0, libyuv::I010Copy(
             source.DataY(), source.StrideY(), source.DataU(), source.StrideU(),
             source.DataV(), source.StrideV(), buffer->MutableDataY(),
             buffer->StrideY(), buffer->MutableDataU(), buffer->StrideU(),
             buffer->MutableDataV(), buffer->StrideV(), width, height));
  return buffer;
}

// static
rtc::scoped_refptr<I010Buffer> I010Buffer::Copy(
    const I420BufferInterface& source) {
  const int width = source.width();
  const int height = source.height();
  rtc::scoped_refptr<I010Buffer> buffer = Create(width, height);
  RTC_CHECK_EQ(
      0, libyuv::I420ToI010(
             source.DataY(), source.StrideY(), source.DataU(), source.StrideU(),
             source.DataV(), source.StrideV(), buffer->MutableDataY(),
             buffer->StrideY(), buffer->MutableDataU(), buffer->StrideU(),
             buffer->MutableDataV(), buffer->StrideV(), width, height));
  return buffer;
}

// static
rtc::scoped_refptr<I010Buffer> I010Buffer::Rotate(
    const I010BufferInterface& src,
    VideoRotation rotation) {
  if (rotation == webrtc::kVideoRotation_0)
    return Copy(src);

  RTC_CHECK(src.DataY());
  RTC_CHECK(src.DataU());
  RTC_CHECK(src.DataV());
  int rotated_width = src.width();
  int rotated_height = src.height();
  if (rotation == webrtc::kVideoRotation_90 ||
      rotation == webrtc::kVideoRotation_270) {
    std::swap(rotated_width, rotated_height);
  }

  rtc::scoped_refptr<webrtc::I010Buffer> buffer =
      Create(rotated_width, rotated_height);
  // TODO(emircan): Remove this when there is libyuv::I010Rotate().
  for (int x = 0; x < src.width(); x++) {
    for (int y = 0; y < src.height(); y++) {
      int dest_x = x;
      int dest_y = y;
      switch (rotation) {
        // This case is covered by the early return.
        case webrtc::kVideoRotation_0:
          RTC_NOTREACHED();
          break;
        case webrtc::kVideoRotation_90:
          dest_x = src.height() - y - 1;
          dest_y = x;
          break;
        case webrtc::kVideoRotation_180:
          dest_x = src.width() - x - 1;
          dest_y = src.height() - y - 1;
          break;
        case webrtc::kVideoRotation_270:
          dest_x = y;
          dest_y = src.width() - x - 1;
          break;
      }
      buffer->MutableDataY()[dest_x + buffer->StrideY() * dest_y] =
          src.DataY()[x + src.StrideY() * y];
      dest_x /= 2;
      dest_y /= 2;
      int src_x = x / 2;
      int src_y = y / 2;
      buffer->MutableDataU()[dest_x + buffer->StrideU() * dest_y] =
          src.DataU()[src_x + src.StrideU() * src_y];
      buffer->MutableDataV()[dest_x + buffer->StrideV() * dest_y] =
          src.DataV()[src_x + src.StrideV() * src_y];
    }
  }
  return buffer;
}

rtc::scoped_refptr<I420BufferInterface> I010Buffer::ToI420() {
  rtc::scoped_refptr<I420Buffer> i420_buffer =
      I420Buffer::Create(width(), height());
  libyuv::I010ToI420(DataY(), StrideY(), DataU(), StrideU(), DataV(), StrideV(),
                     i420_buffer->MutableDataY(), i420_buffer->StrideY(),
                     i420_buffer->MutableDataU(), i420_buffer->StrideU(),
                     i420_buffer->MutableDataV(), i420_buffer->StrideV(),
                     width(), height());
  return i420_buffer;
}

int I010Buffer::width() const {
  return width_;
}

int I010Buffer::height() const {
  return height_;
}

const uint16_t* I010Buffer::DataY() const {
  return data_.get();
}
const uint16_t* I010Buffer::DataU() const {
  return data_.get() + stride_y_ * height_;
}
const uint16_t* I010Buffer::DataV() const {
  return data_.get() + stride_y_ * height_ + stride_u_ * ((height_ + 1) / 2);
}

int I010Buffer::StrideY() const {
  return stride_y_;
}
int I010Buffer::StrideU() const {
  return stride_u_;
}
int I010Buffer::StrideV() const {
  return stride_v_;
}

uint16_t* I010Buffer::MutableDataY() {
  return const_cast<uint16_t*>(DataY());
}
uint16_t* I010Buffer::MutableDataU() {
  return const_cast<uint16_t*>(DataU());
}
uint16_t* I010Buffer::MutableDataV() {
  return const_cast<uint16_t*>(DataV());
}

void I010Buffer::CropAndScaleFrom(const I010BufferInterface& src,
                                  int offset_x,
                                  int offset_y,
                                  int crop_width,
                                  int crop_height) {
  RTC_CHECK_LE(crop_width, src.width());
  RTC_CHECK_LE(crop_height, src.height());
  RTC_CHECK_LE(crop_width + offset_x, src.width());
  RTC_CHECK_LE(crop_height + offset_y, src.height());
  RTC_CHECK_GE(offset_x, 0);
  RTC_CHECK_GE(offset_y, 0);

  // Make sure offset is even so that u/v plane becomes aligned.
  const int uv_offset_x = offset_x / 2;
  const int uv_offset_y = offset_y / 2;
  offset_x = uv_offset_x * 2;
  offset_y = uv_offset_y * 2;

  const uint16_t* y_plane = src.DataY() + src.StrideY() * offset_y + offset_x;
  const uint16_t* u_plane =
      src.DataU() + src.StrideU() * uv_offset_y + uv_offset_x;
  const uint16_t* v_plane =
      src.DataV() + src.StrideV() * uv_offset_y + uv_offset_x;
  int res = libyuv::I420Scale_16(
      y_plane, src.StrideY(), u_plane, src.StrideU(), v_plane, src.StrideV(),
      crop_width, crop_height, MutableDataY(), StrideY(), MutableDataU(),
      StrideU(), MutableDataV(), StrideV(), width(), height(),
      libyuv::kFilterBox);

  RTC_DCHECK_EQ(res, 0);
}

void I010Buffer::ScaleFrom(const I010BufferInterface& src) {
  CropAndScaleFrom(src, 0, 0, src.width(), src.height());
}

void I010Buffer::PasteFrom(const I010BufferInterface& picture,
                           int offset_col,
                           int offset_row) {
  RTC_CHECK_LE(picture.width() + offset_col, width());
  RTC_CHECK_LE(picture.height() + offset_row, height());
  RTC_CHECK_GE(offset_col, 0);
  RTC_CHECK_GE(offset_row, 0);

  // Pasted picture has to be aligned so subsumpled UV plane isn't corrupted.
  RTC_CHECK(offset_col % 2 == 0);
  RTC_CHECK(offset_row % 2 == 0);
  RTC_CHECK(picture.width() % 2 == 0 ||
            picture.width() + offset_col == width());
  RTC_CHECK(picture.height() % 2 == 0 ||
            picture.height() + offset_row == height());

  libyuv::CopyPlane_16(picture.DataY(), picture.StrideY(),
                       MutableDataY() + StrideY() * offset_row + offset_col,
                       StrideY(), picture.width(), picture.height());

  libyuv::CopyPlane_16(
      picture.DataU(), picture.StrideU(),
      MutableDataU() + StrideU() * offset_row / 2 + offset_col / 2, StrideU(),
      picture.width() / 2, picture.height() / 2);

  libyuv::CopyPlane_16(
      picture.DataV(), picture.StrideV(),
      MutableDataV() + StrideV() * offset_row / 2 + offset_col / 2, StrideV(),
      picture.width() / 2, picture.height() / 2);
}

}  // namespace webrtc
