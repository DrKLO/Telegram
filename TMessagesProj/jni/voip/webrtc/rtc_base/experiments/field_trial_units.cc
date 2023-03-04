/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/experiments/field_trial_units.h"

#include <stdio.h>

#include <limits>
#include <string>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"

// Large enough to fit "seconds", the longest supported unit name.
#define RTC_TRIAL_UNIT_LENGTH_STR "7"
#define RTC_TRIAL_UNIT_SIZE 8

namespace webrtc {
namespace {

struct ValueWithUnit {
  double value;
  std::string unit;
};

absl::optional<ValueWithUnit> ParseValueWithUnit(absl::string_view str) {
  if (str == "inf") {
    return ValueWithUnit{std::numeric_limits<double>::infinity(), ""};
  } else if (str == "-inf") {
    return ValueWithUnit{-std::numeric_limits<double>::infinity(), ""};
  } else {
    double double_val;
    char unit_char[RTC_TRIAL_UNIT_SIZE];
    unit_char[0] = 0;
    if (sscanf(std::string(str).c_str(), "%lf%" RTC_TRIAL_UNIT_LENGTH_STR "s",
               &double_val, unit_char) >= 1) {
      return ValueWithUnit{double_val, unit_char};
    }
  }
  return absl::nullopt;
}
}  // namespace

template <>
absl::optional<DataRate> ParseTypedParameter<DataRate>(absl::string_view str) {
  absl::optional<ValueWithUnit> result = ParseValueWithUnit(str);
  if (result) {
    if (result->unit.empty() || result->unit == "kbps") {
      return DataRate::KilobitsPerSec(result->value);
    } else if (result->unit == "bps") {
      return DataRate::BitsPerSec(result->value);
    }
  }
  return absl::nullopt;
}

template <>
absl::optional<DataSize> ParseTypedParameter<DataSize>(absl::string_view str) {
  absl::optional<ValueWithUnit> result = ParseValueWithUnit(str);
  if (result) {
    if (result->unit.empty() || result->unit == "bytes")
      return DataSize::Bytes(result->value);
  }
  return absl::nullopt;
}

template <>
absl::optional<TimeDelta> ParseTypedParameter<TimeDelta>(
    absl::string_view str) {
  absl::optional<ValueWithUnit> result = ParseValueWithUnit(str);
  if (result) {
    if (result->unit == "s" || result->unit == "seconds") {
      return TimeDelta::Seconds(result->value);
    } else if (result->unit == "us") {
      return TimeDelta::Micros(result->value);
    } else if (result->unit.empty() || result->unit == "ms") {
      return TimeDelta::Millis(result->value);
    }
  }
  return absl::nullopt;
}

template <>
absl::optional<absl::optional<DataRate>>
ParseTypedParameter<absl::optional<DataRate>>(absl::string_view str) {
  return ParseOptionalParameter<DataRate>(str);
}
template <>
absl::optional<absl::optional<DataSize>>
ParseTypedParameter<absl::optional<DataSize>>(absl::string_view str) {
  return ParseOptionalParameter<DataSize>(str);
}
template <>
absl::optional<absl::optional<TimeDelta>>
ParseTypedParameter<absl::optional<TimeDelta>>(absl::string_view str) {
  return ParseOptionalParameter<TimeDelta>(str);
}

template class FieldTrialParameter<DataRate>;
template class FieldTrialParameter<DataSize>;
template class FieldTrialParameter<TimeDelta>;

template class FieldTrialConstrained<DataRate>;
template class FieldTrialConstrained<DataSize>;
template class FieldTrialConstrained<TimeDelta>;

template class FieldTrialOptional<DataRate>;
template class FieldTrialOptional<DataSize>;
template class FieldTrialOptional<TimeDelta>;
}  // namespace webrtc
