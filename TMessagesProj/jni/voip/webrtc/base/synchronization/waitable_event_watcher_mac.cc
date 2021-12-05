// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/synchronization/waitable_event_watcher.h"

#include "base/bind.h"
#include "base/callback.h"
#include "base/threading/sequenced_task_runner_handle.h"

namespace base {

WaitableEventWatcher::WaitableEventWatcher() : weak_ptr_factory_(this) {}

WaitableEventWatcher::~WaitableEventWatcher() {
  StopWatching();
}

bool WaitableEventWatcher::StartWatching(
    WaitableEvent* event,
    EventCallback callback,
    scoped_refptr<SequencedTaskRunner> task_runner) {
  DCHECK(task_runner->RunsTasksInCurrentSequence());
  DCHECK(!source_ || dispatch_source_testcancel(source_));

  // Keep a reference to the receive right, so that if the event is deleted
  // out from under the watcher, a signal can still be observed.
  receive_right_ = event->receive_right_;

  callback_ = BindOnce(std::move(callback), event);

  // Locals for capture by the block. Accessing anything through the |this| or
  // |event| pointers is not safe, since either may have been deleted by the
  // time the handler block is invoked.
  WeakPtr<WaitableEventWatcher> weak_this = weak_ptr_factory_.GetWeakPtr();
  const bool auto_reset =
      event->policy_ == WaitableEvent::ResetPolicy::AUTOMATIC;

  // Auto-reset events always use a dispatch source. Manual-reset events
  // only do so if dispatch provides reliable delivery, otherwise a manual
  // watcher list is used.
  if (!WaitableEvent::UseSlowWatchList(event->policy_)) {
    // Use the global concurrent queue here, since it is only used to thunk
    // to the real callback on the target task runner.
    source_.reset(dispatch_source_create(
        DISPATCH_SOURCE_TYPE_MACH_RECV, receive_right_->Name(), 0,
        dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0)));

    // Additional locals for block capture.
    dispatch_source_t source = source_.get();
    mach_port_t name = receive_right_->Name();

    dispatch_source_set_event_handler(source_, ^{
      // For automatic-reset events, only fire the callback if this watcher
      // can claim/dequeue the event. For manual-reset events, all watchers can
      // be called back.
      if (auto_reset && !WaitableEvent::PeekPort(name, true)) {
        return;
      }

      // The event has been consumed. A watcher is one-shot, so cancel the
      // source to prevent receiving future event signals.
      dispatch_source_cancel(source);

      task_runner->PostTask(
          FROM_HERE,
          BindOnce(&WaitableEventWatcher::InvokeCallback, weak_this));
    });
    dispatch_resume(source_);
  } else {
    // The |event->watch_list_| closures can be run from any thread, so bind
    // the callback as an invocation of PostTask.
    OnceClosure watcher =
        BindOnce(IgnoreResult(&TaskRunner::PostTask), task_runner, FROM_HERE,
                 BindOnce(&WaitableEventWatcher::InvokeCallback, weak_this));

    // Hold an additional reference to the ReceiveRight, in case |watcher|
    // runs and deletes the event while the lock is held.
    // Hold the lock for the duration of IsSignaled() so that if Signal()
    // is called by another thread, it waits for this to be added to the
    // watch list.
    scoped_refptr<WaitableEvent::ReceiveRight> receive_right(receive_right_);
    AutoLock lock(receive_right->SlowWatchList()->lock);
    if (event->IsSignaled()) {
      std::move(watcher).Run();
      return true;
    }
    receive_right_->SlowWatchList()->list.push_back(std::move(watcher));
  }

  return true;
}

void WaitableEventWatcher::StopWatching() {
  callback_.Reset();
  receive_right_ = nullptr;
  if (source_) {
    dispatch_source_cancel(source_);
    source_.reset();
  }
}

void WaitableEventWatcher::InvokeCallback() {
  // The callback can be null if StopWatching() is called between signaling
  // and the |callback_| getting run on the target task runner.
  if (callback_.is_null())
    return;
  source_.reset();
  receive_right_ = nullptr;
  std::move(callback_).Run();
}

}  // namespace base
