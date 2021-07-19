// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_COMMON_SCOPED_DEFER_TASK_POSTING_H_
#define BASE_TASK_COMMON_SCOPED_DEFER_TASK_POSTING_H_

#include "base/base_export.h"
#include "base/location.h"
#include "base/macros.h"
#include "base/sequenced_task_runner.h"

namespace base {

// Tracing wants to post tasks from within a trace event within PostTask, but
// this can lead to a deadlock. Create a scope to ensure that we are posting
// the tasks in question outside of the scope of the lock.
// NOTE: This scope affects only the thread it is created on. All other threads
// still can post tasks.
//
// TODO(altimin): It should be possible to get rid of this scope, but this
// requires refactoring TimeDomain to ensure that TimeDomain never changes and
// we can read current time without grabbing a lock.
class BASE_EXPORT ScopedDeferTaskPosting {
 public:
  static void PostOrDefer(scoped_refptr<SequencedTaskRunner> task_runner,
                          const Location& from_here,
                          OnceClosure task,
                          base::TimeDelta delay);

  static bool IsPresent();

  ScopedDeferTaskPosting();
  ~ScopedDeferTaskPosting();

 private:
  static ScopedDeferTaskPosting* Get();
  // Returns whether the |scope| was set as active, which happens only
  // when the scope wasn't set before.
  static bool Set(ScopedDeferTaskPosting* scope);

  void DeferTaskPosting(scoped_refptr<SequencedTaskRunner> task_runner,
                        const Location& from_here,
                        OnceClosure task,
                        base::TimeDelta delay);

  struct DeferredTask {
    DeferredTask(scoped_refptr<SequencedTaskRunner> task_runner,
                 Location from_here,
                 OnceClosure task,
                 base::TimeDelta delay);
    DeferredTask(DeferredTask&& task);
    ~DeferredTask();

    scoped_refptr<SequencedTaskRunner> task_runner;
    Location from_here;
    OnceClosure task;
    base::TimeDelta delay;

    DISALLOW_COPY_AND_ASSIGN(DeferredTask);
  };

  std::vector<DeferredTask> deferred_tasks_;

  // Scopes can be nested (e.g. ScheduleWork inside PostTasks can post a task
  // to another task runner), so we want to know whether the scope is top-level
  // or not.
  bool top_level_scope_ = false;

  DISALLOW_COPY_AND_ASSIGN(ScopedDeferTaskPosting);
};

}  // namespace base

#endif  // BASE_TASK_COMMON_SCOPED_DEFER_TASK_POSTING_H_
