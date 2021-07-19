// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_LAZILY_DEALLOCATED_DEQUE_H_
#define BASE_TASK_SEQUENCE_MANAGER_LAZILY_DEALLOCATED_DEQUE_H_

#include <algorithm>
#include <cmath>
#include <memory>
#include <vector>

#include "base/debug/alias.h"
#include "base/gtest_prod_util.h"
#include "base/logging.h"
#include "base/time/time.h"

namespace base {
namespace sequence_manager {
namespace internal {

// A LazilyDeallocatedDeque specialized for the SequenceManager's usage
// patterns. The queue generally grows while tasks are added and then removed
// until empty and the cycle repeats.
//
// The main difference between sequence_manager::LazilyDeallocatedDeque and
// others is memory management.  For performance (memory allocation isn't free)
// we don't automatically reclaiming memory when the queue becomes empty.
// Instead we rely on the surrounding code periodically calling
// MaybeShrinkQueue, ideally when the queue is empty.
//
// We keep track of the maximum recent queue size and rate limit
// MaybeShrinkQueue to avoid unnecessary churn.
//
// NB this queue isn't by itself thread safe.
template <typename T, TimeTicks (*now_source)() = TimeTicks::Now>
class LazilyDeallocatedDeque {
 public:
  enum {
    // Minimum allocation for a ring. Note a ring of size 4 will only hold up to
    // 3 elements.
    kMinimumRingSize = 4,

    // Maximum "wasted" capacity allowed when considering if we should resize
    // the backing store.
    kReclaimThreshold = 16,

    // Used to rate limit how frequently MaybeShrinkQueue actually shrinks the
    // queue.
    kMinimumShrinkIntervalInSeconds = 5
  };

  LazilyDeallocatedDeque() {}

  ~LazilyDeallocatedDeque() { clear(); }

  bool empty() const { return size_ == 0; }

  size_t max_size() const { return max_size_; }

  size_t size() const { return size_; }

  size_t capacity() const {
    size_t capacity = 0;
    for (const Ring* iter = head_.get(); iter; iter = iter->next_.get()) {
      capacity += iter->capacity();
    }
    return capacity;
  }

  void clear() {
    while (head_) {
      head_ = std::move(head_->next_);
    }

    tail_ = nullptr;
    size_ = 0;
  }

  // Assumed to be an uncommon operation.
  void push_front(T t) {
    if (!head_) {
      DCHECK(!tail_);
      head_ = std::make_unique<Ring>(kMinimumRingSize);
      tail_ = head_.get();
    }

    // Grow if needed, by the minimum amount.
    if (!head_->CanPush()) {
      // TODO(alexclarke): Remove once we've understood the OOMs.
      size_t size = size_;
      base::debug::Alias(&size);

      std::unique_ptr<Ring> new_ring = std::make_unique<Ring>(kMinimumRingSize);
      new_ring->next_ = std::move(head_);
      head_ = std::move(new_ring);
    }

    head_->push_front(std::move(t));
    max_size_ = std::max(max_size_, ++size_);
  }

  // Assumed to be a common operation.
  void push_back(T t) {
    if (!head_) {
      DCHECK(!tail_);
      head_ = std::make_unique<Ring>(kMinimumRingSize);
      tail_ = head_.get();
    }

    // Grow if needed.
    if (!tail_->CanPush()) {
      // TODO(alexclarke): Remove once we've understood the OOMs.
      size_t size = size_;
      base::debug::Alias(&size);

      // Doubling the size is a common strategy, but one which can be wasteful
      // so we use a (somewhat) slower growth curve.
      tail_->next_ = std::make_unique<Ring>(2 + tail_->capacity() +
                                            (tail_->capacity() / 2));
      tail_ = tail_->next_.get();
    }

    tail_->push_back(std::move(t));
    max_size_ = std::max(max_size_, ++size_);
  }

  T& front() {
    DCHECK(head_);
    return head_->front();
  }

  const T& front() const {
    DCHECK(head_);
    return head_->front();
  }

  T& back() {
    DCHECK(tail_);
    return tail_->back();
  }

  const T& back() const {
    DCHECK(tail_);
    return tail_->back();
  }

  void pop_front() {
    DCHECK(head_);
    DCHECK(!head_->empty());
    DCHECK(tail_);
    DCHECK_GT(size_, 0u);
    head_->pop_front();

    // If the ring has become empty and we have several rings then, remove the
    // head one (which we expect to have lower capacity than the remaining
    // ones).
    if (head_->empty() && head_->next_) {
      head_ = std::move(head_->next_);
    }

    --size_;
  }

  void swap(LazilyDeallocatedDeque& other) {
    std::swap(head_, other.head_);
    std::swap(tail_, other.tail_);
    std::swap(size_, other.size_);
    std::swap(max_size_, other.max_size_);
    std::swap(next_resize_time_, other.next_resize_time_);
  }

