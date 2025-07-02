/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/used_ids.h"

#include "absl/strings/string_view.h"
#include "test/gtest.h"

using cricket::UsedIds;
using cricket::UsedRtpHeaderExtensionIds;

struct Foo {
  int id;
};

TEST(UsedIdsTest, UniqueIdsAreUnchanged) {
  UsedIds<Foo> used_ids(1, 5);
  for (int i = 1; i <= 5; ++i) {
    Foo id = {i};
    used_ids.FindAndSetIdUsed(&id);
    EXPECT_EQ(id.id, i);
  }
}

TEST(UsedIdsTest, IdsOutsideRangeAreUnchanged) {
  UsedIds<Foo> used_ids(1, 5);

  Foo id_11 = {11};
  Foo id_12 = {12};
  Foo id_12_collision = {12};
  Foo id_13 = {13};
  Foo id_13_collision = {13};

  used_ids.FindAndSetIdUsed(&id_11);
  EXPECT_EQ(id_11.id, 11);
  used_ids.FindAndSetIdUsed(&id_12);
  EXPECT_EQ(id_12.id, 12);
  used_ids.FindAndSetIdUsed(&id_12_collision);
  EXPECT_EQ(id_12_collision.id, 12);
  used_ids.FindAndSetIdUsed(&id_13);
  EXPECT_EQ(id_13.id, 13);
  used_ids.FindAndSetIdUsed(&id_13_collision);
  EXPECT_EQ(id_13_collision.id, 13);
}

TEST(UsedIdsTest, CollisionsAreReassignedIdsInReverseOrder) {
  UsedIds<Foo> used_ids(1, 10);
  Foo id_1 = {1};
  Foo id_2 = {2};
  Foo id_2_collision = {2};
  Foo id_3 = {3};
  Foo id_3_collision = {3};

  used_ids.FindAndSetIdUsed(&id_1);
  used_ids.FindAndSetIdUsed(&id_2);
  used_ids.FindAndSetIdUsed(&id_2_collision);
  EXPECT_EQ(id_2_collision.id, 10);
  used_ids.FindAndSetIdUsed(&id_3);
  used_ids.FindAndSetIdUsed(&id_3_collision);
  EXPECT_EQ(id_3_collision.id, 9);
}

struct TestParams {
  UsedRtpHeaderExtensionIds::IdDomain id_domain;
  int max_id;
};

class UsedRtpHeaderExtensionIdsTest
    : public ::testing::TestWithParam<TestParams> {};

constexpr TestParams kOneByteTestParams = {
    UsedRtpHeaderExtensionIds::IdDomain::kOneByteOnly, 14};
constexpr TestParams kTwoByteTestParams = {
    UsedRtpHeaderExtensionIds::IdDomain::kTwoByteAllowed, 255};

INSTANTIATE_TEST_SUITE_P(All,
                         UsedRtpHeaderExtensionIdsTest,
                         ::testing::Values(kOneByteTestParams,
                                           kTwoByteTestParams));

TEST_P(UsedRtpHeaderExtensionIdsTest, UniqueIdsAreUnchanged) {
  UsedRtpHeaderExtensionIds used_ids(GetParam().id_domain);

  // Fill all IDs.
  for (int j = 1; j <= GetParam().max_id; ++j) {
    webrtc::RtpExtension extension("", j);
    used_ids.FindAndSetIdUsed(&extension);
    EXPECT_EQ(extension.id, j);
  }
}

TEST_P(UsedRtpHeaderExtensionIdsTest, PrioritizeReassignmentToOneByteIds) {
  UsedRtpHeaderExtensionIds used_ids(GetParam().id_domain);
  webrtc::RtpExtension id_1("", 1);
  webrtc::RtpExtension id_2("", 2);
  webrtc::RtpExtension id_2_collision("", 2);
  webrtc::RtpExtension id_3("", 3);
  webrtc::RtpExtension id_3_collision("", 3);

  // Expect that colliding IDs are reassigned to one-byte IDs.
  used_ids.FindAndSetIdUsed(&id_1);
  used_ids.FindAndSetIdUsed(&id_2);
  used_ids.FindAndSetIdUsed(&id_2_collision);
  EXPECT_EQ(id_2_collision.id, 14);
  used_ids.FindAndSetIdUsed(&id_3);
  used_ids.FindAndSetIdUsed(&id_3_collision);
  EXPECT_EQ(id_3_collision.id, 13);
}

TEST_F(UsedRtpHeaderExtensionIdsTest, TwoByteIdsAllowed) {
  UsedRtpHeaderExtensionIds used_ids(
      UsedRtpHeaderExtensionIds::IdDomain::kTwoByteAllowed);

  // Fill all one byte IDs.
  for (int i = 1; i <= webrtc::RtpExtension::kOneByteHeaderExtensionMaxId;
       ++i) {
    webrtc::RtpExtension id("", i);
    used_ids.FindAndSetIdUsed(&id);
  }

  // Add new extensions with colliding IDs.
  webrtc::RtpExtension id1_collision("", 1);
  webrtc::RtpExtension id2_collision("", 2);
  webrtc::RtpExtension id3_collision("", 3);

  // Expect to reassign to two-byte header extension IDs.
  used_ids.FindAndSetIdUsed(&id1_collision);
  EXPECT_EQ(id1_collision.id, 16);
  used_ids.FindAndSetIdUsed(&id2_collision);
  EXPECT_EQ(id2_collision.id, 17);
  used_ids.FindAndSetIdUsed(&id3_collision);
  EXPECT_EQ(id3_collision.id, 18);
}

// Death tests.
// Disabled on Android because death tests misbehave on Android, see
// base/test/gtest_util.h.
#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST(UsedIdsDeathTest, DieWhenAllIdsAreOccupied) {
  UsedIds<Foo> used_ids(1, 5);
  for (int i = 1; i <= 5; ++i) {
    Foo id = {i};
    used_ids.FindAndSetIdUsed(&id);
  }
  Foo id_collision = {3};
  EXPECT_DEATH(used_ids.FindAndSetIdUsed(&id_collision), "");
}

using UsedRtpHeaderExtensionIdsDeathTest = UsedRtpHeaderExtensionIdsTest;
INSTANTIATE_TEST_SUITE_P(All,
                         UsedRtpHeaderExtensionIdsDeathTest,
                         ::testing::Values(kOneByteTestParams,
                                           kTwoByteTestParams));

TEST_P(UsedRtpHeaderExtensionIdsDeathTest, DieWhenAllIdsAreOccupied) {
  UsedRtpHeaderExtensionIds used_ids(GetParam().id_domain);

  // Fill all IDs.
  for (int j = 1; j <= GetParam().max_id; ++j) {
    webrtc::RtpExtension id("", j);
    used_ids.FindAndSetIdUsed(&id);
  }

  webrtc::RtpExtension id1_collision("", 1);
  webrtc::RtpExtension id2_collision("", 2);
  webrtc::RtpExtension id3_collision("", GetParam().max_id);

  EXPECT_DEATH(used_ids.FindAndSetIdUsed(&id1_collision), "");
  EXPECT_DEATH(used_ids.FindAndSetIdUsed(&id2_collision), "");
  EXPECT_DEATH(used_ids.FindAndSetIdUsed(&id3_collision), "");
}
#endif  // RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
