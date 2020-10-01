// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_sampler.h"

#include <pthread.h>

#include "base/threading/platform_thread.h"
#include "build/build_config.h"

namespace base {

std::unique_ptr<StackSampler> StackSampler::Create(
    SamplingProfilerThreadToken thread_token,
    ModuleCache* module_cache,
    StackSamplerTestDelegate* test_delegate,
    std::unique_ptr<Unwinder> native_unwinder) {
  return nullptr;
}

size_t StackSampler::GetStackBufferSize() {
  size_t stack_size = PlatformThread::GetDefaultThreadStackSize();

  pthread_attr_t attr;
  if (stack_size == 0 && pthread_attr_init(&attr) == 0) {
    if (pthread_attr_getstacksize(&attr, &stack_size) != 0)
      stack_size = 0;
    pthread_attr_destroy(&attr);
  }

  // Maximum limits under NPTL implementation.
  constexpr size_t kDefaultStackLimit = 4 * (1 << 20);
  return stack_size > 0 ? stack_size : kDefaultStackLimit;
}

}  // namespace base
