// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_POSIX_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_POSIX_H_

#include <errno.h>
#include <sys/mman.h>

#include "base/logging.h"
#include "build/build_config.h"

#if defined(OS_MACOSX)
#include "base/mac/foundation_util.h"
#include "base/mac/mac_util.h"
#include "base/mac/scoped_cftyperef.h"

#include <Security/Security.h>
#include <mach/mach.h>
#endif
#if defined(OS_ANDROID)
#include <sys/prctl.h>
#endif
#if defined(OS_LINUX)
#include <sys/resource.h>

#include <algorithm>
#endif

#include "base/allocator/partition_allocator/page_allocator.h"

#ifndef MAP_ANONYMOUS
#define MAP_ANONYMOUS MAP_ANON
#endif

namespace base {

namespace {

#if defined(OS_ANDROID)
const char* PageTagToName(PageTag tag) {
  // Important: All the names should be string literals. As per prctl.h in
  // //third_party/android_ndk the kernel keeps a pointer to the name instead
  // of copying it.
  //
  // Having the name in .rodata ensures that the pointer remains valid as
  // long as the mapping is alive.
  switch (tag) {
    case PageTag::kBlinkGC:
      return "blink_gc";
    case PageTag::kPartitionAlloc:
      return "partition_alloc";
    case PageTag::kChromium:
      return "chromium";
    case PageTag::kV8:
      return "v8";
    default:
      DCHECK(false);
      return "";
  }
}
#endif  // defined(OS_ANDROID)

#if defined(OS_MACOSX)
// Tests whether the version of macOS supports the MAP_JIT flag and if the
// current process is signed with the allow-jit entitlement.
bool UseMapJit() {
  if (!mac::IsAtLeastOS10_14())
    return false;

  ScopedCFTypeRef<SecTaskRef> task(SecTaskCreateFromSelf(kCFAllocatorDefault));
  ScopedCFTypeRef<CFErrorRef> error;
  ScopedCFTypeRef<CFTypeRef> value(SecTaskCopyValueForEntitlement(
      task.get(), CFSTR("com.apple.security.cs.allow-jit"),
      error.InitializeInto()));
  if (error)
    return false;
  return mac::CFCast<CFBooleanRef>(value.get()) == kCFBooleanTrue;
}
#endif  // defined(OS_MACOSX)

}  // namespace

// |mmap| uses a nearby address if the hint address is blocked.
constexpr bool kHintIsAdvisory = true;
std::atomic<int32_t> s_allocPageErrorCode{0};

int GetAccessFlags(PageAccessibilityConfiguration accessibility) {
  switch (accessibility) {
    case PageRead:
      return PROT_READ;
    case PageReadWrite:
      return PROT_READ | PROT_WRITE;
    case PageReadExecute:
      return PROT_READ | PROT_EXEC;
    case PageReadWriteExecute:
      return PROT_READ | PROT_WRITE | PROT_EXEC;
    default:
      NOTREACHED();
      FALLTHROUGH;
    case PageInaccessible:
      return PROT_NONE;
  }
}

void* SystemAllocPagesInternal(void* hint,
                               size_t length,
                               PageAccessibilityConfiguration accessibility,
                               PageTag page_tag,
                               bool commit) {
#if defined(OS_MACOSX)
  // Use a custom tag to make it easier to distinguish Partition Alloc regions
  // in vmmap(1). Tags between 240-255 are supported.
  DCHECK_LE(PageTag::kFirst, page_tag);
  DCHECK_GE(PageTag::kLast, page_tag);
  int fd = VM_MAKE_TAG(static_cast<int>(page_tag));
#else
  int fd = -1;
#endif

  int access_flag = GetAccessFlags(accessibility);
  int map_flags = MAP_ANONYMOUS | MAP_PRIVATE;

#if defined(OS_MACOSX)
  // On macOS 10.14 and higher, executables that are code signed with the
  // "runtime" option cannot execute writable memory by default. They can opt
  // into this capability by specifying the "com.apple.security.cs.allow-jit"
  // code signing entitlement and allocating the region with the MAP_JIT flag.
  static const bool kUseMapJit = UseMapJit();
  if (page_tag == PageTag::kV8 && kUseMapJit) {
    map_flags |= MAP_JIT;
  }
#endif

  void* ret =
      mmap(hint, length, access_flag, map_flags, fd, 0);
  if (ret == MAP_FAILED) {
    s_allocPageErrorCode = errno;
    ret = nullptr;
  }

#if defined(OS_ANDROID)
  // On Android, anonymous mappings can have a name attached to them. This is
  // useful for debugging, and double-checking memory attribution.
  if (ret) {
    // No error checking on purpose, testing only.
    prctl(PR_SET_VMA, PR_SET_VMA_ANON_NAME, ret, length,
          PageTagToName(page_tag));
  }
#endif

  return ret;
}

void* TrimMappingInternal(void* base,
                          size_t base_length,
                          size_t trim_length,
                          PageAccessibilityConfiguration accessibility,
                          bool commit,
                          size_t pre_slack,
                          size_t post_slack) {
  void* ret = base;
  // We can resize the allocation run. Release unneeded memory before and after
  // the aligned range.
  if (pre_slack) {
    int res = munmap(base, pre_slack);
    CHECK(!res);
    ret = reinterpret_cast<char*>(base) + pre_slack;
  }
  if (post_slack) {
    int res = munmap(reinterpret_cast<char*>(ret) + trim_length, post_slack);
    CHECK(!res);
  }
  return ret;
}

bool TrySetSystemPagesAccessInternal(
    void* address,
    size_t length,
    PageAccessibilityConfiguration accessibility) {
  return 0 == mprotect(address, length, GetAccessFlags(accessibility));
}

void SetSystemPagesAccessInternal(
    void* address,
    size_t length,
    PageAccessibilityConfiguration accessibility) {
  CHECK_EQ(0, mprotect(address, length, GetAccessFlags(accessibility)));
}

void FreePagesInternal(void* address, size_t length) {
  CHECK(!munmap(address, length));
}

void DecommitSystemPagesInternal(void* address, size_t length) {
  // In POSIX, there is no decommit concept. Discarding is an effective way of
  // implementing the Windows semantics where the OS is allowed to not swap the
  // pages in the region.
  //
  // TODO(ajwong): Also explore setting PageInaccessible to make the protection
  // semantics consistent between Windows and POSIX. This might have a perf cost
  // though as both decommit and recommit would incur an extra syscall.
  // http://crbug.com/766882
  DiscardSystemPages(address, length);
}

bool RecommitSystemPagesInternal(void* address,
                                 size_t length,
                                 PageAccessibilityConfiguration accessibility) {
#if defined(OS_MACOSX)
  // On macOS, to update accounting, we need to make another syscall. For more
  // details, see https://crbug.com/823915.
  madvise(address, length, MADV_FREE_REUSE);
#endif

  // On POSIX systems, the caller need simply read the memory to recommit it.
  // This has the correct behavior because the API requires the permissions to
  // be the same as before decommitting and all configurations can read.
  return true;
}

void DiscardSystemPagesInternal(void* address, size_t length) {
#if defined(OS_MACOSX)
  int ret = madvise(address, length, MADV_FREE_REUSABLE);
  if (ret) {
    // MADV_FREE_REUSABLE sometimes fails, so fall back to MADV_DONTNEED.
    ret = madvise(address, length, MADV_DONTNEED);
  }
  CHECK(0 == ret);
#else
  // We have experimented with other flags, but with suboptimal results.
  //
  // MADV_FREE (Linux): Makes our memory measurements less predictable;
  // performance benefits unclear.
  //
  // Therefore, we just do the simple thing: MADV_DONTNEED.
  CHECK(!madvise(address, length, MADV_DONTNEED));
#endif
}

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_POSIX_H_
