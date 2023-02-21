/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/h264_profile_level_id.h"

#include <cstdio>
#include <cstdlib>
#include <string>

#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

const char kProfileLevelId[] = "profile-level-id";

// For level_idc=11 and profile_idc=0x42, 0x4D, or 0x58, the constraint set3
// flag specifies if level 1b or level 1.1 is used.
const uint8_t kConstraintSet3Flag = 0x10;

// Convert a string of 8 characters into a byte where the positions containing
// character c will have their bit set. For example, c = 'x', str = "x1xx0000"
// will return 0b10110000. constexpr is used so that the pattern table in
// kProfilePatterns is statically initialized.
constexpr uint8_t ByteMaskString(char c, const char (&str)[9]) {
  return (str[0] == c) << 7 | (str[1] == c) << 6 | (str[2] == c) << 5 |
         (str[3] == c) << 4 | (str[4] == c) << 3 | (str[5] == c) << 2 |
         (str[6] == c) << 1 | (str[7] == c) << 0;
}

// Class for matching bit patterns such as "x1xx0000" where 'x' is allowed to be
// either 0 or 1.
class BitPattern {
 public:
  explicit constexpr BitPattern(const char (&str)[9])
      : mask_(~ByteMaskString('x', str)),
        masked_value_(ByteMaskString('1', str)) {}

  bool IsMatch(uint8_t value) const { return masked_value_ == (value & mask_); }

 private:
  const uint8_t mask_;
  const uint8_t masked_value_;
};

// Table for converting between profile_idc/profile_iop to H264Profile.
struct ProfilePattern {
  const uint8_t profile_idc;
  const BitPattern profile_iop;
  const H264Profile profile;
};

// This is from https://tools.ietf.org/html/rfc6184#section-8.1.
constexpr ProfilePattern kProfilePatterns[] = {
    {0x42, BitPattern("x1xx0000"), H264Profile::kProfileConstrainedBaseline},
    {0x4D, BitPattern("1xxx0000"), H264Profile::kProfileConstrainedBaseline},
    {0x58, BitPattern("11xx0000"), H264Profile::kProfileConstrainedBaseline},
    {0x42, BitPattern("x0xx0000"), H264Profile::kProfileBaseline},
    {0x58, BitPattern("10xx0000"), H264Profile::kProfileBaseline},
    {0x4D, BitPattern("0x0x0000"), H264Profile::kProfileMain},
    {0x64, BitPattern("00000000"), H264Profile::kProfileHigh},
    {0x64, BitPattern("00001100"), H264Profile::kProfileConstrainedHigh},
    {0xF4, BitPattern("00000000"), H264Profile::kProfilePredictiveHigh444}};

struct LevelConstraint {
  const int max_macroblocks_per_second;
  const int max_macroblock_frame_size;
  const H264Level level;
};

// This is from ITU-T H.264 (02/2016) Table A-1 – Level limits.
static constexpr LevelConstraint kLevelConstraints[] = {
    {1485, 99, H264Level::kLevel1},
    {1485, 99, H264Level::kLevel1_b},
    {3000, 396, H264Level::kLevel1_1},
    {6000, 396, H264Level::kLevel1_2},
    {11880, 396, H264Level::kLevel1_3},
    {11880, 396, H264Level::kLevel2},
    {19800, 792, H264Level::kLevel2_1},
    {20250, 1620, H264Level::kLevel2_2},
    {40500, 1620, H264Level::kLevel3},
    {108000, 3600, H264Level::kLevel3_1},
    {216000, 5120, H264Level::kLevel3_2},
    {245760, 8192, H264Level::kLevel4},
    {245760, 8192, H264Level::kLevel4_1},
    {522240, 8704, H264Level::kLevel4_2},
    {589824, 22080, H264Level::kLevel5},
    {983040, 36864, H264Level::kLevel5_1},
    {2073600, 36864, H264Level::kLevel5_2},
};

}  // anonymous namespace

