// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_FILES_FILE_ENUMERATOR_H_
#define BASE_FILES_FILE_ENUMERATOR_H_

#include <stddef.h>
#include <stdint.h>

#include <vector>

#include "base/base_export.h"
#include "base/containers/stack.h"
#include "base/files/file.h"
#include "base/files/file_path.h"
#include "base/macros.h"
#include "base/optional.h"
#include "base/time/time.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <unistd.h>
#include <unordered_set>

#include "base/files/file.h"
#endif

namespace base {

// A class for enumerating the files in a provided path. The order of the
// results is not guaranteed.
//
// This is blocking. Do not use on critical threads.
//
// Example:
//
//   base::FileEnumerator enum(my_dir, false, base::FileEnumerator::FILES,
//                             FILE_PATH_LITERAL("*.txt"));
//   for (base::FilePath name = enum.Next(); !name.empty(); name = enum.Next())
//     ...
class BASE_EXPORT FileEnumerator {
 public:
  // Note: copy & assign supported.
  class BASE_EXPORT FileInfo {
   public:
    FileInfo();
    ~FileInfo();

    bool IsDirectory() const;

    // The name of the file. This will not include any path information. This
    // is in constrast to the value returned by FileEnumerator.Next() which
    // includes the |root_path| passed into the FileEnumerator constructor.
    FilePath GetName() const;

    int64_t GetSize() const;
    Time GetLastModifiedTime() const;

#if defined(OS_WIN)
    // Note that the cAlternateFileName (used to hold the "short" 8.3 name)
    // of the WIN32_FIND_DATA will be empty. Since we don't use short file
    // names, we tell Windows to omit it which speeds up the query slightly.
    const WIN32_FIND_DATA& find_data() const { return find_data_; }
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
    const stat_wrapper_t& stat() const { return stat_; }
#endif

   private:
    friend class FileEnumerator;

#if defined(OS_WIN)
    WIN32_FIND_DATA find_data_;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
    stat_wrapper_t stat_;
    FilePath filename_;
#endif
  };

  enum FileType {
    FILES = 1 << 0,
    DIRECTORIES = 1 << 1,
    INCLUDE_DOT_DOT = 1 << 2,
#if defined(OS_POSIX) || defined(OS_FUCHSIA)
    SHOW_SYM_LINKS = 1 << 4,
#endif
  };

  // Search policy for intermediate folders.
  enum class FolderSearchPolicy {
    // Recursive search will pass through folders whose names match the
    // pattern. Inside each one, all files will be returned. Folders with names
    // that do not match the pattern will be ignored within their interior.
    MATCH_ONLY,
    // Recursive search will pass through every folder and perform pattern
    // matching inside each one.
    ALL,
  };

  // Determines how a FileEnumerator handles errors encountered during
  // enumeration. When no ErrorPolicy is explicitly set, FileEnumerator defaults
  // to IGNORE_ERRORS.
  enum class ErrorPolicy {
    // Errors are ignored if possible and FileEnumerator returns as many files
    // as it is able to enumerate.
    IGNORE_ERRORS,

    // Any error encountered during enumeration will terminate the enumeration
    // immediately. An error code indicating the nature of a failure can be
    // retrieved from |GetError()|.
    STOP_ENUMERATION,
  };

  // |root_path| is the starting directory to search for. It may or may not end
  // in a slash.
  //
  // If |recursive| is true, this will enumerate all matches in any
  // subdirectories matched as well. It does a breadth-first search, so all
  // files in one directory will be returned before any files in a
  // subdirectory.
  //
  // |file_type|, a bit mask of FileType, specifies whether the enumerator
  // should match files, directories, or both.
  //
  // |pattern| is an optional pattern for which files to match. This
  // works like shell globbing. For example, "*.txt" or "Foo???.doc".
  // However, be careful in specifying patterns that aren't cross platform
  // since the underlying code uses OS-specific matching routines.  In general,
  // Windows matching is less featureful than others, so test there first.
  // If unspecified, this will match all files.
  FileEnumerator(const FilePath& root_path, bool recursive, int file_type);
  FileEnumerator(const FilePath& root_path,
                 bool recursive,
                 int file_type,
                 const FilePath::StringType& pattern);
  FileEnumerator(const FilePath& root_path,
                 bool recursive,
                 int file_type,
                 const FilePath::StringType& pattern,
                 FolderSearchPolicy folder_search_policy);
  FileEnumerator(const FilePath& root_path,
                 bool recursive,
                 int file_type,
                 const FilePath::StringType& pattern,
                 FolderSearchPolicy folder_search_policy,
                 ErrorPolicy error_policy);
  ~FileEnumerator();

  // Returns the next file or an empty string if there are no more results.
  //
  // The returned path will incorporate the |root_path| passed in the
  // constructor: "<root_path>/file_name.txt". If the |root_path| is absolute,
  // then so will be the result of Next().
  FilePath Next();

  // Write the file info into |info|.
  FileInfo GetInfo() const;

  // Once |Next()| returns an empty path, enumeration has been terminated. If
  // termination was normal (i.e. no more results to enumerate) or ErrorPolicy
  // is set to IGNORE_ERRORS, this returns FILE_OK. Otherwise it returns an
  // error code reflecting why enumeration was stopped early.
  File::Error GetError() const { return error_; }

 private:
  // Returns true if the given path should be skipped in enumeration.
  bool ShouldSkip(const FilePath& path);

  bool IsTypeMatched(bool is_dir) const;

  bool IsPatternMatched(const FilePath& src) const;

#if defined(OS_WIN)
  // True when find_data_ is valid.
  bool has_find_data_ = false;
  WIN32_FIND_DATA find_data_;
  HANDLE find_handle_ = INVALID_HANDLE_VALUE;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  // The files in the current directory
  std::vector<FileInfo> directory_entries_;

  // Set of visited directories. Used to prevent infinite looping along
  // circular symlinks.
  std::unordered_set<ino_t> visited_directories_;

  // The next entry to use from the directory_entries_ vector
  size_t current_directory_entry_;
#endif
  FilePath root_path_;
  const bool recursive_;
  const int file_type_;
  FilePath::StringType pattern_;
  const FolderSearchPolicy folder_search_policy_;
  const ErrorPolicy error_policy_;
  File::Error error_ = File::FILE_OK;

  // A stack that keeps track of which subdirectories we still need to
  // enumerate in the breadth-first search.
  base::stack<FilePath> pending_paths_;

  DISALLOW_COPY_AND_ASSIGN(FileEnumerator);
};

}  // namespace base

#endif  // BASE_FILES_FILE_ENUMERATOR_H_
