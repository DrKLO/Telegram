/*
 * Copyright 2017 The Abseil Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.

 * Defines ABSL_STACKTRACE_INL_HEADER to the *-inl.h containing
 * actual unwinder implementation.
 * This header is "private" to stacktrace.cc.
 * DO NOT include it into any other files.
*/
#ifndef ABSL_DEBUGGING_INTERNAL_STACKTRACE_CONFIG_H_
#define ABSL_DEBUGGING_INTERNAL_STACKTRACE_CONFIG_H_

#if defined(ABSL_STACKTRACE_INL_HEADER)
#error ABSL_STACKTRACE_INL_HEADER cannot be directly set

#elif defined(_WIN32)
#define ABSL_STACKTRACE_INL_HEADER \
    "absl/debugging/internal/stacktrace_win32-inl.inc"

#elif defined(__linux__) && !defined(__ANDROID__)

#if !defined(NO_FRAME_POINTER)
# if defined(__i386__) || defined(__x86_64__)
#define ABSL_STACKTRACE_INL_HEADER \
    "absl/debugging/internal/stacktrace_x86-inl.inc"
# elif defined(__ppc__) || defined(__PPC__)
#define ABSL_STACKTRACE_INL_HEADER \
    "absl/debugging/internal/stacktrace_powerpc-inl.inc"
# elif defined(__aarch64__)
#define ABSL_STACKTRACE_INL_HEADER \
    "absl/debugging/internal/stacktrace_aarch64-inl.inc"
# elif defined(__arm__)
// Note: When using glibc this may require -funwind-tables to function properly.
#define ABSL_STACKTRACE_INL_HEADER \
  "absl/debugging/internal/stacktrace_generic-inl.inc"
# else
#define ABSL_STACKTRACE_INL_HEADER \
   "absl/debugging/internal/stacktrace_unimplemented-inl.inc"
# endif
#else  // defined(NO_FRAME_POINTER)
# if defined(__i386__) || defined(__x86_64__) || defined(__aarch64__)
#define ABSL_STACKTRACE_INL_HEADER \
    "absl/debugging/internal/stacktrace_generic-inl.inc"
# elif defined(__ppc__) || defined(__PPC__)
#define ABSL_STACKTRACE_INL_HEADER \
    "absl/debugging/internal/stacktrace_generic-inl.inc"
# else
#define ABSL_STACKTRACE_INL_HEADER \
   "absl/debugging/internal/stacktrace_unimplemented-inl.inc"
# endif
#endif  // NO_FRAME_POINTER

#else
#define ABSL_STACKTRACE_INL_HEADER \
  "absl/debugging/internal/stacktrace_unimplemented-inl.inc"

#endif

#endif  // ABSL_DEBUGGING_INTERNAL_STACKTRACE_CONFIG_H_
