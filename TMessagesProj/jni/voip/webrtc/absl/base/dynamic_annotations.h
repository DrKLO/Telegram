/*
 *  Copyright 2017 The Abseil Authors.
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
 */
/* This file defines dynamic annotations for use with dynamic analysis
   tool such as valgrind, PIN, etc.

   Dynamic annotation is a source code annotation that affects
   the generated code (that is, the annotation is not a comment).
   Each such annotation is attached to a particular
   instruction and/or to a particular object (address) in the program.

   The annotations that should be used by users are macros in all upper-case
   (e.g., ABSL_ANNOTATE_THREAD_NAME).

   Actual implementation of these macros may differ depending on the
   dynamic analysis tool being used.

   This file supports the following configurations:
   - Dynamic Annotations enabled (with static thread-safety warnings disabled).
     In this case, macros expand to functions implemented by Thread Sanitizer,
     when building with TSan. When not provided an external implementation,
     dynamic_annotations.cc provides no-op implementations.

   - Static Clang thread-safety warnings enabled.
     When building with a Clang compiler that supports thread-safety warnings,
     a subset of annotations can be statically-checked at compile-time. We
     expand these macros to static-inline functions that can be analyzed for
     thread-safety, but afterwards elided when building the final binary.

   - All annotations are disabled.
     If neither Dynamic Annotations nor Clang thread-safety warnings are
     enabled, then all annotation-macros expand to empty. */

#ifndef ABSL_BASE_DYNAMIC_ANNOTATIONS_H_
#define ABSL_BASE_DYNAMIC_ANNOTATIONS_H_

#ifndef ABSL_DYNAMIC_ANNOTATIONS_ENABLED
# define ABSL_DYNAMIC_ANNOTATIONS_ENABLED 0
#endif

#if ABSL_DYNAMIC_ANNOTATIONS_ENABLED != 0

  /* -------------------------------------------------------------
     Annotations that suppress errors.  It is usually better to express the
     program's synchronization using the other annotations, but these can
     be used when all else fails. */

  /* Report that we may have a benign race at "pointer", with size
     "sizeof(*(pointer))". "pointer" must be a non-void* pointer.  Insert at the
     point where "pointer" has been allocated, preferably close to the point
     where the race happens.  See also ABSL_ANNOTATE_BENIGN_RACE_STATIC. */
  #define ABSL_ANNOTATE_BENIGN_RACE(pointer, description) \
    AbslAnnotateBenignRaceSized(__FILE__, __LINE__, pointer, \
                            sizeof(*(pointer)), description)

  /* Same as ABSL_ANNOTATE_BENIGN_RACE(address, description), but applies to
     the memory range [address, address+size). */
  #define ABSL_ANNOTATE_BENIGN_RACE_SIZED(address, size, description) \
    AbslAnnotateBenignRaceSized(__FILE__, __LINE__, address, size, description)

  /* Enable (enable!=0) or disable (enable==0) race detection for all threads.
     This annotation could be useful if you want to skip expensive race analysis
     during some period of program execution, e.g. during initialization. */
  #define ABSL_ANNOTATE_ENABLE_RACE_DETECTION(enable) \
    AbslAnnotateEnableRaceDetection(__FILE__, __LINE__, enable)

  /* -------------------------------------------------------------
     Annotations useful for debugging. */

  /* Report the current thread name to a race detector. */
  #define ABSL_ANNOTATE_THREAD_NAME(name) \
    AbslAnnotateThreadName(__FILE__, __LINE__, name)

  /* -------------------------------------------------------------
     Annotations useful when implementing locks.  They are not
     normally needed by modules that merely use locks.
     The "lock" argument is a pointer to the lock object. */

  /* Report that a lock has been created at address "lock". */
  #define ABSL_ANNOTATE_RWLOCK_CREATE(lock) \
    AbslAnnotateRWLockCreate(__FILE__, __LINE__, lock)

  /* Report that a linker initialized lock has been created at address "lock".
   */
