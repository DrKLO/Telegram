// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MEMORY_PLATFORM_SHARED_MEMORY_REGION_H_
#define BASE_MEMORY_PLATFORM_SHARED_MEMORY_REGION_H_

#include <utility>

#include "base/compiler_specific.h"
#include "base/gtest_prod_util.h"
#include "base/macros.h"
#include "base/unguessable_token.h"
#include "build/build_config.h"

#if defined(OS_MACOSX) && !defined(OS_IOS)
#include <mach/mach.h>
#include "base/mac/scoped_mach_port.h"
#elif defined(OS_FUCHSIA)
#include <lib/zx/vmo.h>
#elif defined(OS_WIN)
#include "base/win/scoped_handle.h"
#include "base/win/windows_types.h"
#elif defined(OS_POSIX)
#include <sys/types.h>
#include "base/file_descriptor_posix.h"
#include "base/files/scoped_file.h"
#endif

#if defined(OS_LINUX)
namespace content {
class SandboxIPCHandler;
}
#endif

namespace base {
namespace subtle {

#if defined(OS_POSIX) && (!defined(OS_MACOSX) || defined(OS_IOS)) && \
    !defined(OS_ANDROID)
// Helper structs to keep two descriptors on POSIX. It's needed to support
// ConvertToReadOnly().
struct BASE_EXPORT FDPair {
  // The main shared memory descriptor that is used for mapping. May be either
  // writable or read-only, depending on region's mode.
  int fd;
  // The read-only descriptor, valid only in kWritable mode. Replaces |fd| when
  // a region is converted to read-only.
  int readonly_fd;
};

struct BASE_EXPORT ScopedFDPair {
  ScopedFDPair();
  ScopedFDPair(ScopedFD in_fd, ScopedFD in_readonly_fd);
  ScopedFDPair(ScopedFDPair&&);
  ScopedFDPair& operator=(ScopedFDPair&&);
  ~ScopedFDPair();

  FDPair get() const;

  ScopedFD fd;
  ScopedFD readonly_fd;
};
#endif

// Implementation class for shared memory regions.
//
// This class does the following:
//
// - Wraps and owns a shared memory region platform handle.
// - Provides a way to allocate a new region of platform shared memory of given
//   size.
// - Provides a way to create mapping of the region in the current process'
//   address space, under special access-control constraints (see Mode).
// - Provides methods to help transferring the handle across process boundaries.
// - Holds a 128-bit unique identifier used to uniquely identify the same
//   kernel region resource across processes (used for memory tracking).
// - Has a method to retrieve the region's size in bytes.
//
// IMPORTANT NOTE: Users should never use this directly, but
// ReadOnlySharedMemoryRegion, WritableSharedMemoryRegion or
// UnsafeSharedMemoryRegion since this is an implementation class.
class BASE_EXPORT PlatformSharedMemoryRegion {
 public:
  // Permission mode of the platform handle. Each mode corresponds to one of the
  // typed shared memory classes:
  //
  // * ReadOnlySharedMemoryRegion: A region that can only create read-only
  // mappings.
  //
  // * WritableSharedMemoryRegion: A region that can only create writable
  // mappings. The region can be demoted to ReadOnlySharedMemoryRegion without
  // the possibility of promoting back to writable.
  //
  // * UnsafeSharedMemoryRegion: A region that can only create writable
  // mappings. The region cannot be demoted to ReadOnlySharedMemoryRegion.
  enum class Mode {
    kReadOnly,  // ReadOnlySharedMemoryRegion
    kWritable,  // WritableSharedMemoryRegion
    kUnsafe,    // UnsafeSharedMemoryRegion
    kMaxValue = kUnsafe
  };

