/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/experiments/field_trial_parser.h"

#include <inttypes.h>

#include <algorithm>
#include <map>
#include <type_traits>
#include <utility>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

FieldTrialParameterInterface::FieldTrialParameterInterface(std::string key)
    : key_(key) {}
FieldTrialParameterInterface::~FieldTrialParameterInterface() {
  RTC_DCHECK(used_) << "Field trial parameter with key: '" << key_
                    << "' never used.";
}

void ParseFieldTrial(
    std::initializer_list<FieldTrialParameterInterface*> fields,
    absl::string_view trial_string) {
  std::map<absl::string_view, FieldTrialParameterInterface*> field_map;
  FieldTrialParameterInterface* keyless_field = nullptr;
  for (FieldTrialParameterInterface* field : fields) {
    field->MarkAsUsed();
    if (!field->sub_parameters_.empty()) {
      for (FieldTrialParameterInterface* sub_field : field->sub_parameters_) {
        RTC_DCHECK(!sub_field->key_.empty());
        sub_field->MarkAsUsed();
        field_map[sub_field->key_] = sub_field;
      }
      continue;
    }

    if (field->key_.empty()) {
      RTC_DCHECK(!keyless_field);
      keyless_field = field;
    } else {
      field_map[field->key_] = field;
    }
  }
  bool logged_unknown_key = false;

  absl::string_view tail = trial_string;
  while (!tail.empty()) {
    size_t key_end = tail.find_first_of(",:");
    absl::string_view key = tail.substr(0, key_end);
    absl::optional<std::string> opt_value;
    if (key_end == absl::string_view::npos) {
      tail = "";
    } else if (tail[key_end] == ':') {
      tail = tail.substr(key_end + 1);
      size_t value_end = tail.find(',');
      opt_value.emplace(tail.substr(0, value_end));
      if (value_end == absl::string_view::npos) {
        tail = "";
      } else {
        tail = tail.substr(value_end + 1);
      }
    } else {
      RTC_DCHECK_EQ(tail[key_end], ',');
      tail = tail.substr(key_end + 1);
    }

    auto field = field_map.find(key);
    if (field != field_map.end()) {
      if (!field->second->Parse(std::move(opt_value))) {
        RTC_LOG(LS_WARNING) << "Failed to read field with key: '" << key
                            << "' in trial: \"" << trial_string << "\"";
      }
    } else if (!opt_value && keyless_field && !key.empty()) {
      if (!keyless_field->Parse(std::string(key))) {
        RTC_LOG(LS_WARNING) << "Failed to read empty key field with value '"
                            << key << "' in trial: \"" << trial_string << "\"";
      }
    } else if (key.empty() || key[0] != '_') {
      // "_" is be used to prefix keys that are part of the string for
      // debugging purposes but not neccessarily used.
      // e.g. WebRTC-Experiment/param: value, _DebuggingString
      if (!logged_unknown_key) {
        RTC_LOG(LS_INFO) << "No field with key: '" << key
                         << "' (found in trial: \"" << trial_string << "\")";
        std::string valid_keys;
        for (const auto& f : field_map) {
          valid_keys.append(f.first.data(), f.first.size());
          valid_keys += ", ";
        }
        RTC_LOG(LS_INFO) << "Valid keys are: " << valid_keys;
        logged_unknown_key = true;
      }
    }
  }

  for (FieldTrialParameterInterface* field : fields) {
    field->ParseDone();
  }
}

template <>
absl::optional<bool> ParseTypedParameter<bool>(std::string str) {
  if (str == "true" || str == "1") {
    return true;
  } else if (str == "false" || str == "0") {
    return false;
  }
  return absl::nullopt;
}

template <>
absl::optional<double> ParseTypedParameter<double>(std::string str) {
  double value;
  char unit[2]{0, 0};
  if (sscanf(str.c_str(), "%lf%1s", &value, unit) >= 1) {
    if (unit[0] == '%')
      return value / 100;
    return value;
  } else {
    return absl::nullopt;
  }
}

