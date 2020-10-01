// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/sequence_sort_key.h"

namespace base {
namespace internal {

SequenceSortKey::SequenceSortKey(TaskPriority priority,
                                 TimeTicks next_task_sequenced_time)
    : priority_(priority),
      next_task_sequenced_time_(next_task_sequenced_time) {}

bool SequenceSortKey::operator<=(const SequenceSortKey& other) const {
  // This SequenceSortKey is considered more important than |other| if it has a
  // higher priority or if it has the same priority but its next task was
  // posted sooner than |other|'s.
  const int priority_diff =
      static_cast<int>(priority_) - static_cast<int>(other.priority_);
  if (priority_diff > 0)
    return true;
  if (priority_diff < 0)
    return false;
  return next_task_sequenced_time_ <= other.next_task_sequenced_time_;
}

}  // namespace internal
}  // namespace base
