// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/dump_without_crashing.h"

#include "base/logging.h"

namespace {

// Pointer to the function that's called by DumpWithoutCrashing() to dump the
// process's memory.
void(CDECL* dump_without_crashing_function_)() = nullptr;

}  // namespace

namespace base {

namespace debug {

bool DumpWithoutCrashing() {
  if (dump_without_crashing_function_) {
    (*dump_without_crashing_function_)();
    return true;
  }
  return false;
}

void SetDumpWithoutCrashingFunction(void (CDECL *function)()) {
#if !defined(COMPONENT_BUILD)
  // In component builds, the same base is shared between modules
  // so might be initialized several times. However in non-
  // component builds this should never happen.
  DCHECK(!dump_without_crashing_function_);
#endif
  dump_without_crashing_function_ = function;
}

}  // namespace debug

}  // namespace base
