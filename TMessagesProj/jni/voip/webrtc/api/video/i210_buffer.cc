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
               libyuv::I210Rotate(
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
