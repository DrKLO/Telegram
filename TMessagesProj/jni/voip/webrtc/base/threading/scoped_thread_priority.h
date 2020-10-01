// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_THREADING_SCOPED_THREAD_PRIORITY_H_
#define BASE_THREADING_SCOPED_THREAD_PRIORITY_H_

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/location.h"
#include "base/macros.h"
#include "base/optional.h"
#include "build/build_config.h"

namespace base {

class Location;
enum class ThreadPriority : int;

// INTERNAL_SCOPED_THREAD_PRIORITY_APPEND_LINE(name) produces an identifier by
// appending the current line number to |name|. This is used to avoid name
// collisions from variables defined inside a macro.
#define INTERNAL_SCOPED_THREAD_PRIORITY_CONCAT(a, b) a##b
// CONCAT1 provides extra level of indirection so that __LINE__ macro expands.
#define INTERNAL_SCOPED_THREAD_PRIORITY_CONCAT1(a, b) \
  INTERNAL_SCOPED_THREAD_PRIORITY_CONCAT(a, b)
#define INTERNAL_SCOPED_THREAD_PRIORITY_APPEND_LINE(name) \
  INTERNAL_SCOPED_THREAD_PRIORITY_CONCAT1(name, __LINE__)

// All code that may load a DLL on a background thread must be surrounded by a
// scope that starts with this macro.
//
// Example:
//   Foo();
//   {
//     SCOPED_MAY_LOAD_LIBRARY_AT_BACKGROUND_PRIORITY();
//     LoadMyDll();
//   }
//   Bar();
//
// The macro raises the thread priority to NORMAL for the scope when first
// encountered. On Windows, loading a DLL on a background thread can lead to a
// priority inversion on the loader lock and cause huge janks.
#define SCOPED_MAY_LOAD_LIBRARY_AT_BACKGROUND_PRIORITY()                    \
  base::internal::ScopedMayLoadLibraryAtBackgroundPriority                  \
      INTERNAL_SCOPED_THREAD_PRIORITY_APPEND_LINE(                          \
          scoped_may_load_library_at_background_priority)(FROM_HERE);       \
  {                                                                         \
    /* Thread-safe static local variable initialization ensures that */     \
    /* OnScopeFirstEntered() is only invoked the first time that this is */ \
    /* encountered. */                                                      \
    static bool INTERNAL_SCOPED_THREAD_PRIORITY_APPEND_LINE(invoke_once) =  \
        INTERNAL_SCOPED_THREAD_PRIORITY_APPEND_LINE(                        \
            scoped_may_load_library_at_background_priority)                 \
            .OnScopeFirstEntered();                                         \
    ALLOW_UNUSED_LOCAL(                                                     \
        INTERNAL_SCOPED_THREAD_PRIORITY_APPEND_LINE(invoke_once));          \
  }

namespace internal {

class BASE_EXPORT ScopedMayLoadLibraryAtBackgroundPriority {
 public:
  explicit ScopedMayLoadLibraryAtBackgroundPriority(const Location& from_here);
  ~ScopedMayLoadLibraryAtBackgroundPriority();

  // The SCOPED_MAY_LOAD_LIBRARY_AT_BACKGROUND_PRIORITY() macro invokes this the
  // first time that it is encountered.
  bool OnScopeFirstEntered();

 private:
#if defined(OS_WIN)
  // The original priority when invoking OnScopeFirstEntered().
  base::Optional<ThreadPriority> original_thread_priority_;
#endif

  DISALLOW_COPY_AND_ASSIGN(ScopedMayLoadLibraryAtBackgroundPriority);
};

}  // namespace internal

}  // namespace base

#endif  // BASE_THREADING_SCOPED_THREAD_PRIORITY_H_
