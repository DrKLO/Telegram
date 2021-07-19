// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/json/json_value_converter.h"

#include "base/strings/utf_string_conversions.h"

namespace base {
namespace internal {

bool BasicValueConverter<int>::Convert(
    const base::Value& value, int* field) const {
  if (!value.is_int())
    return false;
  if (field)
    *field = value.GetInt();
  return true;
}

bool BasicValueConverter<std::string>::Convert(
    const base::Value& value, std::string* field) const {
  if (!value.is_string())
    return false;
  if (field)
    *field = value.GetString();
  return true;
}

bool BasicValueConverter<string16>::Convert(
    const base::Value& value, string16* field) const {
  if (!value.is_string())
    return false;
  if (field)
    *field = base::UTF8ToUTF16(value.GetString());
  return true;
}

bool BasicValueConverter<double>::Convert(
    const base::Value& value, double* field) const {
  if (!value.is_double() && !value.is_int())
    return false;
  if (field)
    *field = value.GetDouble();
  return true;
}

bool BasicValueConverter<bool>::Convert(
    const base::Value& value, bool* field) const {
  if (!value.is_bool())
    return false;
  if (field)
    *field = value.GetBool();
  return true;
}

}  // namespace internal
}  // namespace base

