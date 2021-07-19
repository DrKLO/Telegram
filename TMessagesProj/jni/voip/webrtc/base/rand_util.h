// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_RAND_UTIL_H_
#define BASE_RAND_UTIL_H_

#include <stddef.h>
#include <stdint.h>

#include <algorithm>
#include <string>

#include "base/base_export.h"
#include "build/build_config.h"

namespace base {

// Returns a random number in range [0, UINT64_MAX]. Thread-safe.
BASE_EXPORT uint64_t RandUint64();

// Returns a random number between min and max (inclusive). Thread-safe.
BASE_EXPORT int RandInt(int min, int max);

// Returns a random number in range [0, range).  Thread-safe.
BASE_EXPORT uint64_t RandGenerator(uint64_t range);

// Returns a random double in range [0, 1). Thread-safe.
BASE_EXPORT double RandDouble();

// Given input |bits|, convert with maximum precision to a double in
// the range [0, 1). Thread-safe.
BASE_EXPORT double BitsToOpenEndedUnitInterval(uint64_t bits);

// Fills |output_length| bytes of |output| with random data. Thread-safe.
//
// Although implementations are required to use a cryptographically secure
// random number source, code outside of base/ that relies on this should use
// crypto::RandBytes instead to ensure the requirement is easily discoverable.
BASE_EXPORT void RandBytes(void* output, size_t output_length);

// Fills a string of length |length| with random data and returns it.
// |length| should be nonzero. Thread-safe.
//
// Note that this is a variation of |RandBytes| with a different return type.
// The returned string is likely not ASCII/UTF-8. Use with care.
//
// Although implementations are required to use a cryptographically secure
// random number source, code outside of base/ that relies on this should use
// crypto::RandBytes instead to ensure the requirement is easily discoverable.
BASE_EXPORT std::string RandBytesAsString(size_t length);

// An STL UniformRandomBitGenerator backed by RandUint64.
// TODO(tzik): Consider replacing this with a faster implementation.
class RandomBitGenerator {
 public:
  using result_type = uint64_t;
  static constexpr result_type min() { return 0; }
  static constexpr result_type max() { return UINT64_MAX; }
  result_type operator()() const { return RandUint64(); }

  RandomBitGenerator() = default;
  ~RandomBitGenerator() = default;
};

// Shuffles [first, last) randomly. Thread-safe.
template <typename Itr>
void RandomShuffle(Itr first, Itr last) {
  std::shuffle(first, last, RandomBitGenerator());
}

#if defined(OS_POSIX)
BASE_EXPORT int GetUrandomFD();
#endif

}  // namespace base

#endif  // BASE_RAND_UTIL_H_
