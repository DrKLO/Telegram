// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_CIRCULAR_DEQUE_H_
#define BASE_CONTAINERS_CIRCULAR_DEQUE_H_

#include <algorithm>
#include <cstddef>
#include <iterator>
#include <type_traits>
#include <utility>

#include "base/containers/vector_buffer.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/stl_util.h"
#include "base/template_util.h"

// base::circular_deque is similar to std::deque. Unlike std::deque, the
// storage is provided in a flat circular buffer conceptually similar to a
// vector. The beginning and end will wrap around as necessary so that
// pushes and pops will be constant time as long as a capacity expansion is
// not required.
//
// The API should be identical to std::deque with the following differences:
//
//  - ITERATORS ARE NOT STABLE. Mutating the container will invalidate all
//    iterators.
//
//  - Insertions may resize the vector and so are not constant time (std::deque
//    guarantees constant time for insertions at the ends).
//
//  - Container-wide comparisons are not implemented. If you want to compare
//    two containers, use an algorithm so the expensive iteration is explicit.
//
// If you want a similar container with only a queue API, use base::queue in
// base/containers/queue.h.
//
// Constructors:
//   circular_deque();
//   circular_deque(size_t count);
//   circular_deque(size_t count, const T& value);
//   circular_deque(InputIterator first, InputIterator last);
//   circular_deque(const circular_deque&);
//   circular_deque(circular_deque&&);
//   circular_deque(std::initializer_list<value_type>);
//
// Assignment functions:
//   circular_deque& operator=(const circular_deque&);
//   circular_deque& operator=(circular_deque&&);
//   circular_deque& operator=(std::initializer_list<T>);
//   void assign(size_t count, const T& value);
//   void assign(InputIterator first, InputIterator last);
//   void assign(std::initializer_list<T> value);
//
// Random accessors:
//   T& at(size_t);
//   const T& at(size_t) const;
//   T& operator[](size_t);
//   const T& operator[](size_t) const;
//
// End accessors:
//   T& front();
//   const T& front() const;
//   T& back();
//   const T& back() const;
//
// Iterator functions:
//   iterator               begin();
//   const_iterator         begin() const;
//   const_iterator         cbegin() const;
//   iterator               end();
//   const_iterator         end() const;
//   const_iterator         cend() const;
//   reverse_iterator       rbegin();
//   const_reverse_iterator rbegin() const;
//   const_reverse_iterator crbegin() const;
//   reverse_iterator       rend();
//   const_reverse_iterator rend() const;
//   const_reverse_iterator crend() const;
//
// Memory management:
//   void reserve(size_t);  // SEE IMPLEMENTATION FOR SOME GOTCHAS.
//   size_t capacity() const;
//   void shrink_to_fit();
//
// Size management:
//   void clear();
//   bool empty() const;
//   size_t size() const;
//   void resize(size_t);
//   void resize(size_t count, const T& value);
//
// Positional insert and erase:
//   void insert(const_iterator pos, size_type count, const T& value);
//   void insert(const_iterator pos,
//               InputIterator first, InputIterator last);
//   iterator insert(const_iterator pos, const T& value);
//   iterator insert(const_iterator pos, T&& value);
//   iterator emplace(const_iterator pos, Args&&... args);
//   iterator erase(const_iterator pos);
//   iterator erase(const_iterator first, const_iterator last);
//
// End insert and erase:
//   void push_front(const T&);
//   void push_front(T&&);
//   void push_back(const T&);
//   void push_back(T&&);
//   T& emplace_front(Args&&...);
//   T& emplace_back(Args&&...);
//   void pop_front();
//   void pop_back();
//
// General:
//   void swap(circular_deque&);

namespace base {

template <class T>
class circular_deque;

namespace internal {

// Start allocating nonempty buffers with this many entries. This is the
// external capacity so the internal buffer will be one larger (= 4) which is
// more even for the allocator. See the descriptions of internal vs. external
// capacity on the comment above the buffer_ variable below.
constexpr size_t kCircularBufferInitialCapacity = 3;

template <typename T>
class circular_deque_const_iterator {
 public:
  using difference_type = std::ptrdiff_t;
  using value_type = T;
  using pointer = const T*;
  using reference = const T&;
  using iterator_category = std::random_access_iterator_tag;

  circular_deque_const_iterator() : parent_deque_(nullptr), index_(0) {
#if DCHECK_IS_ON()
    created_generation_ = 0;
#endif  // DCHECK_IS_ON()
  }

