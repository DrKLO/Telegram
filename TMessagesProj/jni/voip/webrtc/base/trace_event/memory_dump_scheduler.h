// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_MEMORY_DUMP_SCHEDULER_H
#define BASE_TRACE_EVENT_MEMORY_DUMP_SCHEDULER_H

#include <stdint.h>

#include <vector>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/memory/ref_counted.h"
#include "base/trace_event/memory_dump_request_args.h"

namespace base {
class SequencedTaskRunner;

namespace trace_event {

// Schedules global dump requests based on the triggers added. The methods of
// this class are NOT thread safe and the client has to take care of invoking
// all the methods of the class safely.
class BASE_EXPORT MemoryDumpScheduler {
 public:
  using PeriodicCallback = RepeatingCallback<void(MemoryDumpLevelOfDetail)>;

  // Passed to Start().
  struct BASE_EXPORT Config {
    struct Trigger {
      MemoryDumpLevelOfDetail level_of_detail;
      uint32_t period_ms;
    };

    Config();
    Config(const Config&);
    ~Config();

    std::vector<Trigger> triggers;
    PeriodicCallback callback;
  };

  static MemoryDumpScheduler* GetInstance();

  void Start(Config, scoped_refptr<SequencedTaskRunner> task_runner);
  void Stop();
  bool is_enabled_for_testing() const { return bool(task_runner_); }

 private:
  friend class MemoryDumpSchedulerTest;
  MemoryDumpScheduler();
  ~MemoryDumpScheduler();

  void StartInternal(Config);
  void StopInternal();
  void Tick(uint32_t expected_generation);

  // Accessed only by the public methods (never from the task runner itself).
  scoped_refptr<SequencedTaskRunner> task_runner_;

  // These fields instead are only accessed from within the task runner.
  uint32_t period_ms_;   // 0 == disabled.
  uint32_t generation_;  // Used to invalidate outstanding tasks after Stop().
  uint32_t tick_count_;
  uint32_t light_dump_rate_;
  uint32_t heavy_dump_rate_;
  PeriodicCallback callback_;

  DISALLOW_COPY_AND_ASSIGN(MemoryDumpScheduler);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_MEMORY_DUMP_SCHEDULER_H
