// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SYNCHRONIZATION_WAITABLE_EVENT_H_
#define BASE_SYNCHRONIZATION_WAITABLE_EVENT_H_

#include <stddef.h>

#include "base/base_export.h"
#include "base/macros.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include "base/win/scoped_handle.h"
#elif defined(OS_MACOSX)
#include <mach/mach.h>

#include <list>
#include <memory>

#include "base/callback_forward.h"
#include "base/mac/scoped_mach_port.h"
#include "base/memory/ref_counted.h"
#include "base/synchronization/lock.h"
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <list>
#include <utility>

#include "base/memory/ref_counted.h"
#include "base/synchronization/lock.h"
#endif

namespace base {

class TimeDelta;

// A WaitableEvent can be a useful thread synchronization tool when you want to
// allow one thread to wait for another thread to finish some work. For
// non-Windows systems, this can only be used from within a single address
// space.
//
// Use a WaitableEvent when you would otherwise use a Lock+ConditionVariable to
// protect a simple boolean value.  However, if you find yourself using a
// WaitableEvent in conjunction with a Lock to wait for a more complex state
// change (e.g., for an item to be added to a queue), then you should probably
// be using a ConditionVariable instead of a WaitableEvent.
//
// NOTE: On Windows, this class provides a subset of the functionality afforded
// by a Windows event object.  This is intentional.  If you are writing Windows
// specific code and you need other features of a Windows event, then you might
// be better off just using an Windows event directly.
class BASE_EXPORT WaitableEvent {
 public:
  // Indicates whether a WaitableEvent should automatically reset the event
  // state after a single waiting thread has been released or remain signaled
  // until Reset() is manually invoked.
  enum class ResetPolicy { MANUAL, AUTOMATIC };

  // Indicates whether a new WaitableEvent should start in a signaled state or
  // not.
  enum class InitialState { SIGNALED, NOT_SIGNALED };

  // Constructs a WaitableEvent with policy and initial state as detailed in
  // the above enums.
  WaitableEvent(ResetPolicy reset_policy = ResetPolicy::MANUAL,
                InitialState initial_state = InitialState::NOT_SIGNALED);

#if defined(OS_WIN)
  // Create a WaitableEvent from an Event HANDLE which has already been
  // created. This objects takes ownership of the HANDLE and will close it when
  // deleted.
  explicit WaitableEvent(win::ScopedHandle event_handle);
#endif

  ~WaitableEvent();

  // Put the event in the un-signaled state.
  void Reset();

  // Put the event in the signaled state.  Causing any thread blocked on Wait
  // to be woken up.
  void Signal();

  // Returns true if the event is in the signaled state, else false.  If this
  // is not a manual reset event, then this test will cause a reset.
  bool IsSignaled();

  // Wait indefinitely for the event to be signaled. Wait's return "happens
  // after" |Signal| has completed. This means that it's safe for a
  // WaitableEvent to synchronise its own destruction, like this:
  //
  //   WaitableEvent *e = new WaitableEvent;
  //   SendToOtherThread(e);
  //   e->Wait();
  //   delete e;
  void Wait();

  // Wait up until wait_delta has passed for the event to be signaled
  // (real-time; ignores time overrides).  Returns true if the event was
  // signaled. Handles spurious wakeups and guarantees that |wait_delta| will
  // have elapsed if this returns false.
  //
  // TimedWait can synchronise its own destruction like |Wait|.
  bool TimedWait(const TimeDelta& wait_delta);

#if defined(OS_WIN)
  HANDLE handle() const { return handle_.Get(); }
#endif

  // Declares that this WaitableEvent will only ever be used by a thread that is
  // idle at the bottom of its stack and waiting for work (in particular, it is
  // not synchronously waiting on this event before resuming ongoing work). This
  // is useful to avoid telling base-internals that this thread is "blocked"
  // when it's merely idle and ready to do work. As such, this is only expected
  // to be used by thread and thread pool impls.
  void declare_only_used_while_idle() { waiting_is_blocking_ = false; }

  // Wait, synchronously, on multiple events.
  //   waitables: an array of WaitableEvent pointers
  //   count: the number of elements in @waitables
  //
  // returns: the index of a WaitableEvent which has been signaled.
  //
  // You MUST NOT delete any of the WaitableEvent objects while this wait is
  // happening, however WaitMany's return "happens after" the |Signal| call
  // that caused it has completed, like |Wait|.
  //
  // If more than one WaitableEvent is signaled to unblock WaitMany, the lowest
  // index among them is returned.
  static size_t WaitMany(WaitableEvent** waitables, size_t count);

  // For asynchronous waiting, see WaitableEventWatcher

  // This is a private helper class. It's here because it's used by friends of
  // this class (such as WaitableEventWatcher) to be able to enqueue elements
  // of the wait-list
  class Waiter {
   public:
    // Signal the waiter to wake up.
    //
    // Consider the case of a Waiter which is in multiple WaitableEvent's
    // wait-lists. Each WaitableEvent is automatic-reset and two of them are
    // signaled at the same time. Now, each will wake only the first waiter in
    // the wake-list before resetting. However, if those two waiters happen to
    // be the same object (as can happen if another thread didn't have a chance
    // to dequeue the waiter from the other wait-list in time), two auto-resets
    // will have happened, but only one waiter has been signaled!
    //
    // Because of this, a Waiter may "reject" a wake by returning false. In
    // this case, the auto-reset WaitableEvent shouldn't act as if anything has
    // been notified.
    virtual bool Fire(WaitableEvent* signaling_event) = 0;

