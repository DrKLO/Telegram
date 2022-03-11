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
//
// An open-addressing
// hashtable with quadratic probing.
//
// This is a low level hashtable on top of which different interfaces can be
// implemented, like flat_hash_set, node_hash_set, string_hash_set, etc.
//
// The table interface is similar to that of std::unordered_set. Notable
// differences are that most member functions support heterogeneous keys when
// BOTH the hash and eq functions are marked as transparent. They do so by
// providing a typedef called `is_transparent`.
//
// When heterogeneous lookup is enabled, functions that take key_type act as if
// they have an overload set like:
//
//   iterator find(const key_type& key);
//   template <class K>
//   iterator find(const K& key);
//
//   size_type erase(const key_type& key);
//   template <class K>
//   size_type erase(const K& key);
//
//   std::pair<iterator, iterator> equal_range(const key_type& key);
//   template <class K>
//   std::pair<iterator, iterator> equal_range(const K& key);
//
// When heterogeneous lookup is disabled, only the explicit `key_type` overloads
// exist.
//
// find() also supports passing the hash explicitly:
//
//   iterator find(const key_type& key, size_t hash);
//   template <class U>
//   iterator find(const U& key, size_t hash);
//
// In addition the pointer to element and iterator stability guarantees are
// weaker: all iterators and pointers are invalidated after a new element is
// inserted.
//
// IMPLEMENTATION DETAILS
//
// The table stores elements inline in a slot array. In addition to the slot
// array the table maintains some control state per slot. The extra state is one
// byte per slot and stores empty or deleted marks, or alternatively 7 bits from
// the hash of an occupied slot. The table is split into logical groups of
// slots, like so:
//
//      Group 1         Group 2        Group 3
// +---------------+---------------+---------------+
// | | | | | | | | | | | | | | | | | | | | | | | | |
// +---------------+---------------+---------------+
//
// On lookup the hash is split into two parts:
// - H2: 7 bits (those stored in the control bytes)
// - H1: the rest of the bits
// The groups are probed using H1. For each group the slots are matched to H2 in
// parallel. Because H2 is 7 bits (128 states) and the number of slots per group
// is low (8 or 16) in almost all cases a match in H2 is also a lookup hit.
//
// On insert, once the right group is found (as in lookup), its slots are
// filled in order.
//
// On erase a slot is cleared. In case the group did not have any empty slots
// before the erase, the erased slot is marked as deleted.
//
// Groups without empty slots (but maybe with deleted slots) extend the probe
// sequence. The probing algorithm is quadratic. Given N the number of groups,
// the probing function for the i'th probe is:
//
//   P(0) = H1 % N
//
//   P(i) = (P(i - 1) + i) % N
//
// This probing function guarantees that after N probes, all the groups of the
// table will be probed exactly once.
//
// The control state and slot array are stored contiguously in a shared heap
// allocation. The layout of this allocation is: `capacity()` control bytes,
// one sentinel control byte, `Group::kWidth - 1` cloned control bytes,
// <possible padding>, `capacity()` slots. The sentinel control byte is used in
// iteration so we know when we reach the end of the table. The cloned control
// bytes at the end of the table are cloned from the beginning of the table so
// groups that begin near the end of the table can see a full group. In cases in
// which there are more than `capacity()` cloned control bytes, the extra bytes
// are `kEmpty`, and these ensure that we always see at least one empty slot and
// can stop an unsuccessful search.

#ifndef ABSL_CONTAINER_INTERNAL_RAW_HASH_SET_H_
#define ABSL_CONTAINER_INTERNAL_RAW_HASH_SET_H_

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iterator>
#include <limits>
#include <memory>
#include <tuple>
#include <type_traits>
#include <utility>

#include "absl/base/internal/endian.h"
#include "absl/base/optimization.h"
#include "absl/base/port.h"
#include "absl/container/internal/common.h"
#include "absl/container/internal/compressed_tuple.h"
#include "absl/container/internal/container_memory.h"
#include "absl/container/internal/hash_policy_traits.h"
#include "absl/container/internal/hashtable_debug_hooks.h"
#include "absl/container/internal/hashtablez_sampler.h"
#include "absl/container/internal/have_sse.h"
#include "absl/memory/memory.h"
#include "absl/meta/type_traits.h"
#include "absl/numeric/bits.h"
#include "absl/utility/utility.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace container_internal {

template <typename AllocType>
void SwapAlloc(AllocType& lhs, AllocType& rhs,
               std::true_type /* propagate_on_container_swap */) {
  using std::swap;
  swap(lhs, rhs);
}
template <typename AllocType>
void SwapAlloc(AllocType& /*lhs*/, AllocType& /*rhs*/,
               std::false_type /* propagate_on_container_swap */) {}

template <size_t Width>
class probe_seq {
 public:
  probe_seq(size_t hash, size_t mask) {
    assert(((mask + 1) & mask) == 0 && "not a mask");
    mask_ = mask;
    offset_ = hash & mask_;
  }
  size_t offset() const { return offset_; }
  size_t offset(size_t i) const { return (offset_ + i) & mask_; }

  void next() {
    index_ += Width;
    offset_ += index_;
    offset_ &= mask_;
  }
  // 0-based probe index. The i-th probe in the probe sequence.
  size_t index() const { return index_; }

 private:
  size_t mask_;
  size_t offset_;
  size_t index_ = 0;
};

template <class ContainerKey, class Hash, class Eq>
struct RequireUsableKey {
  template <class PassedKey, class... Args>
  std::pair<
      decltype(std::declval<const Hash&>()(std::declval<const PassedKey&>())),
      decltype(std::declval<const Eq&>()(std::declval<const ContainerKey&>(),
                                         std::declval<const PassedKey&>()))>*
  operator()(const PassedKey&, const Args&...) const;
};

template <class E, class Policy, class Hash, class Eq, class... Ts>
struct IsDecomposable : std::false_type {};

template <class Policy, class Hash, class Eq, class... Ts>
struct IsDecomposable<
    absl::void_t<decltype(
        Policy::apply(RequireUsableKey<typename Policy::key_type, Hash, Eq>(),
                      std::declval<Ts>()...))>,
    Policy, Hash, Eq, Ts...> : std::true_type {};

// TODO(alkis): Switch to std::is_nothrow_swappable when gcc/clang supports it.
template <class T>
constexpr bool IsNoThrowSwappable(std::true_type = {} /* is_swappable */) {
  using std::swap;
  return noexcept(swap(std::declval<T&>(), std::declval<T&>()));
}
template <class T>
constexpr bool IsNoThrowSwappable(std::false_type /* is_swappable */) {
  return false;
}

template <typename T>
uint32_t TrailingZeros(T x) {
  ABSL_INTERNAL_ASSUME(x != 0);
  return static_cast<uint32_t>(countr_zero(x));
}

// An abstraction over a bitmask. It provides an easy way to iterate through the
// indexes of the set bits of a bitmask.  When Shift=0 (platforms with SSE),
// this is a true bitmask.  On non-SSE, platforms the arithematic used to
// emulate the SSE behavior works in bytes (Shift=3) and leaves each bytes as
// either 0x00 or 0x80.
//
// For example:
//   for (int i : BitMask<uint32_t, 16>(0x5)) -> yields 0, 2
//   for (int i : BitMask<uint64_t, 8, 3>(0x0000000080800000)) -> yields 2, 3
template <class T, int SignificantBits, int Shift = 0>
class BitMask {
  static_assert(std::is_unsigned<T>::value, "");
  static_assert(Shift == 0 || Shift == 3, "");

 public:
  // These are useful for unit tests (gunit).
  using value_type = int;
  using iterator = BitMask;
  using const_iterator = BitMask;

  explicit BitMask(T mask) : mask_(mask) {}
  BitMask& operator++() {
    mask_ &= (mask_ - 1);
    return *this;
  }
  explicit operator bool() const { return mask_ != 0; }
  uint32_t operator*() const { return LowestBitSet(); }
  uint32_t LowestBitSet() const {
    return container_internal::TrailingZeros(mask_) >> Shift;
  }
  uint32_t HighestBitSet() const {
    return static_cast<uint32_t>((bit_width(mask_) - 1) >> Shift);
  }

  BitMask begin() const { return *this; }
  BitMask end() const { return BitMask(0); }

  uint32_t TrailingZeros() const {
    return container_internal::TrailingZeros(mask_) >> Shift;
  }

  uint32_t LeadingZeros() const {
    constexpr int total_significant_bits = SignificantBits << Shift;
    constexpr int extra_bits = sizeof(T) * 8 - total_significant_bits;
    return static_cast<uint32_t>(countl_zero(mask_ << extra_bits)) >> Shift;
  }

 private:
  friend bool operator==(const BitMask& a, const BitMask& b) {
    return a.mask_ == b.mask_;
  }
  friend bool operator!=(const BitMask& a, const BitMask& b) {
    return a.mask_ != b.mask_;
  }

  T mask_;
};

using h2_t = uint8_t;

// The values here are selected for maximum performance. See the static asserts
// below for details. We use an enum class so that when strict aliasing is
// enabled, the compiler knows ctrl_t doesn't alias other types.
enum class ctrl_t : int8_t {
  kEmpty = -128,   // 0b10000000
  kDeleted = -2,   // 0b11111110
  kSentinel = -1,  // 0b11111111
};
static_assert(
    (static_cast<int8_t>(ctrl_t::kEmpty) &
     static_cast<int8_t>(ctrl_t::kDeleted) &
     static_cast<int8_t>(ctrl_t::kSentinel) & 0x80) != 0,
    "Special markers need to have the MSB to make checking for them efficient");
static_assert(
    ctrl_t::kEmpty < ctrl_t::kSentinel && ctrl_t::kDeleted < ctrl_t::kSentinel,
    "ctrl_t::kEmpty and ctrl_t::kDeleted must be smaller than "
    "ctrl_t::kSentinel to make the SIMD test of IsEmptyOrDeleted() efficient");
static_assert(
    ctrl_t::kSentinel == static_cast<ctrl_t>(-1),
    "ctrl_t::kSentinel must be -1 to elide loading it from memory into SIMD "
    "registers (pcmpeqd xmm, xmm)");
