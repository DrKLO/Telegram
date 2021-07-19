// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_MEMORY_RECLAIMER_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_MEMORY_RECLAIMER_H_

#include <memory>
#include <set>

#include "base/bind.h"
#include "base/callback.h"
#include "base/location.h"
#include "base/no_destructor.h"
#include "base/single_thread_task_runner.h"
#include "base/thread_annotations.h"
#include "base/time/time.h"
#include "base/timer/elapsed_timer.h"
#include "base/timer/timer.h"

namespace base {

namespace internal {

struct PartitionRootBase;

}  // namespace internal

// Posts and handles memory reclaim tasks for PartitionAlloc.
//
// Thread safety: |RegisterPartition()| and |UnregisterPartition()| can be
// called from any thread, concurrently with reclaim. Reclaim itself runs in the
// context of the provided |SequencedTaskRunner|, meaning that the caller must
// take care of this runner being compatible with the various partitions.
//
// Singleton as this runs as long as the process is alive, and
// having multiple instances would be wasteful.
class BASE_EXPORT PartitionAllocMemoryReclaimer {
 public:
  static PartitionAllocMemoryReclaimer* Instance();

  // Internal. Do not use.
  // Registers a partition to be tracked by the reclaimer.
  void RegisterPartition(internal::PartitionRootBase* partition);
  // Internal. Do not use.
  // Unregisters a partition to be tracked by the reclaimer.
  void UnregisterPartition(internal::PartitionRootBase* partition);
  // Starts the periodic reclaim. Should be called once.
  void Start(scoped_refptr<SequencedTaskRunner> task_runner);
  // Triggers an explicit reclaim now.
  void Reclaim();

  static constexpr TimeDelta kStatsRecordingTimeDelta =
      TimeDelta::FromMinutes(5);

 private:
  PartitionAllocMemoryReclaimer();
  ~PartitionAllocMemoryReclaimer();
  void ReclaimAndReschedule();
  void RecordStatistics();
  void ResetForTesting();

  // Total time spent in |Reclaim()|.
  bool has_called_reclaim_ = false;
  TimeDelta total_reclaim_thread_time_;
  // Schedules periodic |Reclaim()|.
  std::unique_ptr<RepeatingTimer> timer_;

  Lock lock_;
  std::set<internal::PartitionRootBase*> partitions_ GUARDED_BY(lock_);

  friend class NoDestructor<PartitionAllocMemoryReclaimer>;
  friend class PartitionAllocMemoryReclaimerTest;
  DISALLOW_COPY_AND_ASSIGN(PartitionAllocMemoryReclaimer);
};

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_MEMORY_RECLAIMER_H_
