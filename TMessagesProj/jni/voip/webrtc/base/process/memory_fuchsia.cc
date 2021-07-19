// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/memory.h"

#include <stdlib.h>

namespace base {

void EnableTerminationOnOutOfMemory() {
  // Nothing to be done here.
}

void EnableTerminationOnHeapCorruption() {
  // Nothing to be done here.
}

bool UncheckedMalloc(size_t size, void** result) {
  *result = malloc(size);
  return *result != nullptr;
}

}  // namespace base
