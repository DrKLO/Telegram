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

#ifndef ABSL_CONTAINER_INTERNAL_COUNTING_ALLOCATOR_H_
#define ABSL_CONTAINER_INTERNAL_COUNTING_ALLOCATOR_H_

#include <cassert>
#include <cstdint>
#include <memory>

#include "absl/base/config.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace container_internal {

// This is a stateful allocator, but the state lives outside of the
// allocator (in whatever test is using the allocator). This is odd
// but helps in tests where the allocator is propagated into nested
// containers - that chain of allocators uses the same state and is
// thus easier to query for aggregate allocation information.
template <typename T>
class CountingAllocator : public std::allocator<T> {
 public:
  using Alloc = std::allocator<T>;
  using pointer = typename Alloc::pointer;
  using size_type = typename Alloc::size_type;

  CountingAllocator() : bytes_used_(nullptr) {}
  explicit CountingAllocator(int64_t* b) : bytes_used_(b) {}

  template <typename U>
  CountingAllocator(const CountingAllocator<U>& x)
      : Alloc(x), bytes_used_(x.bytes_used_) {}

  pointer allocate(size_type n,
                   std::allocator<void>::const_pointer hint = nullptr) {
    assert(bytes_used_ != nullptr);
    *bytes_used_ += n * sizeof(T);
    return Alloc::allocate(n, hint);
  }

  void deallocate(pointer p, size_type n) {
    Alloc::deallocate(p, n);
    assert(bytes_used_ != nullptr);
    *bytes_used_ -= n * sizeof(T);
  }

  template<typename U>
  class rebind {
   public:
    using other = CountingAllocator<U>;
  };

  friend bool operator==(const CountingAllocator& a,
                         const CountingAllocator& b) {
    return a.bytes_used_ == b.bytes_used_;
  }

  friend bool operator!=(const CountingAllocator& a,
                         const CountingAllocator& b) {
    return !(a == b);
  }

  int64_t* bytes_used_;
};

}  // namespace container_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_CONTAINER_INTERNAL_COUNTING_ALLOCATOR_H_
