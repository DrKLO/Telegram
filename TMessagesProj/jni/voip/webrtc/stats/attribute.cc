/*
 *  Copyright 2024 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/stats/attribute.h"

#include <string>

#include "absl/types/variant.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

namespace {

struct VisitIsSequence {
  // Any type of vector is a sequence.
  template <typename T>
  bool operator()(const absl::optional<std::vector<T>>* attribute) {
    return true;
  }
  // Any other type is not.
  template <typename T>
  bool operator()(const absl::optional<T>* attribute) {
    return false;
  }
};

// Converts the attribute to string in a JSON-compatible way.
struct VisitToString {
  template <typename T,
            typename std::enable_if_t<
                std::is_same_v<T, int32_t> || std::is_same_v<T, uint32_t> ||
                    std::is_same_v<T, bool> || std::is_same_v<T, std::string>,
                bool> = true>
  std::string ValueToString(const T& value) {
    return rtc::ToString(value);
  }
  // Convert 64-bit integers to doubles before converting to string because JSON
  // represents all numbers as floating points with ~15 digits of precision.
  template <typename T,
            typename std::enable_if_t<std::is_same_v<T, int64_t> ||
                                          std::is_same_v<T, uint64_t> ||
                                          std::is_same_v<T, double>,
                                      bool> = true>
  std::string ValueToString(const T& value) {
    char buf[32];
    const int len = std::snprintf(&buf[0], arraysize(buf), "%.16g",
                                  static_cast<double>(value));
    RTC_DCHECK_LE(len, arraysize(buf));
    return std::string(&buf[0], len);
  }

  // Vector attributes.
  template <typename T>
  std::string operator()(const absl::optional<std::vector<T>>* attribute) {
    rtc::StringBuilder sb;
    sb << "[";
    const char* separator = "";
    constexpr bool element_is_string = std::is_same<T, std::string>::value;
    for (const T& element : attribute->value()) {
      sb << separator;
      if (element_is_string) {
        sb << "\"";
      }
      sb << ValueToString(element);
      if (element_is_string) {
        sb << "\"";
      }
      separator = ",";
    }
    sb << "]";
    return sb.Release();
  }
  // Map attributes.
  template <typename T>
  std::string operator()(
      const absl::optional<std::map<std::string, T>>* attribute) {
    rtc::StringBuilder sb;
    sb << "{";
    const char* separator = "";
    constexpr bool element_is_string = std::is_same<T, std::string>::value;
    for (const auto& pair : attribute->value()) {
      sb << separator;
      sb << "\"" << pair.first << "\":";
      if (element_is_string) {
        sb << "\"";
      }
      sb << ValueToString(pair.second);
      if (element_is_string) {
        sb << "\"";
      }
      separator = ",";
    }
    sb << "}";
    return sb.Release();
  }
  // Simple attributes.
  template <typename T>
  std::string operator()(const absl::optional<T>* attribute) {
    return ValueToString(attribute->value());
  }
};

struct VisitIsEqual {
  template <typename T>
  bool operator()(const absl::optional<T>* attribute) {
    if (!other.holds_alternative<T>()) {
      return false;
    }
    return *attribute == other.as_optional<T>();
  }

  const Attribute& other;
};

}  // namespace

const char* Attribute::name() const {
  return name_;
}

const Attribute::StatVariant& Attribute::as_variant() const {
  return attribute_;
}

bool Attribute::has_value() const {
  return absl::visit([](const auto* attr) { return attr->has_value(); },
                     attribute_);
}

bool Attribute::is_sequence() const {
  return absl::visit(VisitIsSequence(), attribute_);
}

bool Attribute::is_string() const {
  return absl::holds_alternative<const absl::optional<std::string>*>(
      attribute_);
}

std::string Attribute::ToString() const {
  if (!has_value()) {
    return "null";
  }
  return absl::visit(VisitToString(), attribute_);
}

bool Attribute::operator==(const Attribute& other) const {
  return absl::visit(VisitIsEqual{.other = other}, attribute_);
}

bool Attribute::operator!=(const Attribute& other) const {
  return !(*this == other);
}

AttributeInit::AttributeInit(const char* name,
                             const Attribute::StatVariant& variant)
    : name(name), variant(variant) {}

}  // namespace webrtc
