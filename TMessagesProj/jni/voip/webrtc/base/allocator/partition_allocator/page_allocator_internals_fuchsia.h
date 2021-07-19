// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
//
// This file implements memory allocation primitives for PageAllocator using
// Fuchsia's VMOs (Virtual Memory Objects). VMO API is documented in
// https://fuchsia.dev/fuchsia-src/zircon/objects/vm_object . A VMO is a kernel
// object that corresponds to a set of memory pages. VMO pages may be mapped
// to an address space. The code below creates VMOs for each memory allocations
// and maps them to the default address space of the current process.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_FUCHSIA_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_FUCHSIA_H_

#include <lib/zx/vmar.h>
#include <lib/zx/vmo.h>

#include "base/allocator/partition_allocator/page_allocator.h"
#include "base/fuchsia/fuchsia_logging.h"
#include "base/logging.h"

namespace base {

namespace {

// Returns VMO name for a PageTag.
const char* PageTagToName(PageTag tag) {
  switch (tag) {
    case PageTag::kBlinkGC:
      return "cr_blink_gc";
    case PageTag::kPartitionAlloc:
      return "cr_partition_alloc";
    case PageTag::kChromium:
      return "cr_chromium";
    case PageTag::kV8:
      return "cr_v8";
    default:
      DCHECK(false);
      return "";
  }
}

zx_vm_option_t PageAccessibilityToZxVmOptions(
    PageAccessibilityConfiguration accessibility) {
  switch (accessibility) {
    case PageRead:
      return ZX_VM_PERM_READ;
    case PageReadWrite:
      return ZX_VM_PERM_READ | ZX_VM_PERM_WRITE;
    case PageReadExecute:
      return ZX_VM_PERM_READ | ZX_VM_PERM_EXECUTE;
    case PageReadWriteExecute:
      return ZX_VM_PERM_READ | ZX_VM_PERM_WRITE | ZX_VM_PERM_EXECUTE;
    default:
      NOTREACHED();
      FALLTHROUGH;
    case PageInaccessible:
      return 0;
  }
}

}  // namespace

// zx_vmar_map() will fail if the VMO cannot be mapped at |vmar_offset|, i.e.
// |hint| is not advisory.
constexpr bool kHintIsAdvisory = false;

std::atomic<int32_t> s_allocPageErrorCode{0};

void* SystemAllocPagesInternal(void* hint,
                               size_t length,
                               PageAccessibilityConfiguration accessibility,
                               PageTag page_tag,
                               bool commit) {
  zx::vmo vmo;
  zx_status_t status = zx::vmo::create(length, 0, &vmo);
  if (status != ZX_OK) {
    ZX_DLOG(INFO, status) << "zx_vmo_create";
    return nullptr;
  }

  const char* vmo_name = PageTagToName(page_tag);
  status = vmo.set_property(ZX_PROP_NAME, vmo_name, strlen(vmo_name));

  // VMO names are used only for debugging, so failure to set a name is not
  // fatal.
  ZX_DCHECK(status == ZX_OK, status);

  if (page_tag == PageTag::kV8) {
    // V8 uses JIT. Call zx_vmo_replace_as_executable() to allow code execution
    // in the new VMO.
    status = vmo.replace_as_executable(zx::resource(), &vmo);
    if (status != ZX_OK) {
      ZX_DLOG(INFO, status) << "zx_vmo_replace_as_executable";
      return nullptr;
    }
  }

  zx_vm_option_t options = PageAccessibilityToZxVmOptions(accessibility);

  uint64_t vmar_offset = 0;
  if (hint) {
    vmar_offset = reinterpret_cast<uint64_t>(hint);
    options |= ZX_VM_SPECIFIC;
  }

  uint64_t address;
  status =
      zx::vmar::root_self()->map(vmar_offset, vmo,
                                 /*vmo_offset=*/0, length, options, &address);
  if (status != ZX_OK) {
    // map() is expected to fail if |hint| is set to an already-in-use location.
    if (!hint) {
      ZX_DLOG(ERROR, status) << "zx_vmar_map";
    }
    return nullptr;
  }

  return reinterpret_cast<void*>(address);
}

void* TrimMappingInternal(void* base,
                          size_t base_length,
                          size_t trim_length,
                          PageAccessibilityConfiguration accessibility,
                          bool commit,
                          size_t pre_slack,
                          size_t post_slack) {
  DCHECK_EQ(base_length, trim_length + pre_slack + post_slack);

  uint64_t base_address = reinterpret_cast<uint64_t>(base);

  // Unmap head if necessary.
  if (pre_slack) {
    zx_status_t status = zx::vmar::root_self()->unmap(base_address, pre_slack);
    ZX_CHECK(status == ZX_OK, status);
  }

  // Unmap tail if necessary.
  if (post_slack) {
    zx_status_t status = zx::vmar::root_self()->unmap(
        base_address + pre_slack + trim_length, post_slack);
    ZX_CHECK(status == ZX_OK, status);
  }

  return reinterpret_cast<void*>(base_address + pre_slack);
}

bool TrySetSystemPagesAccessInternal(
    void* address,
    size_t length,
    PageAccessibilityConfiguration accessibility) {
  zx_status_t status = zx::vmar::root_self()->protect(
      reinterpret_cast<uint64_t>(address), length,
      PageAccessibilityToZxVmOptions(accessibility));
  return status == ZX_OK;
}

void SetSystemPagesAccessInternal(
    void* address,
    size_t length,
    PageAccessibilityConfiguration accessibility) {
  zx_status_t status = zx::vmar::root_self()->protect(
      reinterpret_cast<uint64_t>(address), length,
      PageAccessibilityToZxVmOptions(accessibility));
  ZX_CHECK(status == ZX_OK, status);
}

void FreePagesInternal(void* address, size_t length) {
  uint64_t address_int = reinterpret_cast<uint64_t>(address);
  zx_status_t status = zx::vmar::root_self()->unmap(address_int, length);
  ZX_CHECK(status == ZX_OK, status);
}

void DiscardSystemPagesInternal(void* address, size_t length) {
  // TODO(https://crbug.com/1022062): Mark pages as discardable, rather than
  // forcibly de-committing them immediately, when Fuchsia supports it.
  uint64_t address_int = reinterpret_cast<uint64_t>(address);
  zx_status_t status = zx::vmar::root_self()->op_range(
      ZX_VMO_OP_DECOMMIT, address_int, length, nullptr, 0);
  ZX_CHECK(status == ZX_OK, status);
}

void DecommitSystemPagesInternal(void* address, size_t length) {
  // TODO(https://crbug.com/1022062): Review whether this implementation is
  // still appropriate once DiscardSystemPagesInternal() migrates to a "lazy"
  // discardable API.
  DiscardSystemPagesInternal(address, length);

  SetSystemPagesAccessInternal(address, length, PageInaccessible);
}

bool RecommitSystemPagesInternal(void* address,
                                 size_t length,
                                 PageAccessibilityConfiguration accessibility) {
  SetSystemPagesAccessInternal(address, length, accessibility);
  return true;
}

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PAGE_ALLOCATOR_INTERNALS_FUCHSIA_H_
