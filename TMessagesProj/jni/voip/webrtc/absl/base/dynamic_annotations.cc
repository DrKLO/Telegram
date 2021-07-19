// Copyright 2017 The Abseil Authors.
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

#include <stdlib.h>
#include <string.h>

#include "absl/base/dynamic_annotations.h"

#ifndef __has_feature
#define __has_feature(x) 0
#endif

/* Compiler-based ThreadSanitizer defines
   ABSL_DYNAMIC_ANNOTATIONS_EXTERNAL_IMPL = 1
   and provides its own definitions of the functions. */

#ifndef ABSL_DYNAMIC_ANNOTATIONS_EXTERNAL_IMPL
# define ABSL_DYNAMIC_ANNOTATIONS_EXTERNAL_IMPL 0
#endif

/* Each function is empty and called (via a macro) only in debug mode.
   The arguments are captured by dynamic tools at runtime. */

#if ABSL_DYNAMIC_ANNOTATIONS_EXTERNAL_IMPL == 0 && !defined(__native_client__)

#if __has_feature(memory_sanitizer)
#include <sanitizer/msan_interface.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

void AbslAnnotateRWLockCreate(const char *, int,
                          const volatile void *){}
void AbslAnnotateRWLockDestroy(const char *, int,
                           const volatile void *){}
void AbslAnnotateRWLockAcquired(const char *, int,
                            const volatile void *, long){}
void AbslAnnotateRWLockReleased(const char *, int,
                            const volatile void *, long){}
void AbslAnnotateBenignRace(const char *, int,
                        const volatile void *,
                        const char *){}
void AbslAnnotateBenignRaceSized(const char *, int,
                             const volatile void *,
                             size_t,
                             const char *) {}
void AbslAnnotateThreadName(const char *, int,
                        const char *){}
void AbslAnnotateIgnoreReadsBegin(const char *, int){}
void AbslAnnotateIgnoreReadsEnd(const char *, int){}
void AbslAnnotateIgnoreWritesBegin(const char *, int){}
void AbslAnnotateIgnoreWritesEnd(const char *, int){}
void AbslAnnotateEnableRaceDetection(const char *, int, int){}
void AbslAnnotateMemoryIsInitialized(const char *, int,
                                 const volatile void *mem, size_t size) {
#if __has_feature(memory_sanitizer)
  __msan_unpoison(mem, size);
#else
  (void)mem;
  (void)size;
#endif
}

void AbslAnnotateMemoryIsUninitialized(const char *, int,
                                   const volatile void *mem, size_t size) {
#if __has_feature(memory_sanitizer)
  __msan_allocated_memory(mem, size);
#else
  (void)mem;
  (void)size;
#endif
}

static int AbslGetRunningOnValgrind(void) {
#ifdef RUNNING_ON_VALGRIND
  if (RUNNING_ON_VALGRIND) return 1;
#endif
  char *running_on_valgrind_str = getenv("RUNNING_ON_VALGRIND");
  if (running_on_valgrind_str) {
    return strcmp(running_on_valgrind_str, "0") != 0;
  }
  return 0;
}

/* See the comments in dynamic_annotations.h */
int AbslRunningOnValgrind(void) {
  static volatile int running_on_valgrind = -1;
  int local_running_on_valgrind = running_on_valgrind;
  /* C doesn't have thread-safe initialization of statics, and we
     don't want to depend on pthread_once here, so hack it. */
  ABSL_ANNOTATE_BENIGN_RACE(&running_on_valgrind, "safe hack");
  if (local_running_on_valgrind == -1)
    running_on_valgrind = local_running_on_valgrind = AbslGetRunningOnValgrind();
  return local_running_on_valgrind;
}

/* See the comments in dynamic_annotations.h */
double AbslValgrindSlowdown(void) {
  /* Same initialization hack as in AbslRunningOnValgrind(). */
  static volatile double slowdown = 0.0;
  double local_slowdown = slowdown;
  ABSL_ANNOTATE_BENIGN_RACE(&slowdown, "safe hack");
  if (AbslRunningOnValgrind() == 0) {
    return 1.0;
  }
  if (local_slowdown == 0.0) {
    char *env = getenv("VALGRIND_SLOWDOWN");
    slowdown = local_slowdown = env ? atof(env) : 50.0;
  }
  return local_slowdown;
}

#ifdef __cplusplus
}  // extern "C"
#endif
#endif  /* ABSL_DYNAMIC_ANNOTATIONS_EXTERNAL_IMPL == 0 */
