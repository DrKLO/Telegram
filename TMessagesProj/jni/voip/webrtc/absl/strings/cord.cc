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

#include "absl/strings/cord.h"

#include <algorithm>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <iomanip>
#include <limits>
#include <ostream>
#include <sstream>
#include <type_traits>
#include <unordered_set>
#include <vector>

#include "absl/base/casts.h"
#include "absl/base/internal/raw_logging.h"
#include "absl/base/macros.h"
#include "absl/base/port.h"
#include "absl/container/fixed_array.h"
#include "absl/strings/escaping.h"
#include "absl/strings/internal/cord_internal.h"
#include "absl/strings/internal/resize_uninitialized.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_format.h"
#include "absl/strings/str_join.h"
#include "absl/strings/string_view.h"

namespace absl {
ABSL_NAMESPACE_BEGIN

using ::absl::cord_internal::CordRep;
using ::absl::cord_internal::CordRepConcat;
using ::absl::cord_internal::CordRepExternal;
using ::absl::cord_internal::CordRepSubstring;

// Various representations that we allow
enum CordRepKind {
  CONCAT        = 0,
  EXTERNAL      = 1,
  SUBSTRING     = 2,

  // We have different tags for different sized flat arrays,
  // starting with FLAT
  FLAT          = 3,
};

namespace {

// Type used with std::allocator for allocating and deallocating
// `CordRepExternal`. std::allocator is used because it opaquely handles the
// different new / delete overloads available on a given platform.
struct alignas(absl::cord_internal::ExternalRepAlignment()) ExternalAllocType {
  unsigned char value[absl::cord_internal::ExternalRepAlignment()];
};

// Returns the number of objects to pass in to std::allocator<ExternalAllocType>
// allocate() and deallocate() to create enough room for `CordRepExternal` with
// `releaser_size` bytes on the end.
constexpr size_t GetExternalAllocNumObjects(size_t releaser_size) {
  // Be sure to round up since `releaser_size` could be smaller than
  // `sizeof(ExternalAllocType)`.
  return (sizeof(CordRepExternal) + releaser_size + sizeof(ExternalAllocType) -
          1) /
         sizeof(ExternalAllocType);
}

// Allocates enough memory for `CordRepExternal` and a releaser with size
// `releaser_size` bytes.
void* AllocateExternal(size_t releaser_size) {
  return std::allocator<ExternalAllocType>().allocate(
      GetExternalAllocNumObjects(releaser_size));
}

// Deallocates the memory for a `CordRepExternal` assuming it was allocated with
// a releaser of given size and alignment.
void DeallocateExternal(CordRepExternal* p, size_t releaser_size) {
  std::allocator<ExternalAllocType>().deallocate(
      reinterpret_cast<ExternalAllocType*>(p),
      GetExternalAllocNumObjects(releaser_size));
}

// Returns a pointer to the type erased releaser for the given CordRepExternal.
void* GetExternalReleaser(CordRepExternal* rep) {
  return rep + 1;
}

}  // namespace

namespace cord_internal {

inline CordRepConcat* CordRep::concat() {
  assert(tag == CONCAT);
  return static_cast<CordRepConcat*>(this);
}

inline const CordRepConcat* CordRep::concat() const {
  assert(tag == CONCAT);
  return static_cast<const CordRepConcat*>(this);
}

inline CordRepSubstring* CordRep::substring() {
  assert(tag == SUBSTRING);
  return static_cast<CordRepSubstring*>(this);
}

inline const CordRepSubstring* CordRep::substring() const {
  assert(tag == SUBSTRING);
  return static_cast<const CordRepSubstring*>(this);
}

inline CordRepExternal* CordRep::external() {
  assert(tag == EXTERNAL);
  return static_cast<CordRepExternal*>(this);
}

inline const CordRepExternal* CordRep::external() const {
  assert(tag == EXTERNAL);
  return static_cast<const CordRepExternal*>(this);
}

using CordTreeConstPath = CordTreePath<const CordRep*, MaxCordDepth()>;

// This type is used to store the list of pending nodes during re-balancing.
// Its maximum size is 2 * MaxCordDepth() because the tree has a maximum
// possible depth of MaxCordDepth() and every concat node along a tree path
// could theoretically be split during rebalancing.
using RebalancingStack = CordTreePath<CordRep*, 2 * MaxCordDepth()>;

}  // namespace cord_internal

static const size_t kFlatOverhead = offsetof(CordRep, data);

// Largest and smallest flat node lengths we are willing to allocate
// Flat allocation size is stored in tag, which currently can encode sizes up
// to 4K, encoded as multiple of either 8 or 32 bytes.
// If we allow for larger sizes, we need to change this to 8/64, 16/128, etc.
static constexpr size_t kMaxFlatSize = 4096;
static constexpr size_t kMaxFlatLength = kMaxFlatSize - kFlatOverhead;
static constexpr size_t kMinFlatLength = 32 - kFlatOverhead;

// Prefer copying blocks of at most this size, otherwise reference count.
static const size_t kMaxBytesToCopy = 511;

// Helper functions for rounded div, and rounding to exact sizes.
static size_t DivUp(size_t n, size_t m) { return (n + m - 1) / m; }
static size_t RoundUp(size_t n, size_t m) { return DivUp(n, m) * m; }

// Returns the size to the nearest equal or larger value that can be
// expressed exactly as a tag value.
static size_t RoundUpForTag(size_t size) {
  return RoundUp(size, (size <= 1024) ? 8 : 32);
}

// Converts the allocated size to a tag, rounding down if the size
// does not exactly match a 'tag expressible' size value. The result is
// undefined if the size exceeds the maximum size that can be encoded in
// a tag, i.e., if size is larger than TagToAllocatedSize(<max tag>).
static uint8_t AllocatedSizeToTag(size_t size) {
  const size_t tag = (size <= 1024) ? size / 8 : 128 + size / 32 - 1024 / 32;
  assert(tag <= std::numeric_limits<uint8_t>::max());
  return tag;
}

// Converts the provided tag to the corresponding allocated size
static constexpr size_t TagToAllocatedSize(uint8_t tag) {
  return (tag <= 128) ? (tag * 8) : (1024 + (tag - 128) * 32);
}

// Converts the provided tag to the corresponding available data length
static constexpr size_t TagToLength(uint8_t tag) {
  return TagToAllocatedSize(tag) - kFlatOverhead;
}

// Enforce that kMaxFlatSize maps to a well-known exact tag value.
static_assert(TagToAllocatedSize(224) == kMaxFlatSize, "Bad tag logic");

constexpr size_t Fibonacci(uint8_t n, const size_t a = 0, const size_t b = 1) {
  return n == 0
             ? a
             : n == 1 ? b
                      : Fibonacci(n - 1, b,
                                  (a > (size_t(-1) - b)) ? size_t(-1) : a + b);
}

// Minimum length required for a given depth tree -- a tree is considered
// balanced if
//      length(t) >= kMinLength[depth(t)]
// The node depth is allowed to become larger to reduce rebalancing
// for larger strings (see ShouldRebalance).
constexpr size_t kMinLength[] = {
    Fibonacci(2),  Fibonacci(3),  Fibonacci(4),  Fibonacci(5),  Fibonacci(6),
    Fibonacci(7),  Fibonacci(8),  Fibonacci(9),  Fibonacci(10), Fibonacci(11),
    Fibonacci(12), Fibonacci(13), Fibonacci(14), Fibonacci(15), Fibonacci(16),
    Fibonacci(17), Fibonacci(18), Fibonacci(19), Fibonacci(20), Fibonacci(21),
    Fibonacci(22), Fibonacci(23), Fibonacci(24), Fibonacci(25), Fibonacci(26),
    Fibonacci(27), Fibonacci(28), Fibonacci(29), Fibonacci(30), Fibonacci(31),
    Fibonacci(32), Fibonacci(33), Fibonacci(34), Fibonacci(35), Fibonacci(36),
    Fibonacci(37), Fibonacci(38), Fibonacci(39), Fibonacci(40), Fibonacci(41),
    Fibonacci(42), Fibonacci(43), Fibonacci(44), Fibonacci(45), Fibonacci(46),
    Fibonacci(47), Fibonacci(48), Fibonacci(49), Fibonacci(50), Fibonacci(51),
    Fibonacci(52), Fibonacci(53), Fibonacci(54), Fibonacci(55), Fibonacci(56),
    Fibonacci(57), Fibonacci(58), Fibonacci(59), Fibonacci(60), Fibonacci(61),
    Fibonacci(62), Fibonacci(63), Fibonacci(64), Fibonacci(65), Fibonacci(66),
    Fibonacci(67), Fibonacci(68), Fibonacci(69), Fibonacci(70), Fibonacci(71),
    Fibonacci(72), Fibonacci(73), Fibonacci(74), Fibonacci(75), Fibonacci(76),
    Fibonacci(77), Fibonacci(78), Fibonacci(79), Fibonacci(80), Fibonacci(81),
    Fibonacci(82), Fibonacci(83), Fibonacci(84), Fibonacci(85), Fibonacci(86),
    Fibonacci(87), Fibonacci(88), Fibonacci(89), Fibonacci(90), Fibonacci(91),
    Fibonacci(92), Fibonacci(93), Fibonacci(94), Fibonacci(95)};

static_assert(sizeof(kMinLength) / sizeof(size_t) >=
                  (cord_internal::MaxCordDepth() + 1),
              "Not enough elements in kMinLength array to cover all the "
              "supported Cord depth(s)");

inline bool ShouldRebalance(const CordRep* node) {
  if (node->tag != CONCAT) return false;

  size_t node_depth = node->concat()->depth();

  if (node_depth <= 15) return false;

  // Rebalancing Cords is expensive, so we reduce how often rebalancing occurs
  // by allowing shallow Cords to have twice the depth that the Fibonacci rule
  // would otherwise imply. Deep Cords need to follow the rule more closely,
  // however to ensure algorithm correctness. We implement this with linear
  // interpolation. Cords of depth 16 are treated as though they have a depth
  // of 16 * 1/2, and Cords of depth MaxCordDepth() interpolate to
  // MaxCordDepth() * 1.
  return node->length <
         kMinLength[(node_depth * (cord_internal::MaxCordDepth() - 16)) /
                    (2 * cord_internal::MaxCordDepth() - 16 - node_depth)];
}

// Unlike root balancing condition this one is part of the re-balancing
// algorithm and has to be always matching against right depth for
// algorithm to be correct.
inline bool IsNodeBalanced(const CordRep* node) {
  if (node->tag != CONCAT) return true;

  size_t node_depth = node->concat()->depth();

  return node->length >= kMinLength[node_depth];
}

static CordRep* Rebalance(CordRep* node);
static void DumpNode(const CordRep* rep, bool include_data, std::ostream* os);
static bool VerifyNode(const CordRep* root, const CordRep* start_node,
                       bool full_validation);

static inline CordRep* VerifyTree(CordRep* node) {
  // Verification is expensive, so only do it in debug mode.
  // Even in debug mode we normally do only light validation.
  // If you are debugging Cord itself, you should define the
  // macro EXTRA_CORD_VALIDATION, e.g. by adding
  // --copt=-DEXTRA_CORD_VALIDATION to the blaze line.
#ifdef EXTRA_CORD_VALIDATION
  assert(node == nullptr || VerifyNode(node, node, /*full_validation=*/true));
#else   // EXTRA_CORD_VALIDATION
  assert(node == nullptr || VerifyNode(node, node, /*full_validation=*/false));
#endif  // EXTRA_CORD_VALIDATION
  static_cast<void>(&VerifyNode);

  return node;
}

// --------------------------------------------------------------------
// Memory management

inline CordRep* Ref(CordRep* rep) {
  if (rep != nullptr) {
    rep->refcount.Increment();
  }
  return rep;
}

// This internal routine is called from the cold path of Unref below. Keeping it
// in a separate routine allows good inlining of Unref into many profitable call
// sites. However, the call to this function can be highly disruptive to the
// register pressure in those callers. To minimize the cost to callers, we use
// a special LLVM calling convention that preserves most registers. This allows
// the call to this routine in cold paths to not disrupt the caller's register
// pressure. This calling convention is not available on all platforms; we
// intentionally allow LLVM to ignore the attribute rather than attempting to
// hardcode the list of supported platforms.
#if defined(__clang__) && !defined(__i386__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wattributes"
__attribute__((preserve_most))
#pragma clang diagnostic pop
#endif
static void UnrefInternal(CordRep* rep) {
  assert(rep != nullptr);

  cord_internal::RebalancingStack pending;

  while (true) {
    if (rep->tag == CONCAT) {
      CordRepConcat* rep_concat = rep->concat();
      CordRep* right = rep_concat->right;
      if (!right->refcount.Decrement()) {
        pending.push_back(right);
      }
      CordRep* left = rep_concat->left;
      delete rep_concat;
      rep = nullptr;
      if (!left->refcount.Decrement()) {
        rep = left;
        continue;
      }
    } else if (rep->tag == EXTERNAL) {
      CordRepExternal* rep_external = rep->external();
      absl::string_view data(rep_external->base, rep->length);
      void* releaser = GetExternalReleaser(rep_external);
      size_t releaser_size = rep_external->releaser_invoker(releaser, data);
      rep_external->~CordRepExternal();
      DeallocateExternal(rep_external, releaser_size);
      rep = nullptr;
    } else if (rep->tag == SUBSTRING) {
      CordRepSubstring* rep_substring = rep->substring();
      CordRep* child = rep_substring->child;
      delete rep_substring;
      rep = nullptr;
      if (!child->refcount.Decrement()) {
        rep = child;
        continue;
      }
    } else {
      // Flat CordReps are allocated and constructed with raw ::operator new
      // and placement new, and must be destructed and deallocated
      // accordingly.
#if defined(__cpp_sized_deallocation)
      size_t size = TagToAllocatedSize(rep->tag);
      rep->~CordRep();
      ::operator delete(rep, size);
#else
      rep->~CordRep();
      ::operator delete(rep);
#endif
      rep = nullptr;
    }

    if (!pending.empty()) {
      rep = pending.back();
      pending.pop_back();
    } else {
      break;
    }
  }
}

inline void Unref(CordRep* rep) {
  // Fast-path for two common, hot cases: a null rep and a shared root.
  if (ABSL_PREDICT_TRUE(rep == nullptr ||
                        rep->refcount.DecrementExpectHighRefcount())) {
    return;
  }

  UnrefInternal(rep);
}

// Return the depth of a node
static int Depth(const CordRep* rep) {
  if (rep->tag == CONCAT) {
    return rep->concat()->depth();
  } else {
    return 0;
  }
}

static void SetConcatChildren(CordRepConcat* concat, CordRep* left,
                              CordRep* right) {
  concat->left = left;
  concat->right = right;

  concat->length = left->length + right->length;
  concat->set_depth(1 + std::max(Depth(left), Depth(right)));

  ABSL_INTERNAL_CHECK(concat->depth() <= cord_internal::MaxCordDepth(),
                      "Cord depth exceeds max");
  ABSL_INTERNAL_CHECK(concat->length >= left->length, "Cord is too long");
  ABSL_INTERNAL_CHECK(concat->length >= right->length, "Cord is too long");
}

// Create a concatenation of the specified nodes.
// Does not change the refcounts of "left" and "right".
// The returned node has a refcount of 1.
static CordRep* RawConcat(CordRep* left, CordRep* right) {
  // Avoid making degenerate concat nodes (one child is empty)
  if (left == nullptr || left->length == 0) {
    Unref(left);
    return right;
  }
  if (right == nullptr || right->length == 0) {
    Unref(right);
    return left;
  }

  CordRepConcat* rep = new CordRepConcat();
  rep->tag = CONCAT;
  SetConcatChildren(rep, left, right);

  return rep;
}

static CordRep* Concat(CordRep* left, CordRep* right) {
  CordRep* rep = RawConcat(left, right);
  if (rep != nullptr && ShouldRebalance(rep)) {
    rep = Rebalance(rep);
  }
  return VerifyTree(rep);
}

// Make a balanced tree out of an array of leaf nodes.
static CordRep* MakeBalancedTree(CordRep** reps, size_t n) {
  // Make repeated passes over the array, merging adjacent pairs
  // until we are left with just a single node.
  while (n > 1) {
    size_t dst = 0;
    for (size_t src = 0; src < n; src += 2) {
      if (src + 1 < n) {
        reps[dst] = Concat(reps[src], reps[src + 1]);
      } else {
        reps[dst] = reps[src];
      }
      dst++;
    }
    n = dst;
  }

  return reps[0];
}

// Create a new flat node.
static CordRep* NewFlat(size_t length_hint) {
  if (length_hint <= kMinFlatLength) {
    length_hint = kMinFlatLength;
  } else if (length_hint > kMaxFlatLength) {
    length_hint = kMaxFlatLength;
  }

  // Round size up so it matches a size we can exactly express in a tag.
  const size_t size = RoundUpForTag(length_hint + kFlatOverhead);
  void* const raw_rep = ::operator new(size);
  CordRep* rep = new (raw_rep) CordRep();
  rep->tag = AllocatedSizeToTag(size);
  return VerifyTree(rep);
}

// Create a new tree out of the specified array.
// The returned node has a refcount of 1.
static CordRep* NewTree(const char* data,
                        size_t length,
                        size_t alloc_hint) {
  if (length == 0) return nullptr;
  absl::FixedArray<CordRep*> reps((length - 1) / kMaxFlatLength + 1);
  size_t n = 0;
  do {
    const size_t len = std::min(length, kMaxFlatLength);
    CordRep* rep = NewFlat(len + alloc_hint);
    rep->length = len;
    memcpy(rep->data, data, len);
    reps[n++] = VerifyTree(rep);
    data += len;
    length -= len;
  } while (length != 0);
  return MakeBalancedTree(reps.data(), n);
}

namespace cord_internal {

ExternalRepReleaserPair NewExternalWithUninitializedReleaser(
    absl::string_view data, ExternalReleaserInvoker invoker,
    size_t releaser_size) {
  assert(!data.empty());

  void* raw_rep = AllocateExternal(releaser_size);
  auto* rep = new (raw_rep) CordRepExternal();
  rep->length = data.size();
  rep->tag = EXTERNAL;
  rep->base = data.data();
  rep->releaser_invoker = invoker;
  return {VerifyTree(rep), GetExternalReleaser(rep)};
}

}  // namespace cord_internal

static CordRep* NewSubstring(CordRep* child, size_t offset, size_t length) {
  // Never create empty substring nodes
  if (length == 0) {
    Unref(child);
    return nullptr;
  } else {
    CordRepSubstring* rep = new CordRepSubstring();
    assert((offset + length) <= child->length);
    rep->length = length;
    rep->tag = SUBSTRING;
    rep->start = offset;
    rep->child = child;
    return VerifyTree(rep);
  }
}

// --------------------------------------------------------------------
// Cord::InlineRep functions

// This will trigger LNK2005 in MSVC.
#ifndef COMPILER_MSVC
const unsigned char Cord::InlineRep::kMaxInline;
#endif  // COMPILER_MSVC

inline void Cord::InlineRep::set_data(const char* data, size_t n,
                                      bool nullify_tail) {
  static_assert(kMaxInline == 15, "set_data is hard-coded for a length of 15");

  cord_internal::SmallMemmove(data_, data, n, nullify_tail);
  data_[kMaxInline] = static_cast<char>(n);
}

inline char* Cord::InlineRep::set_data(size_t n) {
  assert(n <= kMaxInline);
  memset(data_, 0, sizeof(data_));
  data_[kMaxInline] = static_cast<char>(n);
  return data_;
}

inline CordRep* Cord::InlineRep::force_tree(size_t extra_hint) {
  size_t len = data_[kMaxInline];
  CordRep* result;
  if (len > kMaxInline) {
    memcpy(&result, data_, sizeof(result));
  } else {
    result = NewFlat(len + extra_hint);
    result->length = len;
    memcpy(result->data, data_, len);
    set_tree(result);
  }
  return result;
}

inline void Cord::InlineRep::reduce_size(size_t n) {
  size_t tag = data_[kMaxInline];
  assert(tag <= kMaxInline);
  assert(tag >= n);
  tag -= n;
  memset(data_ + tag, 0, n);
  data_[kMaxInline] = static_cast<char>(tag);
}

inline void Cord::InlineRep::remove_prefix(size_t n) {
  cord_internal::SmallMemmove(data_, data_ + n, data_[kMaxInline] - n);
  reduce_size(n);
}

void Cord::InlineRep::AppendTree(CordRep* tree) {
  if (tree == nullptr) return;
  size_t len = data_[kMaxInline];
  if (len == 0) {
    set_tree(tree);
  } else {
    set_tree(Concat(force_tree(0), tree));
  }
}

void Cord::InlineRep::PrependTree(CordRep* tree) {
  if (tree == nullptr) return;
  size_t len = data_[kMaxInline];
  if (len == 0) {
    set_tree(tree);
  } else {
    set_tree(Concat(tree, force_tree(0)));
  }
}

// Searches for a non-full flat node at the rightmost leaf of the tree. If a
// suitable leaf is found, the function will update the length field for all
// nodes to account for the size increase. The append region address will be
// written to region and the actual size increase will be written to size.
static inline bool PrepareAppendRegion(CordRep* root, char** region,
                                       size_t* size, size_t max_length) {
  // Search down the right-hand path for a non-full FLAT node.
  CordRep* dst = root;
  while (dst->tag == CONCAT && dst->refcount.IsOne()) {
    dst = dst->concat()->right;
  }

  if (dst->tag < FLAT || !dst->refcount.IsOne()) {
    *region = nullptr;
    *size = 0;
    return false;
  }

  const size_t in_use = dst->length;
  const size_t capacity = TagToLength(dst->tag);
  if (in_use == capacity) {
    *region = nullptr;
    *size = 0;
    return false;
  }

  size_t size_increase = std::min(capacity - in_use, max_length);

  // We need to update the length fields for all nodes, including the leaf node.
  for (CordRep* rep = root; rep != dst; rep = rep->concat()->right) {
    rep->length += size_increase;
  }
  dst->length += size_increase;

  *region = dst->data + in_use;
  *size = size_increase;
  return true;
}

void Cord::InlineRep::GetAppendRegion(char** region, size_t* size,
                                      size_t max_length) {
  if (max_length == 0) {
    *region = nullptr;
    *size = 0;
    return;
  }

  // Try to fit in the inline buffer if possible.
  size_t inline_length = data_[kMaxInline];
  if (inline_length < kMaxInline && max_length <= kMaxInline - inline_length) {
    *region = data_ + inline_length;
    *size = max_length;
    data_[kMaxInline] = static_cast<char>(inline_length + max_length);
    return;
  }

  CordRep* root = force_tree(max_length);

  if (PrepareAppendRegion(root, region, size, max_length)) {
    return;
  }

  // Allocate new node.
  CordRep* new_node =
      NewFlat(std::max(static_cast<size_t>(root->length), max_length));
  new_node->length =
      std::min(static_cast<size_t>(TagToLength(new_node->tag)), max_length);
  *region = new_node->data;
  *size = new_node->length;
  replace_tree(Concat(root, new_node));
}

void Cord::InlineRep::GetAppendRegion(char** region, size_t* size) {
  const size_t max_length = std::numeric_limits<size_t>::max();

  // Try to fit in the inline buffer if possible.
  size_t inline_length = data_[kMaxInline];
  if (inline_length < kMaxInline) {
    *region = data_ + inline_length;
    *size = kMaxInline - inline_length;
    data_[kMaxInline] = kMaxInline;
    return;
  }

  CordRep* root = force_tree(max_length);

  if (PrepareAppendRegion(root, region, size, max_length)) {
    return;
  }

  // Allocate new node.
  CordRep* new_node = NewFlat(root->length);
  new_node->length = TagToLength(new_node->tag);
  *region = new_node->data;
  *size = new_node->length;
  replace_tree(Concat(root, new_node));
}

// If the rep is a leaf, this will increment the value at total_mem_usage and
// will return true.
static bool RepMemoryUsageLeaf(const CordRep* rep, size_t* total_mem_usage) {
  if (rep->tag >= FLAT) {
    *total_mem_usage += TagToAllocatedSize(rep->tag);
    return true;
  }
  if (rep->tag == EXTERNAL) {
    *total_mem_usage += sizeof(CordRepConcat) + rep->length;
    return true;
  }
  return false;
}

void Cord::InlineRep::AssignSlow(const Cord::InlineRep& src) {
  ClearSlow();

  memcpy(data_, src.data_, sizeof(data_));
  if (is_tree()) {
    Ref(tree());
  }
}

void Cord::InlineRep::ClearSlow() {
  if (is_tree()) {
    Unref(tree());
  }
  memset(data_, 0, sizeof(data_));
}

inline Cord::InternalChunkIterator Cord::internal_chunk_begin() const {
  return InternalChunkIterator(this);
}

inline Cord::InternalChunkRange Cord::InternalChunks() const {
  return InternalChunkRange(this);
}

// --------------------------------------------------------------------
// Constructors and destructors

Cord::Cord(const Cord& src) : contents_(src.contents_) {
  Ref(contents_.tree());  // Does nothing if contents_ has embedded data
}

Cord::Cord(absl::string_view src) {
  const size_t n = src.size();
  if (n <= InlineRep::kMaxInline) {
    contents_.set_data(src.data(), n, false);
  } else {
    contents_.set_tree(NewTree(src.data(), n, 0));
  }
}

// The destruction code is separate so that the compiler can determine
// that it does not need to call the destructor on a moved-from Cord.
void Cord::DestroyCordSlow() {
  Unref(VerifyTree(contents_.tree()));
}

// --------------------------------------------------------------------
// Mutators

void Cord::Clear() {
  Unref(contents_.clear());
}

Cord& Cord::operator=(absl::string_view src) {

  const char* data = src.data();
  size_t length = src.size();
  CordRep* tree = contents_.tree();
  if (length <= InlineRep::kMaxInline) {
    // Embed into this->contents_
    contents_.set_data(data, length, true);
    Unref(tree);
    return *this;
  }
  if (tree != nullptr && tree->tag >= FLAT &&
      TagToLength(tree->tag) >= length && tree->refcount.IsOne()) {
    // Copy in place if the existing FLAT node is reusable.
    memmove(tree->data, data, length);
    tree->length = length;
    VerifyTree(tree);
    return *this;
  }
  contents_.set_tree(NewTree(data, length, 0));
  Unref(tree);
  return *this;
}

// TODO(sanjay): Move to Cord::InlineRep section of file.  For now,
// we keep it here to make diffs easier.
void Cord::InlineRep::AppendArray(const char* src_data, size_t src_size) {
  if (src_size == 0) return;  // memcpy(_, nullptr, 0) is undefined.
  // Try to fit in the inline buffer if possible.
  size_t inline_length = data_[kMaxInline];
  if (inline_length < kMaxInline && src_size <= kMaxInline - inline_length) {
    // Append new data to embedded array
    data_[kMaxInline] = static_cast<char>(inline_length + src_size);
    memcpy(data_ + inline_length, src_data, src_size);
    return;
  }

  CordRep* root = tree();

  size_t appended = 0;
  if (root) {
    char* region;
    if (PrepareAppendRegion(root, &region, &appended, src_size)) {
      memcpy(region, src_data, appended);
    }
  } else {
    // It is possible that src_data == data_, but when we transition from an
    // InlineRep to a tree we need to assign data_ = root via set_tree. To
    // avoid corrupting the source data before we copy it, delay calling
    // set_tree until after we've copied data.
    // We are going from an inline size to beyond inline size. Make the new size
    // either double the inlined size, or the added size + 10%.
    const size_t size1 = inline_length * 2 + src_size;
    const size_t size2 = inline_length + src_size / 10;
    root = NewFlat(std::max<size_t>(size1, size2));
    appended = std::min(src_size, TagToLength(root->tag) - inline_length);
    memcpy(root->data, data_, inline_length);
    memcpy(root->data + inline_length, src_data, appended);
    root->length = inline_length + appended;
    set_tree(root);
  }

  src_data += appended;
  src_size -= appended;
  if (src_size == 0) {
    return;
  }

  // Use new block(s) for any remaining bytes that were not handled above.
  // Alloc extra memory only if the right child of the root of the new tree is
  // going to be a FLAT node, which will permit further inplace appends.
  size_t length = src_size;
  if (src_size < kMaxFlatLength) {
    // The new length is either
    // - old size + 10%
    // - old_size + src_size
    // This will cause a reasonable conservative step-up in size that is still
    // large enough to avoid excessive amounts of small fragments being added.
    length = std::max<size_t>(root->length / 10, src_size);
  }
  set_tree(Concat(root, NewTree(src_data, src_size, length - src_size)));
}

inline CordRep* Cord::TakeRep() const& {
  return Ref(contents_.tree());
}

inline CordRep* Cord::TakeRep() && {
  CordRep* rep = contents_.tree();
  contents_.clear();
  return rep;
}

template <typename C>
inline void Cord::AppendImpl(C&& src) {
  if (empty()) {
    // In case of an empty destination avoid allocating a new node, do not copy
    // data.
    *this = std::forward<C>(src);
    return;
  }

  // For short cords, it is faster to copy data if there is room in dst.
  const size_t src_size = src.contents_.size();
  if (src_size <= kMaxBytesToCopy) {
    CordRep* src_tree = src.contents_.tree();
    if (src_tree == nullptr) {
      // src has embedded data.
      contents_.AppendArray(src.contents_.data(), src_size);
      return;
    }
    if (src_tree->tag >= FLAT) {
      // src tree just has one flat node.
      contents_.AppendArray(src_tree->data, src_size);
      return;
    }
    if (&src == this) {
      // ChunkIterator below assumes that src is not modified during traversal.
      Append(Cord(src));
      return;
    }
    // TODO(mec): Should we only do this if "dst" has space?
    for (absl::string_view chunk : src.Chunks()) {
      Append(chunk);
    }
    return;
  }

  contents_.AppendTree(std::forward<C>(src).TakeRep());
}

void Cord::Append(const Cord& src) { AppendImpl(src); }

void Cord::Append(Cord&& src) { AppendImpl(std::move(src)); }

void Cord::Prepend(const Cord& src) {
  CordRep* src_tree = src.contents_.tree();
  if (src_tree != nullptr) {
    Ref(src_tree);
    contents_.PrependTree(src_tree);
    return;
  }

  // `src` cord is inlined.
  absl::string_view src_contents(src.contents_.data(), src.contents_.size());
  return Prepend(src_contents);
}

void Cord::Prepend(absl::string_view src) {
  if (src.empty()) return;  // memcpy(_, nullptr, 0) is undefined.
  size_t cur_size = contents_.size();
  if (!contents_.is_tree() && cur_size + src.size() <= InlineRep::kMaxInline) {
    // Use embedded storage.
    char data[InlineRep::kMaxInline + 1] = {0};
    data[InlineRep::kMaxInline] = cur_size + src.size();  // set size
    memcpy(data, src.data(), src.size());
    memcpy(data + src.size(), contents_.data(), cur_size);
    memcpy(reinterpret_cast<void*>(&contents_), data,
           InlineRep::kMaxInline + 1);
  } else {
    contents_.PrependTree(NewTree(src.data(), src.size(), 0));
  }
}

static CordRep* RemovePrefixFrom(CordRep* node, size_t n) {
  if (n >= node->length) return nullptr;
  if (n == 0) return Ref(node);
  cord_internal::CordTreeMutablePath rhs_stack;

  while (node->tag == CONCAT) {
    assert(n <= node->length);
    if (n < node->concat()->left->length) {
      // Push right to stack, descend left.
      rhs_stack.push_back(node->concat()->right);
      node = node->concat()->left;
    } else {
      // Drop left, descend right.
      n -= node->concat()->left->length;
      node = node->concat()->right;
    }
  }
  assert(n <= node->length);

  if (n == 0) {
    Ref(node);
  } else {
    size_t start = n;
    size_t len = node->length - n;
    if (node->tag == SUBSTRING) {
      // Consider in-place update of node, similar to in RemoveSuffixFrom().
      start += node->substring()->start;
      node = node->substring()->child;
    }
    node = NewSubstring(Ref(node), start, len);
  }
  while (!rhs_stack.empty()) {
    node = Concat(node, Ref(rhs_stack.back()));
    rhs_stack.pop_back();
  }
  return node;
}

// RemoveSuffixFrom() is very similar to RemovePrefixFrom(), with the
// exception that removing a suffix has an optimization where a node may be
// edited in place iff that node and all its ancestors have a refcount of 1.
static CordRep* RemoveSuffixFrom(CordRep* node, size_t n) {
  if (n >= node->length) return nullptr;
  if (n == 0) return Ref(node);
  absl::cord_internal::CordTreeMutablePath lhs_stack;
  bool inplace_ok = node->refcount.IsOne();

  while (node->tag == CONCAT) {
    assert(n <= node->length);
    if (n < node->concat()->right->length) {
      // Push left to stack, descend right.
      lhs_stack.push_back(node->concat()->left);
      node = node->concat()->right;
    } else {
      // Drop right, descend left.
      n -= node->concat()->right->length;
      node = node->concat()->left;
    }
    inplace_ok = inplace_ok && node->refcount.IsOne();
  }
  assert(n <= node->length);

  if (n == 0) {
    Ref(node);
  } else if (inplace_ok && node->tag != EXTERNAL) {
    // Consider making a new buffer if the current node capacity is much
    // larger than the new length.
    Ref(node);
    node->length -= n;
  } else {
    size_t start = 0;
    size_t len = node->length - n;
    if (node->tag == SUBSTRING) {
      start = node->substring()->start;
      node = node->substring()->child;
    }
    node = NewSubstring(Ref(node), start, len);
  }
  while (!lhs_stack.empty()) {
    node = Concat(Ref(lhs_stack.back()), node);
    lhs_stack.pop_back();
  }
  return node;
}

void Cord::RemovePrefix(size_t n) {
  ABSL_INTERNAL_CHECK(n <= size(),
                      absl::StrCat("Requested prefix size ", n,
                                   " exceeds Cord's size ", size()));
  CordRep* tree = contents_.tree();
  if (tree == nullptr) {
    contents_.remove_prefix(n);
  } else {
    CordRep* newrep = RemovePrefixFrom(tree, n);
    Unref(tree);
    contents_.replace_tree(VerifyTree(newrep));
  }
}

void Cord::RemoveSuffix(size_t n) {
  ABSL_INTERNAL_CHECK(n <= size(),
                      absl::StrCat("Requested suffix size ", n,
                                   " exceeds Cord's size ", size()));
  CordRep* tree = contents_.tree();
  if (tree == nullptr) {
    contents_.reduce_size(n);
  } else {
    CordRep* newrep = RemoveSuffixFrom(tree, n);
    Unref(tree);
    contents_.replace_tree(VerifyTree(newrep));
  }
}

// Work item for NewSubRange().
struct SubRange {
  SubRange() = default;
  SubRange(CordRep* a_node, size_t a_pos, size_t a_n)
      : node(a_node), pos(a_pos), n(a_n) {}
  CordRep* node;  // nullptr means concat last 2 results.
  size_t pos;
  size_t n;
};

static CordRep* NewSubRange(CordRep* node, size_t pos, size_t n) {
  cord_internal::CordTreeMutablePath results;
  // The algorithm below in worst case scenario adds up to 3 nodes to the `todo`
  // list, but we also pop one out on every cycle. If original tree has depth d
  // todo list can grew up to 2*d in size.
  cord_internal::CordTreePath<SubRange, 2 * cord_internal::MaxCordDepth()> todo;
  todo.push_back(SubRange(node, pos, n));
  do {
    const SubRange& sr = todo.back();
    node = sr.node;
    pos = sr.pos;
    n = sr.n;
    todo.pop_back();

    if (node == nullptr) {
      assert(results.size() >= 2);
      CordRep* right = results.back();
      results.pop_back();
      CordRep* left = results.back();
      results.pop_back();
      results.push_back(Concat(left, right));
    } else if (pos == 0 && n == node->length) {
      results.push_back(Ref(node));
    } else if (node->tag != CONCAT) {
      if (node->tag == SUBSTRING) {
        pos += node->substring()->start;
        node = node->substring()->child;
      }
      results.push_back(NewSubstring(Ref(node), pos, n));
    } else if (pos + n <= node->concat()->left->length) {
      todo.push_back(SubRange(node->concat()->left, pos, n));
    } else if (pos >= node->concat()->left->length) {
      pos -= node->concat()->left->length;
      todo.push_back(SubRange(node->concat()->right, pos, n));
    } else {
      size_t left_n = node->concat()->left->length - pos;
      todo.push_back(SubRange(nullptr, 0, 0));  // Concat()
      todo.push_back(SubRange(node->concat()->right, 0, n - left_n));
      todo.push_back(SubRange(node->concat()->left, pos, left_n));
    }
  } while (!todo.empty());
  assert(results.size() == 1);
  return results.back();
}

Cord Cord::Subcord(size_t pos, size_t new_size) const {
  Cord sub_cord;
  size_t length = size();
  if (pos > length) pos = length;
  if (new_size > length - pos) new_size = length - pos;
  CordRep* tree = contents_.tree();
  if (tree == nullptr) {
    // sub_cord is newly constructed, no need to re-zero-out the tail of
    // contents_ memory.
    sub_cord.contents_.set_data(contents_.data() + pos, new_size, false);
  } else if (new_size == 0) {
    // We want to return empty subcord, so nothing to do.
  } else if (new_size <= InlineRep::kMaxInline) {
    Cord::InternalChunkIterator it = internal_chunk_begin();
    it.AdvanceBytes(pos);
    char* dest = sub_cord.contents_.data_;
    size_t remaining_size = new_size;
    while (remaining_size > it->size()) {
      cord_internal::SmallMemmove(dest, it->data(), it->size());
      remaining_size -= it->size();
      dest += it->size();
      ++it;
    }
    cord_internal::SmallMemmove(dest, it->data(), remaining_size);
    sub_cord.contents_.data_[InlineRep::kMaxInline] = new_size;
  } else {
    sub_cord.contents_.set_tree(NewSubRange(tree, pos, new_size));
  }
  return sub_cord;
}

// --------------------------------------------------------------------
// Balancing

class CordForest {
 public:
  explicit CordForest(size_t length) : root_length_(length), trees_({}) {}

