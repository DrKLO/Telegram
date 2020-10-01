// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_GUID_H_
#define BASE_GUID_H_

#include <stdint.h>

#include <string>

#include "base/base_export.h"
#include "base/strings/string_piece.h"
#include "build/build_config.h"

namespace base {

// Generate a 128-bit random GUID in the form of version 4 as described in
// RFC 4122, section 4.4.
// The format of GUID version 4 must be xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx,
// where y is one of [8, 9, A, B].
// The hexadecimal values "a" through "f" are output as lower case characters.
//
// A cryptographically secure random source will be used, but consider using
// UnguessableToken for greater type-safety if GUID format is unnecessary.
BASE_EXPORT std::string GenerateGUID();

// Returns true if the input string conforms to the version 4 GUID format.
// Note that this does NOT check if the hexadecimal values "a" through "f"
// are in lower case characters, as Version 4 RFC says onput they're
// case insensitive. (Use IsValidGUIDOutputString for checking if the
// given string is valid output string)
BASE_EXPORT bool IsValidGUID(base::StringPiece guid);
BASE_EXPORT bool IsValidGUID(base::StringPiece16 guid);

// Returns true if the input string is valid version 4 GUID output string.
// This also checks if the hexadecimal values "a" through "f" are in lower
// case characters.
BASE_EXPORT bool IsValidGUIDOutputString(base::StringPiece guid);

// For unit testing purposes only.  Do not use outside of tests.
BASE_EXPORT std::string RandomDataToGUIDString(const uint64_t bytes[2]);

}  // namespace base

#endif  // BASE_GUID_H_