  // Dereferencing.
  const T& operator*() const {
    CheckUnstableUsage();
    parent_deque_->CheckValidIndex(index_);
    return parent_deque_->buffer_[index_];
  }
  const T* operator->() const {
    CheckUnstableUsage();
    parent_deque_->CheckValidIndex(index_);
    return &parent_deque_->buffer_[index_];
  }
  const value_type& operator[](difference_type i) const { return *(*this + i); }

  // Increment and decrement.
  circular_deque_const_iterator& operator++() {
    Increment();
    return *this;
  }
  circular_deque_const_iterator operator++(int) {
    circular_deque_const_iterator ret = *this;
    Increment();
    return ret;
  }
  circular_deque_const_iterator& operator--() {
    Decrement();
    return *this;
  }
  circular_deque_const_iterator operator--(int) {
    circular_deque_const_iterator ret = *this;
    Decrement();
    return ret;
  }

  // Random access mutation.
  friend circular_deque_const_iterator operator+(
      const circular_deque_const_iterator& iter,
      difference_type offset) {
    circular_deque_const_iterator ret = iter;
    ret.Add(offset);
    return ret;
  }
  circular_deque_const_iterator& operator+=(difference_type offset) {
    Add(offset);
    return *this;
  }
  friend circular_deque_const_iterator operator-(
      const circular_deque_const_iterator& iter,
      difference_type offset) {
    circular_deque_const_iterator ret = iter;
    ret.Add(-offset);
    return ret;
  }
  circular_deque_const_iterator& operator-=(difference_type offset) {
    Add(-offset);
    return *this;
  }

  friend std::ptrdiff_t operator-(const circular_deque_const_iterator& lhs,
                                  const circular_deque_const_iterator& rhs) {
    lhs.CheckComparable(rhs);
    return lhs.OffsetFromBegin() - rhs.OffsetFromBegin();
  }

  // Comparisons.
  friend bool operator==(const circular_deque_const_iterator& lhs,
                         const circular_deque_const_iterator& rhs) {
    lhs.CheckComparable(rhs);
    return lhs.index_ == rhs.index_;
  }
  friend bool operator!=(const circular_deque_const_iterator& lhs,
                         const circular_deque_const_iterator& rhs) {
    return !(lhs == rhs);
  }
  friend bool operator<(const circular_deque_const_iterator& lhs,
                        const circular_deque_const_iterator& rhs) {
    lhs.CheckComparable(rhs);
    return lhs.OffsetFromBegin() < rhs.OffsetFromBegin();
  }
  friend bool operator<=(const circular_deque_const_iterator& lhs,
                         const circular_deque_const_iterator& rhs) {
    return !(lhs > rhs);
  }
  friend bool operator>(const circular_deque_const_iterator& lhs,
                        const circular_deque_const_iterator& rhs) {
    lhs.CheckComparable(rhs);
    return lhs.OffsetFromBegin() > rhs.OffsetFromBegin();
  }
  friend bool operator>=(const circular_deque_const_iterator& lhs,
                         const circular_deque_const_iterator& rhs) {
    return !(lhs < rhs);
  }

 protected:
  friend class circular_deque<T>;

  circular_deque_const_iterator(const circular_deque<T>* parent, size_t index)
      : parent_deque_(parent), index_(index) {
#if DCHECK_IS_ON()
    created_generation_ = parent->generation_;
#endif  // DCHECK_IS_ON()
  }

  // Returns the offset from the beginning index of the buffer to the current
  // item.
  size_t OffsetFromBegin() const {
    if (index_ >= parent_deque_->begin_)
      return index_ - parent_deque_->begin_;  // On the same side as begin.
    return parent_deque_->buffer_.capacity() - parent_deque_->begin_ + index_;
  }

