// Copyright 2019 The Abseil Authors.
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
// File: inlined_vector.h
// -----------------------------------------------------------------------------
//
// This header file contains the declaration and definition of an "inlined
// vector" which behaves in an equivalent fashion to a `std::vector`, except
// that storage for small sequences of the vector are provided inline without
// requiring any heap allocation.
//
// An `absl::InlinedVector<T, N>` specifies the default capacity `N` as one of
// its template parameters. Instances where `size() <= N` hold contained
// elements in inline space. Typically `N` is very small so that sequences that
// are expected to be short do not require allocations.
//
// An `absl::InlinedVector` does not usually require a specific allocator. If
// the inlined vector grows beyond its initial constraints, it will need to
// allocate (as any normal `std::vector` would). This is usually performed with
// the default allocator (defined as `std::allocator<T>`). Optionally, a custom
// allocator type may be specified as `A` in `absl::InlinedVector<T, N, A>`.

#ifndef ABSL_CONTAINER_INLINED_VECTOR_H_
#define ABSL_CONTAINER_INLINED_VECTOR_H_

#include <algorithm>
#include <cassert>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <initializer_list>
#include <iterator>
#include <memory>
#include <type_traits>
#include <utility>

#include "absl/algorithm/algorithm.h"
#include "absl/base/internal/throw_delegate.h"
#include "absl/base/macros.h"
#include "absl/base/optimization.h"
#include "absl/base/port.h"
#include "absl/container/internal/inlined_vector.h"
#include "absl/memory/memory.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
// -----------------------------------------------------------------------------
// InlinedVector
// -----------------------------------------------------------------------------
//
// An `absl::InlinedVector` is designed to be a drop-in replacement for
// `std::vector` for use cases where the vector's size is sufficiently small
// that it can be inlined. If the inlined vector does grow beyond its estimated
// capacity, it will trigger an initial allocation on the heap, and will behave
// as a `std:vector`. The API of the `absl::InlinedVector` within this file is
// designed to cover the same API footprint as covered by `std::vector`.
template <typename T, size_t N, typename A = std::allocator<T>>
class InlinedVector {
  static_assert(N > 0, "`absl::InlinedVector` requires an inlined capacity.");

  using Storage = inlined_vector_internal::Storage<T, N, A>;

  using AllocatorTraits = typename Storage::AllocatorTraits;
  using RValueReference = typename Storage::RValueReference;
  using MoveIterator = typename Storage::MoveIterator;
  using IsMemcpyOk = typename Storage::IsMemcpyOk;

  template <typename Iterator>
  using IteratorValueAdapter =
      typename Storage::template IteratorValueAdapter<Iterator>;
  using CopyValueAdapter = typename Storage::CopyValueAdapter;
  using DefaultValueAdapter = typename Storage::DefaultValueAdapter;

  template <typename Iterator>
  using EnableIfAtLeastForwardIterator = absl::enable_if_t<
      inlined_vector_internal::IsAtLeastForwardIterator<Iterator>::value>;
  template <typename Iterator>
  using DisableIfAtLeastForwardIterator = absl::enable_if_t<
      !inlined_vector_internal::IsAtLeastForwardIterator<Iterator>::value>;

 public:
  using allocator_type = typename Storage::allocator_type;
  using value_type = typename Storage::value_type;
  using pointer = typename Storage::pointer;
  using const_pointer = typename Storage::const_pointer;
  using size_type = typename Storage::size_type;
  using difference_type = typename Storage::difference_type;
  using reference = typename Storage::reference;
  using const_reference = typename Storage::const_reference;
  using iterator = typename Storage::iterator;
  using const_iterator = typename Storage::const_iterator;
  using reverse_iterator = typename Storage::reverse_iterator;
  using const_reverse_iterator = typename Storage::const_reverse_iterator;

  // ---------------------------------------------------------------------------
  // InlinedVector Constructors and Destructor
  // ---------------------------------------------------------------------------

