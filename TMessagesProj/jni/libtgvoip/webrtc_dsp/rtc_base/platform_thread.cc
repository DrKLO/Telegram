/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/platform_thread.h"

#if !defined(WEBRTC_WIN)
#include <sched.h>
#endif
#include <stdint.h>
#include <time.h>
#include <algorithm>

#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"
#include "rtc_base/timeutils.h"

namespace rtc {
namespace {
#if defined(WEBRTC_WIN)
void CALLBACK RaiseFlag(ULONG_PTR param) {
  *reinterpret_cast<bool*>(param) = true;
}
#else
struct ThreadAttributes {
  ThreadAttributes() { pthread_attr_init(&attr); }
  ~ThreadAttributes() { pthread_attr_destroy(&attr); }
  pthread_attr_t* operator&() { return &attr; }
  pthread_attr_t attr;
};
#endif  // defined(WEBRTC_WIN)
}

PlatformThread::PlatformThread(ThreadRunFunctionDeprecated func,
                               void* obj,
                               const char* thread_name)
    : run_function_deprecated_(func),
      obj_(obj),
      name_(thread_name ? thread_name : "webrtc") {
  RTC_DCHECK(func);
  RTC_DCHECK(name_.length() < 64);
  spawned_thread_checker_.DetachFromThread();
}

PlatformThread::PlatformThread(ThreadRunFunction func,
                               void* obj,
                               const char* thread_name,
                               ThreadPriority priority /*= kNormalPriority*/)
    : run_function_(func), priority_(priority), obj_(obj), name_(thread_name) {
  RTC_DCHECK(func);
  RTC_DCHECK(!name_.empty());
  // TODO(tommi): Consider lowering the limit to 15 (limit on Linux).
  RTC_DCHECK(name_.length() < 64);
  spawned_thread_checker_.DetachFromThread();
}

PlatformThread::~PlatformThread() {
  RTC_DCHECK(thread_checker_.CalledOnValidThread());
#if defined(WEBRTC_WIN)
  RTC_DCHECK(!thread_);
  RTC_DCHECK(!thread_id_);
#endif  // defined(WEBRTC_WIN)
}

#if defined(WEBRTC_WIN)
DWORD WINAPI PlatformThread::StartThread(void* param) {
  // The GetLastError() function only returns valid results when it is called
  // after a Win32 API function that returns a "failed" result. A crash dump
  // contains the result from GetLastError() and to make sure it does not
  // falsely report a Windows error we call SetLastError here.
  ::SetLastError(ERROR_SUCCESS);
  static_cast<PlatformThread*>(param)->Run();
  return 0;
}
#else
void* PlatformThread::StartThread(void* param) {
  static_cast<PlatformThread*>(param)->Run();
  return 0;
}
#endif  // defined(WEBRTC_WIN)

void PlatformThread::Start() {
  RTC_DCHECK(thread_checker_.CalledOnValidThread());
  RTC_DCHECK(!thread_) << "Thread already started?";
#if defined(WEBRTC_WIN)
  stop_ = false;

  // See bug 2902 for background on STACK_SIZE_PARAM_IS_A_RESERVATION.
  // Set the reserved stack stack size to 1M, which is the default on Windows
  // and Linux.
  thread_ = ::CreateThread(nullptr, 1024 * 1024, &StartThread, this,
                           STACK_SIZE_PARAM_IS_A_RESERVATION, &thread_id_);
  RTC_CHECK(thread_) << "CreateThread failed";
  RTC_DCHECK(thread_id_);
#else
  ThreadAttributes attr;
  // Set the stack stack size to 1M.
  pthread_attr_setstacksize(&attr, 1024 * 1024);
  RTC_CHECK_EQ(0, pthread_create(&thread_, &attr, &StartThread, this));
#endif  // defined(WEBRTC_WIN)
}

bool PlatformThread::IsRunning() const {
  RTC_DCHECK(thread_checker_.CalledOnValidThread());
#if defined(WEBRTC_WIN)
  return thread_ != nullptr;
#else
  return thread_ != 0;
#endif  // defined(WEBRTC_WIN)
}

PlatformThreadRef PlatformThread::GetThreadRef() const {
#if defined(WEBRTC_WIN)
  return thread_id_;
#else
  return thread_;
#endif  // defined(WEBRTC_WIN)
}

void PlatformThread::Stop() {
  RTC_DCHECK(thread_checker_.CalledOnValidThread());
  if (!IsRunning())
    return;

#if defined(WEBRTC_WIN)
  // Set stop_ to |true| on the worker thread.
  bool queued = QueueAPC(&RaiseFlag, reinterpret_cast<ULONG_PTR>(&stop_));
  // Queuing the APC can fail if the thread is being terminated.
  RTC_CHECK(queued || GetLastError() == ERROR_GEN_FAILURE);
  WaitForSingleObject(thread_, INFINITE);
  CloseHandle(thread_);
  thread_ = nullptr;
  thread_id_ = 0;
#else
  if (!run_function_)
    RTC_CHECK_EQ(1, AtomicOps::Increment(&stop_flag_));
  RTC_CHECK_EQ(0, pthread_join(thread_, nullptr));
  if (!run_function_)
    AtomicOps::ReleaseStore(&stop_flag_, 0);
  thread_ = 0;
#endif  // defined(WEBRTC_WIN)
  spawned_thread_checker_.DetachFromThread();
}

// TODO(tommi): Deprecate the loop behavior in PlatformThread.
// * Introduce a new callback type that returns void.
// * Remove potential for a busy loop in PlatformThread.
// * Delegate the responsibility for how to stop the thread, to the
//   implementation that actually uses the thread.
// All implementations will need to be aware of how the thread should be stopped
// and encouraging a busy polling loop, can be costly in terms of power and cpu.
void PlatformThread::Run() {
  // Attach the worker thread checker to this thread.
  RTC_DCHECK(spawned_thread_checker_.CalledOnValidThread());
  rtc::SetCurrentThreadName(name_.c_str());

  if (run_function_) {
    SetPriority(priority_);
    run_function_(obj_);
    return;
  }

// TODO(tommi): Delete the rest of this function when looping isn't supported.
#if RTC_DCHECK_IS_ON
  // These constants control the busy loop detection algorithm below.
  // |kMaxLoopCount| controls the limit for how many times we allow the loop
  // to run within a period, before DCHECKing.
  // |kPeriodToMeasureMs| controls how long that period is.
  static const int kMaxLoopCount = 1000;
  static const int kPeriodToMeasureMs = 100;
  int64_t loop_stamps[kMaxLoopCount] = {};
  int64_t sequence_nr = 0;
#endif

  do {
    // The interface contract of Start/Stop is that for a successful call to
    // Start, there should be at least one call to the run function.  So we
    // call the function before checking |stop_|.
    if (!run_function_deprecated_(obj_))
      break;
#if RTC_DCHECK_IS_ON
    auto id = sequence_nr % kMaxLoopCount;
    loop_stamps[id] = rtc::TimeMillis();
    if (sequence_nr > kMaxLoopCount) {
      auto compare_id = (id + 1) % kMaxLoopCount;
      auto diff = loop_stamps[id] - loop_stamps[compare_id];
      RTC_DCHECK_GE(diff, 0);
      if (diff < kPeriodToMeasureMs) {
        RTC_NOTREACHED() << "This thread is too busy: " << name_ << " " << diff
                         << "ms sequence=" << sequence_nr << " "
                         << loop_stamps[id] << " vs " << loop_stamps[compare_id]
                         << ", " << id << " vs " << compare_id;
      }
    }
    ++sequence_nr;
#endif
#if defined(WEBRTC_WIN)
    // Alertable sleep to permit RaiseFlag to run and update |stop_|.
    SleepEx(0, true);
  } while (!stop_);
#else
#if defined(WEBRTC_MAC) || defined(WEBRTC_ANDROID)
    sched_yield();
#else
    static const struct timespec ts_null = {0};
    nanosleep(&ts_null, nullptr);
#endif
  } while (!AtomicOps::AcquireLoad(&stop_flag_));
#endif  // defined(WEBRTC_WIN)
}

