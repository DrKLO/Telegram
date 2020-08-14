// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_STACK_SAMPLER_H_
#define BASE_PROFILER_STACK_SAMPLER_H_

#include <memory>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/profiler/sampling_profiler_thread_token.h"
#include "base/threading/platform_thread.h"

namespace base {

class Unwinder;
class ModuleCache;
class ProfileBuilder;
class StackBuffer;
class StackSamplerTestDelegate;

// StackSampler is an implementation detail of StackSamplingProfiler. It
// abstracts the native implementation required to record a set of stack frames
// for a given thread.
class BASE_EXPORT StackSampler {
 public:
  virtual ~StackSampler();

  // Creates a stack sampler that records samples for thread with
  // |thread_token|. Returns null if this platform does not support stack
  // sampling.
  static std::unique_ptr<StackSampler> Create(
      SamplingProfilerThreadToken thread_token,
      ModuleCache* module_cache,
      StackSamplerTestDelegate* test_delegate,
      std::unique_ptr<Unwinder> native_unwinder = nullptr);

  // Gets the required size of the stack buffer.
  static size_t GetStackBufferSize();

  // Creates an instance of the a stack buffer that can be used for calls to
  // any StackSampler object.
  static std::unique_ptr<StackBuffer> CreateStackBuffer();

  // The following functions are all called on the SamplingThread (not the
  // thread being sampled).

  // Adds an auxiliary unwinder to handle additional, non-native-code unwind
  // scenarios.
  virtual void AddAuxUnwinder(std::unique_ptr<Unwinder> unwinder) = 0;

  // Records a set of frames and returns them.
  virtual void RecordStackFrames(StackBuffer* stackbuffer,
                                 ProfileBuilder* profile_builder) = 0;

 protected:
  StackSampler();

 private:
  DISALLOW_COPY_AND_ASSIGN(StackSampler);
};

// StackSamplerTestDelegate provides seams for test code to execute during stack
// collection.
class BASE_EXPORT StackSamplerTestDelegate {
 public:
  virtual ~StackSamplerTestDelegate();

  // Called after copying the stack and resuming the target thread, but prior to
  // walking the stack. Invoked on the SamplingThread.
  virtual void OnPreStackWalk() = 0;

 protected:
  StackSamplerTestDelegate();

 private:
  DISALLOW_COPY_AND_ASSIGN(StackSamplerTestDelegate);
};

}  // namespace base

#endif  // BASE_PROFILER_STACK_SAMPLER_H_