static_assert(ctrl_t::kEmpty == static_cast<ctrl_t>(-128),
              "ctrl_t::kEmpty must be -128 to make the SIMD check for its "
              "existence efficient (psignb xmm, xmm)");
static_assert(
    (~static_cast<int8_t>(ctrl_t::kEmpty) &
     ~static_cast<int8_t>(ctrl_t::kDeleted) &
     static_cast<int8_t>(ctrl_t::kSentinel) & 0x7F) != 0,
    "ctrl_t::kEmpty and ctrl_t::kDeleted must share an unset bit that is not "
    "shared by ctrl_t::kSentinel to make the scalar test for "
    "MatchEmptyOrDeleted() efficient");
static_assert(ctrl_t::kDeleted == static_cast<ctrl_t>(-2),
              "ctrl_t::kDeleted must be -2 to make the implementation of "
              "ConvertSpecialToEmptyAndFullToDeleted efficient");

// A single block of empty control bytes for tables without any slots allocated.
// This enables removing a branch in the hot path of find().
ABSL_DLL extern const ctrl_t kEmptyGroup[16];
inline ctrl_t* EmptyGroup() {
  return const_cast<ctrl_t*>(kEmptyGroup);
}

// Mixes a randomly generated per-process seed with `hash` and `ctrl` to
// randomize insertion order within groups.
bool ShouldInsertBackwards(size_t hash, const ctrl_t* ctrl);

// Returns a hash seed.
//
// The seed consists of the ctrl_ pointer, which adds enough entropy to ensure
// non-determinism of iteration order in most cases.
inline size_t HashSeed(const ctrl_t* ctrl) {
  // The low bits of the pointer have little or no entropy because of
  // alignment. We shift the pointer to try to use higher entropy bits. A
  // good number seems to be 12 bits, because that aligns with page size.
  return reinterpret_cast<uintptr_t>(ctrl) >> 12;
}

inline size_t H1(size_t hash, const ctrl_t* ctrl) {
  return (hash >> 7) ^ HashSeed(ctrl);
}
inline h2_t H2(size_t hash) { return hash & 0x7F; }

inline bool IsEmpty(ctrl_t c) { return c == ctrl_t::kEmpty; }
inline bool IsFull(ctrl_t c) { return c >= static_cast<ctrl_t>(0); }
inline bool IsDeleted(ctrl_t c) { return c == ctrl_t::kDeleted; }
inline bool IsEmptyOrDeleted(ctrl_t c) { return c < ctrl_t::kSentinel; }

#if ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSE2

// https://github.com/abseil/abseil-cpp/issues/209
// https://gcc.gnu.org/bugzilla/show_bug.cgi?id=87853
// _mm_cmpgt_epi8 is broken under GCC with -funsigned-char
// Work around this by using the portable implementation of Group
// when using -funsigned-char under GCC.
inline __m128i _mm_cmpgt_epi8_fixed(__m128i a, __m128i b) {
#if defined(__GNUC__) && !defined(__clang__)
  if (std::is_unsigned<char>::value) {
    const __m128i mask = _mm_set1_epi8(0x80);
    const __m128i diff = _mm_subs_epi8(b, a);
    return _mm_cmpeq_epi8(_mm_and_si128(diff, mask), mask);
  }
#endif
  return _mm_cmpgt_epi8(a, b);
}

struct GroupSse2Impl {
  static constexpr size_t kWidth = 16;  // the number of slots per group

  explicit GroupSse2Impl(const ctrl_t* pos) {
    ctrl = _mm_loadu_si128(reinterpret_cast<const __m128i*>(pos));
  }

  // Returns a bitmask representing the positions of slots that match hash.
  BitMask<uint32_t, kWidth> Match(h2_t hash) const {
    auto match = _mm_set1_epi8(hash);
    return BitMask<uint32_t, kWidth>(
        static_cast<uint32_t>(_mm_movemask_epi8(_mm_cmpeq_epi8(match, ctrl))));
  }

  // Returns a bitmask representing the positions of empty slots.
  BitMask<uint32_t, kWidth> MatchEmpty() const {
#if ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSSE3
    // This only works because ctrl_t::kEmpty is -128.
    return BitMask<uint32_t, kWidth>(
        static_cast<uint32_t>(_mm_movemask_epi8(_mm_sign_epi8(ctrl, ctrl))));
#else
    return Match(static_cast<h2_t>(ctrl_t::kEmpty));
#endif
  }

  // Returns a bitmask representing the positions of empty or deleted slots.
  BitMask<uint32_t, kWidth> MatchEmptyOrDeleted() const {
    auto special = _mm_set1_epi8(static_cast<uint8_t>(ctrl_t::kSentinel));
    return BitMask<uint32_t, kWidth>(
        static_cast<uint32_t>(
            _mm_movemask_epi8(_mm_cmpgt_epi8_fixed(special, ctrl))));
  }

  // Returns the number of trailing empty or deleted elements in the group.
  uint32_t CountLeadingEmptyOrDeleted() const {
    auto special = _mm_set1_epi8(static_cast<uint8_t>(ctrl_t::kSentinel));
    return TrailingZeros(static_cast<uint32_t>(
        _mm_movemask_epi8(_mm_cmpgt_epi8_fixed(special, ctrl)) + 1));
  }

  void ConvertSpecialToEmptyAndFullToDeleted(ctrl_t* dst) const {
    auto msbs = _mm_set1_epi8(static_cast<char>(-128));
    auto x126 = _mm_set1_epi8(126);
#if ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSSE3
    auto res = _mm_or_si128(_mm_shuffle_epi8(x126, ctrl), msbs);
#else
    auto zero = _mm_setzero_si128();
    auto special_mask = _mm_cmpgt_epi8_fixed(zero, ctrl);
    auto res = _mm_or_si128(msbs, _mm_andnot_si128(special_mask, x126));
#endif
    _mm_storeu_si128(reinterpret_cast<__m128i*>(dst), res);
  }

  __m128i ctrl;
};
#endif  // ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSE2

struct GroupPortableImpl {
  static constexpr size_t kWidth = 8;

  explicit GroupPortableImpl(const ctrl_t* pos)
      : ctrl(little_endian::Load64(pos)) {}

  BitMask<uint64_t, kWidth, 3> Match(h2_t hash) const {
    // For the technique, see:
    // http://graphics.stanford.edu/~seander/bithacks.html##ValueInWord
    // (Determine if a word has a byte equal to n).
    //
    // Caveat: there are false positives but:
    // - they only occur if there is a real match
    // - they never occur on ctrl_t::kEmpty, ctrl_t::kDeleted, ctrl_t::kSentinel
    // - they will be handled gracefully by subsequent checks in code
    //
    // Example:
    //   v = 0x1716151413121110
    //   hash = 0x12
    //   retval = (v - lsbs) & ~v & msbs = 0x0000000080800000
    constexpr uint64_t msbs = 0x8080808080808080ULL;
    constexpr uint64_t lsbs = 0x0101010101010101ULL;
    auto x = ctrl ^ (lsbs * hash);
    return BitMask<uint64_t, kWidth, 3>((x - lsbs) & ~x & msbs);
  }

  BitMask<uint64_t, kWidth, 3> MatchEmpty() const {
    constexpr uint64_t msbs = 0x8080808080808080ULL;
    return BitMask<uint64_t, kWidth, 3>((ctrl & (~ctrl << 6)) & msbs);
  }

  BitMask<uint64_t, kWidth, 3> MatchEmptyOrDeleted() const {
    constexpr uint64_t msbs = 0x8080808080808080ULL;
    return BitMask<uint64_t, kWidth, 3>((ctrl & (~ctrl << 7)) & msbs);
  }

  uint32_t CountLeadingEmptyOrDeleted() const {
    constexpr uint64_t gaps = 0x00FEFEFEFEFEFEFEULL;
    return (TrailingZeros(((~ctrl & (ctrl >> 7)) | gaps) + 1) + 7) >> 3;
  }

  void ConvertSpecialToEmptyAndFullToDeleted(ctrl_t* dst) const {
    constexpr uint64_t msbs = 0x8080808080808080ULL;
    constexpr uint64_t lsbs = 0x0101010101010101ULL;
    auto x = ctrl & msbs;
    auto res = (~x + (x >> 7)) & ~lsbs;
    little_endian::Store64(dst, res);
  }

  uint64_t ctrl;
};

#if ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSE2
using Group = GroupSse2Impl;
#else
using Group = GroupPortableImpl;
#endif

// The number of cloned control bytes that we copy from the beginning to the
// end of the control bytes array.
constexpr size_t NumClonedBytes() { return Group::kWidth - 1; }

template <class Policy, class Hash, class Eq, class Alloc>
class raw_hash_set;

inline bool IsValidCapacity(size_t n) { return ((n + 1) & n) == 0 && n > 0; }

// PRECONDITION:
//   IsValidCapacity(capacity)
//   ctrl[capacity] == ctrl_t::kSentinel
//   ctrl[i] != ctrl_t::kSentinel for all i < capacity
// Applies mapping for every byte in ctrl:
//   DELETED -> EMPTY
//   EMPTY -> EMPTY
//   FULL -> DELETED
void ConvertDeletedToEmptyAndFullToDeleted(ctrl_t* ctrl, size_t capacity);

// Rounds up the capacity to the next power of 2 minus 1, with a minimum of 1.
inline size_t NormalizeCapacity(size_t n) {
  return n ? ~size_t{} >> countl_zero(n) : 1;
}

// General notes on capacity/growth methods below:
// - We use 7/8th as maximum load factor. For 16-wide groups, that gives an
//   average of two empty slots per group.
// - For (capacity+1) >= Group::kWidth, growth is 7/8*capacity.
// - For (capacity+1) < Group::kWidth, growth == capacity. In this case, we
//   never need to probe (the whole table fits in one group) so we don't need a
//   load factor less than 1.

