// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_REACHED_CODE_PROFILER_H_
#define BASE_ANDROID_REACHED_CODE_PROFILER_H_

#include "base/android/library_loader/library_loader_hooks.h"
#include "base/base_export.h"

namespace base {
namespace android {

// Initializes and starts the reached code profiler for |library_process_type|.
// Reached symbols are not recorded before calling this function, so it has to
// be called as early in startup as possible. This has to be called before the
// process creates any thread.
// TODO(crbug.com/916263): Currently, the reached code profiler must be
// initialized before the tracing profiler. If we want to start it at later
// point, we need to check that the tracing profiler isn't initialized first.
BASE_EXPORT void InitReachedCodeProfilerAtStartup(
    LibraryProcessType library_process_type);

// Returns whether the reached code profiler is enabled.
BASE_EXPORT bool IsReachedCodeProfilerEnabled();

// Returns whether the reached code profiler can be possibly enabled for the
// current build configuration.
BASE_EXPORT bool IsReachedCodeProfilerSupported();

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_REACHED_CODE_PROFILER_H_
