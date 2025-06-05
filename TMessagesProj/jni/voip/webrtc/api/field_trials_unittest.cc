/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/field_trials.h"

#include <memory>
#include <utility>

#include "absl/strings/string_view.h"
#include "api/transport/field_trial_based_config.h"
#include "rtc_base/containers/flat_set.h"
#include "system_wrappers/include/field_trial.h"
#include "test/field_trial.h"
#include "test/gmock.h"
#include "test/gtest.h"

#if GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
#include "test/testsupport/rtc_expect_death.h"
#endif  // GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

namespace webrtc {
namespace {

using ::testing::NotNull;
using ::webrtc::field_trial::FieldTrialsAllowedInScopeForTesting;
using ::webrtc::test::ScopedFieldTrials;

TEST(FieldTrialsTest, EmptyStringHasNoEffect) {
  FieldTrialsAllowedInScopeForTesting k({"MyCoolTrial"});
  FieldTrials f("");
  f.RegisterKeysForTesting({"MyCoolTrial"});

  EXPECT_FALSE(f.IsEnabled("MyCoolTrial"));
  EXPECT_FALSE(f.IsDisabled("MyCoolTrial"));
}

TEST(FieldTrialsTest, EnabledDisabledMustBeFirstInValue) {
  FieldTrials f(
      "MyCoolTrial/EnabledFoo/"
      "MyUncoolTrial/DisabledBar/"
      "AnotherTrial/BazEnabled/");
  f.RegisterKeysForTesting({"MyCoolTrial", "MyUncoolTrial", "AnotherTrial"});

  EXPECT_TRUE(f.IsEnabled("MyCoolTrial"));
  EXPECT_TRUE(f.IsDisabled("MyUncoolTrial"));
  EXPECT_FALSE(f.IsEnabled("AnotherTrial"));
}

TEST(FieldTrialsTest, FieldTrialsDoesNotReadGlobalString) {
  FieldTrialsAllowedInScopeForTesting k({"MyCoolTrial", "MyUncoolTrial"});
  ScopedFieldTrials g("MyCoolTrial/Enabled/MyUncoolTrial/Disabled/");
  FieldTrials f("");
  f.RegisterKeysForTesting({"MyCoolTrial", "MyUncoolTrial"});

  EXPECT_FALSE(f.IsEnabled("MyCoolTrial"));
  EXPECT_FALSE(f.IsDisabled("MyUncoolTrial"));
}

TEST(FieldTrialsTest, FieldTrialsWritesGlobalString) {
  FieldTrialsAllowedInScopeForTesting k({"MyCoolTrial", "MyUncoolTrial"});
  FieldTrials f("MyCoolTrial/Enabled/MyUncoolTrial/Disabled/");
  EXPECT_TRUE(webrtc::field_trial::IsEnabled("MyCoolTrial"));
  EXPECT_TRUE(webrtc::field_trial::IsDisabled("MyUncoolTrial"));
}

TEST(FieldTrialsTest, FieldTrialsRestoresGlobalStringAfterDestruction) {
  static constexpr char s[] = "SomeString/Enabled/";
  ScopedFieldTrials g(s);
  {
    FieldTrials f("SomeOtherString/Enabled/");
    EXPECT_STREQ(webrtc::field_trial::GetFieldTrialString(),
                 "SomeOtherString/Enabled/");
  }
  EXPECT_STREQ(webrtc::field_trial::GetFieldTrialString(), s);
}

#if GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST(FieldTrialsTest, FieldTrialsDoesNotSupportSimultaneousInstances) {
  FieldTrials f("SomeString/Enabled/");
  RTC_EXPECT_DEATH(FieldTrials("SomeOtherString/Enabled/").Lookup("Whatever"),
                   "Only one instance");
}
#endif  // GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

TEST(FieldTrialsTest, FieldTrialsSupportsSeparateInstances) {
  { FieldTrials f("SomeString/Enabled/"); }
  { FieldTrials f("SomeOtherString/Enabled/"); }
}

TEST(FieldTrialsTest, NonGlobalFieldTrialsInstanceDoesNotModifyGlobalString) {
  FieldTrialsAllowedInScopeForTesting k({"SomeString"});
  std::unique_ptr<FieldTrials> f =
      FieldTrials::CreateNoGlobal("SomeString/Enabled/");
  ASSERT_THAT(f, NotNull());
  f->RegisterKeysForTesting({"SomeString"});

  EXPECT_TRUE(f->IsEnabled("SomeString"));
  EXPECT_FALSE(webrtc::field_trial::IsEnabled("SomeString"));
}

TEST(FieldTrialsTest, NonGlobalFieldTrialsSupportSimultaneousInstances) {
  std::unique_ptr<FieldTrials> f1 =
      FieldTrials::CreateNoGlobal("SomeString/Enabled/");
  std::unique_ptr<FieldTrials> f2 =
      FieldTrials::CreateNoGlobal("SomeOtherString/Enabled/");
  ASSERT_THAT(f1, NotNull());
  ASSERT_THAT(f2, NotNull());
  f1->RegisterKeysForTesting({"SomeString", "SomeOtherString"});
  f2->RegisterKeysForTesting({"SomeString", "SomeOtherString"});

  EXPECT_TRUE(f1->IsEnabled("SomeString"));
  EXPECT_FALSE(f1->IsEnabled("SomeOtherString"));

  EXPECT_FALSE(f2->IsEnabled("SomeString"));
  EXPECT_TRUE(f2->IsEnabled("SomeOtherString"));
}

TEST(FieldTrialsTest, GlobalAndNonGlobalFieldTrialsAreDisjoint) {
  FieldTrialsAllowedInScopeForTesting k({"SomeString", "SomeOtherString"});
  FieldTrials f1("SomeString/Enabled/");
  std::unique_ptr<FieldTrials> f2 =
      FieldTrials::CreateNoGlobal("SomeOtherString/Enabled/");
  ASSERT_THAT(f2, NotNull());
  f1.RegisterKeysForTesting({"SomeString", "SomeOtherString"});
  f2->RegisterKeysForTesting({"SomeString", "SomeOtherString"});

  EXPECT_TRUE(f1.IsEnabled("SomeString"));
  EXPECT_FALSE(f1.IsEnabled("SomeOtherString"));

  EXPECT_FALSE(f2->IsEnabled("SomeString"));
  EXPECT_TRUE(f2->IsEnabled("SomeOtherString"));
}

TEST(FieldTrialsTest, FieldTrialBasedConfigReadsGlobalString) {
  FieldTrialsAllowedInScopeForTesting k({"MyCoolTrial", "MyUncoolTrial"});
  ScopedFieldTrials g("MyCoolTrial/Enabled/MyUncoolTrial/Disabled/");
  FieldTrialBasedConfig f;
  f.RegisterKeysForTesting({"MyCoolTrial", "MyUncoolTrial"});

  EXPECT_TRUE(f.IsEnabled("MyCoolTrial"));
  EXPECT_TRUE(f.IsDisabled("MyUncoolTrial"));
}

}  // namespace
}  // namespace webrtc
