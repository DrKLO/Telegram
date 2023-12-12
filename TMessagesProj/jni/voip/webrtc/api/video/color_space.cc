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

namespace webrtc {
namespace {
// Try to convert `enum_value` into the enum class T. `enum_bitmask` is created
// by the funciton below. Returns true if conversion was successful, false
// otherwise.
template <typename T>
bool SetFromUint8(uint8_t enum_value, uint64_t enum_bitmask, T* out) {
  if ((enum_value < 64) && ((enum_bitmask >> enum_value) & 1)) {
    *out = static_cast<T>(enum_value);
    return true;
  }
  return false;
}

// This function serves as an assert for the constexpr function below. It's on
// purpose not declared as constexpr so that it causes a build problem if enum
// values of 64 or above are used. The bitmask and the code generating it would
// have to be extended if the standard is updated to include enum values >= 64.
int EnumMustBeLessThan64() {
  return -1;
}

template <typename T, size_t N>
constexpr int MakeMask(const int index, const int length, T (&values)[N]) {
  return length > 1
             ? (MakeMask(index, 1, values) +
                MakeMask(index + 1, length - 1, values))
             : (static_cast<uint8_t>(values[index]) < 64
                    ? (uint64_t{1} << static_cast<uint8_t>(values[index]))
                    : EnumMustBeLessThan64());
}

// Create a bitmask where each bit corresponds to one potential enum value.
// `values` should be an array listing all possible enum values. The bit is set
// to one if the corresponding enum exists. Only works for enums with values
// less than 64.
template <typename T, size_t N>
constexpr uint64_t CreateEnumBitmask(T (&values)[N]) {
  return MakeMask(0, N, values);
}

bool SetChromaSitingFromUint8(uint8_t enum_value,
                              ColorSpace::ChromaSiting* chroma_siting) {
  constexpr ColorSpace::ChromaSiting kChromaSitings[] = {
      ColorSpace::ChromaSiting::kUnspecified,
      ColorSpace::ChromaSiting::kCollocated, ColorSpace::ChromaSiting::kHalf};
  constexpr uint64_t enum_bitmask = CreateEnumBitmask(kChromaSitings);

  return SetFromUint8(enum_value, enum_bitmask, chroma_siting);
}

}  // namespace

ColorSpace::ColorSpace() = default;
ColorSpace::ColorSpace(const ColorSpace& other) = default;
ColorSpace::ColorSpace(ColorSpace&& other) = default;
ColorSpace& ColorSpace::operator=(const ColorSpace& other) = default;

ColorSpace::ColorSpace(PrimaryID primaries,
                       TransferID transfer,
                       MatrixID matrix,
                       RangeID range)
    : ColorSpace(primaries,
                 transfer,
                 matrix,
                 range,
                 ChromaSiting::kUnspecified,
                 ChromaSiting::kUnspecified,
                 nullptr) {}

ColorSpace::ColorSpace(PrimaryID primaries,
                       TransferID transfer,
                       MatrixID matrix,
                       RangeID range,
                       ChromaSiting chroma_siting_horz,
                       ChromaSiting chroma_siting_vert,
                       const HdrMetadata* hdr_metadata)
    : primaries_(primaries),
      transfer_(transfer),
      matrix_(matrix),
      range_(range),
      chroma_siting_horizontal_(chroma_siting_horz),
      chroma_siting_vertical_(chroma_siting_vert),
      hdr_metadata_(hdr_metadata ? absl::make_optional(*hdr_metadata)
                                 : absl::nullopt) {}

ColorSpace::PrimaryID ColorSpace::primaries() const {
  return primaries_;
}

ColorSpace::TransferID ColorSpace::transfer() const {
  return transfer_;
}

ColorSpace::MatrixID ColorSpace::matrix() const {
  return matrix_;
}

ColorSpace::RangeID ColorSpace::range() const {
  return range_;
}

ColorSpace::ChromaSiting ColorSpace::chroma_siting_horizontal() const {
  return chroma_siting_horizontal_;
}

ColorSpace::ChromaSiting ColorSpace::chroma_siting_vertical() const {
  return chroma_siting_vertical_;
}

const HdrMetadata* ColorSpace::hdr_metadata() const {
  return hdr_metadata_ ? &*hdr_metadata_ : nullptr;
}

bool ColorSpace::set_primaries_from_uint8(uint8_t enum_value) {
  constexpr PrimaryID kPrimaryIds[] = {
      PrimaryID::kBT709,      PrimaryID::kUnspecified, PrimaryID::kBT470M,
      PrimaryID::kBT470BG,    PrimaryID::kSMPTE170M,   PrimaryID::kSMPTE240M,
      PrimaryID::kFILM,       PrimaryID::kBT2020,      PrimaryID::kSMPTEST428,
      PrimaryID::kSMPTEST431, PrimaryID::kSMPTEST432,  PrimaryID::kJEDECP22};
  constexpr uint64_t enum_bitmask = CreateEnumBitmask(kPrimaryIds);

  return SetFromUint8(enum_value, enum_bitmask, &primaries_);
}

bool ColorSpace::set_transfer_from_uint8(uint8_t enum_value) {
  constexpr TransferID kTransferIds[] = {
      TransferID::kBT709,       TransferID::kUnspecified,
      TransferID::kGAMMA22,     TransferID::kGAMMA28,
      TransferID::kSMPTE170M,   TransferID::kSMPTE240M,
      TransferID::kLINEAR,      TransferID::kLOG,
      TransferID::kLOG_SQRT,    TransferID::kIEC61966_2_4,
      TransferID::kBT1361_ECG,  TransferID::kIEC61966_2_1,
      TransferID::kBT2020_10,   TransferID::kBT2020_12,
      TransferID::kSMPTEST2084, TransferID::kSMPTEST428,
      TransferID::kARIB_STD_B67};
  constexpr uint64_t enum_bitmask = CreateEnumBitmask(kTransferIds);

  return SetFromUint8(enum_value, enum_bitmask, &transfer_);
}

bool ColorSpace::set_matrix_from_uint8(uint8_t enum_value) {
  constexpr MatrixID kMatrixIds[] = {
      MatrixID::kRGB,       MatrixID::kBT709,       MatrixID::kUnspecified,
      MatrixID::kFCC,       MatrixID::kBT470BG,     MatrixID::kSMPTE170M,
      MatrixID::kSMPTE240M, MatrixID::kYCOCG,       MatrixID::kBT2020_NCL,
      MatrixID::kBT2020_CL, MatrixID::kSMPTE2085,   MatrixID::kCDNCLS,
      MatrixID::kCDCLS,     MatrixID::kBT2100_ICTCP};
  constexpr uint64_t enum_bitmask = CreateEnumBitmask(kMatrixIds);

  return SetFromUint8(enum_value, enum_bitmask, &matrix_);
}

bool ColorSpace::set_range_from_uint8(uint8_t enum_value) {
  constexpr RangeID kRangeIds[] = {RangeID::kInvalid, RangeID::kLimited,
                                   RangeID::kFull, RangeID::kDerived};
  constexpr uint64_t enum_bitmask = CreateEnumBitmask(kRangeIds);

  return SetFromUint8(enum_value, enum_bitmask, &range_);
}

bool ColorSpace::set_chroma_siting_horizontal_from_uint8(uint8_t enum_value) {
  return SetChromaSitingFromUint8(enum_value, &chroma_siting_horizontal_);
}

bool ColorSpace::set_chroma_siting_vertical_from_uint8(uint8_t enum_value) {
  return SetChromaSitingFromUint8(enum_value, &chroma_siting_vertical_);
}

void ColorSpace::set_hdr_metadata(const HdrMetadata* hdr_metadata) {
  hdr_metadata_ =
      hdr_metadata ? absl::make_optional(*hdr_metadata) : absl::nullopt;
}

}  // namespace webrtc
