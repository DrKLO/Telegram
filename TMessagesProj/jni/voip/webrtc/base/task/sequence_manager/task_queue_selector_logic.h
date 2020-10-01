// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_SELECTOR_LOGIC_H_
#define BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_SELECTOR_LOGIC_H_

namespace base {
namespace sequence_manager {
namespace internal {

// Used to describe the logic trigerred when a task queue is selected to
// service.
// This enum is used for histograms and should not be renumbered.
enum class TaskQueueSelectorLogic {

  // Selected due to priority rules.
  kControlPriorityLogic = 0,
  kHighestPriorityLogic = 1,
  kHighPriorityLogic = 2,
  kNormalPriorityLogic = 3,
  kLowPriorityLogic = 4,
  kBestEffortPriorityLogic = 5,

  // Selected due to starvation logic.
  kHighPriorityStarvationLogic = 6,
  kNormalPriorityStarvationLogic = 7,
  kLowPriorityStarvationLogic = 8,

  kCount = 9,
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_SELECTOR_LOGIC_H_