  // Creates an empty inlined vector with a value-initialized allocator.
  InlinedVector() noexcept(noexcept(allocator_type())) : storage_() {}

  // Creates an empty inlined vector with a copy of `alloc`.
  explicit InlinedVector(const allocator_type& alloc) noexcept
      : storage_(alloc) {}

  // Creates an inlined vector with `n` copies of `value_type()`.
  explicit InlinedVector(size_type n,
                         const allocator_type& alloc = allocator_type())
      : storage_(alloc) {
    storage_.Initialize(DefaultValueAdapter(), n);
  }

  // Creates an inlined vector with `n` copies of `v`.
  InlinedVector(size_type n, const_reference v,
                const allocator_type& alloc = allocator_type())
      : storage_(alloc) {
    storage_.Initialize(CopyValueAdapter(v), n);
  }

  // Creates an inlined vector with copies of the elements of `list`.
  InlinedVector(std::initializer_list<value_type> list,
                const allocator_type& alloc = allocator_type())
      : InlinedVector(list.begin(), list.end(), alloc) {}

  // Creates an inlined vector with elements constructed from the provided
  // forward iterator range [`first`, `last`).
  //
  // NOTE: the `enable_if` prevents ambiguous interpretation between a call to
  // this constructor with two integral arguments and a call to the above
  // `InlinedVector(size_type, const_reference)` constructor.
  template <typename ForwardIterator,
            EnableIfAtLeastForwardIterator<ForwardIterator>* = nullptr>
  InlinedVector(ForwardIterator first, ForwardIterator last,
                const allocator_type& alloc = allocator_type())
      : storage_(alloc) {
    storage_.Initialize(IteratorValueAdapter<ForwardIterator>(first),
                        std::distance(first, last));
  }

  // Creates an inlined vector with elements constructed from the provided input
  // iterator range [`first`, `last`).
  template <typename InputIterator,
            DisableIfAtLeastForwardIterator<InputIterator>* = nullptr>
  InlinedVector(InputIterator first, InputIterator last,
                const allocator_type& alloc = allocator_type())
      : storage_(alloc) {
    std::copy(first, last, std::back_inserter(*this));
  }

  // Creates an inlined vector by copying the contents of `other` using
  // `other`'s allocator.
  InlinedVector(const InlinedVector& other)
      : InlinedVector(other, *other.storage_.GetAllocPtr()) {}

  // Creates an inlined vector by copying the contents of `other` using `alloc`.
  InlinedVector(const InlinedVector& other, const allocator_type& alloc)
      : storage_(alloc) {
    if (IsMemcpyOk::value && !other.storage_.GetIsAllocated()) {
      storage_.MemcpyFrom(other.storage_);
    } else {
      storage_.Initialize(IteratorValueAdapter<const_pointer>(other.data()),
                          other.size());
    }
  }

  // Creates an inlined vector by moving in the contents of `other` without
  // allocating. If `other` contains allocated memory, the newly-created inlined
  // vector will take ownership of that memory. However, if `other` does not
  // contain allocated memory, the newly-created inlined vector will perform
  // element-wise move construction of the contents of `other`.
  //
  // NOTE: since no allocation is performed for the inlined vector in either
  // case, the `noexcept(...)` specification depends on whether moving the
  // underlying objects can throw. It is assumed assumed that...
  //  a) move constructors should only throw due to allocation failure.
  //  b) if `value_type`'s move constructor allocates, it uses the same
  //     allocation function as the inlined vector's allocator.
  // Thus, the move constructor is non-throwing if the allocator is non-throwing
  // or `value_type`'s move constructor is specified as `noexcept`.
  InlinedVector(InlinedVector&& other) noexcept(
      absl::allocator_is_nothrow<allocator_type>::value ||
      std::is_nothrow_move_constructible<value_type>::value)
      : storage_(*other.storage_.GetAllocPtr()) {
    if (IsMemcpyOk::value) {
      storage_.MemcpyFrom(other.storage_);

      other.storage_.SetInlinedSize(0);
    } else if (other.storage_.GetIsAllocated()) {
      storage_.SetAllocatedData(other.storage_.GetAllocatedData(),
                                other.storage_.GetAllocatedCapacity());
      storage_.SetAllocatedSize(other.storage_.GetSize());

      other.storage_.SetInlinedSize(0);
    } else {
      IteratorValueAdapter<MoveIterator> other_values(
          MoveIterator(other.storage_.GetInlinedData()));

      inlined_vector_internal::ConstructElements(
          storage_.GetAllocPtr(), storage_.GetInlinedData(), &other_values,
          other.storage_.GetSize());

      storage_.SetInlinedSize(other.storage_.GetSize());
    }
  }

