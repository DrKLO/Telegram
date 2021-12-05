// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_pump_kqueue.h"

#include <sys/errno.h>

#include "base/auto_reset.h"
#include "base/logging.h"
#include "base/mac/mac_util.h"
#include "base/mac/mach_logging.h"
#include "base/mac/scoped_nsautorelease_pool.h"
#include "base/posix/eintr_wrapper.h"

namespace base {

namespace {

// Prior to macOS 10.12, a kqueue could not watch individual Mach ports, only
// port sets. MessagePumpKqueue will directly use Mach ports in the kqueue if
// it is possible.
bool KqueueNeedsPortSet() {
  static bool kqueue_needs_port_set = mac::IsAtMostOS10_11();
  return kqueue_needs_port_set;
}

int ChangeOneEvent(const ScopedFD& kqueue, kevent64_s* event) {
  return HANDLE_EINTR(kevent64(kqueue.get(), event, 1, nullptr, 0, 0, nullptr));
}

}  // namespace

MessagePumpKqueue::FdWatchController::FdWatchController(
    const Location& from_here)
    : FdWatchControllerInterface(from_here) {}

MessagePumpKqueue::FdWatchController::~FdWatchController() {
  StopWatchingFileDescriptor();
}

bool MessagePumpKqueue::FdWatchController::StopWatchingFileDescriptor() {
  if (!pump_)
    return true;
  return pump_->StopWatchingFileDescriptor(this);
}

void MessagePumpKqueue::FdWatchController::Init(WeakPtr<MessagePumpKqueue> pump,
                                                int fd,
                                                int mode,
                                                FdWatcher* watcher) {
  DCHECK_NE(fd, -1);
  DCHECK(!watcher_);
  DCHECK(watcher);
  DCHECK(pump);
  fd_ = fd;
  mode_ = mode;
  watcher_ = watcher;
  pump_ = pump;
}

void MessagePumpKqueue::FdWatchController::Reset() {
  fd_ = -1;
  mode_ = 0;
  watcher_ = nullptr;
  pump_ = nullptr;
}

MessagePumpKqueue::MachPortWatchController::MachPortWatchController(
    const Location& from_here)
    : from_here_(from_here) {}

MessagePumpKqueue::MachPortWatchController::~MachPortWatchController() {
  StopWatchingMachPort();
}

bool MessagePumpKqueue::MachPortWatchController::StopWatchingMachPort() {
  if (!pump_)
    return true;
  return pump_->StopWatchingMachPort(this);
}

void MessagePumpKqueue::MachPortWatchController::Init(
    WeakPtr<MessagePumpKqueue> pump,
    mach_port_t port,
    MachPortWatcher* watcher) {
  DCHECK(!watcher_);
  DCHECK(watcher);
  DCHECK(pump);
  port_ = port;
  watcher_ = watcher;
  pump_ = pump;
}

void MessagePumpKqueue::MachPortWatchController::Reset() {
  port_ = MACH_PORT_NULL;
  watcher_ = nullptr;
  pump_ = nullptr;
}

MessagePumpKqueue::MessagePumpKqueue()
    : kqueue_(kqueue()), weak_factory_(this) {
  PCHECK(kqueue_.is_valid()) << "kqueue";

  // Create a Mach port that will be used to wake up the pump by sending
  // a message in response to ScheduleWork(). This is significantly faster than
  // using an EVFILT_USER event, especially when triggered across threads.
  kern_return_t kr = mach_port_allocate(
      mach_task_self(), MACH_PORT_RIGHT_RECEIVE,
      base::mac::ScopedMachReceiveRight::Receiver(wakeup_).get());
  MACH_CHECK(kr == KERN_SUCCESS, kr) << "mach_port_allocate";

  kevent64_s event{};
  if (KqueueNeedsPortSet()) {
    kr = mach_port_allocate(mach_task_self(), MACH_PORT_RIGHT_PORT_SET,
                            mac::ScopedMachPortSet::Receiver(port_set_).get());
    MACH_CHECK(kr == KERN_SUCCESS, kr) << "mach_port_allocate PORT_SET";

    kr = mach_port_insert_member(mach_task_self(), wakeup_.get(),
                                 port_set_.get());
    MACH_CHECK(kr == KERN_SUCCESS, kr) << "mach_port_insert_member";

    event.ident = port_set_.get();
    event.filter = EVFILT_MACHPORT;
    event.flags = EV_ADD;
  } else {
    // When not using a port set, the wakeup port event can be specified to
    // directly receive the Mach message as part of the kevent64() syscall.
    // This is not done when using a port set, since that would potentially
    // receive client MachPortWatchers' messages.
    event.ident = wakeup_.get();
    event.filter = EVFILT_MACHPORT;
    event.flags = EV_ADD;
    event.fflags = MACH_RCV_MSG;
    event.ext[0] = reinterpret_cast<uint64_t>(&wakeup_buffer_);
    event.ext[1] = sizeof(wakeup_buffer_);
  }

  int rv = ChangeOneEvent(kqueue_, &event);
  PCHECK(rv == 0) << "kevent64";
}

MessagePumpKqueue::~MessagePumpKqueue() {}

void MessagePumpKqueue::Run(Delegate* delegate) {
  AutoReset<bool> reset_keep_running(&keep_running_, true);

  while (keep_running_) {
    mac::ScopedNSAutoreleasePool pool;

    bool do_more_work = DoInternalWork(nullptr);
    if (!keep_running_)
      break;

    Delegate::NextWorkInfo next_work_info = delegate->DoSomeWork();
    do_more_work |= next_work_info.is_immediate();
    if (!keep_running_)
      break;

    if (do_more_work)
      continue;

    do_more_work |= delegate->DoIdleWork();
    if (!keep_running_)
      break;

    if (do_more_work)
      continue;

    DoInternalWork(&next_work_info);
  }
}

void MessagePumpKqueue::Quit() {
  keep_running_ = false;
  ScheduleWork();
}

void MessagePumpKqueue::ScheduleWork() {
  mach_msg_empty_send_t message{};
  message.header.msgh_size = sizeof(message);
  message.header.msgh_bits =
      MACH_MSGH_BITS_REMOTE(MACH_MSG_TYPE_MAKE_SEND_ONCE);
  message.header.msgh_remote_port = wakeup_.get();
  kern_return_t kr = mach_msg_send(&message.header);
  if (kr != KERN_SUCCESS) {
    // If ScheduleWork() is being called by other threads faster than the pump
    // can dispatch work, the kernel message queue for the wakeup port can fill
    // up (this happens under base_perftests, for example). The kernel does
    // return a SEND_ONCE right in the case of failure, which must be destroyed
    // to avoid leaking.
    MACH_DLOG_IF(ERROR, (kr & ~MACH_MSG_IPC_SPACE) != MACH_SEND_NO_BUFFER, kr)
        << "mach_msg_send";
    mach_msg_destroy(&message.header);
  }
}

void MessagePumpKqueue::ScheduleDelayedWork(
    const TimeTicks& delayed_work_time) {
  // Nothing to do. This MessagePump uses DoSomeWork().
}

bool MessagePumpKqueue::WatchMachReceivePort(
    mach_port_t port,
    MachPortWatchController* controller,
    MachPortWatcher* delegate) {
  DCHECK(port != MACH_PORT_NULL);
  DCHECK(controller);
  DCHECK(delegate);

  if (controller->port() != MACH_PORT_NULL) {
    DLOG(ERROR)
        << "Cannot use the same MachPortWatchController while it is active";
    return false;
  }

  if (KqueueNeedsPortSet()) {
    kern_return_t kr =
        mach_port_insert_member(mach_task_self(), port, port_set_.get());
    if (kr != KERN_SUCCESS) {
      MACH_LOG(ERROR, kr) << "mach_port_insert_member";
      return false;
    }
  } else {
    kevent64_s event{};
    event.ident = port;
    event.filter = EVFILT_MACHPORT;
    event.flags = EV_ADD;
    int rv = ChangeOneEvent(kqueue_, &event);
    if (rv < 0) {
      DPLOG(ERROR) << "kevent64";
      return false;
    }
    ++event_count_;
  }

  controller->Init(weak_factory_.GetWeakPtr(), port, delegate);
  port_controllers_.AddWithID(controller, port);

  return true;
}

bool MessagePumpKqueue::WatchFileDescriptor(int fd,
                                            bool persistent,
                                            int mode,
                                            FdWatchController* controller,
                                            FdWatcher* delegate) {
  DCHECK_GE(fd, 0);
  DCHECK(controller);
  DCHECK(delegate);
  DCHECK_NE(mode & Mode::WATCH_READ_WRITE, 0);

  if (controller->fd() != -1 && controller->fd() != fd) {
    DLOG(ERROR) << "Cannot use the same FdWatchController on two different FDs";
    return false;
  }
  StopWatchingFileDescriptor(controller);

  std::vector<kevent64_s> events;

  kevent64_s base_event{};
  base_event.ident = fd;
  base_event.flags = EV_ADD | (!persistent ? EV_ONESHOT : 0);

  if (mode & Mode::WATCH_READ) {
    base_event.filter = EVFILT_READ;
    base_event.udata = fd_controllers_.Add(controller);
    events.push_back(base_event);
  }
  if (mode & Mode::WATCH_WRITE) {
    base_event.filter = EVFILT_WRITE;
    base_event.udata = fd_controllers_.Add(controller);
    events.push_back(base_event);
  }

  int rv = HANDLE_EINTR(kevent64(kqueue_.get(), events.data(), events.size(),
                                 nullptr, 0, 0, nullptr));
  if (rv < 0) {
    DPLOG(ERROR) << "WatchFileDescriptor kevent64";
    return false;
  }

  event_count_ += events.size();
  controller->Init(weak_factory_.GetWeakPtr(), fd, mode, delegate);

  return true;
}

bool MessagePumpKqueue::StopWatchingMachPort(
    MachPortWatchController* controller) {
  mach_port_t port = controller->port();
  controller->Reset();
  port_controllers_.Remove(port);

  if (KqueueNeedsPortSet()) {
    kern_return_t kr =
        mach_port_extract_member(mach_task_self(), port, port_set_.get());
    if (kr != KERN_SUCCESS) {
      MACH_LOG(ERROR, kr) << "mach_port_extract_member";
      return false;
    }
  } else {
    kevent64_s event{};
    event.ident = port;
    event.filter = EVFILT_MACHPORT;
    event.flags = EV_DELETE;
    --event_count_;
    int rv = ChangeOneEvent(kqueue_, &event);
    if (rv < 0) {
      DPLOG(ERROR) << "kevent64";
      return false;
    }
  }

  return true;
}

bool MessagePumpKqueue::StopWatchingFileDescriptor(
    FdWatchController* controller) {
  int fd = controller->fd();
  int mode = controller->mode();
  controller->Reset();

  if (fd == -1)
    return true;

  std::vector<kevent64_s> events;

  kevent64_s base_event{};
  base_event.ident = fd;
  base_event.flags = EV_DELETE;

  if (mode & Mode::WATCH_READ) {
    base_event.filter = EVFILT_READ;
    events.push_back(base_event);
  }
  if (mode & Mode::WATCH_WRITE) {
    base_event.filter = EVFILT_WRITE;
    events.push_back(base_event);
  }

  int rv = HANDLE_EINTR(kevent64(kqueue_.get(), events.data(), events.size(),
                                 nullptr, 0, 0, nullptr));
  DPLOG_IF(ERROR, rv < 0) << "StopWatchingFileDescriptor kevent64";

  // The keys for the IDMap aren't recorded anywhere (they're attached to the
  // kevent object in the kernel), so locate the entries by controller pointer.
  for (auto it = IDMap<FdWatchController*>::iterator(&fd_controllers_);
       !it.IsAtEnd(); it.Advance()) {
    if (it.GetCurrentValue() == controller) {
      fd_controllers_.Remove(it.GetCurrentKey());
    }
  }

  event_count_ -= events.size();

  return rv >= 0;
}

bool MessagePumpKqueue::DoInternalWork(Delegate::NextWorkInfo* next_work_info) {
  if (events_.size() < event_count_) {
    events_.resize(event_count_);
  }

  bool poll = next_work_info == nullptr;
  int flags = poll ? KEVENT_FLAG_IMMEDIATE : 0;
  bool indefinite =
      next_work_info != nullptr && next_work_info->delayed_run_time.is_max();

  int rv = 0;
  do {
    timespec timeout{};
    if (!indefinite && !poll) {
      if (rv != 0) {
        // The wait was interrupted and made |next_work_info|'s view of
        // TimeTicks::Now() stale. Refresh it before doing another wait.
        next_work_info->recent_now = TimeTicks::Now();
      }
      timeout = next_work_info->remaining_delay().ToTimeSpec();
    }
    // This does not use HANDLE_EINTR, since retrying the syscall requires
    // adjusting the timeout to account for time already waited.
    rv = kevent64(kqueue_.get(), nullptr, 0, events_.data(), events_.size(),
                  flags, indefinite ? nullptr : &timeout);
  } while (rv < 0 && errno == EINTR);

  PCHECK(rv >= 0) << "kevent64";
  return ProcessEvents(rv);
}

bool MessagePumpKqueue::ProcessEvents(int count) {
  bool did_work = false;

  for (int i = 0; i < count; ++i) {
    auto* event = &events_[i];
    if (event->filter == EVFILT_READ || event->filter == EVFILT_WRITE) {
      did_work = true;

      FdWatchController* controller = fd_controllers_.Lookup(event->udata);
      if (!controller) {
        // The controller was removed by some other work callout before
        // this event could be processed.
        continue;
      }
      FdWatcher* delegate = controller->watcher();

      if (event->flags & EV_ONESHOT) {
        // If this was a one-shot event, the Controller needs to stop tracking
        // the descriptor, so it is not double-removed when it is told to stop
        // watching.
        controller->Reset();
        fd_controllers_.Remove(event->udata);
        --event_count_;
      }

      if (event->filter == EVFILT_READ) {
        delegate->OnFileCanReadWithoutBlocking(event->ident);
      } else if (event->filter == EVFILT_WRITE) {
        delegate->OnFileCanWriteWithoutBlocking(event->ident);
      }
    } else if (event->filter == EVFILT_MACHPORT) {
      mach_port_t port = KqueueNeedsPortSet() ? event->data : event->ident;

      if (port == wakeup_.get()) {
        // The wakeup event has been received, do not treat this as "doing
        // work", this just wakes up the pump.
        if (KqueueNeedsPortSet()) {
          // When using the kqueue directly, the message can be received
          // straight into a buffer that was created when adding the event.
          // But when using a port set, the message must be drained manually.
          wakeup_buffer_.header.msgh_local_port = port;
          wakeup_buffer_.header.msgh_size = sizeof(wakeup_buffer_);
          kern_return_t kr = mach_msg_receive(&wakeup_buffer_.header);
          MACH_LOG_IF(ERROR, kr != KERN_SUCCESS, kr)
              << "mach_msg_receive wakeup";
        }
        continue;
      }

      did_work = true;

      MachPortWatchController* controller = port_controllers_.Lookup(port);
      // The controller could have been removed by some other work callout
      // before this event could be processed.
      if (controller) {
        controller->watcher()->OnMachMessageReceived(port);
      }
    } else {
      NOTREACHED() << "Unexpected event for filter " << event->filter;
    }
  }

  return did_work;
}

}  // namespace base
