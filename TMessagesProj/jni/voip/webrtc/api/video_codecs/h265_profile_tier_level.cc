/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/h265_profile_tier_level.h"

#include <string>

#include "rtc_base/string_to_number.h"

namespace webrtc {

namespace {

const char kH265FmtpProfile[] = "profile-id";
const char kH265FmtpTier[] = "tier-flag";
const char kH265FmtpLevel[] = "level-id";

}  // anonymous namespace

// Annex A of https://www.itu.int/rec/T-REC-H.265 (08/21), section A.3.
absl::optional<H265Profile> StringToH265Profile(const std::string& profile) {
  absl::optional<int> i = rtc::StringToNumber<int>(profile);
  if (!i.has_value()) {
    return absl::nullopt;
  }

  switch (i.value()) {
    case 1:
      return H265Profile::kProfileMain;
    case 2:
      return H265Profile::kProfileMain10;
    case 3:
      return H265Profile::kProfileMainStill;
    case 4:
      return H265Profile::kProfileRangeExtensions;
    case 5:
      return H265Profile::kProfileHighThroughput;
    case 6:
      return H265Profile::kProfileMultiviewMain;
    case 7:
      return H265Profile::kProfileScalableMain;
    case 8:
      return H265Profile::kProfile3dMain;
    case 9:
      return H265Profile::kProfileScreenContentCoding;
    case 10:
      return H265Profile::kProfileScalableRangeExtensions;
    case 11:
      return H265Profile::kProfileHighThroughputScreenContentCoding;
    default:
      return absl::nullopt;
  }
}

// Annex A of https://www.itu.int/rec/T-REC-H.265 (08/21), section A.4,
// tiers and levels.
absl::optional<H265Tier> StringToH265Tier(const std::string& tier) {
  absl::optional<int> i = rtc::StringToNumber<int>(tier);
  if (!i.has_value()) {
    return absl::nullopt;
  }

  switch (i.value()) {
    case 0:
      return H265Tier::kTier0;
    case 1:
      return H265Tier::kTier1;
    default:
      return absl::nullopt;
  }
}

absl::optional<H265Level> StringToH265Level(const std::string& level) {
  const absl::optional<int> i = rtc::StringToNumber<int>(level);
  if (!i.has_value())
    return absl::nullopt;

  switch (i.value()) {
    case 30:
      return H265Level::kLevel1;
    case 60:
      return H265Level::kLevel2;
    case 63:
      return H265Level::kLevel2_1;
    case 90:
      return H265Level::kLevel3;
    case 93:
      return H265Level::kLevel3_1;
    case 120:
      return H265Level::kLevel4;
    case 123:
      return H265Level::kLevel4_1;
    case 150:
      return H265Level::kLevel5;
    case 153:
      return H265Level::kLevel5_1;
    case 156:
      return H265Level::kLevel5_2;
    case 180:
      return H265Level::kLevel6;
    case 183:
      return H265Level::kLevel6_1;
    case 186:
      return H265Level::kLevel6_2;
    default:
      return absl::nullopt;
  }
}

std::string H265ProfileToString(H265Profile profile) {
  switch (profile) {
    case H265Profile::kProfileMain:
      return "1";
    case H265Profile::kProfileMain10:
      return "2";
    case H265Profile::kProfileMainStill:
      return "3";
    case H265Profile::kProfileRangeExtensions:
      return "4";
    case H265Profile::kProfileHighThroughput:
      return "5";
    case H265Profile::kProfileMultiviewMain:
      return "6";
    case H265Profile::kProfileScalableMain:
      return "7";
    case H265Profile::kProfile3dMain:
      return "8";
    case H265Profile::kProfileScreenContentCoding:
      return "9";
    case H265Profile::kProfileScalableRangeExtensions:
      return "10";
    case H265Profile::kProfileHighThroughputScreenContentCoding:
      return "11";
  }
}

std::string H265TierToString(H265Tier tier) {
  switch (tier) {
    case H265Tier::kTier0:
      return "0";
    case H265Tier::kTier1:
      return "1";
  }
}

std::string H265LevelToString(H265Level level) {
  switch (level) {
    case H265Level::kLevel1:
      return "30";
    case H265Level::kLevel2:
      return "60";
    case H265Level::kLevel2_1:
      return "63";
    case H265Level::kLevel3:
      return "90";
    case H265Level::kLevel3_1:
      return "93";
    case H265Level::kLevel4:
      return "120";
    case H265Level::kLevel4_1:
      return "123";
    case H265Level::kLevel5:
      return "150";
    case H265Level::kLevel5_1:
      return "153";
    case H265Level::kLevel5_2:
      return "156";
    case H265Level::kLevel6:
      return "180";
    case H265Level::kLevel6_1:
      return "183";
    case H265Level::kLevel6_2:
      return "186";
  }
}

absl::optional<H265ProfileTierLevel> ParseSdpForH265ProfileTierLevel(
    const CodecParameterMap& params) {
  static const H265ProfileTierLevel kDefaultProfileTierLevel(
      H265Profile::kProfileMain, H265Tier::kTier0, H265Level::kLevel3_1);
  bool profile_tier_level_specified = false;

  absl::optional<H265Profile> profile;
  const auto profile_it = params.find(kH265FmtpProfile);
  if (profile_it != params.end()) {
    profile_tier_level_specified = true;
    const std::string& profile_str = profile_it->second;
    profile = StringToH265Profile(profile_str);
    if (!profile) {
      return absl::nullopt;
    }
  } else {
    profile = H265Profile::kProfileMain;
  }
  absl::optional<H265Tier> tier;
  const auto tier_it = params.find(kH265FmtpTier);
  if (tier_it != params.end()) {
    profile_tier_level_specified = true;
    const std::string& tier_str = tier_it->second;
    tier = StringToH265Tier(tier_str);
    if (!tier) {
      return absl::nullopt;
    }
  } else {
    tier = H265Tier::kTier0;
  }
  absl::optional<H265Level> level;
  const auto level_it = params.find(kH265FmtpLevel);
  if (level_it != params.end()) {
    profile_tier_level_specified = true;
    const std::string& level_str = level_it->second;
    level = StringToH265Level(level_str);
    if (!level) {
      return absl::nullopt;
    }
  } else {
    level = H265Level::kLevel3_1;
  }

  // Spec Table A.9, level 1 to level 3.1 does not allow high tiers.
  if (level <= H265Level::kLevel3_1 && tier == H265Tier::kTier1) {
    return absl::nullopt;
  }

  return !profile_tier_level_specified
             ? kDefaultProfileTierLevel
             : H265ProfileTierLevel(profile.value(), tier.value(),
                                    level.value());
}

bool H265IsSameProfileTierLevel(const CodecParameterMap& params1,
                                const CodecParameterMap& params2) {
  const absl::optional<H265ProfileTierLevel> ptl1 =
      ParseSdpForH265ProfileTierLevel(params1);
  const absl::optional<H265ProfileTierLevel> ptl2 =
      ParseSdpForH265ProfileTierLevel(params2);
  return ptl1 && ptl2 && ptl1->profile == ptl2->profile &&
         ptl1->tier == ptl2->tier && ptl1->level == ptl2->level;
}

}  // namespace webrtc
