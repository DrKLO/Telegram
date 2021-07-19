// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/sequence_manager.h"

namespace base {
namespace sequence_manager {

NativeWorkHandle::~NativeWorkHandle() = default;

SequenceManager::MetricRecordingSettings::MetricRecordingSettings(
    double task_thread_time_sampling_rate)
    : task_sampling_rate_for_recording_cpu_time(
          base::ThreadTicks::IsSupported() ? task_thread_time_sampling_rate
                                           : 0) {}

SequenceManager::Settings::Settings() = default;

SequenceManager::Settings::Settings(Settings&& move_from) noexcept = default;

SequenceManager::Settings::Builder::Builder() = default;

SequenceManager::Settings::Builder::~Builder() = default;

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetMessagePumpType(
    MessagePumpType message_loop_type_val) {
  settings_.message_loop_type = message_loop_type_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetRandomisedSamplingEnabled(
    bool randomised_sampling_enabled_val) {
  settings_.randomised_sampling_enabled = randomised_sampling_enabled_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetTickClock(const TickClock* clock_val) {
  settings_.clock = clock_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetAddQueueTimeToTasks(
    bool add_queue_time_to_tasks_val) {
  settings_.add_queue_time_to_tasks = add_queue_time_to_tasks_val;
  return *this;
}

#if DCHECK_IS_ON()

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetTaskLogging(
    TaskLogging task_execution_logging_val) {
  settings_.task_execution_logging = task_execution_logging_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetLogPostTask(bool log_post_task_val) {
  settings_.log_post_task = log_post_task_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetLogTaskDelayExpiry(
    bool log_task_delay_expiry_val) {
  settings_.log_task_delay_expiry = log_task_delay_expiry_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetLogRunloopQuitAndQuitWhenIdle(
    bool log_runloop_quit_and_quit_when_idle_val) {
  settings_.log_runloop_quit_and_quit_when_idle =
      log_runloop_quit_and_quit_when_idle_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetPerPriorityCrossThreadTaskDelay(
    std::array<TimeDelta, TaskQueue::kQueuePriorityCount>
        per_priority_cross_thread_task_delay_val) {
  settings_.per_priority_cross_thread_task_delay =
      per_priority_cross_thread_task_delay_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetPerPrioritySameThreadTaskDelay(
    std::array<TimeDelta, TaskQueue::kQueuePriorityCount>
        per_priority_same_thread_task_delay_val) {
  settings_.per_priority_same_thread_task_delay =
      per_priority_same_thread_task_delay_val;
  return *this;
}

SequenceManager::Settings::Builder&
SequenceManager::Settings::Builder::SetRandomTaskSelectionSeed(
    int random_task_selection_seed_val) {
  settings_.random_task_selection_seed = random_task_selection_seed_val;
  return *this;
}

#endif  // DCHECK_IS_ON()

SequenceManager::Settings SequenceManager::Settings::Builder::Build() {
  return std::move(settings_);
}

}  // namespace sequence_manager
}  // namespace base