    // Waiters may implement this in order to provide an extra condition for
    // two Waiters to be considered equal. In WaitableEvent::Dequeue, if the
    // pointers match then this function is called as a final check. See the
    // comments in ~Handle for why.
    virtual bool Compare(void* tag) = 0;

   protected:
    virtual ~Waiter() = default;
  };

 private:
  friend class WaitableEventWatcher;

#if defined(OS_WIN)
  win::ScopedHandle handle_;
#elif defined(OS_MACOSX)
  // Prior to macOS 10.12, a TYPE_MACH_RECV dispatch source may not be invoked
  // immediately. If a WaitableEventWatcher is used on a manual-reset event,
  // and another thread that is Wait()ing on the event calls Reset()
  // immediately after waking up, the watcher may not receive the callback.
  // On macOS 10.12 and higher, dispatch delivery is reliable. But for OSes
  // prior, a lock-protected list of callbacks is used for manual-reset event
  // watchers. Automatic-reset events are not prone to this issue, since the
  // first thread to wake will claim the event.
  static bool UseSlowWatchList(ResetPolicy policy);

  // Peeks the message queue named by |port| and returns true if a message
  // is present and false if not. If |dequeue| is true, the messsage will be
  // drained from the queue. If |dequeue| is false, the queue will only be
  // peeked. |port| must be a receive right.
  static bool PeekPort(mach_port_t port, bool dequeue);

  // The Mach receive right is waited on by both WaitableEvent and
  // WaitableEventWatcher. It is valid to signal and then delete an event, and
  // a watcher should still be notified. If the right were to be destroyed
  // immediately, the watcher would not receive the signal. Because Mach
  // receive rights cannot have a user refcount greater than one, the right
  // must be reference-counted manually.
  class ReceiveRight : public RefCountedThreadSafe<ReceiveRight> {
   public:
    ReceiveRight(mach_port_t name, bool create_slow_watch_list);

    mach_port_t Name() const { return right_.get(); }

    // This structure is used iff UseSlowWatchList() is true. See the comment
    // in Signal() for details.
    struct WatchList {
      WatchList();
      ~WatchList();

      // The lock protects a list of closures to be run when the event is
      // Signal()ed. The closures are invoked on the signaling thread, so they
      // must be safe to be called from any thread.
      Lock lock;
      std::list<OnceClosure> list;
    };

    WatchList* SlowWatchList() const { return slow_watch_list_.get(); }

   private:
    friend class RefCountedThreadSafe<ReceiveRight>;
    ~ReceiveRight();

    mac::ScopedMachReceiveRight right_;

    // This is allocated iff UseSlowWatchList() is true. It is created on the
    // heap to avoid performing initialization when not using the slow path.
    std::unique_ptr<WatchList> slow_watch_list_;

    DISALLOW_COPY_AND_ASSIGN(ReceiveRight);
  };

  const ResetPolicy policy_;

  // The receive right for the event.
  scoped_refptr<ReceiveRight> receive_right_;

  // The send right used to signal the event. This can be disposed of with
  // the event, unlike the receive right, since a deleted event cannot be
  // signaled.
  mac::ScopedMachSendRight send_right_;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  // On Windows, you must not close a HANDLE which is currently being waited on.
  // The MSDN documentation says that the resulting behaviour is 'undefined'.
  // To solve that issue each WaitableEventWatcher duplicates the given event
  // handle.

  // However, if we were to include the following members
  // directly then, on POSIX, one couldn't use WaitableEventWatcher to watch an
  // event which gets deleted. This mismatch has bitten us several times now,
  // so we have a kernel of the WaitableEvent, which is reference counted.
  // WaitableEventWatchers may then take a reference and thus match the Windows
  // behaviour.
  struct WaitableEventKernel :
      public RefCountedThreadSafe<WaitableEventKernel> {
   public:
    WaitableEventKernel(ResetPolicy reset_policy, InitialState initial_state);

    bool Dequeue(Waiter* waiter, void* tag);

    base::Lock lock_;
    const bool manual_reset_;
    bool signaled_;
    std::list<Waiter*> waiters_;

   private:
    friend class RefCountedThreadSafe<WaitableEventKernel>;
    ~WaitableEventKernel();
  };

  typedef std::pair<WaitableEvent*, size_t> WaiterAndIndex;

  // When dealing with arrays of WaitableEvent*, we want to sort by the address
  // of the WaitableEvent in order to have a globally consistent locking order.
  // In that case we keep them, in sorted order, in an array of pairs where the
  // second element is the index of the WaitableEvent in the original,
  // unsorted, array.
  static size_t EnqueueMany(WaiterAndIndex* waitables,
                            size_t count, Waiter* waiter);

  bool SignalAll();
  bool SignalOne();
  void Enqueue(Waiter* waiter);

  scoped_refptr<WaitableEventKernel> kernel_;
#endif

  // Whether a thread invoking Wait() on this WaitableEvent should be considered
  // blocked as opposed to idle (and potentially replaced if part of a pool).
  bool waiting_is_blocking_ = true;

  DISALLOW_COPY_AND_ASSIGN(WaitableEvent);
};

}  // namespace base

#endif  // BASE_SYNCHRONIZATION_WAITABLE_EVENT_H_
