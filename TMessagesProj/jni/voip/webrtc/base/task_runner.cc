// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task_runner.h"

#include <utility>

#include "base/bind.h"
#include "base/compiler_specific.h"
#include "base/logging.h"
#include "base/threading/post_task_and_reply_impl.h"

namespace base {

namespace {

// TODO(akalin): There's only one other implementation of
// PostTaskAndReplyImpl in post_task.cc.  Investigate whether it'll be
// possible to merge the two.
class PostTaskAndReplyTaskRunner : public internal::PostTaskAndReplyImpl {
 public:
  explicit PostTaskAndReplyTaskRunner(TaskRunner* destination);

 private:
  bool PostTask(const Location& from_here, OnceClosure task) override;

  // Non-owning.
  TaskRunner* destination_;
};

PostTaskAndReplyTaskRunner::PostTaskAndReplyTaskRunner(
    TaskRunner* destination) : destination_(destination) {
  DCHECK(destination_);
}

bool PostTaskAndReplyTaskRunner::PostTask(const Location& from_here,
                                          OnceClosure task) {
  return destination_->PostTask(from_here, std::move(task));
}

}  // namespace

bool TaskRunner::PostTask(const Location& from_here, OnceClosure task) {
  return PostDelayedTask(from_here, std::move(task), base::TimeDelta());
}

bool TaskRunner::PostTaskAndReply(const Location& from_here,
                                  OnceClosure task,
                                  OnceClosure reply) {
  return PostTaskAndReplyTaskRunner(this).PostTaskAndReply(
      from_here, std::move(task), std::move(reply));
}

TaskRunner::TaskRunner() = default;

TaskRunner::~TaskRunner() = default;

void TaskRunner::OnDestruct() const {
  delete this;
}

void TaskRunnerTraits::Destruct(const TaskRunner* task_runner) {
  task_runner->OnDestruct();
}

}  // namespace base
