// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/file.h"

#include <io.h>
#include <stdint.h>

#include "base/logging.h"
#include "base/metrics/histogram_functions.h"
#include "base/strings/string_util.h"
#include "base/threading/scoped_blocking_call.h"

#include <windows.h>

namespace base {

// Make sure our Whence mappings match the system headers.
static_assert(File::FROM_BEGIN == FILE_BEGIN &&
                  File::FROM_CURRENT == FILE_CURRENT &&
                  File::FROM_END == FILE_END,
              "whence mapping must match the system headers");

bool File::IsValid() const {
  return file_.IsValid();
}

PlatformFile File::GetPlatformFile() const {
  return file_.Get();
}

PlatformFile File::TakePlatformFile() {
  return file_.Take();
}

void File::Close() {
  if (!file_.IsValid())
    return;

  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  SCOPED_FILE_TRACE("Close");
  file_.Close();
}

int64_t File::Seek(Whence whence, int64_t offset) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());

  SCOPED_FILE_TRACE_WITH_SIZE("Seek", offset);

  LARGE_INTEGER distance, res;
  distance.QuadPart = offset;
  DWORD move_method = static_cast<DWORD>(whence);
  if (!SetFilePointerEx(file_.Get(), distance, &res, move_method))
    return -1;
  return res.QuadPart;
}

int File::Read(int64_t offset, char* data, int size) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());
  DCHECK(!async_);
  if (size < 0)
    return -1;

  SCOPED_FILE_TRACE_WITH_SIZE("Read", size);

  LARGE_INTEGER offset_li;
  offset_li.QuadPart = offset;

  OVERLAPPED overlapped = {};
  overlapped.Offset = offset_li.LowPart;
  overlapped.OffsetHigh = offset_li.HighPart;

  DWORD bytes_read;
  if (::ReadFile(file_.Get(), data, size, &bytes_read, &overlapped))
    return bytes_read;
  if (ERROR_HANDLE_EOF == GetLastError())
    return 0;

  return -1;
}

int File::ReadAtCurrentPos(char* data, int size) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());
  DCHECK(!async_);
  if (size < 0)
    return -1;

  SCOPED_FILE_TRACE_WITH_SIZE("ReadAtCurrentPos", size);

  DWORD bytes_read;
  if (::ReadFile(file_.Get(), data, size, &bytes_read, NULL))
    return bytes_read;
  if (ERROR_HANDLE_EOF == GetLastError())
    return 0;

  return -1;
}

int File::ReadNoBestEffort(int64_t offset, char* data, int size) {
  // TODO(dbeam): trace this separately?
  return Read(offset, data, size);
}

int File::ReadAtCurrentPosNoBestEffort(char* data, int size) {
  // TODO(dbeam): trace this separately?
  return ReadAtCurrentPos(data, size);
}

int File::Write(int64_t offset, const char* data, int size) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());
  DCHECK(!async_);

  SCOPED_FILE_TRACE_WITH_SIZE("Write", size);

  LARGE_INTEGER offset_li;
  offset_li.QuadPart = offset;

  OVERLAPPED overlapped = {};
  overlapped.Offset = offset_li.LowPart;
  overlapped.OffsetHigh = offset_li.HighPart;

  DWORD bytes_written;
  if (::WriteFile(file_.Get(), data, size, &bytes_written, &overlapped))
    return bytes_written;

  return -1;
}

int File::WriteAtCurrentPos(const char* data, int size) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());
  DCHECK(!async_);
  if (size < 0)
    return -1;

  SCOPED_FILE_TRACE_WITH_SIZE("WriteAtCurrentPos", size);

  DWORD bytes_written;
  if (::WriteFile(file_.Get(), data, size, &bytes_written, NULL))
    return bytes_written;

  return -1;
}

int File::WriteAtCurrentPosNoBestEffort(const char* data, int size) {
  return WriteAtCurrentPos(data, size);
}

int64_t File::GetLength() {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());

  SCOPED_FILE_TRACE("GetLength");

  LARGE_INTEGER size;
  if (!::GetFileSizeEx(file_.Get(), &size))
    return -1;

  return static_cast<int64_t>(size.QuadPart);
}

bool File::SetLength(int64_t length) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());

  SCOPED_FILE_TRACE_WITH_SIZE("SetLength", length);

  // Get the current file pointer.
  LARGE_INTEGER file_pointer;
  LARGE_INTEGER zero;
  zero.QuadPart = 0;
  if (!::SetFilePointerEx(file_.Get(), zero, &file_pointer, FILE_CURRENT))
    return false;

  LARGE_INTEGER length_li;
  length_li.QuadPart = length;
  // If length > file size, SetFilePointerEx() should extend the file
  // with zeroes on all Windows standard file systems (NTFS, FATxx).
  if (!::SetFilePointerEx(file_.Get(), length_li, NULL, FILE_BEGIN))
    return false;

  // Set the new file length and move the file pointer to its old position.
  // This is consistent with ftruncate()'s behavior, even when the file
  // pointer points to a location beyond the end of the file.
  // TODO(rvargas): Emulating ftruncate details seem suspicious and it is not
  // promised by the interface (nor was promised by PlatformFile). See if this
  // implementation detail can be removed.
  return ((::SetEndOfFile(file_.Get()) != FALSE) &&
          (::SetFilePointerEx(file_.Get(), file_pointer, NULL, FILE_BEGIN) !=
           FALSE));
}

