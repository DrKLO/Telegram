// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_COMMON_INTRUSIVE_HEAP_H_
#define BASE_TASK_COMMON_INTRUSIVE_HEAP_H_

#include "base/containers/intrusive_heap.h"

namespace base {
namespace internal {

using HeapHandle = base::HeapHandle;

template <typename T>
struct IntrusiveHeapImpl {
  struct GreaterUsingLessEqual {
    bool operator()(const T& t1, const T& t2) const { return t2 <= t1; }
  };

  using type = base::IntrusiveHeap<T, GreaterUsingLessEqual>;
};

// base/task wants a min-heap that uses the <= operator, whereas
// base::IntrusiveHeap is a max-heap by default. This is a very thin adapter
// over that class that exposes minimal functionality required by the
// base/task IntrusiveHeap clients.
template <typename T>
class IntrusiveHeap : private IntrusiveHeapImpl<T>::type {
 public:
  using IntrusiveHeapImplType = typename IntrusiveHeapImpl<T>::type;

  // The majority of sets in the scheduler have 0-3 items in them (a few will
  // have perhaps up to 100), so this means we usually only have to allocate
  // memory once.
  static constexpr size_t kMinimumHeapSize = 4;

  IntrusiveHeap() { IntrusiveHeapImplType::reserve(kMinimumHeapSize); }

  ~IntrusiveHeap() = default;

  IntrusiveHeap& operator=(IntrusiveHeap&& other) = default;

  bool empty() const { return IntrusiveHeapImplType::empty(); }
  size_t size() const { return IntrusiveHeapImplType::size(); }

  void Clear() {
    IntrusiveHeapImplType::clear();
    IntrusiveHeapImplType::reserve(kMinimumHeapSize);
  }

  const T& Min() const { return IntrusiveHeapImplType::top(); }
  void Pop() { IntrusiveHeapImplType::pop(); }

  void insert(T&& element) {
    IntrusiveHeapImplType::insert(std::move(element));
  }

  void erase(HeapHandle handle) { IntrusiveHeapImplType::erase(handle); }

  void ReplaceMin(T&& element) {
    IntrusiveHeapImplType::ReplaceTop(std::move(element));
  }

  void ChangeKey(HeapHandle handle, T&& element) {
    IntrusiveHeapImplType::Replace(handle, std::move(element));
  }

  const T& at(HeapHandle handle) const {
    return IntrusiveHeapImplType::at(handle);
  }

  // Caution, mutating the heap invalidates iterators!
  const T* begin() const { return IntrusiveHeapImplType::data(); }
  const T* end() const { return IntrusiveHeapImplType::data() + size(); }
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_COMMON_INTRUSIVE_HEAP_H_