#ifdef THREAD_SANITIZER
  #define ABSL_ANNOTATE_RWLOCK_CREATE_STATIC(lock) \
    AbslAnnotateRWLockCreateStatic(__FILE__, __LINE__, lock)
#else
  #define ABSL_ANNOTATE_RWLOCK_CREATE_STATIC(lock) ABSL_ANNOTATE_RWLOCK_CREATE(lock)
#endif

  /* Report that the lock at address "lock" is about to be destroyed. */
  #define ABSL_ANNOTATE_RWLOCK_DESTROY(lock) \
    AbslAnnotateRWLockDestroy(__FILE__, __LINE__, lock)

  /* Report that the lock at address "lock" has been acquired.
     is_w=1 for writer lock, is_w=0 for reader lock. */
  #define ABSL_ANNOTATE_RWLOCK_ACQUIRED(lock, is_w) \
    AbslAnnotateRWLockAcquired(__FILE__, __LINE__, lock, is_w)

  /* Report that the lock at address "lock" is about to be released. */
  #define ABSL_ANNOTATE_RWLOCK_RELEASED(lock, is_w) \
    AbslAnnotateRWLockReleased(__FILE__, __LINE__, lock, is_w)

#else  /* ABSL_DYNAMIC_ANNOTATIONS_ENABLED == 0 */

  #define ABSL_ANNOTATE_RWLOCK_CREATE(lock) /* empty */
  #define ABSL_ANNOTATE_RWLOCK_CREATE_STATIC(lock) /* empty */
  #define ABSL_ANNOTATE_RWLOCK_DESTROY(lock) /* empty */
  #define ABSL_ANNOTATE_RWLOCK_ACQUIRED(lock, is_w) /* empty */
  #define ABSL_ANNOTATE_RWLOCK_RELEASED(lock, is_w) /* empty */
  #define ABSL_ANNOTATE_BENIGN_RACE(address, description) /* empty */
  #define ABSL_ANNOTATE_BENIGN_RACE_SIZED(address, size, description) /* empty */
  #define ABSL_ANNOTATE_THREAD_NAME(name) /* empty */
  #define ABSL_ANNOTATE_ENABLE_RACE_DETECTION(enable) /* empty */

#endif  /* ABSL_DYNAMIC_ANNOTATIONS_ENABLED */

/* These annotations are also made available to LLVM's Memory Sanitizer */
#if ABSL_DYNAMIC_ANNOTATIONS_ENABLED == 1 || defined(MEMORY_SANITIZER)
  #define ABSL_ANNOTATE_MEMORY_IS_INITIALIZED(address, size) \
    AbslAnnotateMemoryIsInitialized(__FILE__, __LINE__, address, size)

  #define ABSL_ANNOTATE_MEMORY_IS_UNINITIALIZED(address, size) \
    AbslAnnotateMemoryIsUninitialized(__FILE__, __LINE__, address, size)
#else
  #define ABSL_ANNOTATE_MEMORY_IS_INITIALIZED(address, size) /* empty */
  #define ABSL_ANNOTATE_MEMORY_IS_UNINITIALIZED(address, size) /* empty */
#endif  /* ABSL_DYNAMIC_ANNOTATIONS_ENABLED || MEMORY_SANITIZER */

/* TODO(delesley) -- Replace __CLANG_SUPPORT_DYN_ANNOTATION__ with the
   appropriate feature ID. */
#if defined(__clang__) && (!defined(SWIG)) \
    && defined(__CLANG_SUPPORT_DYN_ANNOTATION__)

  #if ABSL_DYNAMIC_ANNOTATIONS_ENABLED == 0
    #define ABSL_ANNOTALYSIS_ENABLED
  #endif

  /* When running in opt-mode, GCC will issue a warning, if these attributes are
     compiled. Only include them when compiling using Clang. */
  #define ABSL_ATTRIBUTE_IGNORE_READS_BEGIN \
      __attribute((exclusive_lock_function("*")))
  #define ABSL_ATTRIBUTE_IGNORE_READS_END \
      __attribute((unlock_function("*")))
