// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/allocator_shim.h"

#include <errno.h>

#include <atomic>
#include <new>

#include "base/allocator/buildflags.h"
#include "base/atomicops.h"
#include "base/bits.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/process/process_metrics.h"
#include "base/threading/platform_thread.h"
#include "build/build_config.h"

#if !defined(OS_WIN)
#include <unistd.h>
#else
#include "base/allocator/winheap_stubs_win.h"
#endif

#if defined(OS_MACOSX)
#include <malloc/malloc.h>

#include "base/allocator/allocator_interception_mac.h"
#endif

// No calls to malloc / new in this file. They would would cause re-entrancy of
// the shim, which is hard to deal with. Keep this code as simple as possible
// and don't use any external C++ object here, not even //base ones. Even if
// they are safe to use today, in future they might be refactored.

namespace {

base::subtle::AtomicWord g_chain_head =
    reinterpret_cast<base::subtle::AtomicWord>(
        &base::allocator::AllocatorDispatch::default_dispatch);

bool g_call_new_handler_on_malloc_failure = false;

inline size_t GetCachedPageSize() {
  static size_t pagesize = 0;
  if (!pagesize)
    pagesize = base::GetPageSize();
  return pagesize;
}

// Calls the std::new handler thread-safely. Returns true if a new_handler was
// set and called, false if no new_handler was set.
bool CallNewHandler(size_t size) {
#if defined(OS_WIN)
  return base::allocator::WinCallNewHandler(size);
#else
  std::new_handler nh = std::get_new_handler();
  if (!nh)
    return false;
  (*nh)();
  // Assume the new_handler will abort if it fails. Exception are disabled and
  // we don't support the case of a new_handler throwing std::bad_balloc.
  return true;
#endif
}

inline const base::allocator::AllocatorDispatch* GetChainHead() {
  // TODO(primiano): Just use NoBarrier_Load once crbug.com/593344 is fixed.
  // Unfortunately due to that bug NoBarrier_Load() is mistakenly fully
  // barriered on Linux+Clang, and that causes visible perf regressons.
  return reinterpret_cast<const base::allocator::AllocatorDispatch*>(
#if defined(OS_LINUX) && defined(__clang__)
      *static_cast<const volatile base::subtle::AtomicWord*>(&g_chain_head)
#else
      base::subtle::NoBarrier_Load(&g_chain_head)
#endif
  );
}

}  // namespace

namespace base {
namespace allocator {

void SetCallNewHandlerOnMallocFailure(bool value) {
  g_call_new_handler_on_malloc_failure = value;
}

void* UncheckedAlloc(size_t size) {
  const allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->alloc_function(chain_head, size, nullptr);
}

void InsertAllocatorDispatch(AllocatorDispatch* dispatch) {
  // Loop in case of (an unlikely) race on setting the list head.
  size_t kMaxRetries = 7;
  for (size_t i = 0; i < kMaxRetries; ++i) {
    const AllocatorDispatch* chain_head = GetChainHead();
    dispatch->next = chain_head;

    // This function guarantees to be thread-safe w.r.t. concurrent
    // insertions. It also has to guarantee that all the threads always
    // see a consistent chain, hence the atomic_thread_fence() below.
    // InsertAllocatorDispatch() is NOT a fastpath, as opposite to malloc(), so
    // we don't really want this to be a release-store with a corresponding
    // acquire-load during malloc().
    std::atomic_thread_fence(std::memory_order_seq_cst);
    subtle::AtomicWord old_value =
        reinterpret_cast<subtle::AtomicWord>(chain_head);
    // Set the chain head to the new dispatch atomically. If we lose the race,
    // the comparison will fail, and the new head of chain will be returned.
    if (subtle::NoBarrier_CompareAndSwap(
            &g_chain_head, old_value,
            reinterpret_cast<subtle::AtomicWord>(dispatch)) == old_value) {
      // Success.
      return;
    }
  }

  CHECK(false);  // Too many retries, this shouldn't happen.
}

void RemoveAllocatorDispatchForTesting(AllocatorDispatch* dispatch) {
  DCHECK_EQ(GetChainHead(), dispatch);
  subtle::NoBarrier_Store(&g_chain_head,
                          reinterpret_cast<subtle::AtomicWord>(dispatch->next));
}

}  // namespace allocator
}  // namespace base

