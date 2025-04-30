
/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/i444_buffer.h"

#include "api/video/i420_buffer.h"
#include "test/frame_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {
int GetY(rtc::scoped_refptr<I444BufferInterface> buf, int col, int row) {
  return buf->DataY()[row * buf->StrideY() + col];
}

int GetU(rtc::scoped_refptr<I444BufferInterface> buf, int col, int row) {
  return buf->DataU()[row * buf->StrideU() + col];
}

int GetV(rtc::scoped_refptr<I444BufferInterface> buf, int col, int row) {
  return buf->DataV()[row * buf->StrideV() + col];
}

void FillI444Buffer(rtc::scoped_refptr<I444Buffer> buf) {
  const uint8_t Y = 1;
  const uint8_t U = 2;
  const uint8_t V = 3;
  for (int row = 0; row < buf->height(); ++row) {
    for (int col = 0; col < buf->width(); ++col) {
      buf->MutableDataY()[row * buf->StrideY() + col] = Y;
      buf->MutableDataU()[row * buf->StrideU() + col] = U;
      buf->MutableDataV()[row * buf->StrideV() + col] = V;
    }
  }
}

}  // namespace

TEST(I444BufferTest, InitialData) {
  constexpr int stride = 3;
  constexpr int width = 3;
  constexpr int height = 3;

  rtc::scoped_refptr<I444Buffer> i444_buffer(I444Buffer::Create(width, height));
  EXPECT_EQ(width, i444_buffer->width());
  EXPECT_EQ(height, i444_buffer->height());
  EXPECT_EQ(stride, i444_buffer->StrideY());
  EXPECT_EQ(stride, i444_buffer->StrideU());
  EXPECT_EQ(stride, i444_buffer->StrideV());
  EXPECT_EQ(3, i444_buffer->ChromaWidth());
  EXPECT_EQ(3, i444_buffer->ChromaHeight());
}

TEST(I444BufferTest, ReadPixels) {
  constexpr int width = 3;
  constexpr int height = 3;

  rtc::scoped_refptr<I444Buffer> i444_buffer(I444Buffer::Create(width, height));
  // Y = 1, U = 2, V = 3.
  FillI444Buffer(i444_buffer);
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(1, GetY(i444_buffer, col, row));
      EXPECT_EQ(2, GetU(i444_buffer, col, row));
      EXPECT_EQ(3, GetV(i444_buffer, col, row));
    }
  }
}

TEST(I444BufferTest, ToI420) {
  constexpr int width = 3;
  constexpr int height = 3;
  constexpr int size_y = width * height;
  constexpr int size_u = (width + 1) / 2 * (height + 1) / 2;
  constexpr int size_v = (width + 1) / 2 * (height + 1) / 2;
  rtc::scoped_refptr<I420Buffer> reference(I420Buffer::Create(width, height));
  memset(reference->MutableDataY(), 8, size_y);
  memset(reference->MutableDataU(), 4, size_u);
  memset(reference->MutableDataV(), 2, size_v);

  rtc::scoped_refptr<I444Buffer> i444_buffer(I444Buffer::Create(width, height));
  // Convert the reference buffer to I444.
  memset(i444_buffer->MutableDataY(), 8, size_y);
  memset(i444_buffer->MutableDataU(), 4, size_y);
  memset(i444_buffer->MutableDataV(), 2, size_y);

  // Confirm YUV values are as expected.
  for (int row = 0; row < height; row++) {
    for (int col = 0; col < width; col++) {
      EXPECT_EQ(8, GetY(i444_buffer, col, row));
      EXPECT_EQ(4, GetU(i444_buffer, col, row));
      EXPECT_EQ(2, GetV(i444_buffer, col, row));
    }
  }

  rtc::scoped_refptr<I420BufferInterface> i420_buffer(i444_buffer->ToI420());
  EXPECT_EQ(height, i420_buffer->height());
  EXPECT_EQ(width, i420_buffer->width());
  EXPECT_TRUE(test::FrameBufsEqual(reference, i420_buffer));
}

}  // namespace webrtc
