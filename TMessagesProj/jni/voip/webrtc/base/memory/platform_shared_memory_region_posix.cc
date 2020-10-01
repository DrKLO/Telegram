// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/platform_shared_memory_region.h"

#include <fcntl.h>
#include <sys/mman.h>

#include "base/files/file.h"
#include "base/files/file_util.h"
#include "base/metrics/histogram_macros.h"
#include "base/threading/thread_restrictions.h"
#include "build/build_config.h"

namespace base {
namespace subtle {

namespace {

struct ScopedPathUnlinkerTraits {
  static const FilePath* InvalidValue() { return nullptr; }

  static void Free(const FilePath* path) {
    if (unlink(path->value().c_str()))
      PLOG(WARNING) << "unlink";
  }
};

// Unlinks the FilePath when the object is destroyed.
using ScopedPathUnlinker =
    ScopedGeneric<const FilePath*, ScopedPathUnlinkerTraits>;

#if !defined(OS_NACL)
bool CheckFDAccessMode(int fd, int expected_mode) {
  int fd_status = fcntl(fd, F_GETFL);
  if (fd_status == -1) {
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    PLOG(ERROR) << "fcntl(" << fd << ", F_GETFL) failed";
    return false;
  }

  int mode = fd_status & O_ACCMODE;
  if (mode != expected_mode) {
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    LOG(ERROR) << "Descriptor access mode (" << mode
               << ") differs from expected (" << expected_mode << ")";
    return false;
  }

  return true;
}
#endif  // !defined(OS_NACL)

}  // namespace

ScopedFDPair::ScopedFDPair() = default;

ScopedFDPair::ScopedFDPair(ScopedFDPair&&) = default;

ScopedFDPair& ScopedFDPair::operator=(ScopedFDPair&&) = default;

ScopedFDPair::~ScopedFDPair() = default;

ScopedFDPair::ScopedFDPair(ScopedFD in_fd, ScopedFD in_readonly_fd)
    : fd(std::move(in_fd)), readonly_fd(std::move(in_readonly_fd)) {}

FDPair ScopedFDPair::get() const {
  return {fd.get(), readonly_fd.get()};
}

#if defined(OS_LINUX)
// static
ScopedFD PlatformSharedMemoryRegion::ExecutableRegion::CreateFD(size_t size) {
  PlatformSharedMemoryRegion region =
      Create(Mode::kUnsafe, size, true /* executable */);
  if (region.IsValid())
    return region.PassPlatformHandle().fd;
  return ScopedFD();
}
#endif  // defined(OS_LINUX)

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Take(
    ScopedFDPair handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid) {
  if (!handle.fd.is_valid())
    return {};

  if (size == 0)
    return {};

  if (size > static_cast<size_t>(std::numeric_limits<int>::max()))
    return {};

  CHECK(
      CheckPlatformHandlePermissionsCorrespondToMode(handle.get(), mode, size));

  switch (mode) {
    case Mode::kReadOnly:
    case Mode::kUnsafe:
      if (handle.readonly_fd.is_valid()) {
        handle.readonly_fd.reset();
        DLOG(WARNING) << "Readonly handle shouldn't be valid for a "
                         "non-writable memory region; closing";
      }
      break;
    case Mode::kWritable:
      if (!handle.readonly_fd.is_valid()) {
        DLOG(ERROR)
            << "Readonly handle must be valid for writable memory region";
        return {};
      }
      break;
    default:
      DLOG(ERROR) << "Invalid permission mode: " << static_cast<int>(mode);
      return {};
  }

  return PlatformSharedMemoryRegion(std::move(handle), mode, size, guid);
}

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Take(
    ScopedFD handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid) {
  CHECK_NE(mode, Mode::kWritable);
  return Take(ScopedFDPair(std::move(handle), ScopedFD()), mode, size, guid);
}

FDPair PlatformSharedMemoryRegion::GetPlatformHandle() const {
  return handle_.get();
}

bool PlatformSharedMemoryRegion::IsValid() const {
  return handle_.fd.is_valid() &&
         (mode_ == Mode::kWritable ? handle_.readonly_fd.is_valid() : true);
}

PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Duplicate() const {
  if (!IsValid())
    return {};

  CHECK_NE(mode_, Mode::kWritable)
      << "Duplicating a writable shared memory region is prohibited";

  ScopedFD duped_fd(HANDLE_EINTR(dup(handle_.fd.get())));
  if (!duped_fd.is_valid()) {
    DPLOG(ERROR) << "dup(" << handle_.fd.get() << ") failed";
    return {};
  }

  return PlatformSharedMemoryRegion({std::move(duped_fd), ScopedFD()}, mode_,
                                    size_, guid_);
}

bool PlatformSharedMemoryRegion::ConvertToReadOnly() {
  if (!IsValid())
    return false;

  CHECK_EQ(mode_, Mode::kWritable)
      << "Only writable shared memory region can be converted to read-only";

  handle_.fd.reset(handle_.readonly_fd.release());
  mode_ = Mode::kReadOnly;
  return true;
}

bool PlatformSharedMemoryRegion::ConvertToUnsafe() {
  if (!IsValid())
    return false;

  CHECK_EQ(mode_, Mode::kWritable)
      << "Only writable shared memory region can be converted to unsafe";

  handle_.readonly_fd.reset();
  mode_ = Mode::kUnsafe;
  return true;
}

bool PlatformSharedMemoryRegion::MapAtInternal(off_t offset,
                                               size_t size,
                                               void** memory,
                                               size_t* mapped_size) const {
  bool write_allowed = mode_ != Mode::kReadOnly;
  *memory = mmap(nullptr, size, PROT_READ | (write_allowed ? PROT_WRITE : 0),
                 MAP_SHARED, handle_.fd.get(), offset);

  bool mmap_succeeded = *memory && *memory != MAP_FAILED;
  if (!mmap_succeeded) {
    DPLOG(ERROR) << "mmap " << handle_.fd.get() << " failed";
    return false;
  }

  *mapped_size = size;
  return true;
}

// static
PlatformSharedMemoryRegion PlatformSharedMemoryRegion::Create(Mode mode,
                                                              size_t size
#if defined(OS_LINUX)
                                                              ,
                                                              bool executable
#endif
) {
#if defined(OS_NACL)
  // Untrusted code can't create descriptors or handles.
  return {};
#else
  if (size == 0) {
    return {};
  }

  if (size > static_cast<size_t>(std::numeric_limits<int>::max())) {
    return {};
  }

  CHECK_NE(mode, Mode::kReadOnly) << "Creating a region in read-only mode will "
                                     "lead to this region being non-modifiable";

  // This function theoretically can block on the disk, but realistically
  // the temporary files we create will just go into the buffer cache
  // and be deleted before they ever make it out to disk.
  ThreadRestrictions::ScopedAllowIO allow_io;

  // We don't use shm_open() API in order to support the --disable-dev-shm-usage
  // flag.
  FilePath directory;
  if (!GetShmemTempDir(
#if defined(OS_LINUX)
          executable,
#else
          false /* executable */,
#endif
          &directory)) {
    return {};
  }

  FilePath path;
  ScopedFD fd = CreateAndOpenFdForTemporaryFileInDir(directory, &path);
  File shm_file(fd.release());

  if (!shm_file.IsValid()) {
    PLOG(ERROR) << "Creating shared memory in " << path.value() << " failed";
    FilePath dir = path.DirName();
    if (access(dir.value().c_str(), W_OK | X_OK) < 0) {
      PLOG(ERROR) << "Unable to access(W_OK|X_OK) " << dir.value();
      if (dir.value() == "/dev/shm") {
        LOG(FATAL) << "This is frequently caused by incorrect permissions on "
                   << "/dev/shm.  Try 'sudo chmod 1777 /dev/shm' to fix.";
      }
    }
    return {};
  }

  // Deleting the file prevents anyone else from mapping it in (making it
  // private), and prevents the need for cleanup (once the last fd is
  // closed, it is truly freed).
  ScopedPathUnlinker path_unlinker(&path);

  ScopedFD readonly_fd;
  if (mode == Mode::kWritable) {
    // Also open as readonly so that we can ConvertToReadOnly().
    readonly_fd.reset(HANDLE_EINTR(open(path.value().c_str(), O_RDONLY)));
    if (!readonly_fd.is_valid()) {
      DPLOG(ERROR) << "open(\"" << path.value() << "\", O_RDONLY) failed";
      return {};
    }
  }

  if (!AllocateFileRegion(&shm_file, 0, size)) {
    return {};
  }

  if (readonly_fd.is_valid()) {
    stat_wrapper_t shm_stat;
    if (File::Fstat(shm_file.GetPlatformFile(), &shm_stat) != 0) {
      DPLOG(ERROR) << "fstat(fd) failed";
      return {};
    }

    stat_wrapper_t readonly_stat;
    if (File::Fstat(readonly_fd.get(), &readonly_stat) != 0) {
      DPLOG(ERROR) << "fstat(readonly_fd) failed";
      return {};
    }

    if (shm_stat.st_dev != readonly_stat.st_dev ||
        shm_stat.st_ino != readonly_stat.st_ino) {
      LOG(ERROR) << "Writable and read-only inodes don't match; bailing";
      return {};
    }
  }

  return PlatformSharedMemoryRegion(
      {ScopedFD(shm_file.TakePlatformFile()), std::move(readonly_fd)}, mode,
      size, UnguessableToken::Create());
#endif  // !defined(OS_NACL)
}

bool PlatformSharedMemoryRegion::CheckPlatformHandlePermissionsCorrespondToMode(
    PlatformHandle handle,
    Mode mode,
    size_t size) {
#if !defined(OS_NACL)
  if (!CheckFDAccessMode(handle.fd,
                         mode == Mode::kReadOnly ? O_RDONLY : O_RDWR)) {
    return false;
  }

  if (mode == Mode::kWritable)
    return CheckFDAccessMode(handle.readonly_fd, O_RDONLY);

  // The second descriptor must be invalid in kReadOnly and kUnsafe modes.
  if (handle.readonly_fd != -1) {
    // TODO(crbug.com/838365): convert to DLOG when bug fixed.
    LOG(ERROR) << "The second descriptor must be invalid";
    return false;
  }

  return true;
#else
  // fcntl(_, F_GETFL) is not implemented on NaCl.
  // We also cannot try to mmap() a region as writable and look at the return
  // status because the plugin process crashes if system mmap() fails.
  return true;
#endif  // !defined(OS_NACL)
}

PlatformSharedMemoryRegion::PlatformSharedMemoryRegion(
    ScopedFDPair handle,
    Mode mode,
    size_t size,
    const UnguessableToken& guid)
    : handle_(std::move(handle)), mode_(mode), size_(size), guid_(guid) {}

}  // namespace subtle
}  // namespace base