absl::optional<H264ProfileLevelId> ParseH264ProfileLevelId(const char* str) {
  // The string should consist of 3 bytes in hexadecimal format.
  if (strlen(str) != 6u)
    return absl::nullopt;
  const uint32_t profile_level_id_numeric = strtol(str, nullptr, 16);
  if (profile_level_id_numeric == 0)
    return absl::nullopt;

  // Separate into three bytes.
  const uint8_t level_idc =
      static_cast<uint8_t>(profile_level_id_numeric & 0xFF);
  const uint8_t profile_iop =
      static_cast<uint8_t>((profile_level_id_numeric >> 8) & 0xFF);
  const uint8_t profile_idc =
      static_cast<uint8_t>((profile_level_id_numeric >> 16) & 0xFF);

  // Parse level based on level_idc and constraint set 3 flag.
  H264Level level_casted = static_cast<H264Level>(level_idc);
  H264Level level;

  switch (level_casted) {
    case H264Level::kLevel1_1:
      level = (profile_iop & kConstraintSet3Flag) != 0 ? H264Level::kLevel1_b
                                                       : H264Level::kLevel1_1;
      break;
    case H264Level::kLevel1:
    case H264Level::kLevel1_2:
    case H264Level::kLevel1_3:
    case H264Level::kLevel2:
    case H264Level::kLevel2_1:
    case H264Level::kLevel2_2:
    case H264Level::kLevel3:
    case H264Level::kLevel3_1:
    case H264Level::kLevel3_2:
    case H264Level::kLevel4:
    case H264Level::kLevel4_1:
    case H264Level::kLevel4_2:
    case H264Level::kLevel5:
    case H264Level::kLevel5_1:
    case H264Level::kLevel5_2:
      level = level_casted;
      break;
    default:
      // Unrecognized level_idc.
      return absl::nullopt;
  }

  // Parse profile_idc/profile_iop into a Profile enum.
  for (const ProfilePattern& pattern : kProfilePatterns) {
    if (profile_idc == pattern.profile_idc &&
        pattern.profile_iop.IsMatch(profile_iop)) {
      return H264ProfileLevelId(pattern.profile, level);
    }
  }

  // Unrecognized profile_idc/profile_iop combination.
  return absl::nullopt;
}

absl::optional<H264Level> H264SupportedLevel(int max_frame_pixel_count,
                                             float max_fps) {
  static const int kPixelsPerMacroblock = 16 * 16;

  for (int i = arraysize(kLevelConstraints) - 1; i >= 0; --i) {
    const LevelConstraint& level_constraint = kLevelConstraints[i];
    if (level_constraint.max_macroblock_frame_size * kPixelsPerMacroblock <=
            max_frame_pixel_count &&
        level_constraint.max_macroblocks_per_second <=
            max_fps * level_constraint.max_macroblock_frame_size) {
      return level_constraint.level;
    }
  }

  // No level supported.
  return absl::nullopt;
}

absl::optional<H264ProfileLevelId> ParseSdpForH264ProfileLevelId(
    const SdpVideoFormat::Parameters& params) {
  // TODO(magjed): The default should really be kProfileBaseline and kLevel1
  // according to the spec: https://tools.ietf.org/html/rfc6184#section-8.1. In
  // order to not break backwards compatibility with older versions of WebRTC
  // where external codecs don't have any parameters, use
  // kProfileConstrainedBaseline kLevel3_1 instead. This workaround will only be
  // done in an interim period to allow external clients to update their code.
  // http://crbug/webrtc/6337.
  static const H264ProfileLevelId kDefaultProfileLevelId(
      H264Profile::kProfileConstrainedBaseline, H264Level::kLevel3_1);

  const auto profile_level_id_it = params.find(kProfileLevelId);
  return (profile_level_id_it == params.end())
             ? kDefaultProfileLevelId
             : ParseH264ProfileLevelId(profile_level_id_it->second.c_str());
}

absl::optional<std::string> H264ProfileLevelIdToString(
    const H264ProfileLevelId& profile_level_id) {
  // Handle special case level == 1b.
  if (profile_level_id.level == H264Level::kLevel1_b) {
    switch (profile_level_id.profile) {
      case H264Profile::kProfileConstrainedBaseline:
        return {"42f00b"};
      case H264Profile::kProfileBaseline:
        return {"42100b"};
      case H264Profile::kProfileMain:
        return {"4d100b"};
      // Level 1b is not allowed for other profiles.
      default:
        return absl::nullopt;
    }
  }

  const char* profile_idc_iop_string;
  switch (profile_level_id.profile) {
    case H264Profile::kProfileConstrainedBaseline:
      profile_idc_iop_string = "42e0";
      break;
    case H264Profile::kProfileBaseline:
      profile_idc_iop_string = "4200";
      break;
    case H264Profile::kProfileMain:
      profile_idc_iop_string = "4d00";
      break;
    case H264Profile::kProfileConstrainedHigh:
      profile_idc_iop_string = "640c";
      break;
    case H264Profile::kProfileHigh:
      profile_idc_iop_string = "6400";
      break;
    case H264Profile::kProfilePredictiveHigh444:
      profile_idc_iop_string = "f400";
      break;
    // Unrecognized profile.
    default:
      return absl::nullopt;
  }

  char str[7];
  snprintf(str, 7u, "%s%02x", profile_idc_iop_string, profile_level_id.level);
  return {str};
}

bool H264IsSameProfile(const SdpVideoFormat::Parameters& params1,
                       const SdpVideoFormat::Parameters& params2) {
  const absl::optional<H264ProfileLevelId> profile_level_id =
      ParseSdpForH264ProfileLevelId(params1);
  const absl::optional<H264ProfileLevelId> other_profile_level_id =
      ParseSdpForH264ProfileLevelId(params2);
  // Compare H264 profiles, but not levels.
  return profile_level_id && other_profile_level_id &&
         profile_level_id->profile == other_profile_level_id->profile;
}

}  // namespace webrtc