  void Build(CordRep* cord_root) {
    // We are adding up to two nodes to the `pending` list, but we also popping
    // one, so the size of `pending` will never exceed `MaxCordDepth()`.
    cord_internal::CordTreeMutablePath pending(cord_root);

    while (!pending.empty()) {
      CordRep* node = pending.back();
      pending.pop_back();
      CheckNode(node);
      if (ABSL_PREDICT_FALSE(node->tag != CONCAT)) {
        AddNode(node);
        continue;
      }

      CordRepConcat* concat_node = node->concat();
      if (IsNodeBalanced(concat_node)) {
        AddNode(node);
        continue;
      }
      pending.push_back(concat_node->right);
      pending.push_back(concat_node->left);

      if (concat_node->refcount.IsOne()) {
        concat_node->left = concat_freelist_;
        concat_freelist_ = concat_node;
      } else {
        Ref(concat_node->right);
        Ref(concat_node->left);
        Unref(concat_node);
      }
    }
  }

  CordRep* ConcatNodes() {
    CordRep* sum = nullptr;
    for (auto* node : trees_) {
      if (node == nullptr) continue;

      sum = PrependNode(node, sum);
      root_length_ -= node->length;
      if (root_length_ == 0) break;
    }
    ABSL_INTERNAL_CHECK(sum != nullptr, "Failed to locate sum node");
    return VerifyTree(sum);
  }

