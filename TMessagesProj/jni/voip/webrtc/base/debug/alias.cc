// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/alias.h"
#include "build/build_config.h"

namespace base {
namespace debug {

// This file/function should be excluded from LTO/LTCG to ensure that the
// compiler can't see this function's implementation when compiling calls to it.
NOINLINE void Alias(const void* var) {}

}  // namespace debug
}  // namespace base
