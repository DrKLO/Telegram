// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/scoped_clear_last_error.h"

#include <windows.h>

namespace base {
namespace internal {

ScopedClearLastError::ScopedClearLastError()
    : last_system_error_(::GetLastError()) {
  ::SetLastError(0);
}

ScopedClearLastError::~ScopedClearLastError() {
  ::SetLastError(last_system_error_);
}

}  // namespace internal
}  // namespace base
