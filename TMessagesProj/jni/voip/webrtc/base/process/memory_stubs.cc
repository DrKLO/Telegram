// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/memory.h"

#include <stddef.h>
#include <stdlib.h>

namespace base {

void EnableTerminationOnOutOfMemory() {
}

void EnableTerminationOnHeapCorruption() {
}

bool AdjustOOMScore(ProcessId process, int score) {
  return false;
}

void TerminateBecauseOutOfMemory(size_t size) {
  abort();
}

// UncheckedMalloc and Calloc exist so that platforms making use of
// EnableTerminationOnOutOfMemory have a way to allocate memory without
// crashing. This _stubs.cc file is for platforms that do not support
// EnableTerminationOnOutOfMemory (note the empty implementation above). As
// such, these two Unchecked.alloc functions need only trivially pass-through to
// their respective stdlib function since those functions will return null on a
// failure to allocate.

bool UncheckedMalloc(size_t size, void** result) {
  *result = malloc(size);
  return *result != nullptr;
}

bool UncheckedCalloc(size_t num_items, size_t size, void** result) {
  *result = calloc(num_items, size);
  return *result != nullptr;
}

}  // namespace base