  // Most uses will be ++ and -- so use a simplified implementation.
  void Increment() {
    CheckUnstableUsage();
    parent_deque_->CheckValidIndex(index_);
    index_++;
    if (index_ == parent_deque_->buffer_.capacity())
      index_ = 0;
  }
  void Decrement() {
    CheckUnstableUsage();
    parent_deque_->CheckValidIndexOrEnd(index_);
    if (index_ == 0)
      index_ = parent_deque_->buffer_.capacity() - 1;
    else
      index_--;
  }
  void Add(difference_type delta) {
    CheckUnstableUsage();
#if DCHECK_IS_ON()
    if (delta <= 0)
      parent_deque_->CheckValidIndexOrEnd(index_);
    else
      parent_deque_->CheckValidIndex(index_);
#endif
    // It should be valid to add 0 to any iterator, even if the container is
    // empty and the iterator points to end(). The modulo below will divide
    // by 0 if the buffer capacity is empty, so it's important to check for
    // this case explicitly.
    if (delta == 0)
      return;

    difference_type new_offset = OffsetFromBegin() + delta;
    DCHECK(new_offset >= 0 &&
           new_offset <= static_cast<difference_type>(parent_deque_->size()));
    index_ = (new_offset + parent_deque_->begin_) %
             parent_deque_->buffer_.capacity();
  }

#if DCHECK_IS_ON()
  void CheckUnstableUsage() const {
    DCHECK(parent_deque_);
    // Since circular_deque doesn't guarantee stability, any attempt to
    // dereference this iterator after a mutation (i.e. the generation doesn't
    // match the original) in the container is illegal.
    DCHECK_EQ(created_generation_, parent_deque_->generation_)
        << "circular_deque iterator dereferenced after mutation.";
  }
  void CheckComparable(const circular_deque_const_iterator& other) const {
    DCHECK_EQ(parent_deque_, other.parent_deque_);
    // Since circular_deque doesn't guarantee stability, two iterators that
    // are compared must have been generated without mutating the container.
    // If this fires, the container was mutated between generating the two
    // iterators being compared.
    DCHECK_EQ(created_generation_, other.created_generation_);
  }
#else
  inline void CheckUnstableUsage() const {}
  inline void CheckComparable(const circular_deque_const_iterator&) const {}
#endif  // DCHECK_IS_ON()

  const circular_deque<T>* parent_deque_;
  size_t index_;

#if DCHECK_IS_ON()
  // The generation of the parent deque when this iterator was created. The
  // container will update the generation for every modification so we can
  // test if the container was modified by comparing them.
  uint64_t created_generation_;
#endif  // DCHECK_IS_ON()
};

template <typename T>
class circular_deque_iterator : public circular_deque_const_iterator<T> {
  using base = circular_deque_const_iterator<T>;

 public:
  friend class circular_deque<T>;

  using difference_type = std::ptrdiff_t;
  using value_type = T;
  using pointer = T*;
  using reference = T&;
  using iterator_category = std::random_access_iterator_tag;

  // Expose the base class' constructor.
  circular_deque_iterator() : circular_deque_const_iterator<T>() {}

  // Dereferencing.
  T& operator*() const { return const_cast<T&>(base::operator*()); }
  T* operator->() const { return const_cast<T*>(base::operator->()); }
  T& operator[](difference_type i) {
    return const_cast<T&>(base::operator[](i));
  }

  // Random access mutation.
  friend circular_deque_iterator operator+(const circular_deque_iterator& iter,
                                           difference_type offset) {
    circular_deque_iterator ret = iter;
    ret.Add(offset);
    return ret;
  }
  circular_deque_iterator& operator+=(difference_type offset) {
    base::Add(offset);
    return *this;
  }
  friend circular_deque_iterator operator-(const circular_deque_iterator& iter,
                                           difference_type offset) {
    circular_deque_iterator ret = iter;
    ret.Add(-offset);
    return ret;
  }
  circular_deque_iterator& operator-=(difference_type offset) {
    base::Add(-offset);
    return *this;
  }

  // Increment and decrement.
  circular_deque_iterator& operator++() {
    base::Increment();
    return *this;
  }
  circular_deque_iterator operator++(int) {
    circular_deque_iterator ret = *this;
    base::Increment();
    return ret;
  }
  circular_deque_iterator& operator--() {
    base::Decrement();
    return *this;
  }
  circular_deque_iterator operator--(int) {
    circular_deque_iterator ret = *this;
    base::Decrement();
    return ret;
  }

 private:
  circular_deque_iterator(const circular_deque<T>* parent, size_t index)
      : circular_deque_const_iterator<T>(parent, index) {}
};

}  // namespace internal

template <typename T>
class circular_deque {
 private:
  using VectorBuffer = internal::VectorBuffer<T>;

 public:
  using value_type = T;
  using size_type = std::size_t;
  using difference_type = std::ptrdiff_t;
  using reference = value_type&;
  using const_reference = const value_type&;
  using pointer = value_type*;
  using const_pointer = const value_type*;

  using iterator = internal::circular_deque_iterator<T>;
  using const_iterator = internal::circular_deque_const_iterator<T>;
  using reverse_iterator = std::reverse_iterator<iterator>;
  using const_reverse_iterator = std::reverse_iterator<const_iterator>;

  // ---------------------------------------------------------------------------
  // Constructor

  constexpr circular_deque() = default;

