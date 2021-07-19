// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/reached_code_profiler.h"

namespace base {
namespace android {

void InitReachedCodeProfilerAtStartup(LibraryProcessType library_process_type) {
}

bool IsReachedCodeProfilerEnabled() {
  return false;
}

bool IsReachedCodeProfilerSupported() {
  return false;
}

}  // namespace android
}  // namespace base
