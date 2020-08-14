//
//  Copyright 2019 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ABSL_FLAGS_CONFIG_H_
#define ABSL_FLAGS_CONFIG_H_

// Determine if we should strip string literals from the Flag objects.
// By default we strip string literals on mobile platforms.
#if !defined(ABSL_FLAGS_STRIP_NAMES)

#if defined(__ANDROID__)
#define ABSL_FLAGS_STRIP_NAMES 1

#elif defined(__APPLE__)
#include <TargetConditionals.h>
#if defined(TARGET_OS_IPHONE) && TARGET_OS_IPHONE
#define ABSL_FLAGS_STRIP_NAMES 1
#elif defined(TARGET_OS_EMBEDDED) && TARGET_OS_EMBEDDED
#define ABSL_FLAGS_STRIP_NAMES 1
#endif  // TARGET_OS_*
#endif

#endif  // !defined(ABSL_FLAGS_STRIP_NAMES)

#if !defined(ABSL_FLAGS_STRIP_NAMES)
// If ABSL_FLAGS_STRIP_NAMES wasn't set on the command line or above,
// the default is not to strip.
#define ABSL_FLAGS_STRIP_NAMES 0
#endif

#if !defined(ABSL_FLAGS_STRIP_HELP)
// By default, if we strip names, we also strip help.
#define ABSL_FLAGS_STRIP_HELP ABSL_FLAGS_STRIP_NAMES
#endif

// ABSL_FLAGS_INTERNAL_ATOMIC_DOUBLE_WORD macro is used for using atomics with
// double words, e.g. absl::Duration.
// For reasons in bug https://gcc.gnu.org/bugzilla/show_bug.cgi?id=80878, modern
// versions of GCC do not support cmpxchg16b instruction in standard atomics.
#ifdef ABSL_FLAGS_INTERNAL_ATOMIC_DOUBLE_WORD
#error "ABSL_FLAGS_INTERNAL_ATOMIC_DOUBLE_WORD should not be defined."
#elif defined(__clang__) && defined(__x86_64__) && \
    defined(__GCC_HAVE_SYNC_COMPARE_AND_SWAP_16)
#define ABSL_FLAGS_INTERNAL_ATOMIC_DOUBLE_WORD 1
#endif

// ABSL_FLAGS_INTERNAL_HAS_RTTI macro is used for selecting if we can use RTTI
// for flag type identification.
#ifdef ABSL_FLAGS_INTERNAL_HAS_RTTI
#error ABSL_FLAGS_INTERNAL_HAS_RTTI cannot be directly set
#elif !defined(__GNUC__) || defined(__GXX_RTTI)
#define ABSL_FLAGS_INTERNAL_HAS_RTTI 1
#endif  // !defined(__GNUC__) || defined(__GXX_RTTI)

#endif  // ABSL_FLAGS_CONFIG_H_