  // Creates an inlined vector by moving in the contents of `other` with a copy
  // of `alloc`.
  //
  // NOTE: if `other`'s allocator is not equal to `alloc`, even if `other`
  // contains allocated memory, this move constructor will still allocate. Since
  // allocation is performed, this constructor can only be `noexcept` if the
  // specified allocator is also `noexcept`.
  InlinedVector(InlinedVector&& other, const allocator_type& alloc) noexcept(
      absl::allocator_is_nothrow<allocator_type>::value)
      : storage_(alloc) {
    if (IsMemcpyOk::value) {
      storage_.MemcpyFrom(other.storage_);

      other.storage_.SetInlinedSize(0);
    } else if ((*storage_.GetAllocPtr() == *other.storage_.GetAllocPtr()) &&
               other.storage_.GetIsAllocated()) {
      storage_.SetAllocatedData(other.storage_.GetAllocatedData(),
                                other.storage_.GetAllocatedCapacity());
      storage_.SetAllocatedSize(other.storage_.GetSize());

      other.storage_.SetInlinedSize(0);
    } else {
      storage_.Initialize(
          IteratorValueAdapter<MoveIterator>(MoveIterator(other.data())),
          other.size());
    }
  }

  ~InlinedVector() {}

  // ---------------------------------------------------------------------------
  // InlinedVector Member Accessors
  // ---------------------------------------------------------------------------

  // `InlinedVector::empty()`
  //
  // Returns whether the inlined vector contains no elements.
  bool empty() const noexcept { return !size(); }

  // `InlinedVector::size()`
  //
  // Returns the number of elements in the inlined vector.
  size_type size() const noexcept { return storage_.GetSize(); }

  // `InlinedVector::max_size()`
  //
  // Returns the maximum number of elements the inlined vector can hold.
  size_type max_size() const noexcept {
    // One bit of the size storage is used to indicate whether the inlined
    // vector contains allocated memory. As a result, the maximum size that the
    // inlined vector can express is half of the max for `size_type`.
    return (std::numeric_limits<size_type>::max)() / 2;
  }

  // `InlinedVector::capacity()`
  //
  // Returns the number of elements that could be stored in the inlined vector
  // without requiring a reallocation.
  //
  // NOTE: for most inlined vectors, `capacity()` should be equal to the
  // template parameter `N`. For inlined vectors which exceed this capacity,
  // they will no longer be inlined and `capacity()` will equal the capactity of
  // the allocated memory.
  size_type capacity() const noexcept {
    return storage_.GetIsAllocated() ? storage_.GetAllocatedCapacity()
                                     : storage_.GetInlinedCapacity();
  }

  // `InlinedVector::data()`
  //
  // Returns a `pointer` to the elements of the inlined vector. This pointer
  // can be used to access and modify the contained elements.
  //
  // NOTE: only elements within [`data()`, `data() + size()`) are valid.
  pointer data() noexcept {
    return storage_.GetIsAllocated() ? storage_.GetAllocatedData()
                                     : storage_.GetInlinedData();
  }