#else
  #define ABSL_ATTRIBUTE_IGNORE_READS_BEGIN  /* empty */
  #define ABSL_ATTRIBUTE_IGNORE_READS_END  /* empty */
#endif  /* defined(__clang__) && ... */

#if (ABSL_DYNAMIC_ANNOTATIONS_ENABLED != 0) || defined(ABSL_ANNOTALYSIS_ENABLED)
  #define ABSL_ANNOTATIONS_ENABLED
#endif

#if (ABSL_DYNAMIC_ANNOTATIONS_ENABLED != 0)

  /* Request the analysis tool to ignore all reads in the current thread
     until ABSL_ANNOTATE_IGNORE_READS_END is called.
     Useful to ignore intentional racey reads, while still checking
     other reads and all writes.
     See also ABSL_ANNOTATE_UNPROTECTED_READ. */
  #define ABSL_ANNOTATE_IGNORE_READS_BEGIN() \
    AbslAnnotateIgnoreReadsBegin(__FILE__, __LINE__)

  /* Stop ignoring reads. */
  #define ABSL_ANNOTATE_IGNORE_READS_END() \
    AbslAnnotateIgnoreReadsEnd(__FILE__, __LINE__)

  /* Similar to ABSL_ANNOTATE_IGNORE_READS_BEGIN, but ignore writes instead. */
  #define ABSL_ANNOTATE_IGNORE_WRITES_BEGIN() \
    AbslAnnotateIgnoreWritesBegin(__FILE__, __LINE__)

  /* Stop ignoring writes. */
  #define ABSL_ANNOTATE_IGNORE_WRITES_END() \
    AbslAnnotateIgnoreWritesEnd(__FILE__, __LINE__)

/* Clang provides limited support for static thread-safety analysis
   through a feature called Annotalysis. We configure macro-definitions
   according to whether Annotalysis support is available. */
#elif defined(ABSL_ANNOTALYSIS_ENABLED)

  #define ABSL_ANNOTATE_IGNORE_READS_BEGIN() \
    AbslStaticAnnotateIgnoreReadsBegin(__FILE__, __LINE__)

  #define ABSL_ANNOTATE_IGNORE_READS_END() \
    AbslStaticAnnotateIgnoreReadsEnd(__FILE__, __LINE__)

  #define ABSL_ANNOTATE_IGNORE_WRITES_BEGIN() \
    AbslStaticAnnotateIgnoreWritesBegin(__FILE__, __LINE__)

  #define ABSL_ANNOTATE_IGNORE_WRITES_END() \
    AbslStaticAnnotateIgnoreWritesEnd(__FILE__, __LINE__)

#else
  #define ABSL_ANNOTATE_IGNORE_READS_BEGIN()  /* empty */
  #define ABSL_ANNOTATE_IGNORE_READS_END()  /* empty */
  #define ABSL_ANNOTATE_IGNORE_WRITES_BEGIN()  /* empty */
  #define ABSL_ANNOTATE_IGNORE_WRITES_END()  /* empty */
#endif

/* Implement the ANNOTATE_IGNORE_READS_AND_WRITES_* annotations using the more
   primitive annotations defined above. */
#if defined(ABSL_ANNOTATIONS_ENABLED)

  /* Start ignoring all memory accesses (both reads and writes). */
  #define ABSL_ANNOTATE_IGNORE_READS_AND_WRITES_BEGIN() \
    do {                                           \
      ABSL_ANNOTATE_IGNORE_READS_BEGIN();               \
      ABSL_ANNOTATE_IGNORE_WRITES_BEGIN();              \
    }while (0)

  /* Stop ignoring both reads and writes. */
  #define ABSL_ANNOTATE_IGNORE_READS_AND_WRITES_END()   \
    do {                                           \
      ABSL_ANNOTATE_IGNORE_WRITES_END();                \
      ABSL_ANNOTATE_IGNORE_READS_END();                 \
    }while (0)