  // Constructs with |count| copies of |value| or default constructed version.
  circular_deque(size_type count) { resize(count); }
  circular_deque(size_type count, const T& value) { resize(count, value); }

  // Range constructor.
  template <class InputIterator>
  circular_deque(InputIterator first, InputIterator last) {
    assign(first, last);
  }

  // Copy/move.
  circular_deque(const circular_deque& other) : buffer_(other.size() + 1) {
    assign(other.begin(), other.end());
  }
  circular_deque(circular_deque&& other) noexcept
      : buffer_(std::move(other.buffer_)),
        begin_(other.begin_),
        end_(other.end_) {
    other.begin_ = 0;
    other.end_ = 0;
  }

  circular_deque(std::initializer_list<value_type> init) { assign(init); }

  ~circular_deque() { DestructRange(begin_, end_); }

  // ---------------------------------------------------------------------------
  // Assignments.
  //
  // All of these may invalidate iterators and references.

  circular_deque& operator=(const circular_deque& other) {
    if (&other == this)
      return *this;

    reserve(other.size());
    assign(other.begin(), other.end());
    return *this;
  }
  circular_deque& operator=(circular_deque&& other) noexcept {
    if (&other == this)
      return *this;

    // We're about to overwrite the buffer, so don't free it in clear to
    // avoid doing it twice.
    ClearRetainCapacity();
    buffer_ = std::move(other.buffer_);
    begin_ = other.begin_;
    end_ = other.end_;

    other.begin_ = 0;
    other.end_ = 0;

    IncrementGeneration();
    return *this;
  }
  circular_deque& operator=(std::initializer_list<value_type> ilist) {
    reserve(ilist.size());
    assign(std::begin(ilist), std::end(ilist));
    return *this;
  }

  void assign(size_type count, const value_type& value) {
    ClearRetainCapacity();
    reserve(count);
    for (size_t i = 0; i < count; i++)
      emplace_back(value);
    IncrementGeneration();
  }

  // This variant should be enabled only when InputIterator is an iterator.
  template <typename InputIterator>
  typename std::enable_if<::base::internal::is_iterator<InputIterator>::value,
                          void>::type
  assign(InputIterator first, InputIterator last) {
    // Possible future enhancement, dispatch on iterator tag type. For forward
    // iterators we can use std::difference to preallocate the space required
    // and only do one copy.
    ClearRetainCapacity();
    for (; first != last; ++first)
      emplace_back(*first);
    IncrementGeneration();
  }

  void assign(std::initializer_list<value_type> value) {
    reserve(std::distance(value.begin(), value.end()));
    assign(value.begin(), value.end());
  }

  // ---------------------------------------------------------------------------
  // Accessors.
  //
  // Since this class assumes no exceptions, at() and operator[] are equivalent.

  const value_type& at(size_type i) const {
    DCHECK(i < size());
    size_t right_size = buffer_.capacity() - begin_;
    if (begin_ <= end_ || i < right_size)
      return buffer_[begin_ + i];
    return buffer_[i - right_size];
  }
  value_type& at(size_type i) {
    return const_cast<value_type&>(as_const(*this).at(i));
  }

  value_type& operator[](size_type i) {
    return const_cast<value_type&>(as_const(*this)[i]);
  }

  const value_type& operator[](size_type i) const { return at(i); }

  value_type& front() {
    DCHECK(!empty());
    return buffer_[begin_];
  }
  const value_type& front() const {
    DCHECK(!empty());
    return buffer_[begin_];
  }

  value_type& back() {
    DCHECK(!empty());
    return *(--end());
  }
  const value_type& back() const {
    DCHECK(!empty());
    return *(--end());
  }

  // ---------------------------------------------------------------------------
  // Iterators.

  iterator begin() { return iterator(this, begin_); }
  const_iterator begin() const { return const_iterator(this, begin_); }
  const_iterator cbegin() const { return const_iterator(this, begin_); }

  iterator end() { return iterator(this, end_); }
  const_iterator end() const { return const_iterator(this, end_); }
  const_iterator cend() const { return const_iterator(this, end_); }

  reverse_iterator rbegin() { return reverse_iterator(end()); }
  const_reverse_iterator rbegin() const {
    return const_reverse_iterator(end());
  }
  const_reverse_iterator crbegin() const { return rbegin(); }

  reverse_iterator rend() { return reverse_iterator(begin()); }
  const_reverse_iterator rend() const {
    return const_reverse_iterator(begin());
  }
  const_reverse_iterator crend() const { return rend(); }

  // ---------------------------------------------------------------------------
  // Memory management.

