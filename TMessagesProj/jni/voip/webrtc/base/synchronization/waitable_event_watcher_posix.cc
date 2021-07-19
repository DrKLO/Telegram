// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/synchronization/waitable_event_watcher.h"

#include <utility>

#include "base/bind.h"
#include "base/logging.h"
#include "base/synchronization/lock.h"
#include "base/threading/sequenced_task_runner_handle.h"

namespace base {

// -----------------------------------------------------------------------------
// WaitableEventWatcher (async waits).
//
// The basic design is that we add an AsyncWaiter to the wait-list of the event.
// That AsyncWaiter has a pointer to SequencedTaskRunner, and a Task to be
// posted to it. The task ends up calling the callback when it runs on the
// sequence.
//
// Since the wait can be canceled, we have a thread-safe Flag object which is
// set when the wait has been canceled. At each stage in the above, we check the
// flag before going onto the next stage. Since the wait may only be canceled in
// the sequence which runs the Task, we are assured that the callback cannot be
// called after canceling...

// -----------------------------------------------------------------------------
// A thread-safe, reference-counted, write-once flag.
// -----------------------------------------------------------------------------
class Flag : public RefCountedThreadSafe<Flag> {
 public:
  Flag() { flag_ = false; }

  void Set() {
    AutoLock locked(lock_);
    flag_ = true;
  }

  bool value() const {
    AutoLock locked(lock_);
    return flag_;
  }

 private:
  friend class RefCountedThreadSafe<Flag>;
  ~Flag() = default;

  mutable Lock lock_;
  bool flag_;

  DISALLOW_COPY_AND_ASSIGN(Flag);
};

// -----------------------------------------------------------------------------
// This is an asynchronous waiter which posts a task to a SequencedTaskRunner
// when fired. An AsyncWaiter may only be in a single wait-list.
// -----------------------------------------------------------------------------
class AsyncWaiter : public WaitableEvent::Waiter {
 public:
  AsyncWaiter(scoped_refptr<SequencedTaskRunner> task_runner,
              base::OnceClosure callback,
              Flag* flag)
      : task_runner_(std::move(task_runner)),
        callback_(std::move(callback)),
        flag_(flag) {}

  bool Fire(WaitableEvent* event) override {
    // Post the callback if we haven't been cancelled.
    if (!flag_->value())
      task_runner_->PostTask(FROM_HERE, std::move(callback_));

    // We are removed from the wait-list by the WaitableEvent itself. It only
    // remains to delete ourselves.
    delete this;

    // We can always return true because an AsyncWaiter is never in two
    // different wait-lists at the same time.
    return true;
  }

  // See StopWatching for discussion
  bool Compare(void* tag) override { return tag == flag_.get(); }

