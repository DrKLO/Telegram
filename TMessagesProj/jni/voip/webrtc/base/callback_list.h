// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CALLBACK_LIST_H_
#define BASE_CALLBACK_LIST_H_

#include <list>
#include <memory>

#include "base/bind.h"
#include "base/callback.h"
#include "base/compiler_specific.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"

// OVERVIEW:
//
// A container for a list of (repeating) callbacks. Unlike a normal vector or
// list, this container can be modified during iteration without invalidating
// the iterator. It safely handles the case of a callback removing itself or
// another callback from the list while callbacks are being run.
//
// TYPICAL USAGE:
//
// class MyWidget {
//  public:
//   ...
//
//   std::unique_ptr<base::CallbackList<void(const Foo&)>::Subscription>
//   RegisterCallback(const base::RepeatingCallback<void(const Foo&)>& cb) {
//     return callback_list_.Add(cb);
//   }
//
//  private:
//   void NotifyFoo(const Foo& foo) {
//      callback_list_.Notify(foo);
//   }
//
//   base::CallbackList<void(const Foo&)> callback_list_;
//
//   DISALLOW_COPY_AND_ASSIGN(MyWidget);
// };
//
//
// class MyWidgetListener {
//  public:
//   MyWidgetListener::MyWidgetListener() {
//     foo_subscription_ = MyWidget::GetCurrent()->RegisterCallback(
//             base::BindRepeating(&MyWidgetListener::OnFoo, this)));
//   }
//
//   MyWidgetListener::~MyWidgetListener() {
//      // Subscription gets deleted automatically and will deregister
//      // the callback in the process.
//   }
//
//  private:
//   void OnFoo(const Foo& foo) {
//     // Do something.
//   }
//
//   std::unique_ptr<base::CallbackList<void(const Foo&)>::Subscription>
//       foo_subscription_;
//
//   DISALLOW_COPY_AND_ASSIGN(MyWidgetListener);
// };

namespace base {

namespace internal {

template <typename CallbackType>
class CallbackListBase {
 public:
  class Subscription {
   public:
    explicit Subscription(base::OnceClosure subscription_destroyed)
        : subscription_destroyed_(std::move(subscription_destroyed)) {}

    ~Subscription() { std::move(subscription_destroyed_).Run(); }

    // Returns true if the CallbackList associated with this subscription has
    // been deleted, which means that the associated callback will no longer be
    // invoked.
    bool IsCancelled() const { return subscription_destroyed_.IsCancelled(); }

   private:
    base::OnceClosure subscription_destroyed_;

    DISALLOW_COPY_AND_ASSIGN(Subscription);
  };

  // Add a callback to the list. The callback will remain registered until the
  // returned Subscription is destroyed. When the CallbackList is destroyed, any
  // outstanding subscriptions are safely invalidated.
  std::unique_ptr<Subscription> Add(const CallbackType& cb) WARN_UNUSED_RESULT {
    DCHECK(!cb.is_null());
    return std::make_unique<Subscription>(
        base::BindOnce(&CallbackListBase::OnSubscriptionDestroyed,
                       weak_ptr_factory_.GetWeakPtr(),
                       callbacks_.insert(callbacks_.end(), cb)));
  }

  // Sets a callback which will be run when a subscription list is changed.
  void set_removal_callback(const RepeatingClosure& callback) {
    removal_callback_ = callback;
  }

  // Returns true if there are no subscriptions. This is only valid to call when
  // not looping through the list.
  bool empty() {
    DCHECK_EQ(0u, active_iterator_count_);
    return callbacks_.empty();
  }

 protected:
  // An iterator class that can be used to access the list of callbacks.
  class Iterator {
   public:
    explicit Iterator(CallbackListBase<CallbackType>* list)
        : list_(list),
          list_iter_(list_->callbacks_.begin()) {
      ++list_->active_iterator_count_;
    }

    Iterator(const Iterator& iter)
        : list_(iter.list_),
          list_iter_(iter.list_iter_) {
      ++list_->active_iterator_count_;
    }

    ~Iterator() {
      if (list_ && --list_->active_iterator_count_ == 0) {
        list_->Compact();
      }
    }

    CallbackType* GetNext() {
      while ((list_iter_ != list_->callbacks_.end()) && list_iter_->is_null())
        ++list_iter_;

      CallbackType* cb = nullptr;
      if (list_iter_ != list_->callbacks_.end()) {
        cb = &(*list_iter_);
        ++list_iter_;
      }
      return cb;
    }

   private:
    CallbackListBase<CallbackType>* list_;
    typename std::list<CallbackType>::iterator list_iter_;
  };

  CallbackListBase() = default;

  ~CallbackListBase() { DCHECK_EQ(0u, active_iterator_count_); }

  // Returns an instance of a CallbackListBase::Iterator which can be used
  // to run callbacks.
  Iterator GetIterator() {
    return Iterator(this);
  }

  // Compact the list: remove any entries which were nulled out during
  // iteration.
  void Compact() {
    auto it = callbacks_.begin();
    bool updated = false;
    while (it != callbacks_.end()) {
      if ((*it).is_null()) {
        updated = true;
        it = callbacks_.erase(it);
      } else {
        ++it;
      }
    }

    if (updated && !removal_callback_.is_null())
      removal_callback_.Run();
  }

 private:
  void OnSubscriptionDestroyed(
      const typename std::list<CallbackType>::iterator& iter) {
    if (active_iterator_count_) {
      iter->Reset();
    } else {
      callbacks_.erase(iter);
      if (removal_callback_)
        removal_callback_.Run();
    }
    // Note that |removal_callback_| may destroy |this|.
  }

  std::list<CallbackType> callbacks_;
  size_t active_iterator_count_ = 0;
  RepeatingClosure removal_callback_;
  WeakPtrFactory<CallbackListBase> weak_ptr_factory_{this};

  DISALLOW_COPY_AND_ASSIGN(CallbackListBase);
};

}  // namespace internal

template <typename Sig> class CallbackList;

template <typename... Args>
class CallbackList<void(Args...)>
    : public internal::CallbackListBase<RepeatingCallback<void(Args...)>> {
 public:
  using CallbackType = RepeatingCallback<void(Args...)>;

  CallbackList() = default;

  template <typename... RunArgs>
  void Notify(RunArgs&&... args) {
    auto it = this->GetIterator();
    CallbackType* cb;
    while ((cb = it.GetNext()) != nullptr) {
      cb->Run(args...);
    }
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(CallbackList);
};

}  // namespace base

#endif  // BASE_CALLBACK_LIST_H_
