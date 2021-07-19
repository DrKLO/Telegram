/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_GUNIT_H_
#define RTC_BASE_GUNIT_H_

#include "rtc_base/fake_clock.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread.h"
#include "test/gtest.h"

// Wait until "ex" is true, or "timeout" expires.
#define WAIT(ex, timeout)                                       \
  for (int64_t start = rtc::SystemTimeMillis();                 \
       !(ex) && rtc::SystemTimeMillis() < start + (timeout);) { \
    rtc::Thread::Current()->ProcessMessages(0);                 \
    rtc::Thread::Current()->SleepMs(1);                         \
  }

// This returns the result of the test in res, so that we don't re-evaluate
// the expression in the XXXX_WAIT macros below, since that causes problems
// when the expression is only true the first time you check it.
#define WAIT_(ex, timeout, res)                                   \
  do {                                                            \
    int64_t start = rtc::SystemTimeMillis();                      \
    res = (ex);                                                   \
    while (!res && rtc::SystemTimeMillis() < start + (timeout)) { \
      rtc::Thread::Current()->ProcessMessages(0);                 \
      rtc::Thread::Current()->SleepMs(1);                         \
      res = (ex);                                                 \
    }                                                             \
  } while (0)

// The typical EXPECT_XXXX and ASSERT_XXXXs, but done until true or a timeout.
// One can add failure message by appending "<< msg".
#define EXPECT_TRUE_WAIT(ex, timeout)                   \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                         \
  if (bool res = true) {                                \
    WAIT_(ex, timeout, res);                            \
    if (!res)                                           \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__); \
  } else                                                \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : EXPECT_TRUE(ex)

#define EXPECT_EQ_WAIT(v1, v2, timeout)                 \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                         \
  if (bool res = true) {                                \
    WAIT_(v1 == v2, timeout, res);                      \
    if (!res)                                           \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__); \
  } else                                                \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : EXPECT_EQ(v1, v2)

#define ASSERT_TRUE_WAIT(ex, timeout)                   \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                         \
  if (bool res = true) {                                \
    WAIT_(ex, timeout, res);                            \
    if (!res)                                           \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__); \
  } else                                                \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : ASSERT_TRUE(ex)

#define ASSERT_EQ_WAIT(v1, v2, timeout)                 \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                         \
  if (bool res = true) {                                \
    WAIT_(v1 == v2, timeout, res);                      \
    if (!res)                                           \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__); \
  } else                                                \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : ASSERT_EQ(v1, v2)

// Version with a "soft" timeout and a margin. This logs if the timeout is
// exceeded, but it only fails if the expression still isn't true after the
// margin time passes.
#define EXPECT_TRUE_WAIT_MARGIN(ex, timeout, margin)                           \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                                                \
  if (bool res = true) {                                                       \
    WAIT_(ex, timeout, res);                                                   \
    if (res)                                                                   \
      break;                                                                   \
    RTC_LOG(LS_WARNING) << "Expression " << #ex << " still not true after "    \
                        << (timeout) << "ms; waiting an additional " << margin \
                        << "ms";                                               \
    WAIT_(ex, margin, res);                                                    \
    if (!res)                                                                  \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__);                        \
  } else                                                                       \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : EXPECT_TRUE(ex)

// Wait until "ex" is true, or "timeout" expires, using fake clock where
// messages are processed every millisecond.
// TODO(pthatcher): Allow tests to control how many milliseconds to advance.
#define SIMULATED_WAIT(ex, timeout, clock)                \
  for (int64_t start = rtc::TimeMillis();                 \
       !(ex) && rtc::TimeMillis() < start + (timeout);) { \
    (clock).AdvanceTime(webrtc::TimeDelta::Millis(1));    \
  }

// This returns the result of the test in res, so that we don't re-evaluate
// the expression in the XXXX_WAIT macros below, since that causes problems
// when the expression is only true the first time you check it.
#define SIMULATED_WAIT_(ex, timeout, res, clock)            \
  do {                                                      \
    int64_t start = rtc::TimeMillis();                      \
    res = (ex);                                             \
    while (!res && rtc::TimeMillis() < start + (timeout)) { \
      (clock).AdvanceTime(webrtc::TimeDelta::Millis(1));    \
      res = (ex);                                           \
    }                                                       \
  } while (0)

// The typical EXPECT_XXXX, but done until true or a timeout with a fake clock.
#define EXPECT_TRUE_SIMULATED_WAIT(ex, timeout, clock) \
  do {                                                 \
    bool res;                                          \
    SIMULATED_WAIT_(ex, timeout, res, clock);          \
    if (!res) {                                        \
      EXPECT_TRUE(ex);                                 \
    }                                                  \
  } while (0)

#define EXPECT_EQ_SIMULATED_WAIT(v1, v2, timeout, clock) \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                          \
  if (bool res = true) {                                 \
    SIMULATED_WAIT_(v1 == v2, timeout, res, clock);      \
    if (!res)                                            \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__);  \
  } else                                                 \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : EXPECT_EQ(v1, v2)

#define ASSERT_TRUE_SIMULATED_WAIT(ex, timeout, clock)  \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                         \
  if (bool res = true) {                                \
    SIMULATED_WAIT_(ex, timeout, res, clock);           \
    if (!res)                                           \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__); \
  } else                                                \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : ASSERT_TRUE(ex)

#define ASSERT_EQ_SIMULATED_WAIT(v1, v2, timeout, clock) \
  GTEST_AMBIGUOUS_ELSE_BLOCKER_                          \
  if (bool res = true) {                                 \
    SIMULATED_WAIT_(v1 == v2, timeout, res, clock);      \
    if (!res)                                            \
      goto GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__);  \
  } else                                                 \
    GTEST_CONCAT_TOKEN_(gunit_label_, __LINE__) : ASSERT_EQ(v1, v2)

// Usage: EXPECT_PRED_FORMAT2(AssertStartsWith, text, "prefix");
testing::AssertionResult AssertStartsWith(const char* text_expr,
                                          const char* prefix_expr,
                                          absl::string_view text,
                                          absl::string_view prefix);

// Usage: EXPECT_PRED_FORMAT2(AssertStringContains, str, "substring");
testing::AssertionResult AssertStringContains(const char* str_expr,
                                              const char* substr_expr,
                                              const std::string& str,
                                              const std::string& substr);

#endif  // RTC_BASE_GUNIT_H_
