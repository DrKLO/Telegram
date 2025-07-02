/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/nv12_buffer.h"

#include "api/video/i420_buffer.h"
#include "test/frame_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {
int GetY(rtc::scoped_refptr<NV12BufferInterface> buf, int col, int row) {
  return buf->DataY()[row * buf->StrideY() + col];
}

int GetU(rtc::scoped_refptr<NV12BufferInterface> buf, int col, int row) {
  return buf->DataUV()[(row / 2) * buf->StrideUV() + (col / 2) * 2];
}

int GetV(rtc::scoped_refptr<NV12BufferInterface> buf, int col, int row) {
  return buf->DataUV()[(row / 2) * buf->StrideUV() + (col / 2) * 2 + 1];
}

void FillNV12Buffer(rtc::scoped_refptr<NV12Buffer> buf) {
  const uint8_t Y = 1;
  const uint8_t U = 2;
  const uint8_t V = 3;
  for (int row = 0; row < buf->height(); ++row) {
    for (int col = 0; col < buf->width(); ++col) {
      buf->MutableDataY()[row * buf->StrideY() + col] = Y;
    }
  }
  // Fill interleaving UV values.
  for (int row = 0; row < buf->ChromaHeight(); row++) {
    for (int col = 0; col < buf->StrideUV(); col += 2) {
      int uv_index = row * buf->StrideUV() + col;
      buf->MutableDataUV()[uv_index] = U;
      buf->MutableDataUV()[uv_index + 1] = V;
    }
  }
}

}  // namespace

TEST(NV12BufferTest, InitialData) {
  constexpr int stride_y = 3;
  constexpr int stride_uv = 4;
  constexpr int width = 3;
  constexpr int height = 3;

  rtc::scoped_refptr<NV12Buffer> nv12_buffer(NV12Buffer::Create(width, height));
  EXPECT_EQ(width, nv12_buffer->width());
  EXPECT_EQ(height, nv12_buffer->height());
  EXPECT_EQ(stride_y, nv12_buffer->StrideY());
  EXPECT_EQ(stride_uv, nv12_buffer->StrideUV());
  EXPECT_EQ(2, nv12_buffer->ChromaWidth());
  EXPECT_EQ(2, nv12_buffer->ChromaHeight());
}

TEST(NV12BufferTest, ReadPixels) {
  constexpr int width = 3;
  constexpr int height = 3;

  rtc::scoped_refptr<NV12Buffer> nv12_buffer(NV12Buffer::Create(width, height));
  // Y = 1, U = 2, V = 3.
  FillNV12Buffer(nv12_buffer);
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(1, GetY(nv12_buffer, col, row));
      EXPECT_EQ(2, GetU(nv12_buffer, col, row));
      EXPECT_EQ(3, GetV(nv12_buffer, col, row));
    }
  }
}

TEST(NV12BufferTest, ToI420) {
  constexpr int width = 3;
  constexpr int height = 3;
  constexpr int size_y = width * height;
  constexpr int size_u = (width + 1) / 2 * (height + 1) / 2;
  constexpr int size_v = (width + 1) / 2 * (height + 1) / 2;
  rtc::scoped_refptr<I420Buffer> reference(I420Buffer::Create(width, height));
  memset(reference->MutableDataY(), 8, size_y);
  memset(reference->MutableDataU(), 4, size_u);
  memset(reference->MutableDataV(), 2, size_v);

  rtc::scoped_refptr<NV12Buffer> nv12_buffer(NV12Buffer::Create(width, height));
  // Convert the reference buffer to NV12.
  memset(nv12_buffer->MutableDataY(), 8, size_y);
  // Interleaving u/v values.
  for (int i = 0; i < size_u + size_v; i += 2) {
    nv12_buffer->MutableDataUV()[i] = 4;
    nv12_buffer->MutableDataUV()[i + 1] = 2;
  }
  // Confirm YUV values are as expected.
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(8, GetY(nv12_buffer, col, row));
      EXPECT_EQ(4, GetU(nv12_buffer, col, row));
      EXPECT_EQ(2, GetV(nv12_buffer, col, row));
    }
  }

  rtc::scoped_refptr<I420BufferInterface> i420_buffer(nv12_buffer->ToI420());
  EXPECT_EQ(height, i420_buffer->height());
  EXPECT_EQ(width, i420_buffer->width());
  EXPECT_TRUE(test::FrameBufsEqual(reference, i420_buffer));
}

}  // namespace webrtc