  // Overload of `InlinedVector::data()` that returns a `const_pointer` to the
  // elements of the inlined vector. This pointer can be used to access but not
  // modify the contained elements.
  //
  // NOTE: only elements within [`data()`, `data() + size()`) are valid.
  const_pointer data() const noexcept {
    return storage_.GetIsAllocated() ? storage_.GetAllocatedData()
                                     : storage_.GetInlinedData();
  }

  // `InlinedVector::operator[](...)`
  //
  // Returns a `reference` to the `i`th element of the inlined vector.
  reference operator[](size_type i) {
    ABSL_HARDENING_ASSERT(i < size());
    return data()[i];
  }

  // Overload of `InlinedVector::operator[](...)` that returns a
  // `const_reference` to the `i`th element of the inlined vector.
  const_reference operator[](size_type i) const {
    ABSL_HARDENING_ASSERT(i < size());
    return data()[i];
  }

  // `InlinedVector::at(...)`
  //
  // Returns a `reference` to the `i`th element of the inlined vector.
  //
  // NOTE: if `i` is not within the required range of `InlinedVector::at(...)`,
  // in both debug and non-debug builds, `std::out_of_range` will be thrown.
  reference at(size_type i) {
    if (ABSL_PREDICT_FALSE(i >= size())) {
      base_internal::ThrowStdOutOfRange(
          "`InlinedVector::at(size_type)` failed bounds check");
    }
    return data()[i];
  }

  // Overload of `InlinedVector::at(...)` that returns a `const_reference` to
  // the `i`th element of the inlined vector.
  //
  // NOTE: if `i` is not within the required range of `InlinedVector::at(...)`,
  // in both debug and non-debug builds, `std::out_of_range` will be thrown.
  const_reference at(size_type i) const {
    if (ABSL_PREDICT_FALSE(i >= size())) {
      base_internal::ThrowStdOutOfRange(
          "`InlinedVector::at(size_type) const` failed bounds check");
    }
    return data()[i];
  }

  // `InlinedVector::front()`
  //
  // Returns a `reference` to the first element of the inlined vector.
  reference front() {
    ABSL_HARDENING_ASSERT(!empty());
    return at(0);
  }

  // Overload of `InlinedVector::front()` that returns a `const_reference` to
  // the first element of the inlined vector.
  const_reference front() const {
    ABSL_HARDENING_ASSERT(!empty());
    return at(0);
  }

  // `InlinedVector::back()`
  //
  // Returns a `reference` to the last element of the inlined vector.
  reference back() {
    ABSL_HARDENING_ASSERT(!empty());
    return at(size() - 1);
  }

  // Overload of `InlinedVector::back()` that returns a `const_reference` to the
  // last element of the inlined vector.
  const_reference back() const {
    ABSL_HARDENING_ASSERT(!empty());
    return at(size() - 1);
  }

  // `InlinedVector::begin()`
  //
  // Returns an `iterator` to the beginning of the inlined vector.
  iterator begin() noexcept { return data(); }

  // Overload of `InlinedVector::begin()` that returns a `const_iterator` to
  // the beginning of the inlined vector.
  const_iterator begin() const noexcept { return data(); }

  // `InlinedVector::end()`
  //
  // Returns an `iterator` to the end of the inlined vector.
  iterator end() noexcept { return data() + size(); }

  // Overload of `InlinedVector::end()` that returns a `const_iterator` to the
  // end of the inlined vector.
  const_iterator end() const noexcept { return data() + size(); }

  // `InlinedVector::cbegin()`
  //
  // Returns a `const_iterator` to the beginning of the inlined vector.
  const_iterator cbegin() const noexcept { return begin(); }

  // `InlinedVector::cend()`
  //
  // Returns a `const_iterator` to the end of the inlined vector.
  const_iterator cend() const noexcept { return end(); }

  // `InlinedVector::rbegin()`
  //
  // Returns a `reverse_iterator` from the end of the inlined vector.
  reverse_iterator rbegin() noexcept { return reverse_iterator(end()); }