// Given `capacity` of the table, returns the size (i.e. number of full slots)
// at which we should grow the capacity.
inline size_t CapacityToGrowth(size_t capacity) {
  assert(IsValidCapacity(capacity));
  // `capacity*7/8`
  if (Group::kWidth == 8 && capacity == 7) {
    // x-x/8 does not work when x==7.
    return 6;
  }
  return capacity - capacity / 8;
}
// From desired "growth" to a lowerbound of the necessary capacity.
// Might not be a valid one and requires NormalizeCapacity().
inline size_t GrowthToLowerboundCapacity(size_t growth) {
  // `growth*8/7`
  if (Group::kWidth == 8 && growth == 7) {
    // x+(x-1)/7 does not work when x==7.
    return 8;
  }
  return growth + static_cast<size_t>((static_cast<int64_t>(growth) - 1) / 7);
}

template <class InputIter>
size_t SelectBucketCountForIterRange(InputIter first, InputIter last,
                                     size_t bucket_count) {
  if (bucket_count != 0) {
    return bucket_count;
  }
  using InputIterCategory =
      typename std::iterator_traits<InputIter>::iterator_category;
  if (std::is_base_of<std::random_access_iterator_tag,
                      InputIterCategory>::value) {
    return GrowthToLowerboundCapacity(
        static_cast<size_t>(std::distance(first, last)));
  }
  return 0;
}

inline void AssertIsFull(ctrl_t* ctrl) {
  ABSL_HARDENING_ASSERT(
      (ctrl != nullptr && IsFull(*ctrl)) &&
      "Invalid operation on iterator. The element might have "
      "been erased, the table might have rehashed, or this may "
      "be an end() iterator.");
}

inline void AssertIsValid(ctrl_t* ctrl) {
  ABSL_HARDENING_ASSERT(
      (ctrl == nullptr || IsFull(*ctrl)) &&
      "Invalid operation on iterator. The element might have "
      "been erased, the table might have rehashed, or this may "
      "be an end() iterator.");
}

struct FindInfo {
  size_t offset;
  size_t probe_length;
};

// The representation of the object has two modes:
//  - small: For capacities < kWidth-1
//  - large: For the rest.
//
// Differences:
//  - In small mode we are able to use the whole capacity. The extra control
//  bytes give us at least one "empty" control byte to stop the iteration.
//  This is important to make 1 a valid capacity.
//
//  - In small mode only the first `capacity()` control bytes after the
//  sentinel are valid. The rest contain dummy ctrl_t::kEmpty values that do not
//  represent a real slot. This is important to take into account on
//  find_first_non_full(), where we never try ShouldInsertBackwards() for
//  small tables.
inline bool is_small(size_t capacity) { return capacity < Group::kWidth - 1; }

inline probe_seq<Group::kWidth> probe(const ctrl_t* ctrl, size_t hash,
                                      size_t capacity) {
  return probe_seq<Group::kWidth>(H1(hash, ctrl), capacity);
}

// Probes the raw_hash_set with the probe sequence for hash and returns the
// pointer to the first empty or deleted slot.
// NOTE: this function must work with tables having both ctrl_t::kEmpty and
// ctrl_t::kDeleted in one group. Such tables appears during
// drop_deletes_without_resize.
//
// This function is very useful when insertions happen and:
// - the input is already a set
// - there are enough slots
// - the element with the hash is not in the table
template <typename = void>
inline FindInfo find_first_non_full(const ctrl_t* ctrl, size_t hash,
                                    size_t capacity) {
  auto seq = probe(ctrl, hash, capacity);
  while (true) {
    Group g{ctrl + seq.offset()};
    auto mask = g.MatchEmptyOrDeleted();
    if (mask) {
#if !defined(NDEBUG)
      // We want to add entropy even when ASLR is not enabled.
      // In debug build we will randomly insert in either the front or back of
      // the group.
      // TODO(kfm,sbenza): revisit after we do unconditional mixing
      if (!is_small(capacity) && ShouldInsertBackwards(hash, ctrl)) {
        return {seq.offset(mask.HighestBitSet()), seq.index()};
      }
#endif
      return {seq.offset(mask.LowestBitSet()), seq.index()};
    }
    seq.next();
    assert(seq.index() <= capacity && "full table!");
  }
}

// Extern template for inline function keep possibility of inlining.
// When compiler decided to not inline, no symbols will be added to the
// corresponding translation unit.
extern template FindInfo find_first_non_full(const ctrl_t*, size_t, size_t);

// Reset all ctrl bytes back to ctrl_t::kEmpty, except the sentinel.
inline void ResetCtrl(size_t capacity, ctrl_t* ctrl, const void* slot,
                      size_t slot_size) {
  std::memset(ctrl, static_cast<int8_t>(ctrl_t::kEmpty),
              capacity + 1 + NumClonedBytes());
  ctrl[capacity] = ctrl_t::kSentinel;
  SanitizerPoisonMemoryRegion(slot, slot_size * capacity);
}

// Sets the control byte, and if `i < NumClonedBytes()`, set the cloned byte
// at the end too.
inline void SetCtrl(size_t i, ctrl_t h, size_t capacity, ctrl_t* ctrl,
                    const void* slot, size_t slot_size) {
  assert(i < capacity);

  auto* slot_i = static_cast<const char*>(slot) + i * slot_size;
  if (IsFull(h)) {
    SanitizerUnpoisonMemoryRegion(slot_i, slot_size);
  } else {
    SanitizerPoisonMemoryRegion(slot_i, slot_size);
  }

  ctrl[i] = h;
  ctrl[((i - NumClonedBytes()) & capacity) + (NumClonedBytes() & capacity)] = h;
}

inline void SetCtrl(size_t i, h2_t h, size_t capacity, ctrl_t* ctrl,
                    const void* slot, size_t slot_size) {
  SetCtrl(i, static_cast<ctrl_t>(h), capacity, ctrl, slot, slot_size);
}

// The allocated block consists of `capacity + 1 + NumClonedBytes()` control
// bytes followed by `capacity` slots, which must be aligned to `slot_align`.
// SlotOffset returns the offset of the slots into the allocated block.
inline size_t SlotOffset(size_t capacity, size_t slot_align) {
  assert(IsValidCapacity(capacity));
  const size_t num_control_bytes = capacity + 1 + NumClonedBytes();
  return (num_control_bytes + slot_align - 1) & (~slot_align + 1);
}

// Returns the size of the allocated block. See also above comment.
inline size_t AllocSize(size_t capacity, size_t slot_size, size_t slot_align) {
  return SlotOffset(capacity, slot_align) + capacity * slot_size;
}

// Policy: a policy defines how to perform different operations on
// the slots of the hashtable (see hash_policy_traits.h for the full interface
// of policy).
//
// Hash: a (possibly polymorphic) functor that hashes keys of the hashtable. The
// functor should accept a key and return size_t as hash. For best performance
// it is important that the hash function provides high entropy across all bits
// of the hash.
//
// Eq: a (possibly polymorphic) functor that compares two keys for equality. It
// should accept two (of possibly different type) keys and return a bool: true
// if they are equal, false if they are not. If two keys compare equal, then
// their hash values as defined by Hash MUST be equal.
//
// Allocator: an Allocator
// [https://en.cppreference.com/w/cpp/named_req/Allocator] with which
// the storage of the hashtable will be allocated and the elements will be
// constructed and destroyed.
template <class Policy, class Hash, class Eq, class Alloc>
class raw_hash_set {
  using PolicyTraits = hash_policy_traits<Policy>;
  using KeyArgImpl =
      KeyArg<IsTransparent<Eq>::value && IsTransparent<Hash>::value>;

 public:
  using init_type = typename PolicyTraits::init_type;
  using key_type = typename PolicyTraits::key_type;
  // TODO(sbenza): Hide slot_type as it is an implementation detail. Needs user
  // code fixes!
  using slot_type = typename PolicyTraits::slot_type;
  using allocator_type = Alloc;
  using size_type = size_t;
  using difference_type = ptrdiff_t;
  using hasher = Hash;
  using key_equal = Eq;
  using policy_type = Policy;
  using value_type = typename PolicyTraits::value_type;
  using reference = value_type&;
  using const_reference = const value_type&;
  using pointer = typename absl::allocator_traits<
      allocator_type>::template rebind_traits<value_type>::pointer;
  using const_pointer = typename absl::allocator_traits<
      allocator_type>::template rebind_traits<value_type>::const_pointer;

  // Alias used for heterogeneous lookup functions.
  // `key_arg<K>` evaluates to `K` when the functors are transparent and to
  // `key_type` otherwise. It permits template argument deduction on `K` for the
  // transparent case.
  template <class K>
  using key_arg = typename KeyArgImpl::template type<K, key_type>;

 private:
  // Give an early error when key_type is not hashable/eq.
  auto KeyTypeCanBeHashed(const Hash& h, const key_type& k) -> decltype(h(k));
  auto KeyTypeCanBeEq(const Eq& eq, const key_type& k) -> decltype(eq(k, k));

  using AllocTraits = absl::allocator_traits<allocator_type>;
  using SlotAlloc = typename absl::allocator_traits<
      allocator_type>::template rebind_alloc<slot_type>;
  using SlotAllocTraits = typename absl::allocator_traits<
      allocator_type>::template rebind_traits<slot_type>;

  static_assert(std::is_lvalue_reference<reference>::value,
                "Policy::element() must return a reference");

  template <typename T>
  struct SameAsElementReference
      : std::is_same<typename std::remove_cv<
                         typename std::remove_reference<reference>::type>::type,
                     typename std::remove_cv<
                         typename std::remove_reference<T>::type>::type> {};

  // An enabler for insert(T&&): T must be convertible to init_type or be the
  // same as [cv] value_type [ref].
  // Note: we separate SameAsElementReference into its own type to avoid using
  // reference unless we need to. MSVC doesn't seem to like it in some
  // cases.
  template <class T>
  using RequiresInsertable = typename std::enable_if<
      absl::disjunction<std::is_convertible<T, init_type>,
                        SameAsElementReference<T>>::value,
      int>::type;

  // RequiresNotInit is a workaround for gcc prior to 7.1.
  // See https://godbolt.org/g/Y4xsUh.
  template <class T>
  using RequiresNotInit =
      typename std::enable_if<!std::is_same<T, init_type>::value, int>::type;

  template <class... Ts>
  using IsDecomposable = IsDecomposable<void, PolicyTraits, Hash, Eq, Ts...>;

