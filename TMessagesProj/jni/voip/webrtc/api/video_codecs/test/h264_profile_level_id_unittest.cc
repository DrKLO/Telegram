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

#include <map>
#include <string>

#include "absl/types/optional.h"
#include "test/gtest.h"

namespace webrtc {

TEST(H264ProfileLevelId, TestParsingInvalid) {
  // Malformed strings.
  EXPECT_FALSE(ParseH264ProfileLevelId(""));
  EXPECT_FALSE(ParseH264ProfileLevelId(" 42e01f"));
  EXPECT_FALSE(ParseH264ProfileLevelId("4242e01f"));
  EXPECT_FALSE(ParseH264ProfileLevelId("e01f"));
  EXPECT_FALSE(ParseH264ProfileLevelId("gggggg"));

  // Invalid level.
  EXPECT_FALSE(ParseH264ProfileLevelId("42e000"));
  EXPECT_FALSE(ParseH264ProfileLevelId("42e00f"));
  EXPECT_FALSE(ParseH264ProfileLevelId("42e0ff"));

  // Invalid profile.
  EXPECT_FALSE(ParseH264ProfileLevelId("42e11f"));
  EXPECT_FALSE(ParseH264ProfileLevelId("58601f"));
  EXPECT_FALSE(ParseH264ProfileLevelId("64e01f"));
}

TEST(H264ProfileLevelId, TestParsingLevel) {
  EXPECT_EQ(H264Level::kLevel3_1, ParseH264ProfileLevelId("42e01f")->level);
  EXPECT_EQ(H264Level::kLevel1_1, ParseH264ProfileLevelId("42e00b")->level);
  EXPECT_EQ(H264Level::kLevel1_b, ParseH264ProfileLevelId("42f00b")->level);
  EXPECT_EQ(H264Level::kLevel4_2, ParseH264ProfileLevelId("42C02A")->level);
  EXPECT_EQ(H264Level::kLevel5_2, ParseH264ProfileLevelId("640c34")->level);
}

TEST(H264ProfileLevelId, TestParsingConstrainedBaseline) {
  EXPECT_EQ(H264Profile::kProfileConstrainedBaseline,
            ParseH264ProfileLevelId("42e01f")->profile);
  EXPECT_EQ(H264Profile::kProfileConstrainedBaseline,
            ParseH264ProfileLevelId("42C02A")->profile);
  EXPECT_EQ(H264Profile::kProfileConstrainedBaseline,
            ParseH264ProfileLevelId("4de01f")->profile);
  EXPECT_EQ(H264Profile::kProfileConstrainedBaseline,
            ParseH264ProfileLevelId("58f01f")->profile);
}

TEST(H264ProfileLevelId, TestParsingBaseline) {
  EXPECT_EQ(H264Profile::kProfileBaseline,
            ParseH264ProfileLevelId("42a01f")->profile);
  EXPECT_EQ(H264Profile::kProfileBaseline,
            ParseH264ProfileLevelId("58A01F")->profile);
}

TEST(H264ProfileLevelId, TestParsingMain) {
  EXPECT_EQ(H264Profile::kProfileMain,
            ParseH264ProfileLevelId("4D401f")->profile);
}

TEST(H264ProfileLevelId, TestParsingHigh) {
  EXPECT_EQ(H264Profile::kProfileHigh,
            ParseH264ProfileLevelId("64001f")->profile);
}

TEST(H264ProfileLevelId, TestParsingConstrainedHigh) {
  EXPECT_EQ(H264Profile::kProfileConstrainedHigh,
            ParseH264ProfileLevelId("640c1f")->profile);
}

TEST(H264ProfileLevelId, TestSupportedLevel) {
  EXPECT_EQ(H264Level::kLevel2_1, *H264SupportedLevel(640 * 480, 25));
  EXPECT_EQ(H264Level::kLevel3_1, *H264SupportedLevel(1280 * 720, 30));
  EXPECT_EQ(H264Level::kLevel4_2, *H264SupportedLevel(1920 * 1280, 60));
}

// Test supported level below level 1 requirements.
TEST(H264ProfileLevelId, TestSupportedLevelInvalid) {
  EXPECT_FALSE(H264SupportedLevel(0, 0));
  // All levels support fps > 5.
  EXPECT_FALSE(H264SupportedLevel(1280 * 720, 5));
  // All levels support frame sizes > 183 * 137.
  EXPECT_FALSE(H264SupportedLevel(183 * 137, 30));
}

TEST(H264ProfileLevelId, TestToString) {
  EXPECT_EQ("42e01f", *H264ProfileLevelIdToString(H264ProfileLevelId(
                          H264Profile::kProfileConstrainedBaseline,
                          H264Level::kLevel3_1)));
  EXPECT_EQ("42000a", *H264ProfileLevelIdToString(H264ProfileLevelId(
                          H264Profile::kProfileBaseline, H264Level::kLevel1)));
  EXPECT_EQ("4d001f", H264ProfileLevelIdToString(H264ProfileLevelId(
                          H264Profile::kProfileMain, H264Level::kLevel3_1)));
  EXPECT_EQ("640c2a",
            *H264ProfileLevelIdToString(H264ProfileLevelId(
                H264Profile::kProfileConstrainedHigh, H264Level::kLevel4_2)));
  EXPECT_EQ("64002a", *H264ProfileLevelIdToString(H264ProfileLevelId(
                          H264Profile::kProfileHigh, H264Level::kLevel4_2)));
}

TEST(H264ProfileLevelId, TestToStringLevel1b) {
  EXPECT_EQ("42f00b", *H264ProfileLevelIdToString(H264ProfileLevelId(
                          H264Profile::kProfileConstrainedBaseline,
                          H264Level::kLevel1_b)));
  EXPECT_EQ("42100b",
            *H264ProfileLevelIdToString(H264ProfileLevelId(
                H264Profile::kProfileBaseline, H264Level::kLevel1_b)));
  EXPECT_EQ("4d100b", *H264ProfileLevelIdToString(H264ProfileLevelId(
                          H264Profile::kProfileMain, H264Level::kLevel1_b)));
}

TEST(H264ProfileLevelId, TestToStringRoundTrip) {
  EXPECT_EQ("42e01f",
            *H264ProfileLevelIdToString(*ParseH264ProfileLevelId("42e01f")));
  EXPECT_EQ("42e01f",
            *H264ProfileLevelIdToString(*ParseH264ProfileLevelId("42E01F")));
  EXPECT_EQ("4d100b",
            *H264ProfileLevelIdToString(*ParseH264ProfileLevelId("4d100b")));
  EXPECT_EQ("4d100b",
            *H264ProfileLevelIdToString(*ParseH264ProfileLevelId("4D100B")));
  EXPECT_EQ("640c2a",
            *H264ProfileLevelIdToString(*ParseH264ProfileLevelId("640c2a")));
  EXPECT_EQ("640c2a",
            *H264ProfileLevelIdToString(*ParseH264ProfileLevelId("640C2A")));
}

TEST(H264ProfileLevelId, TestToStringInvalid) {
  EXPECT_FALSE(H264ProfileLevelIdToString(
      H264ProfileLevelId(H264Profile::kProfileHigh, H264Level::kLevel1_b)));
  EXPECT_FALSE(H264ProfileLevelIdToString(H264ProfileLevelId(
      H264Profile::kProfileConstrainedHigh, H264Level::kLevel1_b)));
  EXPECT_FALSE(H264ProfileLevelIdToString(
      H264ProfileLevelId(static_cast<H264Profile>(255), H264Level::kLevel3_1)));
}

TEST(H264ProfileLevelId, TestParseSdpProfileLevelIdEmpty) {
  const absl::optional<H264ProfileLevelId> profile_level_id =
      ParseSdpForH264ProfileLevelId(CodecParameterMap());
  EXPECT_TRUE(profile_level_id);
  EXPECT_EQ(H264Profile::kProfileConstrainedBaseline,
            profile_level_id->profile);
  EXPECT_EQ(H264Level::kLevel3_1, profile_level_id->level);
}

TEST(H264ProfileLevelId, TestParseSdpProfileLevelIdConstrainedHigh) {
  CodecParameterMap params;
  params["profile-level-id"] = "640c2a";
  const absl::optional<H264ProfileLevelId> profile_level_id =
      ParseSdpForH264ProfileLevelId(params);
  EXPECT_TRUE(profile_level_id);
  EXPECT_EQ(H264Profile::kProfileConstrainedHigh, profile_level_id->profile);
  EXPECT_EQ(H264Level::kLevel4_2, profile_level_id->level);
}

TEST(H264ProfileLevelId, TestParseSdpProfileLevelIdInvalid) {
  CodecParameterMap params;
  params["profile-level-id"] = "foobar";
  EXPECT_FALSE(ParseSdpForH264ProfileLevelId(params));
}

}  // namespace webrtc
