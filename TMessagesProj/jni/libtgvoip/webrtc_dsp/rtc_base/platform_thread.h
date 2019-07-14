/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_PLATFORM_THREAD_H_
#define RTC_BASE_PLATFORM_THREAD_H_

#ifndef WEBRTC_WIN
#include <pthread.h>
#endif
#include <string>

#include "rtc_base/constructormagic.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/thread_checker.h"

namespace rtc {

// Callback function that the spawned thread will enter once spawned.
// A return value of false is interpreted as that the function has no
// more work to do and that the thread can be released.
typedef bool (*ThreadRunFunctionDeprecated)(void*);
typedef void (*ThreadRunFunction)(void*);

enum ThreadPriority {
#ifdef WEBRTC_WIN
  kLowPriority = THREAD_PRIORITY_BELOW_NORMAL,
  kNormalPriority = THREAD_PRIORITY_NORMAL,
  kHighPriority = THREAD_PRIORITY_ABOVE_NORMAL,
  kHighestPriority = THREAD_PRIORITY_HIGHEST,
  kRealtimePriority = THREAD_PRIORITY_TIME_CRITICAL
#else
  kLowPriority = 1,
  kNormalPriority = 2,
  kHighPriority = 3,
  kHighestPriority = 4,
  kRealtimePriority = 5
#endif
};

// Represents a simple worker thread.  The implementation must be assumed
// to be single threaded, meaning that all methods of the class, must be
// called from the same thread, including instantiation.
class PlatformThread {
 public:
  PlatformThread(ThreadRunFunctionDeprecated func,
                 void* obj,
                 const char* thread_name);
  PlatformThread(ThreadRunFunction func,
                 void* obj,
                 const char* thread_name,
                 ThreadPriority priority = kNormalPriority);
  virtual ~PlatformThread();

  const std::string& name() const { return name_; }

  // Spawns a thread and tries to set thread priority according to the priority
  // from when CreateThread was called.
  void Start();

  bool IsRunning() const;

  // Returns an identifier for the worker thread that can be used to do
  // thread checks.
  PlatformThreadRef GetThreadRef() const;

  // Stops (joins) the spawned thread.
  void Stop();

  // Set the priority of the thread. Must be called when thread is running.
  // TODO(tommi): Make private and only allow public support via ctor.
  bool SetPriority(ThreadPriority priority);

 protected:
#if defined(WEBRTC_WIN)
  // Exposed to derived classes to allow for special cases specific to Windows.
  bool QueueAPC(PAPCFUNC apc_function, ULONG_PTR data);
#endif

 private:
  void Run();

  ThreadRunFunctionDeprecated const run_function_deprecated_ = nullptr;
  ThreadRunFunction const run_function_ = nullptr;
  const ThreadPriority priority_ = kNormalPriority;
  void* const obj_;
  // TODO(pbos): Make sure call sites use string literals and update to a const
  // char* instead of a std::string.
  const std::string name_;
  rtc::ThreadChecker thread_checker_;
  rtc::ThreadChecker spawned_thread_checker_;
#if defined(WEBRTC_WIN)
  static DWORD WINAPI StartThread(void* param);

  bool stop_ = false;
  HANDLE thread_ = nullptr;
  DWORD thread_id_ = 0;
#else
  static void* StartThread(void* param);

  // An atomic flag that we use to stop the thread. Only modified on the
  // controlling thread and checked on the worker thread.
  volatile int stop_flag_ = 0;
  pthread_t thread_ = 0;
#endif  // defined(WEBRTC_WIN)
  RTC_DISALLOW_COPY_AND_ASSIGN(PlatformThread);
};

}  // namespace rtc

#endif  // RTC_BASE_PLATFORM_THREAD_H_