 private:
  const scoped_refptr<SequencedTaskRunner> task_runner_;
  base::OnceClosure callback_;
  const scoped_refptr<Flag> flag_;
};

// -----------------------------------------------------------------------------
// For async waits we need to run a callback on a sequence. We do this by
// posting an AsyncCallbackHelper task, which calls the callback and keeps track
// of when the event is canceled.
// -----------------------------------------------------------------------------
void AsyncCallbackHelper(Flag* flag,
                         WaitableEventWatcher::EventCallback callback,
                         WaitableEvent* event) {
  // Runs on the sequence that called StartWatching().
  if (!flag->value()) {
    // This is to let the WaitableEventWatcher know that the event has occured.
    flag->Set();
    std::move(callback).Run(event);
  }
}

WaitableEventWatcher::WaitableEventWatcher() {
  sequence_checker_.DetachFromSequence();
}

WaitableEventWatcher::~WaitableEventWatcher() {
  // The destructor may be called from a different sequence than StartWatching()
  // when there is no active watch. To avoid triggering a DCHECK in
  // StopWatching(), do not call it when there is no active watch.
  if (cancel_flag_ && !cancel_flag_->value())
    StopWatching();
}

// -----------------------------------------------------------------------------
// The Handle is how the user cancels a wait. After deleting the Handle we
// insure that the delegate cannot be called.
// -----------------------------------------------------------------------------
bool WaitableEventWatcher::StartWatching(
    WaitableEvent* event,
    EventCallback callback,
    scoped_refptr<SequencedTaskRunner> task_runner) {
  DCHECK(sequence_checker_.CalledOnValidSequence());

  // A user may call StartWatching from within the callback function. In this
  // case, we won't know that we have finished watching, expect that the Flag
  // will have been set in AsyncCallbackHelper().
  if (cancel_flag_.get() && cancel_flag_->value())
    cancel_flag_ = nullptr;

  DCHECK(!cancel_flag_) << "StartWatching called while still watching";

  cancel_flag_ = new Flag;
  OnceClosure internal_callback =
      base::BindOnce(&AsyncCallbackHelper, base::RetainedRef(cancel_flag_),
                     std::move(callback), event);
  WaitableEvent::WaitableEventKernel* kernel = event->kernel_.get();

  AutoLock locked(kernel->lock_);

  if (kernel->signaled_) {
    if (!kernel->manual_reset_)
      kernel->signaled_ = false;

    // No hairpinning - we can't call the delegate directly here. We have to
    // post a task to |task_runner| as usual.
    task_runner->PostTask(FROM_HERE, std::move(internal_callback));
    return true;
  }

  kernel_ = kernel;
  waiter_ = new AsyncWaiter(std::move(task_runner),
                            std::move(internal_callback), cancel_flag_.get());
  event->Enqueue(waiter_);

  return true;
}

void WaitableEventWatcher::StopWatching() {
  DCHECK(sequence_checker_.CalledOnValidSequence());

  if (!cancel_flag_.get())  // if not currently watching...
    return;

  if (cancel_flag_->value()) {
    // In this case, the event has fired, but we haven't figured that out yet.
    // The WaitableEvent may have been deleted too.
    cancel_flag_ = nullptr;
    return;
  }

  if (!kernel_.get()) {
    // We have no kernel. This means that we never enqueued a Waiter on an
    // event because the event was already signaled when StartWatching was
    // called.
    //
    // In this case, a task was enqueued on the MessageLoop and will run.
    // We set the flag in case the task hasn't yet run. The flag will stop the
    // delegate getting called. If the task has run then we have the last
    // reference to the flag and it will be deleted immedately after.
    cancel_flag_->Set();
    cancel_flag_ = nullptr;
    return;
  }

  AutoLock locked(kernel_->lock_);
  // We have a lock on the kernel. No one else can signal the event while we
  // have it.

  // We have a possible ABA issue here. If Dequeue was to compare only the
  // pointer values then it's possible that the AsyncWaiter could have been
  // fired, freed and the memory reused for a different Waiter which was
  // enqueued in the same wait-list. We would think that that waiter was our
  // AsyncWaiter and remove it.
  //
  // To stop this, Dequeue also takes a tag argument which is passed to the
  // virtual Compare function before the two are considered a match. So we need
  // a tag which is good for the lifetime of this handle: the Flag. Since we
  // have a reference to the Flag, its memory cannot be reused while this object
  // still exists. So if we find a waiter with the correct pointer value, and
  // which shares a Flag pointer, we have a real match.
  if (kernel_->Dequeue(waiter_, cancel_flag_.get())) {
    // Case 2: the waiter hasn't been signaled yet; it was still on the wait
    // list. We've removed it, thus we can delete it and the task (which cannot
    // have been enqueued with the MessageLoop because the waiter was never
    // signaled)
    delete waiter_;
    cancel_flag_ = nullptr;
    return;
  }

  // Case 3: the waiter isn't on the wait-list, thus it was signaled. It may not
  // have run yet, so we set the flag to tell it not to bother enqueuing the
  // task on the SequencedTaskRunner, but to delete it instead. The Waiter
  // deletes itself once run.
  cancel_flag_->Set();
  cancel_flag_ = nullptr;

  // If the waiter has already run then the task has been enqueued. If the Task
  // hasn't yet run, the flag will stop the delegate from getting called. (This
  // is thread safe because one may only delete a Handle from the sequence that
  // called StartWatching()).
  //
  // If the delegate has already been called then we have nothing to do. The
  // task has been deleted by the MessageLoop.
}

}  // namespace base
