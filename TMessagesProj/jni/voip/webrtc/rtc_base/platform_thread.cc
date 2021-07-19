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

#include <algorithm>
#include <memory>

#if !defined(WEBRTC_WIN)
#include <sched.h>
#endif

#include "rtc_base/checks.h"

namespace rtc {
namespace {

#if defined(WEBRTC_WIN)
int Win32PriorityFromThreadPriority(ThreadPriority priority) {
  switch (priority) {
    case ThreadPriority::kLow:
      return THREAD_PRIORITY_BELOW_NORMAL;
    case ThreadPriority::kNormal:
      return THREAD_PRIORITY_NORMAL;
    case ThreadPriority::kHigh:
      return THREAD_PRIORITY_ABOVE_NORMAL;
    case ThreadPriority::kRealtime:
      return THREAD_PRIORITY_TIME_CRITICAL;
  }
}
#endif

bool SetPriority(ThreadPriority priority) {
#if defined(WEBRTC_WIN)
  return SetThreadPriority(GetCurrentThread(),
                           Win32PriorityFromThreadPriority(priority)) != FALSE;
#elif defined(__native_client__) || defined(WEBRTC_FUCHSIA)
  // Setting thread priorities is not supported in NaCl or Fuchsia.
  return true;
#elif defined(WEBRTC_CHROMIUM_BUILD) && defined(WEBRTC_LINUX)
  // TODO(tommi): Switch to the same mechanism as Chromium uses for changing
  // thread priorities.
  return true;
#else
  const int policy = SCHED_FIFO;
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
    case ThreadPriority::kLow:
      param.sched_priority = low_prio;
      break;
    case ThreadPriority::kNormal:
      // The -1 ensures that the kHighPriority is always greater or equal to
      // kNormalPriority.
      param.sched_priority = (low_prio + top_prio - 1) / 2;
      break;
    case ThreadPriority::kHigh:
      param.sched_priority = std::max(top_prio - 2, low_prio);
      break;
    case ThreadPriority::kRealtime:
      param.sched_priority = top_prio;
      break;
  }
  return pthread_setschedparam(pthread_self(), policy, &param) == 0;
#endif  // defined(WEBRTC_WIN)
}

#if defined(WEBRTC_WIN)
DWORD WINAPI RunPlatformThread(void* param) {
  // The GetLastError() function only returns valid results when it is called
  // after a Win32 API function that returns a "failed" result. A crash dump
  // contains the result from GetLastError() and to make sure it does not
  // falsely report a Windows error we call SetLastError here.
  ::SetLastError(ERROR_SUCCESS);
  auto function = static_cast<std::function<void()>*>(param);
  (*function)();
  delete function;
  return 0;
}
#else
void* RunPlatformThread(void* param) {
  auto function = static_cast<std::function<void()>*>(param);
  (*function)();
  delete function;
  return 0;
}
#endif  // defined(WEBRTC_WIN)

}  // namespace

PlatformThread::PlatformThread(Handle handle, bool joinable)
    : handle_(handle), joinable_(joinable) {}

PlatformThread::PlatformThread(PlatformThread&& rhs)
    : handle_(rhs.handle_), joinable_(rhs.joinable_) {
  rhs.handle_ = absl::nullopt;
}

PlatformThread& PlatformThread::operator=(PlatformThread&& rhs) {
  Finalize();
  handle_ = rhs.handle_;
  joinable_ = rhs.joinable_;
  rhs.handle_ = absl::nullopt;
  return *this;
}

PlatformThread::~PlatformThread() {
  Finalize();
}

PlatformThread PlatformThread::SpawnJoinable(
    std::function<void()> thread_function,
    absl::string_view name,
    ThreadAttributes attributes) {
  return SpawnThread(std::move(thread_function), name, attributes,
                     /*joinable=*/true);
}

PlatformThread PlatformThread::SpawnDetached(
    std::function<void()> thread_function,
    absl::string_view name,
    ThreadAttributes attributes) {
  return SpawnThread(std::move(thread_function), name, attributes,
                     /*joinable=*/false);
}

absl::optional<PlatformThread::Handle> PlatformThread::GetHandle() const {
  return handle_;
}

#if defined(WEBRTC_WIN)
bool PlatformThread::QueueAPC(PAPCFUNC function, ULONG_PTR data) {
  RTC_DCHECK(handle_.has_value());
  return handle_.has_value() ? QueueUserAPC(function, *handle_, data) != FALSE
                             : false;
}
#endif

void PlatformThread::Finalize() {
  if (!handle_.has_value())
    return;
#if defined(WEBRTC_WIN)
  if (joinable_)
    WaitForSingleObject(*handle_, INFINITE);
  CloseHandle(*handle_);
#else
  if (joinable_)
    RTC_CHECK_EQ(0, pthread_join(*handle_, nullptr));
#endif
  handle_ = absl::nullopt;
}

PlatformThread PlatformThread::SpawnThread(
    std::function<void()> thread_function,
    absl::string_view name,
    ThreadAttributes attributes,
    bool joinable) {
  RTC_DCHECK(thread_function);
  RTC_DCHECK(!name.empty());
  // TODO(tommi): Consider lowering the limit to 15 (limit on Linux).
  RTC_DCHECK(name.length() < 64);
  auto start_thread_function_ptr =
      new std::function<void()>([thread_function = std::move(thread_function),
                                 name = std::string(name), attributes] {
        rtc::SetCurrentThreadName(name.c_str());
        SetPriority(attributes.priority);
        thread_function();
      });
#if defined(WEBRTC_WIN)
  // See bug 2902 for background on STACK_SIZE_PARAM_IS_A_RESERVATION.
  // Set the reserved stack stack size to 1M, which is the default on Windows
  // and Linux.
  DWORD thread_id = 0;
  PlatformThread::Handle handle = ::CreateThread(
      nullptr, 1024 * 1024, &RunPlatformThread, start_thread_function_ptr,
      STACK_SIZE_PARAM_IS_A_RESERVATION, &thread_id);
  RTC_CHECK(handle) << "CreateThread failed";
#else
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  // Set the stack stack size to 1M.
  pthread_attr_setstacksize(&attr, 1024 * 1024);
  pthread_attr_setdetachstate(
      &attr, joinable ? PTHREAD_CREATE_JOINABLE : PTHREAD_CREATE_DETACHED);
  PlatformThread::Handle handle;
  RTC_CHECK_EQ(0, pthread_create(&handle, &attr, &RunPlatformThread,
                                 start_thread_function_ptr));
  pthread_attr_destroy(&attr);
#endif  // defined(WEBRTC_WIN)
  return PlatformThread(handle, joinable);
}

}  // namespace rtc