#else
  #define ABSL_ANNOTATE_IGNORE_READS_AND_WRITES_BEGIN()  /* empty */
  #define ABSL_ANNOTATE_IGNORE_READS_AND_WRITES_END()  /* empty */
#endif

/* Use the macros above rather than using these functions directly. */
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
void AbslAnnotateRWLockCreate(const char *file, int line,
                          const volatile void *lock);
void AbslAnnotateRWLockCreateStatic(const char *file, int line,
                          const volatile void *lock);
void AbslAnnotateRWLockDestroy(const char *file, int line,
                           const volatile void *lock);
void AbslAnnotateRWLockAcquired(const char *file, int line,
                            const volatile void *lock, long is_w);  /* NOLINT */
void AbslAnnotateRWLockReleased(const char *file, int line,
                            const volatile void *lock, long is_w);  /* NOLINT */
void AbslAnnotateBenignRace(const char *file, int line,
                        const volatile void *address,
                        const char *description);
void AbslAnnotateBenignRaceSized(const char *file, int line,
                        const volatile void *address,
                        size_t size,
                        const char *description);
void AbslAnnotateThreadName(const char *file, int line,
                        const char *name);
void AbslAnnotateEnableRaceDetection(const char *file, int line, int enable);
void AbslAnnotateMemoryIsInitialized(const char *file, int line,
                                 const volatile void *mem, size_t size);
void AbslAnnotateMemoryIsUninitialized(const char *file, int line,
                                   const volatile void *mem, size_t size);

/* Annotations expand to these functions, when Dynamic Annotations are enabled.
   These functions are either implemented as no-op calls, if no Sanitizer is
   attached, or provided with externally-linked implementations by a library
   like ThreadSanitizer. */
void AbslAnnotateIgnoreReadsBegin(const char *file, int line)
    ABSL_ATTRIBUTE_IGNORE_READS_BEGIN;
void AbslAnnotateIgnoreReadsEnd(const char *file, int line)
    ABSL_ATTRIBUTE_IGNORE_READS_END;
void AbslAnnotateIgnoreWritesBegin(const char *file, int line);
void AbslAnnotateIgnoreWritesEnd(const char *file, int line);

#if defined(ABSL_ANNOTALYSIS_ENABLED)
/* When Annotalysis is enabled without Dynamic Annotations, the use of
   static-inline functions allows the annotations to be read at compile-time,
   while still letting the compiler elide the functions from the final build.

   TODO(delesley) -- The exclusive lock here ignores writes as well, but
   allows IGNORE_READS_AND_WRITES to work properly. */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-function"
static inline void AbslStaticAnnotateIgnoreReadsBegin(const char *file, int line)
    ABSL_ATTRIBUTE_IGNORE_READS_BEGIN { (void)file; (void)line; }
static inline void AbslStaticAnnotateIgnoreReadsEnd(const char *file, int line)
    ABSL_ATTRIBUTE_IGNORE_READS_END { (void)file; (void)line; }
static inline void AbslStaticAnnotateIgnoreWritesBegin(
    const char *file, int line) { (void)file; (void)line; }
static inline void AbslStaticAnnotateIgnoreWritesEnd(
    const char *file, int line) { (void)file; (void)line; }
#pragma GCC diagnostic pop
#endif

/* Return non-zero value if running under valgrind.

  If "valgrind.h" is included into dynamic_annotations.cc,
  the regular valgrind mechanism will be used.
  See http://valgrind.org/docs/manual/manual-core-adv.html about
  RUNNING_ON_VALGRIND and other valgrind "client requests".
  The file "valgrind.h" may be obtained by doing
     svn co svn://svn.valgrind.org/valgrind/trunk/include

  If for some reason you can't use "valgrind.h" or want to fake valgrind,
  there are two ways to make this function return non-zero:
    - Use environment variable: export RUNNING_ON_VALGRIND=1
    - Make your tool intercept the function AbslRunningOnValgrind() and
      change its return value.
 */
