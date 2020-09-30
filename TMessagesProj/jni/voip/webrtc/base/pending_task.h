// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PENDING_TASK_H_
#define BASE_PENDING_TASK_H_

#include <array>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/containers/queue.h"
#include "base/location.h"
#include "base/optional.h"
#include "base/time/time.h"

namespace base {

enum class Nestable : uint8_t {
  kNonNestable,
  kNestable,
};

// Contains data about a pending task. Stored in TaskQueue and DelayedTaskQueue
// for use by classes that queue and execute tasks.
struct BASE_EXPORT PendingTask {
  PendingTask();
  PendingTask(const Location& posted_from,
              OnceClosure task,
              TimeTicks delayed_run_time = TimeTicks(),
              Nestable nestable = Nestable::kNestable);
  PendingTask(PendingTask&& other);
  ~PendingTask();

  PendingTask& operator=(PendingTask&& other);

  // Used to support sorting.
  bool operator<(const PendingTask& other) const;

  // The task to run.
  OnceClosure task;

  // The site this PendingTask was posted from.
  Location posted_from;

  // The time when the task should be run. This is null for an immediate task.
  base::TimeTicks delayed_run_time;

  // The time at which the task was queued. For SequenceManager tasks and
  // ThreadPool non-delayed tasks, this happens at post time. For
  // ThreadPool delayed tasks, this happens some time after the task's delay
  // has expired. This is not set for SequenceManager tasks if
  // SetAddQueueTimeToTasks(true) wasn't call. This defaults to a null TimeTicks
  // if the task hasn't been inserted in a sequence yet.
  TimeTicks queue_time;

  // Chain of symbols of the parent tasks which led to this one being posted.
  static constexpr size_t kTaskBacktraceLength = 4;
  std::array<const void*, kTaskBacktraceLength> task_backtrace = {};

  // The context of the IPC message that was being handled when this task was
  // posted. This is a hash of the IPC message name that is set within the scope
  // of an IPC handler and when symbolized uniquely identifies the message being
  // processed. This property is also propagated from one PendingTask to the
  // next. For example, if pending task A was posted while handling an IPC,
  // and pending task B was posted from within pending task A, then pending task
  // B will inherit the |ipc_hash| of pending task A. In some sense this can be
  // interpreted as a "root" task backtrace frame.
  uint32_t ipc_hash = 0;

  // Secondary sort key for run time.
  int sequence_num = 0;

  bool task_backtrace_overflow = false;

  // OK to dispatch from a nested loop.
  Nestable nestable;

  // Needs high resolution timers.
  bool is_high_res = false;
};

using TaskQueue = base::queue<PendingTask>;

// PendingTasks are sorted by their |delayed_run_time| property.
using DelayedTaskQueue = std::priority_queue<base::PendingTask>;

}  // namespace base

#endif  // BASE_PENDING_TASK_H_