 public:
  static_assert(std::is_same<pointer, value_type*>::value,
                "Allocators with custom pointer types are not supported");
  static_assert(std::is_same<const_pointer, const value_type*>::value,
                "Allocators with custom pointer types are not supported");

  class iterator {
    friend class raw_hash_set;

   public:
    using iterator_category = std::forward_iterator_tag;
    using value_type = typename raw_hash_set::value_type;
    using reference =
        absl::conditional_t<PolicyTraits::constant_iterators::value,
                            const value_type&, value_type&>;
    using pointer = absl::remove_reference_t<reference>*;
    using difference_type = typename raw_hash_set::difference_type;

    iterator() {}

    // PRECONDITION: not an end() iterator.
    reference operator*() const {
      AssertIsFull(ctrl_);
      return PolicyTraits::element(slot_);
    }

    // PRECONDITION: not an end() iterator.
    pointer operator->() const { return &operator*(); }

    // PRECONDITION: not an end() iterator.
    iterator& operator++() {
      AssertIsFull(ctrl_);
      ++ctrl_;
      ++slot_;
      skip_empty_or_deleted();
      return *this;
    }
    // PRECONDITION: not an end() iterator.
    iterator operator++(int) {
      auto tmp = *this;
      ++*this;
      return tmp;
    }

    friend bool operator==(const iterator& a, const iterator& b) {
      AssertIsValid(a.ctrl_);
      AssertIsValid(b.ctrl_);
      return a.ctrl_ == b.ctrl_;
    }
    friend bool operator!=(const iterator& a, const iterator& b) {
      return !(a == b);
    }

   private:
    iterator(ctrl_t* ctrl, slot_type* slot) : ctrl_(ctrl), slot_(slot) {
      // This assumption helps the compiler know that any non-end iterator is
      // not equal to any end iterator.
      ABSL_INTERNAL_ASSUME(ctrl != nullptr);
    }

    void skip_empty_or_deleted() {
      while (IsEmptyOrDeleted(*ctrl_)) {
        uint32_t shift = Group{ctrl_}.CountLeadingEmptyOrDeleted();
        ctrl_ += shift;
        slot_ += shift;
      }
      if (ABSL_PREDICT_FALSE(*ctrl_ == ctrl_t::kSentinel)) ctrl_ = nullptr;
    }

    ctrl_t* ctrl_ = nullptr;
    // To avoid uninitialized member warnings, put slot_ in an anonymous union.
    // The member is not initialized on singleton and end iterators.
    union {
      slot_type* slot_;
    };
  };

  class const_iterator {
    friend class raw_hash_set;

   public:
    using iterator_category = typename iterator::iterator_category;
    using value_type = typename raw_hash_set::value_type;
    using reference = typename raw_hash_set::const_reference;
    using pointer = typename raw_hash_set::const_pointer;
    using difference_type = typename raw_hash_set::difference_type;

    const_iterator() {}
    // Implicit construction from iterator.
    const_iterator(iterator i) : inner_(std::move(i)) {}

    reference operator*() const { return *inner_; }
    pointer operator->() const { return inner_.operator->(); }

    const_iterator& operator++() {
      ++inner_;
      return *this;
    }
    const_iterator operator++(int) { return inner_++; }

    friend bool operator==(const const_iterator& a, const const_iterator& b) {
      return a.inner_ == b.inner_;
    }
    friend bool operator!=(const const_iterator& a, const const_iterator& b) {
      return !(a == b);
    }

   private:
    const_iterator(const ctrl_t* ctrl, const slot_type* slot)
        : inner_(const_cast<ctrl_t*>(ctrl), const_cast<slot_type*>(slot)) {}

    iterator inner_;
  };

  using node_type = node_handle<Policy, hash_policy_traits<Policy>, Alloc>;
  using insert_return_type = InsertReturnType<iterator, node_type>;

  raw_hash_set() noexcept(
      std::is_nothrow_default_constructible<hasher>::value&&
          std::is_nothrow_default_constructible<key_equal>::value&&
              std::is_nothrow_default_constructible<allocator_type>::value) {}

  explicit raw_hash_set(size_t bucket_count, const hasher& hash = hasher(),
                        const key_equal& eq = key_equal(),
                        const allocator_type& alloc = allocator_type())
      : ctrl_(EmptyGroup()),
        settings_(0, HashtablezInfoHandle(), hash, eq, alloc) {
    if (bucket_count) {
      capacity_ = NormalizeCapacity(bucket_count);
      initialize_slots();
    }
  }

  raw_hash_set(size_t bucket_count, const hasher& hash,
               const allocator_type& alloc)
      : raw_hash_set(bucket_count, hash, key_equal(), alloc) {}

  raw_hash_set(size_t bucket_count, const allocator_type& alloc)
      : raw_hash_set(bucket_count, hasher(), key_equal(), alloc) {}

  explicit raw_hash_set(const allocator_type& alloc)
      : raw_hash_set(0, hasher(), key_equal(), alloc) {}

  template <class InputIter>
  raw_hash_set(InputIter first, InputIter last, size_t bucket_count = 0,
               const hasher& hash = hasher(), const key_equal& eq = key_equal(),
               const allocator_type& alloc = allocator_type())
      : raw_hash_set(SelectBucketCountForIterRange(first, last, bucket_count),
                     hash, eq, alloc) {
    insert(first, last);
  }

  template <class InputIter>
  raw_hash_set(InputIter first, InputIter last, size_t bucket_count,
               const hasher& hash, const allocator_type& alloc)
      : raw_hash_set(first, last, bucket_count, hash, key_equal(), alloc) {}

  template <class InputIter>
  raw_hash_set(InputIter first, InputIter last, size_t bucket_count,
               const allocator_type& alloc)
      : raw_hash_set(first, last, bucket_count, hasher(), key_equal(), alloc) {}

  template <class InputIter>
  raw_hash_set(InputIter first, InputIter last, const allocator_type& alloc)
      : raw_hash_set(first, last, 0, hasher(), key_equal(), alloc) {}

  // Instead of accepting std::initializer_list<value_type> as the first
  // argument like std::unordered_set<value_type> does, we have two overloads
  // that accept std::initializer_list<T> and std::initializer_list<init_type>.
  // This is advantageous for performance.
  //
  //   // Turns {"abc", "def"} into std::initializer_list<std::string>, then
  //   // copies the strings into the set.
  //   std::unordered_set<std::string> s = {"abc", "def"};
  //
  //   // Turns {"abc", "def"} into std::initializer_list<const char*>, then
  //   // copies the strings into the set.
  //   absl::flat_hash_set<std::string> s = {"abc", "def"};
  //
  // The same trick is used in insert().
  //
  // The enabler is necessary to prevent this constructor from triggering where
  // the copy constructor is meant to be called.
  //
  //   absl::flat_hash_set<int> a, b{a};
  //
  // RequiresNotInit<T> is a workaround for gcc prior to 7.1.
  template <class T, RequiresNotInit<T> = 0, RequiresInsertable<T> = 0>
  raw_hash_set(std::initializer_list<T> init, size_t bucket_count = 0,
               const hasher& hash = hasher(), const key_equal& eq = key_equal(),
               const allocator_type& alloc = allocator_type())
      : raw_hash_set(init.begin(), init.end(), bucket_count, hash, eq, alloc) {}

  raw_hash_set(std::initializer_list<init_type> init, size_t bucket_count = 0,
               const hasher& hash = hasher(), const key_equal& eq = key_equal(),
               const allocator_type& alloc = allocator_type())
      : raw_hash_set(init.begin(), init.end(), bucket_count, hash, eq, alloc) {}

  template <class T, RequiresNotInit<T> = 0, RequiresInsertable<T> = 0>
  raw_hash_set(std::initializer_list<T> init, size_t bucket_count,
               const hasher& hash, const allocator_type& alloc)
      : raw_hash_set(init, bucket_count, hash, key_equal(), alloc) {}

  raw_hash_set(std::initializer_list<init_type> init, size_t bucket_count,
               const hasher& hash, const allocator_type& alloc)
      : raw_hash_set(init, bucket_count, hash, key_equal(), alloc) {}

  template <class T, RequiresNotInit<T> = 0, RequiresInsertable<T> = 0>
  raw_hash_set(std::initializer_list<T> init, size_t bucket_count,
               const allocator_type& alloc)
      : raw_hash_set(init, bucket_count, hasher(), key_equal(), alloc) {}

  raw_hash_set(std::initializer_list<init_type> init, size_t bucket_count,
               const allocator_type& alloc)
      : raw_hash_set(init, bucket_count, hasher(), key_equal(), alloc) {}

  template <class T, RequiresNotInit<T> = 0, RequiresInsertable<T> = 0>
  raw_hash_set(std::initializer_list<T> init, const allocator_type& alloc)
      : raw_hash_set(init, 0, hasher(), key_equal(), alloc) {}

  raw_hash_set(std::initializer_list<init_type> init,
               const allocator_type& alloc)
      : raw_hash_set(init, 0, hasher(), key_equal(), alloc) {}

  raw_hash_set(const raw_hash_set& that)
      : raw_hash_set(that, AllocTraits::select_on_container_copy_construction(
                               that.alloc_ref())) {}

  raw_hash_set(const raw_hash_set& that, const allocator_type& a)
      : raw_hash_set(0, that.hash_ref(), that.eq_ref(), a) {
    reserve(that.size());
    // Because the table is guaranteed to be empty, we can do something faster
    // than a full `insert`.
    for (const auto& v : that) {
      const size_t hash = PolicyTraits::apply(HashElement{hash_ref()}, v);
      auto target = find_first_non_full(ctrl_, hash, capacity_);
      SetCtrl(target.offset, H2(hash), capacity_, ctrl_, slots_,
              sizeof(slot_type));
      emplace_at(target.offset, v);
      infoz().RecordInsert(hash, target.probe_length);
    }
    size_ = that.size();
    growth_left() -= that.size();
  }