  // IMPORTANT NOTE ON reserve(...): This class implements auto-shrinking of
  // the buffer when elements are deleted and there is "too much" wasted space.
  // So if you call reserve() with a large size in anticipation of pushing many
  // elements, but pop an element before the queue is full, the capacity you
  // reserved may be lost.
  //
  // As a result, it's only worthwhile to call reserve() when you're adding
  // many things at once with no intermediate operations.
  void reserve(size_type new_capacity) {
    if (new_capacity > capacity())
      SetCapacityTo(new_capacity);
  }

  size_type capacity() const {
    // One item is wasted to indicate end().
    return buffer_.capacity() == 0 ? 0 : buffer_.capacity() - 1;
  }

  void shrink_to_fit() {
    if (empty()) {
      // Optimize empty case to really delete everything if there was
      // something.
      if (buffer_.capacity())
        buffer_ = VectorBuffer();
    } else {
      SetCapacityTo(size());
    }
  }

  // ---------------------------------------------------------------------------
  // Size management.

  // This will additionally reset the capacity() to 0.
  void clear() {
    // This can't resize(0) because that requires a default constructor to
    // compile, which not all contained classes may implement.
    ClearRetainCapacity();
    buffer_ = VectorBuffer();
  }

  bool empty() const { return begin_ == end_; }

  size_type size() const {
    if (begin_ <= end_)
      return end_ - begin_;
    return buffer_.capacity() - begin_ + end_;
  }

  // When reducing size, the elements are deleted from the end. When expanding
  // size, elements are added to the end with |value| or the default
  // constructed version. Even when using resize(count) to shrink, a default
  // constructor is required for the code to compile, even though it will not
  // be called.
  //
  // There are two versions rather than using a default value to avoid
  // creating a temporary when shrinking (when it's not needed). Plus if
  // the default constructor is desired when expanding usually just calling it
  // for each element is faster than making a default-constructed temporary and
  // copying it.
  void resize(size_type count) {
    // SEE BELOW VERSION if you change this. The code is mostly the same.
    if (count > size()) {
      // This could be slighly more efficient but expanding a queue with
      // identical elements is unusual and the extra computations of emplacing
      // one-by-one will typically be small relative to calling the constructor
      // for every item.
      ExpandCapacityIfNecessary(count - size());
      while (size() < count)
        emplace_back();
    } else if (count < size()) {
      size_t new_end = (begin_ + count) % buffer_.capacity();
      DestructRange(new_end, end_);
      end_ = new_end;

      ShrinkCapacityIfNecessary();
    }
    IncrementGeneration();
  }
  void resize(size_type count, const value_type& value) {
    // SEE ABOVE VERSION if you change this. The code is mostly the same.
    if (count > size()) {
      ExpandCapacityIfNecessary(count - size());
      while (size() < count)
        emplace_back(value);
    } else if (count < size()) {
      size_t new_end = (begin_ + count) % buffer_.capacity();
      DestructRange(new_end, end_);
      end_ = new_end;

      ShrinkCapacityIfNecessary();
    }
    IncrementGeneration();
  }

  // ---------------------------------------------------------------------------
  // Insert and erase.
  //
  // Insertion and deletion in the middle is O(n) and invalidates all existing
  // iterators.
  //
  // The implementation of insert isn't optimized as much as it could be. If
  // the insertion requires that the buffer be grown, it will first be grown
  // and everything moved, and then the items will be inserted, potentially
  // moving some items twice. This simplifies the implemntation substantially
  // and means less generated templatized code. Since this is an uncommon
  // operation for deques, and already relatively slow, it doesn't seem worth
  // the benefit to optimize this.

  void insert(const_iterator pos, size_type count, const T& value) {
    ValidateIterator(pos);

    // Optimize insert at the beginning.
    if (pos == begin()) {
      ExpandCapacityIfNecessary(count);
      for (size_t i = 0; i < count; i++)
        push_front(value);
      return;
    }

    iterator insert_cur(this, pos.index_);
    iterator insert_end;
    MakeRoomFor(count, &insert_cur, &insert_end);
    while (insert_cur < insert_end) {
      new (&buffer_[insert_cur.index_]) T(value);
      ++insert_cur;
    }

    IncrementGeneration();
  }

