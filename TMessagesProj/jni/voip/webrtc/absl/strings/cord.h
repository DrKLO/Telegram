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
//
// -----------------------------------------------------------------------------
// File: cord.h
// -----------------------------------------------------------------------------
//
// This file defines the `absl::Cord` data structure and operations on that data
// structure. A Cord is a string-like sequence of characters optimized for
// specific use cases. Unlike a `std::string`, which stores an array of
// contiguous characters, Cord data is stored in a structure consisting of
// separate, reference-counted "chunks." (Currently, this implementation is a
// tree structure, though that implementation may change.)
//
// Because a Cord consists of these chunks, data can be added to or removed from
// a Cord during its lifetime. Chunks may also be shared between Cords. Unlike a
// `std::string`, a Cord can therefore accomodate data that changes over its
// lifetime, though it's not quite "mutable"; it can change only in the
// attachment, detachment, or rearrangement of chunks of its constituent data.
//
// A Cord provides some benefit over `std::string` under the following (albeit
// narrow) circumstances:
//
//   * Cord data is designed to grow and shrink over a Cord's lifetime. Cord
//     provides efficient insertions and deletions at the start and end of the
//     character sequences, avoiding copies in those cases. Static data should
//     generally be stored as strings.
//   * External memory consisting of string-like data can be directly added to
//     a Cord without requiring copies or allocations.
//   * Cord data may be shared and copied cheaply. Cord provides a copy-on-write
//     implementation and cheap sub-Cord operations. Copying a Cord is an O(1)
//     operation.
//
// As a consequence to the above, Cord data is generally large. Small data
// should generally use strings, as construction of a Cord requires some
// overhead. Small Cords (<= 15 bytes) are represented inline, but most small
// Cords are expected to grow over their lifetimes.
//
// Note that because a Cord is made up of separate chunked data, random access
// to character data within a Cord is slower than within a `std::string`.
//
// Thread Safety
//
// Cord has the same thread-safety properties as many other types like
// std::string, std::vector<>, int, etc -- it is thread-compatible. In
// particular, if threads do not call non-const methods, then it is safe to call
// const methods without synchronization. Copying a Cord produces a new instance
// that can be used concurrently with the original in arbitrary ways.

#ifndef ABSL_STRINGS_CORD_H_
#define ABSL_STRINGS_CORD_H_

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <iterator>
#include <string>
#include <type_traits>

#include "absl/base/internal/endian.h"
#include "absl/base/internal/invoke.h"
#include "absl/base/internal/per_thread_tls.h"
#include "absl/base/macros.h"
#include "absl/base/port.h"
#include "absl/container/inlined_vector.h"
#include "absl/functional/function_ref.h"
#include "absl/meta/type_traits.h"
#include "absl/strings/internal/cord_internal.h"
#include "absl/strings/internal/resize_uninitialized.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
class Cord;
class CordTestPeer;
template <typename Releaser>
Cord MakeCordFromExternal(absl::string_view, Releaser&&);
void CopyCordToString(const Cord& src, std::string* dst);
namespace hash_internal {
template <typename H>
H HashFragmentedCord(H, const Cord&);
}

// Cord
//
// A Cord is a sequence of characters, designed to be more efficient than a
// `std::string` in certain circumstances: namely, large string data that needs
// to change over its lifetime or shared, especially when such data is shared
// across API boundaries.
//
// A Cord stores its character data in a structure that allows efficient prepend
// and append operations. This makes a Cord useful for large string data sent
// over in a wire format that may need to be prepended or appended at some point
// during the data exchange (e.g. HTTP, protocol buffers). For example, a
// Cord is useful for storing an HTTP request, and prepending an HTTP header to
// such a request.
//
// Cords should not be used for storing general string data, however. They
// require overhead to construct and are slower than strings for random access.
//
// The Cord API provides the following common API operations:
//
// * Create or assign Cords out of existing string data, memory, or other Cords
// * Append and prepend data to an existing Cord
// * Create new Sub-Cords from existing Cord data
// * Swap Cord data and compare Cord equality
// * Write out Cord data by constructing a `std::string`
//
// Additionally, the API provides iterator utilities to iterate through Cord
// data via chunks or character bytes.
//

namespace cord_internal {

// It's expensive to keep a Cord's tree perfectly balanced, so instead we keep
// trees approximately balanced.  A tree node N of depth D(N) that contains a
// string of L(N) characters is considered balanced if L >= Fibonacci(D + 2).
// The "+ 2" is used to ensure that every balanced leaf node contains at least
//  one character. Here we presume that
//   Fibonacci(0) = 0
//   Fibonacci(1) = 1
//   Fibonacci(2) = 1
//   Fibonacci(3) = 2
//   ...
// The algorithm is based on paper by Hans Boehm et al:
// https://www.cs.rit.edu/usr/local/pub/jeh/courses/QUARTERS/FP/Labs/CedarRope/rope-paper.pdf
// In this paper authors shows that rebalancing based on cord forest of already
// balanced subtrees can be proven to never produce tree of depth larger than
// largest Fibonacci number representable in the same integral type as cord size
// For 64 bit integers this is the 93rd Fibonacci number. For 32 bit integrals
// this is 47th Fibonacci number.
constexpr size_t MaxCordDepth() { return sizeof(size_t) == 8 ? 93 : 47; }

// This class models fixed max size stack of CordRep pointers.
// The elements are being pushed back and popped from the back.
template <typename CordRepPtr, size_t N>
class CordTreePath {
 public:
  CordTreePath() {}
  explicit CordTreePath(CordRepPtr root) { push_back(root); }

  bool empty() const { return size_ == 0; }
  size_t size() const { return size_; }
  void clear() { size_ = 0; }

  CordRepPtr back() { return data_[size_ - 1]; }

  void pop_back() {
    --size_;
    assert(size_ < N);
  }
  void push_back(CordRepPtr elem) { data_[size_++] = elem; }

 private:
  CordRepPtr data_[N];
  size_t size_ = 0;
};

// Fixed length container for mutable "path" in cord tree, which can hold any
// possible valid path in cord tree.
using CordTreeMutablePath = CordTreePath<CordRep*, MaxCordDepth()>;
// Variable length container for mutable "path" in cord tree. It starts with
// capacity for 15 elements and grow if necessary.
using CordTreeDynamicPath =
    absl::InlinedVector<absl::cord_internal::CordRep*, 15>;
}  // namespace cord_internal

