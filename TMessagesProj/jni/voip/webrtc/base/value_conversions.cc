// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/value_conversions.h"

#include <stdint.h>

#include <algorithm>
#include <string>
#include <vector>

#include "base/files/file_path.h"
#include "base/memory/ptr_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/time/time.h"
#include "base/unguessable_token.h"
#include "base/values.h"

namespace base {
namespace {
// Helper for serialize/deserialize UnguessableToken.
union UnguessableTokenRepresentation {
  struct Field {
    uint64_t high;
    uint64_t low;
  } field;

  uint8_t buffer[sizeof(Field)];
};
}  // namespace

// |Value| internally stores strings in UTF-8, so we have to convert from the
// system native code to UTF-8 and back.
Value CreateFilePathValue(const FilePath& in_value) {
  return Value(in_value.AsUTF8Unsafe());
}

bool GetValueAsFilePath(const Value& value, FilePath* file_path) {
  std::string str;
  if (!value.GetAsString(&str))
    return false;
  if (file_path)
    *file_path = FilePath::FromUTF8Unsafe(str);
  return true;
}

// It is recommended in time.h to use ToDeltaSinceWindowsEpoch() and
// FromDeltaSinceWindowsEpoch() for opaque serialization and
// deserialization of time values.
Value CreateTimeValue(const Time& time) {
  return CreateTimeDeltaValue(time.ToDeltaSinceWindowsEpoch());
}

bool GetValueAsTime(const Value& value, Time* time) {
  TimeDelta time_delta;
  if (!GetValueAsTimeDelta(value, &time_delta))
    return false;

  if (time)
    *time = Time::FromDeltaSinceWindowsEpoch(time_delta);
  return true;
}

// |Value| does not support 64-bit integers, and doubles do not have enough
// precision, so we store the 64-bit time value as a string instead.
Value CreateTimeDeltaValue(const TimeDelta& time_delta) {
  std::string string_value = base::NumberToString(time_delta.InMicroseconds());
  return Value(string_value);
}

bool GetValueAsTimeDelta(const Value& value, TimeDelta* time_delta) {
  std::string str;
  int64_t int_value;
  if (!value.GetAsString(&str) || !base::StringToInt64(str, &int_value))
    return false;
  if (time_delta)
    *time_delta = TimeDelta::FromMicroseconds(int_value);
  return true;
}

Value CreateUnguessableTokenValue(const UnguessableToken& token) {
  UnguessableTokenRepresentation representation;
  representation.field.high = token.GetHighForSerialization();
  representation.field.low = token.GetLowForSerialization();

  return Value(HexEncode(representation.buffer, sizeof(representation.buffer)));
}

bool GetValueAsUnguessableToken(const Value& value, UnguessableToken* token) {
  if (!value.is_string()) {
    return false;
  }

  UnguessableTokenRepresentation representation;
  if (!HexStringToSpan(value.GetString(), representation.buffer)) {
    return false;
  }

  *token = UnguessableToken::Deserialize(representation.field.high,
                                         representation.field.low);
  return true;
}

}  // namespace base
