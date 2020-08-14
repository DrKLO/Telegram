// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_NATIVE_UNWINDER_ANDROID_H_
#define BASE_PROFILER_NATIVE_UNWINDER_ANDROID_H_

#include "base/profiler/unwinder.h"

namespace base {

// Native unwinder implementation for Android, using libunwindstack.
//
// TODO(charliea): Implement this class.
// See: https://crbug.com/989102
class NativeUnwinderAndroid : public Unwinder {
 public:
  NativeUnwinderAndroid() = default;

  NativeUnwinderAndroid(const NativeUnwinderAndroid&) = delete;
  NativeUnwinderAndroid& operator=(const NativeUnwinderAndroid&) = delete;

  // Unwinder
  bool CanUnwindFrom(const Frame& current_frame) const override;
  UnwindResult TryUnwind(RegisterContext* thread_context,
                         uintptr_t stack_top,
                         ModuleCache* module_cache,
                         std::vector<Frame>* stack) const override;
};

}  // namespace base

#endif  // BASE_PROFILER_NATIVE_UNWINDER_ANDROID_H_
