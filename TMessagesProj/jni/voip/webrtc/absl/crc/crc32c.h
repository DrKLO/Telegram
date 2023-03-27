// Copyright 2022 The Abseil Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// -----------------------------------------------------------------------------
// File: crc32c.h
// -----------------------------------------------------------------------------
//
// This header file defines the API for computing CRC32C values as checksums
// for arbitrary sequences of bytes provided as a string buffer.
//
// The API includes the basic functions for computing such CRC32C values and
// some utility functions for performing more efficient mathematical
// computations using an existing checksum.
#ifndef ABSL_CRC_CRC32C_H_
#define ABSL_CRC_CRC32C_H_

#include <cstdint>
#include <iostream>
#include <ostream>

#include "absl/crc/internal/crc32c_inline.h"
#include "absl/strings/string_view.h"

namespace absl {
ABSL_NAMESPACE_BEGIN

//-----------------------------------------------------------------------------
// crc32c_t
//-----------------------------------------------------------------------------

// `crc32c_t` defines a strongly typed integer type for holding a CRC32C value.
enum class crc32c_t : uint32_t {};

// ToCrc32c()
//
// Converts a uint32_t value to crc32c_t. This API is necessary in C++14
// and earlier. Code targeting C++17-or-later can instead use `crc32c_t{n}`.
inline crc32c_t ToCrc32c(uint32_t n) {
  return static_cast<crc32c_t>(n);
}
// operator^
//
// Performs a bitwise XOR on two CRC32C values
inline crc32c_t operator^(crc32c_t lhs, crc32c_t rhs) {
  const auto lhs_int = static_cast<uint32_t>(lhs);
  const auto rhs_int = static_cast<uint32_t>(rhs);
  return ToCrc32c(lhs_int ^ rhs_int);
}

namespace crc_internal {
// Non-inline code path for `absl::ExtendCrc32c()`. Do not call directly.
// Call `absl::ExtendCrc32c()` (defined below) instead.
crc32c_t ExtendCrc32cInternal(crc32c_t initial_crc,
                              absl::string_view buf_to_add);
}  // namespace crc_internal

// -----------------------------------------------------------------------------
// CRC32C Computation Functions
// -----------------------------------------------------------------------------

// ComputeCrc32c()
//
// Returns the CRC32C value of the provided string.
crc32c_t ComputeCrc32c(absl::string_view buf);

// ExtendCrc32c()
//
// Computes a CRC32C value from an `initial_crc` CRC32C value including the
// `buf_to_add` bytes of an additional buffer. Using this function is more
// efficient than computing a CRC32C value for the combined buffer from
// scratch.
//
// Note: `ExtendCrc32c` with an initial_crc of 0 is equivalent to
// `ComputeCrc32c`.
//
// This operation has a runtime cost of O(`buf_to_add.size()`)
inline crc32c_t ExtendCrc32c(crc32c_t initial_crc,
                             absl::string_view buf_to_add) {
  // Approximately 75% of calls have size <= 64.
  if (buf_to_add.size() <= 64) {
    uint32_t crc = static_cast<uint32_t>(initial_crc);
    if (crc_internal::ExtendCrc32cInline(&crc, buf_to_add.data(),
                                         buf_to_add.size())) {
      return ToCrc32c(crc);
    }
  }
  return crc_internal::ExtendCrc32cInternal(initial_crc, buf_to_add);
}

// ExtendCrc32cByZeroes()
//
// Computes a CRC32C value for a buffer with an `initial_crc` CRC32C value,
// where `length` bytes with a value of 0 are appended to the buffer. Using this
// function is more efficient than computing a CRC32C value for the combined
// buffer from scratch.
//
// This operation has a runtime cost of O(log(`length`))
crc32c_t ExtendCrc32cByZeroes(crc32c_t initial_crc, size_t length);

// MemcpyCrc32c()
//
// Copies `src` to `dest` using `memcpy()` semantics, returning the CRC32C
// value of the copied buffer.
//
// Using `MemcpyCrc32c()` is potentially faster than performing the `memcpy()`
// and `ComputeCrc32c()` operations separately.
crc32c_t MemcpyCrc32c(void* dest, const void* src, size_t count,
                      crc32c_t initial_crc = ToCrc32c(0));

// -----------------------------------------------------------------------------
// CRC32C Arithmetic Functions
// -----------------------------------------------------------------------------

// The following functions perform arithmetic on CRC32C values, which are
// generally more efficient than recalculating any given result's CRC32C value.

// ConcatCrc32c()
//
// Calculates the CRC32C value of two buffers with known CRC32C values
// concatenated together.
//
// Given a buffer with CRC32C value `crc1` and a buffer with
// CRC32C value `crc2` and length, `crc2_length`, returns the CRC32C value of
// the concatenation of these two buffers.
//
// This operation has a runtime cost of O(log(`crc2_length`)).
crc32c_t ConcatCrc32c(crc32c_t crc1, crc32c_t crc2, size_t crc2_length);

// RemoveCrc32cPrefix()
//
// Calculates the CRC32C value of an existing buffer with a series of bytes
// (the prefix) removed from the beginning of that buffer.
//
// Given the CRC32C value of an existing buffer, `full_string_crc`; The CRC32C
// value of a prefix of that buffer, `prefix_crc`; and the length of the buffer
// with the prefix removed, `remaining_string_length` , return the CRC32C
// value of the buffer with the prefix removed.
//
// This operation has a runtime cost of O(log(`remaining_string_length`)).
crc32c_t RemoveCrc32cPrefix(crc32c_t prefix_crc, crc32c_t full_string_crc,
                            size_t remaining_string_length);
// RemoveCrc32cSuffix()
//
// Calculates the CRC32C value of an existing buffer with a series of bytes
// (the suffix) removed from the end of that buffer.
//
// Given a CRC32C value of an existing buffer `full_string_crc`, the CRC32C
// value of the suffix to remove `suffix_crc`, and the length of that suffix
// `suffix_len`, returns the CRC32C value of the buffer with suffix removed.
//
// This operation has a runtime cost of O(log(`suffix_len`))
crc32c_t RemoveCrc32cSuffix(crc32c_t full_string_crc, crc32c_t suffix_crc,
                            size_t suffix_length);

// operator<<
//
// Streams the CRC32C value `crc` to the stream `os`.
inline std::ostream& operator<<(std::ostream& os, crc32c_t crc) {
  return os << static_cast<uint32_t>(crc);
}

ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_CRC_CRC32C_H_
