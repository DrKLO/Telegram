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

#include "absl/types/optional.h"
#include "test/gtest.h"

namespace webrtc {

TEST(H265ProfileTierLevel, TestLevelToString) {
  EXPECT_EQ(H265LevelToString(H265Level::kLevel1), "30");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel2), "60");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel2_1), "63");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel3), "90");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel3_1), "93");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel4), "120");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel4_1), "123");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel5), "150");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel5_1), "153");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel5_2), "156");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel6), "180");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel6_1), "183");
  EXPECT_EQ(H265LevelToString(H265Level::kLevel6_2), "186");
}

TEST(H265ProfileTierLevel, TestProfileToString) {
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileMain), "1");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileMain10), "2");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileMainStill), "3");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileRangeExtensions), "4");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileHighThroughput), "5");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileMultiviewMain), "6");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileScalableMain), "7");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfile3dMain), "8");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileScreenContentCoding), "9");
  EXPECT_EQ(H265ProfileToString(H265Profile::kProfileScalableRangeExtensions),
            "10");
  EXPECT_EQ(H265ProfileToString(
                H265Profile::kProfileHighThroughputScreenContentCoding),
            "11");
}

TEST(H265ProfileTierLevel, TestTierToString) {
  EXPECT_EQ(H265TierToString(H265Tier::kTier0), "0");
  EXPECT_EQ(H265TierToString(H265Tier::kTier1), "1");
}

TEST(H265ProfileTierLevel, TestStringToProfile) {
  // Invalid profiles.
  EXPECT_FALSE(StringToH265Profile("0"));
  EXPECT_FALSE(StringToH265Profile("12"));

  // Malformed profiles
  EXPECT_FALSE(StringToH265Profile(""));
  EXPECT_FALSE(StringToH265Profile(" 1"));
  EXPECT_FALSE(StringToH265Profile("12x"));
  EXPECT_FALSE(StringToH265Profile("x12"));
  EXPECT_FALSE(StringToH265Profile("gggg"));

  // Valid profiles.
  EXPECT_EQ(StringToH265Profile("1"), H265Profile::kProfileMain);
  EXPECT_EQ(StringToH265Profile("2"), H265Profile::kProfileMain10);
  EXPECT_EQ(StringToH265Profile("4"), H265Profile::kProfileRangeExtensions);
}

TEST(H265ProfileTierLevel, TestStringToLevel) {
  // Invalid levels.
  EXPECT_FALSE(StringToH265Level("0"));
  EXPECT_FALSE(StringToH265Level("200"));

  // Malformed levels.
  EXPECT_FALSE(StringToH265Level(""));
  EXPECT_FALSE(StringToH265Level(" 30"));
  EXPECT_FALSE(StringToH265Level("30x"));
  EXPECT_FALSE(StringToH265Level("x30"));
  EXPECT_FALSE(StringToH265Level("ggggg"));

  // Valid levels.
  EXPECT_EQ(StringToH265Level("30"), H265Level::kLevel1);
  EXPECT_EQ(StringToH265Level("93"), H265Level::kLevel3_1);
  EXPECT_EQ(StringToH265Level("183"), H265Level::kLevel6_1);
}

TEST(H265ProfileTierLevel, TestStringToTier) {
  // Invalid tiers.
  EXPECT_FALSE(StringToH265Tier("4"));
  EXPECT_FALSE(StringToH265Tier("-1"));

  // Malformed tiers.
  EXPECT_FALSE(StringToH265Tier(""));
  EXPECT_FALSE(StringToH265Tier(" 1"));
  EXPECT_FALSE(StringToH265Tier("t1"));

  // Valid tiers.
  EXPECT_EQ(StringToH265Tier("0"), H265Tier::kTier0);
  EXPECT_EQ(StringToH265Tier("1"), H265Tier::kTier1);
}

TEST(H265ProfileTierLevel, TestParseSdpProfileTierLevelAllEmpty) {
  const absl::optional<H265ProfileTierLevel> profile_tier_level =
      ParseSdpForH265ProfileTierLevel(CodecParameterMap());
  EXPECT_TRUE(profile_tier_level);
  EXPECT_EQ(H265Profile::kProfileMain, profile_tier_level->profile);
  EXPECT_EQ(H265Level::kLevel3_1, profile_tier_level->level);
  EXPECT_EQ(H265Tier::kTier0, profile_tier_level->tier);
}

