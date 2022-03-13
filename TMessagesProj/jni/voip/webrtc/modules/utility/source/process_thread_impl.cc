/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/utility/source/process_thread_impl.h"

#include <string>

#include "modules/include/module.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

namespace webrtc {
namespace {

// We use this constant internally to signal that a module has requested
// a callback right away.  When this is set, no call to TimeUntilNextProcess
// should be made, but Process() should be called directly.
const int64_t kCallProcessImmediately = -1;

int64_t GetNextCallbackTime(Module* module, int64_t time_now) {
  int64_t interval = module->TimeUntilNextProcess();
  if (interval < 0) {
    // Falling behind, we should call the callback now.
    return time_now;
  }
  return time_now + interval;
}
}  // namespace

ProcessThread::~ProcessThread() {}

// static
std::unique_ptr<ProcessThread> ProcessThread::Create(const char* thread_name) {
  return std::unique_ptr<ProcessThread>(new ProcessThreadImpl(thread_name));
}

ProcessThreadImpl::ProcessThreadImpl(const char* thread_name)
    : stop_(false), thread_name_(thread_name) {}

ProcessThreadImpl::~ProcessThreadImpl() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!stop_);

  while (!delayed_tasks_.empty()) {
    delete delayed_tasks_.top().task;
    delayed_tasks_.pop();
  }

  while (!queue_.empty()) {
    delete queue_.front();
    queue_.pop();
  }
}

void ProcessThreadImpl::Delete() {
  RTC_LOG(LS_WARNING) << "Process thread " << thread_name_
                      << " is destroyed as a TaskQueue.";
  Stop();
  delete this;
}

// Doesn't need locking, because the contending thread isn't running.
void ProcessThreadImpl::Start() RTC_NO_THREAD_SAFETY_ANALYSIS {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(thread_.empty());
  if (!thread_.empty())
    return;

  RTC_DCHECK(!stop_);

  for (ModuleCallback& m : modules_)
    m.module->ProcessThreadAttached(this);

  thread_ = rtc::PlatformThread::SpawnJoinable(
      [this] {
        CurrentTaskQueueSetter set_current(this);
        while (Process()) {
        }
      },
      thread_name_);
}

void ProcessThreadImpl::Stop() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (thread_.empty())
    return;

  {
    // Need to take lock, for synchronization with `thread_`.
    MutexLock lock(&mutex_);
    stop_ = true;
  }

  wake_up_.Set();
  thread_.Finalize();

  StopNoLocks();
}

// No locking needed, since this is called after the contending thread is
// stopped.
void ProcessThreadImpl::StopNoLocks() RTC_NO_THREAD_SAFETY_ANALYSIS {
  RTC_DCHECK(thread_.empty());
  stop_ = false;

  for (ModuleCallback& m : modules_)
    m.module->ProcessThreadAttached(nullptr);
}

void ProcessThreadImpl::WakeUp(Module* module) {
  // Allowed to be called on any thread.
  auto holds_mutex = [this] {
    if (!IsCurrent()) {
      return false;
    }
    RTC_DCHECK_RUN_ON(this);
    return holds_mutex_;
  };
  if (holds_mutex()) {
    // Avoid locking if called on the ProcessThread, via a module's Process),
    WakeUpNoLocks(module);
  } else {
    MutexLock lock(&mutex_);
    WakeUpInternal(module);
  }
  wake_up_.Set();
}

// Must be called only indirectly from Process, which already holds the lock.
void ProcessThreadImpl::WakeUpNoLocks(Module* module)
    RTC_NO_THREAD_SAFETY_ANALYSIS {
  RTC_DCHECK_RUN_ON(this);
  WakeUpInternal(module);
}

void ProcessThreadImpl::WakeUpInternal(Module* module) {
  for (ModuleCallback& m : modules_) {
    if (m.module == module)
      m.next_callback = kCallProcessImmediately;
  }
}

void ProcessThreadImpl::PostTask(std::unique_ptr<QueuedTask> task) {
  // Allowed to be called on any thread, except from a module's Process method.
  if (IsCurrent()) {
    RTC_DCHECK_RUN_ON(this);
    RTC_DCHECK(!holds_mutex_) << "Calling ProcessThread::PostTask from "
                                 "Module::Process is not supported";
  }
  {
    MutexLock lock(&mutex_);
    queue_.push(task.release());
  }
  wake_up_.Set();
}

