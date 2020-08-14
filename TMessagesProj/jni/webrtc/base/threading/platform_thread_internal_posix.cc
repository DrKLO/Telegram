// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/platform_thread_internal_posix.h"

#include "base/containers/adapters.h"
#include "base/logging.h"

namespace base {

namespace internal {

int ThreadPriorityToNiceValue(ThreadPriority priority) {
  for (const auto& pair : kThreadPriorityToNiceValueMap) {
    if (pair.priority == priority)
      return pair.nice_value;
  }
  NOTREACHED() << "Unknown ThreadPriority";
  return 0;
}

ThreadPriority NiceValueToThreadPriority(int nice_value) {
  // Try to find a priority that best describes |nice_value|. If there isn't
  // an exact match, this method returns the closest priority whose nice value
  // is higher (lower priority) than |nice_value|.
  for (const auto& pair : Reversed(kThreadPriorityToNiceValueMap)) {
    if (pair.nice_value >= nice_value)
      return pair.priority;
  }

  // Reaching here means |nice_value| is more than any of the defined
  // priorities. The lowest priority is suitable in this case.
  return ThreadPriority::BACKGROUND;
}

}  // namespace internal

}  // namespace base
