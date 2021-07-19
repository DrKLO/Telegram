// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_STACK_SAMPLER_IMPL_H_
#define BASE_PROFILER_STACK_SAMPLER_IMPL_H_

#include <memory>

#include "base/base_export.h"
#include "base/profiler/frame.h"
#include "base/profiler/register_context.h"
#include "base/profiler/stack_copier.h"
#include "base/profiler/stack_sampler.h"

namespace base {

class Unwinder;

// Cross-platform stack sampler implementation. Delegates to StackCopier for the
// platform-specific stack copying implementation.
class BASE_EXPORT StackSamplerImpl : public StackSampler {
 public:
  StackSamplerImpl(std::unique_ptr<StackCopier> stack_copier,
                   std::unique_ptr<Unwinder> native_unwinder,
                   ModuleCache* module_cache,
                   StackSamplerTestDelegate* test_delegate = nullptr);
  ~StackSamplerImpl() override;

  StackSamplerImpl(const StackSamplerImpl&) = delete;
  StackSamplerImpl& operator=(const StackSamplerImpl&) = delete;

  // StackSampler:
  void AddAuxUnwinder(std::unique_ptr<Unwinder> unwinder) override;
  void RecordStackFrames(StackBuffer* stack_buffer,
                         ProfileBuilder* profile_builder) override;

  // Exposes the internal function for unit testing.
  static std::vector<Frame> WalkStackForTesting(ModuleCache* module_cache,
                                                RegisterContext* thread_context,
                                                uintptr_t stack_top,
                                                Unwinder* native_unwinder,
                                                Unwinder* aux_unwinder);

 private:
  static std::vector<Frame> WalkStack(ModuleCache* module_cache,
                                      RegisterContext* thread_context,
                                      uintptr_t stack_top,
                                      Unwinder* native_unwinder,
                                      Unwinder* aux_unwinder);

  const std::unique_ptr<StackCopier> stack_copier_;
  const std::unique_ptr<Unwinder> native_unwinder_;
  std::unique_ptr<Unwinder> aux_unwinder_;
  ModuleCache* const module_cache_;
  StackSamplerTestDelegate* const test_delegate_;
};

}  // namespace base

#endif  // BASE_PROFILER_STACK_SAMPLER_IMPL_H_
