// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_THREADING_SEQUENCED_TASK_RUNNER_HANDLE_H_
#define BASE_THREADING_SEQUENCED_TASK_RUNNER_HANDLE_H_

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/memory/scoped_refptr.h"
#include "base/sequenced_task_runner.h"

namespace base {

class ThreadTaskRunnerHandle;

class BASE_EXPORT SequencedTaskRunnerHandle {
 public:
  // Returns a SequencedTaskRunner which guarantees that posted tasks will only
  // run after the current task is finished and will satisfy a SequenceChecker.
  // It should only be called if IsSet() returns true (see the comment there for
  // the requirements).
  static const scoped_refptr<SequencedTaskRunner>& Get() WARN_UNUSED_RESULT;

  // Returns true if one of the following conditions is fulfilled:
  // a) A SequencedTaskRunner has been assigned to the current thread by
  //    instantiating a SequencedTaskRunnerHandle.
  // b) The current thread has a ThreadTaskRunnerHandle (which includes any
  //    thread that has a MessageLoop associated with it).
  static bool IsSet() WARN_UNUSED_RESULT;

  // Binds |task_runner| to the current thread.
  explicit SequencedTaskRunnerHandle(
      scoped_refptr<SequencedTaskRunner> task_runner);
  ~SequencedTaskRunnerHandle();

 private:
  // Friend needed for ThreadTaskRunnerHandle::OverrideForTesting().
  friend class ThreadTaskRunnerHandle;

  scoped_refptr<SequencedTaskRunner> task_runner_;

  DISALLOW_COPY_AND_ASSIGN(SequencedTaskRunnerHandle);
};

}  // namespace base

#endif  // BASE_THREADING_SEQUENCED_TASK_RUNNER_HANDLE_H_
