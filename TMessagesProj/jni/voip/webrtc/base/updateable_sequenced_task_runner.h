// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UPDATEABLE_SEQUENCED_TASK_RUNNER_H_
#define BASE_UPDATEABLE_SEQUENCED_TASK_RUNNER_H_

#include "base/sequenced_task_runner.h"
#include "base/task/task_traits.h"

namespace base {

// A SequencedTaskRunner whose posted tasks' priorities can be updated.
class BASE_EXPORT UpdateableSequencedTaskRunner : public SequencedTaskRunner {
 public:
  // Updates the priority for tasks posted through this TaskRunner to
  // |priority|.
  virtual void UpdatePriority(TaskPriority priority) = 0;

 protected:
  UpdateableSequencedTaskRunner() = default;
  ~UpdateableSequencedTaskRunner() override = default;

  DISALLOW_COPY_AND_ASSIGN(UpdateableSequencedTaskRunner);
};

}  // namespace base

#endif  // BASE_UPDATEABLE_SEQUENCED_TASK_RUNNER_H_
