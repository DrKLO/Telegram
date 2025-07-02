/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/color_space.h"

#include <stdint.h>

#include "test/gtest.h"

namespace webrtc {
TEST(ColorSpace, TestSettingPrimariesFromUint8) {
  ColorSpace color_space;
  EXPECT_TRUE(color_space.set_primaries_from_uint8(
      static_cast<uint8_t>(ColorSpace::PrimaryID::kBT470BG)));
  EXPECT_EQ(ColorSpace::PrimaryID::kBT470BG, color_space.primaries());
  EXPECT_FALSE(color_space.set_primaries_from_uint8(3));
  EXPECT_FALSE(color_space.set_primaries_from_uint8(23));
  EXPECT_FALSE(color_space.set_primaries_from_uint8(64));
}

TEST(ColorSpace, TestSettingTransferFromUint8) {
  ColorSpace color_space;
  EXPECT_TRUE(color_space.set_transfer_from_uint8(
      static_cast<uint8_t>(ColorSpace::TransferID::kBT2020_10)));
  EXPECT_EQ(ColorSpace::TransferID::kBT2020_10, color_space.transfer());
  EXPECT_FALSE(color_space.set_transfer_from_uint8(3));
  EXPECT_FALSE(color_space.set_transfer_from_uint8(19));
  EXPECT_FALSE(color_space.set_transfer_from_uint8(128));
}

TEST(ColorSpace, TestSettingMatrixFromUint8) {
  ColorSpace color_space;
  EXPECT_TRUE(color_space.set_matrix_from_uint8(
      static_cast<uint8_t>(ColorSpace::MatrixID::kCDNCLS)));
  EXPECT_EQ(ColorSpace::MatrixID::kCDNCLS, color_space.matrix());
  EXPECT_FALSE(color_space.set_matrix_from_uint8(3));
  EXPECT_FALSE(color_space.set_matrix_from_uint8(15));
  EXPECT_FALSE(color_space.set_matrix_from_uint8(255));
}

TEST(ColorSpace, TestSettingRangeFromUint8) {
  ColorSpace color_space;
  EXPECT_TRUE(color_space.set_range_from_uint8(
      static_cast<uint8_t>(ColorSpace::RangeID::kFull)));
  EXPECT_EQ(ColorSpace::RangeID::kFull, color_space.range());
  EXPECT_FALSE(color_space.set_range_from_uint8(4));
}

TEST(ColorSpace, TestSettingChromaSitingHorizontalFromUint8) {
  ColorSpace color_space;
  EXPECT_TRUE(color_space.set_chroma_siting_horizontal_from_uint8(
      static_cast<uint8_t>(ColorSpace::ChromaSiting::kCollocated)));
  EXPECT_EQ(ColorSpace::ChromaSiting::kCollocated,
            color_space.chroma_siting_horizontal());
  EXPECT_FALSE(color_space.set_chroma_siting_horizontal_from_uint8(3));
}

TEST(ColorSpace, TestSettingChromaSitingVerticalFromUint8) {
  ColorSpace color_space;
  EXPECT_TRUE(color_space.set_chroma_siting_vertical_from_uint8(
      static_cast<uint8_t>(ColorSpace::ChromaSiting::kHalf)));
  EXPECT_EQ(ColorSpace::ChromaSiting::kHalf,
            color_space.chroma_siting_vertical());
  EXPECT_FALSE(color_space.set_chroma_siting_vertical_from_uint8(3));
}

TEST(ColorSpace, TestAsStringFunction) {
  ColorSpace color_space(
      ColorSpace::PrimaryID::kBT709, ColorSpace::TransferID::kBT709,
      ColorSpace::MatrixID::kBT709, ColorSpace::RangeID::kLimited);
  EXPECT_EQ(
      color_space.AsString(),
      "{primaries:kBT709, transfer:kBT709, matrix:kBT709, range:kLimited}");
}

}  // namespace webrtc
