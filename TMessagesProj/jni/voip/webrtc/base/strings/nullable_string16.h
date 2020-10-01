// Copyright (c) 2010 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_STRINGS_NULLABLE_STRING16_H_
#define BASE_STRINGS_NULLABLE_STRING16_H_

#include <iosfwd>

#include "base/base_export.h"
#include "base/optional.h"
#include "base/strings/string16.h"
#include "base/strings/string_util.h"

namespace base {

// This class is a simple wrapper for string16 which also contains a null
// state.  This should be used only where the difference between null and
// empty is meaningful.
class BASE_EXPORT NullableString16 {
 public:
  NullableString16();
  NullableString16(const NullableString16& other);
  NullableString16(NullableString16&& other);
  NullableString16(const string16& string, bool is_null);
  explicit NullableString16(Optional<string16> optional_string16);
  ~NullableString16();

  NullableString16& operator=(const NullableString16& other);
  NullableString16& operator=(NullableString16&& other);

  const string16& string() const {
    return string_ ? *string_ : EmptyString16();
  }
  bool is_null() const { return !string_; }
  const Optional<string16>& as_optional_string16() const { return string_; }

 private:
  Optional<string16> string_;
};

inline bool operator==(const NullableString16& a, const NullableString16& b) {
  return a.as_optional_string16() == b.as_optional_string16();
}

inline bool operator!=(const NullableString16& a, const NullableString16& b) {
  return !(a == b);
}

BASE_EXPORT std::ostream& operator<<(std::ostream& out,
                                     const NullableString16& value);

}  // namespace base

#endif  // BASE_STRINGS_NULLABLE_STRING16_H_