// A Cord is a sequence of characters.
class Cord {
 private:
  template <typename T>
  using EnableIfString =
      absl::enable_if_t<std::is_same<T, std::string>::value, int>;

  //----------------------------------------------------------------------------
  // Cord::GenericChunkIterator
  //----------------------------------------------------------------------------
  //
  // A `Cord::GenericChunkIterator` provides an interface for the standard
  // `Cord::ChunkIterator` as well as some private implementations.
  template <typename StorageType>
  class GenericChunkIterator {
   public:
    using iterator_category = std::input_iterator_tag;
    using value_type = absl::string_view;
    using difference_type = ptrdiff_t;
    using pointer = const value_type*;
    using reference = value_type;

    GenericChunkIterator() = default;

    GenericChunkIterator& operator++();
    GenericChunkIterator operator++(int);
    bool operator==(const GenericChunkIterator& other) const;
    bool operator!=(const GenericChunkIterator& other) const;
    reference operator*() const;
    pointer operator->() const;

    friend class Cord;
    friend class CharIterator;

   private:
    // Constructs a `begin()` iterator from `cord`.
    explicit GenericChunkIterator(const Cord* cord);

    // Removes `n` bytes from `current_chunk_`. Expects `n` to be smaller than
    // `current_chunk_.size()`.
    void RemoveChunkPrefix(size_t n);
    Cord AdvanceAndReadBytes(size_t n);
    void AdvanceBytes(size_t n);
    // Iterates `n` bytes, where `n` is expected to be greater than or equal to
    // `current_chunk_.size()`.
    void AdvanceBytesSlowPath(size_t n);

    // A view into bytes of the current `CordRep`. It may only be a view to a
    // suffix of bytes if this is being used by `CharIterator`.
    absl::string_view current_chunk_;
    // The current leaf, or `nullptr` if the iterator points to short data.
    // If the current chunk is a substring node, current_leaf_ points to the
    // underlying flat or external node.
    cord_internal::CordRep* current_leaf_ = nullptr;
    // The number of bytes left in the `Cord` over which we are iterating.
    size_t bytes_remaining_ = 0;
    StorageType stack_of_right_children_;
  };
  template <typename IteratorType>
  class GenericChunkRange {
   public:
    explicit GenericChunkRange(const Cord* cord) : cord_(cord) {}

    IteratorType begin() const { return IteratorType(cord_); }
    IteratorType end() const { return IteratorType(); }

   private:
    const Cord* cord_;
  };

 public:
  // Cord::Cord() Constructors

  // Creates an empty Cord
  constexpr Cord() noexcept;

  // Creates a Cord from an existing Cord. Cord is copyable and efficiently
  // movable. The moved-from state is valid but unspecified.
  Cord(const Cord& src);
  Cord(Cord&& src) noexcept;
  Cord& operator=(const Cord& x);
  Cord& operator=(Cord&& x) noexcept;

  // Creates a Cord from a `src` string. This constructor is marked explicit to
  // prevent implicit Cord constructions from arguments convertible to an
  // `absl::string_view`.
  explicit Cord(absl::string_view src);
  Cord& operator=(absl::string_view src);

  // Creates a Cord from a `std::string&&` rvalue. These constructors are
  // templated to avoid ambiguities for types that are convertible to both
  // `absl::string_view` and `std::string`, such as `const char*`.
  //
  // Note that these functions reserve the right to use the `string&&`'s
  // memory and that they will do so in the future.
  template <typename T, EnableIfString<T> = 0>
  explicit Cord(T&& src) : Cord(absl::string_view(src)) {}
  template <typename T, EnableIfString<T> = 0>
  Cord& operator=(T&& src);

  // Cord::~Cord()
  //
  // Destructs the Cord
  ~Cord() {
    if (contents_.is_tree()) DestroyCordSlow();
  }

  // Cord::MakeCordFromExternal(data, callable)
  //
  // Creates a Cord that takes ownership of external string memory. The
  // contents of `data` are not copied to the Cord; instead, the external
  // memory is added to the Cord and reference-counted. This data may not be
  // changed for the life of the Cord, though it may be prepended or appended
  // to.
  //
  // `MakeCordFromExternal()` takes a callable "releaser" that is invoked when
  // the reference count for `data` reaches zero. As noted above, this data must
  // remain live until the releaser is invoked. The callable releaser also must:
  //
  //   * be move constructible
  //   * support `void operator()(absl::string_view) const` or `void operator()`
  //   * not have alignment requirement greater than what is guaranteed by
  //     `::operator new`. This alignment is dictated by
  //     `alignof(std::max_align_t)` (pre-C++17 code) or
  //     `__STDCPP_DEFAULT_NEW_ALIGNMENT__` (C++17 code).
  //
  // Example:
  //
  // Cord MakeCord(BlockPool* pool) {
  //   Block* block = pool->NewBlock();
  //   FillBlock(block);
  //   return absl::MakeCordFromExternal(
  //       block->ToStringView(),
  //       [pool, block](absl::string_view v) {
  //         pool->FreeBlock(block, v);
  //       });
  // }
  //
  // WARNING: Because a Cord can be reference-counted, it's likely a bug if your
  // releaser doesn't do anything. For example, consider the following:
  //
  // void Foo(const char* buffer, int len) {
  //   auto c = absl::MakeCordFromExternal(absl::string_view(buffer, len),
  //                                       [](absl::string_view) {});
  //
  //   // BUG: If Bar() copies its cord for any reason, including keeping a
  //   // substring of it, the lifetime of buffer might be extended beyond
  //   // when Foo() returns.
  //   Bar(c);
  // }
  template <typename Releaser>
  friend Cord MakeCordFromExternal(absl::string_view data, Releaser&& releaser);

  // Cord::Clear()
  //
  // Releases the Cord data. Any nodes that share data with other Cords, if
  // applicable, will have their reference counts reduced by 1.
  void Clear();

  // Cord::Append()
  //
  // Appends data to the Cord, which may come from another Cord or other string
  // data.
  void Append(const Cord& src);
  void Append(Cord&& src);
  void Append(absl::string_view src);
  template <typename T, EnableIfString<T> = 0>
  void Append(T&& src);

  // Cord::Prepend()
  //
  // Prepends data to the Cord, which may come from another Cord or other string
  // data.
  void Prepend(const Cord& src);
  void Prepend(absl::string_view src);
  template <typename T, EnableIfString<T> = 0>
  void Prepend(T&& src);

