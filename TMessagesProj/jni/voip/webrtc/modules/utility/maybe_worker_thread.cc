/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/utility/maybe_worker_thread.h"

#include <utility>

#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_base.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_queue.h"

namespace webrtc {

MaybeWorkerThread::MaybeWorkerThread(const FieldTrialsView& field_trials,
                                     absl::string_view task_queue_name,
                                     TaskQueueFactory* factory)
    : owned_task_queue_(
          field_trials.IsEnabled("WebRTC-SendPacketsOnWorkerThread")
              ? nullptr
              : factory->CreateTaskQueue(task_queue_name,
                                         rtc::TaskQueue::Priority::NORMAL)),
      worker_thread_(TaskQueueBase::Current()) {
  RTC_DCHECK(worker_thread_);
  RTC_LOG(LS_INFO) << "WebRTC-SendPacketsOnWorkerThread"
                   << (owned_task_queue_ ? " Disabled" : " Enabled");
}

MaybeWorkerThread::~MaybeWorkerThread() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  if (owned_task_queue_) {
    // owned_task_queue_ must be a valid pointer when the task queue is
    // destroyed since there may be tasks that use this object that run when the
    // task queue is deleted.
    owned_task_queue_->Delete();
    owned_task_queue_.release();
  }
}

void MaybeWorkerThread::RunSynchronous(absl::AnyInvocable<void() &&> task) {
  if (owned_task_queue_) {
    rtc::Event thread_sync_event;
    auto closure = [&thread_sync_event, task = std::move(task)]() mutable {
      std::move(task)();
      thread_sync_event.Set();
    };
    owned_task_queue_->PostTask(std::move(closure));
    thread_sync_event.Wait(rtc::Event::kForever);
  } else {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    std::move(task)();
  }
}

void MaybeWorkerThread::RunOrPost(absl::AnyInvocable<void() &&> task) {
  if (owned_task_queue_) {
    owned_task_queue_->PostTask(std::move(task));
  } else {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    std::move(task)();
  }
}

TaskQueueBase* MaybeWorkerThread::TaskQueueForDelayedTasks() const {
  RTC_DCHECK(IsCurrent());
  return owned_task_queue_ ? owned_task_queue_.get() : worker_thread_;
}

TaskQueueBase* MaybeWorkerThread::TaskQueueForPost() const {
  return owned_task_queue_ ? owned_task_queue_.get() : worker_thread_;
}

bool MaybeWorkerThread::IsCurrent() const {
  if (owned_task_queue_) {
    return owned_task_queue_->IsCurrent();
  }
  return worker_thread_->IsCurrent();
}

absl::AnyInvocable<void() &&> MaybeWorkerThread::MaybeSafeTask(
    rtc::scoped_refptr<PendingTaskSafetyFlag> flag,
    absl::AnyInvocable<void() &&> task) {
  if (owned_task_queue_) {
    return task;
  } else {
    return SafeTask(std::move(flag), std::move(task));
  }
}

}  // namespace webrtc
