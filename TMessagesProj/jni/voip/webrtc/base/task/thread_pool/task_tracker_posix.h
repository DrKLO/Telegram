// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_TASK_TRACKER_POSIX_H_
#define BASE_TASK_THREAD_POOL_TASK_TRACKER_POSIX_H_

#include <memory>

#include "base/base_export.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/message_loop/message_pump_type.h"
#include "base/task/thread_pool/task_tracker.h"
#include "base/threading/platform_thread.h"

namespace base {
namespace internal {

struct Task;

// A TaskTracker that instantiates a FileDescriptorWatcher in the scope in which
// a task runs. Used on all POSIX platforms except NaCl SFI.
// set_io_thread_task_runner() must be called before the
// TaskTracker can run tasks.
class BASE_EXPORT TaskTrackerPosix : public TaskTracker {
 public:
  TaskTrackerPosix(StringPiece name);
  ~TaskTrackerPosix() override;

  // Sets the task runner with which to setup FileDescriptorWatcher in
  // the scope in which tasks run. |io_thread_task_runner| must refer to
  // a Thread with MessagePumpType::IO.
  // Must be called before starting to run tasks.
  // External synchronization is required between a call to this and a call to
  // RunTask().
  void set_io_thread_task_runner(
      scoped_refptr<SingleThreadTaskRunner> io_thread_task_runner) {
    io_thread_task_runner_ = std::move(io_thread_task_runner);
  }

 protected:
  // TaskTracker:
  void RunTask(Task task,
               TaskSource* task_source,
               const TaskTraits& traits) override;

 private:
  scoped_refptr<SingleThreadTaskRunner> io_thread_task_runner_;

  DISALLOW_COPY_AND_ASSIGN(TaskTrackerPosix);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_TASK_TRACKER_POSIX_H_