  // Cord::RemovePrefix()
  //
  // Removes the first `n` bytes of a Cord.
  void RemovePrefix(size_t n);
  void RemoveSuffix(size_t n);

  // Cord::Subcord()
  //
  // Returns a new Cord representing the subrange [pos, pos + new_size) of
  // *this. If pos >= size(), the result is empty(). If
  // (pos + new_size) >= size(), the result is the subrange [pos, size()).
  Cord Subcord(size_t pos, size_t new_size) const;

  // swap()
  //
  // Swaps the data of Cord `x` with Cord `y`.
  friend void swap(Cord& x, Cord& y) noexcept;

  // Cord::size()
  //
  // Returns the size of the Cord.
  size_t size() const;

  // Cord::empty()
  //
  // Determines whether the given Cord is empty, returning `true` is so.
  bool empty() const;

  // Cord:EstimatedMemoryUsage()
  //
  // Returns the *approximate* number of bytes held in full or in part by this
  // Cord (which may not remain the same between invocations).  Note that Cords
  // that share memory could each be "charged" independently for the same shared
  // memory.
  size_t EstimatedMemoryUsage() const;

  // Cord::Compare()
  //
  // Compares 'this' Cord with rhs. This function and its relatives treat Cords
  // as sequences of unsigned bytes. The comparison is a straightforward
  // lexicographic comparison. `Cord::Compare()` returns values as follows:
  //
  //   -1  'this' Cord is smaller
  //    0  two Cords are equal
  //    1  'this' Cord is larger
  int Compare(absl::string_view rhs) const;
  int Compare(const Cord& rhs) const;

  // Cord::StartsWith()
  //
  // Determines whether the Cord starts with the passed string data `rhs`.
  bool StartsWith(const Cord& rhs) const;
  bool StartsWith(absl::string_view rhs) const;

  // Cord::EndsWidth()
  //
  // Determines whether the Cord ends with the passed string data `rhs`.
  bool EndsWith(absl::string_view rhs) const;
  bool EndsWith(const Cord& rhs) const;

  // Cord::operator std::string()
  //
  // Converts a Cord into a `std::string()`. This operator is marked explicit to
  // prevent unintended Cord usage in functions that take a string.
  explicit operator std::string() const;

  // CopyCordToString()
  //
  // Copies the contents of a `src` Cord into a `*dst` string.
  //
  // This function optimizes the case of reusing the destination string since it
  // can reuse previously allocated capacity. However, this function does not
  // guarantee that pointers previously returned by `dst->data()` remain valid
  // even if `*dst` had enough capacity to hold `src`. If `*dst` is a new
  // object, prefer to simply use the conversion operator to `std::string`.
  friend void CopyCordToString(const Cord& src, std::string* dst);

  class CharIterator;

  //----------------------------------------------------------------------------
  // Cord::ChunkIterator
  //----------------------------------------------------------------------------
  //
  // A `Cord::ChunkIterator` allows iteration over the constituent chunks of its
  // Cord. Such iteration allows you to perform non-const operatons on the data
  // of a Cord without modifying it.
  //
  // Generally, you do not instantiate a `Cord::ChunkIterator` directly;
  // instead, you create one implicitly through use of the `Cord::Chunks()`
  // member function.
  //
  // The `Cord::ChunkIterator` has the following properties:
  //
  //   * The iterator is invalidated after any non-const operation on the
  //     Cord object over which it iterates.
  //   * The `string_view` returned by dereferencing a valid, non-`end()`
  //     iterator is guaranteed to be non-empty.
  //   * Two `ChunkIterator` objects can be compared equal if and only if they
  //     remain valid and iterate over the same Cord.
  //   * The iterator in this case is a proxy iterator; the `string_view`
  //     returned by the iterator does not live inside the Cord, and its
  //     lifetime is limited to the lifetime of the iterator itself. To help
  //     prevent lifetime issues, `ChunkIterator::reference` is not a true
  //     reference type and is equivalent to `value_type`.
  //   * The iterator keeps state that can grow for Cords that contain many
  //     nodes and are imbalanced due to sharing. Prefer to pass this type by
  //     const reference instead of by value.
  using ChunkIterator =
      GenericChunkIterator<cord_internal::CordTreeDynamicPath>;

  // Cord::ChunkIterator::chunk_begin()
  //
  // Returns an iterator to the first chunk of the `Cord`.
  //
  // Generally, prefer using `Cord::Chunks()` within a range-based for loop for
  // iterating over the chunks of a Cord. This method may be useful for getting
  // a `ChunkIterator` where range-based for-loops are not useful.
  //
  // Example:
  //
  //   absl::Cord::ChunkIterator FindAsChunk(const absl::Cord& c,
  //                                         absl::string_view s) {
  //     return std::find(c.chunk_begin(), c.chunk_end(), s);
  //   }
  ChunkIterator chunk_begin() const;

  // Cord::ChunkItertator::chunk_end()
  //
  // Returns an iterator one increment past the last chunk of the `Cord`.
  //
  // Generally, prefer using `Cord::Chunks()` within a range-based for loop for
  // iterating over the chunks of a Cord. This method may be useful for getting
  // a `ChunkIterator` where range-based for-loops may not be available.
  ChunkIterator chunk_end() const;

  //----------------------------------------------------------------------------
  // Cord::ChunkIterator::ChunkRange
  //----------------------------------------------------------------------------
  //
  // `ChunkRange` is a helper class for iterating over the chunks of the `Cord`,
  // producing an iterator which can be used within a range-based for loop.
  // Construction of a `ChunkRange` will return an iterator pointing to the
  // first chunk of the Cord. Generally, do not construct a `ChunkRange`
  // directly; instead, prefer to use the `Cord::Chunks()` method.
  //
  // Implementation note: `ChunkRange` is simply a convenience wrapper over
  // `Cord::chunk_begin()` and `Cord::chunk_end()`.
  using ChunkRange = GenericChunkRange<ChunkIterator>;

  // Cord::Chunks()
  //
  // Returns a `Cord::ChunkIterator::ChunkRange` for iterating over the chunks
  // of a `Cord` with a range-based for-loop. For most iteration tasks on a
  // Cord, use `Cord::Chunks()` to retrieve this iterator.
  //
  // Example:
  //
  //   void ProcessChunks(const Cord& cord) {
  //     for (absl::string_view chunk : cord.Chunks()) { ... }
  //   }
  //
  // Note that the ordinary caveats of temporary lifetime extension apply:
  //
  //   void Process() {
  //     for (absl::string_view chunk : CordFactory().Chunks()) {
  //       // The temporary Cord returned by CordFactory has been destroyed!
  //     }
  //   }
  ChunkRange Chunks() const;