 private:
  CordRep* AppendNode(CordRep* node, CordRep* sum) {
    return (sum == nullptr) ? node : MakeConcat(sum, node);
  }

  CordRep* PrependNode(CordRep* node, CordRep* sum) {
    return (sum == nullptr) ? node : MakeConcat(node, sum);
  }

  void AddNode(CordRep* node) {
    CordRep* sum = nullptr;

    // Collect together everything with which we will merge with node
    int i = 0;
    for (; node->length >= kMinLength[i + 1]; ++i) {
      auto& tree_at_i = trees_[i];

      if (tree_at_i == nullptr) continue;
      sum = PrependNode(tree_at_i, sum);
      tree_at_i = nullptr;
    }

    sum = AppendNode(node, sum);

    // Insert sum into appropriate place in the forest
    for (; sum->length >= kMinLength[i]; ++i) {
      auto& tree_at_i = trees_[i];
      if (tree_at_i == nullptr) continue;

      sum = MakeConcat(tree_at_i, sum);
      tree_at_i = nullptr;
    }

    // kMinLength[0] == 1, which means sum->length >= kMinLength[0]
    assert(i > 0);
    trees_[i - 1] = sum;
  }

  // Make concat node trying to resue existing CordRepConcat nodes we
  // already collected in the concat_freelist_.
  CordRep* MakeConcat(CordRep* left, CordRep* right) {
    if (concat_freelist_ == nullptr) return RawConcat(left, right);

    CordRepConcat* rep = concat_freelist_;
    if (concat_freelist_->left == nullptr) {
      concat_freelist_ = nullptr;
    } else {
      concat_freelist_ = concat_freelist_->left->concat();
    }
    SetConcatChildren(rep, left, right);

    return rep;
  }

