// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_OBSERVER_LIST_THREADSAFE_H_
#define BASE_OBSERVER_LIST_THREADSAFE_H_

#include <unordered_map>
#include <utility>

#include "base/base_export.h"
#include "base/bind.h"
#include "base/lazy_instance.h"
#include "base/location.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/observer_list.h"
#include "base/sequenced_task_runner.h"
#include "base/stl_util.h"
#include "base/synchronization/lock.h"
#include "base/threading/sequenced_task_runner_handle.h"
#include "base/threading/thread_local.h"
#include "build/build_config.h"

///////////////////////////////////////////////////////////////////////////////
//
// OVERVIEW:
//
//   A thread-safe container for a list of observers. This is similar to the
//   observer_list (see observer_list.h), but it is more robust for multi-
//   threaded situations.
//
//   The following use cases are supported:
//    * Observers can register for notifications from any sequence. They are
//      always notified on the sequence from which they were registered.
//    * Any sequence may trigger a notification via Notify().
//    * Observers can remove themselves from the observer list inside of a
//      callback.
//    * If one sequence is notifying observers concurrently with an observer
//      removing itself from the observer list, the notifications will be
//      silently dropped.
//
//   The drawback of the threadsafe observer list is that notifications are not
//   as real-time as the non-threadsafe version of this class. Notifications
//   will always be done via PostTask() to another sequence, whereas with the
//   non-thread-safe observer_list, notifications happen synchronously.
//
///////////////////////////////////////////////////////////////////////////////

namespace base {
namespace internal {

class BASE_EXPORT ObserverListThreadSafeBase
    : public RefCountedThreadSafe<ObserverListThreadSafeBase> {
 public:
  ObserverListThreadSafeBase() = default;

 protected:
  template <typename ObserverType, typename Method>
  struct Dispatcher;

  template <typename ObserverType, typename ReceiverType, typename... Params>
  struct Dispatcher<ObserverType, void (ReceiverType::*)(Params...)> {
    static void Run(void (ReceiverType::*m)(Params...),
                    Params... params,
                    ObserverType* obj) {
      (obj->*m)(std::forward<Params>(params)...);
    }
  };

  struct NotificationDataBase {
    NotificationDataBase(void* observer_list_in, const Location& from_here_in)
        : observer_list(observer_list_in), from_here(from_here_in) {}

    void* observer_list;
    Location from_here;
  };

  virtual ~ObserverListThreadSafeBase() = default;

  static LazyInstance<ThreadLocalPointer<const NotificationDataBase>>::Leaky
      tls_current_notification_;

 private:
  friend class RefCountedThreadSafe<ObserverListThreadSafeBase>;

  DISALLOW_COPY_AND_ASSIGN(ObserverListThreadSafeBase);
};

}  // namespace internal

template <class ObserverType>
class ObserverListThreadSafe : public internal::ObserverListThreadSafeBase {
 public:
  ObserverListThreadSafe() = default;
  explicit ObserverListThreadSafe(ObserverListPolicy policy)
      : policy_(policy) {}

  // Adds |observer| to the list. |observer| must not already be in the list.
  void AddObserver(ObserverType* observer) {
    // TODO(fdoray): Change this to a DCHECK once all call sites have a
    // SequencedTaskRunnerHandle.
    if (!SequencedTaskRunnerHandle::IsSet())
      return;

    AutoLock auto_lock(lock_);

    // Add |observer| to the list of observers.
    DCHECK(!Contains(observers_, observer));
    const scoped_refptr<SequencedTaskRunner> task_runner =
        SequencedTaskRunnerHandle::Get();
    observers_[observer] = task_runner;

    // If this is called while a notification is being dispatched on this thread
    // and |policy_| is ALL, |observer| must be notified (if a notification is
    // being dispatched on another thread in parallel, the notification may or
    // may not make it to |observer| depending on the outcome of the race to
    // |lock_|).
    if (policy_ == ObserverListPolicy::ALL) {
      const NotificationDataBase* current_notification =
          tls_current_notification_.Get().Get();
      if (current_notification && current_notification->observer_list == this) {
        task_runner->PostTask(
            current_notification->from_here,
            BindOnce(
                &ObserverListThreadSafe<ObserverType>::NotifyWrapper, this,
                observer,
                *static_cast<const NotificationData*>(current_notification)));
      }
    }
  }

