// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file contains all the logic necessary to intercept allocations on
// macOS. "malloc zones" are an abstraction that allows the process to intercept
// all malloc-related functions.  There is no good mechanism [short of
// interposition] to determine new malloc zones are added, so there's no clean
// mechanism to intercept all malloc zones. This file contains logic to
// intercept the default and purgeable zones, which always exist. A cursory
// review of Chrome seems to imply that non-default zones are almost never used.
//
// This file also contains logic to intercept Core Foundation and Objective-C
// allocations. The implementations forward to the default malloc zone, so the
// only reason to intercept these calls is to re-label OOM crashes with slightly
// more details.

#include "base/allocator/allocator_interception_mac.h"

#include <CoreFoundation/CoreFoundation.h>
#import <Foundation/Foundation.h>
#include <errno.h>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#import <objc/runtime.h>
#include <stddef.h>

#include <new>

#include "base/allocator/buildflags.h"
#include "base/allocator/malloc_zone_functions_mac.h"
#include "base/bind.h"
#include "base/bits.h"
#include "base/logging.h"
#include "base/mac/mac_util.h"
#include "base/mac/mach_logging.h"
#include "base/process/memory.h"
#include "base/threading/sequenced_task_runner_handle.h"
#include "build/build_config.h"
#include "third_party/apple_apsl/CFBase.h"

