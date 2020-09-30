// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/sampling_profiler_thread_token.h"

namespace base {

SamplingProfilerThreadToken GetSamplingProfilerCurrentThreadToken() {
#if defined(OS_ANDROID) || defined(OS_LINUX)
  return {PlatformThread::CurrentId(), pthread_self()};
#else
  return {PlatformThread::CurrentId()};
#endif
}

}  // namespace base
