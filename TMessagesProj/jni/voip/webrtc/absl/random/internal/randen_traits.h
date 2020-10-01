// Copyright 2017 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ABSL_RANDOM_INTERNAL_RANDEN_TRAITS_H_
#define ABSL_RANDOM_INTERNAL_RANDEN_TRAITS_H_

// HERMETIC NOTE: The randen_hwaes target must not introduce duplicate
// symbols from arbitrary system and other headers, since it may be built
// with different flags from other targets, using different levels of
// optimization, potentially introducing ODR violations.

#include <cstddef>

#include "absl/base/config.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace random_internal {

// RANDen = RANDom generator or beetroots in Swiss German.
// 'Strong' (well-distributed, unpredictable, backtracking-resistant) random
// generator, faster in some benchmarks than std::mt19937_64 and pcg64_c32.
//
// RandenTraits contains the basic algorithm traits, such as the size of the
// state, seed, sponge, etc.
struct RandenTraits {
  // Size of the entire sponge / state for the randen PRNG.
  static constexpr size_t kStateBytes = 256;  // 2048-bit

  // Size of the 'inner' (inaccessible) part of the sponge. Larger values would
  // require more frequent calls to RandenGenerate.
  static constexpr size_t kCapacityBytes = 16;  // 128-bit

  // Size of the default seed consumed by the sponge.
  static constexpr size_t kSeedBytes = kStateBytes - kCapacityBytes;

  // Largest size for which security proofs are known.
  static constexpr size_t kFeistelBlocks = 16;

  // Type-2 generalized Feistel => one round function for every two blocks.
  static constexpr size_t kFeistelFunctions = kFeistelBlocks / 2;  // = 8

  // Ensures SPRP security and two full subblock diffusions.
  // Must be > 4 * log2(kFeistelBlocks).
  static constexpr size_t kFeistelRounds = 16 + 1;
};

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_INTERNAL_RANDEN_TRAITS_H_