namespace base {
namespace allocator {

bool g_replaced_default_zone = false;

namespace {

bool g_oom_killer_enabled;

// Starting with Mac OS X 10.7, the zone allocators set up by the system are
// read-only, to prevent them from being overwritten in an attack. However,
// blindly unprotecting and reprotecting the zone allocators fails with
// GuardMalloc because GuardMalloc sets up its zone allocator using a block of
// memory in its bss. Explicit saving/restoring of the protection is required.
//
// This function takes a pointer to a malloc zone, de-protects it if necessary,
// and returns (in the out parameters) a region of memory (if any) to be
// re-protected when modifications are complete. This approach assumes that
// there is no contention for the protection of this memory.
void DeprotectMallocZone(ChromeMallocZone* default_zone,
                         mach_vm_address_t* reprotection_start,
                         mach_vm_size_t* reprotection_length,
                         vm_prot_t* reprotection_value) {
  mach_port_t unused;
  *reprotection_start = reinterpret_cast<mach_vm_address_t>(default_zone);
  struct vm_region_basic_info_64 info;
  mach_msg_type_number_t count = VM_REGION_BASIC_INFO_COUNT_64;
  kern_return_t result = mach_vm_region(
      mach_task_self(), reprotection_start, reprotection_length,
      VM_REGION_BASIC_INFO_64, reinterpret_cast<vm_region_info_t>(&info),
      &count, &unused);
  MACH_CHECK(result == KERN_SUCCESS, result) << "mach_vm_region";

  // The kernel always returns a null object for VM_REGION_BASIC_INFO_64, but
  // balance it with a deallocate in case this ever changes. See 10.9.2
  // xnu-2422.90.20/osfmk/vm/vm_map.c vm_map_region.
  mach_port_deallocate(mach_task_self(), unused);

  // Does the region fully enclose the zone pointers? Possibly unwarranted
  // simplification used: using the size of a full version 8 malloc zone rather
  // than the actual smaller size if the passed-in zone is not version 8.
  CHECK(*reprotection_start <=
        reinterpret_cast<mach_vm_address_t>(default_zone));
  mach_vm_size_t zone_offset =
      reinterpret_cast<mach_vm_size_t>(default_zone) -
      reinterpret_cast<mach_vm_size_t>(*reprotection_start);
  CHECK(zone_offset + sizeof(ChromeMallocZone) <= *reprotection_length);

  if (info.protection & VM_PROT_WRITE) {
    // No change needed; the zone is already writable.
    *reprotection_start = 0;
    *reprotection_length = 0;
    *reprotection_value = VM_PROT_NONE;
  } else {
    *reprotection_value = info.protection;
    result = mach_vm_protect(mach_task_self(), *reprotection_start,
                             *reprotection_length, false,
                             info.protection | VM_PROT_WRITE);
    MACH_CHECK(result == KERN_SUCCESS, result) << "mach_vm_protect";
  }
}

#if !defined(ADDRESS_SANITIZER)

MallocZoneFunctions g_old_zone;
MallocZoneFunctions g_old_purgeable_zone;

void* oom_killer_malloc(struct _malloc_zone_t* zone, size_t size) {
  void* result = g_old_zone.malloc(zone, size);
  if (!result && size)
    TerminateBecauseOutOfMemory(size);
  return result;
}

void* oom_killer_calloc(struct _malloc_zone_t* zone,
                        size_t num_items,
                        size_t size) {
  void* result = g_old_zone.calloc(zone, num_items, size);
  if (!result && num_items && size)
    TerminateBecauseOutOfMemory(num_items * size);
  return result;
}

void* oom_killer_valloc(struct _malloc_zone_t* zone, size_t size) {
  void* result = g_old_zone.valloc(zone, size);
  if (!result && size)
    TerminateBecauseOutOfMemory(size);
  return result;
}

void oom_killer_free(struct _malloc_zone_t* zone, void* ptr) {
  g_old_zone.free(zone, ptr);
}

void* oom_killer_realloc(struct _malloc_zone_t* zone, void* ptr, size_t size) {
  void* result = g_old_zone.realloc(zone, ptr, size);
  if (!result && size)
    TerminateBecauseOutOfMemory(size);
  return result;
}

void* oom_killer_memalign(struct _malloc_zone_t* zone,
                          size_t alignment,
                          size_t size) {
  void* result = g_old_zone.memalign(zone, alignment, size);
  // Only die if posix_memalign would have returned ENOMEM, since there are
  // other reasons why NULL might be returned (see
  // http://opensource.apple.com/source/Libc/Libc-583/gen/malloc.c ).
  if (!result && size && alignment >= sizeof(void*) &&
      base::bits::IsPowerOfTwo(alignment)) {
    TerminateBecauseOutOfMemory(size);
  }
  return result;
}

void* oom_killer_malloc_purgeable(struct _malloc_zone_t* zone, size_t size) {
  void* result = g_old_purgeable_zone.malloc(zone, size);
  if (!result && size)
    TerminateBecauseOutOfMemory(size);
  return result;
}

void* oom_killer_calloc_purgeable(struct _malloc_zone_t* zone,
                                  size_t num_items,
                                  size_t size) {
  void* result = g_old_purgeable_zone.calloc(zone, num_items, size);
  if (!result && num_items && size)
    TerminateBecauseOutOfMemory(num_items * size);
  return result;
}

void* oom_killer_valloc_purgeable(struct _malloc_zone_t* zone, size_t size) {
  void* result = g_old_purgeable_zone.valloc(zone, size);
  if (!result && size)
    TerminateBecauseOutOfMemory(size);
  return result;
}

void oom_killer_free_purgeable(struct _malloc_zone_t* zone, void* ptr) {
  g_old_purgeable_zone.free(zone, ptr);
}

void* oom_killer_realloc_purgeable(struct _malloc_zone_t* zone,
                                   void* ptr,
                                   size_t size) {
  void* result = g_old_purgeable_zone.realloc(zone, ptr, size);
  if (!result && size)
    TerminateBecauseOutOfMemory(size);
  return result;
}

void* oom_killer_memalign_purgeable(struct _malloc_zone_t* zone,
                                    size_t alignment,
                                    size_t size) {
  void* result = g_old_purgeable_zone.memalign(zone, alignment, size);
  // Only die if posix_memalign would have returned ENOMEM, since there are
  // other reasons why NULL might be returned (see
  // http://opensource.apple.com/source/Libc/Libc-583/gen/malloc.c ).
  if (!result && size && alignment >= sizeof(void*) &&
      base::bits::IsPowerOfTwo(alignment)) {
    TerminateBecauseOutOfMemory(size);
  }
  return result;
}

#endif  // !defined(ADDRESS_SANITIZER)

#if !defined(ADDRESS_SANITIZER)

// === Core Foundation CFAllocators ===

bool CanGetContextForCFAllocator() {
  return !base::mac::IsOSLaterThan10_15_DontCallThis();
}

CFAllocatorContext* ContextForCFAllocator(CFAllocatorRef allocator) {
  ChromeCFAllocatorLions* our_allocator = const_cast<ChromeCFAllocatorLions*>(
      reinterpret_cast<const ChromeCFAllocatorLions*>(allocator));
  return &our_allocator->_context;
}

CFAllocatorAllocateCallBack g_old_cfallocator_system_default;
CFAllocatorAllocateCallBack g_old_cfallocator_malloc;
CFAllocatorAllocateCallBack g_old_cfallocator_malloc_zone;

void* oom_killer_cfallocator_system_default(CFIndex alloc_size,
                                            CFOptionFlags hint,
                                            void* info) {
  void* result = g_old_cfallocator_system_default(alloc_size, hint, info);
  if (!result)
    TerminateBecauseOutOfMemory(alloc_size);
  return result;
}

void* oom_killer_cfallocator_malloc(CFIndex alloc_size,
                                    CFOptionFlags hint,
                                    void* info) {
  void* result = g_old_cfallocator_malloc(alloc_size, hint, info);
  if (!result)
    TerminateBecauseOutOfMemory(alloc_size);
  return result;
}

void* oom_killer_cfallocator_malloc_zone(CFIndex alloc_size,
                                         CFOptionFlags hint,
                                         void* info) {
  void* result = g_old_cfallocator_malloc_zone(alloc_size, hint, info);
  if (!result)
    TerminateBecauseOutOfMemory(alloc_size);
  return result;
}

#endif  // !defined(ADDRESS_SANITIZER)

// === Cocoa NSObject allocation ===

typedef id (*allocWithZone_t)(id, SEL, NSZone*);
allocWithZone_t g_old_allocWithZone;

id oom_killer_allocWithZone(id self, SEL _cmd, NSZone* zone) {
  id result = g_old_allocWithZone(self, _cmd, zone);
  if (!result)
    TerminateBecauseOutOfMemory(0);
  return result;
}

void UninterceptMallocZoneForTesting(struct _malloc_zone_t* zone) {
  ChromeMallocZone* chrome_zone = reinterpret_cast<ChromeMallocZone*>(zone);
  if (!IsMallocZoneAlreadyStored(chrome_zone))
    return;
  MallocZoneFunctions& functions = GetFunctionsForZone(zone);
  ReplaceZoneFunctions(chrome_zone, &functions);
}

}  // namespace

bool UncheckedMallocMac(size_t size, void** result) {
#if defined(ADDRESS_SANITIZER)
  *result = malloc(size);
#else
  if (g_old_zone.malloc) {
    *result = g_old_zone.malloc(malloc_default_zone(), size);
  } else {
    *result = malloc(size);
  }
#endif  // defined(ADDRESS_SANITIZER)

  return *result != NULL;
}

bool UncheckedCallocMac(size_t num_items, size_t size, void** result) {
#if defined(ADDRESS_SANITIZER)
  *result = calloc(num_items, size);
#else
  if (g_old_zone.calloc) {
    *result = g_old_zone.calloc(malloc_default_zone(), num_items, size);
  } else {
    *result = calloc(num_items, size);
  }
#endif  // defined(ADDRESS_SANITIZER)

  return *result != NULL;
}

void StoreFunctionsForDefaultZone() {
  ChromeMallocZone* default_zone = reinterpret_cast<ChromeMallocZone*>(
      malloc_default_zone());
  StoreMallocZone(default_zone);
}

void StoreFunctionsForAllZones() {
  // This ensures that the default zone is always at the front of the array,
  // which is important for performance.
  StoreFunctionsForDefaultZone();

  vm_address_t* zones;
  unsigned int count;
  kern_return_t kr = malloc_get_all_zones(mach_task_self(), 0, &zones, &count);
  if (kr != KERN_SUCCESS)
    return;
  for (unsigned int i = 0; i < count; ++i) {
    ChromeMallocZone* zone = reinterpret_cast<ChromeMallocZone*>(zones[i]);
    StoreMallocZone(zone);
  }
}

void ReplaceFunctionsForStoredZones(const MallocZoneFunctions* functions) {
  // The default zone does not get returned in malloc_get_all_zones().
  ChromeMallocZone* default_zone =
      reinterpret_cast<ChromeMallocZone*>(malloc_default_zone());
  if (DoesMallocZoneNeedReplacing(default_zone, functions)) {
    ReplaceZoneFunctions(default_zone, functions);
  }

  vm_address_t* zones;
  unsigned int count;
  kern_return_t kr =
      malloc_get_all_zones(mach_task_self(), nullptr, &zones, &count);
  if (kr != KERN_SUCCESS)
    return;
  for (unsigned int i = 0; i < count; ++i) {
    ChromeMallocZone* zone = reinterpret_cast<ChromeMallocZone*>(zones[i]);
    if (DoesMallocZoneNeedReplacing(zone, functions)) {
      ReplaceZoneFunctions(zone, functions);
    }
  }
  g_replaced_default_zone = true;
}

void InterceptAllocationsMac() {
  if (g_oom_killer_enabled)
    return;

  g_oom_killer_enabled = true;

// === C malloc/calloc/valloc/realloc/posix_memalign ===

// This approach is not perfect, as requests for amounts of memory larger than
// MALLOC_ABSOLUTE_MAX_SIZE (currently SIZE_T_MAX - (2 * PAGE_SIZE)) will
// still fail with a NULL rather than dying (see
// http://opensource.apple.com/source/Libc/Libc-583/gen/malloc.c for details).
// Unfortunately, it's the best we can do. Also note that this does not affect
// allocations from non-default zones.

#if !defined(ADDRESS_SANITIZER)
  // Don't do anything special on OOM for the malloc zones replaced by
  // AddressSanitizer, as modifying or protecting them may not work correctly.
  ChromeMallocZone* default_zone =
      reinterpret_cast<ChromeMallocZone*>(malloc_default_zone());
  if (!IsMallocZoneAlreadyStored(default_zone)) {
    StoreZoneFunctions(default_zone, &g_old_zone);
    MallocZoneFunctions new_functions = {};
    new_functions.malloc = oom_killer_malloc;
    new_functions.calloc = oom_killer_calloc;
    new_functions.valloc = oom_killer_valloc;
    new_functions.free = oom_killer_free;
    new_functions.realloc = oom_killer_realloc;
    new_functions.memalign = oom_killer_memalign;

    ReplaceZoneFunctions(default_zone, &new_functions);
    g_replaced_default_zone = true;
  }

  ChromeMallocZone* purgeable_zone =
      reinterpret_cast<ChromeMallocZone*>(malloc_default_purgeable_zone());
  if (purgeable_zone && !IsMallocZoneAlreadyStored(purgeable_zone)) {
    StoreZoneFunctions(purgeable_zone, &g_old_purgeable_zone);
    MallocZoneFunctions new_functions = {};
    new_functions.malloc = oom_killer_malloc_purgeable;
    new_functions.calloc = oom_killer_calloc_purgeable;
    new_functions.valloc = oom_killer_valloc_purgeable;
    new_functions.free = oom_killer_free_purgeable;
    new_functions.realloc = oom_killer_realloc_purgeable;
    new_functions.memalign = oom_killer_memalign_purgeable;
    ReplaceZoneFunctions(purgeable_zone, &new_functions);
  }
#endif

  // === C malloc_zone_batch_malloc ===

  // batch_malloc is omitted because the default malloc zone's implementation
  // only supports batch_malloc for "tiny" allocations from the free list. It
  // will fail for allocations larger than "tiny", and will only allocate as
  // many blocks as it's able to from the free list. These factors mean that it
  // can return less than the requested memory even in a non-out-of-memory
  // situation. There's no good way to detect whether a batch_malloc failure is
  // due to these other factors, or due to genuine memory or address space
  // exhaustion. The fact that it only allocates space from the "tiny" free list
  // means that it's likely that a failure will not be due to memory exhaustion.
  // Similarly, these constraints on batch_malloc mean that callers must always
  // be expecting to receive less memory than was requested, even in situations
  // where memory pressure is not a concern. Finally, the only public interface
  // to batch_malloc is malloc_zone_batch_malloc, which is specific to the
  // system's malloc implementation. It's unlikely that anyone's even heard of
  // it.

#ifndef ADDRESS_SANITIZER
  // === Core Foundation CFAllocators ===

  // This will not catch allocation done by custom allocators, but will catch
  // all allocation done by system-provided ones.

  CHECK(!g_old_cfallocator_system_default && !g_old_cfallocator_malloc &&
        !g_old_cfallocator_malloc_zone)
      << "Old allocators unexpectedly non-null";

  bool cf_allocator_internals_known = CanGetContextForCFAllocator();

  if (cf_allocator_internals_known) {
    CFAllocatorContext* context =
        ContextForCFAllocator(kCFAllocatorSystemDefault);
    CHECK(context) << "Failed to get context for kCFAllocatorSystemDefault.";
    g_old_cfallocator_system_default = context->allocate;
    CHECK(g_old_cfallocator_system_default)
        << "Failed to get kCFAllocatorSystemDefault allocation function.";
    context->allocate = oom_killer_cfallocator_system_default;

    context = ContextForCFAllocator(kCFAllocatorMalloc);
    CHECK(context) << "Failed to get context for kCFAllocatorMalloc.";
    g_old_cfallocator_malloc = context->allocate;
    CHECK(g_old_cfallocator_malloc)
        << "Failed to get kCFAllocatorMalloc allocation function.";
    context->allocate = oom_killer_cfallocator_malloc;

    context = ContextForCFAllocator(kCFAllocatorMallocZone);
    CHECK(context) << "Failed to get context for kCFAllocatorMallocZone.";
    g_old_cfallocator_malloc_zone = context->allocate;
    CHECK(g_old_cfallocator_malloc_zone)
        << "Failed to get kCFAllocatorMallocZone allocation function.";
    context->allocate = oom_killer_cfallocator_malloc_zone;
  } else {
    DLOG(WARNING) << "Internals of CFAllocator not known; out-of-memory "
                     "failures via CFAllocator will not result in termination. "
                     "http://crbug.com/45650";
  }
#endif

  // === Cocoa NSObject allocation ===

  // Note that both +[NSObject new] and +[NSObject alloc] call through to
  // +[NSObject allocWithZone:].

  CHECK(!g_old_allocWithZone) << "Old allocator unexpectedly non-null";

  Class nsobject_class = [NSObject class];
  Method orig_method =
      class_getClassMethod(nsobject_class, @selector(allocWithZone:));
  g_old_allocWithZone =
      reinterpret_cast<allocWithZone_t>(method_getImplementation(orig_method));
  CHECK(g_old_allocWithZone)
      << "Failed to get allocWithZone allocation function.";
  method_setImplementation(orig_method,
                           reinterpret_cast<IMP>(oom_killer_allocWithZone));
}

void UninterceptMallocZonesForTesting() {
  UninterceptMallocZoneForTesting(malloc_default_zone());
  vm_address_t* zones;
  unsigned int count;
  kern_return_t kr = malloc_get_all_zones(mach_task_self(), 0, &zones, &count);
  CHECK(kr == KERN_SUCCESS);
  for (unsigned int i = 0; i < count; ++i) {
    UninterceptMallocZoneForTesting(
        reinterpret_cast<struct _malloc_zone_t*>(zones[i]));
  }

  ClearAllMallocZonesForTesting();
}

namespace {

void ShimNewMallocZonesAndReschedule(base::Time end_time,
                                     base::TimeDelta delay) {
  ShimNewMallocZones();

  if (base::Time::Now() > end_time)
    return;

  base::TimeDelta next_delay = delay * 2;
  SequencedTaskRunnerHandle::Get()->PostDelayedTask(
      FROM_HERE,
      base::BindOnce(&ShimNewMallocZonesAndReschedule, end_time, next_delay),
      delay);
}

}  // namespace

void PeriodicallyShimNewMallocZones() {
  base::Time end_time = base::Time::Now() + base::TimeDelta::FromMinutes(1);
  base::TimeDelta initial_delay = base::TimeDelta::FromSeconds(1);
  ShimNewMallocZonesAndReschedule(end_time, initial_delay);
}

void ShimNewMallocZones() {
  StoreFunctionsForAllZones();

  // Use the functions for the default zone as a template to replace those
  // new zones.
  ChromeMallocZone* default_zone =
      reinterpret_cast<ChromeMallocZone*>(malloc_default_zone());
  DCHECK(IsMallocZoneAlreadyStored(default_zone));

  MallocZoneFunctions new_functions;
  StoreZoneFunctions(default_zone, &new_functions);
  ReplaceFunctionsForStoredZones(&new_functions);
}

void ReplaceZoneFunctions(ChromeMallocZone* zone,
                          const MallocZoneFunctions* functions) {
  // Remove protection.
  mach_vm_address_t reprotection_start = 0;
  mach_vm_size_t reprotection_length = 0;
  vm_prot_t reprotection_value = VM_PROT_NONE;
  DeprotectMallocZone(zone, &reprotection_start, &reprotection_length,
                      &reprotection_value);

  CHECK(functions->malloc && functions->calloc && functions->valloc &&
        functions->free && functions->realloc);
  zone->malloc = functions->malloc;
  zone->calloc = functions->calloc;
  zone->valloc = functions->valloc;
  zone->free = functions->free;
  zone->realloc = functions->realloc;
  if (functions->batch_malloc)
    zone->batch_malloc = functions->batch_malloc;
  if (functions->batch_free)
    zone->batch_free = functions->batch_free;
  if (functions->size)
    zone->size = functions->size;
  if (zone->version >= 5 && functions->memalign) {
    zone->memalign = functions->memalign;
  }
  if (zone->version >= 6 && functions->free_definite_size) {
    zone->free_definite_size = functions->free_definite_size;
  }

  // Restore protection if it was active.
  if (reprotection_start) {
    kern_return_t result =
        mach_vm_protect(mach_task_self(), reprotection_start,
                        reprotection_length, false, reprotection_value);
    MACH_CHECK(result == KERN_SUCCESS, result) << "mach_vm_protect";
  }
}

}  // namespace allocator
}  // namespace base
