/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_UTILITY_SOURCE_PROCESS_THREAD_IMPL_H_
#define MODULES_UTILITY_SOURCE_PROCESS_THREAD_IMPL_H_

#include <stdint.h>

#include <list>
#include <memory>
#include <queue>

#include "api/sequence_checker.h"
#include "api/task_queue/queued_task.h"
#include "modules/include/module.h"
#include "modules/utility/include/process_thread.h"
#include "rtc_base/event.h"
#include "rtc_base/location.h"
#include "rtc_base/platform_thread.h"

namespace webrtc {

class ProcessThreadImpl : public ProcessThread {
 public:
  explicit ProcessThreadImpl(const char* thread_name);
  ~ProcessThreadImpl() override;

  void Start() override;
  void Stop() override;

  void WakeUp(Module* module) override;
  void PostTask(std::unique_ptr<QueuedTask> task) override;
  void PostDelayedTask(std::unique_ptr<QueuedTask> task,
                       uint32_t milliseconds) override;

  void RegisterModule(Module* module, const rtc::Location& from) override;
  void DeRegisterModule(Module* module) override;

 protected:
  bool Process();

 private:
  struct ModuleCallback {
    ModuleCallback() = delete;
    ModuleCallback(ModuleCallback&& cb) = default;
    ModuleCallback(const ModuleCallback& cb) = default;
    ModuleCallback(Module* module, const rtc::Location& location)
        : module(module), location(location) {}
    bool operator==(const ModuleCallback& cb) const {
      return cb.module == module;
    }

    Module* const module;
    int64_t next_callback = 0;  // Absolute timestamp.
    const rtc::Location location;

   private:
    ModuleCallback& operator=(ModuleCallback&);
  };
  struct DelayedTask {
    DelayedTask(int64_t run_at_ms, std::unique_ptr<QueuedTask> task)
        : run_at_ms(run_at_ms), task(task.release()) {}
    friend bool operator<(const DelayedTask& lhs, const DelayedTask& rhs) {
      // Earliest DelayedTask should be at the top of the priority queue.
      return lhs.run_at_ms > rhs.run_at_ms;
    }

    int64_t run_at_ms;
    // DelayedTask owns the `task`, but some delayed tasks must be removed from
    // the std::priority_queue, but mustn't be deleted. std::priority_queue does
    // not give non-const access to the values, so storing unique_ptr would
    // delete the task as soon as it is remove from the priority queue.
    // Thus lifetime of the `task` is managed manually.
    QueuedTask* task;
  };
  typedef std::list<ModuleCallback> ModuleList;

  void Delete() override;
  // The part of Stop processing that doesn't need any locking.
  void StopNoLocks();
  void WakeUpNoLocks(Module* module);
  void WakeUpInternal(Module* module) RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Members protected by this mutex are accessed on the constructor thread and
  // on the spawned process thread, and locking is needed only while the process
  // thread is running.
  Mutex mutex_;

  SequenceChecker thread_checker_;
  rtc::Event wake_up_;
  rtc::PlatformThread thread_;

  ModuleList modules_ RTC_GUARDED_BY(mutex_);
  // Set to true when calling Process, to allow reentrant calls to WakeUp.
  bool holds_mutex_ RTC_GUARDED_BY(this) = false;
  std::queue<QueuedTask*> queue_;
  std::priority_queue<DelayedTask> delayed_tasks_ RTC_GUARDED_BY(mutex_);
  // The `stop_` flag is modified only by the construction thread, protected by
  // `thread_checker_`. It is read also by the spawned `thread_`. The latter
  // thread must take `mutex_` before access, and for thread safety, the
  // constructor thread needs to take `mutex_` when it modifies `stop_` and
  // `thread_` is running. Annotations like RTC_GUARDED_BY doesn't support this
  // usage pattern.
  bool stop_ RTC_GUARDED_BY(mutex_);
  const char* thread_name_;
};

}  // namespace webrtc

#endif  // MODULES_UTILITY_SOURCE_PROCESS_THREAD_IMPL_H_