template <>
absl::optional<int> ParseTypedParameter<int>(std::string str) {
  int64_t value;
  if (sscanf(str.c_str(), "%" SCNd64, &value) == 1) {
    if (rtc::IsValueInRangeForNumericType<int, int64_t>(value)) {
      return static_cast<int>(value);
    }
  }
  return absl::nullopt;
}

template <>
absl::optional<unsigned> ParseTypedParameter<unsigned>(std::string str) {
  int64_t value;
  if (sscanf(str.c_str(), "%" SCNd64, &value) == 1) {
    if (rtc::IsValueInRangeForNumericType<unsigned, int64_t>(value)) {
      return static_cast<unsigned>(value);
    }
  }
  return absl::nullopt;
}

template <>
absl::optional<std::string> ParseTypedParameter<std::string>(std::string str) {
  return std::move(str);
}

template <>
absl::optional<absl::optional<bool>> ParseTypedParameter<absl::optional<bool>>(
    std::string str) {
  return ParseOptionalParameter<bool>(str);
}
template <>
absl::optional<absl::optional<int>> ParseTypedParameter<absl::optional<int>>(
    std::string str) {
  return ParseOptionalParameter<int>(str);
}
template <>
absl::optional<absl::optional<unsigned>>
ParseTypedParameter<absl::optional<unsigned>>(std::string str) {
  return ParseOptionalParameter<unsigned>(str);
}
template <>
absl::optional<absl::optional<double>>
ParseTypedParameter<absl::optional<double>>(std::string str) {
  return ParseOptionalParameter<double>(str);
}

FieldTrialFlag::FieldTrialFlag(std::string key) : FieldTrialFlag(key, false) {}

FieldTrialFlag::FieldTrialFlag(std::string key, bool default_value)
    : FieldTrialParameterInterface(key), value_(default_value) {}

bool FieldTrialFlag::Get() const {
  return value_;
}

webrtc::FieldTrialFlag::operator bool() const {
  return value_;
}

bool FieldTrialFlag::Parse(absl::optional<std::string> str_value) {
  // Only set the flag if there is no argument provided.
  if (str_value) {
    absl::optional<bool> opt_value = ParseTypedParameter<bool>(*str_value);
    if (!opt_value)
      return false;
    value_ = *opt_value;
  } else {
    value_ = true;
  }
  return true;
}

AbstractFieldTrialEnum::AbstractFieldTrialEnum(
    std::string key,
    int default_value,
    std::map<std::string, int> mapping)
    : FieldTrialParameterInterface(key),
      value_(default_value),
      enum_mapping_(mapping) {
  for (auto& key_val : enum_mapping_)
    valid_values_.insert(key_val.second);
}
AbstractFieldTrialEnum::AbstractFieldTrialEnum(const AbstractFieldTrialEnum&) =
    default;
AbstractFieldTrialEnum::~AbstractFieldTrialEnum() = default;

bool AbstractFieldTrialEnum::Parse(absl::optional<std::string> str_value) {
  if (str_value) {
    auto it = enum_mapping_.find(*str_value);
    if (it != enum_mapping_.end()) {
      value_ = it->second;
      return true;
    }
    absl::optional<int> value = ParseTypedParameter<int>(*str_value);
    if (value.has_value() &&
        (valid_values_.find(*value) != valid_values_.end())) {
      value_ = *value;
      return true;
    }
  }
  return false;
}

template class FieldTrialParameter<bool>;
template class FieldTrialParameter<double>;
template class FieldTrialParameter<int>;
template class FieldTrialParameter<unsigned>;
template class FieldTrialParameter<std::string>;

template class FieldTrialConstrained<double>;
template class FieldTrialConstrained<int>;
template class FieldTrialConstrained<unsigned>;

template class FieldTrialOptional<double>;
template class FieldTrialOptional<int>;
template class FieldTrialOptional<unsigned>;
template class FieldTrialOptional<bool>;
template class FieldTrialOptional<std::string>;

}  // namespace webrtc
