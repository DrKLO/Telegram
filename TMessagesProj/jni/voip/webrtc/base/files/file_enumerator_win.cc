// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/file_enumerator.h"

#include <stdint.h>
#include <string.h>

#include "base/logging.h"
#include "base/strings/string_util.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/win/shlwapi.h"

namespace base {

namespace {

FilePath BuildSearchFilter(FileEnumerator::FolderSearchPolicy policy,
                           const FilePath& root_path,
                           const FilePath::StringType& pattern) {
  // MATCH_ONLY policy filters incoming files by pattern on OS side. ALL policy
  // collects all files and filters them manually.
  switch (policy) {
    case FileEnumerator::FolderSearchPolicy::MATCH_ONLY:
      return root_path.Append(pattern);
    case FileEnumerator::FolderSearchPolicy::ALL:
      return root_path.Append(FILE_PATH_LITERAL("*"));
  }
  NOTREACHED();
  return {};
}

}  // namespace

// FileEnumerator::FileInfo ----------------------------------------------------

FileEnumerator::FileInfo::FileInfo() {
  memset(&find_data_, 0, sizeof(find_data_));
}

bool FileEnumerator::FileInfo::IsDirectory() const {
  return (find_data_.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
}

FilePath FileEnumerator::FileInfo::GetName() const {
  return FilePath(find_data_.cFileName);
}

int64_t FileEnumerator::FileInfo::GetSize() const {
  ULARGE_INTEGER size;
  size.HighPart = find_data_.nFileSizeHigh;
  size.LowPart = find_data_.nFileSizeLow;
  DCHECK_LE(size.QuadPart,
            static_cast<ULONGLONG>(std::numeric_limits<int64_t>::max()));
  return static_cast<int64_t>(size.QuadPart);
}

Time FileEnumerator::FileInfo::GetLastModifiedTime() const {
  return Time::FromFileTime(find_data_.ftLastWriteTime);
}

// FileEnumerator --------------------------------------------------------------

FileEnumerator::FileEnumerator(const FilePath& root_path,
                               bool recursive,
                               int file_type)
    : FileEnumerator(root_path,
                     recursive,
                     file_type,
                     FilePath::StringType(),
                     FolderSearchPolicy::MATCH_ONLY) {}

FileEnumerator::FileEnumerator(const FilePath& root_path,
                               bool recursive,
                               int file_type,
                               const FilePath::StringType& pattern)
    : FileEnumerator(root_path,
                     recursive,
                     file_type,
                     pattern,
                     FolderSearchPolicy::MATCH_ONLY) {}

FileEnumerator::FileEnumerator(const FilePath& root_path,
                               bool recursive,
                               int file_type,
                               const FilePath::StringType& pattern,
                               FolderSearchPolicy folder_search_policy)
    : FileEnumerator(root_path,
                     recursive,
                     file_type,
                     pattern,
                     folder_search_policy,
                     ErrorPolicy::IGNORE_ERRORS) {}

FileEnumerator::FileEnumerator(const FilePath& root_path,
                               bool recursive,
                               int file_type,
                               const FilePath::StringType& pattern,
                               FolderSearchPolicy folder_search_policy,
                               ErrorPolicy error_policy)
    : recursive_(recursive),
      file_type_(file_type),
      pattern_(!pattern.empty() ? pattern : FILE_PATH_LITERAL("*")),
      folder_search_policy_(folder_search_policy),
      error_policy_(error_policy) {
  // INCLUDE_DOT_DOT must not be specified if recursive.
  DCHECK(!(recursive && (INCLUDE_DOT_DOT & file_type_)));
  memset(&find_data_, 0, sizeof(find_data_));
  pending_paths_.push(root_path);
}

FileEnumerator::~FileEnumerator() {
  if (find_handle_ != INVALID_HANDLE_VALUE)
    FindClose(find_handle_);
}

FileEnumerator::FileInfo FileEnumerator::GetInfo() const {
  if (!has_find_data_) {
    NOTREACHED();
    return FileInfo();
  }
  FileInfo ret;
  memcpy(&ret.find_data_, &find_data_, sizeof(find_data_));
  return ret;
}

FilePath FileEnumerator::Next() {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);

  while (has_find_data_ || !pending_paths_.empty()) {
    if (!has_find_data_) {
      // The last find FindFirstFile operation is done, prepare a new one.
      root_path_ = pending_paths_.top();
      pending_paths_.pop();

      // Start a new find operation.
      const FilePath src =
          BuildSearchFilter(folder_search_policy_, root_path_, pattern_);
      find_handle_ = FindFirstFileEx(src.value().c_str(),
                                     FindExInfoBasic,  // Omit short name.
                                     &find_data_, FindExSearchNameMatch,
                                     nullptr, FIND_FIRST_EX_LARGE_FETCH);
      has_find_data_ = true;
    } else {
      // Search for the next file/directory.
      if (!FindNextFile(find_handle_, &find_data_)) {
        FindClose(find_handle_);
        find_handle_ = INVALID_HANDLE_VALUE;
      }
    }

    DWORD last_error = GetLastError();
    if (INVALID_HANDLE_VALUE == find_handle_) {
      has_find_data_ = false;

      // MATCH_ONLY policy clears pattern for matched subfolders. ALL policy
      // applies pattern for all subfolders.
      if (folder_search_policy_ == FolderSearchPolicy::MATCH_ONLY) {
        // This is reached when we have finished a directory and are advancing
        // to the next one in the queue. We applied the pattern (if any) to the
        // files in the root search directory, but for those directories which
        // were matched, we want to enumerate all files inside them. This will
        // happen when the handle is empty.
        pattern_ = FILE_PATH_LITERAL("*");
      }

      if (last_error == ERROR_NO_MORE_FILES ||
          error_policy_ == ErrorPolicy::IGNORE_ERRORS) {
        continue;
      }

      error_ = File::OSErrorToFileError(last_error);
      return FilePath();
    }

    const FilePath filename(find_data_.cFileName);
    if (ShouldSkip(filename))
      continue;

    const bool is_dir =
        (find_data_.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
    const FilePath abs_path = root_path_.Append(filename);

    // Check if directory should be processed recursive.
    if (is_dir && recursive_) {
      // If |cur_file| is a directory, and we are doing recursive searching,
      // add it to pending_paths_ so we scan it after we finish scanning this
      // directory. However, don't do recursion through reparse points or we
      // may end up with an infinite cycle.
      DWORD attributes = GetFileAttributes(abs_path.value().c_str());
      if (!(attributes & FILE_ATTRIBUTE_REPARSE_POINT))
        pending_paths_.push(abs_path);
    }

    if (IsTypeMatched(is_dir) && IsPatternMatched(filename))
      return abs_path;
  }
  return FilePath();
}

bool FileEnumerator::IsPatternMatched(const FilePath& src) const {
  switch (folder_search_policy_) {
    case FolderSearchPolicy::MATCH_ONLY:
      // MATCH_ONLY policy filters by pattern on search request, so all found
      // files already fits to pattern.
      return true;
    case FolderSearchPolicy::ALL:
      // ALL policy enumerates all files, we need to check pattern match
      // manually.
      return PathMatchSpec(src.value().c_str(), pattern_.c_str()) == TRUE;
  }
  NOTREACHED();
  return false;
}

}  // namespace base
