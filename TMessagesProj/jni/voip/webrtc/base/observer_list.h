// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_OBSERVER_LIST_H_
#define BASE_OBSERVER_LIST_H_

#include <stddef.h>

#include <algorithm>
#include <iterator>
#include <limits>
#include <utility>
#include <vector>

#include "base/gtest_prod_util.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/observer_list_internal.h"
#include "base/sequence_checker.h"
#include "base/stl_util.h"

///////////////////////////////////////////////////////////////////////////////
//
// OVERVIEW:
//
//   A list of observers. Unlike a standard vector or list, this container can
//   be modified during iteration without invalidating the iterator. So, it
//   safely handles the case of an observer removing itself or other observers
//   from the list while observers are being notified.
//
//
// WARNING:
//
//   ObserverList is not thread-compatible. Iterating on the same ObserverList
//   simultaneously in different threads is not safe, even when the ObserverList
//   itself is not modified.
//
//   For a thread-safe observer list, see ObserverListThreadSafe.
//
//
// TYPICAL USAGE:
//
//   class MyWidget {
//    public:
//     ...
//
//     class Observer : public base::CheckedObserver {
//      public:
//       virtual void OnFoo(MyWidget* w) = 0;
//       virtual void OnBar(MyWidget* w, int x, int y) = 0;
//     };
//
//     void AddObserver(Observer* obs) {
//       observers_.AddObserver(obs);
//     }
//
//     void RemoveObserver(Observer* obs) {
//       observers_.RemoveObserver(obs);
//     }
//
//     void NotifyFoo() {
//       for (Observer& obs : observers_)
//         obs.OnFoo(this);
//     }
//
//     void NotifyBar(int x, int y) {
//       for (Observer& obs : observers_)
//         obs.OnBar(this, x, y);
//     }
//
//    private:
//     base::ObserverList<Observer> observers_;
//   };
//
//
///////////////////////////////////////////////////////////////////////////////

namespace base {

// Enumeration of which observers are notified by ObserverList.
enum class ObserverListPolicy {
  // Specifies that any observers added during notification are notified.
  // This is the default policy if no policy is provided to the constructor.
  ALL,

  // Specifies that observers added while sending out notification are not
  // notified.
  EXISTING_ONLY,
};

// When check_empty is true, assert that the list is empty on destruction.
// When allow_reentrancy is false, iterating throught the list while already in
// the iteration loop will result in DCHECK failure.
// TODO(oshima): Change the default to non reentrant. https://crbug.com/812109
template <class ObserverType,
          bool check_empty = false,
          bool allow_reentrancy = true,
          class ObserverStorageType = internal::CheckedObserverAdapter>
class ObserverList {
 public:
  // Allow declaring an ObserverList<...>::Unchecked that replaces the default
  // ObserverStorageType to use raw pointers. This is required to support legacy
  // observers that do not inherit from CheckedObserver. The majority of new
  // code should not use this, but it may be suited for performance-critical
  // situations to avoid overheads of a CHECK(). Note the type can't be chosen
  // based on ObserverType's definition because ObserverLists are often declared
  // in headers using a forward-declare of ObserverType.
  using Unchecked = ObserverList<ObserverType,
                                 check_empty,
                                 allow_reentrancy,
                                 internal::UncheckedObserverAdapter>;

  // An iterator class that can be used to access the list of observers.
  class Iter {
   public:
    using iterator_category = std::forward_iterator_tag;
    using value_type = ObserverType;
    using difference_type = ptrdiff_t;
    using pointer = ObserverType*;
    using reference = ObserverType&;

    Iter() : index_(0), max_index_(0) {}

    explicit Iter(const ObserverList* list)
        : list_(const_cast<ObserverList*>(list)),
          index_(0),
          max_index_(list->policy_ == ObserverListPolicy::ALL
                         ? std::numeric_limits<size_t>::max()
                         : list->observers_.size()) {
      DCHECK(list);
      DCHECK(allow_reentrancy || list_.IsOnlyRemainingNode());
      // Bind to this sequence when creating the first iterator.
      DCHECK_CALLED_ON_VALID_SEQUENCE(list_->iteration_sequence_checker_);
      EnsureValidIndex();
    }

    ~Iter() {
      if (list_.IsOnlyRemainingNode())
        list_->Compact();
    }

    Iter(const Iter& other)
        : index_(other.index_), max_index_(other.max_index_) {
      if (other.list_)
        list_.SetList(other.list_.get());
    }

    Iter& operator=(const Iter& other) {
      if (&other == this)
        return *this;

      if (list_.IsOnlyRemainingNode())
        list_->Compact();

      list_.Invalidate();
      if (other.list_)
        list_.SetList(other.list_.get());

      index_ = other.index_;
      max_index_ = other.max_index_;
      return *this;
    }

    bool operator==(const Iter& other) const {
      return (is_end() && other.is_end()) ||
             (list_.get() == other.list_.get() && index_ == other.index_);
    }

    bool operator!=(const Iter& other) const { return !(*this == other); }

    Iter& operator++() {
      if (list_) {
        ++index_;
        EnsureValidIndex();
      }
      return *this;
    }

