// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_TASK_FEATURES_H_
#define BASE_TASK_TASK_FEATURES_H_

#include "base/base_export.h"
#include "base/metrics/field_trial_params.h"
#include "build/build_config.h"

namespace base {

struct Feature;

extern const BASE_EXPORT Feature kAllTasksUserBlocking;

// Under this feature, unused threads in ThreadGroup are only detached
// if the total number of threads in the pool is above the initial capacity.
extern const BASE_EXPORT Feature kNoDetachBelowInitialCapacity;

// Under this feature, workers blocked with MayBlock are replaced immediately
// instead of waiting for a threshold in the foreground thread group.
extern const BASE_EXPORT Feature kMayBlockWithoutDelay;

// Under this feature, best effort capacity is never increased.
// While it's unlikely we'd ship this as-is, this experiment allows us to
// determine whether blocked worker replacement logic on best-effort tasks has
// any impact on guardian metrics.
extern const BASE_EXPORT Feature kFixedMaxBestEffortTasks;

#if defined(OS_WIN) || defined(OS_MACOSX)
#define HAS_NATIVE_THREAD_POOL() 1
#else
#define HAS_NATIVE_THREAD_POOL() 0
#endif

#if HAS_NATIVE_THREAD_POOL()
// Under this feature, ThreadPoolImpl will use a ThreadGroup backed by a
// native thread pool implementation. The Windows Thread Pool API and
// libdispatch are used on Windows and macOS/iOS respectively.
extern const BASE_EXPORT Feature kUseNativeThreadPool;
#endif

// Whether threads in the ThreadPool should be reclaimed after being idle for 5
// minutes, instead of 30 seconds.
extern const BASE_EXPORT Feature kUseFiveMinutesThreadReclaimTime;

}  // namespace base

#endif  // BASE_TASK_TASK_FEATURES_H_
