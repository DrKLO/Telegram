// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/platform_shared_memory_region.h"

#include <mach/mach_vm.h>

#include "base/mac/mach_logging.h"
#include "base/mac/scoped_mach_vm.h"
#include "base/metrics/histogram_functions.h"
#include "base/metrics/histogram_macros.h"
#include "build/build_config.h"

#if defined(OS_IOS)
#error "MacOS only - iOS uses platform_shared_memory_region_posix.cc"
#endif

namespace base {
namespace subtle {

namespace {

}  // namespace

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Take(
    mac::ScopedMachSendRight handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid) {
  if (!handle.is_valid())
    return {};

  if (size == 0)
    return {};

  if (size > static_cast<size_t>(std::numeric_limits<int>::max()))
    return {};

  CHECK(
      CheckPlatformHandlePermissionsCorrespondToMode(handle.get(), mode, size));

  return PlatformSharedMemoryRegion(std::move(handle), mode, size, guid);
}

mach_port_t PlatformSharedMemoryRegion::GetPlatformHandle() const {
  return handle_.get();
}

bool PlatformSharedMemoryRegion::IsValid() const {
  return handle_.is_valid();
}

PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Duplicate() const {
  if (!IsValid())
    return {};

  CHECK_NE(mode_, Mode::kWritable)
      << "Duplicating a writable shared memory region is prohibited";

  // Increment the ref count.
  kern_return_t kr = mach_port_mod_refs(mach_task_self(), handle_.get(),
                                        MACH_PORT_RIGHT_SEND, 1);
  if (kr != KERN_SUCCESS) {
    MACH_DLOG(ERROR, kr) << "mach_port_mod_refs";
    return {};
  }

  return PlatformSharedMemoryRegion(mac::ScopedMachSendRight(handle_.get()),
                                    mode_, size_, guid_);
}

bool PlatformSharedMemoryRegion::ConvertToReadOnly() {
  return ConvertToReadOnly(nullptr);
}

bool PlatformSharedMemoryRegion::ConvertToReadOnly(void* mapped_addr) {
  if (!IsValid())
    return false;

  CHECK_EQ(mode_, Mode::kWritable)
      << "Only writable shared memory region can be converted to read-only";

  mac::ScopedMachSendRight handle_copy(handle_.release());

  void* temp_addr = mapped_addr;
  mac::ScopedMachVM scoped_memory;
  if (!temp_addr) {
    // Intentionally lower current prot and max prot to |VM_PROT_READ|.
    kern_return_t kr = mach_vm_map(
        mach_task_self(), reinterpret_cast<mach_vm_address_t*>(&temp_addr),
        size_, 0, VM_FLAGS_ANYWHERE, handle_copy.get(), 0, FALSE, VM_PROT_READ,
        VM_PROT_READ, VM_INHERIT_NONE);
    if (kr != KERN_SUCCESS) {
      MACH_DLOG(ERROR, kr) << "mach_vm_map";
      return false;
    }
    scoped_memory.reset(reinterpret_cast<vm_address_t>(temp_addr),
                        mach_vm_round_page(size_));
  }

  // Make new memory object.
  memory_object_size_t allocation_size = size_;
  mac::ScopedMachSendRight named_right;
  kern_return_t kr = mach_make_memory_entry_64(
      mach_task_self(), &allocation_size,
      reinterpret_cast<memory_object_offset_t>(temp_addr), VM_PROT_READ,
      mac::ScopedMachSendRight::Receiver(named_right).get(), MACH_PORT_NULL);
  if (kr != KERN_SUCCESS) {
    MACH_DLOG(ERROR, kr) << "mach_make_memory_entry_64";
    return false;
  }
  DCHECK_GE(allocation_size, size_);

  handle_ = std::move(named_right);
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
  bool write_allowed = mode_ != Mode::kReadOnly;
  vm_prot_t vm_prot_write = write_allowed ? VM_PROT_WRITE : 0;
  kern_return_t kr = mach_vm_map(
      mach_task_self(),
      reinterpret_cast<mach_vm_address_t*>(memory),  // Output parameter
      size,
      0,  // Alignment mask
      VM_FLAGS_ANYWHERE, handle_.get(), offset,
      FALSE,                         // Copy
      VM_PROT_READ | vm_prot_write,  // Current protection
      VM_PROT_READ | vm_prot_write,  // Maximum protection
      VM_INHERIT_NONE);
  if (kr != KERN_SUCCESS) {
    MACH_DLOG(ERROR, kr) << "mach_vm_map";
    return false;
  }

  *mapped_size = size;
  return true;
}

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Create(Mode mode,
                                                              size_t size) {
  if (size == 0) {
    return {};
  }

  if (size > static_cast<size_t>(std::numeric_limits<int>::max())) {
    return {};
  }

  CHECK_NE(mode, Mode::kReadOnly) << "Creating a region in read-only mode will "
                                     "lead to this region being non-modifiable";

  mach_vm_size_t vm_size = size;
  mac::ScopedMachSendRight named_right;
  kern_return_t kr = mach_make_memory_entry_64(
      mach_task_self(), &vm_size,
      0,  // Address.
      MAP_MEM_NAMED_CREATE | VM_PROT_READ | VM_PROT_WRITE,
      mac::ScopedMachSendRight::Receiver(named_right).get(),
      MACH_PORT_NULL);  // Parent handle.
  // Crash as soon as shm allocation fails to debug the issue
  // https://crbug.com/872237.
  MACH_CHECK(kr == KERN_SUCCESS, kr) << "mach_make_memory_entry_64";
  DCHECK_GE(vm_size, size);

  return PlatformSharedMemoryRegion(std::move(named_right), mode, size,
                                    UnguessableToken::Create());
}

// static
bool PlatformSharedMemoryRegion::CheckPlatformHandlePermissionsCorrespondToMode(
    PlatformHandle handle,
    Mode mode,
    size_t size) {
  mach_vm_address_t temp_addr = 0;
  kern_return_t kr =
      mach_vm_map(mach_task_self(), &temp_addr, size, 0, VM_FLAGS_ANYWHERE,
                  handle, 0, FALSE, VM_PROT_READ | VM_PROT_WRITE,
                  VM_PROT_READ | VM_PROT_WRITE, VM_INHERIT_NONE);
  if (kr == KERN_SUCCESS) {
    kern_return_t kr_deallocate =
        mach_vm_deallocate(mach_task_self(), temp_addr, size);
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    MACH_LOG_IF(ERROR, kr_deallocate != KERN_SUCCESS, kr_deallocate)
        << "mach_vm_deallocate";
  } else if (kr != KERN_INVALID_RIGHT) {
    MACH_LOG(ERROR, kr) << "mach_vm_map";
    return false;
  }

  bool is_read_only = kr == KERN_INVALID_RIGHT;
  bool expected_read_only = mode == Mode::kReadOnly;

  if (is_read_only != expected_read_only) {
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    LOG(ERROR) << "VM region has a wrong protection mask: it is"
               << (is_read_only ? " " : " not ") << "read-only but it should"
               << (expected_read_only ? " " : " not ") << "be";
    return false;
  }

  return true;
}

PlatformSharedMemoryRegion::PlatformSharedMemoryRegion(
    mac::ScopedMachSendRight handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid)
    : handle_(std::move(handle)), mode_(mode), size_(size), guid_(guid) {}

}  // namespace subtle
}  // namespace base