  // This enable_if keeps this call from getting confused with the (pos, count,
  // value) version when value is an integer.
  template <class InputIterator>
  typename std::enable_if<::base::internal::is_iterator<InputIterator>::value,
                          void>::type
  insert(const_iterator pos, InputIterator first, InputIterator last) {
    ValidateIterator(pos);

    size_t inserted_items = std::distance(first, last);
    if (inserted_items == 0)
      return;  // Can divide by 0 when doing modulo below, so return early.

    // Make a hole to copy the items into.
    iterator insert_cur;
    iterator insert_end;
    if (pos == begin()) {
      // Optimize insert at the beginning, nothing needs to be shifted and the
      // hole is the |inserted_items| block immediately before |begin_|.
      ExpandCapacityIfNecessary(inserted_items);
      insert_end = begin();
      begin_ =
          (begin_ + buffer_.capacity() - inserted_items) % buffer_.capacity();
      insert_cur = begin();
    } else {
      insert_cur = iterator(this, pos.index_);
      MakeRoomFor(inserted_items, &insert_cur, &insert_end);
    }

    // Copy the items.
    while (insert_cur < insert_end) {
      new (&buffer_[insert_cur.index_]) T(*first);
      ++insert_cur;
      ++first;
    }

    IncrementGeneration();
  }

  // These all return an iterator to the inserted item. Existing iterators will
  // be invalidated.
  iterator insert(const_iterator pos, const T& value) {
    return emplace(pos, value);
  }
  iterator insert(const_iterator pos, T&& value) {
    return emplace(pos, std::move(value));
  }
  template <class... Args>
  iterator emplace(const_iterator pos, Args&&... args) {
    ValidateIterator(pos);

    // Optimize insert at beginning which doesn't require shifting.
    if (pos == cbegin()) {
      emplace_front(std::forward<Args>(args)...);
      return begin();
    }

    // Do this before we make the new iterators we return.
    IncrementGeneration();

    iterator insert_begin(this, pos.index_);
    iterator insert_end;
    MakeRoomFor(1, &insert_begin, &insert_end);
    new (&buffer_[insert_begin.index_]) T(std::forward<Args>(args)...);

    return insert_begin;
  }

  // Calling erase() won't automatically resize the buffer smaller like resize
  // or the pop functions. Erase is slow and relatively uncommon, and for
  // normal deque usage a pop will normally be done on a regular basis that
  // will prevent excessive buffer usage over long periods of time. It's not
  // worth having the extra code for every template instantiation of erase()
  // to resize capacity downward to a new buffer.
  iterator erase(const_iterator pos) { return erase(pos, pos + 1); }
  iterator erase(const_iterator first, const_iterator last) {
    ValidateIterator(first);
    ValidateIterator(last);

    IncrementGeneration();

    // First, call the destructor on the deleted items.
    if (first.index_ == last.index_) {
      // Nothing deleted. Need to return early to avoid falling through to
      // moving items on top of themselves.
      return iterator(this, first.index_);
    } else if (first.index_ < last.index_) {
      // Contiguous range.
      buffer_.DestructRange(&buffer_[first.index_], &buffer_[last.index_]);
    } else {
      // Deleted range wraps around.
      buffer_.DestructRange(&buffer_[first.index_],
                            &buffer_[buffer_.capacity()]);
      buffer_.DestructRange(&buffer_[0], &buffer_[last.index_]);
    }

    if (first.index_ == begin_) {
      // This deletion is from the beginning. Nothing needs to be copied, only
      // begin_ needs to be updated.
      begin_ = last.index_;
      return iterator(this, last.index_);
    }

    // In an erase operation, the shifted items all move logically to the left,
    // so move them from left-to-right.
    iterator move_src(this, last.index_);
    iterator move_src_end = end();
    iterator move_dest(this, first.index_);
    for (; move_src < move_src_end; move_src++, move_dest++) {
      buffer_.MoveRange(&buffer_[move_src.index_],
                        &buffer_[move_src.index_ + 1],
                        &buffer_[move_dest.index_]);
    }

    end_ = move_dest.index_;

    // Since we did not reallocate and only changed things after the erase
    // element(s), the input iterator's index points to the thing following the
    // deletion.
    return iterator(this, first.index_);
  }

  // ---------------------------------------------------------------------------
  // Begin/end operations.

  void push_front(const T& value) { emplace_front(value); }
  void push_front(T&& value) { emplace_front(std::move(value)); }

  void push_back(const T& value) { emplace_back(value); }
  void push_back(T&& value) { emplace_back(std::move(value)); }

  template <class... Args>
  reference emplace_front(Args&&... args) {
    ExpandCapacityIfNecessary(1);
    if (begin_ == 0)
      begin_ = buffer_.capacity() - 1;
    else
      begin_--;
    IncrementGeneration();
    new (&buffer_[begin_]) T(std::forward<Args>(args)...);
    return front();
  }

