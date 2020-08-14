// Copyright 2020 The Abseil Authors.
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

#ifndef ABSL_STRINGS_INTERNAL_CORD_INTERNAL_H_
#define ABSL_STRINGS_INTERNAL_CORD_INTERNAL_H_

#include <atomic>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <type_traits>

#include "absl/meta/type_traits.h"
#include "absl/strings/string_view.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace cord_internal {

// Wraps std::atomic for reference counting.
class Refcount {
 public:
  Refcount() : count_{1} {}
  ~Refcount() {}

  // Increments the reference count by 1. Imposes no memory ordering.
  inline void Increment() { count_.fetch_add(1, std::memory_order_relaxed); }

  // Asserts that the current refcount is greater than 0. If the refcount is
  // greater than 1, decrements the reference count by 1.
  //
  // Returns false if there are no references outstanding; true otherwise.
  // Inserts barriers to ensure that state written before this method returns
  // false will be visible to a thread that just observed this method returning
  // false.
  inline bool Decrement() {
    int32_t refcount = count_.load(std::memory_order_acquire);
    assert(refcount > 0);
    return refcount != 1 && count_.fetch_sub(1, std::memory_order_acq_rel) != 1;
  }

  // Same as Decrement but expect that refcount is greater than 1.
  inline bool DecrementExpectHighRefcount() {
    int32_t refcount = count_.fetch_sub(1, std::memory_order_acq_rel);
    assert(refcount > 0);
    return refcount != 1;
  }

  // Returns the current reference count using acquire semantics.
  inline int32_t Get() const { return count_.load(std::memory_order_acquire); }

  // Returns whether the atomic integer is 1.
  // If the reference count is used in the conventional way, a
  // reference count of 1 implies that the current thread owns the
  // reference and no other thread shares it.
  // This call performs the test for a reference count of one, and
  // performs the memory barrier needed for the owning thread
  // to act on the object, knowing that it has exclusive access to the
  // object.
  inline bool IsOne() { return count_.load(std::memory_order_acquire) == 1; }

 private:
  std::atomic<int32_t> count_;
};

// The overhead of a vtable is too much for Cord, so we roll our own subclasses
// using only a single byte to differentiate classes from each other - the "tag"
// byte.  Define the subclasses first so we can provide downcasting helper
// functions in the base class.

struct CordRepConcat;
struct CordRepSubstring;
struct CordRepExternal;

struct CordRep {
  // The following three fields have to be less than 32 bytes since
  // that is the smallest supported flat node size.
  size_t length;
  Refcount refcount;
  // If tag < FLAT, it represents CordRepKind and indicates the type of node.
  // Otherwise, the node type is CordRepFlat and the tag is the encoded size.
  uint8_t tag;
  char data[1];  // Starting point for flat array: MUST BE LAST FIELD of CordRep

  inline CordRepConcat* concat();
  inline const CordRepConcat* concat() const;
  inline CordRepSubstring* substring();
  inline const CordRepSubstring* substring() const;
  inline CordRepExternal* external();
  inline const CordRepExternal* external() const;
};

struct CordRepConcat : public CordRep {
  CordRep* left;
  CordRep* right;

  uint8_t depth() const { return static_cast<uint8_t>(data[0]); }
  void set_depth(uint8_t depth) { data[0] = static_cast<char>(depth); }
};

struct CordRepSubstring : public CordRep {
  size_t start;  // Starting offset of substring in child
  CordRep* child;
};

// TODO(strel): replace the following logic (and related functions in cord.cc)
// with container_internal::Layout.

// Alignment requirement for CordRepExternal so that the type erased releaser
// will be stored at a suitably aligned address.
constexpr size_t ExternalRepAlignment() {
#if defined(__STDCPP_DEFAULT_NEW_ALIGNMENT__)
  return __STDCPP_DEFAULT_NEW_ALIGNMENT__;
#else
  return alignof(max_align_t);
#endif
}

// Type for function pointer that will invoke and destroy the type-erased
// releaser function object. Accepts a pointer to the releaser and the
// `string_view` that were passed in to `NewExternalRep` below. The return value
// is the size of the `Releaser` type.
using ExternalReleaserInvoker = size_t (*)(void*, absl::string_view);

// External CordReps are allocated together with a type erased releaser. The
// releaser is stored in the memory directly following the CordRepExternal.
struct alignas(ExternalRepAlignment()) CordRepExternal : public CordRep {
  const char* base;
  // Pointer to function that knows how to call and destroy the releaser.
  ExternalReleaserInvoker releaser_invoker;
};

// TODO(strel): look into removing, it doesn't seem like anything relies on this
static_assert(sizeof(CordRepConcat) == sizeof(CordRepSubstring), "");

}  // namespace cord_internal
ABSL_NAMESPACE_END
}  // namespace absl
#endif  // ABSL_STRINGS_INTERNAL_CORD_INTERNAL_H_