  // Errors that can occur during Shared Memory construction.
  // These match tools/metrics/histograms/enums.xml.
  // This enum is append-only.
  enum class CreateError {
    SUCCESS = 0,
    SIZE_ZERO = 1,
    SIZE_TOO_LARGE = 2,
    INITIALIZE_ACL_FAILURE = 3,
    INITIALIZE_SECURITY_DESC_FAILURE = 4,
    SET_SECURITY_DESC_FAILURE = 5,
    CREATE_FILE_MAPPING_FAILURE = 6,
    REDUCE_PERMISSIONS_FAILURE = 7,
    ALREADY_EXISTS = 8,
    ALLOCATE_FILE_REGION_FAILURE = 9,
    FSTAT_FAILURE = 10,
    INODES_MISMATCH = 11,
    GET_SHMEM_TEMP_DIR_FAILURE = 12,
    kMaxValue = GET_SHMEM_TEMP_DIR_FAILURE
  };

#if defined(OS_LINUX)
  // Structure to limit access to executable region creation.
  struct ExecutableRegion {
   private:
    // Creates a new shared memory region the unsafe mode (writable and not and
    // convertible to read-only), and in addition marked executable. A ScopedFD
    // to this region is returned. Any any mapping will have to be done
    // manually, including setting executable permissions if necessary
    //
    // This is only used to support sandbox_ipc_linux.cc, and should not be used
    // anywhere else in chrome. This is restricted via AllowCreateExecutable.
    // TODO(crbug.com/982879): remove this when NaCl is unshipped.
    //
    // Returns an invalid ScopedFD if the call fails.
    static ScopedFD CreateFD(size_t size);

    friend class content::SandboxIPCHandler;
  };
#endif

// Platform-specific shared memory type used by this class.
#if defined(OS_MACOSX) && !defined(OS_IOS)
  using PlatformHandle = mach_port_t;
  using ScopedPlatformHandle = mac::ScopedMachSendRight;
#elif defined(OS_FUCHSIA)
  using PlatformHandle = zx::unowned_vmo;
  using ScopedPlatformHandle = zx::vmo;
#elif defined(OS_WIN)
  using PlatformHandle = HANDLE;
  using ScopedPlatformHandle = win::ScopedHandle;
#elif defined(OS_ANDROID)
  using PlatformHandle = int;
  using ScopedPlatformHandle = ScopedFD;
#else
  using PlatformHandle = FDPair;
  using ScopedPlatformHandle = ScopedFDPair;
#endif

  // The minimum alignment in bytes that any mapped address produced by Map()
  // and MapAt() is guaranteed to have.
  enum { kMapMinimumAlignment = 32 };

  // Creates a new PlatformSharedMemoryRegion with corresponding mode and size.
  // Creating in kReadOnly mode isn't supported because then there will be no
  // way to modify memory content.
  static PlatformSharedMemoryRegion CreateWritable(size_t size);
  static PlatformSharedMemoryRegion CreateUnsafe(size_t size);

  // Returns a new PlatformSharedMemoryRegion that takes ownership of the
  // |handle|. All parameters must be taken from another valid
  // PlatformSharedMemoryRegion instance, e.g. |size| must be equal to the
  // actual region size as allocated by the kernel.
  // Closes the |handle| and returns an invalid instance if passed parameters
  // are invalid.
  static PlatformSharedMemoryRegion Take(ScopedPlatformHandle handle,
                                         Mode mode,
                                         size_t size,
                                         const UnguessableToken& guid);
#if defined(OS_POSIX) && !defined(OS_ANDROID) && \
    !(defined(OS_MACOSX) && !defined(OS_IOS))
  // Specialized version of Take() for POSIX that takes only one file descriptor
  // instead of pair. Cannot be used with kWritable |mode|.
  static PlatformSharedMemoryRegion Take(ScopedFD handle,
                                         Mode mode,
                                         size_t size,
                                         const UnguessableToken& guid);
#endif

  // Default constructor initializes an invalid instance, i.e. an instance that
  // doesn't wrap any valid platform handle.
  PlatformSharedMemoryRegion();

  // Move operations are allowed.
  PlatformSharedMemoryRegion(PlatformSharedMemoryRegion&&);
  PlatformSharedMemoryRegion& operator=(PlatformSharedMemoryRegion&&);