// The Shim* functions below are the entry-points into the shim-layer and
// are supposed to be invoked by the allocator_shim_override_*
// headers to route the malloc / new symbols through the shim layer.
// They are defined as ALWAYS_INLINE in order to remove a level of indirection
// between the system-defined entry points and the shim implementations.
extern "C" {

// The general pattern for allocations is:
// - Try to allocate, if succeded return the pointer.
// - If the allocation failed:
//   - Call the std::new_handler if it was a C++ allocation.
//   - Call the std::new_handler if it was a malloc() (or calloc() or similar)
//     AND SetCallNewHandlerOnMallocFailure(true).
//   - If the std::new_handler is NOT set just return nullptr.
//   - If the std::new_handler is set:
//     - Assume it will abort() if it fails (very likely the new_handler will
//       just suicide priting a message).
//     - Assume it did succeed if it returns, in which case reattempt the alloc.

ALWAYS_INLINE void* ShimCppNew(size_t size) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr;
  do {
    void* context = nullptr;
#if defined(OS_MACOSX)
    context = malloc_default_zone();
#endif
    ptr = chain_head->alloc_function(chain_head, size, context);
  } while (!ptr && CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void* ShimCppAlignedNew(size_t size, size_t alignment) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr;
  do {
    void* context = nullptr;
#if defined(OS_MACOSX)
    context = malloc_default_zone();
#endif
    ptr = chain_head->alloc_aligned_function(chain_head, alignment, size,
                                             context);
  } while (!ptr && CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void ShimCppDelete(void* address) {
  void* context = nullptr;
#if defined(OS_MACOSX)
  context = malloc_default_zone();
#endif
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->free_function(chain_head, address, context);
}

ALWAYS_INLINE void* ShimMalloc(size_t size, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr;
  do {
    ptr = chain_head->alloc_function(chain_head, size, context);
  } while (!ptr && g_call_new_handler_on_malloc_failure &&
           CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void* ShimCalloc(size_t n, size_t size, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr;
  do {
    ptr = chain_head->alloc_zero_initialized_function(chain_head, n, size,
                                                      context);
  } while (!ptr && g_call_new_handler_on_malloc_failure &&
           CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void* ShimRealloc(void* address, size_t size, void* context) {
  // realloc(size == 0) means free() and might return a nullptr. We should
  // not call the std::new_handler in that case, though.
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr;
  do {
    ptr = chain_head->realloc_function(chain_head, address, size, context);
  } while (!ptr && size && g_call_new_handler_on_malloc_failure &&
           CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void* ShimMemalign(size_t alignment, size_t size, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr;
  do {
    ptr = chain_head->alloc_aligned_function(chain_head, alignment, size,
                                             context);
  } while (!ptr && g_call_new_handler_on_malloc_failure &&
           CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE int ShimPosixMemalign(void** res, size_t alignment, size_t size) {
  // posix_memalign is supposed to check the arguments. See tc_posix_memalign()
  // in tc_malloc.cc.
  if (((alignment % sizeof(void*)) != 0) ||
      !base::bits::IsPowerOfTwo(alignment)) {
    return EINVAL;
  }
  void* ptr = ShimMemalign(alignment, size, nullptr);
  *res = ptr;
  return ptr ? 0 : ENOMEM;
}

ALWAYS_INLINE void* ShimValloc(size_t size, void* context) {
  return ShimMemalign(GetCachedPageSize(), size, context);
}

ALWAYS_INLINE void* ShimPvalloc(size_t size) {
  // pvalloc(0) should allocate one page, according to its man page.
  if (size == 0) {
    size = GetCachedPageSize();
  } else {
    size = (size + GetCachedPageSize() - 1) & ~(GetCachedPageSize() - 1);
  }
  // The third argument is nullptr because pvalloc is glibc only and does not
  // exist on OSX/BSD systems.
  return ShimMemalign(GetCachedPageSize(), size, nullptr);
}

ALWAYS_INLINE void ShimFree(void* address, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->free_function(chain_head, address, context);
}

ALWAYS_INLINE size_t ShimGetSizeEstimate(const void* address, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->get_size_estimate_function(
      chain_head, const_cast<void*>(address), context);
}

ALWAYS_INLINE unsigned ShimBatchMalloc(size_t size,
                                       void** results,
                                       unsigned num_requested,
                                       void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->batch_malloc_function(chain_head, size, results,
                                           num_requested, context);
}

ALWAYS_INLINE void ShimBatchFree(void** to_be_freed,
                                 unsigned num_to_be_freed,
                                 void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->batch_free_function(chain_head, to_be_freed,
                                         num_to_be_freed, context);
}

ALWAYS_INLINE void ShimFreeDefiniteSize(void* ptr, size_t size, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->free_definite_size_function(chain_head, ptr, size,
                                                 context);
}

ALWAYS_INLINE void* ShimAlignedMalloc(size_t size,
                                      size_t alignment,
                                      void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr = nullptr;
  do {
    ptr = chain_head->aligned_malloc_function(chain_head, size, alignment,
                                              context);
  } while (!ptr && g_call_new_handler_on_malloc_failure &&
           CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void* ShimAlignedRealloc(void* address,
                                       size_t size,
                                       size_t alignment,
                                       void* context) {
  // _aligned_realloc(size == 0) means _aligned_free() and might return a
  // nullptr. We should not call the std::new_handler in that case, though.
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  void* ptr = nullptr;
  do {
    ptr = chain_head->aligned_realloc_function(chain_head, address, size,
                                               alignment, context);
  } while (!ptr && size && g_call_new_handler_on_malloc_failure &&
           CallNewHandler(size));
  return ptr;
}

ALWAYS_INLINE void ShimAlignedFree(void* address, void* context) {
  const base::allocator::AllocatorDispatch* const chain_head = GetChainHead();
  return chain_head->aligned_free_function(chain_head, address, context);
}

}  // extern "C"

#if !defined(OS_WIN) && !defined(OS_MACOSX)
// Cpp symbols (new / delete) should always be routed through the shim layer
// except on Windows and macOS where the malloc intercept is deep enough that it
// also catches the cpp calls.
#include "base/allocator/allocator_shim_override_cpp_symbols.h"
#endif

#if defined(OS_ANDROID)
// Android does not support symbol interposition. The way malloc symbols are
// intercepted on Android is by using link-time -wrap flags.
#include "base/allocator/allocator_shim_override_linker_wrapped_symbols.h"
#elif defined(OS_WIN)
// On Windows we use plain link-time overriding of the CRT symbols.
#include "base/allocator/allocator_shim_override_ucrt_symbols_win.h"
#elif defined(OS_MACOSX)
#include "base/allocator/allocator_shim_default_dispatch_to_mac_zoned_malloc.h"
#include "base/allocator/allocator_shim_override_mac_symbols.h"
#else
#include "base/allocator/allocator_shim_override_libc_symbols.h"
#endif

// In the case of tcmalloc we also want to plumb into the glibc hooks
// to avoid that allocations made in glibc itself (e.g., strdup()) get
// accidentally performed on the glibc heap instead of the tcmalloc one.
#if BUILDFLAG(USE_TCMALLOC)
#include "base/allocator/allocator_shim_override_glibc_weak_symbols.h"
#endif

#if defined(OS_MACOSX)
namespace base {
namespace allocator {
void InitializeAllocatorShim() {
  // Prepares the default dispatch. After the intercepted malloc calls have
  // traversed the shim this will route them to the default malloc zone.
  InitializeDefaultDispatchToMacAllocator();

  MallocZoneFunctions functions = MallocZoneFunctionsToReplaceDefault();

  // This replaces the default malloc zone, causing calls to malloc & friends
  // from the codebase to be routed to ShimMalloc() above.
  base::allocator::ReplaceFunctionsForStoredZones(&functions);
}
}  // namespace allocator
}  // namespace base
#endif

// Cross-checks.

#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
#error The allocator shim should not be compiled when building for memory tools.
#endif

#if (defined(__GNUC__) && defined(__EXCEPTIONS)) || \
    (defined(_MSC_VER) && defined(_CPPUNWIND))
#error This code cannot be used when exceptions are turned on.
#endif
