// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_VERSION_H_
#define BASE_VERSION_H_

#include <stdint.h>

#include <iosfwd>
#include <string>
#include <vector>

#include "base/base_export.h"
#include "base/strings/string_piece.h"

namespace base {

// Version represents a dotted version number, like "1.2.3.4", supporting
// parsing and comparison.
class BASE_EXPORT Version {
 public:
  // The only thing you can legally do to a default constructed
  // Version object is assign to it.
  Version();

  Version(const Version& other);

  // Initializes from a decimal dotted version number, like "0.1.1".
  // Each component is limited to a uint16_t. Call IsValid() to learn
  // the outcome.
  explicit Version(StringPiece version_str);

  // Initializes from a vector of components, like {1, 2, 3, 4}. Call IsValid()
  // to learn the outcome.
  explicit Version(std::vector<uint32_t> components);

  ~Version();

  // Returns true if the object contains a valid version number.
  bool IsValid() const;

  // Returns true if the version wildcard string is valid. The version wildcard
  // string may end with ".*" (e.g. 1.2.*, 1.*). Any other arrangement with "*"
  // is invalid (e.g. 1.*.3 or 1.2.3*). This functions defaults to standard
  // Version behavior (IsValid) if no wildcard is present.
  static bool IsValidWildcardString(StringPiece wildcard_string);

  // Returns -1, 0, 1 for <, ==, >.
  int CompareTo(const Version& other) const;

  // Given a valid version object, compare if a |wildcard_string| results in a
  // newer version. This function will default to CompareTo if the string does
  // not end in wildcard sequence ".*". IsValidWildcard(wildcard_string) must be
  // true before using this function.
  int CompareToWildcardString(StringPiece wildcard_string) const;

  // Return the string representation of this version.
  std::string GetString() const;

  const std::vector<uint32_t>& components() const { return components_; }

 private:
  std::vector<uint32_t> components_;
};

BASE_EXPORT bool operator==(const Version& v1, const Version& v2);
BASE_EXPORT bool operator!=(const Version& v1, const Version& v2);
BASE_EXPORT bool operator<(const Version& v1, const Version& v2);
BASE_EXPORT bool operator<=(const Version& v1, const Version& v2);
BASE_EXPORT bool operator>(const Version& v1, const Version& v2);
BASE_EXPORT bool operator>=(const Version& v1, const Version& v2);
BASE_EXPORT std::ostream& operator<<(std::ostream& stream, const Version& v);

}  // namespace base

#endif  // BASE_VERSION_H_
