// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/util/values/values_util.h"

#include "base/strings/string_number_conversions.h"

namespace util {

base::Value Int64ToValue(int64_t integer) {
  return base::Value(base::NumberToString(integer));
}

base::Optional<int64_t> ValueToInt64(const base::Value* value) {
  return value ? ValueToInt64(*value) : base::nullopt;
}

base::Optional<int64_t> ValueToInt64(const base::Value& value) {
  if (!value.is_string())
    return base::nullopt;

  int64_t integer;
  if (!base::StringToInt64(value.GetString(), &integer))
    return base::nullopt;

  return integer;
}

base::Value TimeDeltaToValue(base::TimeDelta time_delta) {
  return Int64ToValue(time_delta.InMicroseconds());
}

base::Optional<base::TimeDelta> ValueToTimeDelta(const base::Value* value) {
  return value ? ValueToTimeDelta(*value) : base::nullopt;
}

base::Optional<base::TimeDelta> ValueToTimeDelta(const base::Value& value) {
  base::Optional<int64_t> integer = ValueToInt64(value);
  if (!integer)
    return base::nullopt;
  return base::TimeDelta::FromMicroseconds(*integer);
}

base::Value TimeToValue(base::Time time) {
  return TimeDeltaToValue(time.ToDeltaSinceWindowsEpoch());
}

base::Optional<base::Time> ValueToTime(const base::Value* value) {
  return value ? ValueToTime(*value) : base::nullopt;
}

base::Optional<base::Time> ValueToTime(const base::Value& value) {
  base::Optional<base::TimeDelta> time_delta = ValueToTimeDelta(value);
  if (!time_delta)
    return base::nullopt;
  return base::Time::FromDeltaSinceWindowsEpoch(*time_delta);
}

}  // namespace util
