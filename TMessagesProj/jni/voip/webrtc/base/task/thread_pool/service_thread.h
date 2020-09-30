// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_SERVICE_THREAD_H_
#define BASE_TASK_THREAD_POOL_SERVICE_THREAD_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/threading/thread.h"
#include "base/time/time.h"
#include "base/timer/timer.h"

namespace base {
namespace internal {

class TaskTracker;

// The ThreadPool's ServiceThread is a mostly idle thread that is responsible
// for handling async events (e.g. delayed tasks and async I/O). Its role is to
// merely forward such events to their destination (hence staying mostly idle
// and highly responsive).
// It aliases Thread::Run() to enforce that ServiceThread::Run() be on the stack
// and make it easier to identify the service thread in stack traces.
class BASE_EXPORT ServiceThread : public Thread {
 public:
  // Constructs a ServiceThread which will record heartbeat metrics. This
  // includes metrics recorded through |report_heartbeat_metrics_callback|,
  // in addition to latency metrics through |task_tracker| if non-null. In that
  // case, this ServiceThread will assume a registered ThreadPool instance
  // and that |task_tracker| will outlive this ServiceThread.
  explicit ServiceThread(const TaskTracker* task_tracker,
                         RepeatingClosure report_heartbeat_metrics_callback);

  ~ServiceThread() override;

  // Overrides the default interval at which |heartbeat_latency_timer_| fires.
  // Call this with a |heartbeat| of zero to undo the override.
  // Must not be called while the ServiceThread is running.
  static void SetHeartbeatIntervalForTesting(TimeDelta heartbeat);

 private:
  // Thread:
  void Init() override;
  void Run(RunLoop* run_loop) override;

  void ReportHeartbeatMetrics() const;

  // Kicks off a single async task which will record a histogram on the latency
  // of a randomly chosen set of TaskTraits.
  void PerformHeartbeatLatencyReport() const;

  const TaskTracker* const task_tracker_;

  // Fires a recurring heartbeat task to record metrics which are independent
  // from any execution sequence. This is done on the service thread to avoid
  // all external dependencies (even main thread).
  base::RepeatingTimer heartbeat_metrics_timer_;

  RepeatingClosure report_heartbeat_metrics_callback_;

  DISALLOW_COPY_AND_ASSIGN(ServiceThread);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_SERVICE_THREAD_H_
