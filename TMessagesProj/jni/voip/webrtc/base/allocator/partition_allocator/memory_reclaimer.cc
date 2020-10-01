// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/memory_reclaimer.h"

#include "base/allocator/partition_allocator/partition_alloc.h"
#include "base/bind.h"
#include "base/location.h"
#include "base/metrics/histogram_functions.h"
#include "base/timer/elapsed_timer.h"
#include "base/trace_event/trace_event.h"

namespace base {

constexpr TimeDelta PartitionAllocMemoryReclaimer::kStatsRecordingTimeDelta;

// static
PartitionAllocMemoryReclaimer* PartitionAllocMemoryReclaimer::Instance() {
  static NoDestructor<PartitionAllocMemoryReclaimer> instance;
  return instance.get();
}

void PartitionAllocMemoryReclaimer::RegisterPartition(
    internal::PartitionRootBase* partition) {
  AutoLock lock(lock_);
  DCHECK(partition);
  auto it_and_whether_inserted = partitions_.insert(partition);
  DCHECK(it_and_whether_inserted.second);
}

void PartitionAllocMemoryReclaimer::UnregisterPartition(
    internal::PartitionRootBase* partition) {
  AutoLock lock(lock_);
  DCHECK(partition);
  size_t erased_count = partitions_.erase(partition);
  DCHECK_EQ(1u, erased_count);
}

void PartitionAllocMemoryReclaimer::Start(
    scoped_refptr<SequencedTaskRunner> task_runner) {
  DCHECK(!timer_);
  DCHECK(task_runner);

  {
    AutoLock lock(lock_);
    DCHECK(!partitions_.empty());
  }

  // This does not need to run on the main thread, however there are a few
  // reasons to do it there:
  // - Most of PartitionAlloc's usage is on the main thread, hence PA's metadata
  //   is more likely in cache when executing on the main thread.
  // - Memory reclaim takes the partition lock for each partition. As a
  //   consequence, while reclaim is running, the main thread is unlikely to be
  //   able to make progress, as it would be waiting on the lock.
  // - Finally, this runs in idle time only, so there should be no visible
  //   impact.
  //
  // From local testing, time to reclaim is 100us-1ms, and reclaiming every few
  // seconds is useful. Since this is meant to run during idle time only, it is
  // a reasonable starting point balancing effectivenes vs cost. See
  // crbug.com/942512 for details and experimental results.
  constexpr TimeDelta kInterval = TimeDelta::FromSeconds(4);

  timer_ = std::make_unique<RepeatingTimer>();
  timer_->SetTaskRunner(task_runner);
  // Here and below, |Unretained(this)| is fine as |this| lives forever, as a
  // singleton.
  timer_->Start(
      FROM_HERE, kInterval,
      BindRepeating(&PartitionAllocMemoryReclaimer::Reclaim, Unretained(this)));

  task_runner->PostDelayedTask(
      FROM_HERE,
      BindOnce(&PartitionAllocMemoryReclaimer::RecordStatistics,
               Unretained(this)),
      kStatsRecordingTimeDelta);
}

PartitionAllocMemoryReclaimer::PartitionAllocMemoryReclaimer() = default;
PartitionAllocMemoryReclaimer::~PartitionAllocMemoryReclaimer() = default;

void PartitionAllocMemoryReclaimer::Reclaim() {
  TRACE_EVENT0("base", "PartitionAllocMemoryReclaimer::Reclaim()");
  // Reclaim will almost always call into the kernel, so tail latency of this
  // task would likely be affected by descheduling.
  //
  // On Linux (and Android) at least, ThreadTicks also includes kernel time, so
  // this is a good measure of the true cost of decommit.
  ElapsedThreadTimer timer;
  constexpr int kFlags =
      PartitionPurgeDecommitEmptyPages | PartitionPurgeDiscardUnusedSystemPages;

  {
    AutoLock lock(lock_);  // Has to protect from concurrent (Un)Register calls.
    for (auto* partition : partitions_)
      partition->PurgeMemory(kFlags);
  }

  has_called_reclaim_ = true;
  if (timer.is_supported())
    total_reclaim_thread_time_ += timer.Elapsed();
}

void PartitionAllocMemoryReclaimer::RecordStatistics() {
  if (!ElapsedThreadTimer().is_supported())
    return;
  if (!has_called_reclaim_)
    return;

  UmaHistogramTimes("Memory.PartitionAlloc.MainThreadTime.5min",
                    total_reclaim_thread_time_);
  has_called_reclaim_ = false;
  total_reclaim_thread_time_ = TimeDelta();
}

void PartitionAllocMemoryReclaimer::ResetForTesting() {
  AutoLock lock(lock_);

  has_called_reclaim_ = false;
  total_reclaim_thread_time_ = TimeDelta();
  timer_ = nullptr;
  partitions_.clear();
}

}  // namespace base