  // Overload of `InlinedVector::rbegin()` that returns a
  // `const_reverse_iterator` from the end of the inlined vector.
  const_reverse_iterator rbegin() const noexcept {
    return const_reverse_iterator(end());
  }

  // `InlinedVector::rend()`
  //
  // Returns a `reverse_iterator` from the beginning of the inlined vector.
  reverse_iterator rend() noexcept { return reverse_iterator(begin()); }

  // Overload of `InlinedVector::rend()` that returns a `const_reverse_iterator`
  // from the beginning of the inlined vector.
  const_reverse_iterator rend() const noexcept {
    return const_reverse_iterator(begin());
  }

  // `InlinedVector::crbegin()`
  //
  // Returns a `const_reverse_iterator` from the end of the inlined vector.
  const_reverse_iterator crbegin() const noexcept { return rbegin(); }

  // `InlinedVector::crend()`
  //
  // Returns a `const_reverse_iterator` from the beginning of the inlined
  // vector.
  const_reverse_iterator crend() const noexcept { return rend(); }

  // `InlinedVector::get_allocator()`
  //
  // Returns a copy of the inlined vector's allocator.
  allocator_type get_allocator() const { return *storage_.GetAllocPtr(); }

  // ---------------------------------------------------------------------------
  // InlinedVector Member Mutators
  // ---------------------------------------------------------------------------

  // `InlinedVector::operator=(...)`
  //
  // Replaces the elements of the inlined vector with copies of the elements of
  // `list`.
  InlinedVector& operator=(std::initializer_list<value_type> list) {
    assign(list.begin(), list.end());

    return *this;
  }

  // Overload of `InlinedVector::operator=(...)` that replaces the elements of
  // the inlined vector with copies of the elements of `other`.
  InlinedVector& operator=(const InlinedVector& other) {
    if (ABSL_PREDICT_TRUE(this != std::addressof(other))) {
      const_pointer other_data = other.data();
      assign(other_data, other_data + other.size());
    }

    return *this;
  }

  // Overload of `InlinedVector::operator=(...)` that moves the elements of
  // `other` into the inlined vector.
  //
  // NOTE: as a result of calling this overload, `other` is left in a valid but
  // unspecified state.
  InlinedVector& operator=(InlinedVector&& other) {
    if (ABSL_PREDICT_TRUE(this != std::addressof(other))) {
      if (IsMemcpyOk::value || other.storage_.GetIsAllocated()) {
        inlined_vector_internal::DestroyElements(storage_.GetAllocPtr(), data(),
                                                 size());
        storage_.DeallocateIfAllocated();
        storage_.MemcpyFrom(other.storage_);

        other.storage_.SetInlinedSize(0);
      } else {
        storage_.Assign(IteratorValueAdapter<MoveIterator>(
                            MoveIterator(other.storage_.GetInlinedData())),
                        other.size());
      }
    }

    return *this;
  }

  // `InlinedVector::assign(...)`
  //
  // Replaces the contents of the inlined vector with `n` copies of `v`.
  void assign(size_type n, const_reference v) {
    storage_.Assign(CopyValueAdapter(v), n);
  }

  // Overload of `InlinedVector::assign(...)` that replaces the contents of the
  // inlined vector with copies of the elements of `list`.
  void assign(std::initializer_list<value_type> list) {
    assign(list.begin(), list.end());
  }

  // Overload of `InlinedVector::assign(...)` to replace the contents of the
  // inlined vector with the range [`first`, `last`).
  //
  // NOTE: this overload is for iterators that are "forward" category or better.
  template <typename ForwardIterator,
            EnableIfAtLeastForwardIterator<ForwardIterator>* = nullptr>
  void assign(ForwardIterator first, ForwardIterator last) {
    storage_.Assign(IteratorValueAdapter<ForwardIterator>(first),
                    std::distance(first, last));
  }