bool PlatformThread::SetPriority(ThreadPriority priority) {
#if RTC_DCHECK_IS_ON
  if (run_function_) {
    // The non-deprecated way of how this function gets called, is that it must
    // be called on the worker thread itself.
    RTC_DCHECK(!thread_checker_.CalledOnValidThread());
    RTC_DCHECK(spawned_thread_checker_.CalledOnValidThread());
  } else {
    // In the case of deprecated use of this method, it must be called on the
    // same thread as the PlatformThread object is constructed on.
    RTC_DCHECK(thread_checker_.CalledOnValidThread());
    RTC_DCHECK(IsRunning());
  }
#endif

#if defined(WEBRTC_WIN)
  return SetThreadPriority(thread_, priority) != FALSE;
#elif defined(__native_client__) || defined(WEBRTC_FUCHSIA)
  // Setting thread priorities is not supported in NaCl or Fuchsia.
  return true;
#elif defined(WEBRTC_CHROMIUM_BUILD) && defined(WEBRTC_LINUX)
  // TODO(tommi): Switch to the same mechanism as Chromium uses for changing
  // thread priorities.
  return true;
#else
#ifdef WEBRTC_THREAD_RR
  const int policy = SCHED_RR;
#else
  const int policy = SCHED_FIFO;
#endif
  const int min_prio = sched_get_priority_min(policy);
  const int max_prio = sched_get_priority_max(policy);
  if (min_prio == -1 || max_prio == -1) {
    return false;
  }

  if (max_prio - min_prio <= 2)
    return false;

  // Convert webrtc priority to system priorities:
  sched_param param;
  const int top_prio = max_prio - 1;
  const int low_prio = min_prio + 1;
  switch (priority) {
    case kLowPriority:
      param.sched_priority = low_prio;
      break;
    case kNormalPriority:
      // The -1 ensures that the kHighPriority is always greater or equal to
      // kNormalPriority.
      param.sched_priority = (low_prio + top_prio - 1) / 2;
      break;
    case kHighPriority:
      param.sched_priority = std::max(top_prio - 2, low_prio);
      break;
    case kHighestPriority:
      param.sched_priority = std::max(top_prio - 1, low_prio);
      break;
    case kRealtimePriority:
      param.sched_priority = top_prio;
      break;
  }
  return pthread_setschedparam(thread_, policy, &param) == 0;
#endif  // defined(WEBRTC_WIN)
}

#if defined(WEBRTC_WIN)
bool PlatformThread::QueueAPC(PAPCFUNC function, ULONG_PTR data) {
  RTC_DCHECK(thread_checker_.CalledOnValidThread());
  RTC_DCHECK(IsRunning());

  return QueueUserAPC(function, thread_, data) != FALSE;
}
#endif

}  // namespace rtc