  // Destructor closes the platform handle. Does nothing if the handle is
  // invalid.
  ~PlatformSharedMemoryRegion();

  // Passes ownership of the platform handle to the caller. The current instance
  // becomes invalid. It's the responsibility of the caller to close the
  // handle. If the current instance is invalid, ScopedPlatformHandle will also
  // be invalid.
  ScopedPlatformHandle PassPlatformHandle() WARN_UNUSED_RESULT;

  // Returns the platform handle. The current instance keeps ownership of this
  // handle.
  PlatformHandle GetPlatformHandle() const;

  // Whether the platform handle is valid.
  bool IsValid() const;

  // Duplicates the platform handle and creates a new PlatformSharedMemoryRegion
  // with the same |mode_|, |size_| and |guid_| that owns this handle. Returns
  // invalid region on failure, the current instance remains valid.
  // Can be called only in kReadOnly and kUnsafe modes, CHECK-fails if is
  // called in kWritable mode.
  PlatformSharedMemoryRegion Duplicate() const;

  // Converts the region to read-only. Returns whether the operation succeeded.
  // Makes the current instance invalid on failure. Can be called only in
  // kWritable mode, all other modes will CHECK-fail. The object will have
  // kReadOnly mode after this call on success.
  bool ConvertToReadOnly();
#if defined(OS_MACOSX) && !defined(OS_IOS)
  // Same as above, but |mapped_addr| is used as a hint to avoid additional
  // mapping of the memory object.
  // |mapped_addr| must be mapped location of |memory_object_|. If the location
  // is unknown, |mapped_addr| should be |nullptr|.
  bool ConvertToReadOnly(void* mapped_addr);
#endif  // defined(OS_MACOSX) && !defined(OS_IOS)

  // Converts the region to unsafe. Returns whether the operation succeeded.
  // Makes the current instance invalid on failure. Can be called only in
  // kWritable mode, all other modes will CHECK-fail. The object will have
  // kUnsafe mode after this call on success.
  bool ConvertToUnsafe();

  // Maps |size| bytes of the shared memory region starting with the given
  // |offset| into the caller's address space. |offset| must be aligned to value
  // of |SysInfo::VMAllocationGranularity()|. Fails if requested bytes are out
  // of the region limits.
  // Returns true and sets |memory| and |mapped_size| on success, returns false
  // and leaves output parameters in unspecified state otherwise. The mapped
  // address is guaranteed to have an alignment of at least
  // |kMapMinimumAlignment|.
  bool MapAt(off_t offset,
             size_t size,
             void** memory,
             size_t* mapped_size) const;

  const UnguessableToken& GetGUID() const { return guid_; }

  size_t GetSize() const { return size_; }

  Mode GetMode() const { return mode_; }

 private:
  FRIEND_TEST_ALL_PREFIXES(PlatformSharedMemoryRegionTest,
                           CreateReadOnlyRegionDeathTest);
  FRIEND_TEST_ALL_PREFIXES(PlatformSharedMemoryRegionTest,
                           CheckPlatformHandlePermissionsCorrespondToMode);
  static PlatformSharedMemoryRegion Create(Mode mode,
                                           size_t size
#if defined(OS_LINUX)
                                           ,
                                           bool executable = false
#endif
  );

  static bool CheckPlatformHandlePermissionsCorrespondToMode(
      PlatformHandle handle,
      Mode mode,
      size_t size);

  PlatformSharedMemoryRegion(ScopedPlatformHandle handle,
                             Mode mode,
                             size_t size,
                             const UnguessableToken& guid);

  bool MapAtInternal(off_t offset,
                     size_t size,
                     void** memory,
                     size_t* mapped_size) const;

  ScopedPlatformHandle handle_;
  Mode mode_ = Mode::kReadOnly;
  size_t size_ = 0;
  UnguessableToken guid_;

  DISALLOW_COPY_AND_ASSIGN(PlatformSharedMemoryRegion);
};

}  // namespace subtle
}  // namespace base

#endif  // BASE_MEMORY_PLATFORM_SHARED_MEMORY_REGION_H_