  // Overload of `InlinedVector::assign(...)` to replace the contents of the
  // inlined vector with the range [`first`, `last`).
  //
  // NOTE: this overload is for iterators that are "input" category.
  template <typename InputIterator,
            DisableIfAtLeastForwardIterator<InputIterator>* = nullptr>
  void assign(InputIterator first, InputIterator last) {
    size_type i = 0;
    for (; i < size() && first != last; ++i, static_cast<void>(++first)) {
      at(i) = *first;
    }

    erase(data() + i, data() + size());
    std::copy(first, last, std::back_inserter(*this));
  }

  // `InlinedVector::resize(...)`
  //
  // Resizes the inlined vector to contain `n` elements.
  //
  // NOTE: if `n` is smaller than `size()`, extra elements are destroyed. If `n`
  // is larger than `size()`, new elements are value-initialized.
  void resize(size_type n) { storage_.Resize(DefaultValueAdapter(), n); }

  // Overload of `InlinedVector::resize(...)` that resizes the inlined vector to
  // contain `n` elements.
  //
  // NOTE: if `n` is smaller than `size()`, extra elements are destroyed. If `n`
  // is larger than `size()`, new elements are copied-constructed from `v`.
  void resize(size_type n, const_reference v) {
    storage_.Resize(CopyValueAdapter(v), n);
  }

  // `InlinedVector::insert(...)`
  //
  // Inserts a copy of `v` at `pos`, returning an `iterator` to the newly
  // inserted element.
  iterator insert(const_iterator pos, const_reference v) {
    return emplace(pos, v);
  }

  // Overload of `InlinedVector::insert(...)` that inserts `v` at `pos` using
  // move semantics, returning an `iterator` to the newly inserted element.
  iterator insert(const_iterator pos, RValueReference v) {
    return emplace(pos, std::move(v));
  }

  // Overload of `InlinedVector::insert(...)` that inserts `n` contiguous copies
  // of `v` starting at `pos`, returning an `iterator` pointing to the first of
  // the newly inserted elements.
  iterator insert(const_iterator pos, size_type n, const_reference v) {
    ABSL_HARDENING_ASSERT(pos >= begin());
    ABSL_HARDENING_ASSERT(pos <= end());

    if (ABSL_PREDICT_TRUE(n != 0)) {
      value_type dealias = v;
      return storage_.Insert(pos, CopyValueAdapter(dealias), n);
    } else {
      return const_cast<iterator>(pos);
    }
  }

  // Overload of `InlinedVector::insert(...)` that inserts copies of the
  // elements of `list` starting at `pos`, returning an `iterator` pointing to
  // the first of the newly inserted elements.
  iterator insert(const_iterator pos, std::initializer_list<value_type> list) {
    return insert(pos, list.begin(), list.end());
  }

  // Overload of `InlinedVector::insert(...)` that inserts the range [`first`,
  // `last`) starting at `pos`, returning an `iterator` pointing to the first
  // of the newly inserted elements.
  //
  // NOTE: this overload is for iterators that are "forward" category or better.
  template <typename ForwardIterator,
            EnableIfAtLeastForwardIterator<ForwardIterator>* = nullptr>
  iterator insert(const_iterator pos, ForwardIterator first,
                  ForwardIterator last) {
    ABSL_HARDENING_ASSERT(pos >= begin());
    ABSL_HARDENING_ASSERT(pos <= end());

    if (ABSL_PREDICT_TRUE(first != last)) {
      return storage_.Insert(pos, IteratorValueAdapter<ForwardIterator>(first),
                             std::distance(first, last));
    } else {
      return const_cast<iterator>(pos);
    }
  }

  // Overload of `InlinedVector::insert(...)` that inserts the range [`first`,
  // `last`) starting at `pos`, returning an `iterator` pointing to the first
  // of the newly inserted elements.
  //
  // NOTE: this overload is for iterators that are "input" category.
  template <typename InputIterator,
            DisableIfAtLeastForwardIterator<InputIterator>* = nullptr>
  iterator insert(const_iterator pos, InputIterator first, InputIterator last) {
    ABSL_HARDENING_ASSERT(pos >= begin());
    ABSL_HARDENING_ASSERT(pos <= end());

    size_type index = std::distance(cbegin(), pos);
    for (size_type i = index; first != last; ++i, static_cast<void>(++first)) {
      insert(data() + i, *first);
    }

    return iterator(data() + index);
  }