  //----------------------------------------------------------------------------
  // Cord::CharIterator
  //----------------------------------------------------------------------------
  //
  // A `Cord::CharIterator` allows iteration over the constituent characters of
  // a `Cord`.
  //
  // Generally, you do not instantiate a `Cord::CharIterator` directly; instead,
  // you create one implicitly through use of the `Cord::Chars()` member
  // function.
  //
  // A `Cord::CharIterator` has the following properties:
  //
  //   * The iterator is invalidated after any non-const operation on the
  //     Cord object over which it iterates.
  //   * Two `CharIterator` objects can be compared equal if and only if they
  //     remain valid and iterate over the same Cord.
  //   * The iterator keeps state that can grow for Cords that contain many
  //     nodes and are imbalanced due to sharing. Prefer to pass this type by
  //     const reference instead of by value.
  //   * This type cannot act as a forward iterator because a `Cord` can reuse
  //     sections of memory. This fact violates the requirement for forward
  //     iterators to compare equal if dereferencing them returns the same
  //     object.
  class CharIterator {
   public:
    using iterator_category = std::input_iterator_tag;
    using value_type = char;
    using difference_type = ptrdiff_t;
    using pointer = const char*;
    using reference = const char&;

    CharIterator() = default;

    CharIterator& operator++();
    CharIterator operator++(int);
    bool operator==(const CharIterator& other) const;
    bool operator!=(const CharIterator& other) const;
    reference operator*() const;
    pointer operator->() const;

    friend Cord;

   private:
    explicit CharIterator(const Cord* cord) : chunk_iterator_(cord) {}

    ChunkIterator chunk_iterator_;
  };

  // Cord::CharIterator::AdvanceAndRead()
  //
  // Advances the `Cord::CharIterator` by `n_bytes` and returns the bytes
  // advanced as a separate `Cord`. `n_bytes` must be less than or equal to the
  // number of bytes within the Cord; otherwise, behavior is undefined. It is
  // valid to pass `char_end()` and `0`.
  static Cord AdvanceAndRead(CharIterator* it, size_t n_bytes);

  // Cord::CharIterator::Advance()
  //
  // Advances the `Cord::CharIterator` by `n_bytes`. `n_bytes` must be less than
  // or equal to the number of bytes remaining within the Cord; otherwise,
  // behavior is undefined. It is valid to pass `char_end()` and `0`.
  static void Advance(CharIterator* it, size_t n_bytes);

  // Cord::CharIterator::ChunkRemaining()
  //
  // Returns the longest contiguous view starting at the iterator's position.
  //
  // `it` must be dereferenceable.
  static absl::string_view ChunkRemaining(const CharIterator& it);

  // Cord::CharIterator::char_begin()
  //
  // Returns an iterator to the first character of the `Cord`.
  //
  // Generally, prefer using `Cord::Chars()` within a range-based for loop for
  // iterating over the chunks of a Cord. This method may be useful for getting
  // a `CharIterator` where range-based for-loops may not be available.
  CharIterator char_begin() const;

  // Cord::CharIterator::char_end()
  //
  // Returns an iterator to one past the last character of the `Cord`.
  //
  // Generally, prefer using `Cord::Chars()` within a range-based for loop for
  // iterating over the chunks of a Cord. This method may be useful for getting
  // a `CharIterator` where range-based for-loops are not useful.
  CharIterator char_end() const;

  // Cord::CharIterator::CharRange
  //
  // `CharRange` is a helper class for iterating over the characters of a
  // producing an iterator which can be used within a range-based for loop.
  // Construction of a `CharRange` will return an iterator pointing to the first
  // character of the Cord. Generally, do not construct a `CharRange` directly;
  // instead, prefer to use the `Cord::Chars()` method show below.
  //
  // Implementation note: `CharRange` is simply a convenience wrapper over
  // `Cord::char_begin()` and `Cord::char_end()`.
  class CharRange {
   public:
    explicit CharRange(const Cord* cord) : cord_(cord) {}

    CharIterator begin() const;
    CharIterator end() const;

   private:
    const Cord* cord_;
  };

  // Cord::CharIterator::Chars()
  //
  // Returns a `Cord::CharIterator` for iterating over the characters of a
  // `Cord` with a range-based for-loop. For most character-based iteration
  // tasks on a Cord, use `Cord::Chars()` to retrieve this iterator.
  //
  // Example:
  //
  //   void ProcessCord(const Cord& cord) {
  //     for (char c : cord.Chars()) { ... }
  //   }
  //
  // Note that the ordinary caveats of temporary lifetime extension apply:
  //
  //   void Process() {
  //     for (char c : CordFactory().Chars()) {
  //       // The temporary Cord returned by CordFactory has been destroyed!
  //     }
  //   }
  CharRange Chars() const;

  // Cord::operator[]
  //
  // Get the "i"th character of the Cord and returns it, provided that
  // 0 <= i < Cord.size().
  //
  // NOTE: This routine is reasonably efficient. It is roughly
  // logarithmic based on the number of chunks that make up the cord. Still,
  // if you need to iterate over the contents of a cord, you should
  // use a CharIterator/ChunkIterator rather than call operator[] or Get()
  // repeatedly in a loop.
  char operator[](size_t i) const;

  // Cord::TryFlat()
  //
  // If this cord's representation is a single flat array, return a
  // string_view referencing that array.  Otherwise return nullopt.
  absl::optional<absl::string_view> TryFlat() const;

  // Cord::Flatten()
  //
  // Flattens the cord into a single array and returns a view of the data.
  //
  // If the cord was already flat, the contents are not modified.
  absl::string_view Flatten();

 private:
  friend class CordTestPeer;
  template <typename H>
  friend H absl::hash_internal::HashFragmentedCord(H, const Cord&);
  friend bool operator==(const Cord& lhs, const Cord& rhs);
  friend bool operator==(const Cord& lhs, absl::string_view rhs);

  // Call the provided function once for each cord chunk, in order.  Unlike
  // Chunks(), this API will not allocate memory.
  void ForEachChunk(absl::FunctionRef<void(absl::string_view)>) const;

  // Allocates new contiguous storage for the contents of the cord. This is
  // called by Flatten() when the cord was not already flat.
  absl::string_view FlattenSlowPath();

