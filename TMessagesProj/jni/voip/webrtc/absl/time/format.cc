// Copyright 2017 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <string.h>
#include <cctype>
#include <cstdint>

#include "absl/time/internal/cctz/include/cctz/time_zone.h"
#include "absl/time/time.h"

namespace cctz = absl::time_internal::cctz;

namespace absl {
ABSL_NAMESPACE_BEGIN

ABSL_DLL extern const char RFC3339_full[] =
    "%Y-%m-%dT%H:%M:%E*S%Ez";
ABSL_DLL extern const char RFC3339_sec[] = "%Y-%m-%dT%H:%M:%S%Ez";

ABSL_DLL extern const char RFC1123_full[] =
    "%a, %d %b %E4Y %H:%M:%S %z";
ABSL_DLL extern const char RFC1123_no_wday[] =
    "%d %b %E4Y %H:%M:%S %z";

namespace {

const char kInfiniteFutureStr[] = "infinite-future";
const char kInfinitePastStr[] = "infinite-past";

struct cctz_parts {
  cctz::time_point<cctz::seconds> sec;
  cctz::detail::femtoseconds fem;
};

inline cctz::time_point<cctz::seconds> unix_epoch() {
  return std::chrono::time_point_cast<cctz::seconds>(
      std::chrono::system_clock::from_time_t(0));
}

// Splits a Time into seconds and femtoseconds, which can be used with CCTZ.
// Requires that 't' is finite. See duration.cc for details about rep_hi and
// rep_lo.
cctz_parts Split(absl::Time t) {
  const auto d = time_internal::ToUnixDuration(t);
  const int64_t rep_hi = time_internal::GetRepHi(d);
  const int64_t rep_lo = time_internal::GetRepLo(d);
  const auto sec = unix_epoch() + cctz::seconds(rep_hi);
  const auto fem = cctz::detail::femtoseconds(rep_lo * (1000 * 1000 / 4));
  return {sec, fem};
}

// Joins the given seconds and femtoseconds into a Time. See duration.cc for
// details about rep_hi and rep_lo.
absl::Time Join(const cctz_parts& parts) {
  const int64_t rep_hi = (parts.sec - unix_epoch()).count();
  const uint32_t rep_lo = parts.fem.count() / (1000 * 1000 / 4);
  const auto d = time_internal::MakeDuration(rep_hi, rep_lo);
  return time_internal::FromUnixDuration(d);
}

}  // namespace

std::string FormatTime(const std::string& format, absl::Time t,
                       absl::TimeZone tz) {
  if (t == absl::InfiniteFuture()) return kInfiniteFutureStr;
  if (t == absl::InfinitePast()) return kInfinitePastStr;
  const auto parts = Split(t);
  return cctz::detail::format(format, parts.sec, parts.fem,
                              cctz::time_zone(tz));
}

std::string FormatTime(absl::Time t, absl::TimeZone tz) {
  return FormatTime(RFC3339_full, t, tz);
}

std::string FormatTime(absl::Time t) {
  return absl::FormatTime(RFC3339_full, t, absl::LocalTimeZone());
}

bool ParseTime(const std::string& format, const std::string& input,
               absl::Time* time, std::string* err) {
  return absl::ParseTime(format, input, absl::UTCTimeZone(), time, err);
}

// If the input string does not contain an explicit UTC offset, interpret
// the fields with respect to the given TimeZone.
bool ParseTime(const std::string& format, const std::string& input,
               absl::TimeZone tz, absl::Time* time, std::string* err) {
  const char* data = input.c_str();
  while (std::isspace(*data)) ++data;

  size_t inf_size = strlen(kInfiniteFutureStr);
  if (strncmp(data, kInfiniteFutureStr, inf_size) == 0) {
    const char* new_data = data + inf_size;
    while (std::isspace(*new_data)) ++new_data;
    if (*new_data == '\0') {
      *time = InfiniteFuture();
      return true;
    }
  }

  inf_size = strlen(kInfinitePastStr);
  if (strncmp(data, kInfinitePastStr, inf_size) == 0) {
    const char* new_data = data + inf_size;
    while (std::isspace(*new_data)) ++new_data;
    if (*new_data == '\0') {
      *time = InfinitePast();
      return true;
    }
  }

  std::string error;
  cctz_parts parts;
  const bool b = cctz::detail::parse(format, input, cctz::time_zone(tz),
                                     &parts.sec, &parts.fem, &error);
  if (b) {
    *time = Join(parts);
  } else if (err != nullptr) {
    *err = error;
  }
  return b;
}

// Functions required to support absl::Time flags.
bool AbslParseFlag(absl::string_view text, absl::Time* t, std::string* error) {
  return absl::ParseTime(RFC3339_full, std::string(text), absl::UTCTimeZone(),
                         t, error);
}

std::string AbslUnparseFlag(absl::Time t) {
  return absl::FormatTime(RFC3339_full, t, absl::UTCTimeZone());
}
bool ParseFlag(const std::string& text, absl::Time* t, std::string* error) {
  return absl::ParseTime(RFC3339_full, text, absl::UTCTimeZone(), t, error);
}

std::string UnparseFlag(absl::Time t) {
  return absl::FormatTime(RFC3339_full, t, absl::UTCTimeZone());
}

ABSL_NAMESPACE_END
}  // namespace absl
