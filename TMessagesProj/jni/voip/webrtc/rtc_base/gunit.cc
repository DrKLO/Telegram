/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/gunit.h"

#include <string>

#include "absl/strings/match.h"
#include "absl/strings/string_view.h"

::testing::AssertionResult AssertStartsWith(const char* text_expr,
                                            const char* prefix_expr,
                                            absl::string_view text,
                                            absl::string_view prefix) {
  if (absl::StartsWith(text, prefix)) {
    return ::testing::AssertionSuccess();
  } else {
    return ::testing::AssertionFailure()
           << text_expr << "\nwhich is\n\"" << text
           << "\"\ndoes not start with\n"
           << prefix_expr << "\nwhich is\n\"" << prefix << "\"";
  }
}

::testing::AssertionResult AssertStringContains(const char* str_expr,
                                                const char* substr_expr,
                                                absl::string_view str,
                                                absl::string_view substr) {
  if (str.find(substr) != absl::string_view::npos) {
    return ::testing::AssertionSuccess();
  } else {
    return ::testing::AssertionFailure()
           << str_expr << "\nwhich is\n\"" << str << "\"\ndoes not contain\n"
           << substr_expr << "\nwhich is\n\"" << substr << "\"";
  }
}
