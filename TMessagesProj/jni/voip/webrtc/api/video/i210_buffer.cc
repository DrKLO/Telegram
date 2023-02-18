/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/video/i210_buffer.h"

#include <utility>

#include "api/make_ref_counted.h"
#include "api/video/i420_buffer.h"
#include "api/video/i422_buffer.h"
#include "rtc_base/checks.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include "third_party/libyuv/include/libyuv/scale.h"

// Aligning pointer to 64 bytes for improved performance, e.g. use SIMD.
static const int kBufferAlignment = 64;
static const int kBytesPerPixel = 2;

namespace webrtc {

namespace {

int I210DataSize(int height, int stride_y, int stride_u, int stride_v) {
  return kBytesPerPixel *
         (stride_y * height + stride_u * height + stride_v * height);
}

void webrtcRotatePlane90_16(const uint16_t* src,
                            int src_stride,
                            uint16_t* dst,
                            int dst_stride,
                            int width,
                            int height) {
  for (int x = 0; x < width; x++) {
    for (int y = 0; y < height; y++) {
      int dest_x = height - y - 1;
      int dest_y = x;
      dst[dest_x + dst_stride * dest_y] = src[x + src_stride * y];
    }
  }
}

void webrtcRotatePlane180_16(const uint16_t* src,
                             int src_stride,
                             uint16_t* dst,
                             int dst_stride,
                             int width,
                             int height) {
  for (int x = 0; x < width; x++) {
    for (int y = 0; y < height; y++) {
      int dest_x = width - x - 1;
      int dest_y = height - y - 1;
      dst[dest_x + dst_stride * dest_y] = src[x + src_stride * y];
    }
  }
}

void webrtcRotatePlane270_16(const uint16_t* src,
                             int src_stride,
                             uint16_t* dst,
                             int dst_stride,
                             int width,
                             int height) {
  for (int x = 0; x < width; x++) {
    for (int y = 0; y < height; y++) {
      int dest_x = y;
      int dest_y = width - x - 1;
      dst[dest_x + dst_stride * dest_y] = src[x + src_stride * y];
    }
  }
}

// TODO(sergio.garcia.murillo@gmail.com): Remove as soon it is available in
// libyuv. Due to the rotate&scale required, this function may not be merged in
// to libyuv inmediatelly.
// https://bugs.chromium.org/p/libyuv/issues/detail?id=926
// This method assumes continuous allocation of the y-plane, possibly clobbering
// any padding between pixel rows.
int webrtcI210Rotate(const uint16_t* src_y,
                     int src_stride_y,
                     const uint16_t* src_u,
                     int src_stride_u,
                     const uint16_t* src_v,
                     int src_stride_v,
                     uint16_t* dst_y,
                     int dst_stride_y,
                     uint16_t* dst_u,
                     int dst_stride_u,
                     uint16_t* dst_v,
                     int dst_stride_v,
                     int width,
                     int height,
                     enum libyuv::RotationMode mode) {
  int halfwidth = (width + 1) >> 1;
  int halfheight = (height + 1) >> 1;
  if (!src_y || !src_u || !src_v || width <= 0 || height == 0 || !dst_y ||
      !dst_u || !dst_v || dst_stride_y < 0) {
    return -1;
  }
  // Negative height means invert the image.
  if (height < 0) {
    height = -height;
    src_y = src_y + (height - 1) * src_stride_y;
    src_u = src_u + (height - 1) * src_stride_u;
    src_v = src_v + (height - 1) * src_stride_v;
    src_stride_y = -src_stride_y;
    src_stride_u = -src_stride_u;
    src_stride_v = -src_stride_v;
  }

