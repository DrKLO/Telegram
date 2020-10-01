// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_SAMPLING_PROFILER_THREAD_TOKEN_H_
#define BASE_PROFILER_SAMPLING_PROFILER_THREAD_TOKEN_H_

#include "base/base_export.h"
#include "base/threading/platform_thread.h"
#include "build/build_config.h"

#if defined(OS_ANDROID) || defined(OS_LINUX)
#include <pthread.h>
#endif

namespace base {

// SamplingProfilerThreadToken represents the thread identifier(s) required by
// sampling profiler to operate on a thread. PlatformThreadId is needed for all
// platforms, while non-Mac POSIX also requires a pthread_t to pass to pthread
// functions used to obtain the stack base address.
struct SamplingProfilerThreadToken {
  PlatformThreadId id;
#if defined(OS_ANDROID) || defined(OS_LINUX)
  pthread_t pthread_id;
#endif
};

BASE_EXPORT SamplingProfilerThreadToken GetSamplingProfilerCurrentThreadToken();

}  // namespace base

#endif  // BASE_PROFILER_SAMPLING_PROFILER_THREAD_TOKEN_H_