void ProcessThreadImpl::PostDelayedTask(std::unique_ptr<QueuedTask> task,
                                        uint32_t milliseconds) {
  int64_t run_at_ms = rtc::TimeMillis() + milliseconds;
  bool recalculate_wakeup_time;
  {
    MutexLock lock(&mutex_);
    recalculate_wakeup_time =
        delayed_tasks_.empty() || run_at_ms < delayed_tasks_.top().run_at_ms;
    delayed_tasks_.emplace(run_at_ms, std::move(task));
  }
  if (recalculate_wakeup_time) {
    wake_up_.Set();
  }
}

void ProcessThreadImpl::RegisterModule(Module* module,
                                       const rtc::Location& from) {
  TRACE_EVENT0("webrtc", "ProcessThreadImpl::RegisterModule");
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(module) << from.ToString();

#if RTC_DCHECK_IS_ON
  {
    // Catch programmer error.
    MutexLock lock(&mutex_);
    for (const ModuleCallback& mc : modules_) {
      RTC_DCHECK(mc.module != module)
          << "Already registered here: " << mc.location.ToString()
          << "\n"
             "Now attempting from here: "
          << from.ToString();
    }
  }
#endif

  // Now that we know the module isn't in the list, we'll call out to notify
  // the module that it's attached to the worker thread.  We don't hold
  // the lock while we make this call.
  if (!thread_.empty())
    module->ProcessThreadAttached(this);

  {
    MutexLock lock(&mutex_);
    modules_.push_back(ModuleCallback(module, from));
  }

  // Wake the thread calling ProcessThreadImpl::Process() to update the
  // waiting time. The waiting time for the just registered module may be
  // shorter than all other registered modules.
  wake_up_.Set();
}

void ProcessThreadImpl::DeRegisterModule(Module* module) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(module);

  {
    MutexLock lock(&mutex_);
    modules_.remove_if(
        [&module](const ModuleCallback& m) { return m.module == module; });
  }

  // Notify the module that it's been detached.
  module->ProcessThreadAttached(nullptr);
}

bool ProcessThreadImpl::Process() {
  TRACE_EVENT1("webrtc", "ProcessThreadImpl", "name", thread_name_);
  int64_t now = rtc::TimeMillis();
  int64_t next_checkpoint = now + (1000 * 60);
  RTC_DCHECK_RUN_ON(this);
  {
    MutexLock lock(&mutex_);
    if (stop_)
      return false;
    for (ModuleCallback& m : modules_) {
      // TODO(tommi): Would be good to measure the time TimeUntilNextProcess
      // takes and dcheck if it takes too long (e.g. >=10ms).  Ideally this
      // operation should not require taking a lock, so querying all modules
      // should run in a matter of nanoseconds.
      if (m.next_callback == 0)
        m.next_callback = GetNextCallbackTime(m.module, now);

      // Set to true for the duration of the calls to modules' Process().
      holds_mutex_ = true;
      if (m.next_callback <= now ||
          m.next_callback == kCallProcessImmediately) {
        {
          TRACE_EVENT2("webrtc", "ModuleProcess", "function",
                       m.location.function_name(), "file",
                       m.location.file_name());
          m.module->Process();
        }
        // Use a new 'now' reference to calculate when the next callback
        // should occur.  We'll continue to use 'now' above for the baseline
        // of calculating how long we should wait, to reduce variance.
        int64_t new_now = rtc::TimeMillis();
        m.next_callback = GetNextCallbackTime(m.module, new_now);
      }
      holds_mutex_ = false;

      if (m.next_callback < next_checkpoint)
        next_checkpoint = m.next_callback;
    }

    while (!delayed_tasks_.empty() && delayed_tasks_.top().run_at_ms <= now) {
      queue_.push(delayed_tasks_.top().task);
      delayed_tasks_.pop();
    }

    if (!delayed_tasks_.empty()) {
      next_checkpoint =
          std::min(next_checkpoint, delayed_tasks_.top().run_at_ms);
    }

    while (!queue_.empty()) {
      QueuedTask* task = queue_.front();
      queue_.pop();
      mutex_.Unlock();
      if (task->Run()) {
        delete task;
      }
      mutex_.Lock();
    }
  }

  int64_t time_to_wait = next_checkpoint - rtc::TimeMillis();
  if (time_to_wait > 0)
    wake_up_.Wait(static_cast<int>(time_to_wait));

  return true;
}
}  // namespace webrtc
