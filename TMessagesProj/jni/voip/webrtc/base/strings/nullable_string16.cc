// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/nullable_string16.h"

#include <ostream>
#include <utility>

namespace base {
NullableString16::NullableString16() = default;
NullableString16::NullableString16(const NullableString16& other) = default;
NullableString16::NullableString16(NullableString16&& other) = default;

NullableString16::NullableString16(const string16& string, bool is_null) {
  if (!is_null)
    string_.emplace(string);
}

NullableString16::NullableString16(Optional<string16> optional_string16)
    : string_(std::move(optional_string16)) {}

NullableString16::~NullableString16() = default;
NullableString16& NullableString16::operator=(const NullableString16& other) =
    default;
NullableString16& NullableString16::operator=(NullableString16&& other) =
    default;

std::ostream& operator<<(std::ostream& out, const NullableString16& value) {
  return value.is_null() ? out << "(null)" : out << value.string();
}

}  // namespace base
