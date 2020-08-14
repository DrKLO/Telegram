// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_NATIVE_UNWINDER_H_
#define BASE_PROFILER_NATIVE_UNWINDER_H_

#include <memory>

namespace base {

class ModuleCache;
class Unwinder;

// Creates the native unwinder for the platform.
std::unique_ptr<Unwinder> CreateNativeUnwinder(ModuleCache* module_cache);

}  // namespace base

#endif  // BASE_PROFILER_NATIVE_UNWINDER_H_
