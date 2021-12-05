// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/string_util.h"

#include <cinttypes>

#include "base/time/time.h"
#include "build/build_config.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace base {

template <typename String>
void MeasureIsStringASCII(size_t str_length, size_t non_ascii_pos) {
  String str(str_length, 'A');
  if (non_ascii_pos < str_length)
    str[non_ascii_pos] = '\xAF';

  TimeTicks t0 = TimeTicks::Now();
  for (size_t i = 0; i < 10000000; ++i)
    IsStringASCII(str);
  TimeDelta time = TimeTicks::Now() - t0;
  printf(
      "char-size:\t%zu\tlength:\t%zu\tnon-ascii-pos:\t%zu\ttime-ms:\t%" PRIu64
      "\n",
      sizeof(typename String::value_type), str_length, non_ascii_pos,
      time.InMilliseconds());
}

TEST(StringUtilTest, DISABLED_IsStringASCIIPerf) {
  for (size_t str_length = 4; str_length <= 1024; str_length *= 2) {
    for (size_t non_ascii_loc = 0; non_ascii_loc < 3; ++non_ascii_loc) {
      size_t non_ascii_pos = str_length * non_ascii_loc / 2 + 2;
      MeasureIsStringASCII<std::string>(str_length, non_ascii_pos);
      MeasureIsStringASCII<string16>(str_length, non_ascii_pos);
#if defined(WCHAR_T_IS_UTF32)
      MeasureIsStringASCII<std::basic_string<wchar_t>>(str_length,
                                                       non_ascii_pos);
#endif
    }
  }
}

}  // namespace base