  // Actual cord contents are hidden inside the following simple
  // class so that we can isolate the bulk of cord.cc from changes
  // to the representation.
  //
  // InlineRep holds either either a tree pointer, or an array of kMaxInline
  // bytes.
  class InlineRep {
   public:
    static const unsigned char kMaxInline = 15;
    static_assert(kMaxInline >= sizeof(absl::cord_internal::CordRep*), "");
    // Tag byte & kMaxInline means we are storing a pointer.
    static const unsigned char kTreeFlag = 1 << 4;
    // Tag byte & kProfiledFlag means we are profiling the Cord.
    static const unsigned char kProfiledFlag = 1 << 5;

    constexpr InlineRep() : data_{} {}
    InlineRep(const InlineRep& src);
    InlineRep(InlineRep&& src);
    InlineRep& operator=(const InlineRep& src);
    InlineRep& operator=(InlineRep&& src) noexcept;

    void Swap(InlineRep* rhs);
    bool empty() const;
    size_t size() const;
    const char* data() const;  // Returns nullptr if holding pointer
    void set_data(const char* data, size_t n,
                  bool nullify_tail);  // Discards pointer, if any
    char* set_data(size_t n);  // Write data to the result
    // Returns nullptr if holding bytes
    absl::cord_internal::CordRep* tree() const;
    // Discards old pointer, if any
    void set_tree(absl::cord_internal::CordRep* rep);
    // Replaces a tree with a new root. This is faster than set_tree, but it
    // should only be used when it's clear that the old rep was a tree.
    void replace_tree(absl::cord_internal::CordRep* rep);
    // Returns non-null iff was holding a pointer
    absl::cord_internal::CordRep* clear();
    // Convert to pointer if necessary
    absl::cord_internal::CordRep* force_tree(size_t extra_hint);
    void reduce_size(size_t n);  // REQUIRES: holding data
    void remove_prefix(size_t n);  // REQUIRES: holding data
    void AppendArray(const char* src_data, size_t src_size);
    absl::string_view FindFlatStartPiece() const;
    void AppendTree(absl::cord_internal::CordRep* tree);
    void PrependTree(absl::cord_internal::CordRep* tree);
    void GetAppendRegion(char** region, size_t* size, size_t max_length);
    void GetAppendRegion(char** region, size_t* size);
    bool IsSame(const InlineRep& other) const {
      return memcmp(data_, other.data_, sizeof(data_)) == 0;
    }
    int BitwiseCompare(const InlineRep& other) const {
      uint64_t x, y;
      // Use memcpy to avoid anti-aliasing issues.
      memcpy(&x, data_, sizeof(x));
      memcpy(&y, other.data_, sizeof(y));
      if (x == y) {
        memcpy(&x, data_ + 8, sizeof(x));
        memcpy(&y, other.data_ + 8, sizeof(y));
        if (x == y) return 0;
      }
      return absl::big_endian::FromHost64(x) < absl::big_endian::FromHost64(y)
                 ? -1
                 : 1;
    }
    void CopyTo(std::string* dst) const {
      // memcpy is much faster when operating on a known size. On most supported
      // platforms, the small string optimization is large enough that resizing
      // to 15 bytes does not cause a memory allocation.
      absl::strings_internal::STLStringResizeUninitialized(dst,
                                                           sizeof(data_) - 1);
      memcpy(&(*dst)[0], data_, sizeof(data_) - 1);
      // erase is faster than resize because the logic for memory allocation is
      // not needed.
      dst->erase(data_[kMaxInline]);
    }

    // Copies the inline contents into `dst`. Assumes the cord is not empty.
    void CopyToArray(char* dst) const;

    bool is_tree() const { return data_[kMaxInline] > kMaxInline; }

   private:
    friend class Cord;

    void AssignSlow(const InlineRep& src);
    // Unrefs the tree, stops profiling, and zeroes the contents
    void ClearSlow();

    // If the data has length <= kMaxInline, we store it in data_[0..len-1],
    // and store the length in data_[kMaxInline].  Else we store it in a tree
    // and store a pointer to that tree in data_[0..sizeof(CordRep*)-1].
    alignas(absl::cord_internal::CordRep*) char data_[kMaxInline + 1];
  };
  InlineRep contents_;

  // Helper for MemoryUsage()
  static size_t MemoryUsageAux(const absl::cord_internal::CordRep* rep);

  // Helper for GetFlat() and TryFlat()
  static bool GetFlatAux(absl::cord_internal::CordRep* rep,
                         absl::string_view* fragment);

  // Iterators for use inside Cord implementation
  using InternalChunkIterator =
      GenericChunkIterator<cord_internal::CordTreeMutablePath>;
  using InternalChunkRange = GenericChunkRange<InternalChunkIterator>;

  InternalChunkIterator internal_chunk_begin() const;
  InternalChunkRange InternalChunks() const;

  // Helper for ForEachChunk()
  static void ForEachChunkAux(
      absl::cord_internal::CordRep* rep,
      absl::FunctionRef<void(absl::string_view)> callback);

  // The destructor for non-empty Cords.
  void DestroyCordSlow();

  // Out-of-line implementation of slower parts of logic.
  void CopyToArraySlowPath(char* dst) const;
  int CompareSlowPath(absl::string_view rhs, size_t compared_size,
                      size_t size_to_compare) const;
  int CompareSlowPath(const Cord& rhs, size_t compared_size,
                      size_t size_to_compare) const;
  bool EqualsImpl(absl::string_view rhs, size_t size_to_compare) const;
  bool EqualsImpl(const Cord& rhs, size_t size_to_compare) const;
  int CompareImpl(const Cord& rhs) const;

  template <typename ResultType, typename RHS>
  friend ResultType GenericCompare(const Cord& lhs, const RHS& rhs,
                                   size_t size_to_compare);
  static absl::string_view GetFirstChunk(const Cord& c);
  static absl::string_view GetFirstChunk(absl::string_view sv);

  // Returns a new reference to contents_.tree(), or steals an existing
  // reference if called on an rvalue.
  absl::cord_internal::CordRep* TakeRep() const&;
  absl::cord_internal::CordRep* TakeRep() &&;

  // Helper for Append()
  template <typename C>
  void AppendImpl(C&& src);
};

extern template class Cord::GenericChunkIterator<
    cord_internal::CordTreeMutablePath>;
extern template class Cord::GenericChunkIterator<
    cord_internal::CordTreeDynamicPath>;

