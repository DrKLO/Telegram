// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_sampler.h"

#include <pthread.h>

#include "base/profiler/stack_copier_signal.h"
#include "base/profiler/stack_sampler_impl.h"
#include "base/profiler/thread_delegate_posix.h"
#include "base/profiler/unwinder.h"
#include "base/threading/platform_thread.h"

namespace base {

std::unique_ptr<StackSampler> StackSampler::Create(
    SamplingProfilerThreadToken thread_token,
    ModuleCache* module_cache,
    StackSamplerTestDelegate* test_delegate,
    std::unique_ptr<Unwinder> native_unwinder) {
  return std::make_unique<StackSamplerImpl>(
      std::make_unique<StackCopierSignal>(
          std::make_unique<ThreadDelegatePosix>(thread_token)),
      std::move(native_unwinder), module_cache, test_delegate);
}

size_t StackSampler::GetStackBufferSize() {
  size_t stack_size = PlatformThread::GetDefaultThreadStackSize();

  pthread_attr_t attr;
  if (stack_size == 0 && pthread_attr_init(&attr) == 0) {
    if (pthread_attr_getstacksize(&attr, &stack_size) != 0)
      stack_size = 0;
    pthread_attr_destroy(&attr);
  }

  // 1MB is default thread limit set by Android at art/runtime/thread_pool.h.
  constexpr size_t kDefaultStackLimit = 1 << 20;
  return stack_size > 0 ? stack_size : kDefaultStackLimit;
}

}  // namespace base
