// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_FRAME_H_
#define BASE_PROFILER_FRAME_H_

#include <memory>

#include "base/profiler/module_cache.h"

namespace base {

// Frame represents an individual sampled stack frame with full module
// information.
struct BASE_EXPORT Frame {
  Frame(uintptr_t instruction_pointer, const ModuleCache::Module* module);
  ~Frame();

  // The sampled instruction pointer within the function.
  uintptr_t instruction_pointer;

  // The module information.
  const ModuleCache::Module* module;
};

}  // namespace base

#endif  // BASE_PROFILER_FRAME_H_
