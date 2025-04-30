/*
 *  Copyright 2024 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_STATS_ATTRIBUTE_H_
#define API_STATS_ATTRIBUTE_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "absl/types/variant.h"
#include "rtc_base/checks.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// A light-weight wrapper of an RTCStats attribute, i.e. an individual metric of
// type absl::optional<T>.
class RTC_EXPORT Attribute {
 public:
  // All supported attribute types.
  typedef absl::variant<const absl::optional<bool>*,
                        const absl::optional<int32_t>*,
                        const absl::optional<uint32_t>*,
                        const absl::optional<int64_t>*,
                        const absl::optional<uint64_t>*,
                        const absl::optional<double>*,
                        const absl::optional<std::string>*,
                        const absl::optional<std::vector<bool>>*,
                        const absl::optional<std::vector<int32_t>>*,
                        const absl::optional<std::vector<uint32_t>>*,
                        const absl::optional<std::vector<int64_t>>*,
                        const absl::optional<std::vector<uint64_t>>*,
                        const absl::optional<std::vector<double>>*,
                        const absl::optional<std::vector<std::string>>*,
                        const absl::optional<std::map<std::string, uint64_t>>*,
                        const absl::optional<std::map<std::string, double>>*>
      StatVariant;

  template <typename T>
  Attribute(const char* name, const absl::optional<T>* attribute)
      : name_(name), attribute_(attribute) {}

  const char* name() const;
  const StatVariant& as_variant() const;

  bool has_value() const;
  template <typename T>
  bool holds_alternative() const {
    return absl::holds_alternative<const absl::optional<T>*>(attribute_);
  }
  template <typename T>
  const absl::optional<T>& as_optional() const {
    RTC_CHECK(holds_alternative<T>());
    return *absl::get<const absl::optional<T>*>(attribute_);
  }
  template <typename T>
  const T& get() const {
    RTC_CHECK(holds_alternative<T>());
    RTC_CHECK(has_value());
    return absl::get<const absl::optional<T>*>(attribute_)->value();
  }

  bool is_sequence() const;
  bool is_string() const;
  std::string ToString() const;

  bool operator==(const Attribute& other) const;
  bool operator!=(const Attribute& other) const;

 private:
  const char* name_;
  StatVariant attribute_;
};

struct RTC_EXPORT AttributeInit {
  AttributeInit(const char* name, const Attribute::StatVariant& variant);

  const char* name;
  Attribute::StatVariant variant;
};

}  // namespace webrtc

#endif  // API_STATS_ATTRIBUTE_H_
