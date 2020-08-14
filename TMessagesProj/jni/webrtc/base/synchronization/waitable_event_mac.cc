// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/synchronization/waitable_event.h"

#include <dispatch/dispatch.h>
#include <mach/mach.h>
#include <sys/event.h>

#include "base/debug/activity_tracker.h"
#include "base/files/scoped_file.h"
#include "base/mac/dispatch_source_mach.h"
#include "base/mac/mac_util.h"
#include "base/mac/mach_logging.h"
#include "base/mac/scoped_dispatch_object.h"
#include "base/optional.h"
#include "base/posix/eintr_wrapper.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/threading/thread_restrictions.h"
#include "base/time/time.h"
#include "base/time/time_override.h"
#include "build/build_config.h"

namespace base {

WaitableEvent::WaitableEvent(ResetPolicy reset_policy,
                             InitialState initial_state)
    : policy_(reset_policy) {
  mach_port_options_t options{};
  options.flags = MPO_INSERT_SEND_RIGHT;
  options.mpl.mpl_qlimit = 1;

  mach_port_t name;
  kern_return_t kr = mach_port_construct(mach_task_self(), &options, 0, &name);
  MACH_CHECK(kr == KERN_SUCCESS, kr) << "mach_port_construct";

  receive_right_ = new ReceiveRight(name, UseSlowWatchList(policy_));
  send_right_.reset(name);

  if (initial_state == InitialState::SIGNALED)
    Signal();
}

WaitableEvent::~WaitableEvent() = default;

void WaitableEvent::Reset() {
  PeekPort(receive_right_->Name(), true);
}

// NO_THREAD_SAFETY_ANALYSIS: Runtime dependent locking.
void WaitableEvent::Signal() NO_THREAD_SAFETY_ANALYSIS {
  // If using the slow watch-list, copy the watchers to a local. After
  // mach_msg(), the event object may be deleted by an awoken thread.
  const bool use_slow_path = UseSlowWatchList(policy_);
  ReceiveRight* receive_right = nullptr;  // Manually reference counted.
  std::unique_ptr<std::list<OnceClosure>> watch_list;
  if (use_slow_path) {
    // To avoid a race condition of a WaitableEventWatcher getting added
    // while another thread is in this method, hold the watch-list lock for
    // the duration of mach_msg(). This requires ref-counting the
    // |receive_right_| object that contains it, in case the event is deleted
    // by a waiting thread after mach_msg().
    receive_right = receive_right_.get();
    receive_right->AddRef();

    ReceiveRight::WatchList* slow_watch_list = receive_right->SlowWatchList();
    slow_watch_list->lock.Acquire();

    if (!slow_watch_list->list.empty()) {
      watch_list.reset(new std::list<OnceClosure>());
      std::swap(*watch_list, slow_watch_list->list);
    }
  }

  mach_msg_empty_send_t msg{};
  msg.header.msgh_bits = MACH_MSGH_BITS_REMOTE(MACH_MSG_TYPE_COPY_SEND);
  msg.header.msgh_size = sizeof(&msg);
  msg.header.msgh_remote_port = send_right_.get();
  // If the event is already signaled, this will time out because the queue
  // has a length of one.
  kern_return_t kr =
      mach_msg(&msg.header, MACH_SEND_MSG | MACH_SEND_TIMEOUT, sizeof(msg), 0,
               MACH_PORT_NULL, 0, MACH_PORT_NULL);
  MACH_CHECK(kr == KERN_SUCCESS || kr == MACH_SEND_TIMED_OUT, kr) << "mach_msg";

  if (use_slow_path) {
    // If a WaitableEventWatcher were to start watching when the event is
    // signaled, it runs the callback immediately without adding it to the
    // list. Therefore the watch list can only be non-empty if the event is
    // newly signaled.
    if (watch_list.get()) {
      MACH_CHECK(kr == KERN_SUCCESS, kr);
      for (auto& watcher : *watch_list) {
        std::move(watcher).Run();
      }
    }

    receive_right->SlowWatchList()->lock.Release();
    receive_right->Release();
  }
}

bool WaitableEvent::IsSignaled() {
  return PeekPort(receive_right_->Name(), policy_ == ResetPolicy::AUTOMATIC);
}

void WaitableEvent::Wait() {
  bool result = TimedWait(TimeDelta::Max());
  DCHECK(result) << "TimedWait() should never fail with infinite timeout";
}

bool WaitableEvent::TimedWait(const TimeDelta& wait_delta) {
  if (wait_delta <= TimeDelta())
    return IsSignaled();

  // Record the event that this thread is blocking upon (for hang diagnosis) and
  // consider blocked for scheduling purposes. Ignore this for non-blocking
  // WaitableEvents.
  Optional<debug::ScopedEventWaitActivity> event_activity;
  Optional<internal::ScopedBlockingCallWithBaseSyncPrimitives>
      scoped_blocking_call;
  if (waiting_is_blocking_) {
    event_activity.emplace(this);
    scoped_blocking_call.emplace(FROM_HERE, BlockingType::MAY_BLOCK);
  }

  mach_msg_empty_rcv_t msg{};
  msg.header.msgh_local_port = receive_right_->Name();

  mach_msg_option_t options = MACH_RCV_MSG;

  if (!wait_delta.is_max())
    options |= MACH_RCV_TIMEOUT | MACH_RCV_INTERRUPT;

  mach_msg_size_t rcv_size = sizeof(msg);
  if (policy_ == ResetPolicy::MANUAL) {
    // To avoid dequeing the message, receive with a size of 0 and set
    // MACH_RCV_LARGE to keep the message in the queue.
    options |= MACH_RCV_LARGE;
    rcv_size = 0;
  }

  // TimeTicks takes care of overflow but we special case is_max() nonetheless
  // to avoid invoking TimeTicksNowIgnoringOverride() unnecessarily (same for
  // the increment step of the for loop if the condition variable returns
  // early). Ref: https://crbug.com/910524#c7
  const TimeTicks end_time =
      wait_delta.is_max() ? TimeTicks::Max()
                          : subtle::TimeTicksNowIgnoringOverride() + wait_delta;
  // Fake |kr| value to boostrap the for loop.
  kern_return_t kr = MACH_RCV_INTERRUPTED;
  for (mach_msg_timeout_t timeout = wait_delta.is_max()
                                        ? MACH_MSG_TIMEOUT_NONE
                                        : wait_delta.InMillisecondsRoundedUp();
       // If the thread is interrupted during mach_msg(), the system call will
       // be restarted. However, the libsyscall wrapper does not adjust the
       // timeout by the amount of time already waited. Using MACH_RCV_INTERRUPT
       // will instead return from mach_msg(), so that the call can be retried
       // with an adjusted timeout.
       kr == MACH_RCV_INTERRUPTED;
       timeout =
           end_time.is_max()
               ? MACH_MSG_TIMEOUT_NONE
               : std::max<int64_t>(
                     0, (end_time - subtle::TimeTicksNowIgnoringOverride())
                            .InMillisecondsRoundedUp())) {
    kr = mach_msg(&msg.header, options, 0, rcv_size, receive_right_->Name(),
                  timeout, MACH_PORT_NULL);
  }

  if (kr == KERN_SUCCESS) {
    return true;
  } else if (rcv_size == 0 && kr == MACH_RCV_TOO_LARGE) {
    return true;
  } else {
    MACH_CHECK(kr == MACH_RCV_TIMED_OUT, kr) << "mach_msg";
    return false;
  }
}

// static
bool WaitableEvent::UseSlowWatchList(ResetPolicy policy) {
#if defined(OS_IOS)
  const bool use_slow_path = false;
#else
  static bool use_slow_path = !mac::IsAtLeastOS10_12();
#endif
  return policy == ResetPolicy::MANUAL && use_slow_path;
}

// static
size_t WaitableEvent::WaitMany(WaitableEvent** raw_waitables, size_t count) {
  DCHECK(count) << "Cannot wait on no events";
  internal::ScopedBlockingCallWithBaseSyncPrimitives scoped_blocking_call(
      FROM_HERE, BlockingType::MAY_BLOCK);
  // Record an event (the first) that this thread is blocking upon.
  debug::ScopedEventWaitActivity event_activity(raw_waitables[0]);

  // On macOS 10.11+, using Mach port sets may cause system instability, per
  // https://crbug.com/756102. On macOS 10.12+, a kqueue can be used
  // instead to work around that. On macOS 10.9 and 10.10, kqueue only works
  // for port sets, so port sets are just used directly. On macOS 10.11,
  // libdispatch sources are used. Therefore, there are three different
  // primitives that can be used to implement WaitMany. Which one to use is
  // selected at run-time by OS version checks.
  enum WaitManyPrimitive {
    KQUEUE,
    DISPATCH,
    PORT_SET,
  };
#if defined(OS_IOS)
  const WaitManyPrimitive kPrimitive = PORT_SET;
#else
  const WaitManyPrimitive kPrimitive =
      mac::IsAtLeastOS10_12() ? KQUEUE
                              : (mac::IsOS10_11() ? DISPATCH : PORT_SET);
#endif
  if (kPrimitive == KQUEUE) {
    std::vector<kevent64_s> events(count);
    for (size_t i = 0; i < count; ++i) {
      EV_SET64(&events[i], raw_waitables[i]->receive_right_->Name(),
               EVFILT_MACHPORT, EV_ADD, 0, 0, i, 0, 0);
    }

    std::vector<kevent64_s> out_events(count);

    ScopedFD wait_many(kqueue());
    PCHECK(wait_many.is_valid()) << "kqueue";

    int rv = HANDLE_EINTR(kevent64(wait_many.get(), events.data(), count,
                                   out_events.data(), count, 0, nullptr));
    PCHECK(rv > 0) << "kevent64";

    size_t triggered = -1;
    for (size_t i = 0; i < static_cast<size_t>(rv); ++i) {
      // WaitMany should return the lowest index in |raw_waitables| that was
      // triggered.
      size_t index = static_cast<size_t>(out_events[i].udata);
      triggered = std::min(triggered, index);
    }

    if (raw_waitables[triggered]->policy_ == ResetPolicy::AUTOMATIC) {
      // The message needs to be dequeued to reset the event.
      PeekPort(raw_waitables[triggered]->receive_right_->Name(), true);
    }

    return triggered;
  } else if (kPrimitive == DISPATCH) {
    // Each item in |raw_waitables| will be watched using a dispatch souce
    // scheduled on the serial |queue|. The first one to be invoked will
    // signal the |semaphore| that this method will wait on.
    ScopedDispatchObject<dispatch_queue_t> queue(dispatch_queue_create(
        "org.chromium.base.WaitableEvent.WaitMany", DISPATCH_QUEUE_SERIAL));
    ScopedDispatchObject<dispatch_semaphore_t> semaphore(
        dispatch_semaphore_create(0));

    // Block capture references. |signaled| will identify the index in
    // |raw_waitables| whose source was invoked.
    dispatch_semaphore_t semaphore_ref = semaphore.get();
    const size_t kUnsignaled = -1;
    __block size_t signaled = kUnsignaled;

    // Create a MACH_RECV dispatch source for each event. These must be
    // destroyed before the |queue| and |semaphore|.
    std::vector<std::unique_ptr<DispatchSourceMach>> sources;
    for (size_t i = 0; i < count; ++i) {
      const bool auto_reset =
          raw_waitables[i]->policy_ == WaitableEvent::ResetPolicy::AUTOMATIC;
      // The block will copy a reference to |right|.
      scoped_refptr<WaitableEvent::ReceiveRight> right =
          raw_waitables[i]->receive_right_;
      auto source =
          std::make_unique<DispatchSourceMach>(queue, right->Name(), ^{
            // After the semaphore is signaled, another event be signaled and
            // the source may have its block put on the |queue|. WaitMany
            // should only report (and auto-reset) one event, so the first
            // event to signal is reported.
            if (signaled == kUnsignaled) {
              signaled = i;
              if (auto_reset) {
                PeekPort(right->Name(), true);
              }
              dispatch_semaphore_signal(semaphore_ref);
            }
          });
      source->Resume();
      sources.push_back(std::move(source));
    }

    dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER);
    DCHECK_NE(signaled, kUnsignaled);
    return signaled;
  } else {
    DCHECK_EQ(kPrimitive, PORT_SET);

    kern_return_t kr;

    mac::ScopedMachPortSet port_set;
    {
      mach_port_t name;
      kr =
          mach_port_allocate(mach_task_self(), MACH_PORT_RIGHT_PORT_SET, &name);
      MACH_CHECK(kr == KERN_SUCCESS, kr) << "mach_port_allocate";
      port_set.reset(name);
    }

    for (size_t i = 0; i < count; ++i) {
      kr = mach_port_insert_member(mach_task_self(),
                                   raw_waitables[i]->receive_right_->Name(),
                                   port_set.get());
      MACH_CHECK(kr == KERN_SUCCESS, kr) << "index " << i;
    }

    mach_msg_empty_rcv_t msg{};
    // Wait on the port set. Only specify space enough for the header, to
    // identify which port in the set is signaled. Otherwise, receiving from the
    // port set may dequeue a message for a manual-reset event object, which
    // would cause it to be reset.
    kr = mach_msg(&msg.header,
                  MACH_RCV_MSG | MACH_RCV_LARGE | MACH_RCV_LARGE_IDENTITY, 0,
                  sizeof(msg.header), port_set.get(), 0, MACH_PORT_NULL);
    MACH_CHECK(kr == MACH_RCV_TOO_LARGE, kr) << "mach_msg";

    for (size_t i = 0; i < count; ++i) {
      WaitableEvent* event = raw_waitables[i];
      if (msg.header.msgh_local_port == event->receive_right_->Name()) {
        if (event->policy_ == ResetPolicy::AUTOMATIC) {
          // The message needs to be dequeued to reset the event.
          PeekPort(msg.header.msgh_local_port, true);
        }
        return i;
      }
    }

    NOTREACHED();
    return 0;
  }
}

