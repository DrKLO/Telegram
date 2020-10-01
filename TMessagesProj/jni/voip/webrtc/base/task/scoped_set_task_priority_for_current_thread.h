// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SCOPED_SET_TASK_PRIORITY_FOR_CURRENT_THREAD_H_
#define BASE_TASK_SCOPED_SET_TASK_PRIORITY_FOR_CURRENT_THREAD_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/task/task_traits.h"

namespace base {
namespace internal {

class BASE_EXPORT ScopedSetTaskPriorityForCurrentThread {
 public:
  // Within the scope of this object, GetTaskPriorityForCurrentThread() will
  // return |priority|.
  ScopedSetTaskPriorityForCurrentThread(TaskPriority priority);
  ~ScopedSetTaskPriorityForCurrentThread();

 private:
  const TaskPriority priority_;

  DISALLOW_COPY_AND_ASSIGN(ScopedSetTaskPriorityForCurrentThread);
};

// Returns the priority of the task running on the current thread,
// or TaskPriority::USER_BLOCKING by default if none.
BASE_EXPORT TaskPriority GetTaskPriorityForCurrentThread();

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_SCOPED_SET_TASK_PRIORITY_FOR_CURRENT_THREAD_H_