  raw_hash_set(raw_hash_set&& that) noexcept(
      std::is_nothrow_copy_constructible<hasher>::value&&
          std::is_nothrow_copy_constructible<key_equal>::value&&
              std::is_nothrow_copy_constructible<allocator_type>::value)
      : ctrl_(absl::exchange(that.ctrl_, EmptyGroup())),
        slots_(absl::exchange(that.slots_, nullptr)),
        size_(absl::exchange(that.size_, 0)),
        capacity_(absl::exchange(that.capacity_, 0)),
        // Hash, equality and allocator are copied instead of moved because
        // `that` must be left valid. If Hash is std::function<Key>, moving it
        // would create a nullptr functor that cannot be called.
        settings_(absl::exchange(that.growth_left(), 0),
                  absl::exchange(that.infoz(), HashtablezInfoHandle()),
                  that.hash_ref(), that.eq_ref(), that.alloc_ref()) {}

  raw_hash_set(raw_hash_set&& that, const allocator_type& a)
      : ctrl_(EmptyGroup()),
        slots_(nullptr),
        size_(0),
        capacity_(0),
        settings_(0, HashtablezInfoHandle(), that.hash_ref(), that.eq_ref(),
                  a) {
    if (a == that.alloc_ref()) {
      std::swap(ctrl_, that.ctrl_);
      std::swap(slots_, that.slots_);
      std::swap(size_, that.size_);
      std::swap(capacity_, that.capacity_);
      std::swap(growth_left(), that.growth_left());
      std::swap(infoz(), that.infoz());
    } else {
      reserve(that.size());
      // Note: this will copy elements of dense_set and unordered_set instead of
      // moving them. This can be fixed if it ever becomes an issue.
      for (auto& elem : that) insert(std::move(elem));
    }
  }

  raw_hash_set& operator=(const raw_hash_set& that) {
    raw_hash_set tmp(that,
                     AllocTraits::propagate_on_container_copy_assignment::value
                         ? that.alloc_ref()
                         : alloc_ref());
    swap(tmp);
    return *this;
  }

  raw_hash_set& operator=(raw_hash_set&& that) noexcept(
      absl::allocator_traits<allocator_type>::is_always_equal::value&&
          std::is_nothrow_move_assignable<hasher>::value&&
              std::is_nothrow_move_assignable<key_equal>::value) {
    // TODO(sbenza): We should only use the operations from the noexcept clause
    // to make sure we actually adhere to that contract.
    return move_assign(
        std::move(that),
        typename AllocTraits::propagate_on_container_move_assignment());
  }

  ~raw_hash_set() { destroy_slots(); }

  iterator begin() {
    auto it = iterator_at(0);
    it.skip_empty_or_deleted();
    return it;
  }
  iterator end() { return {}; }

  const_iterator begin() const {
    return const_cast<raw_hash_set*>(this)->begin();
  }
  const_iterator end() const { return {}; }
  const_iterator cbegin() const { return begin(); }
  const_iterator cend() const { return end(); }

  bool empty() const { return !size(); }
  size_t size() const { return size_; }
  size_t capacity() const { return capacity_; }
  size_t max_size() const { return (std::numeric_limits<size_t>::max)(); }

  ABSL_ATTRIBUTE_REINITIALIZES void clear() {
    // Iterating over this container is O(bucket_count()). When bucket_count()
    // is much greater than size(), iteration becomes prohibitively expensive.
    // For clear() it is more important to reuse the allocated array when the
    // container is small because allocation takes comparatively long time
    // compared to destruction of the elements of the container. So we pick the
    // largest bucket_count() threshold for which iteration is still fast and
    // past that we simply deallocate the array.
    if (capacity_ > 127) {
      destroy_slots();

      infoz().RecordClearedReservation();
    } else if (capacity_) {
      for (size_t i = 0; i != capacity_; ++i) {
        if (IsFull(ctrl_[i])) {
          PolicyTraits::destroy(&alloc_ref(), slots_ + i);
        }
      }
      size_ = 0;
      ResetCtrl(capacity_, ctrl_, slots_, sizeof(slot_type));
      reset_growth_left();
    }
    assert(empty());
    infoz().RecordStorageChanged(0, capacity_);
  }

  // This overload kicks in when the argument is an rvalue of insertable and
  // decomposable type other than init_type.
  //
  //   flat_hash_map<std::string, int> m;
  //   m.insert(std::make_pair("abc", 42));
  // TODO(cheshire): A type alias T2 is introduced as a workaround for the nvcc
  // bug.
  template <class T, RequiresInsertable<T> = 0,
            class T2 = T,
            typename std::enable_if<IsDecomposable<T2>::value, int>::type = 0,
            T* = nullptr>
  std::pair<iterator, bool> insert(T&& value) {
    return emplace(std::forward<T>(value));
  }

  // This overload kicks in when the argument is a bitfield or an lvalue of
  // insertable and decomposable type.
  //
  //   union { int n : 1; };
  //   flat_hash_set<int> s;
  //   s.insert(n);
  //
  //   flat_hash_set<std::string> s;
  //   const char* p = "hello";
  //   s.insert(p);
  //
  // TODO(romanp): Once we stop supporting gcc 5.1 and below, replace
  // RequiresInsertable<T> with RequiresInsertable<const T&>.
  // We are hitting this bug: https://godbolt.org/g/1Vht4f.
  template <
      class T, RequiresInsertable<T> = 0,
      typename std::enable_if<IsDecomposable<const T&>::value, int>::type = 0>
  std::pair<iterator, bool> insert(const T& value) {
    return emplace(value);
  }

  // This overload kicks in when the argument is an rvalue of init_type. Its
  // purpose is to handle brace-init-list arguments.
  //
  //   flat_hash_map<std::string, int> s;
  //   s.insert({"abc", 42});
  std::pair<iterator, bool> insert(init_type&& value) {
    return emplace(std::move(value));
  }

  // TODO(cheshire): A type alias T2 is introduced as a workaround for the nvcc
  // bug.
  template <class T, RequiresInsertable<T> = 0, class T2 = T,
            typename std::enable_if<IsDecomposable<T2>::value, int>::type = 0,
            T* = nullptr>
  iterator insert(const_iterator, T&& value) {
    return insert(std::forward<T>(value)).first;
  }

  // TODO(romanp): Once we stop supporting gcc 5.1 and below, replace
  // RequiresInsertable<T> with RequiresInsertable<const T&>.
  // We are hitting this bug: https://godbolt.org/g/1Vht4f.
  template <
      class T, RequiresInsertable<T> = 0,
      typename std::enable_if<IsDecomposable<const T&>::value, int>::type = 0>
  iterator insert(const_iterator, const T& value) {
    return insert(value).first;
  }

  iterator insert(const_iterator, init_type&& value) {
    return insert(std::move(value)).first;
  }

  template <class InputIt>
  void insert(InputIt first, InputIt last) {
    for (; first != last; ++first) emplace(*first);
  }

  template <class T, RequiresNotInit<T> = 0, RequiresInsertable<const T&> = 0>
  void insert(std::initializer_list<T> ilist) {
    insert(ilist.begin(), ilist.end());
  }

  void insert(std::initializer_list<init_type> ilist) {
    insert(ilist.begin(), ilist.end());
  }

  insert_return_type insert(node_type&& node) {
    if (!node) return {end(), false, node_type()};
    const auto& elem = PolicyTraits::element(CommonAccess::GetSlot(node));
    auto res = PolicyTraits::apply(
        InsertSlot<false>{*this, std::move(*CommonAccess::GetSlot(node))},
        elem);
    if (res.second) {
      CommonAccess::Reset(&node);
      return {res.first, true, node_type()};
    } else {
      return {res.first, false, std::move(node)};
    }
  }

  iterator insert(const_iterator, node_type&& node) {
    auto res = insert(std::move(node));
    node = std::move(res.node);
    return res.position;
  }

  // This overload kicks in if we can deduce the key from args. This enables us
  // to avoid constructing value_type if an entry with the same key already
  // exists.
  //
  // For example:
  //
  //   flat_hash_map<std::string, std::string> m = {{"abc", "def"}};
  //   // Creates no std::string copies and makes no heap allocations.
  //   m.emplace("abc", "xyz");
  template <class... Args, typename std::enable_if<
                               IsDecomposable<Args...>::value, int>::type = 0>
  std::pair<iterator, bool> emplace(Args&&... args) {
    return PolicyTraits::apply(EmplaceDecomposable{*this},
                               std::forward<Args>(args)...);
  }

  // This overload kicks in if we cannot deduce the key from args. It constructs
  // value_type unconditionally and then either moves it into the table or
  // destroys.
  template <class... Args, typename std::enable_if<
                               !IsDecomposable<Args...>::value, int>::type = 0>
  std::pair<iterator, bool> emplace(Args&&... args) {
    alignas(slot_type) unsigned char raw[sizeof(slot_type)];
    slot_type* slot = reinterpret_cast<slot_type*>(&raw);

    PolicyTraits::construct(&alloc_ref(), slot, std::forward<Args>(args)...);
    const auto& elem = PolicyTraits::element(slot);
    return PolicyTraits::apply(InsertSlot<true>{*this, std::move(*slot)}, elem);
  }

  template <class... Args>
  iterator emplace_hint(const_iterator, Args&&... args) {
    return emplace(std::forward<Args>(args)...).first;
  }

  // Extension API: support for lazy emplace.
  //
  // Looks up key in the table. If found, returns the iterator to the element.
  // Otherwise calls `f` with one argument of type `raw_hash_set::constructor`.
  //
  // `f` must abide by several restrictions:
  //  - it MUST call `raw_hash_set::constructor` with arguments as if a
  //    `raw_hash_set::value_type` is constructed,
  //  - it MUST NOT access the container before the call to
  //    `raw_hash_set::constructor`, and
  //  - it MUST NOT erase the lazily emplaced element.
  // Doing any of these is undefined behavior.
  //
  // For example:
  //
  //   std::unordered_set<ArenaString> s;
  //   // Makes ArenaStr even if "abc" is in the map.
  //   s.insert(ArenaString(&arena, "abc"));
  //
  //   flat_hash_set<ArenaStr> s;
  //   // Makes ArenaStr only if "abc" is not in the map.
  //   s.lazy_emplace("abc", [&](const constructor& ctor) {
  //     ctor(&arena, "abc");
  //   });
  //
  // WARNING: This API is currently experimental. If there is a way to implement
  // the same thing with the rest of the API, prefer that.
  class constructor {
    friend class raw_hash_set;