bool File::SetTimes(Time last_access_time, Time last_modified_time) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());

  SCOPED_FILE_TRACE("SetTimes");

  FILETIME last_access_filetime = last_access_time.ToFileTime();
  FILETIME last_modified_filetime = last_modified_time.ToFileTime();
  return (::SetFileTime(file_.Get(), NULL, &last_access_filetime,
                        &last_modified_filetime) != FALSE);
}

bool File::GetInfo(Info* info) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());

  SCOPED_FILE_TRACE("GetInfo");

  BY_HANDLE_FILE_INFORMATION file_info;
  if (!GetFileInformationByHandle(file_.Get(), &file_info))
    return false;

  LARGE_INTEGER size;
  size.HighPart = file_info.nFileSizeHigh;
  size.LowPart = file_info.nFileSizeLow;
  info->size = size.QuadPart;
  info->is_directory =
      (file_info.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
  info->is_symbolic_link = false;  // Windows doesn't have symbolic links.
  info->last_modified = Time::FromFileTime(file_info.ftLastWriteTime);
  info->last_accessed = Time::FromFileTime(file_info.ftLastAccessTime);
  info->creation_time = Time::FromFileTime(file_info.ftCreationTime);
  return true;
}

namespace {

DWORD LockFileFlagsForMode(File::LockMode mode) {
  DWORD flags = LOCKFILE_FAIL_IMMEDIATELY;
  switch (mode) {
    case File::LockMode::kShared:
      return flags;
    case File::LockMode::kExclusive:
      return flags | LOCKFILE_EXCLUSIVE_LOCK;
  }
  NOTREACHED();
}

}  // namespace

File::Error File::Lock(File::LockMode mode) {
  DCHECK(IsValid());

  SCOPED_FILE_TRACE("Lock");

  OVERLAPPED overlapped = {};
  BOOL result =
      LockFileEx(file_.Get(), LockFileFlagsForMode(mode), /*dwReserved=*/0,
                 /*nNumberOfBytesToLockLow=*/MAXDWORD,
                 /*nNumberOfBytesToLockHigh=*/MAXDWORD, &overlapped);
  if (!result)
    return GetLastFileError();
  return FILE_OK;
}

File::Error File::Unlock() {
  DCHECK(IsValid());

  SCOPED_FILE_TRACE("Unlock");

  OVERLAPPED overlapped = {};
  BOOL result =
      UnlockFileEx(file_.Get(), /*dwReserved=*/0,
                   /*nNumberOfBytesToLockLow=*/MAXDWORD,
                   /*nNumberOfBytesToLockHigh=*/MAXDWORD, &overlapped);
  if (!result)
    return GetLastFileError();
  return FILE_OK;
}

File File::Duplicate() const {
  if (!IsValid())
    return File();

  SCOPED_FILE_TRACE("Duplicate");

  HANDLE other_handle = nullptr;

  if (!::DuplicateHandle(GetCurrentProcess(),  // hSourceProcessHandle
                         GetPlatformFile(),
                         GetCurrentProcess(),  // hTargetProcessHandle
                         &other_handle,
                         0,  // dwDesiredAccess ignored due to SAME_ACCESS
                         FALSE,  // !bInheritHandle
                         DUPLICATE_SAME_ACCESS)) {
    return File(GetLastFileError());
  }

  return File(ScopedPlatformFile(other_handle), async());
}

bool File::DeleteOnClose(bool delete_on_close) {
  FILE_DISPOSITION_INFO disposition = {delete_on_close};
  return ::SetFileInformationByHandle(GetPlatformFile(), FileDispositionInfo,
                                      &disposition, sizeof(disposition)) != 0;
}

// Static.
File::Error File::OSErrorToFileError(DWORD last_error) {
  switch (last_error) {
    case ERROR_SHARING_VIOLATION:
      return FILE_ERROR_IN_USE;
    case ERROR_ALREADY_EXISTS:
    case ERROR_FILE_EXISTS:
      return FILE_ERROR_EXISTS;
    case ERROR_FILE_NOT_FOUND:
    case ERROR_PATH_NOT_FOUND:
      return FILE_ERROR_NOT_FOUND;
    case ERROR_ACCESS_DENIED:
      return FILE_ERROR_ACCESS_DENIED;
    case ERROR_TOO_MANY_OPEN_FILES:
      return FILE_ERROR_TOO_MANY_OPENED;
    case ERROR_OUTOFMEMORY:
    case ERROR_NOT_ENOUGH_MEMORY:
      return FILE_ERROR_NO_MEMORY;
    case ERROR_HANDLE_DISK_FULL:
    case ERROR_DISK_FULL:
    case ERROR_DISK_RESOURCES_EXHAUSTED:
      return FILE_ERROR_NO_SPACE;
    case ERROR_USER_MAPPED_FILE:
      return FILE_ERROR_INVALID_OPERATION;
    case ERROR_NOT_READY:
    case ERROR_SECTOR_NOT_FOUND:
    case ERROR_DEV_NOT_EXIST:
    case ERROR_IO_DEVICE:
    case ERROR_FILE_CORRUPT:
    case ERROR_DISK_CORRUPT:
      return FILE_ERROR_IO;
    default:
      UmaHistogramSparse("PlatformFile.UnknownErrors.Windows", last_error);
      // This function should only be called for errors.
      DCHECK_NE(static_cast<DWORD>(ERROR_SUCCESS), last_error);
      return FILE_ERROR_FAILED;
  }
}