  switch (mode) {
    case libyuv::kRotate0:
      // copy frame
      libyuv::CopyPlane_16(src_y, src_stride_y, dst_y, dst_stride_y, width,
                           height);
      libyuv::CopyPlane_16(src_u, src_stride_u, dst_u, dst_stride_u, halfwidth,
                           height);
      libyuv::CopyPlane_16(src_v, src_stride_v, dst_v, dst_stride_v, halfwidth,
                           height);
      return 0;
    case libyuv::kRotate90:
      // We need to rotate and rescale, we use plane Y as temporal storage.
      webrtcRotatePlane90_16(src_u, src_stride_u, dst_y, height, halfwidth,
                             height);
      libyuv::ScalePlane_16(dst_y, height, height, halfwidth, dst_u, halfheight,
                            halfheight, width, libyuv::kFilterBilinear);
      webrtcRotatePlane90_16(src_v, src_stride_v, dst_y, height, halfwidth,
                             height);
      libyuv::ScalePlane_16(dst_y, height, height, halfwidth, dst_v, halfheight,
                            halfheight, width, libyuv::kFilterLinear);
      webrtcRotatePlane90_16(src_y, src_stride_y, dst_y, dst_stride_y, width,
                             height);
      return 0;
    case libyuv::kRotate270:
      // We need to rotate and rescale, we use plane Y as temporal storage.
      webrtcRotatePlane270_16(src_u, src_stride_u, dst_y, height, halfwidth,
                              height);
      libyuv::ScalePlane_16(dst_y, height, height, halfwidth, dst_u, halfheight,
                            halfheight, width, libyuv::kFilterBilinear);
      webrtcRotatePlane270_16(src_v, src_stride_v, dst_y, height, halfwidth,
                              height);
      libyuv::ScalePlane_16(dst_y, height, height, halfwidth, dst_v, halfheight,
                            halfheight, width, libyuv::kFilterLinear);
      webrtcRotatePlane270_16(src_y, src_stride_y, dst_y, dst_stride_y, width,
                              height);

      return 0;
    case libyuv::kRotate180:
      webrtcRotatePlane180_16(src_y, src_stride_y, dst_y, dst_stride_y, width,
                              height);
      webrtcRotatePlane180_16(src_u, src_stride_u, dst_u, dst_stride_u,
                              halfwidth, height);
      webrtcRotatePlane180_16(src_v, src_stride_v, dst_v, dst_stride_v,
                              halfwidth, height);
      return 0;
    default:
      break;
  }
  return -1;
}

}  // namespace

I210Buffer::I210Buffer(int width,
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
          AlignedMalloc(I210DataSize(height, stride_y, stride_u, stride_v),
                        kBufferAlignment))) {
  RTC_DCHECK_GT(width, 0);
  RTC_DCHECK_GT(height, 0);
  RTC_DCHECK_GE(stride_y, width);
  RTC_DCHECK_GE(stride_u, (width + 1) / 2);
  RTC_DCHECK_GE(stride_v, (width + 1) / 2);
}

I210Buffer::~I210Buffer() {}

// static
rtc::scoped_refptr<I210Buffer> I210Buffer::Create(int width, int height) {
  return rtc::make_ref_counted<I210Buffer>(width, height, width,
                                           (width + 1) / 2, (width + 1) / 2);
}

// static
rtc::scoped_refptr<I210Buffer> I210Buffer::Copy(
    const I210BufferInterface& source) {
  const int width = source.width();
  const int height = source.height();
  rtc::scoped_refptr<I210Buffer> buffer = Create(width, height);
  RTC_CHECK_EQ(
      0, libyuv::I210Copy(
             source.DataY(), source.StrideY(), source.DataU(), source.StrideU(),
             source.DataV(), source.StrideV(), buffer->MutableDataY(),
             buffer->StrideY(), buffer->MutableDataU(), buffer->StrideU(),
             buffer->MutableDataV(), buffer->StrideV(), width, height));
  return buffer;
}

// static
rtc::scoped_refptr<I210Buffer> I210Buffer::Copy(
    const I420BufferInterface& source) {
  const int width = source.width();
  const int height = source.height();
  auto i422buffer = I422Buffer::Copy(source);
  rtc::scoped_refptr<I210Buffer> buffer = Create(width, height);
  RTC_CHECK_EQ(0, libyuv::I422ToI210(i422buffer->DataY(), i422buffer->StrideY(),
                                     i422buffer->DataU(), i422buffer->StrideU(),
                                     i422buffer->DataV(), i422buffer->StrideV(),
                                     buffer->MutableDataY(), buffer->StrideY(),
                                     buffer->MutableDataU(), buffer->StrideU(),
                                     buffer->MutableDataV(), buffer->StrideV(),
                                     width, height));
  return buffer;
}