   public:
    template <class... Args>
    void operator()(Args&&... args) const {
      assert(*slot_);
      PolicyTraits::construct(alloc_, *slot_, std::forward<Args>(args)...);
      *slot_ = nullptr;
    }

   private:
    constructor(allocator_type* a, slot_type** slot) : alloc_(a), slot_(slot) {}

    allocator_type* alloc_;
    slot_type** slot_;
  };

  template <class K = key_type, class F>
  iterator lazy_emplace(const key_arg<K>& key, F&& f) {
    auto res = find_or_prepare_insert(key);
    if (res.second) {
      slot_type* slot = slots_ + res.first;
      std::forward<F>(f)(constructor(&alloc_ref(), &slot));
      assert(!slot);
    }
    return iterator_at(res.first);
  }

  // Extension API: support for heterogeneous keys.
  //
  //   std::unordered_set<std::string> s;
  //   // Turns "abc" into std::string.
  //   s.erase("abc");
  //
  //   flat_hash_set<std::string> s;
  //   // Uses "abc" directly without copying it into std::string.
  //   s.erase("abc");
  template <class K = key_type>
  size_type erase(const key_arg<K>& key) {
    auto it = find(key);
    if (it == end()) return 0;
    erase(it);
    return 1;
  }

  // Erases the element pointed to by `it`.  Unlike `std::unordered_set::erase`,
  // this method returns void to reduce algorithmic complexity to O(1).  The
  // iterator is invalidated, so any increment should be done before calling
  // erase.  In order to erase while iterating across a map, use the following
  // idiom (which also works for standard containers):
  //
  // for (auto it = m.begin(), end = m.end(); it != end;) {
  //   // `erase()` will invalidate `it`, so advance `it` first.
  //   auto copy_it = it++;
  //   if (<pred>) {
  //     m.erase(copy_it);
  //   }
  // }
  void erase(const_iterator cit) { erase(cit.inner_); }

  // This overload is necessary because otherwise erase<K>(const K&) would be
  // a better match if non-const iterator is passed as an argument.
  void erase(iterator it) {
    AssertIsFull(it.ctrl_);
    PolicyTraits::destroy(&alloc_ref(), it.slot_);
    erase_meta_only(it);
  }

  iterator erase(const_iterator first, const_iterator last) {
    while (first != last) {
      erase(first++);
    }
    return last.inner_;
  }

  // Moves elements from `src` into `this`.
  // If the element already exists in `this`, it is left unmodified in `src`.
  template <typename H, typename E>
  void merge(raw_hash_set<Policy, H, E, Alloc>& src) {  // NOLINT
    assert(this != &src);
    for (auto it = src.begin(), e = src.end(); it != e;) {
      auto next = std::next(it);
      if (PolicyTraits::apply(InsertSlot<false>{*this, std::move(*it.slot_)},
                              PolicyTraits::element(it.slot_))
              .second) {
        src.erase_meta_only(it);
      }
      it = next;
    }
  }

  template <typename H, typename E>
  void merge(raw_hash_set<Policy, H, E, Alloc>&& src) {
    merge(src);
  }

  node_type extract(const_iterator position) {
    AssertIsFull(position.inner_.ctrl_);
    auto node =
        CommonAccess::Transfer<node_type>(alloc_ref(), position.inner_.slot_);
    erase_meta_only(position);
    return node;
  }

  template <
      class K = key_type,
      typename std::enable_if<!std::is_same<K, iterator>::value, int>::type = 0>
  node_type extract(const key_arg<K>& key) {
    auto it = find(key);
    return it == end() ? node_type() : extract(const_iterator{it});
  }

  void swap(raw_hash_set& that) noexcept(
      IsNoThrowSwappable<hasher>() && IsNoThrowSwappable<key_equal>() &&
      IsNoThrowSwappable<allocator_type>(
          typename AllocTraits::propagate_on_container_swap{})) {
    using std::swap;
    swap(ctrl_, that.ctrl_);
    swap(slots_, that.slots_);
    swap(size_, that.size_);
    swap(capacity_, that.capacity_);
    swap(growth_left(), that.growth_left());
    swap(hash_ref(), that.hash_ref());
    swap(eq_ref(), that.eq_ref());
    swap(infoz(), that.infoz());
    SwapAlloc(alloc_ref(), that.alloc_ref(),
              typename AllocTraits::propagate_on_container_swap{});
  }

  void rehash(size_t n) {
    if (n == 0 && capacity_ == 0) return;
    if (n == 0 && size_ == 0) {
      destroy_slots();
      infoz().RecordStorageChanged(0, 0);
      infoz().RecordClearedReservation();
      return;
    }

    // bitor is a faster way of doing `max` here. We will round up to the next
    // power-of-2-minus-1, so bitor is good enough.
    auto m = NormalizeCapacity(n | GrowthToLowerboundCapacity(size()));
    // n == 0 unconditionally rehashes as per the standard.
    if (n == 0 || m > capacity_) {
      resize(m);

      // This is after resize, to ensure that we have completed the allocation
      // and have potentially sampled the hashtable.
      infoz().RecordReservation(n);
    }
  }

  void reserve(size_t n) {
    if (n > size() + growth_left()) {
      size_t m = GrowthToLowerboundCapacity(n);
      resize(NormalizeCapacity(m));

      // This is after resize, to ensure that we have completed the allocation
      // and have potentially sampled the hashtable.
      infoz().RecordReservation(n);
    }
  }

  // Extension API: support for heterogeneous keys.
  //
  //   std::unordered_set<std::string> s;
  //   // Turns "abc" into std::string.
  //   s.count("abc");
  //
  //   ch_set<std::string> s;
  //   // Uses "abc" directly without copying it into std::string.
  //   s.count("abc");
  template <class K = key_type>
  size_t count(const key_arg<K>& key) const {
    return find(key) == end() ? 0 : 1;
  }

  // Issues CPU prefetch instructions for the memory needed to find or insert
  // a key.  Like all lookup functions, this support heterogeneous keys.
  //
  // NOTE: This is a very low level operation and should not be used without
  // specific benchmarks indicating its importance.
  template <class K = key_type>
  void prefetch(const key_arg<K>& key) const {
    (void)key;
#if defined(__GNUC__)
    prefetch_heap_block();
    auto seq = probe(ctrl_, hash_ref()(key), capacity_);
    __builtin_prefetch(static_cast<const void*>(ctrl_ + seq.offset()));
    __builtin_prefetch(static_cast<const void*>(slots_ + seq.offset()));
#endif  // __GNUC__
  }

  // The API of find() has two extensions.
  //
  // 1. The hash can be passed by the user. It must be equal to the hash of the
  // key.
  //
  // 2. The type of the key argument doesn't have to be key_type. This is so
  // called heterogeneous key support.
  template <class K = key_type>
  iterator find(const key_arg<K>& key, size_t hash) {
    auto seq = probe(ctrl_, hash, capacity_);
    while (true) {
      Group g{ctrl_ + seq.offset()};
      for (uint32_t i : g.Match(H2(hash))) {
        if (ABSL_PREDICT_TRUE(PolicyTraits::apply(
                EqualElement<K>{key, eq_ref()},
                PolicyTraits::element(slots_ + seq.offset(i)))))
          return iterator_at(seq.offset(i));
      }
      if (ABSL_PREDICT_TRUE(g.MatchEmpty())) return end();
      seq.next();
      assert(seq.index() <= capacity_ && "full table!");
    }
  }
  template <class K = key_type>
  iterator find(const key_arg<K>& key) {
    prefetch_heap_block();
    return find(key, hash_ref()(key));
  }

  template <class K = key_type>
  const_iterator find(const key_arg<K>& key, size_t hash) const {
    return const_cast<raw_hash_set*>(this)->find(key, hash);
  }
  template <class K = key_type>
  const_iterator find(const key_arg<K>& key) const {
    prefetch_heap_block();
    return find(key, hash_ref()(key));
  }

  template <class K = key_type>
  bool contains(const key_arg<K>& key) const {
    return find(key) != end();
  }

  template <class K = key_type>
  std::pair<iterator, iterator> equal_range(const key_arg<K>& key) {
    auto it = find(key);
    if (it != end()) return {it, std::next(it)};
    return {it, it};
  }
  template <class K = key_type>
  std::pair<const_iterator, const_iterator> equal_range(
      const key_arg<K>& key) const {
    auto it = find(key);
    if (it != end()) return {it, std::next(it)};
    return {it, it};
  }

  size_t bucket_count() const { return capacity_; }
  float load_factor() const {
    return capacity_ ? static_cast<double>(size()) / capacity_ : 0.0;
  }
  float max_load_factor() const { return 1.0f; }
  void max_load_factor(float) {
    // Does nothing.
  }

  hasher hash_function() const { return hash_ref(); }
  key_equal key_eq() const { return eq_ref(); }
  allocator_type get_allocator() const { return alloc_ref(); }

  friend bool operator==(const raw_hash_set& a, const raw_hash_set& b) {
    if (a.size() != b.size()) return false;
    const raw_hash_set* outer = &a;
    const raw_hash_set* inner = &b;
    if (outer->capacity() > inner->capacity()) std::swap(outer, inner);
    for (const value_type& elem : *outer)
      if (!inner->has_element(elem)) return false;
    return true;
  }

  friend bool operator!=(const raw_hash_set& a, const raw_hash_set& b) {
    return !(a == b);
  }

  template <typename H>
  friend typename std::enable_if<H::template is_hashable<value_type>::value,
                                 H>::type
  AbslHashValue(H h, const raw_hash_set& s) {
    return H::combine(H::combine_unordered(std::move(h), s.begin(), s.end()),
                      s.size());
  }

  friend void swap(raw_hash_set& a,
                   raw_hash_set& b) noexcept(noexcept(a.swap(b))) {
    a.swap(b);
  }

 private:
  template <class Container, typename Enabler>
  friend struct absl::container_internal::hashtable_debug_internal::
      HashtableDebugAccess;

