// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ONE_SHOT_EVENT_H_
#define BASE_ONE_SHOT_EVENT_H_

#include <vector>

#include "base/callback_forward.h"
#include "base/logging.h"
#include "base/memory/ref_counted.h"
#include "base/memory/weak_ptr.h"
#include "base/threading/thread_checker.h"

namespace base {

class Location;
class SingleThreadTaskRunner;
class TimeDelta;

// This class represents an event that's expected to happen once.  It
// allows clients to guarantee that code is run after the OneShotEvent
// is signaled.  If the OneShotEvent is destroyed before it's
// signaled, the closures are destroyed without being run.
//
// This class is similar to a WaitableEvent combined with several
// WaitableEventWatchers, but using it is simpler.
//
// This class is not thread-safe, and must be used from a single thread.
class BASE_EXPORT OneShotEvent {
 public:
  OneShotEvent();
  // Use the following constructor to create an already signaled event. This is
  // useful if you construct the event on a different thread from where it is
  // used, in which case it is not possible to call Signal() just after
  // construction.
  explicit OneShotEvent(bool signaled);
  ~OneShotEvent();

  // True if Signal has been called.  This function is mostly for
  // migrating old code; usually calling Post() unconditionally will
  // result in more readable code.
  bool is_signaled() const {
    DCHECK(thread_checker_.CalledOnValidThread());
    return signaled_;
  }

  // Causes is_signaled() to return true and all queued tasks to be
  // run in an arbitrary order.  This method must only be called once.
  void Signal();

  // Scheduled |task| to be called on |runner| after is_signaled()
  // becomes true. If called with |delay|, then the task will happen
  // (roughly) |delay| after is_signaled(), *not* |delay| after the
  // post. Inside |task|, if this OneShotEvent is still alive,
  // CHECK(is_signaled()) will never fail (which implies that
  // OneShotEvent::Reset() doesn't exist).
  //
  // If |*this| is destroyed before being released, none of these
  // tasks will be executed.
  //
  // Omitting the |runner| argument indicates that |task| should run
  // on current thread's TaskRunner.
  //
  // Tasks may be run in an arbitrary order, not just FIFO.  Tasks
  // will never be called on the current thread before this function
  // returns.  Beware that there's no simple way to wait for all tasks
  // on a OneShotEvent to complete, so it's almost never safe to use
  // base::Unretained() when creating one.
  //
  // Const because Post() doesn't modify the logical state of this
  // object (which is just the is_signaled() bit).
  void Post(const Location& from_here, OnceClosure task) const;
  void Post(const Location& from_here,
            OnceClosure task,
            const scoped_refptr<SingleThreadTaskRunner>& runner) const;
  void PostDelayed(const Location& from_here,
                   OnceClosure task,
                   const TimeDelta& delay) const;

 private:
  struct TaskInfo;

  void PostImpl(const Location& from_here,
                OnceClosure task,
                const scoped_refptr<SingleThreadTaskRunner>& runner,
                const TimeDelta& delay) const;

  ThreadChecker thread_checker_;

  bool signaled_;

  // The task list is mutable because it's not part of the logical
  // state of the object.  This lets us return const references to the
  // OneShotEvent to clients that just want to run tasks through it
  // without worrying that they'll signal the event.
  //
  // Optimization note: We could reduce the size of this class to a
  // single pointer by storing |signaled_| in the low bit of a
  // pointer, and storing the size and capacity of the array (if any)
  // on the far end of the pointer.
  mutable std::vector<TaskInfo> tasks_;
};

}  // namespace base

#endif  // BASE_ONE_SHOT_EVENT_H_
