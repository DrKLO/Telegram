// Copyright 2018 The Abseil Authors.
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

#include "absl/hash/internal/hash.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace hash_internal {

uint64_t CityHashState::CombineLargeContiguousImpl32(uint64_t state,
                                                     const unsigned char* first,
                                                     size_t len) {
  while (len >= PiecewiseChunkSize()) {
    state =
        Mix(state, absl::hash_internal::CityHash32(reinterpret_cast<const char*>(first),
                                         PiecewiseChunkSize()));
    len -= PiecewiseChunkSize();
    first += PiecewiseChunkSize();
  }
  // Handle the remainder.
  return CombineContiguousImpl(state, first, len,
                               std::integral_constant<int, 4>{});
}

uint64_t CityHashState::CombineLargeContiguousImpl64(uint64_t state,
                                                     const unsigned char* first,
                                                     size_t len) {
  while (len >= PiecewiseChunkSize()) {
    state =
        Mix(state, absl::hash_internal::CityHash64(reinterpret_cast<const char*>(first),
                                         PiecewiseChunkSize()));
    len -= PiecewiseChunkSize();
    first += PiecewiseChunkSize();
  }
  // Handle the remainder.
  return CombineContiguousImpl(state, first, len,
                               std::integral_constant<int, 8>{});
}

ABSL_CONST_INIT const void* const CityHashState::kSeed = &kSeed;

}  // namespace hash_internal
ABSL_NAMESPACE_END
}  // namespace absl