  struct FindElement {
    template <class K, class... Args>
    const_iterator operator()(const K& key, Args&&...) const {
      return s.find(key);
    }
    const raw_hash_set& s;
  };

  struct HashElement {
    template <class K, class... Args>
    size_t operator()(const K& key, Args&&...) const {
      return h(key);
    }
    const hasher& h;
  };

  template <class K1>
  struct EqualElement {
    template <class K2, class... Args>
    bool operator()(const K2& lhs, Args&&...) const {
      return eq(lhs, rhs);
    }
    const K1& rhs;
    const key_equal& eq;
  };

  struct EmplaceDecomposable {
    template <class K, class... Args>
    std::pair<iterator, bool> operator()(const K& key, Args&&... args) const {
      auto res = s.find_or_prepare_insert(key);
      if (res.second) {
        s.emplace_at(res.first, std::forward<Args>(args)...);
      }
      return {s.iterator_at(res.first), res.second};
    }
    raw_hash_set& s;
  };

  template <bool do_destroy>
  struct InsertSlot {
    template <class K, class... Args>
    std::pair<iterator, bool> operator()(const K& key, Args&&...) && {
      auto res = s.find_or_prepare_insert(key);
      if (res.second) {
        PolicyTraits::transfer(&s.alloc_ref(), s.slots_ + res.first, &slot);
      } else if (do_destroy) {
        PolicyTraits::destroy(&s.alloc_ref(), &slot);
      }
      return {s.iterator_at(res.first), res.second};
    }
    raw_hash_set& s;
    // Constructed slot. Either moved into place or destroyed.
    slot_type&& slot;
  };

  // "erases" the object from the container, except that it doesn't actually
  // destroy the object. It only updates all the metadata of the class.
  // This can be used in conjunction with Policy::transfer to move the object to
  // another place.
  void erase_meta_only(const_iterator it) {
    assert(IsFull(*it.inner_.ctrl_) && "erasing a dangling iterator");
    --size_;
    const size_t index = static_cast<size_t>(it.inner_.ctrl_ - ctrl_);
    const size_t index_before = (index - Group::kWidth) & capacity_;
    const auto empty_after = Group(it.inner_.ctrl_).MatchEmpty();
    const auto empty_before = Group(ctrl_ + index_before).MatchEmpty();

    // We count how many consecutive non empties we have to the right and to the
    // left of `it`. If the sum is >= kWidth then there is at least one probe
    // window that might have seen a full group.
    bool was_never_full =
        empty_before && empty_after &&
        static_cast<size_t>(empty_after.TrailingZeros() +
                            empty_before.LeadingZeros()) < Group::kWidth;

    SetCtrl(index, was_never_full ? ctrl_t::kEmpty : ctrl_t::kDeleted,
            capacity_, ctrl_, slots_, sizeof(slot_type));
    growth_left() += was_never_full;
    infoz().RecordErase();
  }

  void initialize_slots() {
    assert(capacity_);
    // Folks with custom allocators often make unwarranted assumptions about the
    // behavior of their classes vis-a-vis trivial destructability and what
    // calls they will or wont make.  Avoid sampling for people with custom
    // allocators to get us out of this mess.  This is not a hard guarantee but
    // a workaround while we plan the exact guarantee we want to provide.
    //
    // People are often sloppy with the exact type of their allocator (sometimes
    // it has an extra const or is missing the pair, but rebinds made it work
    // anyway).  To avoid the ambiguity, we work off SlotAlloc which we have
    // bound more carefully.
    if (std::is_same<SlotAlloc, std::allocator<slot_type>>::value &&
        slots_ == nullptr) {
      infoz() = Sample(sizeof(slot_type));
    }

    char* mem = static_cast<char*>(Allocate<alignof(slot_type)>(
        &alloc_ref(),
        AllocSize(capacity_, sizeof(slot_type), alignof(slot_type))));
    ctrl_ = reinterpret_cast<ctrl_t*>(mem);
    slots_ = reinterpret_cast<slot_type*>(
        mem + SlotOffset(capacity_, alignof(slot_type)));
    ResetCtrl(capacity_, ctrl_, slots_, sizeof(slot_type));
    reset_growth_left();
    infoz().RecordStorageChanged(size_, capacity_);
  }

  void destroy_slots() {
    if (!capacity_) return;
    for (size_t i = 0; i != capacity_; ++i) {
      if (IsFull(ctrl_[i])) {
        PolicyTraits::destroy(&alloc_ref(), slots_ + i);
      }
    }

    // Unpoison before returning the memory to the allocator.
    SanitizerUnpoisonMemoryRegion(slots_, sizeof(slot_type) * capacity_);
    Deallocate<alignof(slot_type)>(
        &alloc_ref(), ctrl_,
        AllocSize(capacity_, sizeof(slot_type), alignof(slot_type)));
    ctrl_ = EmptyGroup();
    slots_ = nullptr;
    size_ = 0;
    capacity_ = 0;
    growth_left() = 0;
  }

  void resize(size_t new_capacity) {
    assert(IsValidCapacity(new_capacity));
    auto* old_ctrl = ctrl_;
    auto* old_slots = slots_;
    const size_t old_capacity = capacity_;
    capacity_ = new_capacity;
    initialize_slots();

    size_t total_probe_length = 0;
    for (size_t i = 0; i != old_capacity; ++i) {
      if (IsFull(old_ctrl[i])) {
        size_t hash = PolicyTraits::apply(HashElement{hash_ref()},
                                          PolicyTraits::element(old_slots + i));
        auto target = find_first_non_full(ctrl_, hash, capacity_);
        size_t new_i = target.offset;
        total_probe_length += target.probe_length;
        SetCtrl(new_i, H2(hash), capacity_, ctrl_, slots_, sizeof(slot_type));
        PolicyTraits::transfer(&alloc_ref(), slots_ + new_i, old_slots + i);
      }
    }
    if (old_capacity) {
      SanitizerUnpoisonMemoryRegion(old_slots,
                                    sizeof(slot_type) * old_capacity);
      Deallocate<alignof(slot_type)>(
          &alloc_ref(), old_ctrl,
          AllocSize(old_capacity, sizeof(slot_type), alignof(slot_type)));
    }
    infoz().RecordRehash(total_probe_length);
  }

  void drop_deletes_without_resize() ABSL_ATTRIBUTE_NOINLINE {
    assert(IsValidCapacity(capacity_));
    assert(!is_small(capacity_));
    // Algorithm:
    // - mark all DELETED slots as EMPTY
    // - mark all FULL slots as DELETED
    // - for each slot marked as DELETED
    //     hash = Hash(element)
    //     target = find_first_non_full(hash)
    //     if target is in the same group
    //       mark slot as FULL
    //     else if target is EMPTY
    //       transfer element to target
    //       mark slot as EMPTY
    //       mark target as FULL
    //     else if target is DELETED
    //       swap current element with target element
    //       mark target as FULL
    //       repeat procedure for current slot with moved from element (target)
    ConvertDeletedToEmptyAndFullToDeleted(ctrl_, capacity_);
    alignas(slot_type) unsigned char raw[sizeof(slot_type)];
    size_t total_probe_length = 0;
    slot_type* slot = reinterpret_cast<slot_type*>(&raw);
    for (size_t i = 0; i != capacity_; ++i) {
      if (!IsDeleted(ctrl_[i])) continue;
      const size_t hash = PolicyTraits::apply(
          HashElement{hash_ref()}, PolicyTraits::element(slots_ + i));
      const FindInfo target = find_first_non_full(ctrl_, hash, capacity_);
      const size_t new_i = target.offset;
      total_probe_length += target.probe_length;

      // Verify if the old and new i fall within the same group wrt the hash.
      // If they do, we don't need to move the object as it falls already in the
      // best probe we can.
      const size_t probe_offset = probe(ctrl_, hash, capacity_).offset();
      const auto probe_index = [probe_offset, this](size_t pos) {
        return ((pos - probe_offset) & capacity_) / Group::kWidth;
      };

      // Element doesn't move.
      if (ABSL_PREDICT_TRUE(probe_index(new_i) == probe_index(i))) {
        SetCtrl(i, H2(hash), capacity_, ctrl_, slots_, sizeof(slot_type));
        continue;
      }
      if (IsEmpty(ctrl_[new_i])) {
        // Transfer element to the empty spot.
        // SetCtrl poisons/unpoisons the slots so we have to call it at the
        // right time.
        SetCtrl(new_i, H2(hash), capacity_, ctrl_, slots_, sizeof(slot_type));
        PolicyTraits::transfer(&alloc_ref(), slots_ + new_i, slots_ + i);
        SetCtrl(i, ctrl_t::kEmpty, capacity_, ctrl_, slots_, sizeof(slot_type));
      } else {
        assert(IsDeleted(ctrl_[new_i]));
        SetCtrl(new_i, H2(hash), capacity_, ctrl_, slots_, sizeof(slot_type));
        // Until we are done rehashing, DELETED marks previously FULL slots.
        // Swap i and new_i elements.
        PolicyTraits::transfer(&alloc_ref(), slot, slots_ + i);
        PolicyTraits::transfer(&alloc_ref(), slots_ + i, slots_ + new_i);
        PolicyTraits::transfer(&alloc_ref(), slots_ + new_i, slot);
        --i;  // repeat
      }
    }
    reset_growth_left();
    infoz().RecordRehash(total_probe_length);
  }