  template <class... Args>
  reference emplace_back(Args&&... args) {
    ExpandCapacityIfNecessary(1);
    new (&buffer_[end_]) T(std::forward<Args>(args)...);
    if (end_ == buffer_.capacity() - 1)
      end_ = 0;
    else
      end_++;
    IncrementGeneration();
    return back();
  }

  void pop_front() {
    DCHECK(size());
    buffer_.DestructRange(&buffer_[begin_], &buffer_[begin_ + 1]);
    begin_++;
    if (begin_ == buffer_.capacity())
      begin_ = 0;

    ShrinkCapacityIfNecessary();

    // Technically popping will not invalidate any iterators since the
    // underlying buffer will be stable. But in the future we may want to add a
    // feature that resizes the buffer smaller if there is too much wasted
    // space. This ensures we can make such a change safely.
    IncrementGeneration();
  }
  void pop_back() {
    DCHECK(size());
    if (end_ == 0)
      end_ = buffer_.capacity() - 1;
    else
      end_--;
    buffer_.DestructRange(&buffer_[end_], &buffer_[end_ + 1]);

    ShrinkCapacityIfNecessary();

    // See pop_front comment about why this is here.
    IncrementGeneration();
  }

  // ---------------------------------------------------------------------------
  // General operations.

  void swap(circular_deque& other) {
    std::swap(buffer_, other.buffer_);
    std::swap(begin_, other.begin_);
    std::swap(end_, other.end_);
    IncrementGeneration();
  }

  friend void swap(circular_deque& lhs, circular_deque& rhs) { lhs.swap(rhs); }

 private:
  friend internal::circular_deque_iterator<T>;
  friend internal::circular_deque_const_iterator<T>;

  // Moves the items in the given circular buffer to the current one. The
  // source is moved from so will become invalid. The destination buffer must
  // have already been allocated with enough size.
  static void MoveBuffer(VectorBuffer& from_buf,
                         size_t from_begin,
                         size_t from_end,
                         VectorBuffer* to_buf,
                         size_t* to_begin,
                         size_t* to_end) {
    size_t from_capacity = from_buf.capacity();

    *to_begin = 0;
    if (from_begin < from_end) {
      // Contiguous.
      from_buf.MoveRange(&from_buf[from_begin], &from_buf[from_end],
                         to_buf->begin());
      *to_end = from_end - from_begin;
    } else if (from_begin > from_end) {
      // Discontiguous, copy the right side to the beginning of the new buffer.
      from_buf.MoveRange(&from_buf[from_begin], &from_buf[from_capacity],
                         to_buf->begin());
      size_t right_size = from_capacity - from_begin;
      // Append the left side.
      from_buf.MoveRange(&from_buf[0], &from_buf[from_end],
                         &(*to_buf)[right_size]);
      *to_end = right_size + from_end;
    } else {
      // No items.
      *to_end = 0;
    }
  }

  // Expands the buffer size. This assumes the size is larger than the
  // number of elements in the vector (it won't call delete on anything).
  void SetCapacityTo(size_t new_capacity) {
    // Use the capacity + 1 as the internal buffer size to differentiate
    // empty and full (see definition of buffer_ below).
    VectorBuffer new_buffer(new_capacity + 1);
    MoveBuffer(buffer_, begin_, end_, &new_buffer, &begin_, &end_);
    buffer_ = std::move(new_buffer);
  }
  void ExpandCapacityIfNecessary(size_t additional_elts) {
    size_t min_new_capacity = size() + additional_elts;
    if (capacity() >= min_new_capacity)
      return;  // Already enough room.

    min_new_capacity =
        std::max(min_new_capacity, internal::kCircularBufferInitialCapacity);

    // std::vector always grows by at least 50%. WTF::Deque grows by at least
    // 25%. We expect queue workloads to generally stay at a similar size and
    // grow less than a vector might, so use 25%.
    size_t new_capacity =
        std::max(min_new_capacity, capacity() + capacity() / 4);
    SetCapacityTo(new_capacity);
  }

  void ShrinkCapacityIfNecessary() {
    // Don't auto-shrink below this size.
    if (capacity() <= internal::kCircularBufferInitialCapacity)
      return;

    // Shrink when 100% of the size() is wasted.
    size_t sz = size();
    size_t empty_spaces = capacity() - sz;
    if (empty_spaces < sz)
      return;

    // Leave 1/4 the size as free capacity, not going below the initial
    // capacity.
    size_t new_capacity =
        std::max(internal::kCircularBufferInitialCapacity, sz + sz / 4);
    if (new_capacity < capacity()) {
      // Count extra item to convert to internal capacity.
      SetCapacityTo(new_capacity);
    }
  }