int AbslRunningOnValgrind(void);

/* AbslValgrindSlowdown returns:
    * 1.0, if (AbslRunningOnValgrind() == 0)
    * 50.0, if (AbslRunningOnValgrind() != 0 && getenv("VALGRIND_SLOWDOWN") == NULL)
    * atof(getenv("VALGRIND_SLOWDOWN")) otherwise
   This function can be used to scale timeout values:
   EXAMPLE:
   for (;;) {
     DoExpensiveBackgroundTask();
     SleepForSeconds(5 * AbslValgrindSlowdown());
   }
 */
double AbslValgrindSlowdown(void);

#ifdef __cplusplus
}
#endif

/* ABSL_ANNOTATE_UNPROTECTED_READ is the preferred way to annotate racey reads.

     Instead of doing
        ABSL_ANNOTATE_IGNORE_READS_BEGIN();
        ... = x;
        ABSL_ANNOTATE_IGNORE_READS_END();
     one can use
        ... = ABSL_ANNOTATE_UNPROTECTED_READ(x); */
#if defined(__cplusplus) && defined(ABSL_ANNOTATIONS_ENABLED)
template <typename T>
inline T ABSL_ANNOTATE_UNPROTECTED_READ(const volatile T &x) { /* NOLINT */
  ABSL_ANNOTATE_IGNORE_READS_BEGIN();
  T res = x;
  ABSL_ANNOTATE_IGNORE_READS_END();
  return res;
  }
#else
  #define ABSL_ANNOTATE_UNPROTECTED_READ(x) (x)
#endif

#if ABSL_DYNAMIC_ANNOTATIONS_ENABLED != 0 && defined(__cplusplus)
  /* Apply ABSL_ANNOTATE_BENIGN_RACE_SIZED to a static variable. */
  #define ABSL_ANNOTATE_BENIGN_RACE_STATIC(static_var, description)        \
    namespace {                                                       \
      class static_var ## _annotator {                                \
       public:                                                        \
        static_var ## _annotator() {                                  \
          ABSL_ANNOTATE_BENIGN_RACE_SIZED(&static_var,                     \
                                      sizeof(static_var),             \
            # static_var ": " description);                           \
        }                                                             \
      };                                                              \
      static static_var ## _annotator the ## static_var ## _annotator;\
    }  // namespace
#else /* ABSL_DYNAMIC_ANNOTATIONS_ENABLED == 0 */
  #define ABSL_ANNOTATE_BENIGN_RACE_STATIC(static_var, description)  /* empty */
#endif /* ABSL_DYNAMIC_ANNOTATIONS_ENABLED */

#ifdef ADDRESS_SANITIZER
/* Describe the current state of a contiguous container such as e.g.
 * std::vector or std::string. For more details see
 * sanitizer/common_interface_defs.h, which is provided by the compiler. */
#include <sanitizer/common_interface_defs.h>
#define ABSL_ANNOTATE_CONTIGUOUS_CONTAINER(beg, end, old_mid, new_mid) \
  __sanitizer_annotate_contiguous_container(beg, end, old_mid, new_mid)
#define ABSL_ADDRESS_SANITIZER_REDZONE(name)         \
  struct { char x[8] __attribute__ ((aligned (8))); } name
#else
#define ABSL_ANNOTATE_CONTIGUOUS_CONTAINER(beg, end, old_mid, new_mid)
#define ABSL_ADDRESS_SANITIZER_REDZONE(name) static_assert(true, "")
#endif  // ADDRESS_SANITIZER

/* Undefine the macros intended only in this file. */
#undef ABSL_ANNOTALYSIS_ENABLED
#undef ABSL_ANNOTATIONS_ENABLED
#undef ABSL_ATTRIBUTE_IGNORE_READS_BEGIN
#undef ABSL_ATTRIBUTE_IGNORE_READS_END

#endif  /* ABSL_BASE_DYNAMIC_ANNOTATIONS_H_ */