    Iter operator++(int) {
      Iter it(*this);
      ++(*this);
      return it;
    }

    ObserverType* operator->() const {
      ObserverType* const current = GetCurrent();
      DCHECK(current);
      return current;
    }

    ObserverType& operator*() const {
      ObserverType* const current = GetCurrent();
      DCHECK(current);
      return *current;
    }

   private:
    friend class ObserverListTestBase;

    ObserverType* GetCurrent() const {
      DCHECK(list_);
      DCHECK_LT(index_, clamped_max_index());
      return ObserverStorageType::template Get<ObserverType>(
          list_->observers_[index_]);
    }

    void EnsureValidIndex() {
      DCHECK(list_);
      const size_t max_index = clamped_max_index();
      while (index_ < max_index &&
             list_->observers_[index_].IsMarkedForRemoval()) {
        ++index_;
      }
    }

    size_t clamped_max_index() const {
      return std::min(max_index_, list_->observers_.size());
    }

    bool is_end() const { return !list_ || index_ == clamped_max_index(); }

    // Lightweight weak pointer to the ObserverList.
    internal::WeakLinkNode<ObserverList> list_;

    // When initially constructed and each time the iterator is incremented,
    // |index_| is guaranteed to point to a non-null index if the iterator
    // has not reached the end of the ObserverList.
    size_t index_;
    size_t max_index_;
  };

  using iterator = Iter;
  using const_iterator = Iter;
  using value_type = ObserverType;

  const_iterator begin() const {
    // An optimization: do not involve weak pointers for empty list.
    return observers_.empty() ? const_iterator() : const_iterator(this);
  }

  const_iterator end() const { return const_iterator(); }

  explicit ObserverList(ObserverListPolicy policy = ObserverListPolicy::ALL)
      : policy_(policy) {
    // Sequence checks only apply when iterators are live.
    DETACH_FROM_SEQUENCE(iteration_sequence_checker_);
  }

  ~ObserverList() {
    // If there are live iterators, ensure destruction is thread-safe.
    if (!live_iterators_.empty())
      DCHECK_CALLED_ON_VALID_SEQUENCE(iteration_sequence_checker_);

    while (!live_iterators_.empty())
      live_iterators_.head()->value()->Invalidate();
    if (check_empty) {
      Compact();
      DCHECK(observers_.empty());
    }
  }

  // Add an observer to this list. An observer should not be added to the same
  // list more than once.
  //
  // Precondition: obs != nullptr
  // Precondition: !HasObserver(obs)
  void AddObserver(ObserverType* obs) {
    DCHECK(obs);
    if (HasObserver(obs)) {
      NOTREACHED() << "Observers can only be added once!";
      return;
    }
    observers_.emplace_back(ObserverStorageType(obs));
  }

  // Removes the given observer from this list. Does nothing if this observer is
  // not in this list.
  void RemoveObserver(const ObserverType* obs) {
    DCHECK(obs);
    const auto it =
        std::find_if(observers_.begin(), observers_.end(),
                     [obs](const auto& o) { return o.IsEqual(obs); });
    if (it == observers_.end())
      return;

    if (live_iterators_.empty()) {
      observers_.erase(it);
    } else {
      DCHECK_CALLED_ON_VALID_SEQUENCE(iteration_sequence_checker_);
      it->MarkForRemoval();
    }
  }

  // Determine whether a particular observer is in the list.
  bool HasObserver(const ObserverType* obs) const {
    // Client code passing null could be confused by the treatment of observers
    // removed mid-iteration. TODO(https://crbug.com/876588): This should
    // probably DCHECK, but some client code currently does pass null.
    if (obs == nullptr)
      return false;
    return std::find_if(observers_.begin(), observers_.end(),
                        [obs](const auto& o) { return o.IsEqual(obs); }) !=
           observers_.end();
  }

  // Removes all the observers from this list.
  void Clear() {
    if (live_iterators_.empty()) {
      observers_.clear();
    } else {
      DCHECK_CALLED_ON_VALID_SEQUENCE(iteration_sequence_checker_);
      for (auto& observer : observers_)
        observer.MarkForRemoval();
    }
  }

  bool might_have_observers() const { return !observers_.empty(); }

 private:
  friend class internal::WeakLinkNode<ObserverList>;

  // Compacts list of observers by removing those marked for removal.
  void Compact() {
    // Detach whenever the last iterator is destroyed. Detaching is safe because
    // Compact() is only ever called when the last iterator is destroyed.
    DETACH_FROM_SEQUENCE(iteration_sequence_checker_);

    EraseIf(observers_, [](const auto& o) { return o.IsMarkedForRemoval(); });
  }

  std::vector<ObserverStorageType> observers_;

  base::LinkedList<internal::WeakLinkNode<ObserverList>> live_iterators_;

  const ObserverListPolicy policy_;

  SEQUENCE_CHECKER(iteration_sequence_checker_);

  DISALLOW_COPY_AND_ASSIGN(ObserverList);
};

template <class ObserverType, bool check_empty = false>
using ReentrantObserverList = ObserverList<ObserverType, check_empty, true>;

}  // namespace base

#endif  // BASE_OBSERVER_LIST_H_