  static void CheckNode(CordRep* node) {
    ABSL_INTERNAL_CHECK(node->length != 0u, "");
    if (node->tag == CONCAT) {
      ABSL_INTERNAL_CHECK(node->concat()->left != nullptr, "");
      ABSL_INTERNAL_CHECK(node->concat()->right != nullptr, "");
      ABSL_INTERNAL_CHECK(node->length == (node->concat()->left->length +
                                           node->concat()->right->length),
                          "");
    }
  }

  size_t root_length_;
  std::array<cord_internal::CordRep*, cord_internal::MaxCordDepth()> trees_;

  // List of concat nodes we can re-use for Cord balancing.
  CordRepConcat* concat_freelist_ = nullptr;
};

static CordRep* Rebalance(CordRep* node) {
  VerifyTree(node);
  assert(node->tag == CONCAT);

  if (node->length == 0) {
    return nullptr;
  }

  CordForest forest(node->length);
  forest.Build(node);
  return forest.ConcatNodes();
}

// --------------------------------------------------------------------
// Comparators

namespace {

int ClampResult(int memcmp_res) {
  return static_cast<int>(memcmp_res > 0) - static_cast<int>(memcmp_res < 0);
}

int CompareChunks(absl::string_view* lhs, absl::string_view* rhs,
                  size_t* size_to_compare) {
  size_t compared_size = std::min(lhs->size(), rhs->size());
  assert(*size_to_compare >= compared_size);
  *size_to_compare -= compared_size;

  int memcmp_res = ::memcmp(lhs->data(), rhs->data(), compared_size);
  if (memcmp_res != 0) return memcmp_res;

  lhs->remove_prefix(compared_size);
  rhs->remove_prefix(compared_size);

  return 0;
}

// This overload set computes comparison results from memcmp result. This
// interface is used inside GenericCompare below. Differet implementations
// are specialized for int and bool. For int we clamp result to {-1, 0, 1}
// set. For bool we just interested in "value == 0".
template <typename ResultType>
ResultType ComputeCompareResult(int memcmp_res) {
  return ClampResult(memcmp_res);
}
template <>
bool ComputeCompareResult<bool>(int memcmp_res) {
  return memcmp_res == 0;
}

}  // namespace

// Helper routine. Locates the first flat chunk of the Cord without
// initializing the iterator.
inline absl::string_view Cord::InlineRep::FindFlatStartPiece() const {
  size_t n = data_[kMaxInline];
  if (n <= kMaxInline) {
    return absl::string_view(data_, n);
  }

  CordRep* node = tree();
  if (node->tag >= FLAT) {
    return absl::string_view(node->data, node->length);
  }

  if (node->tag == EXTERNAL) {
    return absl::string_view(node->external()->base, node->length);
  }

  // Walk down the left branches until we hit a non-CONCAT node.
  while (node->tag == CONCAT) {
    node = node->concat()->left;
  }

  // Get the child node if we encounter a SUBSTRING.
  size_t offset = 0;
  size_t length = node->length;
  assert(length != 0);

  if (node->tag == SUBSTRING) {
    offset = node->substring()->start;
    node = node->substring()->child;
  }

  if (node->tag >= FLAT) {
    return absl::string_view(node->data + offset, length);
  }

  assert((node->tag == EXTERNAL) && "Expect FLAT or EXTERNAL node here");

  return absl::string_view(node->external()->base + offset, length);
}

inline int Cord::CompareSlowPath(absl::string_view rhs, size_t compared_size,
                                 size_t size_to_compare) const {
  auto advance = [](Cord::InternalChunkIterator* it, absl::string_view* chunk) {
    if (!chunk->empty()) return true;
    ++*it;
    if (it->bytes_remaining_ == 0) return false;
    *chunk = **it;
    return true;
  };

  Cord::InternalChunkIterator lhs_it = internal_chunk_begin();

  // compared_size is inside first chunk.
  absl::string_view lhs_chunk =
      (lhs_it.bytes_remaining_ != 0) ? *lhs_it : absl::string_view();
  assert(compared_size <= lhs_chunk.size());
  assert(compared_size <= rhs.size());
  lhs_chunk.remove_prefix(compared_size);
  rhs.remove_prefix(compared_size);
  size_to_compare -= compared_size;  // skip already compared size.

  while (advance(&lhs_it, &lhs_chunk) && !rhs.empty()) {
    int comparison_result = CompareChunks(&lhs_chunk, &rhs, &size_to_compare);
    if (comparison_result != 0) return comparison_result;
    if (size_to_compare == 0) return 0;
  }

  return static_cast<int>(rhs.empty()) - static_cast<int>(lhs_chunk.empty());
}

inline int Cord::CompareSlowPath(const Cord& rhs, size_t compared_size,
                                 size_t size_to_compare) const {
  auto advance = [](Cord::InternalChunkIterator* it, absl::string_view* chunk) {
    if (!chunk->empty()) return true;
    ++*it;
    if (it->bytes_remaining_ == 0) return false;
    *chunk = **it;
    return true;
  };

  Cord::InternalChunkIterator lhs_it = internal_chunk_begin();
  Cord::InternalChunkIterator rhs_it = rhs.internal_chunk_begin();

  // compared_size is inside both first chunks.
  absl::string_view lhs_chunk =
      (lhs_it.bytes_remaining_ != 0) ? *lhs_it : absl::string_view();
  absl::string_view rhs_chunk =
      (rhs_it.bytes_remaining_ != 0) ? *rhs_it : absl::string_view();
  assert(compared_size <= lhs_chunk.size());
  assert(compared_size <= rhs_chunk.size());
  lhs_chunk.remove_prefix(compared_size);
  rhs_chunk.remove_prefix(compared_size);
  size_to_compare -= compared_size;  // skip already compared size.

  while (advance(&lhs_it, &lhs_chunk) && advance(&rhs_it, &rhs_chunk)) {
    int memcmp_res = CompareChunks(&lhs_chunk, &rhs_chunk, &size_to_compare);
    if (memcmp_res != 0) return memcmp_res;
    if (size_to_compare == 0) return 0;
  }

  return static_cast<int>(rhs_chunk.empty()) -
         static_cast<int>(lhs_chunk.empty());
}

inline absl::string_view Cord::GetFirstChunk(const Cord& c) {
  return c.contents_.FindFlatStartPiece();
}
inline absl::string_view Cord::GetFirstChunk(absl::string_view sv) {
  return sv;
}

// Compares up to 'size_to_compare' bytes of 'lhs' with 'rhs'. It is assumed
// that 'size_to_compare' is greater that size of smallest of first chunks.
template <typename ResultType, typename RHS>
ResultType GenericCompare(const Cord& lhs, const RHS& rhs,
                          size_t size_to_compare) {
  absl::string_view lhs_chunk = Cord::GetFirstChunk(lhs);
  absl::string_view rhs_chunk = Cord::GetFirstChunk(rhs);

  size_t compared_size = std::min(lhs_chunk.size(), rhs_chunk.size());
  assert(size_to_compare >= compared_size);
  int memcmp_res = ::memcmp(lhs_chunk.data(), rhs_chunk.data(), compared_size);
  if (compared_size == size_to_compare || memcmp_res != 0) {
    return ComputeCompareResult<ResultType>(memcmp_res);
  }

  return ComputeCompareResult<ResultType>(
      lhs.CompareSlowPath(rhs, compared_size, size_to_compare));
}

bool Cord::EqualsImpl(absl::string_view rhs, size_t size_to_compare) const {
  return GenericCompare<bool>(*this, rhs, size_to_compare);
}

bool Cord::EqualsImpl(const Cord& rhs, size_t size_to_compare) const {
  return GenericCompare<bool>(*this, rhs, size_to_compare);
}

template <typename RHS>
inline int SharedCompareImpl(const Cord& lhs, const RHS& rhs) {
  size_t lhs_size = lhs.size();
  size_t rhs_size = rhs.size();
  if (lhs_size == rhs_size) {
    return GenericCompare<int>(lhs, rhs, lhs_size);
  }
  if (lhs_size < rhs_size) {
    auto data_comp_res = GenericCompare<int>(lhs, rhs, lhs_size);
    return data_comp_res == 0 ? -1 : data_comp_res;
  }

  auto data_comp_res = GenericCompare<int>(lhs, rhs, rhs_size);
  return data_comp_res == 0 ? +1 : data_comp_res;
}

int Cord::Compare(absl::string_view rhs) const {
  return SharedCompareImpl(*this, rhs);
}

int Cord::CompareImpl(const Cord& rhs) const {
  return SharedCompareImpl(*this, rhs);
}

bool Cord::EndsWith(absl::string_view rhs) const {
  size_t my_size = size();
  size_t rhs_size = rhs.size();

  if (my_size < rhs_size) return false;

  Cord tmp(*this);
  tmp.RemovePrefix(my_size - rhs_size);
  return tmp.EqualsImpl(rhs, rhs_size);
}

bool Cord::EndsWith(const Cord& rhs) const {
  size_t my_size = size();
  size_t rhs_size = rhs.size();

  if (my_size < rhs_size) return false;

  Cord tmp(*this);
  tmp.RemovePrefix(my_size - rhs_size);
  return tmp.EqualsImpl(rhs, rhs_size);
}

// --------------------------------------------------------------------
// Misc.

Cord::operator std::string() const {
  std::string s;
  absl::CopyCordToString(*this, &s);
  return s;
}

void CopyCordToString(const Cord& src, std::string* dst) {
  if (!src.contents_.is_tree()) {
    src.contents_.CopyTo(dst);
  } else {
    absl::strings_internal::STLStringResizeUninitialized(dst, src.size());
    src.CopyToArraySlowPath(&(*dst)[0]);
  }
}

void Cord::CopyToArraySlowPath(char* dst) const {
  assert(contents_.is_tree());
  absl::string_view fragment;
  if (GetFlatAux(contents_.tree(), &fragment)) {
    memcpy(dst, fragment.data(), fragment.size());
    return;
  }
  for (absl::string_view chunk : Chunks()) {
    memcpy(dst, chunk.data(), chunk.size());
    dst += chunk.size();
  }
}

template <typename StorageType>
Cord::GenericChunkIterator<StorageType>&
Cord::GenericChunkIterator<StorageType>::operator++() {
  ABSL_HARDENING_ASSERT(bytes_remaining_ > 0 &&
                        "Attempted to iterate past `end()`");
  assert(bytes_remaining_ >= current_chunk_.size());
  bytes_remaining_ -= current_chunk_.size();

  if (stack_of_right_children_.empty()) {
    assert(!current_chunk_.empty());  // Called on invalid iterator.
    // We have reached the end of the Cord.
    return *this;
  }

  // Process the next node on the stack.
  CordRep* node = stack_of_right_children_.back();
  stack_of_right_children_.pop_back();

  // Walk down the left branches until we hit a non-CONCAT node. Save the
  // right children to the stack for subsequent traversal.
  while (node->tag == CONCAT) {
    stack_of_right_children_.push_back(node->concat()->right);
    node = node->concat()->left;
  }

  // Get the child node if we encounter a SUBSTRING.
  size_t offset = 0;
  size_t length = node->length;
  if (node->tag == SUBSTRING) {
    offset = node->substring()->start;
    node = node->substring()->child;
  }

  assert(node->tag == EXTERNAL || node->tag >= FLAT);
  assert(length != 0);
  const char* data =
      node->tag == EXTERNAL ? node->external()->base : node->data;
  current_chunk_ = absl::string_view(data + offset, length);
  current_leaf_ = node;
  return *this;
}

template <typename StorageType>
Cord Cord::GenericChunkIterator<StorageType>::AdvanceAndReadBytes(size_t n) {
  ABSL_HARDENING_ASSERT(bytes_remaining_ >= n &&
                        "Attempted to iterate past `end()`");
  Cord subcord;

  if (n <= InlineRep::kMaxInline) {
    // Range to read fits in inline data. Flatten it.
    char* data = subcord.contents_.set_data(n);
    while (n > current_chunk_.size()) {
      memcpy(data, current_chunk_.data(), current_chunk_.size());
      data += current_chunk_.size();
      n -= current_chunk_.size();
      ++*this;
    }
    memcpy(data, current_chunk_.data(), n);
    if (n < current_chunk_.size()) {
      RemoveChunkPrefix(n);
    } else if (n > 0) {
      ++*this;
    }
    return subcord;
  }
  if (n < current_chunk_.size()) {
    // Range to read is a proper subrange of the current chunk.
    assert(current_leaf_ != nullptr);
    CordRep* subnode = Ref(current_leaf_);
    const char* data =
        subnode->tag == EXTERNAL ? subnode->external()->base : subnode->data;
    subnode = NewSubstring(subnode, current_chunk_.data() - data, n);
    subcord.contents_.set_tree(VerifyTree(subnode));
    RemoveChunkPrefix(n);
    return subcord;
  }

  // Range to read begins with a proper subrange of the current chunk.
  assert(!current_chunk_.empty());
  assert(current_leaf_ != nullptr);
  CordRep* subnode = Ref(current_leaf_);
  if (current_chunk_.size() < subnode->length) {
    const char* data =
        subnode->tag == EXTERNAL ? subnode->external()->base : subnode->data;
    subnode = NewSubstring(subnode, current_chunk_.data() - data,
                           current_chunk_.size());
  }
  n -= current_chunk_.size();
  bytes_remaining_ -= current_chunk_.size();

  // Process the next node(s) on the stack, reading whole subtrees depending on
  // their length and how many bytes we are advancing.
  CordRep* node = nullptr;
  while (!stack_of_right_children_.empty()) {
    node = stack_of_right_children_.back();
    stack_of_right_children_.pop_back();
    if (node->length > n) break;
    // TODO(qrczak): This might unnecessarily recreate existing concat nodes.
    // Avoiding that would need pretty complicated logic (instead of
    // current_leaf_, keep current_subtree_ which points to the highest node
    // such that the current leaf can be found on the path of left children
    // starting from current_subtree_; delay creating subnode while node is
    // below current_subtree_; find the proper node along the path of left
    // children starting from current_subtree_ if this loop exits while staying
    // below current_subtree_; etc.; alternatively, push parents instead of
    // right children on the stack).
    subnode = Concat(subnode, Ref(node));
    n -= node->length;
    bytes_remaining_ -= node->length;
    node = nullptr;
  }

  if (node == nullptr) {
    // We have reached the end of the Cord.
    assert(bytes_remaining_ == 0);
    subcord.contents_.set_tree(VerifyTree(subnode));
    return subcord;
  }

  // Walk down the appropriate branches until we hit a non-CONCAT node. Save the
  // right children to the stack for subsequent traversal.
  while (node->tag == CONCAT) {
    if (node->concat()->left->length > n) {
      // Push right, descend left.
      stack_of_right_children_.push_back(node->concat()->right);
      node = node->concat()->left;
    } else {
      // Read left, descend right.
      subnode = Concat(subnode, Ref(node->concat()->left));
      n -= node->concat()->left->length;
      bytes_remaining_ -= node->concat()->left->length;
      node = node->concat()->right;
    }
  }

  // Get the child node if we encounter a SUBSTRING.
  size_t offset = 0;
  size_t length = node->length;
  if (node->tag == SUBSTRING) {
    offset = node->substring()->start;
    node = node->substring()->child;
  }

  // Range to read ends with a proper (possibly empty) subrange of the current
  // chunk.
  assert(node->tag == EXTERNAL || node->tag >= FLAT);
  assert(length > n);
  if (n > 0) subnode = Concat(subnode, NewSubstring(Ref(node), offset, n));
  const char* data =
      node->tag == EXTERNAL ? node->external()->base : node->data;
  current_chunk_ = absl::string_view(data + offset + n, length - n);
  current_leaf_ = node;
  bytes_remaining_ -= n;
  subcord.contents_.set_tree(VerifyTree(subnode));
  return subcord;
}

template <typename StorageType>
void Cord::GenericChunkIterator<StorageType>::AdvanceBytesSlowPath(size_t n) {
  assert(bytes_remaining_ >= n && "Attempted to iterate past `end()`");
  assert(n >= current_chunk_.size());  // This should only be called when
                                       // iterating to a new node.

  n -= current_chunk_.size();
  bytes_remaining_ -= current_chunk_.size();

  // Process the next node(s) on the stack, skipping whole subtrees depending on
  // their length and how many bytes we are advancing.
  CordRep* node = nullptr;
  while (!stack_of_right_children_.empty()) {
    node = stack_of_right_children_.back();
    stack_of_right_children_.pop_back();
    if (node->length > n) break;
    n -= node->length;
    bytes_remaining_ -= node->length;
    node = nullptr;
  }

  if (node == nullptr) {
    // We have reached the end of the Cord.
    assert(bytes_remaining_ == 0);
    return;
  }

  // Walk down the appropriate branches until we hit a non-CONCAT node. Save the
  // right children to the stack for subsequent traversal.
  while (node->tag == CONCAT) {
    if (node->concat()->left->length > n) {
      // Push right, descend left.
      stack_of_right_children_.push_back(node->concat()->right);
      node = node->concat()->left;
    } else {
      // Skip left, descend right.
      n -= node->concat()->left->length;
      bytes_remaining_ -= node->concat()->left->length;
      node = node->concat()->right;
    }
  }

  // Get the child node if we encounter a SUBSTRING.
  size_t offset = 0;
  size_t length = node->length;
  if (node->tag == SUBSTRING) {
    offset = node->substring()->start;
    node = node->substring()->child;
  }

  assert(node->tag == EXTERNAL || node->tag >= FLAT);
  assert(length > n);
  const char* data =
      node->tag == EXTERNAL ? node->external()->base : node->data;
  current_chunk_ = absl::string_view(data + offset + n, length - n);
  current_leaf_ = node;
  bytes_remaining_ -= n;
}

char Cord::operator[](size_t i) const {
  ABSL_HARDENING_ASSERT(i < size());
  size_t offset = i;
  const CordRep* rep = contents_.tree();
  if (rep == nullptr) {
    return contents_.data()[i];
  }
  while (true) {
    assert(rep != nullptr);
    assert(offset < rep->length);
    if (rep->tag >= FLAT) {
      // Get the "i"th character directly from the flat array.
      return rep->data[offset];
    } else if (rep->tag == EXTERNAL) {
      // Get the "i"th character from the external array.
      return rep->external()->base[offset];
    } else if (rep->tag == CONCAT) {
      // Recursively branch to the side of the concatenation that the "i"th
      // character is on.
      size_t left_length = rep->concat()->left->length;
      if (offset < left_length) {
        rep = rep->concat()->left;
      } else {
        offset -= left_length;
        rep = rep->concat()->right;
      }
    } else {
      // This must be a substring a node, so bypass it to get to the child.
      assert(rep->tag == SUBSTRING);
      offset += rep->substring()->start;
      rep = rep->substring()->child;
    }
  }
}

absl::string_view Cord::FlattenSlowPath() {
  size_t total_size = size();
  CordRep* new_rep;
  char* new_buffer;

  // Try to put the contents into a new flat rep. If they won't fit in the
  // biggest possible flat node, use an external rep instead.
  if (total_size <= kMaxFlatLength) {
    new_rep = NewFlat(total_size);
    new_rep->length = total_size;
    new_buffer = new_rep->data;
    CopyToArraySlowPath(new_buffer);
  } else {
    new_buffer = std::allocator<char>().allocate(total_size);
    CopyToArraySlowPath(new_buffer);
    new_rep = absl::cord_internal::NewExternalRep(
        absl::string_view(new_buffer, total_size), [](absl::string_view s) {
          std::allocator<char>().deallocate(const_cast<char*>(s.data()),
                                            s.size());
        });
  }
  Unref(contents_.tree());
  contents_.set_tree(new_rep);
  return absl::string_view(new_buffer, total_size);
}

/* static */ bool Cord::GetFlatAux(CordRep* rep, absl::string_view* fragment) {
  assert(rep != nullptr);
  if (rep->tag >= FLAT) {
    *fragment = absl::string_view(rep->data, rep->length);
    return true;
  } else if (rep->tag == EXTERNAL) {
    *fragment = absl::string_view(rep->external()->base, rep->length);
    return true;
  } else if (rep->tag == SUBSTRING) {
    CordRep* child = rep->substring()->child;
    if (child->tag >= FLAT) {
      *fragment =
          absl::string_view(child->data + rep->substring()->start, rep->length);
      return true;
    } else if (child->tag == EXTERNAL) {
      *fragment = absl::string_view(
          child->external()->base + rep->substring()->start, rep->length);
      return true;
    }
  }
  return false;
}

/* static */ void Cord::ForEachChunkAux(
    absl::cord_internal::CordRep* rep,
    absl::FunctionRef<void(absl::string_view)> callback) {
  assert(rep != nullptr);
  int stack_pos = 0;
  constexpr int stack_max = 128;
  // Stack of right branches for tree traversal
  absl::cord_internal::CordRep* stack[stack_max];
  absl::cord_internal::CordRep* current_node = rep;
  while (true) {
    if (current_node->tag == CONCAT) {
      if (stack_pos == stack_max) {
        // There's no more room on our stack array to add another right branch,
        // and the idea is to avoid allocations, so call this function
        // recursively to navigate this subtree further.  (This is not something
        // we expect to happen in practice).
        ForEachChunkAux(current_node, callback);

        // Pop the next right branch and iterate.
        current_node = stack[--stack_pos];
        continue;
      } else {
        // Save the right branch for later traversal and continue down the left
        // branch.
        stack[stack_pos++] = current_node->concat()->right;
        current_node = current_node->concat()->left;
        continue;
      }
    }
    // This is a leaf node, so invoke our callback.
    absl::string_view chunk;
    bool success = GetFlatAux(current_node, &chunk);
    assert(success);
    if (success) {
      callback(chunk);
    }
    if (stack_pos == 0) {
      // end of traversal
      return;
    }
    current_node = stack[--stack_pos];
  }
}

static void DumpNode(const CordRep* rep, bool include_data, std::ostream* os) {
  const int kIndentStep = 1;
  int indent = 0;
  cord_internal::CordTreeConstPath stack;
  cord_internal::CordTreePath<int, cord_internal::MaxCordDepth()> indents;
  for (;;) {
    *os << std::setw(3) << rep->refcount.Get();
    *os << " " << std::setw(7) << rep->length;
    *os << " [";
    if (include_data) *os << static_cast<const void*>(rep);
    *os << "]";
    *os << " " << (IsNodeBalanced(rep) ? 'b' : 'u');
    *os << " " << std::setw(indent) << "";
    if (rep->tag == CONCAT) {
      *os << "CONCAT depth=" << Depth(rep) << "\n";
      indent += kIndentStep;
      indents.push_back(indent);
      stack.push_back(rep->concat()->right);
      rep = rep->concat()->left;
    } else if (rep->tag == SUBSTRING) {
      *os << "SUBSTRING @ " << rep->substring()->start << "\n";
      indent += kIndentStep;
      rep = rep->substring()->child;
    } else {  // Leaf
      if (rep->tag == EXTERNAL) {
        *os << "EXTERNAL [";
        if (include_data)
          *os << absl::CEscape(std::string(rep->external()->base, rep->length));
        *os << "]\n";
      } else {
        *os << "FLAT cap=" << TagToLength(rep->tag) << " [";
        if (include_data)
          *os << absl::CEscape(absl::string_view(rep->data, rep->length));
        *os << "]\n";
      }
      if (stack.empty()) break;
      rep = stack.back();
      stack.pop_back();
      indent = indents.back();
      indents.pop_back();
    }
  }
  ABSL_INTERNAL_CHECK(indents.empty(), "");
}

static std::string ReportError(const CordRep* root, const CordRep* node) {
  std::ostringstream buf;
  buf << "Error at node " << node << " in:";
  DumpNode(root, true, &buf);
  return buf.str();
}

static bool VerifyNode(const CordRep* root, const CordRep* start_node,
                       bool full_validation) {
  cord_internal::CordTreeConstPath worklist;
  worklist.push_back(start_node);
  do {
    const CordRep* node = worklist.back();
    worklist.pop_back();

    ABSL_INTERNAL_CHECK(node != nullptr, ReportError(root, node));
    if (node != root) {
      ABSL_INTERNAL_CHECK(node->length != 0, ReportError(root, node));
    }

    if (node->tag == CONCAT) {
      ABSL_INTERNAL_CHECK(node->concat()->left != nullptr,
                          ReportError(root, node));
      ABSL_INTERNAL_CHECK(node->concat()->right != nullptr,
                          ReportError(root, node));
      ABSL_INTERNAL_CHECK((node->length == node->concat()->left->length +
                                               node->concat()->right->length),
                          ReportError(root, node));
      if (full_validation) {
        worklist.push_back(node->concat()->right);
        worklist.push_back(node->concat()->left);
      }
    } else if (node->tag >= FLAT) {
      ABSL_INTERNAL_CHECK(node->length <= TagToLength(node->tag),
                          ReportError(root, node));
    } else if (node->tag == EXTERNAL) {
      ABSL_INTERNAL_CHECK(node->external()->base != nullptr,
                          ReportError(root, node));
    } else if (node->tag == SUBSTRING) {
      ABSL_INTERNAL_CHECK(
          node->substring()->start < node->substring()->child->length,
          ReportError(root, node));
      ABSL_INTERNAL_CHECK(node->substring()->start + node->length <=
                              node->substring()->child->length,
                          ReportError(root, node));
    }
  } while (!worklist.empty());
  return true;
}

// Traverses the tree and computes the total memory allocated.
/* static */ size_t Cord::MemoryUsageAux(const CordRep* rep) {
  size_t total_mem_usage = 0;

  // Allow a quick exit for the common case that the root is a leaf.
  if (RepMemoryUsageLeaf(rep, &total_mem_usage)) {
    return total_mem_usage;
  }

  // Iterate over the tree. cur_node is never a leaf node and leaf nodes will
  // never be appended to tree_stack. This reduces overhead from manipulating
  // tree_stack.
  cord_internal::CordTreeConstPath tree_stack;
  const CordRep* cur_node = rep;
  while (true) {
    const CordRep* next_node = nullptr;

    if (cur_node->tag == CONCAT) {
      total_mem_usage += sizeof(CordRepConcat);
      const CordRep* left = cur_node->concat()->left;
      if (!RepMemoryUsageLeaf(left, &total_mem_usage)) {
        next_node = left;
      }

      const CordRep* right = cur_node->concat()->right;
      if (!RepMemoryUsageLeaf(right, &total_mem_usage)) {
        if (next_node) {
          tree_stack.push_back(next_node);
        }
        next_node = right;
      }
    } else {
      // Since cur_node is not a leaf or a concat node it must be a substring.
      assert(cur_node->tag == SUBSTRING);
      total_mem_usage += sizeof(CordRepSubstring);
      next_node = cur_node->substring()->child;
      if (RepMemoryUsageLeaf(next_node, &total_mem_usage)) {
        next_node = nullptr;
      }
    }

    if (!next_node) {
      if (tree_stack.empty()) {
        return total_mem_usage;
      }
      next_node = tree_stack.back();
      tree_stack.pop_back();
    }
    cur_node = next_node;
  }
}

std::ostream& operator<<(std::ostream& out, const Cord& cord) {
  for (absl::string_view chunk : cord.Chunks()) {
    out.write(chunk.data(), chunk.size());
  }
  return out;
}

template class Cord::GenericChunkIterator<cord_internal::CordTreeMutablePath>;
template class Cord::GenericChunkIterator<cord_internal::CordTreeDynamicPath>;

namespace strings_internal {
size_t CordTestAccess::FlatOverhead() { return kFlatOverhead; }
size_t CordTestAccess::MaxFlatLength() { return kMaxFlatLength; }
size_t CordTestAccess::FlatTagToLength(uint8_t tag) {
  return TagToLength(tag);
}
uint8_t CordTestAccess::LengthToTag(size_t s) {
  ABSL_INTERNAL_CHECK(s <= kMaxFlatLength, absl::StrCat("Invalid length ", s));
  return AllocatedSizeToTag(s + kFlatOverhead);
}
size_t CordTestAccess::SizeofCordRepConcat() { return sizeof(CordRepConcat); }
size_t CordTestAccess::SizeofCordRepExternal() {
  return sizeof(CordRepExternal);
}
size_t CordTestAccess::SizeofCordRepSubstring() {
  return sizeof(CordRepSubstring);
}
}  // namespace strings_internal
ABSL_NAMESPACE_END
}  // namespace absl
