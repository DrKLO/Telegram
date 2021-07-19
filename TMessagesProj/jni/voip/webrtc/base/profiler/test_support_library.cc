// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Note: there is intentionally no header file associated with this library so
// we don't risk implicitly demand loading it by accessing a symbol.

#include "build/build_config.h"

#if defined(OS_WIN)
#define BASE_PROFILER_TEST_SUPPORT_LIBRARY_EXPORT __declspec(dllexport)
#else  // defined(OS_WIN)
#define BASE_PROFILER_TEST_SUPPORT_LIBRARY_EXPORT __attribute__((visibility("default")))
#endif

namespace base {

// Must be defined in an extern "C" block so we can look up the unmangled name.
extern "C" {

BASE_PROFILER_TEST_SUPPORT_LIBRARY_EXPORT void InvokeCallbackFunction(
    void (*function)(void*),
    void* arg) {
  function(arg);
  // Prevent tail call.
  volatile int i = 0;
  i = 1;
}

}  // extern "C"

}  // namespace base
