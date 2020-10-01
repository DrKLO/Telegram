// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/scoped_thread_priority.h"

#include "base/location.h"
#include "base/threading/platform_thread.h"
#include "base/trace_event/trace_event.h"

namespace base {
namespace internal {

ScopedMayLoadLibraryAtBackgroundPriority::
    ScopedMayLoadLibraryAtBackgroundPriority(const Location& from_here) {
  TRACE_EVENT_BEGIN2("base", "ScopedMayLoadLibraryAtBackgroundPriority",
                     "file_name", from_here.file_name(), "function_name",
                     from_here.function_name());
}

bool ScopedMayLoadLibraryAtBackgroundPriority::OnScopeFirstEntered() {
#if defined(OS_WIN)
  const base::ThreadPriority priority =
      PlatformThread::GetCurrentThreadPriority();
  if (priority == base::ThreadPriority::BACKGROUND) {
    original_thread_priority_ = priority;
    PlatformThread::SetCurrentThreadPriority(base::ThreadPriority::NORMAL);

    TRACE_EVENT_BEGIN0(
        "base",
        "ScopedMayLoadLibraryAtBackgroundPriority : Priority Increased");
  }
#endif  // OS_WIN

  return true;
}

ScopedMayLoadLibraryAtBackgroundPriority::
    ~ScopedMayLoadLibraryAtBackgroundPriority() {
  // Trace events must be closed in reverse order of opening so that they nest
  // correctly.
#if defined(OS_WIN)
  if (original_thread_priority_) {
    TRACE_EVENT_END0(
        "base",
        "ScopedMayLoadLibraryAtBackgroundPriority : Priority Increased");
    PlatformThread::SetCurrentThreadPriority(original_thread_priority_.value());
  }
#endif  // OS_WIN
  TRACE_EVENT_END0("base", "ScopedMayLoadLibraryAtBackgroundPriority");
}

}  // namespace internal
}  // namespace base