  // `InlinedVector::emplace(...)`
  //
  // Constructs and inserts an element using `args...` in the inlined vector at
  // `pos`, returning an `iterator` pointing to the newly emplaced element.
  template <typename... Args>
  iterator emplace(const_iterator pos, Args&&... args) {
    ABSL_HARDENING_ASSERT(pos >= begin());
    ABSL_HARDENING_ASSERT(pos <= end());

    value_type dealias(std::forward<Args>(args)...);
    return storage_.Insert(pos,
                           IteratorValueAdapter<MoveIterator>(
                               MoveIterator(std::addressof(dealias))),
                           1);
  }

  // `InlinedVector::emplace_back(...)`
  //
  // Constructs and inserts an element using `args...` in the inlined vector at
  // `end()`, returning a `reference` to the newly emplaced element.
  template <typename... Args>
  reference emplace_back(Args&&... args) {
    return storage_.EmplaceBack(std::forward<Args>(args)...);
  }

  // `InlinedVector::push_back(...)`
  //
  // Inserts a copy of `v` in the inlined vector at `end()`.
  void push_back(const_reference v) { static_cast<void>(emplace_back(v)); }

  // Overload of `InlinedVector::push_back(...)` for inserting `v` at `end()`
  // using move semantics.
  void push_back(RValueReference v) {
    static_cast<void>(emplace_back(std::move(v)));
  }

  // `InlinedVector::pop_back()`
  //
  // Destroys the element at `back()`, reducing the size by `1`.
  void pop_back() noexcept {
    ABSL_HARDENING_ASSERT(!empty());

    AllocatorTraits::destroy(*storage_.GetAllocPtr(), data() + (size() - 1));
    storage_.SubtractSize(1);
  }

  // `InlinedVector::erase(...)`
  //
  // Erases the element at `pos`, returning an `iterator` pointing to where the
  // erased element was located.
  //
  // NOTE: may return `end()`, which is not dereferencable.
  iterator erase(const_iterator pos) {
    ABSL_HARDENING_ASSERT(pos >= begin());
    ABSL_HARDENING_ASSERT(pos < end());

    return storage_.Erase(pos, pos + 1);
  }

  // Overload of `InlinedVector::erase(...)` that erases every element in the
  // range [`from`, `to`), returning an `iterator` pointing to where the first
  // erased element was located.
  //
  // NOTE: may return `end()`, which is not dereferencable.
  iterator erase(const_iterator from, const_iterator to) {
    ABSL_HARDENING_ASSERT(from >= begin());
    ABSL_HARDENING_ASSERT(from <= to);
    ABSL_HARDENING_ASSERT(to <= end());

    if (ABSL_PREDICT_TRUE(from != to)) {
      return storage_.Erase(from, to);
    } else {
      return const_cast<iterator>(from);
    }
  }

  // `InlinedVector::clear()`
  //
  // Destroys all elements in the inlined vector, setting the size to `0` and
  // deallocating any held memory.
  void clear() noexcept {
    inlined_vector_internal::DestroyElements(storage_.GetAllocPtr(), data(),
                                             size());
    storage_.DeallocateIfAllocated();

    storage_.SetInlinedSize(0);
  }

  // `InlinedVector::reserve(...)`
  //
  // Ensures that there is enough room for at least `n` elements.
  void reserve(size_type n) { storage_.Reserve(n); }

