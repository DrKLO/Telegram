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

#include "api/task_queue/default_task_queue_factory.h"

namespace webrtc {

TaskQueueForTest::TaskQueueForTest(absl::string_view name, Priority priority)
    : TaskQueue(
          CreateDefaultTaskQueueFactory()->CreateTaskQueue(name, priority)) {}

}  // namespace webrtc