  // Remove an observer from the list if it is in the list.
  //
  // If a notification was sent to the observer but hasn't started to run yet,
  // it will be aborted. If a notification has started to run, removing the
  // observer won't stop it.
  void RemoveObserver(ObserverType* observer) {
    AutoLock auto_lock(lock_);
    observers_.erase(observer);
  }

  // Verifies that the list is currently empty (i.e. there are no observers).
  void AssertEmpty() const {
#if DCHECK_IS_ON()
    AutoLock auto_lock(lock_);
    DCHECK(observers_.empty());
#endif
  }

  // Asynchronously invokes a callback on all observers, on their registration
  // sequence. You cannot assume that at the completion of the Notify call that
  // all Observers have been Notified. The notification may still be pending
  // delivery.
  template <typename Method, typename... Params>
  void Notify(const Location& from_here, Method m, Params&&... params) {
    RepeatingCallback<void(ObserverType*)> method =
        BindRepeating(&Dispatcher<ObserverType, Method>::Run, m,
                      std::forward<Params>(params)...);

    AutoLock lock(lock_);
    for (const auto& observer : observers_) {
      observer.second->PostTask(
          from_here,
          BindOnce(&ObserverListThreadSafe<ObserverType>::NotifyWrapper, this,
                   observer.first, NotificationData(this, from_here, method)));
    }
  }

  // Like Notify() but attempts to synchronously invoke callbacks if they are
  // associated with this thread.
  template <typename Method, typename... Params>
  void NotifySynchronously(const Location& from_here,
                           Method m,
                           Params&&... params) {
    RepeatingCallback<void(ObserverType*)> method =
        BindRepeating(&Dispatcher<ObserverType, Method>::Run, m,
                      std::forward<Params>(params)...);

    // The observers may make reentrant calls (which can be a problem due to the
    // lock), so we extract a list to call synchronously.
    std::vector<ObserverType*> current_sequence_observers;

    {
      AutoLock lock(lock_);
      current_sequence_observers.reserve(observers_.size());
      for (const auto& observer : observers_) {
        if (observer.second->RunsTasksInCurrentSequence()) {
          current_sequence_observers.push_back(observer.first);
        } else {
          observer.second->PostTask(
              from_here,
              BindOnce(&ObserverListThreadSafe<ObserverType>::NotifyWrapper,
                       this, observer.first,
                       NotificationData(this, from_here, method)));
        }
      }
    }

    for (ObserverType* observer : current_sequence_observers) {
      NotifyWrapper(observer, NotificationData(this, from_here, method));
    }
  }

 private:
  friend class RefCountedThreadSafe<ObserverListThreadSafeBase>;

  struct NotificationData : public NotificationDataBase {
    NotificationData(ObserverListThreadSafe* observer_list_in,
                     const Location& from_here_in,
                     const RepeatingCallback<void(ObserverType*)>& method_in)
        : NotificationDataBase(observer_list_in, from_here_in),
          method(method_in) {}

    RepeatingCallback<void(ObserverType*)> method;
  };

  ~ObserverListThreadSafe() override = default;

  void NotifyWrapper(ObserverType* observer,
                     const NotificationData& notification) {
    {
      AutoLock auto_lock(lock_);

      // Check whether the observer still needs a notification.
      auto it = observers_.find(observer);
      if (it == observers_.end())
        return;
      DCHECK(it->second->RunsTasksInCurrentSequence());
    }

    // Keep track of the notification being dispatched on the current thread.
    // This will be used if the callback below calls AddObserver().
    //
    // Note: |tls_current_notification_| may not be nullptr if this runs in a
    // nested loop started by a notification callback. In that case, it is
    // important to save the previous value to restore it later.
    auto& tls_current_notification = tls_current_notification_.Get();
    const NotificationDataBase* const previous_notification =
        tls_current_notification.Get();
    tls_current_notification.Set(&notification);

    // Invoke the callback.
    notification.method.Run(observer);

    // Reset the notification being dispatched on the current thread to its
    // previous value.
    tls_current_notification.Set(previous_notification);
  }

  const ObserverListPolicy policy_ = ObserverListPolicy::ALL;

  // Synchronizes access to |observers_|.
  mutable Lock lock_;

  // Keys are observers. Values are the SequencedTaskRunners on which they must
  // be notified.
  std::unordered_map<ObserverType*, scoped_refptr<SequencedTaskRunner>>
      observers_;

  DISALLOW_COPY_AND_ASSIGN(ObserverListThreadSafe);
};

}  // namespace base

#endif  // BASE_OBSERVER_LIST_THREADSAFE_H_