void File::DoInitialize(const FilePath& path, uint32_t flags) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(!IsValid());

  DWORD disposition = 0;

  if (flags & FLAG_OPEN)
    disposition = OPEN_EXISTING;

  if (flags & FLAG_CREATE) {
    DCHECK(!disposition);
    disposition = CREATE_NEW;
  }

  if (flags & FLAG_OPEN_ALWAYS) {
    DCHECK(!disposition);
    disposition = OPEN_ALWAYS;
  }

  if (flags & FLAG_CREATE_ALWAYS) {
    DCHECK(!disposition);
    DCHECK(flags & FLAG_WRITE);
    disposition = CREATE_ALWAYS;
  }

  if (flags & FLAG_OPEN_TRUNCATED) {
    DCHECK(!disposition);
    DCHECK(flags & FLAG_WRITE);
    disposition = TRUNCATE_EXISTING;
  }

  if (!disposition) {
    ::SetLastError(ERROR_INVALID_PARAMETER);
    error_details_ = FILE_ERROR_FAILED;
    NOTREACHED();
    return;
  }

  DWORD access = 0;
  if (flags & FLAG_WRITE)
    access = GENERIC_WRITE;
  if (flags & FLAG_APPEND) {
    DCHECK(!access);
    access = FILE_APPEND_DATA;
  }
  if (flags & FLAG_READ)
    access |= GENERIC_READ;
  if (flags & FLAG_WRITE_ATTRIBUTES)
    access |= FILE_WRITE_ATTRIBUTES;
  if (flags & FLAG_EXECUTE)
    access |= GENERIC_EXECUTE;
  if (flags & FLAG_CAN_DELETE_ON_CLOSE)
    access |= DELETE;

  DWORD sharing = (flags & FLAG_EXCLUSIVE_READ) ? 0 : FILE_SHARE_READ;
  if (!(flags & FLAG_EXCLUSIVE_WRITE))
    sharing |= FILE_SHARE_WRITE;
  if (flags & FLAG_SHARE_DELETE)
    sharing |= FILE_SHARE_DELETE;

  DWORD create_flags = 0;
  if (flags & FLAG_ASYNC)
    create_flags |= FILE_FLAG_OVERLAPPED;
  if (flags & FLAG_TEMPORARY)
    create_flags |= FILE_ATTRIBUTE_TEMPORARY;
  if (flags & FLAG_HIDDEN)
    create_flags |= FILE_ATTRIBUTE_HIDDEN;
  if (flags & FLAG_DELETE_ON_CLOSE)
    create_flags |= FILE_FLAG_DELETE_ON_CLOSE;
  if (flags & FLAG_BACKUP_SEMANTICS)
    create_flags |= FILE_FLAG_BACKUP_SEMANTICS;
  if (flags & FLAG_SEQUENTIAL_SCAN)
    create_flags |= FILE_FLAG_SEQUENTIAL_SCAN;

  file_.Set(CreateFile(path.value().c_str(), access, sharing, NULL, disposition,
                       create_flags, NULL));

  if (file_.IsValid()) {
    error_details_ = FILE_OK;
    async_ = ((flags & FLAG_ASYNC) == FLAG_ASYNC);

    if (flags & (FLAG_OPEN_ALWAYS))
      created_ = (ERROR_ALREADY_EXISTS != GetLastError());
    else if (flags & (FLAG_CREATE_ALWAYS | FLAG_CREATE))
      created_ = true;
  } else {
    error_details_ = GetLastFileError();
  }
}

bool File::Flush() {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK(IsValid());
  SCOPED_FILE_TRACE("Flush");

  // On Windows 8 and above, FlushFileBuffers is guaranteed to flush the storage
  // device's internal buffers (if they exist) before returning.
  // https://blogs.msdn.microsoft.com/oldnewthing/20170510-00/?p=95505
  return ::FlushFileBuffers(file_.Get()) != FALSE;
}

void File::SetPlatformFile(PlatformFile file) {
  file_.Set(file);
}

// static
File::Error File::GetLastFileError() {
  return File::OSErrorToFileError(GetLastError());
}

}  // namespace base