  // Backend for clear() but does not resize the internal buffer.
  void ClearRetainCapacity() {
    // This can't resize(0) because that requires a default constructor to
    // compile, which not all contained classes may implement.
    DestructRange(begin_, end_);
    begin_ = 0;
    end_ = 0;
    IncrementGeneration();
  }

  // Calls destructors for the given begin->end indices. The indices may wrap
  // around. The buffer is not resized, and the begin_ and end_ members are
  // not changed.
  void DestructRange(size_t begin, size_t end) {
    if (end == begin) {
      return;
    } else if (end > begin) {
      buffer_.DestructRange(&buffer_[begin], &buffer_[end]);
    } else {
      buffer_.DestructRange(&buffer_[begin], &buffer_[buffer_.capacity()]);
      buffer_.DestructRange(&buffer_[0], &buffer_[end]);
    }
  }

  // Makes room for |count| items starting at |*insert_begin|. Since iterators
  // are not stable across buffer resizes, |*insert_begin| will be updated to
  // point to the beginning of the newly opened position in the new array (it's
  // in/out), and the end of the newly opened position (it's out-only).
  void MakeRoomFor(size_t count, iterator* insert_begin, iterator* insert_end) {
    if (count == 0) {
      *insert_end = *insert_begin;
      return;
    }

    // The offset from the beginning will be stable across reallocations.
    size_t begin_offset = insert_begin->OffsetFromBegin();
    ExpandCapacityIfNecessary(count);

    insert_begin->index_ = (begin_ + begin_offset) % buffer_.capacity();
    *insert_end =
        iterator(this, (insert_begin->index_ + count) % buffer_.capacity());

    // Update the new end and prepare the iterators for copying.
    iterator src = end();
    end_ = (end_ + count) % buffer_.capacity();
    iterator dest = end();

    // Move the elements. This will always involve shifting logically to the
    // right, so move in a right-to-left order.
    while (true) {
      if (src == *insert_begin)
        break;
      --src;
      --dest;
      buffer_.MoveRange(&buffer_[src.index_], &buffer_[src.index_ + 1],
                        &buffer_[dest.index_]);
    }
  }

#if DCHECK_IS_ON()
  // Asserts the given index is dereferencable. The index is an index into the
  // buffer, not an index used by operator[] or at() which will be offsets from
  // begin.
  void CheckValidIndex(size_t i) const {
    if (begin_ <= end_)
      DCHECK(i >= begin_ && i < end_);
    else
      DCHECK((i >= begin_ && i < buffer_.capacity()) || i < end_);
  }

  // Asserts the given index is either dereferencable or points to end().
  void CheckValidIndexOrEnd(size_t i) const {
    if (i != end_)
      CheckValidIndex(i);
  }

  void ValidateIterator(const const_iterator& i) const {
    DCHECK(i.parent_deque_ == this);
    i.CheckUnstableUsage();
  }

  // See generation_ below.
  void IncrementGeneration() { generation_++; }
#else
  // No-op versions of these functions for release builds.
  void CheckValidIndex(size_t) const {}
  void CheckValidIndexOrEnd(size_t) const {}
  void ValidateIterator(const const_iterator& i) const {}
  void IncrementGeneration() {}
#endif

  // Danger, the buffer_.capacity() is the "internal capacity" which is
  // capacity() + 1 since there is an extra item to indicate the end. Otherwise
  // being completely empty and completely full are indistinguishable (begin ==
  // end). We could add a separate flag to avoid it, but that adds significant
  // extra complexity since every computation will have to check for it. Always
  // keeping one extra unused element in the buffer makes iterator computations
  // much simpler.
  //
  // Container internal code will want to use buffer_.capacity() for offset
  // computations rather than capacity().
  VectorBuffer buffer_;
  size_type begin_ = 0;
  size_type end_ = 0;

#if DCHECK_IS_ON()
  // Incremented every time a modification is made that could affect iterator
  // invalidations.
  uint64_t generation_ = 0;
#endif
};

// Implementations of base::Erase[If] (see base/stl_util.h).
template <class T, class Value>
size_t Erase(circular_deque<T>& container, const Value& value) {
  auto it = std::remove(container.begin(), container.end(), value);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <class T, class Predicate>
size_t EraseIf(circular_deque<T>& container, Predicate pred) {
  auto it = std::remove_if(container.begin(), container.end(), pred);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

}  // namespace base

#endif  // BASE_CONTAINERS_CIRCULAR_DEQUE_H_
