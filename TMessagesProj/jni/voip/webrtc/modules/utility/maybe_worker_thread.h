/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_UTILITY_MAYBE_WORKER_THREAD_H_
#define MODULES_UTILITY_MAYBE_WORKER_THREAD_H_

#include <memory>

#include "absl/strings/string_view.h"
#include "api/field_trials_view.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_base.h"
#include "api/task_queue/task_queue_factory.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

// Helper class used by experiment to replace usage of the
// RTP worker task queue owned by RtpTransportControllerSend, and the pacer task
// queue owned by TaskQueuePacedSender with the one and only worker thread.
// Tasks will run on the target sequence which is either the worker thread or
// one of these task queues depending on the field trial
// "WebRTC-SendPacketsOnWorkerThread".
// This class is assumed to be created on the worker thread and the worker
// thread is assumed to outlive an instance of this class.
//
// Experiment can be tracked in
// https://bugs.chromium.org/p/webrtc/issues/detail?id=14502
//
// After experiment evaluation, this class should be deleted.
// Calls to RunOrPost and RunSynchronous should be removed and the task should
// be invoked immediately.
// Instead of MaybeSafeTask a SafeTask should be used when posting tasks.
class RTC_LOCKABLE MaybeWorkerThread {
 public:
  MaybeWorkerThread(const FieldTrialsView& field_trials,
                    absl::string_view task_queue_name,
                    TaskQueueFactory* factory);
  ~MaybeWorkerThread();

  // Runs `task` immediately on the worker thread if in experiment, otherwise
  // post the task on the task queue.
  void RunOrPost(absl::AnyInvocable<void() &&> task);
  // Runs `task` immediately on the worker thread if in experiment, otherwise
  // post the task on the task queue and use an even to wait for completion.
  void RunSynchronous(absl::AnyInvocable<void() &&> task);

  // Used for posting delayed or repeated tasks on the worker thread or task
  // queue depending on the field trial. DCHECKs that this method is called on
  // the target sequence.
  TaskQueueBase* TaskQueueForDelayedTasks() const;

  // Used when a task has to be posted from one sequence to the target
  // sequence. A task should only be posted if a sequence hop is needed.
  TaskQueueBase* TaskQueueForPost() const;

  // Workaround to use a SafeTask only if the target sequence is the worker
  // thread. This is used when a SafeTask can not be used because the object
  // that posted the task is not destroyed on the target sequence. Instead, the
  // caller has to guarantee that this MaybeWorkerThread is destroyed first
  // since that guarantee that the posted task is deleted or run before the
  // owning class.
  absl::AnyInvocable<void() &&> MaybeSafeTask(
      rtc::scoped_refptr<PendingTaskSafetyFlag> flag,
      absl::AnyInvocable<void() &&> task);

  // To implement macro RTC_DCHECK_RUN_ON.
  // Implementation delegate to the actual used sequence.
  bool IsCurrent() const;

 private:
  SequenceChecker sequence_checker_;
  std::unique_ptr<TaskQueueBase, TaskQueueDeleter> owned_task_queue_;
  TaskQueueBase* const worker_thread_;
};

}  // namespace webrtc

#endif  // MODULES_UTILITY_MAYBE_WORKER_THREAD_H_
