// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_INITIALIZATION_UTIL_H_
#define BASE_TASK_THREAD_POOL_INITIALIZATION_UTIL_H_

#include "base/base_export.h"

namespace base {

// Computes a value that may be used as the maximum number of threads in a
// ThreadGroup. Developers may use other methods to choose this maximum.
BASE_EXPORT int RecommendedMaxNumberOfThreadsInThreadGroup(
    int min,
    int max,
    double cores_multiplier,
    int offset);

}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_INITIALIZATION_UTIL_H_
