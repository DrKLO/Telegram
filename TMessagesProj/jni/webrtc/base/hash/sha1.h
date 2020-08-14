// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_HASH_SHA1_H_
#define BASE_HASH_SHA1_H_

#include <stddef.h>

#include <array>
#include <string>

#include "base/base_export.h"
#include "base/containers/span.h"

namespace base {

// These functions perform SHA-1 operations.

enum { kSHA1Length = 20 };  // Length in bytes of a SHA-1 hash.

// The output of a SHA-1 operation.
using SHA1Digest = std::array<uint8_t, kSHA1Length>;

// Computes the SHA-1 hash of the input |data| and returns the full hash.
BASE_EXPORT SHA1Digest SHA1HashSpan(span<const uint8_t> data);

// Computes the SHA-1 hash of the input string |str| and returns the full
// hash.
BASE_EXPORT std::string SHA1HashString(const std::string& str);

// Computes the SHA-1 hash of the |len| bytes in |data| and puts the hash
// in |hash|. |hash| must be kSHA1Length bytes long.
BASE_EXPORT void SHA1HashBytes(const unsigned char* data,
                               size_t len,
                               unsigned char* hash);

}  // namespace base

#endif  // BASE_HASH_SHA1_H_