// static
rtc::scoped_refptr<I210Buffer> I210Buffer::Rotate(
    const I210BufferInterface& src,
    VideoRotation rotation) {
  RTC_CHECK(src.DataY());
  RTC_CHECK(src.DataU());
  RTC_CHECK(src.DataV());

  int rotated_width = src.width();
  int rotated_height = src.height();
  if (rotation == webrtc::kVideoRotation_90 ||
      rotation == webrtc::kVideoRotation_270) {
    std::swap(rotated_width, rotated_height);
  }

  rtc::scoped_refptr<webrtc::I210Buffer> buffer =
      I210Buffer::Create(rotated_width, rotated_height);

  RTC_CHECK_EQ(0,
               webrtcI210Rotate(
                   src.DataY(), src.StrideY(), src.DataU(), src.StrideU(),
                   src.DataV(), src.StrideV(), buffer->MutableDataY(),
                   buffer->StrideY(), buffer->MutableDataU(), buffer->StrideU(),
                   buffer->MutableDataV(), buffer->StrideV(), src.width(),
                   src.height(), static_cast<libyuv::RotationMode>(rotation)));

  return buffer;
}

rtc::scoped_refptr<I420BufferInterface> I210Buffer::ToI420() {
  rtc::scoped_refptr<I420Buffer> i420_buffer =
      I420Buffer::Create(width(), height());
  libyuv::I210ToI420(DataY(), StrideY(), DataU(), StrideU(), DataV(), StrideV(),
                     i420_buffer->MutableDataY(), i420_buffer->StrideY(),
                     i420_buffer->MutableDataU(), i420_buffer->StrideU(),
                     i420_buffer->MutableDataV(), i420_buffer->StrideV(),
                     width(), height());
  return i420_buffer;
}

int I210Buffer::width() const {
  return width_;
}

int I210Buffer::height() const {
  return height_;
}

const uint16_t* I210Buffer::DataY() const {
  return data_.get();
}
const uint16_t* I210Buffer::DataU() const {
  return data_.get() + stride_y_ * height_;
}
const uint16_t* I210Buffer::DataV() const {
  return data_.get() + stride_y_ * height_ + stride_u_ * height_;
}

int I210Buffer::StrideY() const {
  return stride_y_;
}
int I210Buffer::StrideU() const {
  return stride_u_;
}
int I210Buffer::StrideV() const {
  return stride_v_;
}

uint16_t* I210Buffer::MutableDataY() {
  return const_cast<uint16_t*>(DataY());
}
uint16_t* I210Buffer::MutableDataU() {
  return const_cast<uint16_t*>(DataU());
}
uint16_t* I210Buffer::MutableDataV() {
  return const_cast<uint16_t*>(DataV());
}

void I210Buffer::CropAndScaleFrom(const I210BufferInterface& src,
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
  RTC_CHECK_GE(crop_width, 0);
  RTC_CHECK_GE(crop_height, 0);

  // Make sure offset is even so that u/v plane becomes aligned.
  const int uv_offset_x = offset_x / 2;
  const int uv_offset_y = offset_y;
  offset_x = uv_offset_x * 2;

  const uint16_t* y_plane = src.DataY() + src.StrideY() * offset_y + offset_x;
  const uint16_t* u_plane =
      src.DataU() + src.StrideU() * uv_offset_y + uv_offset_x;
  const uint16_t* v_plane =
      src.DataV() + src.StrideV() * uv_offset_y + uv_offset_x;
  int res = libyuv::I422Scale_16(
      y_plane, src.StrideY(), u_plane, src.StrideU(), v_plane, src.StrideV(),
      crop_width, crop_height, MutableDataY(), StrideY(), MutableDataU(),
      StrideU(), MutableDataV(), StrideV(), width(), height(),
      libyuv::kFilterBox);

  RTC_DCHECK_EQ(res, 0);
}

void I210Buffer::ScaleFrom(const I210BufferInterface& src) {
  CropAndScaleFrom(src, 0, 0, src.width(), src.height());
}

}  // namespace webrtc