// static
bool WaitableEvent::PeekPort(mach_port_t port, bool dequeue) {
  if (dequeue) {
    mach_msg_empty_rcv_t msg{};
    msg.header.msgh_local_port = port;
    kern_return_t kr = mach_msg(&msg.header, MACH_RCV_MSG | MACH_RCV_TIMEOUT, 0,
                                sizeof(msg), port, 0, MACH_PORT_NULL);
    if (kr == KERN_SUCCESS) {
      return true;
    } else {
      MACH_CHECK(kr == MACH_RCV_TIMED_OUT, kr) << "mach_msg";
      return false;
    }
  } else {
    mach_port_seqno_t seqno = 0;
    mach_msg_size_t size;
    mach_msg_id_t id;
    mach_msg_trailer_t trailer;
    mach_msg_type_number_t trailer_size = sizeof(trailer);
    kern_return_t kr = mach_port_peek(
        mach_task_self(), port, MACH_RCV_TRAILER_TYPE(MACH_RCV_TRAILER_NULL),
        &seqno, &size, &id, reinterpret_cast<mach_msg_trailer_info_t>(&trailer),
        &trailer_size);
    if (kr == KERN_SUCCESS) {
      return true;
    } else {
      MACH_CHECK(kr == KERN_FAILURE, kr) << "mach_port_peek";
      return false;
    }
  }
}

WaitableEvent::ReceiveRight::ReceiveRight(mach_port_t name,
                                          bool create_slow_watch_list)
    : right_(name),
      slow_watch_list_(create_slow_watch_list ? new WatchList() : nullptr) {}

WaitableEvent::ReceiveRight::~ReceiveRight() = default;

WaitableEvent::ReceiveRight::WatchList::WatchList() = default;

WaitableEvent::ReceiveRight::WatchList::~WatchList() = default;

}  // namespace base
