// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/memory_dump_scheduler.h"

#include <algorithm>
#include <limits>

#include "base/bind.h"
#include "base/logging.h"
#include "base/threading/sequenced_task_runner_handle.h"

namespace base {
namespace trace_event {

// static
MemoryDumpScheduler* MemoryDumpScheduler::GetInstance() {
  static MemoryDumpScheduler* instance = new MemoryDumpScheduler();
  return instance;
}

MemoryDumpScheduler::MemoryDumpScheduler() : period_ms_(0), generation_(0) {}
MemoryDumpScheduler::~MemoryDumpScheduler() {
  // Hit only in tests. Check that tests don't leave without stopping.
  DCHECK(!is_enabled_for_testing());
}

void MemoryDumpScheduler::Start(
    MemoryDumpScheduler::Config config,
    scoped_refptr<SequencedTaskRunner> task_runner) {
  DCHECK(!task_runner_);
  task_runner_ = task_runner;
  task_runner->PostTask(FROM_HERE, BindOnce(&MemoryDumpScheduler::StartInternal,
                                            Unretained(this), config));
}

void MemoryDumpScheduler::Stop() {
  if (!task_runner_)
    return;
  task_runner_->PostTask(FROM_HERE, BindOnce(&MemoryDumpScheduler::StopInternal,
                                             Unretained(this)));
  task_runner_ = nullptr;
}

void MemoryDumpScheduler::StartInternal(MemoryDumpScheduler::Config config) {
  uint32_t light_dump_period_ms = 0;
  uint32_t heavy_dump_period_ms = 0;
  uint32_t min_period_ms = std::numeric_limits<uint32_t>::max();
  for (const Config::Trigger& trigger : config.triggers) {
    DCHECK_GT(trigger.period_ms, 0u);
    switch (trigger.level_of_detail) {
      case MemoryDumpLevelOfDetail::BACKGROUND:
        break;
      case MemoryDumpLevelOfDetail::LIGHT:
        DCHECK_EQ(0u, light_dump_period_ms);
        light_dump_period_ms = trigger.period_ms;
        break;
      case MemoryDumpLevelOfDetail::DETAILED:
        DCHECK_EQ(0u, heavy_dump_period_ms);
        heavy_dump_period_ms = trigger.period_ms;
        break;
    }
    min_period_ms = std::min(min_period_ms, trigger.period_ms);
  }

  DCHECK_EQ(0u, light_dump_period_ms % min_period_ms);
  DCHECK_EQ(0u, heavy_dump_period_ms % min_period_ms);
  DCHECK(!config.callback.is_null());
  callback_ = config.callback;
  period_ms_ = min_period_ms;
  tick_count_ = 0;
  light_dump_rate_ = light_dump_period_ms / min_period_ms;
  heavy_dump_rate_ = heavy_dump_period_ms / min_period_ms;

  // Trigger the first dump after 200ms.
  // TODO(lalitm): this is a tempoarary hack to delay the first scheduled dump
  // so that the child processes get tracing enabled notification via IPC.
  // See crbug.com/770151.
  SequencedTaskRunnerHandle::Get()->PostDelayedTask(
      FROM_HERE,
      BindOnce(&MemoryDumpScheduler::Tick, Unretained(this), ++generation_),
      TimeDelta::FromMilliseconds(200));
}

void MemoryDumpScheduler::StopInternal() {
  period_ms_ = 0;
  generation_++;
  callback_.Reset();
}

void MemoryDumpScheduler::Tick(uint32_t expected_generation) {
  if (period_ms_ == 0 || generation_ != expected_generation)
    return;

  MemoryDumpLevelOfDetail level_of_detail = MemoryDumpLevelOfDetail::BACKGROUND;
  if (light_dump_rate_ > 0 && tick_count_ % light_dump_rate_ == 0)
    level_of_detail = MemoryDumpLevelOfDetail::LIGHT;
  if (heavy_dump_rate_ > 0 && tick_count_ % heavy_dump_rate_ == 0)
    level_of_detail = MemoryDumpLevelOfDetail::DETAILED;
  tick_count_++;

  callback_.Run(level_of_detail);

  SequencedTaskRunnerHandle::Get()->PostDelayedTask(
      FROM_HERE,
      BindOnce(&MemoryDumpScheduler::Tick, Unretained(this),
               expected_generation),
      TimeDelta::FromMilliseconds(period_ms_));
}

MemoryDumpScheduler::Config::Config() = default;
MemoryDumpScheduler::Config::~Config() = default;
MemoryDumpScheduler::Config::Config(const MemoryDumpScheduler::Config&) =
    default;

}  // namespace trace_event
}  // namespace base
