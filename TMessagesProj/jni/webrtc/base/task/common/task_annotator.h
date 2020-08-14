// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_COMMON_TASK_ANNOTATOR_H_
#define BASE_TASK_COMMON_TASK_ANNOTATOR_H_

#include <stdint.h>

#include <memory>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/pending_task.h"

namespace base {

// Implements common debug annotations for posted tasks. This includes data
// such as task origins, IPC message contexts, queueing durations and memory
// usage.
class BASE_EXPORT TaskAnnotator {
 public:
  class ObserverForTesting {
   public:
    // Invoked just before RunTask() in the scope in which the task is about to
    // be executed.
    virtual void BeforeRunTask(const PendingTask* pending_task) = 0;
  };

  // This is used to set the |ipc_hash| field for PendingTasks. It is intended
  // to be used only from within generated IPC handler dispatch code.
  class ScopedSetIpcHash;

  static const PendingTask* CurrentTaskForThread();

  TaskAnnotator();
  ~TaskAnnotator();

  // Called to indicate that a task is about to be queued to run in the future,
  // giving one last chance for this TaskAnnotator to add metadata to
  // |pending_task| before it is moved into the queue. |task_queue_name| must
  // live for the duration of the process.
  void WillQueueTask(const char* trace_event_name,
                     PendingTask* pending_task,
                     const char* task_queue_name);

  // Run a previously queued task.
  void RunTask(const char* trace_event_name, PendingTask* pending_task);

  // Creates a process-wide unique ID to represent this task in trace events.
  // This will be mangled with a Process ID hash to reduce the likelyhood of
  // colliding with TaskAnnotator pointers on other processes. Callers may use
  // this when generating their own flow events (i.e. when passing
  // |queue_function == nullptr| in above methods).
  uint64_t GetTaskTraceID(const PendingTask& task) const;

 private:
  friend class TaskAnnotatorBacktraceIntegrationTest;

  // Registers an ObserverForTesting that will be invoked by all TaskAnnotators'
  // RunTask(). This registration and the implementation of BeforeRunTask() are
  // responsible to ensure thread-safety.
  static void RegisterObserverForTesting(ObserverForTesting* observer);
  static void ClearObserverForTesting();

  DISALLOW_COPY_AND_ASSIGN(TaskAnnotator);
};

class BASE_EXPORT TaskAnnotator::ScopedSetIpcHash {
 public:
  explicit ScopedSetIpcHash(uint32_t ipc_hash);
  ~ScopedSetIpcHash();

 private:
  std::unique_ptr<PendingTask> dummy_pending_task_;
  uint32_t old_ipc_hash_ = 0;

  DISALLOW_COPY_AND_ASSIGN(ScopedSetIpcHash);
};

}  // namespace base

#endif  // BASE_TASK_COMMON_TASK_ANNOTATOR_H_
