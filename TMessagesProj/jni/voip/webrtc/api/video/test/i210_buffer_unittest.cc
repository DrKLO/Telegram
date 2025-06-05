
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

#include "api/video/i420_buffer.h"
#include "test/frame_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {

int GetY(rtc::scoped_refptr<I210BufferInterface> buf, int col, int row) {
  return buf->DataY()[row * buf->StrideY() + col];
}

int GetU(rtc::scoped_refptr<I210BufferInterface> buf, int col, int row) {
  return buf->DataU()[row * buf->StrideU() + col];
}

int GetV(rtc::scoped_refptr<I210BufferInterface> buf, int col, int row) {
  return buf->DataV()[row * buf->StrideV() + col];
}

void FillI210Buffer(rtc::scoped_refptr<I210Buffer> buf) {
  const uint16_t Y = 4;
  const uint16_t U = 8;
  const uint16_t V = 16;
  for (int row = 0; row < buf->height(); ++row) {
    for (int col = 0; col < buf->width(); ++col) {
      buf->MutableDataY()[row * buf->StrideY() + col] = Y;
    }
  }
  for (int row = 0; row < buf->ChromaHeight(); ++row) {
    for (int col = 0; col < buf->ChromaWidth(); ++col) {
      buf->MutableDataU()[row * buf->StrideU() + col] = U;
      buf->MutableDataV()[row * buf->StrideV() + col] = V;
    }
  }
}

}  // namespace

TEST(I210BufferTest, InitialData) {
  constexpr int stride = 3;
  constexpr int halfstride = (stride + 1) >> 1;
  constexpr int width = 3;
  constexpr int halfwidth = (width + 1) >> 1;
  constexpr int height = 3;

  rtc::scoped_refptr<I210Buffer> i210_buffer(I210Buffer::Create(width, height));
  EXPECT_EQ(width, i210_buffer->width());
  EXPECT_EQ(height, i210_buffer->height());
  EXPECT_EQ(stride, i210_buffer->StrideY());
  EXPECT_EQ(halfstride, i210_buffer->StrideU());
  EXPECT_EQ(halfstride, i210_buffer->StrideV());
  EXPECT_EQ(halfwidth, i210_buffer->ChromaWidth());
  EXPECT_EQ(height, i210_buffer->ChromaHeight());
}

TEST(I210BufferTest, ReadPixels) {
  constexpr int width = 3;
  constexpr int halfwidth = (width + 1) >> 1;
  constexpr int height = 3;

  rtc::scoped_refptr<I210Buffer> i210_buffer(I210Buffer::Create(width, height));
  // Y = 4, U = 8, V = 16.
  FillI210Buffer(i210_buffer);
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(4, GetY(i210_buffer, col, row));
    }
  }
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < halfwidth; col++) {
      EXPECT_EQ(8, GetU(i210_buffer, col, row));
      EXPECT_EQ(16, GetV(i210_buffer, col, row));
    }
  }
}

TEST(I210BufferTest, ToI420) {
  constexpr int width = 3;
  constexpr int halfwidth = (width + 1) >> 1;
  constexpr int height = 3;
  constexpr int size = width * height;
  constexpr int quartersize = (width + 1) / 2 * (height + 1) / 2;
  rtc::scoped_refptr<I420Buffer> reference(I420Buffer::Create(width, height));
  memset(reference->MutableDataY(), 1, size);
  memset(reference->MutableDataU(), 2, quartersize);
  memset(reference->MutableDataV(), 4, quartersize);

  rtc::scoped_refptr<I210Buffer> i210_buffer(I210Buffer::Create(width, height));
  // Y = 4, U = 8, V = 16.
  FillI210Buffer(i210_buffer);

  // Confirm YUV values are as expected.
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(4, GetY(i210_buffer, col, row));
    }
  }
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < halfwidth; col++) {
      EXPECT_EQ(8, GetU(i210_buffer, col, row));
      EXPECT_EQ(16, GetV(i210_buffer, col, row));
    }
  }

  rtc::scoped_refptr<I420BufferInterface> i420_buffer(i210_buffer->ToI420());
  EXPECT_TRUE(test::FrameBufsEqual(reference, i420_buffer));
  EXPECT_EQ(height, i420_buffer->height());
  EXPECT_EQ(width, i420_buffer->width());
}

}  // namespace webrtc
