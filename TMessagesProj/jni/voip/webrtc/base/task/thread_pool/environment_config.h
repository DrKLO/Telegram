// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_ENVIRONMENT_CONFIG_H_
#define BASE_TASK_THREAD_POOL_ENVIRONMENT_CONFIG_H_

#include <stddef.h>

#include "base/base_export.h"
#include "base/task/task_traits.h"
#include "base/threading/thread.h"

namespace base {
namespace internal {

// TODO(etiennep): This is now specific to
// PooledSingleThreadTaskRunnerManager, move it there.
enum EnvironmentType {
  FOREGROUND = 0,
  FOREGROUND_BLOCKING,
  BACKGROUND,
  BACKGROUND_BLOCKING,
  ENVIRONMENT_COUNT  // Always last.
};

// Order must match the EnvironmentType enum.
struct EnvironmentParams {
  // The threads and histograms of this environment will be labeled with
  // the thread pool name concatenated to this.
  const char* name_suffix;

  // Preferred priority for threads in this environment; the actual thread
  // priority depends on shutdown state and platform capabilities.
  ThreadPriority priority_hint;
};

constexpr EnvironmentParams kEnvironmentParams[] = {
    {"Foreground", base::ThreadPriority::NORMAL},
    {"ForegroundBlocking", base::ThreadPriority::NORMAL},
    {"Background", base::ThreadPriority::BACKGROUND},
    {"BackgroundBlocking", base::ThreadPriority::BACKGROUND},
};

// Returns true if this platform supports having WorkerThreads running with a
// background priority.
bool BASE_EXPORT CanUseBackgroundPriorityForWorkerThread();

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_ENVIRONMENT_CONFIG_H_