ABSL_NAMESPACE_END
}  // namespace absl

namespace absl {
ABSL_NAMESPACE_BEGIN

// allow a Cord to be logged
extern std::ostream& operator<<(std::ostream& out, const Cord& cord);

// ------------------------------------------------------------------
// Internal details follow.  Clients should ignore.

namespace cord_internal {

// Fast implementation of memmove for up to 15 bytes. This implementation is
// safe for overlapping regions. If nullify_tail is true, the destination is
// padded with '\0' up to 16 bytes.
inline void SmallMemmove(char* dst, const char* src, size_t n,
                         bool nullify_tail = false) {
  if (n >= 8) {
    assert(n <= 16);
    uint64_t buf1;
    uint64_t buf2;
    memcpy(&buf1, src, 8);
    memcpy(&buf2, src + n - 8, 8);
    if (nullify_tail) {
      memset(dst + 8, 0, 8);
    }
    memcpy(dst, &buf1, 8);
    memcpy(dst + n - 8, &buf2, 8);
  } else if (n >= 4) {
    uint32_t buf1;
    uint32_t buf2;
    memcpy(&buf1, src, 4);
    memcpy(&buf2, src + n - 4, 4);
    if (nullify_tail) {
      memset(dst + 4, 0, 4);
      memset(dst + 8, 0, 8);
    }
    memcpy(dst, &buf1, 4);
    memcpy(dst + n - 4, &buf2, 4);
  } else {
    if (n != 0) {
      dst[0] = src[0];
      dst[n / 2] = src[n / 2];
      dst[n - 1] = src[n - 1];
    }
    if (nullify_tail) {
      memset(dst + 8, 0, 8);
      memset(dst + n, 0, 8);
    }
  }
}

struct ExternalRepReleaserPair {
  CordRep* rep;
  void* releaser_address;
};

// Allocates a new external `CordRep` and returns a pointer to it and a pointer
// to `releaser_size` bytes where the desired releaser can be constructed.
// Expects `data` to be non-empty.
ExternalRepReleaserPair NewExternalWithUninitializedReleaser(
    absl::string_view data, ExternalReleaserInvoker invoker,
    size_t releaser_size);

struct Rank1 {};
struct Rank0 : Rank1 {};

template <typename Releaser, typename = ::absl::base_internal::InvokeT<
                                 Releaser, absl::string_view>>
void InvokeReleaser(Rank0, Releaser&& releaser, absl::string_view data) {
  ::absl::base_internal::Invoke(std::forward<Releaser>(releaser), data);
}

template <typename Releaser,
          typename = ::absl::base_internal::InvokeT<Releaser>>
void InvokeReleaser(Rank1, Releaser&& releaser, absl::string_view) {
  ::absl::base_internal::Invoke(std::forward<Releaser>(releaser));
}

// Creates a new `CordRep` that owns `data` and `releaser` and returns a pointer
// to it, or `nullptr` if `data` was empty.
template <typename Releaser>
// NOLINTNEXTLINE - suppress clang-tidy raw pointer return.
CordRep* NewExternalRep(absl::string_view data, Releaser&& releaser) {
  static_assert(
#if defined(__STDCPP_DEFAULT_NEW_ALIGNMENT__)
      alignof(Releaser) <= __STDCPP_DEFAULT_NEW_ALIGNMENT__,
#else
      alignof(Releaser) <= alignof(max_align_t),
#endif
      "Releasers with alignment requirement greater than what is returned by "
      "default `::operator new()` are not supported.");

  using ReleaserType = absl::decay_t<Releaser>;
  if (data.empty()) {
    // Never create empty external nodes.
    InvokeReleaser(Rank0{}, ReleaserType(std::forward<Releaser>(releaser)),
                   data);
    return nullptr;
  }

  auto releaser_invoker = [](void* type_erased_releaser, absl::string_view d) {
    auto* my_releaser = static_cast<ReleaserType*>(type_erased_releaser);
    InvokeReleaser(Rank0{}, std::move(*my_releaser), d);
    my_releaser->~ReleaserType();
    return sizeof(Releaser);
  };

  ExternalRepReleaserPair external = NewExternalWithUninitializedReleaser(
      data, releaser_invoker, sizeof(releaser));
  ::new (external.releaser_address)
      ReleaserType(std::forward<Releaser>(releaser));
  return external.rep;
}

// Overload for function reference types that dispatches using a function
// pointer because there are no `alignof()` or `sizeof()` a function reference.
// NOLINTNEXTLINE - suppress clang-tidy raw pointer return.
inline CordRep* NewExternalRep(absl::string_view data,
                               void (&releaser)(absl::string_view)) {
  return NewExternalRep(data, &releaser);
}

}  // namespace cord_internal

template <typename Releaser>
Cord MakeCordFromExternal(absl::string_view data, Releaser&& releaser) {
  Cord cord;
  cord.contents_.set_tree(::absl::cord_internal::NewExternalRep(
      data, std::forward<Releaser>(releaser)));
  return cord;
}

inline Cord::InlineRep::InlineRep(const Cord::InlineRep& src) {
  cord_internal::SmallMemmove(data_, src.data_, sizeof(data_));
}

inline Cord::InlineRep::InlineRep(Cord::InlineRep&& src) {
  memcpy(data_, src.data_, sizeof(data_));
  memset(src.data_, 0, sizeof(data_));
}

inline Cord::InlineRep& Cord::InlineRep::operator=(const Cord::InlineRep& src) {
  if (this == &src) {
    return *this;
  }
  if (!is_tree() && !src.is_tree()) {
    cord_internal::SmallMemmove(data_, src.data_, sizeof(data_));
    return *this;
  }
  AssignSlow(src);
  return *this;
}

inline Cord::InlineRep& Cord::InlineRep::operator=(
    Cord::InlineRep&& src) noexcept {
  if (is_tree()) {
    ClearSlow();
  }
  memcpy(data_, src.data_, sizeof(data_));
  memset(src.data_, 0, sizeof(data_));
  return *this;
}

inline void Cord::InlineRep::Swap(Cord::InlineRep* rhs) {
  if (rhs == this) {
    return;
  }

  Cord::InlineRep tmp;
  cord_internal::SmallMemmove(tmp.data_, data_, sizeof(data_));
  cord_internal::SmallMemmove(data_, rhs->data_, sizeof(data_));
  cord_internal::SmallMemmove(rhs->data_, tmp.data_, sizeof(data_));
}

inline const char* Cord::InlineRep::data() const {
  return is_tree() ? nullptr : data_;
}

inline absl::cord_internal::CordRep* Cord::InlineRep::tree() const {
  if (is_tree()) {
    absl::cord_internal::CordRep* rep;
    memcpy(&rep, data_, sizeof(rep));
    return rep;
  } else {
    return nullptr;
  }
}

inline bool Cord::InlineRep::empty() const { return data_[kMaxInline] == 0; }

inline size_t Cord::InlineRep::size() const {
  const char tag = data_[kMaxInline];
  if (tag <= kMaxInline) return tag;
  return static_cast<size_t>(tree()->length);
}

inline void Cord::InlineRep::set_tree(absl::cord_internal::CordRep* rep) {
  if (rep == nullptr) {
    memset(data_, 0, sizeof(data_));
  } else {
    bool was_tree = is_tree();
    memcpy(data_, &rep, sizeof(rep));
    memset(data_ + sizeof(rep), 0, sizeof(data_) - sizeof(rep) - 1);
    if (!was_tree) {
      data_[kMaxInline] = kTreeFlag;
    }
  }
}

inline void Cord::InlineRep::replace_tree(absl::cord_internal::CordRep* rep) {
  ABSL_ASSERT(is_tree());
  if (ABSL_PREDICT_FALSE(rep == nullptr)) {
    set_tree(rep);
    return;
  }
  memcpy(data_, &rep, sizeof(rep));
  memset(data_ + sizeof(rep), 0, sizeof(data_) - sizeof(rep) - 1);
}

inline absl::cord_internal::CordRep* Cord::InlineRep::clear() {
  const char tag = data_[kMaxInline];
  absl::cord_internal::CordRep* result = nullptr;
  if (tag > kMaxInline) {
    memcpy(&result, data_, sizeof(result));
  }
  memset(data_, 0, sizeof(data_));  // Clear the cord
  return result;
}

inline void Cord::InlineRep::CopyToArray(char* dst) const {
  assert(!is_tree());
  size_t n = data_[kMaxInline];
  assert(n != 0);
  cord_internal::SmallMemmove(dst, data_, n);
}

constexpr inline Cord::Cord() noexcept {}

inline Cord& Cord::operator=(const Cord& x) {
  contents_ = x.contents_;
  return *this;
}

inline Cord::Cord(Cord&& src) noexcept : contents_(std::move(src.contents_)) {}

inline Cord& Cord::operator=(Cord&& x) noexcept {
  contents_ = std::move(x.contents_);
  return *this;
}

template <typename T, Cord::EnableIfString<T>>
inline Cord& Cord::operator=(T&& src) {
  *this = absl::string_view(src);
  return *this;
}

inline size_t Cord::size() const {
  // Length is 1st field in str.rep_
  return contents_.size();
}

inline bool Cord::empty() const { return contents_.empty(); }

inline size_t Cord::EstimatedMemoryUsage() const {
  size_t result = sizeof(Cord);
  if (const absl::cord_internal::CordRep* rep = contents_.tree()) {
    result += MemoryUsageAux(rep);
  }
  return result;
}

inline absl::optional<absl::string_view> Cord::TryFlat() const {
  absl::cord_internal::CordRep* rep = contents_.tree();
  if (rep == nullptr) {
    return absl::string_view(contents_.data(), contents_.size());
  }
  absl::string_view fragment;
  if (GetFlatAux(rep, &fragment)) {
    return fragment;
  }
  return absl::nullopt;
}

inline absl::string_view Cord::Flatten() {
  absl::cord_internal::CordRep* rep = contents_.tree();
  if (rep == nullptr) {
    return absl::string_view(contents_.data(), contents_.size());
  } else {
    absl::string_view already_flat_contents;
    if (GetFlatAux(rep, &already_flat_contents)) {
      return already_flat_contents;
    }
  }
  return FlattenSlowPath();
}

inline void Cord::Append(absl::string_view src) {
  contents_.AppendArray(src.data(), src.size());
}

template <typename T, Cord::EnableIfString<T>>
inline void Cord::Append(T&& src) {
  // Note that this function reserves the right to reuse the `string&&`'s
  // memory and that it will do so in the future.
  Append(absl::string_view(src));
}

template <typename T, Cord::EnableIfString<T>>
inline void Cord::Prepend(T&& src) {
  // Note that this function reserves the right to reuse the `string&&`'s
  // memory and that it will do so in the future.
  Prepend(absl::string_view(src));
}

inline int Cord::Compare(const Cord& rhs) const {
  if (!contents_.is_tree() && !rhs.contents_.is_tree()) {
    return contents_.BitwiseCompare(rhs.contents_);
  }

  return CompareImpl(rhs);
}

// Does 'this' cord start/end with rhs
inline bool Cord::StartsWith(const Cord& rhs) const {
  if (contents_.IsSame(rhs.contents_)) return true;
  size_t rhs_size = rhs.size();
  if (size() < rhs_size) return false;
  return EqualsImpl(rhs, rhs_size);
}

inline bool Cord::StartsWith(absl::string_view rhs) const {
  size_t rhs_size = rhs.size();
  if (size() < rhs_size) return false;
  return EqualsImpl(rhs, rhs_size);
}

template <typename StorageType>
inline Cord::GenericChunkIterator<StorageType>::GenericChunkIterator(
    const Cord* cord)
    : bytes_remaining_(cord->size()) {
  if (cord->empty()) return;
  if (cord->contents_.is_tree()) {
    stack_of_right_children_.push_back(cord->contents_.tree());
    operator++();
  } else {
    current_chunk_ = absl::string_view(cord->contents_.data(), cord->size());
  }
}

template <typename StorageType>
inline Cord::GenericChunkIterator<StorageType>
Cord::GenericChunkIterator<StorageType>::operator++(int) {
  GenericChunkIterator tmp(*this);
  operator++();
  return tmp;
}

template <typename StorageType>
inline bool Cord::GenericChunkIterator<StorageType>::operator==(
    const GenericChunkIterator<StorageType>& other) const {
  return bytes_remaining_ == other.bytes_remaining_;
}

template <typename StorageType>
inline bool Cord::GenericChunkIterator<StorageType>::operator!=(
    const GenericChunkIterator<StorageType>& other) const {
  return !(*this == other);
}

template <typename StorageType>
inline typename Cord::GenericChunkIterator<StorageType>::reference
Cord::GenericChunkIterator<StorageType>::operator*() const {
  ABSL_HARDENING_ASSERT(bytes_remaining_ != 0);
  return current_chunk_;
}

template <typename StorageType>
inline typename Cord::GenericChunkIterator<StorageType>::pointer
Cord::GenericChunkIterator<StorageType>::operator->() const {
  ABSL_HARDENING_ASSERT(bytes_remaining_ != 0);
  return &current_chunk_;
}

template <typename StorageType>
inline void Cord::GenericChunkIterator<StorageType>::RemoveChunkPrefix(
    size_t n) {
  assert(n < current_chunk_.size());
  current_chunk_.remove_prefix(n);
  bytes_remaining_ -= n;
}

template <typename StorageType>
inline void Cord::GenericChunkIterator<StorageType>::AdvanceBytes(size_t n) {
  if (ABSL_PREDICT_TRUE(n < current_chunk_.size())) {
    RemoveChunkPrefix(n);
  } else if (n != 0) {
    AdvanceBytesSlowPath(n);
  }
}

inline Cord::ChunkIterator Cord::chunk_begin() const {
  return ChunkIterator(this);
}

inline Cord::ChunkIterator Cord::chunk_end() const { return ChunkIterator(); }

inline Cord::ChunkRange Cord::Chunks() const { return ChunkRange(this); }

inline Cord::CharIterator& Cord::CharIterator::operator++() {
  if (ABSL_PREDICT_TRUE(chunk_iterator_->size() > 1)) {
    chunk_iterator_.RemoveChunkPrefix(1);
  } else {
    ++chunk_iterator_;
  }
  return *this;
}

inline Cord::CharIterator Cord::CharIterator::operator++(int) {
  CharIterator tmp(*this);
  operator++();
  return tmp;
}

inline bool Cord::CharIterator::operator==(const CharIterator& other) const {
  return chunk_iterator_ == other.chunk_iterator_;
}

inline bool Cord::CharIterator::operator!=(const CharIterator& other) const {
  return !(*this == other);
}

inline Cord::CharIterator::reference Cord::CharIterator::operator*() const {
  return *chunk_iterator_->data();
}

inline Cord::CharIterator::pointer Cord::CharIterator::operator->() const {
  return chunk_iterator_->data();
}

inline Cord Cord::AdvanceAndRead(CharIterator* it, size_t n_bytes) {
  assert(it != nullptr);
  return it->chunk_iterator_.AdvanceAndReadBytes(n_bytes);
}

inline void Cord::Advance(CharIterator* it, size_t n_bytes) {
  assert(it != nullptr);
  it->chunk_iterator_.AdvanceBytes(n_bytes);
}

inline absl::string_view Cord::ChunkRemaining(const CharIterator& it) {
  return *it.chunk_iterator_;
}

inline Cord::CharIterator Cord::char_begin() const {
  return CharIterator(this);
}

inline Cord::CharIterator Cord::char_end() const { return CharIterator(); }

inline Cord::CharIterator Cord::CharRange::begin() const {
  return cord_->char_begin();
}

inline Cord::CharIterator Cord::CharRange::end() const {
  return cord_->char_end();
}

inline Cord::CharRange Cord::Chars() const { return CharRange(this); }

inline void Cord::ForEachChunk(
    absl::FunctionRef<void(absl::string_view)> callback) const {
  absl::cord_internal::CordRep* rep = contents_.tree();
  if (rep == nullptr) {
    callback(absl::string_view(contents_.data(), contents_.size()));
  } else {
    return ForEachChunkAux(rep, callback);
  }
}

// Nonmember Cord-to-Cord relational operarators.
inline bool operator==(const Cord& lhs, const Cord& rhs) {
  if (lhs.contents_.IsSame(rhs.contents_)) return true;
  size_t rhs_size = rhs.size();
  if (lhs.size() != rhs_size) return false;
  return lhs.EqualsImpl(rhs, rhs_size);
}

inline bool operator!=(const Cord& x, const Cord& y) { return !(x == y); }
inline bool operator<(const Cord& x, const Cord& y) {
  return x.Compare(y) < 0;
}
inline bool operator>(const Cord& x, const Cord& y) {
  return x.Compare(y) > 0;
}
inline bool operator<=(const Cord& x, const Cord& y) {
  return x.Compare(y) <= 0;
}
inline bool operator>=(const Cord& x, const Cord& y) {
  return x.Compare(y) >= 0;
}

// Nonmember Cord-to-absl::string_view relational operators.
//
// Due to implicit conversions, these also enable comparisons of Cord with
// with std::string, ::string, and const char*.
inline bool operator==(const Cord& lhs, absl::string_view rhs) {
  size_t lhs_size = lhs.size();
  size_t rhs_size = rhs.size();
  if (lhs_size != rhs_size) return false;
  return lhs.EqualsImpl(rhs, rhs_size);
}

inline bool operator==(absl::string_view x, const Cord& y) { return y == x; }
inline bool operator!=(const Cord& x, absl::string_view y) { return !(x == y); }
inline bool operator!=(absl::string_view x, const Cord& y) { return !(x == y); }
inline bool operator<(const Cord& x, absl::string_view y) {
  return x.Compare(y) < 0;
}
inline bool operator<(absl::string_view x, const Cord& y) {
  return y.Compare(x) > 0;
}
inline bool operator>(const Cord& x, absl::string_view y) { return y < x; }
inline bool operator>(absl::string_view x, const Cord& y) { return y < x; }
inline bool operator<=(const Cord& x, absl::string_view y) { return !(y < x); }
inline bool operator<=(absl::string_view x, const Cord& y) { return !(y < x); }
inline bool operator>=(const Cord& x, absl::string_view y) { return !(x < y); }
inline bool operator>=(absl::string_view x, const Cord& y) { return !(x < y); }

// Overload of swap for Cord. The use of non-const references is
// required. :(
inline void swap(Cord& x, Cord& y) noexcept { y.contents_.Swap(&x.contents_); }

// Some internals exposed to test code.
namespace strings_internal {
class CordTestAccess {
 public:
  static size_t FlatOverhead();
  static size_t MaxFlatLength();
  static size_t SizeofCordRepConcat();
  static size_t SizeofCordRepExternal();
  static size_t SizeofCordRepSubstring();
  static size_t FlatTagToLength(uint8_t tag);
  static uint8_t LengthToTag(size_t s);
};
}  // namespace strings_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_STRINGS_CORD_H_
