// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/native_unwinder_android.h"

#include "base/profiler/module_cache.h"
#include "base/profiler/native_unwinder.h"
#include "base/profiler/profile_builder.h"

namespace base {

bool NativeUnwinderAndroid::CanUnwindFrom(const Frame& current_frame) const {
  return false;
}

UnwindResult NativeUnwinderAndroid::TryUnwind(RegisterContext* thread_context,
                                              uintptr_t stack_top,
                                              ModuleCache* module_cache,
                                              std::vector<Frame>* stack) const {
  return UnwindResult::ABORTED;
}

std::unique_ptr<Unwinder> CreateNativeUnwinder(ModuleCache* module_cache) {
  return std::make_unique<NativeUnwinderAndroid>();
}

}  // namespace base
