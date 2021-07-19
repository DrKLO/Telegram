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

#include "absl/time/internal/test_util.h"

#include <algorithm>
#include <cstddef>
#include <cstring>

#include "absl/base/internal/raw_logging.h"
#include "absl/time/internal/cctz/include/cctz/zone_info_source.h"

namespace cctz = absl::time_internal::cctz;

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace time_internal {

TimeZone LoadTimeZone(const std::string& name) {
  TimeZone tz;
  ABSL_RAW_CHECK(LoadTimeZone(name, &tz), name.c_str());
  return tz;
}

}  // namespace time_internal
ABSL_NAMESPACE_END
}  // namespace absl

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace time_internal {
namespace cctz_extension {
namespace {

// Embed the zoneinfo data for time zones used during tests and benchmarks.
// The data was generated using "xxd -i zoneinfo-file".  There is no need
// to update the data as long as the tests do not depend on recent changes
// (and the past rules remain the same).
#include "absl/time/internal/zoneinfo.inc"

const struct ZoneInfo {
  const char* name;
  const char* data;
  std::size_t length;
} kZoneInfo[] = {
    // The three real time zones used by :time_test and :time_benchmark.
    {"America/Los_Angeles",  //
     reinterpret_cast<char*>(America_Los_Angeles), America_Los_Angeles_len},
    {"America/New_York",  //
     reinterpret_cast<char*>(America_New_York), America_New_York_len},
    {"Australia/Sydney",  //
     reinterpret_cast<char*>(Australia_Sydney), Australia_Sydney_len},

    // Other zones named in tests but which should fail to load.
    {"Invalid/TimeZone", nullptr, 0},
    {"", nullptr, 0},

    // Also allow for loading the local time zone under TZ=US/Pacific.
    {"US/Pacific",  //
     reinterpret_cast<char*>(America_Los_Angeles), America_Los_Angeles_len},

    // Allows use of the local time zone from a system-specific location.
#ifdef _MSC_VER
    {"localtime",  //
     reinterpret_cast<char*>(America_Los_Angeles), America_Los_Angeles_len},
#else
    {"/etc/localtime",  //
     reinterpret_cast<char*>(America_Los_Angeles), America_Los_Angeles_len},
#endif
};

class TestZoneInfoSource : public cctz::ZoneInfoSource {
 public:
  TestZoneInfoSource(const char* data, std::size_t size)
      : data_(data), end_(data + size) {}

  std::size_t Read(void* ptr, std::size_t size) override {
    const std::size_t len = std::min<std::size_t>(size, end_ - data_);
    memcpy(ptr, data_, len);
    data_ += len;
    return len;
  }

  int Skip(std::size_t offset) override {
    data_ += std::min<std::size_t>(offset, end_ - data_);
    return 0;
  }

 private:
  const char* data_;
  const char* const end_;
};

std::unique_ptr<cctz::ZoneInfoSource> TestFactory(
    const std::string& name,
    const std::function<std::unique_ptr<cctz::ZoneInfoSource>(
        const std::string& name)>& /*fallback_factory*/) {
  for (const ZoneInfo& zoneinfo : kZoneInfo) {
    if (name == zoneinfo.name) {
      if (zoneinfo.data == nullptr) return nullptr;
      return std::unique_ptr<cctz::ZoneInfoSource>(
          new TestZoneInfoSource(zoneinfo.data, zoneinfo.length));
    }
  }
  ABSL_RAW_LOG(FATAL, "Unexpected time zone \"%s\" in test", name.c_str());
  return nullptr;
}

}  // namespace

#if !defined(__MINGW32__)
// MinGW does not support the weak symbol extension mechanism.
ZoneInfoSourceFactory zone_info_source_factory = TestFactory;
#endif

}  // namespace cctz_extension
}  // namespace time_internal
ABSL_NAMESPACE_END
}  // namespace absl
