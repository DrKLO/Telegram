
/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/i422_buffer.h"

#include "api/video/i420_buffer.h"
#include "test/frame_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {
int GetY(rtc::scoped_refptr<I422BufferInterface> buf, int col, int row) {
  return buf->DataY()[row * buf->StrideY() + col];
}

int GetU(rtc::scoped_refptr<I422BufferInterface> buf, int col, int row) {
  return buf->DataU()[row * buf->StrideU() + col];
}

int GetV(rtc::scoped_refptr<I422BufferInterface> buf, int col, int row) {
  return buf->DataV()[row * buf->StrideV() + col];
}

void FillI422Buffer(rtc::scoped_refptr<I422Buffer> buf) {
  const uint8_t Y = 1;
  const uint8_t U = 2;
  const uint8_t V = 3;
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

TEST(I422BufferTest, InitialData) {
  constexpr int stride = 3;
  constexpr int halfstride = (stride + 1) >> 1;
  constexpr int width = 3;
  constexpr int halfwidth = (width + 1) >> 1;
  constexpr int height = 3;

  rtc::scoped_refptr<I422Buffer> i422_buffer(I422Buffer::Create(width, height));
  EXPECT_EQ(width, i422_buffer->width());
  EXPECT_EQ(height, i422_buffer->height());
  EXPECT_EQ(stride, i422_buffer->StrideY());
  EXPECT_EQ(halfstride, i422_buffer->StrideU());
  EXPECT_EQ(halfstride, i422_buffer->StrideV());
  EXPECT_EQ(halfwidth, i422_buffer->ChromaWidth());
  EXPECT_EQ(height, i422_buffer->ChromaHeight());
}

TEST(I422BufferTest, ReadPixels) {
  constexpr int width = 3;
  constexpr int halfwidth = (width + 1) >> 1;
  constexpr int height = 3;

  rtc::scoped_refptr<I422Buffer> i422_buffer(I422Buffer::Create(width, height));
  // Y = 1, U = 2, V = 3.
  FillI422Buffer(i422_buffer);
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(1, GetY(i422_buffer, col, row));
    }
  }
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < halfwidth; col++) {
      EXPECT_EQ(2, GetU(i422_buffer, col, row));
      EXPECT_EQ(3, GetV(i422_buffer, col, row));
    }
  }
}

TEST(I422BufferTest, ToI420) {
  constexpr int width = 3;
  constexpr int halfwidth = (width + 1) >> 1;
  constexpr int height = 3;
  constexpr int size = width * height;
  constexpr int halfsize = (width + 1) / 2 * height;
  constexpr int quartersize = (width + 1) / 2 * (height + 1) / 2;
  rtc::scoped_refptr<I420Buffer> reference(I420Buffer::Create(width, height));
  memset(reference->MutableDataY(), 8, size);
  memset(reference->MutableDataU(), 4, quartersize);
  memset(reference->MutableDataV(), 2, quartersize);

  rtc::scoped_refptr<I422Buffer> i422_buffer(I422Buffer::Create(width, height));
  // Convert the reference buffer to I422.
  memset(i422_buffer->MutableDataY(), 8, size);
  memset(i422_buffer->MutableDataU(), 4, halfsize);
  memset(i422_buffer->MutableDataV(), 2, halfsize);

  // Confirm YUV values are as expected.
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(8, GetY(i422_buffer, col, row));
    }
  }
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < halfwidth; col++) {
      EXPECT_EQ(4, GetU(i422_buffer, col, row));
      EXPECT_EQ(2, GetV(i422_buffer, col, row));
    }
  }

  rtc::scoped_refptr<I420BufferInterface> i420_buffer(i422_buffer->ToI420());
  EXPECT_EQ(height, i420_buffer->height());
  EXPECT_EQ(width, i420_buffer->width());
  EXPECT_TRUE(test::FrameBufsEqual(reference, i420_buffer));
}

}  // namespace webrtc
