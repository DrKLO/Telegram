// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/platform_shared_memory_region.h"

#include <lib/zx/vmar.h>
#include <zircon/process.h>
#include <zircon/rights.h>

#include "base/bits.h"
#include "base/fuchsia/fuchsia_logging.h"
#include "base/process/process_metrics.h"

namespace base {
namespace subtle {

static constexpr int kNoWriteOrExec =
    ZX_DEFAULT_VMO_RIGHTS &
    ~(ZX_RIGHT_WRITE | ZX_RIGHT_EXECUTE | ZX_RIGHT_SET_PROPERTY);

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Take(
    zx::vmo handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid) {
  if (!handle.is_valid())
    return {};

  if (size == 0)
    return {};

  if (size > static_cast<size_t>(std::numeric_limits<int>::max()))
    return {};

  CHECK(CheckPlatformHandlePermissionsCorrespondToMode(zx::unowned_vmo(handle),
                                                       mode, size));

  return PlatformSharedMemoryRegion(std::move(handle), mode, size, guid);
}

zx::unowned_vmo PlatformSharedMemoryRegion::GetPlatformHandle() const {
  return zx::unowned_vmo(handle_);
}

bool PlatformSharedMemoryRegion::IsValid() const {
  return handle_.is_valid();
}

PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Duplicate() const {
  if (!IsValid())
    return {};

  CHECK_NE(mode_, Mode::kWritable)
      << "Duplicating a writable shared memory region is prohibited";

  zx::vmo duped_handle;
  zx_status_t status = handle_.duplicate(ZX_RIGHT_SAME_RIGHTS, &duped_handle);
  if (status != ZX_OK) {
    ZX_DLOG(ERROR, status) << "zx_handle_duplicate";
    return {};
  }

  return PlatformSharedMemoryRegion(std::move(duped_handle), mode_, size_,
                                    guid_);
}

bool PlatformSharedMemoryRegion::ConvertToReadOnly() {
  if (!IsValid())
    return false;

  CHECK_EQ(mode_, Mode::kWritable)
      << "Only writable shared memory region can be converted to read-only";

  zx_status_t status = handle_.replace(kNoWriteOrExec, &handle_);
  if (status != ZX_OK) {
    ZX_DLOG(ERROR, status) << "zx_handle_replace";
    return false;
  }

  mode_ = Mode::kReadOnly;
  return true;
}

bool PlatformSharedMemoryRegion::ConvertToUnsafe() {
  if (!IsValid())
    return false;

  CHECK_EQ(mode_, Mode::kWritable)
      << "Only writable shared memory region can be converted to unsafe";

  mode_ = Mode::kUnsafe;
  return true;
}

bool PlatformSharedMemoryRegion::MapAtInternal(off_t offset,
                                               size_t size,
                                               void** memory,
                                               size_t* mapped_size) const {
  uintptr_t addr;
  zx_vm_option_t options = ZX_VM_REQUIRE_NON_RESIZABLE | ZX_VM_PERM_READ;
  if (mode_ != Mode::kReadOnly)
    options |= ZX_VM_PERM_WRITE;
  zx_status_t status = zx::vmar::root_self()->map(
      /*vmar_offset=*/0, handle_, offset, size, options, &addr);
  if (status != ZX_OK) {
    ZX_DLOG(ERROR, status) << "zx_vmar_map";
    return false;
  }

  *memory = reinterpret_cast<void*>(addr);
  *mapped_size = size;
  return true;
}

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Create(Mode mode,
                                                              size_t size) {
  if (size == 0)
    return {};

  // Aligning may overflow so check that the result doesn't decrease.
  size_t rounded_size = bits::Align(size, GetPageSize());
  if (rounded_size < size ||
      rounded_size > static_cast<size_t>(std::numeric_limits<int>::max())) {
    return {};
  }

  CHECK_NE(mode, Mode::kReadOnly) << "Creating a region in read-only mode will "
                                     "lead to this region being non-modifiable";

  zx::vmo vmo;
  zx_status_t status = zx::vmo::create(rounded_size, 0, &vmo);
  if (status != ZX_OK) {
    ZX_DLOG(ERROR, status) << "zx_vmo_create";
    return {};
  }

  // TODO(crbug.com/991805): Take base::Location from the caller and use it to
  // generate the name here.
  constexpr char kVmoName[] = "cr-shared-memory-region";
  status = vmo.set_property(ZX_PROP_NAME, kVmoName, strlen(kVmoName));
  ZX_DCHECK(status == ZX_OK, status);

  const int kNoExecFlags = ZX_DEFAULT_VMO_RIGHTS & ~ZX_RIGHT_EXECUTE;
  status = vmo.replace(kNoExecFlags, &vmo);
  if (status != ZX_OK) {
    ZX_DLOG(ERROR, status) << "zx_handle_replace";
    return {};
  }

  return PlatformSharedMemoryRegion(std::move(vmo), mode, size,
                                    UnguessableToken::Create());
}

// static
bool PlatformSharedMemoryRegion::CheckPlatformHandlePermissionsCorrespondToMode(
    PlatformHandle handle,
    Mode mode,
    size_t size) {
  zx_info_handle_basic_t basic = {};
  zx_status_t status = handle->get_info(ZX_INFO_HANDLE_BASIC, &basic,
                                        sizeof(basic), nullptr, nullptr);
  if (status != ZX_OK) {
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    ZX_LOG(ERROR, status) << "zx_object_get_info";
    return false;
  }

  bool is_read_only = (basic.rights & kNoWriteOrExec) == basic.rights;
  bool expected_read_only = mode == Mode::kReadOnly;

  if (is_read_only != expected_read_only) {
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    LOG(ERROR) << "VMO object has wrong access rights: it is"
               << (is_read_only ? " " : " not ") << "read-only but it should"
               << (expected_read_only ? " " : " not ") << "be";
    return false;
  }

  return true;
}

PlatformSharedMemoryRegion::PlatformSharedMemoryRegion(
    zx::vmo handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid)
    : handle_(std::move(handle)), mode_(mode), size_(size), guid_(guid) {}

}  // namespace subtle
}  // namespace base
