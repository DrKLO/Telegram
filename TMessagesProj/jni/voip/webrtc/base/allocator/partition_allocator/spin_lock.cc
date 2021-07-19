// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/spin_lock.h"

#include "base/threading/platform_thread.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <sched.h>
#endif

// The YIELD_PROCESSOR macro wraps an architecture specific-instruction that
// informs the processor we're in a busy wait, so it can handle the branch more
// intelligently and e.g. reduce power to our core or give more resources to the
// other hyper-thread on this core. See the following for context:
// https://software.intel.com/en-us/articles/benefitting-power-and-performance-sleep-loops
//
// The YIELD_THREAD macro tells the OS to relinquish our quantum. This is
// basically a worst-case fallback, and if you're hitting it with any frequency
// you really should be using a proper lock (such as |base::Lock|)rather than
// these spinlocks.
#if defined(OS_WIN)

#define YIELD_PROCESSOR YieldProcessor()
#define YIELD_THREAD SwitchToThread()

#elif defined(OS_POSIX) || defined(OS_FUCHSIA)

#if defined(ARCH_CPU_X86_64) || defined(ARCH_CPU_X86)
#define YIELD_PROCESSOR __asm__ __volatile__("pause")
#elif (defined(ARCH_CPU_ARMEL) && __ARM_ARCH >= 6) || defined(ARCH_CPU_ARM64)
#define YIELD_PROCESSOR __asm__ __volatile__("yield")
#elif defined(ARCH_CPU_MIPSEL)
// The MIPS32 docs state that the PAUSE instruction is a no-op on older
// architectures (first added in MIPS32r2). To avoid assembler errors when
// targeting pre-r2, we must encode the instruction manually.
#define YIELD_PROCESSOR __asm__ __volatile__(".word 0x00000140")
#elif defined(ARCH_CPU_MIPS64EL) && __mips_isa_rev >= 2
// Don't bother doing using .word here since r2 is the lowest supported mips64
// that Chromium supports.
#define YIELD_PROCESSOR __asm__ __volatile__("pause")
#elif defined(ARCH_CPU_PPC64_FAMILY)
#define YIELD_PROCESSOR __asm__ __volatile__("or 31,31,31")
#elif defined(ARCH_CPU_S390_FAMILY)
// just do nothing
#define YIELD_PROCESSOR ((void)0)
#endif  // ARCH

#ifndef YIELD_PROCESSOR
#warning "Processor yield not supported on this architecture."
#define YIELD_PROCESSOR ((void)0)
#endif

#define YIELD_THREAD sched_yield()

#else  // Other OS

#warning "Thread yield not supported on this OS."
#define YIELD_THREAD ((void)0)

#endif  // OS_WIN

namespace base {
namespace subtle {

void SpinLock::LockSlow() {
  // The value of |kYieldProcessorTries| is cargo culted from TCMalloc, Windows
  // critical section defaults, and various other recommendations.
  // TODO(jschuh): Further tuning may be warranted.
  static const int kYieldProcessorTries = 1000;
  // The value of |kYieldThreadTries| is completely made up.
  static const int kYieldThreadTries = 10;
  int yield_thread_count = 0;
  do {
    do {
      for (int count = 0; count < kYieldProcessorTries; ++count) {
        // Let the processor know we're spinning.
        YIELD_PROCESSOR;
        if (!lock_.load(std::memory_order_relaxed) &&
            LIKELY(!lock_.exchange(true, std::memory_order_acquire)))
          return;
      }

      if (yield_thread_count < kYieldThreadTries) {
        ++yield_thread_count;
        // Give the OS a chance to schedule something on this core.
        YIELD_THREAD;
      } else {
        // At this point, it's likely that the lock is held by a lower priority
        // thread that is unavailable to finish its work because of higher
        // priority threads spinning here. Sleeping should ensure that they make
        // progress.
        PlatformThread::Sleep(TimeDelta::FromMilliseconds(1));
      }
    } while (lock_.load(std::memory_order_relaxed));
  } while (UNLIKELY(lock_.exchange(true, std::memory_order_acquire)));
}

}  // namespace subtle
}  // namespace base