  void rehash_and_grow_if_necessary() {
    if (capacity_ == 0) {
      resize(1);
    } else if (capacity_ > Group::kWidth &&
               // Do these calcuations in 64-bit to avoid overflow.
               size() * uint64_t{32} <= capacity_ * uint64_t{25}) {
      // Squash DELETED without growing if there is enough capacity.
      //
      // Rehash in place if the current size is <= 25/32 of capacity_.
      // Rationale for such a high factor: 1) drop_deletes_without_resize() is
      // faster than resize, and 2) it takes quite a bit of work to add
      // tombstones.  In the worst case, seems to take approximately 4
      // insert/erase pairs to create a single tombstone and so if we are
      // rehashing because of tombstones, we can afford to rehash-in-place as
      // long as we are reclaiming at least 1/8 the capacity without doing more
      // than 2X the work.  (Where "work" is defined to be size() for rehashing
      // or rehashing in place, and 1 for an insert or erase.)  But rehashing in
      // place is faster per operation than inserting or even doubling the size
      // of the table, so we actually afford to reclaim even less space from a
      // resize-in-place.  The decision is to rehash in place if we can reclaim
      // at about 1/8th of the usable capacity (specifically 3/28 of the
      // capacity) which means that the total cost of rehashing will be a small
      // fraction of the total work.
      //
      // Here is output of an experiment using the BM_CacheInSteadyState
      // benchmark running the old case (where we rehash-in-place only if we can
      // reclaim at least 7/16*capacity_) vs. this code (which rehashes in place
      // if we can recover 3/32*capacity_).
      //
      // Note that although in the worst-case number of rehashes jumped up from
      // 15 to 190, but the number of operations per second is almost the same.
      //
      // Abridged output of running BM_CacheInSteadyState benchmark from
      // raw_hash_set_benchmark.   N is the number of insert/erase operations.
      //
      //      | OLD (recover >= 7/16        | NEW (recover >= 3/32)
      // size |    N/s LoadFactor NRehashes |    N/s LoadFactor NRehashes
      //  448 | 145284       0.44        18 | 140118       0.44        19
      //  493 | 152546       0.24        11 | 151417       0.48        28
      //  538 | 151439       0.26        11 | 151152       0.53        38
      //  583 | 151765       0.28        11 | 150572       0.57        50
      //  628 | 150241       0.31        11 | 150853       0.61        66
      //  672 | 149602       0.33        12 | 150110       0.66        90
      //  717 | 149998       0.35        12 | 149531       0.70       129
      //  762 | 149836       0.37        13 | 148559       0.74       190
      //  807 | 149736       0.39        14 | 151107       0.39        14
      //  852 | 150204       0.42        15 | 151019       0.42        15
      drop_deletes_without_resize();
    } else {
      // Otherwise grow the container.
      resize(capacity_ * 2 + 1);
    }
  }

  bool has_element(const value_type& elem) const {
    size_t hash = PolicyTraits::apply(HashElement{hash_ref()}, elem);
    auto seq = probe(ctrl_, hash, capacity_);
    while (true) {
      Group g{ctrl_ + seq.offset()};
      for (uint32_t i : g.Match(H2(hash))) {
        if (ABSL_PREDICT_TRUE(PolicyTraits::element(slots_ + seq.offset(i)) ==
                              elem))
          return true;
      }
      if (ABSL_PREDICT_TRUE(g.MatchEmpty())) return false;
      seq.next();
      assert(seq.index() <= capacity_ && "full table!");
    }
    return false;
  }

  // TODO(alkis): Optimize this assuming *this and that don't overlap.
  raw_hash_set& move_assign(raw_hash_set&& that, std::true_type) {
    raw_hash_set tmp(std::move(that));
    swap(tmp);
    return *this;
  }
  raw_hash_set& move_assign(raw_hash_set&& that, std::false_type) {
    raw_hash_set tmp(std::move(that), alloc_ref());
    swap(tmp);
    return *this;
  }

 protected:
  template <class K>
  std::pair<size_t, bool> find_or_prepare_insert(const K& key) {
    prefetch_heap_block();
    auto hash = hash_ref()(key);
    auto seq = probe(ctrl_, hash, capacity_);
    while (true) {
      Group g{ctrl_ + seq.offset()};
      for (uint32_t i : g.Match(H2(hash))) {
        if (ABSL_PREDICT_TRUE(PolicyTraits::apply(
                EqualElement<K>{key, eq_ref()},
                PolicyTraits::element(slots_ + seq.offset(i)))))
          return {seq.offset(i), false};
      }
      if (ABSL_PREDICT_TRUE(g.MatchEmpty())) break;
      seq.next();
      assert(seq.index() <= capacity_ && "full table!");
    }
    return {prepare_insert(hash), true};
  }

  size_t prepare_insert(size_t hash) ABSL_ATTRIBUTE_NOINLINE {
    auto target = find_first_non_full(ctrl_, hash, capacity_);
    if (ABSL_PREDICT_FALSE(growth_left() == 0 &&
                           !IsDeleted(ctrl_[target.offset]))) {
      rehash_and_grow_if_necessary();
      target = find_first_non_full(ctrl_, hash, capacity_);
    }
    ++size_;
    growth_left() -= IsEmpty(ctrl_[target.offset]);
    SetCtrl(target.offset, H2(hash), capacity_, ctrl_, slots_,
            sizeof(slot_type));
    infoz().RecordInsert(hash, target.probe_length);
    return target.offset;
  }

  // Constructs the value in the space pointed by the iterator. This only works
  // after an unsuccessful find_or_prepare_insert() and before any other
  // modifications happen in the raw_hash_set.
  //
  // PRECONDITION: i is an index returned from find_or_prepare_insert(k), where
  // k is the key decomposed from `forward<Args>(args)...`, and the bool
  // returned by find_or_prepare_insert(k) was true.
  // POSTCONDITION: *m.iterator_at(i) == value_type(forward<Args>(args)...).
  template <class... Args>
  void emplace_at(size_t i, Args&&... args) {
    PolicyTraits::construct(&alloc_ref(), slots_ + i,
                            std::forward<Args>(args)...);

    assert(PolicyTraits::apply(FindElement{*this}, *iterator_at(i)) ==
               iterator_at(i) &&
           "constructed value does not match the lookup key");
  }

  iterator iterator_at(size_t i) { return {ctrl_ + i, slots_ + i}; }
  const_iterator iterator_at(size_t i) const { return {ctrl_ + i, slots_ + i}; }

 private:
  friend struct RawHashSetTestOnlyAccess;

  void reset_growth_left() {
    growth_left() = CapacityToGrowth(capacity()) - size_;
  }

  size_t& growth_left() { return settings_.template get<0>(); }

  void prefetch_heap_block() const {
    // Prefetch the heap-allocated memory region to resolve potential TLB
    // misses.  This is intended to overlap with execution of calculating the
    // hash for a key.
#if defined(__GNUC__)
    __builtin_prefetch(static_cast<const void*>(ctrl_), 0, 1);
#endif  // __GNUC__
  }

  HashtablezInfoHandle& infoz() { return settings_.template get<1>(); }

  hasher& hash_ref() { return settings_.template get<2>(); }
  const hasher& hash_ref() const { return settings_.template get<2>(); }
  key_equal& eq_ref() { return settings_.template get<3>(); }
  const key_equal& eq_ref() const { return settings_.template get<3>(); }
  allocator_type& alloc_ref() { return settings_.template get<4>(); }
  const allocator_type& alloc_ref() const {
    return settings_.template get<4>();
  }

  // TODO(alkis): Investigate removing some of these fields:
  // - ctrl/slots can be derived from each other
  // - size can be moved into the slot array
  ctrl_t* ctrl_ = EmptyGroup();  // [(capacity + 1 + NumClonedBytes()) * ctrl_t]
  slot_type* slots_ = nullptr;   // [capacity * slot_type]
  size_t size_ = 0;              // number of full slots
  size_t capacity_ = 0;          // total number of slots
  absl::container_internal::CompressedTuple<size_t /* growth_left */,
                                            HashtablezInfoHandle, hasher,
                                            key_equal, allocator_type>
      settings_{0u, HashtablezInfoHandle{}, hasher{}, key_equal{},
                allocator_type{}};
};

// Erases all elements that satisfy the predicate `pred` from the container `c`.
template <typename P, typename H, typename E, typename A, typename Predicate>
typename raw_hash_set<P, H, E, A>::size_type EraseIf(
    Predicate& pred, raw_hash_set<P, H, E, A>* c) {
  const auto initial_size = c->size();
  for (auto it = c->begin(), last = c->end(); it != last;) {
    if (pred(*it)) {
      c->erase(it++);
    } else {
      ++it;
    }
  }
  return initial_size - c->size();
}

namespace hashtable_debug_internal {
template <typename Set>
struct HashtableDebugAccess<Set, absl::void_t<typename Set::raw_hash_set>> {
  using Traits = typename Set::PolicyTraits;
  using Slot = typename Traits::slot_type;

  static size_t GetNumProbes(const Set& set,
                             const typename Set::key_type& key) {
    size_t num_probes = 0;
    size_t hash = set.hash_ref()(key);
    auto seq = probe(set.ctrl_, hash, set.capacity_);
    while (true) {
      container_internal::Group g{set.ctrl_ + seq.offset()};
      for (uint32_t i : g.Match(container_internal::H2(hash))) {
        if (Traits::apply(
                typename Set::template EqualElement<typename Set::key_type>{
                    key, set.eq_ref()},
                Traits::element(set.slots_ + seq.offset(i))))
          return num_probes;
        ++num_probes;
      }
      if (g.MatchEmpty()) return num_probes;
      seq.next();
      ++num_probes;
    }
  }

  static size_t AllocatedByteSize(const Set& c) {
    size_t capacity = c.capacity_;
    if (capacity == 0) return 0;
    size_t m = AllocSize(capacity, sizeof(Slot), alignof(Slot));

    size_t per_slot = Traits::space_used(static_cast<const Slot*>(nullptr));
    if (per_slot != ~size_t{}) {
      m += per_slot * c.size();
    } else {
      for (size_t i = 0; i != capacity; ++i) {
        if (container_internal::IsFull(c.ctrl_[i])) {
          m += Traits::space_used(c.slots_ + i);
        }
      }
    }
    return m;
  }

  static size_t LowerBoundAllocatedByteSize(size_t size) {
    size_t capacity = GrowthToLowerboundCapacity(size);
    if (capacity == 0) return 0;
    size_t m =
        AllocSize(NormalizeCapacity(capacity), sizeof(Slot), alignof(Slot));
    size_t per_slot = Traits::space_used(static_cast<const Slot*>(nullptr));
    if (per_slot != ~size_t{}) {
      m += per_slot * size;
    }
    return m;
  }
};

}  // namespace hashtable_debug_internal
}  // namespace container_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_CONTAINER_INTERNAL_RAW_HASH_SET_H_