  void MaybeShrinkQueue() {
    if (!tail_)
      return;

    DCHECK_GE(max_size_, size_);

    // Rate limit how often we shrink the queue because it's somewhat expensive.
    TimeTicks current_time = now_source();
    if (current_time < next_resize_time_)
      return;

    // Due to the way the Ring works we need 1 more slot than is used.
    size_t new_capacity = max_size_ + 1;
    if (new_capacity < kMinimumRingSize)
      new_capacity = kMinimumRingSize;

    // Reset |max_size_| so that unless usage has spiked up we will consider
    // reclaiming it next time.
    max_size_ = size_;

    // Only realloc if the current capacity is sufficiently greater than the
    // observed maximum size for the previous period.
    if (new_capacity + kReclaimThreshold >= capacity())
      return;

    SetCapacity(new_capacity);
    next_resize_time_ =
        current_time + TimeDelta::FromSeconds(kMinimumShrinkIntervalInSeconds);
  }

  void SetCapacity(size_t new_capacity) {
    std::unique_ptr<Ring> new_ring = std::make_unique<Ring>(new_capacity);

    DCHECK_GE(new_capacity, size_ + 1);

    // Preserve the |size_| which counts down to zero in the while loop.
    size_t real_size = size_;

    while (!empty()) {
      DCHECK(new_ring->CanPush());
      new_ring->push_back(std::move(head_->front()));
      pop_front();
    }

    size_ = real_size;

    DCHECK_EQ(head_.get(), tail_);
    head_ = std::move(new_ring);
    tail_ = head_.get();
  }

 private:
  FRIEND_TEST_ALL_PREFIXES(LazilyDeallocatedDequeTest, RingPushFront);
  FRIEND_TEST_ALL_PREFIXES(LazilyDeallocatedDequeTest, RingPushBack);
  FRIEND_TEST_ALL_PREFIXES(LazilyDeallocatedDequeTest, RingCanPush);
  FRIEND_TEST_ALL_PREFIXES(LazilyDeallocatedDequeTest, RingPushPopPushPop);

  struct Ring {
    explicit Ring(size_t capacity)
        : capacity_(capacity),
          front_index_(0),
          back_index_(0),
          data_(reinterpret_cast<T*>(new char[sizeof(T) * capacity])),
          next_(nullptr) {
      DCHECK_GE(capacity_, kMinimumRingSize);
    }

    ~Ring() {
      while (!empty()) {
        pop_front();
      }
      delete[] reinterpret_cast<char*>(data_);
    }

    bool empty() const { return back_index_ == front_index_; }

    size_t capacity() const { return capacity_; }

    bool CanPush() const {
      return front_index_ != CircularIncrement(back_index_);
    }

    void push_front(T&& t) {
      // Mustn't appear to become empty.
      DCHECK_NE(CircularDecrement(front_index_), back_index_);
      new (&data_[front_index_]) T(std::move(t));
      front_index_ = CircularDecrement(front_index_);
    }

    void push_back(T&& t) {
      back_index_ = CircularIncrement(back_index_);
      DCHECK(!empty());  // Mustn't appear to become empty.
      new (&data_[back_index_]) T(std::move(t));
    }

    bool CanPop() const { return front_index_ != back_index_; }

    void pop_front() {
      DCHECK(!empty());
      front_index_ = CircularIncrement(front_index_);
      data_[front_index_].~T();
    }

    T& front() {
      DCHECK(!empty());
      return data_[CircularIncrement(front_index_)];
    }

    const T& front() const {
      DCHECK(!empty());
      return data_[CircularIncrement(front_index_)];
    }

    T& back() {
      DCHECK(!empty());
      return data_[back_index_];
    }

    const T& back() const {
      DCHECK(!empty());
      return data_[back_index_];
    }

    size_t CircularDecrement(size_t index) const {
      if (index == 0)
        return capacity_ - 1;
      return index - 1;
    }

    size_t CircularIncrement(size_t index) const {
      DCHECK_LT(index, capacity_);
      ++index;
      if (index == capacity_)
        return 0;
      return index;
    }

    size_t capacity_;
    size_t front_index_;
    size_t back_index_;
    T* data_;
    std::unique_ptr<Ring> next_;

    DISALLOW_COPY_AND_ASSIGN(Ring);
  };

 public:
  class Iterator {
   public:
    using value_type = T;
    using pointer = const T*;
    using reference = const T&;

    const T& operator->() const { return ring_->data_[index_]; }
    const T& operator*() const { return ring_->data_[index_]; }

    Iterator& operator++() {
      if (index_ == ring_->back_index_) {
        ring_ = ring_->next_.get();
        index_ = ring_ ? ring_->CircularIncrement(ring_->front_index_) : 0;
      } else {
        index_ = ring_->CircularIncrement(index_);
      }
      return *this;
    }

    operator bool() const { return !!ring_; }

   private:
    explicit Iterator(const Ring* ring) {
      if (!ring || ring->empty()) {
        ring_ = nullptr;
        index_ = 0;
        return;
      }

      ring_ = ring;
      index_ = ring_->CircularIncrement(ring->front_index_);
    }

    const Ring* ring_;
    size_t index_;

    friend class LazilyDeallocatedDeque;
  };

  Iterator begin() const { return Iterator(head_.get()); }

  Iterator end() const { return Iterator(nullptr); }

 private:
  // We maintain a list of Ring buffers, to enable us to grow without copying,
  // but most of the time we aim to have only one active Ring.
  std::unique_ptr<Ring> head_;
  Ring* tail_ = nullptr;

  size_t size_ = 0;
  size_t max_size_ = 0;
  TimeTicks next_resize_time_;

  DISALLOW_COPY_AND_ASSIGN(LazilyDeallocatedDeque);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_LAZILY_DEALLOCATED_DEQUE_H_