  // `InlinedVector::shrink_to_fit()`
  //
  // Reduces memory usage by freeing unused memory. After being called, calls to
  // `capacity()` will be equal to `max(N, size())`.
  //
  // If `size() <= N` and the inlined vector contains allocated memory, the
  // elements will all be moved to the inlined space and the allocated memory
  // will be deallocated.
  //
  // If `size() > N` and `size() < capacity()`, the elements will be moved to a
  // smaller allocation.
  void shrink_to_fit() {
    if (storage_.GetIsAllocated()) {
      storage_.ShrinkToFit();
    }
  }

  // `InlinedVector::swap(...)`
  //
  // Swaps the contents of the inlined vector with `other`.
  void swap(InlinedVector& other) {
    if (ABSL_PREDICT_TRUE(this != std::addressof(other))) {
      storage_.Swap(std::addressof(other.storage_));
    }
  }

 private:
  template <typename H, typename TheT, size_t TheN, typename TheA>
  friend H AbslHashValue(H h, const absl::InlinedVector<TheT, TheN, TheA>& a);

  Storage storage_;
};

// -----------------------------------------------------------------------------
// InlinedVector Non-Member Functions
// -----------------------------------------------------------------------------

// `swap(...)`
//
// Swaps the contents of two inlined vectors.
template <typename T, size_t N, typename A>
void swap(absl::InlinedVector<T, N, A>& a,
          absl::InlinedVector<T, N, A>& b) noexcept(noexcept(a.swap(b))) {
  a.swap(b);
}

// `operator==(...)`
//
// Tests for value-equality of two inlined vectors.
template <typename T, size_t N, typename A>
bool operator==(const absl::InlinedVector<T, N, A>& a,
                const absl::InlinedVector<T, N, A>& b) {
  auto a_data = a.data();
  auto b_data = b.data();
  return absl::equal(a_data, a_data + a.size(), b_data, b_data + b.size());
}

// `operator!=(...)`
//
// Tests for value-inequality of two inlined vectors.
template <typename T, size_t N, typename A>
bool operator!=(const absl::InlinedVector<T, N, A>& a,
                const absl::InlinedVector<T, N, A>& b) {
  return !(a == b);
}

// `operator<(...)`
//
// Tests whether the value of an inlined vector is less than the value of
// another inlined vector using a lexicographical comparison algorithm.
template <typename T, size_t N, typename A>
bool operator<(const absl::InlinedVector<T, N, A>& a,
               const absl::InlinedVector<T, N, A>& b) {
  auto a_data = a.data();
  auto b_data = b.data();
  return std::lexicographical_compare(a_data, a_data + a.size(), b_data,
                                      b_data + b.size());
}

// `operator>(...)`
//
// Tests whether the value of an inlined vector is greater than the value of
// another inlined vector using a lexicographical comparison algorithm.
template <typename T, size_t N, typename A>
bool operator>(const absl::InlinedVector<T, N, A>& a,
               const absl::InlinedVector<T, N, A>& b) {
  return b < a;
}

// `operator<=(...)`
//
// Tests whether the value of an inlined vector is less than or equal to the
// value of another inlined vector using a lexicographical comparison algorithm.
template <typename T, size_t N, typename A>
bool operator<=(const absl::InlinedVector<T, N, A>& a,
                const absl::InlinedVector<T, N, A>& b) {
  return !(b < a);
}

// `operator>=(...)`
//
// Tests whether the value of an inlined vector is greater than or equal to the
// value of another inlined vector using a lexicographical comparison algorithm.
template <typename T, size_t N, typename A>
bool operator>=(const absl::InlinedVector<T, N, A>& a,
                const absl::InlinedVector<T, N, A>& b) {
  return !(a < b);
}

// `AbslHashValue(...)`
//
// Provides `absl::Hash` support for `absl::InlinedVector`. It is uncommon to
// call this directly.
template <typename H, typename T, size_t N, typename A>
H AbslHashValue(H h, const absl::InlinedVector<T, N, A>& a) {
  auto size = a.size();
  return H::combine(H::combine_contiguous(std::move(h), a.data(), size), size);
}

ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_CONTAINER_INLINED_VECTOR_H_
