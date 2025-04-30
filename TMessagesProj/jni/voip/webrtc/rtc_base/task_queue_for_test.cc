/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/task_queue_for_test.h"

#include <memory>
#include <utility>

#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/task_queue_base.h"

namespace webrtc {

TaskQueueForTest::TaskQueueForTest(
    std::unique_ptr<TaskQueueBase, TaskQueueDeleter> task_queue)
    : impl_(std::move(task_queue)) {}

TaskQueueForTest::TaskQueueForTest(absl::string_view name,
                                   TaskQueueFactory::Priority priority)
    : impl_(CreateDefaultTaskQueueFactory()->CreateTaskQueue(name, priority)) {}

TaskQueueForTest::~TaskQueueForTest() {
  // Stop the TaskQueue before invalidating impl_ pointer so that tasks that
  // race with the TaskQueueForTest destructor could still use TaskQueueForTest
  // functions like 'IsCurrent'.
  impl_.get_deleter()(impl_.get());
  impl_.release();
}

}  // namespace webrtc
