// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/message_pump_android.h"

#include <android/looper.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/eventfd.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>
#include <utility>

#include "base/android/jni_android.h"
#include "base/android/scoped_java_ref.h"
#include "base/callback_helpers.h"
#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/run_loop.h"
#include "build/build_config.h"

// Android stripped sys/timerfd.h out of their platform headers, so we have to
// use syscall to make use of timerfd. Once the min API level is 20, we can
// directly use timerfd.h.
#ifndef __NR_timerfd_create
#error "Unable to find syscall for __NR_timerfd_create"
#endif

#ifndef TFD_TIMER_ABSTIME
#define TFD_TIMER_ABSTIME (1 << 0)
#endif

using base::android::JavaParamRef;
using base::android::ScopedJavaLocalRef;

namespace base {

namespace {

// See sys/timerfd.h
int timerfd_create(int clockid, int flags) {
  return syscall(__NR_timerfd_create, clockid, flags);
}

// See sys/timerfd.h
int timerfd_settime(int ufc,
                    int flags,
                    const struct itimerspec* utmr,
                    struct itimerspec* otmr) {
  return syscall(__NR_timerfd_settime, ufc, flags, utmr, otmr);
}

// https://crbug.com/873588. The stack may not be aligned when the ALooper calls
// into our code due to the inconsistent ABI on older Android OS versions.
#if defined(ARCH_CPU_X86)
#define STACK_ALIGN __attribute__((force_align_arg_pointer))
#else
#define STACK_ALIGN
#endif

STACK_ALIGN int NonDelayedLooperCallback(int fd, int events, void* data) {
  if (events & ALOOPER_EVENT_HANGUP)
    return 0;

  DCHECK(events & ALOOPER_EVENT_INPUT);
  MessagePumpForUI* pump = reinterpret_cast<MessagePumpForUI*>(data);
  pump->OnNonDelayedLooperCallback();
  return 1;  // continue listening for events
}

STACK_ALIGN int DelayedLooperCallback(int fd, int events, void* data) {
  if (events & ALOOPER_EVENT_HANGUP)
    return 0;

  DCHECK(events & ALOOPER_EVENT_INPUT);
  MessagePumpForUI* pump = reinterpret_cast<MessagePumpForUI*>(data);
  pump->OnDelayedLooperCallback();
  return 1;  // continue listening for events
}

}  // namespace

MessagePumpForUI::MessagePumpForUI()
    : env_(base::android::AttachCurrentThread()) {
  // The Android native ALooper uses epoll to poll our file descriptors and wake
  // us up. We use a simple level-triggered eventfd to signal that non-delayed
  // work is available, and a timerfd to signal when delayed work is ready to
  // be run.
  non_delayed_fd_ = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
  CHECK_NE(non_delayed_fd_, -1);
  DCHECK_EQ(TimeTicks::GetClock(), TimeTicks::Clock::LINUX_CLOCK_MONOTONIC);

  // We can't create the timerfd with TFD_NONBLOCK | TFD_CLOEXEC as we can't
  // include timerfd.h. See comments above on __NR_timerfd_create. It looks like
  // they're just aliases to O_NONBLOCK and O_CLOEXEC anyways, so this should be
  // fine.
  delayed_fd_ = timerfd_create(CLOCK_MONOTONIC, O_NONBLOCK | O_CLOEXEC);
  CHECK_NE(delayed_fd_, -1);

  looper_ = ALooper_prepare(0);
  DCHECK(looper_);
  // Add a reference to the looper so it isn't deleted on us.
  ALooper_acquire(looper_);
  ALooper_addFd(looper_, non_delayed_fd_, 0, ALOOPER_EVENT_INPUT,
                &NonDelayedLooperCallback, reinterpret_cast<void*>(this));
  ALooper_addFd(looper_, delayed_fd_, 0, ALOOPER_EVENT_INPUT,
                &DelayedLooperCallback, reinterpret_cast<void*>(this));
}

MessagePumpForUI::~MessagePumpForUI() {
  DCHECK_EQ(ALooper_forThread(), looper_);
  ALooper_removeFd(looper_, non_delayed_fd_);
  ALooper_removeFd(looper_, delayed_fd_);
  ALooper_release(looper_);
  looper_ = nullptr;

  close(non_delayed_fd_);
  close(delayed_fd_);
}

void MessagePumpForUI::OnDelayedLooperCallback() {
  // There may be non-Chromium callbacks on the same ALooper which may have left
  // a pending exception set, and ALooper does not check for this between
  // callbacks. Check here, and if there's already an exception, just skip this
  // iteration without clearing the fd. If the exception ends up being non-fatal
  // then we'll just get called again on the next polling iteration.
  if (base::android::HasException(env_))
    return;

  // ALooper_pollOnce may call this after Quit() if OnNonDelayedLooperCallback()
  // resulted in Quit() in the same round.
  if (ShouldQuit())
    return;

  // Clear the fd.
  uint64_t value;
  int ret = read(delayed_fd_, &value, sizeof(value));

  // TODO(mthiesse): Figure out how it's possible to hit EAGAIN here.
  // According to http://man7.org/linux/man-pages/man2/timerfd_create.2.html
  // EAGAIN only happens if no timer has expired. Also according to the man page
  // poll only returns readable when a timer has expired. So this function will
  // only be called when a timer has expired, but reading reveals no timer has
  // expired...
  // Quit() and ScheduleDelayedWork() are the only other functions that touch
  // the timerfd, and they both run on the same thread as this callback, so
  // there are no obvious timing or multi-threading related issues.
  DPCHECK(ret >= 0 || errno == EAGAIN);

  delayed_scheduled_time_.reset();

  Delegate::NextWorkInfo next_work_info = delegate_->DoSomeWork();

  if (ShouldQuit())
    return;

  if (next_work_info.is_immediate()) {
    ScheduleWork();
    return;
  }

  DoIdleWork();
  if (!next_work_info.delayed_run_time.is_max())
    ScheduleDelayedWork(next_work_info.delayed_run_time);
}

void MessagePumpForUI::OnNonDelayedLooperCallback() {
  // There may be non-Chromium callbacks on the same ALooper which may have left
  // a pending exception set, and ALooper does not check for this between
  // callbacks. Check here, and if there's already an exception, just skip this
  // iteration without clearing the fd. If the exception ends up being non-fatal
  // then we'll just get called again on the next polling iteration.
  if (base::android::HasException(env_))
    return;

  // ALooper_pollOnce may call this after Quit() if OnDelayedLooperCallback()
  // resulted in Quit() in the same round.
  if (ShouldQuit())
    return;

  // A bit added to the |non_delayed_fd_| to keep it signaled when we yield to
  // native tasks below.
  constexpr uint64_t kTryNativeTasksBeforeIdleBit = uint64_t(1) << 32;

  // We're about to process all the work requested by ScheduleWork().
  // MessagePump users are expected to do their best not to invoke
  // ScheduleWork() again before DoSomeWork() returns a non-immediate
  // NextWorkInfo below. Hence, capturing the file descriptor's value now and
  // resetting its contents to 0 should be okay. The value currently stored
  // should be greater than 0 since work having been scheduled is the reason
  // we're here. See http://man7.org/linux/man-pages/man2/eventfd.2.html
  uint64_t pre_work_value = 0;
  int ret = read(non_delayed_fd_, &pre_work_value, sizeof(pre_work_value));
  DPCHECK(ret >= 0);
  DCHECK_GT(pre_work_value, 0U);

  // Note: We can't skip DoSomeWork() even if
  // |pre_work_value == kTryNativeTasksBeforeIdleBit| here (i.e. no additional
  // ScheduleWork() since yielding to native) as delayed tasks might have come
  // in and we need to re-sample |next_work_info|.

  // Runs all application tasks scheduled to run.
  Delegate::NextWorkInfo next_work_info;
  do {
    if (ShouldQuit())
      return;

    next_work_info = delegate_->DoSomeWork();
  } while (next_work_info.is_immediate());

  // Do not resignal |non_delayed_fd_| if we're quitting (this pump doesn't
  // allow nesting so needing to resume in an outer loop is not an issue
  // either).
  if (ShouldQuit())
    return;

  // Before declaring this loop idle, yield to native tasks and arrange to be
  // called again (unless we're already in that second call).
  if (pre_work_value != kTryNativeTasksBeforeIdleBit) {
    // Note: This write() is racing with potential ScheduleWork() calls. This is
    // fine as write() is adding this bit, not overwriting the existing value,
    // and as such racing ScheduleWork() calls would merely add 1 to the lower
    // bits and we would find |pre_work_value != kTryNativeTasksBeforeIdleBit|
    // in the next cycle again, retrying this.
    ret = write(non_delayed_fd_, &kTryNativeTasksBeforeIdleBit,
                sizeof(kTryNativeTasksBeforeIdleBit));
    DPCHECK(ret >= 0);
    return;
  }

  // We yielded to native tasks already and they didn't generate a
  // ScheduleWork() request so we can declare idleness. It's possible for a
  // ScheduleWork() request to come in racily while this method unwinds, this is
  // fine and will merely result in it being re-invoked shortly after it
  // returns.
  // TODO(scheduler-dev): this doesn't account for tasks that don't ever call
  // SchedulerWork() but still keep the system non-idle (e.g., the Java Handler
  // API). It would be better to add an API to query the presence of native
  // tasks instead of relying on yielding once + kTryNativeTasksBeforeIdleBit.
  DCHECK_EQ(pre_work_value, kTryNativeTasksBeforeIdleBit);

  if (ShouldQuit())
    return;

  // At this point, the java looper might not be idle - it's impossible to know
  // pre-Android-M, so we may end up doing Idle work while java tasks are still
  // queued up. Note that this won't cause us to fail to run java tasks using
  // QuitWhenIdle, as the JavaHandlerThread will finish running all currently
  // scheduled tasks before it quits. Also note that we can't just add an idle
  // callback to the java looper, as that will fire even if application tasks
  // are still queued up.
  DoIdleWork();
  if (!next_work_info.delayed_run_time.is_max())
    ScheduleDelayedWork(next_work_info.delayed_run_time);
}

void MessagePumpForUI::DoIdleWork() {
  if (delegate_->DoIdleWork()) {
    // If DoIdleWork() resulted in any work, we're not idle yet. We need to pump
    // the loop here because we may in fact be idle after doing idle work
    // without any new tasks being queued.
    ScheduleWork();
  }
}

void MessagePumpForUI::Run(Delegate* delegate) {
  DCHECK(IsTestImplementation());
  // This function is only called in tests. We manually pump the native looper
  // which won't run any java tasks.
  quit_ = false;

  SetDelegate(delegate);

  // Pump the loop once in case we're starting off idle as ALooper_pollOnce will
  // never return in that case.
  ScheduleWork();
  while (true) {
    // Waits for either the delayed, or non-delayed fds to be signalled, calling
    // either OnDelayedLooperCallback, or OnNonDelayedLooperCallback,
    // respectively. This uses Android's Looper implementation, which is based
    // off of epoll.
    ALooper_pollOnce(-1, nullptr, nullptr, nullptr);
    if (quit_)
      break;
  }
}

void MessagePumpForUI::Attach(Delegate* delegate) {
  DCHECK(!quit_);

  // Since the Looper is controlled by the UI thread or JavaHandlerThread, we
  // can't use Run() like we do on other platforms or we would prevent Java
  // tasks from running. Instead we create and initialize a run loop here, then
  // return control back to the Looper.

  SetDelegate(delegate);
  run_loop_ = std::make_unique<RunLoop>();
  // Since the RunLoop was just created above, BeforeRun should be guaranteed to
  // return true (it only returns false if the RunLoop has been Quit already).
  if (!run_loop_->BeforeRun())
    NOTREACHED();
}

void MessagePumpForUI::Quit() {
  if (quit_)
    return;

  quit_ = true;

  int64_t value;
  // Clear any pending timer.
  read(delayed_fd_, &value, sizeof(value));
  // Clear the eventfd.
  read(non_delayed_fd_, &value, sizeof(value));

  if (run_loop_) {
    run_loop_->AfterRun();
    run_loop_ = nullptr;
  }
  if (on_quit_callback_) {
    std::move(on_quit_callback_).Run();
  }
}

void MessagePumpForUI::ScheduleWork() {
  // Write (add) 1 to the eventfd. This tells the Looper to wake up and call our
  // callback, allowing us to run tasks. This also allows us to detect, when we
  // clear the fd, whether additional work was scheduled after we finished
  // performing work, but before we cleared the fd, as we'll read back >=2
  // instead of 1 in that case.
  // See the eventfd man pages
  // (http://man7.org/linux/man-pages/man2/eventfd.2.html) for details on how
  // the read and write APIs for this file descriptor work, specifically without
  // EFD_SEMAPHORE.
  uint64_t value = 1;
  int ret = write(non_delayed_fd_, &value, sizeof(value));
  DPCHECK(ret >= 0);
}

void MessagePumpForUI::ScheduleDelayedWork(const TimeTicks& delayed_work_time) {
  if (ShouldQuit())
    return;

  if (delayed_scheduled_time_ && *delayed_scheduled_time_ == delayed_work_time)
    return;

  DCHECK(!delayed_work_time.is_null());
  delayed_scheduled_time_ = delayed_work_time;
  int64_t nanos = delayed_work_time.since_origin().InNanoseconds();
  struct itimerspec ts;
  ts.it_interval.tv_sec = 0;  // Don't repeat.
  ts.it_interval.tv_nsec = 0;
  ts.it_value.tv_sec = nanos / TimeTicks::kNanosecondsPerSecond;
  ts.it_value.tv_nsec = nanos % TimeTicks::kNanosecondsPerSecond;

  int ret = timerfd_settime(delayed_fd_, TFD_TIMER_ABSTIME, &ts, nullptr);
  DPCHECK(ret >= 0);
}

void MessagePumpForUI::QuitWhenIdle(base::OnceClosure callback) {
  DCHECK(!on_quit_callback_);
  DCHECK(run_loop_);
  on_quit_callback_ = std::move(callback);
  run_loop_->QuitWhenIdle();
  // Pump the loop in case we're already idle.
  ScheduleWork();
}

bool MessagePumpForUI::IsTestImplementation() const {
  return false;
}

}  // namespace base
