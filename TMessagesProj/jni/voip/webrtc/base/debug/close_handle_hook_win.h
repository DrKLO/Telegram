// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_DEBUG_CLOSE_HANDLE_HOOK_WIN_H_
#define BASE_DEBUG_CLOSE_HANDLE_HOOK_WIN_H_

#include "base/base_export.h"

namespace base {
namespace debug {

// Installs the hooks required to debug use of improper handles.
BASE_EXPORT void InstallHandleHooks();

}  // namespace debug
}  // namespace base

#endif  // BASE_DEBUG_CLOSE_HANDLE_HOOK_WIN_H_