TEST(H265ProfileTierLevel, TestParseSdpProfileTierLevelPartialEmpty) {
  CodecParameterMap params;
  params["profile-id"] = "1";
  params["tier-flag"] = "0";
  absl::optional<H265ProfileTierLevel> profile_tier_level =
      ParseSdpForH265ProfileTierLevel(params);
  EXPECT_TRUE(profile_tier_level);
  EXPECT_EQ(H265Profile::kProfileMain, profile_tier_level->profile);
  EXPECT_EQ(H265Level::kLevel3_1, profile_tier_level->level);
  EXPECT_EQ(H265Tier::kTier0, profile_tier_level->tier);

  params.clear();
  params["profile-id"] = "2";
  profile_tier_level = ParseSdpForH265ProfileTierLevel(params);
  EXPECT_TRUE(profile_tier_level);
  EXPECT_EQ(H265Profile::kProfileMain10, profile_tier_level->profile);
  EXPECT_EQ(H265Level::kLevel3_1, profile_tier_level->level);
  EXPECT_EQ(H265Tier::kTier0, profile_tier_level->tier);

  params.clear();
  params["level-id"] = "180";
  profile_tier_level = ParseSdpForH265ProfileTierLevel(params);
  EXPECT_TRUE(profile_tier_level);
  EXPECT_EQ(H265Profile::kProfileMain, profile_tier_level->profile);
  EXPECT_EQ(H265Level::kLevel6, profile_tier_level->level);
  EXPECT_EQ(H265Tier::kTier0, profile_tier_level->tier);
}

TEST(H265ProfileTierLevel, TestParseSdpProfileTierLevelInvalid) {
  CodecParameterMap params;

  // Invalid profile-tier-level combination.
  params["profile-id"] = "1";
  params["tier-flag"] = "1";
  params["level-id"] = "93";
  absl::optional<H265ProfileTierLevel> profile_tier_level =
      ParseSdpForH265ProfileTierLevel(params);
  EXPECT_FALSE(profile_tier_level);
  params.clear();
  params["profile-id"] = "1";
  params["tier-flag"] = "4";
  params["level-id"] = "180";
  profile_tier_level = ParseSdpForH265ProfileTierLevel(params);
  EXPECT_FALSE(profile_tier_level);

  // Valid profile-tier-level combination.
  params.clear();
  params["profile-id"] = "1";
  params["tier-flag"] = "0";
  params["level-id"] = "153";
  profile_tier_level = ParseSdpForH265ProfileTierLevel(params);
  EXPECT_TRUE(profile_tier_level);
}

TEST(H265ProfileTierLevel, TestToStringRoundTrip) {
  CodecParameterMap params;
  params["profile-id"] = "1";
  params["tier-flag"] = "0";
  params["level-id"] = "93";
  absl::optional<H265ProfileTierLevel> profile_tier_level =
      ParseSdpForH265ProfileTierLevel(params);
  EXPECT_TRUE(profile_tier_level);
  EXPECT_EQ("1", H265ProfileToString(profile_tier_level->profile));
  EXPECT_EQ("0", H265TierToString(profile_tier_level->tier));
  EXPECT_EQ("93", H265LevelToString(profile_tier_level->level));

  params.clear();
  params["profile-id"] = "2";
  params["tier-flag"] = "1";
  params["level-id"] = "180";
  profile_tier_level = ParseSdpForH265ProfileTierLevel(params);
  EXPECT_TRUE(profile_tier_level);
  EXPECT_EQ("2", H265ProfileToString(profile_tier_level->profile));
  EXPECT_EQ("1", H265TierToString(profile_tier_level->tier));
  EXPECT_EQ("180", H265LevelToString(profile_tier_level->level));
}

TEST(H265ProfileTierLevel, TestProfileTierLevelCompare) {
  CodecParameterMap params1;
  CodecParameterMap params2;

  // None of profile-id/tier-flag/level-id is specified,
  EXPECT_TRUE(H265IsSameProfileTierLevel(params1, params2));

  // Same non-empty PTL
  params1["profile-id"] = "1";
  params1["tier-flag"] = "0";
  params1["level-id"] = "120";
  params2["profile-id"] = "1";
  params2["tier-flag"] = "0";
  params2["level-id"] = "120";
  EXPECT_TRUE(H265IsSameProfileTierLevel(params1, params2));

  // Different profiles.
  params1.clear();
  params2.clear();
  params1["profile-id"] = "1";
  params2["profile-id"] = "2";
  EXPECT_FALSE(H265IsSameProfileTierLevel(params1, params2));

  // Different levels.
  params1.clear();
  params2.clear();
  params1["profile-id"] = "1";
  params2["profile-id"] = "1";
  params1["level-id"] = "93";
  params2["level-id"] = "183";
  EXPECT_FALSE(H265IsSameProfileTierLevel(params1, params2));

  // Different tiers.
  params1.clear();
  params2.clear();
  params1["profile-id"] = "1";
  params2["profile-id"] = "1";
  params1["level-id"] = "93";
  params2["level-id"] = "93";
  params1["tier-flag"] = "0";
  params2["tier-flag"] = "1";
  EXPECT_FALSE(H265IsSameProfileTierLevel(params1, params2));

  // One of the CodecParameterMap is invalid.
  params1.clear();
  params2.clear();
  params1["profile-id"] = "1";
  params2["profile-id"] = "1";
  params1["tier-flag"] = "0";
  params2["tier-flag"] = "4";
  EXPECT_FALSE(H265IsSameProfileTierLevel(params1, params2));
}

}  // namespace webrtc
