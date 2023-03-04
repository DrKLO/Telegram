/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TASK_QUEUE_H_
#define RTC_BASE_TASK_QUEUE_H_

#include <stdint.h>

#include <memory>
#include <utility>

#include "absl/functional/any_invocable.h"
#include "absl/memory/memory.h"
#include "api/task_queue/task_queue_base.h"
#include "api/task_queue/task_queue_factory.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread_annotations.h"

namespace rtc {
// Implements a task queue that asynchronously executes tasks in a way that
// guarantees that they're executed in FIFO order and that tasks never overlap.
// Tasks may always execute on the same worker thread and they may not.
// To DCHECK that tasks are executing on a known task queue, use IsCurrent().
//
// Here are some usage examples:
//
//   1) Asynchronously running a lambda:
//
//     class MyClass {
//       ...
//       TaskQueue queue_("MyQueue");
//     };
//
//     void MyClass::StartWork() {
//       queue_.PostTask([]() { Work(); });
//     ...
//
//   2) Posting a custom task on a timer.  The task posts itself again after
//      every running:
//
//     class TimerTask : public QueuedTask {
//      public:
//       TimerTask() {}
//      private:
//       bool Run() override {
//         ++count_;
//         TaskQueueBase::Current()->PostDelayedTask(
//             absl::WrapUnique(this), 1000);
//         // Ownership has been transferred to the next occurance,
//         // so return false to prevent from being deleted now.
//         return false;
//       }
//       int count_ = 0;
//     };
//     ...
//     queue_.PostDelayedTask(std::make_unique<TimerTask>(), 1000);
//
// For more examples, see task_queue_unittests.cc.
//
// A note on destruction:
//
// When a TaskQueue is deleted, pending tasks will not be executed but they will
// be deleted.  The deletion of tasks may happen asynchronously after the
// TaskQueue itself has been deleted or it may happen synchronously while the
// TaskQueue instance is being deleted.  This may vary from one OS to the next
// so assumptions about lifetimes of pending tasks should not be made.
class RTC_LOCKABLE RTC_EXPORT TaskQueue {
 public:
  // TaskQueue priority levels. On some platforms these will map to thread
  // priorities, on others such as Mac and iOS, GCD queue priorities.
  using Priority = ::webrtc::TaskQueueFactory::Priority;

  explicit TaskQueue(std::unique_ptr<webrtc::TaskQueueBase,
                                     webrtc::TaskQueueDeleter> task_queue);
  ~TaskQueue();

  TaskQueue(const TaskQueue&) = delete;
  TaskQueue& operator=(const TaskQueue&) = delete;

  // Used for DCHECKing the current queue.
  bool IsCurrent() const;

  // Returns non-owning pointer to the task queue implementation.
  webrtc::TaskQueueBase* Get() { return impl_; }

  void PostTask(absl::AnyInvocable<void() &&> task) {
    impl_->PostTask(std::move(task));
  }
  void PostDelayedTask(absl::AnyInvocable<void() &&> task,
                       webrtc::TimeDelta delay) {
    impl_->PostDelayedTask(std::move(task), delay);
  }
  void PostDelayedHighPrecisionTask(absl::AnyInvocable<void() &&> task,
                                    webrtc::TimeDelta delay) {
    impl_->PostDelayedHighPrecisionTask(std::move(task), delay);
  }

 private:
  webrtc::TaskQueueBase* const impl_;
};

}  // namespace rtc

#endif  // RTC_BASE_TASK_QUEUE_H_
