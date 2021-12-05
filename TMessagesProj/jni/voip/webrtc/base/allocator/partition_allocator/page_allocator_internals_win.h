// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_WIN_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_WIN_H_

#include "base/allocator/partition_allocator/oom.h"
#include "base/allocator/partition_allocator/page_allocator_internal.h"
#include "base/logging.h"

namespace base {

// |VirtualAlloc| will fail if allocation at the hint address is blocked.
constexpr bool kHintIsAdvisory = false;
std::atomic<int32_t> s_allocPageErrorCode{ERROR_SUCCESS};

int GetAccessFlags(PageAccessibilityConfiguration accessibility) {
  switch (accessibility) {
    case PageRead:
      return PAGE_READONLY;
    case PageReadWrite:
      return PAGE_READWRITE;
    case PageReadExecute:
      return PAGE_EXECUTE_READ;
    case PageReadWriteExecute:
      return PAGE_EXECUTE_READWRITE;
    default:
      NOTREACHED();
      FALLTHROUGH;
    case PageInaccessible:
      return PAGE_NOACCESS;
  }
}

void* SystemAllocPagesInternal(void* hint,
                               size_t length,
                               PageAccessibilityConfiguration accessibility,
                               PageTag page_tag,
                               bool commit) {
  DWORD access_flag = GetAccessFlags(accessibility);
  const DWORD type_flags = commit ? (MEM_RESERVE | MEM_COMMIT) : MEM_RESERVE;
  void* ret = VirtualAlloc(hint, length, type_flags, access_flag);
  if (ret == nullptr) {
    s_allocPageErrorCode = GetLastError();
  }
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
  if (pre_slack || post_slack) {
    // We cannot resize the allocation run. Free it and retry at the aligned
    // address within the freed range.
    ret = reinterpret_cast<char*>(base) + pre_slack;
    FreePages(base, base_length);
    ret = SystemAllocPages(ret, trim_length, accessibility, PageTag::kChromium,
                           commit);
  }
  return ret;
}

bool TrySetSystemPagesAccessInternal(
    void* address,
    size_t length,
    PageAccessibilityConfiguration accessibility) {
  if (accessibility == PageInaccessible)
    return VirtualFree(address, length, MEM_DECOMMIT) != 0;
  return nullptr != VirtualAlloc(address, length, MEM_COMMIT,
                                 GetAccessFlags(accessibility));
}

void SetSystemPagesAccessInternal(
    void* address,
    size_t length,
    PageAccessibilityConfiguration accessibility) {
  if (accessibility == PageInaccessible) {
    if (!VirtualFree(address, length, MEM_DECOMMIT)) {
      // We check `GetLastError` for `ERROR_SUCCESS` here so that in a crash
      // report we get the error number.
      CHECK_EQ(static_cast<uint32_t>(ERROR_SUCCESS), GetLastError());
    }
  } else {
    if (!VirtualAlloc(address, length, MEM_COMMIT,
                      GetAccessFlags(accessibility))) {
      int32_t error = GetLastError();
      if (error == ERROR_COMMITMENT_LIMIT)
        OOM_CRASH(length);
      // We check `GetLastError` for `ERROR_SUCCESS` here so that in a crash
      // report we get the error number.
      CHECK_EQ(ERROR_SUCCESS, error);
    }
  }
}

void FreePagesInternal(void* address, size_t length) {
  CHECK(VirtualFree(address, 0, MEM_RELEASE));
}

void DecommitSystemPagesInternal(void* address, size_t length) {
  SetSystemPagesAccess(address, length, PageInaccessible);
}

bool RecommitSystemPagesInternal(void* address,
                                 size_t length,
                                 PageAccessibilityConfiguration accessibility) {
  return TrySetSystemPagesAccess(address, length, accessibility);
}

void DiscardSystemPagesInternal(void* address, size_t length) {
  // On Windows, discarded pages are not returned to the system immediately and
  // not guaranteed to be zeroed when returned to the application.
  using DiscardVirtualMemoryFunction =
      DWORD(WINAPI*)(PVOID virtualAddress, SIZE_T size);
  static DiscardVirtualMemoryFunction discard_virtual_memory =
      reinterpret_cast<DiscardVirtualMemoryFunction>(-1);
  if (discard_virtual_memory ==
      reinterpret_cast<DiscardVirtualMemoryFunction>(-1))
    discard_virtual_memory =
        reinterpret_cast<DiscardVirtualMemoryFunction>(GetProcAddress(
            GetModuleHandle(L"Kernel32.dll"), "DiscardVirtualMemory"));
  // Use DiscardVirtualMemory when available because it releases faster than
  // MEM_RESET.
  DWORD ret = 1;
  if (discard_virtual_memory) {
    ret = discard_virtual_memory(address, length);
  }
  // DiscardVirtualMemory is buggy in Win10 SP0, so fall back to MEM_RESET on
  // failure.
  if (ret) {
    void* ptr = VirtualAlloc(address, length, MEM_RESET, PAGE_READWRITE);
    CHECK(ptr);
  }
}

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_WIN_H_
