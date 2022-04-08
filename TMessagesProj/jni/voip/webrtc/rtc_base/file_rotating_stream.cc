/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/file_rotating_stream.h"

#include <cstdio>
#include <string>
#include <utility>

#if defined(WEBRTC_WIN)
#include <windows.h>

#include "rtc_base/string_utils.h"
#else
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#endif  // WEBRTC_WIN

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

// Note: We use fprintf for logging in the write paths of this stream to avoid
// infinite loops when logging.

namespace rtc {

namespace {

const char kCallSessionLogPrefix[] = "webrtc_log";

std::string AddTrailingPathDelimiterIfNeeded(std::string directory);

// `dir` must have a trailing delimiter. `prefix` must not include wild card
// characters.
std::vector<std::string> GetFilesWithPrefix(const std::string& directory,
                                            const std::string& prefix);
bool DeleteFile(const std::string& file);
bool MoveFile(const std::string& old_file, const std::string& new_file);
bool IsFile(const std::string& file);
bool IsFolder(const std::string& file);
absl::optional<size_t> GetFileSize(const std::string& file);

#if defined(WEBRTC_WIN)

std::string AddTrailingPathDelimiterIfNeeded(std::string directory) {
  if (absl::EndsWith(directory, "\\")) {
    return directory;
  }
  return directory + "\\";
}

std::vector<std::string> GetFilesWithPrefix(const std::string& directory,
                                            const std::string& prefix) {
  RTC_DCHECK(absl::EndsWith(directory, "\\"));
  WIN32_FIND_DATAW data;
  HANDLE handle;
  handle = ::FindFirstFileW(ToUtf16(directory + prefix + '*').c_str(), &data);
  if (handle == INVALID_HANDLE_VALUE)
    return {};

  std::vector<std::string> file_list;
  do {
    file_list.emplace_back(directory + ToUtf8(data.cFileName));
  } while (::FindNextFileW(handle, &data) == TRUE);

  ::FindClose(handle);
  return file_list;
}

bool DeleteFile(const std::string& file) {
  return ::DeleteFileW(ToUtf16(file).c_str()) != 0;
}

bool MoveFile(const std::string& old_file, const std::string& new_file) {
  return ::MoveFileW(ToUtf16(old_file).c_str(), ToUtf16(new_file).c_str()) != 0;
}

bool IsFile(const std::string& file) {
  WIN32_FILE_ATTRIBUTE_DATA data = {0};
  if (0 == ::GetFileAttributesExW(ToUtf16(file).c_str(), GetFileExInfoStandard,
                                  &data))
    return false;
  return (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

bool IsFolder(const std::string& file) {
  WIN32_FILE_ATTRIBUTE_DATA data = {0};
  if (0 == ::GetFileAttributesExW(ToUtf16(file).c_str(), GetFileExInfoStandard,
                                  &data))
    return false;
  return (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) ==
         FILE_ATTRIBUTE_DIRECTORY;
}

absl::optional<size_t> GetFileSize(const std::string& file) {
  WIN32_FILE_ATTRIBUTE_DATA data = {0};
  if (::GetFileAttributesExW(ToUtf16(file).c_str(), GetFileExInfoStandard,
                             &data) == 0)
    return absl::nullopt;
  return data.nFileSizeLow;
}

#else  // defined(WEBRTC_WIN)

std::string AddTrailingPathDelimiterIfNeeded(std::string directory) {
  if (absl::EndsWith(directory, "/")) {
    return directory;
  }
  return directory + "/";
}

std::vector<std::string> GetFilesWithPrefix(const std::string& directory,
                                            const std::string& prefix) {
  RTC_DCHECK(absl::EndsWith(directory, "/"));
  DIR* dir = ::opendir(directory.c_str());
  if (dir == nullptr)
    return {};
  std::vector<std::string> file_list;
  for (struct dirent* dirent = ::readdir(dir); dirent;
       dirent = ::readdir(dir)) {
    std::string name = dirent->d_name;
    if (name.compare(0, prefix.size(), prefix) == 0) {
      file_list.emplace_back(directory + name);
    }
  }
  ::closedir(dir);
  return file_list;
}

bool DeleteFile(const std::string& file) {
  return ::unlink(file.c_str()) == 0;
}

bool MoveFile(const std::string& old_file, const std::string& new_file) {
  return ::rename(old_file.c_str(), new_file.c_str()) == 0;
}

bool IsFile(const std::string& file) {
  struct stat st;
  int res = ::stat(file.c_str(), &st);
  // Treat symlinks, named pipes, etc. all as files.
  return res == 0 && !S_ISDIR(st.st_mode);
}

bool IsFolder(const std::string& file) {
  struct stat st;
  int res = ::stat(file.c_str(), &st);
  return res == 0 && S_ISDIR(st.st_mode);
}

absl::optional<size_t> GetFileSize(const std::string& file) {
  struct stat st;
  if (::stat(file.c_str(), &st) != 0)
    return absl::nullopt;
  return st.st_size;
}

#endif

}  // namespace

FileRotatingStream::FileRotatingStream(const std::string& dir_path,
                                       const std::string& file_prefix,
                                       size_t max_file_size,
                                       size_t num_files)
    : dir_path_(AddTrailingPathDelimiterIfNeeded(dir_path)),
      file_prefix_(file_prefix),
      max_file_size_(max_file_size),
      current_file_index_(0),
      rotation_index_(0),
      current_bytes_written_(0),
      disable_buffering_(false) {
  RTC_DCHECK_GT(max_file_size, 0);
  RTC_DCHECK_GT(num_files, 1);
  RTC_DCHECK(IsFolder(dir_path));
  file_names_.clear();
  for (size_t i = 0; i < num_files; ++i) {
    file_names_.push_back(GetFilePath(i, num_files));
  }
  rotation_index_ = num_files - 1;
}

FileRotatingStream::~FileRotatingStream() {}

bool FileRotatingStream::IsOpen() const {
  return file_.is_open();
}

bool FileRotatingStream::Write(const void* data, size_t data_len) {
  if (!file_.is_open()) {
    std::fprintf(stderr, "Open() must be called before Write.\n");
    return false;
  }
  while (data_len > 0) {
    // Write as much as will fit in to the current file.
    RTC_DCHECK_LT(current_bytes_written_, max_file_size_);
    size_t remaining_bytes = max_file_size_ - current_bytes_written_;
    size_t write_length = std::min(data_len, remaining_bytes);

    if (!file_.Write(data, write_length)) {
      return false;
    }
    if (disable_buffering_ && !file_.Flush()) {
      return false;
    }

    current_bytes_written_ += write_length;

    // If we're done with this file, rotate it out.
    if (current_bytes_written_ >= max_file_size_) {
      RTC_DCHECK_EQ(current_bytes_written_, max_file_size_);
      RotateFiles();
    }
    data_len -= write_length;
    data =
        static_cast<const void*>(static_cast<const char*>(data) + write_length);
  }
  return true;
}

bool FileRotatingStream::Flush() {
  if (!file_.is_open()) {
    return false;
  }
  return file_.Flush();
}

void FileRotatingStream::Close() {
  CloseCurrentFile();
}

bool FileRotatingStream::Open() {
  // Delete existing files when opening for write.
  std::vector<std::string> matching_files =
      GetFilesWithPrefix(dir_path_, file_prefix_);
  for (const auto& matching_file : matching_files) {
    if (!DeleteFile(matching_file)) {
      std::fprintf(stderr, "Failed to delete: %s\n", matching_file.c_str());
    }
  }
  return OpenCurrentFile();
}

bool FileRotatingStream::DisableBuffering() {
  disable_buffering_ = true;
  return true;
}

std::string FileRotatingStream::GetFilePath(size_t index) const {
  RTC_DCHECK_LT(index, file_names_.size());
  return file_names_[index];
}

bool FileRotatingStream::OpenCurrentFile() {
  CloseCurrentFile();

  // Opens the appropriate file in the appropriate mode.
  RTC_DCHECK_LT(current_file_index_, file_names_.size());
  std::string file_path = file_names_[current_file_index_];

  // We should always be writing to the zero-th file.
  RTC_DCHECK_EQ(current_file_index_, 0);
  int error;
  file_ = webrtc::FileWrapper::OpenWriteOnly(file_path, &error);
  if (!file_.is_open()) {
    std::fprintf(stderr, "Failed to open: %s Error: %d\n", file_path.c_str(),
                 error);
    return false;
  }
  return true;
}

void FileRotatingStream::CloseCurrentFile() {
  if (!file_.is_open()) {
    return;
  }
  current_bytes_written_ = 0;
  file_.Close();
}

void FileRotatingStream::RotateFiles() {
  CloseCurrentFile();
  // Rotates the files by deleting the file at `rotation_index_`, which is the
  // oldest file and then renaming the newer files to have an incremented index.
  // See header file comments for example.
  RTC_DCHECK_LT(rotation_index_, file_names_.size());
  std::string file_to_delete = file_names_[rotation_index_];
  if (IsFile(file_to_delete)) {
    if (!DeleteFile(file_to_delete)) {
      std::fprintf(stderr, "Failed to delete: %s\n", file_to_delete.c_str());
    }
  }
  for (auto i = rotation_index_; i > 0; --i) {
    std::string rotated_name = file_names_[i];
    std::string unrotated_name = file_names_[i - 1];
    if (IsFile(unrotated_name)) {
      if (!MoveFile(unrotated_name, rotated_name)) {
        std::fprintf(stderr, "Failed to move: %s to %s\n",
                     unrotated_name.c_str(), rotated_name.c_str());
      }
    }
  }
  // Create a new file for 0th index.
  OpenCurrentFile();
  OnRotation();
}

std::string FileRotatingStream::GetFilePath(size_t index,
                                            size_t num_files) const {
  RTC_DCHECK_LT(index, num_files);

  const size_t buffer_size = 32;
  char file_postfix[buffer_size];
  // We want to zero pad the index so that it will sort nicely.
  const int max_digits = std::snprintf(nullptr, 0, "%zu", num_files - 1);
  RTC_DCHECK_LT(1 + max_digits, buffer_size);
  std::snprintf(file_postfix, buffer_size, "_%0*zu", max_digits, index);

  return dir_path_ + file_prefix_ + file_postfix;
}

CallSessionFileRotatingStream::CallSessionFileRotatingStream(
    const std::string& dir_path,
    size_t max_total_log_size)
    : FileRotatingStream(dir_path,
                         kCallSessionLogPrefix,
                         max_total_log_size / 2,
                         GetNumRotatingLogFiles(max_total_log_size) + 1),
      max_total_log_size_(max_total_log_size),
      num_rotations_(0) {
  RTC_DCHECK_GE(max_total_log_size, 4);
}

const size_t CallSessionFileRotatingStream::kRotatingLogFileDefaultSize =
    1024 * 1024;

void CallSessionFileRotatingStream::OnRotation() {
  ++num_rotations_;
  if (num_rotations_ == 1) {
    // On the first rotation adjust the max file size so subsequent files after
    // the first are smaller.
    SetMaxFileSize(GetRotatingLogSize(max_total_log_size_));
  } else if (num_rotations_ == (GetNumFiles() - 1)) {
    // On the next rotation the very first file is going to be deleted. Change
    // the rotation index so this doesn't happen.
    SetRotationIndex(GetRotationIndex() - 1);
  }
}

size_t CallSessionFileRotatingStream::GetRotatingLogSize(
    size_t max_total_log_size) {
  size_t num_rotating_log_files = GetNumRotatingLogFiles(max_total_log_size);
  size_t rotating_log_size = num_rotating_log_files > 2
                                 ? kRotatingLogFileDefaultSize
                                 : max_total_log_size / 4;
  return rotating_log_size;
}

size_t CallSessionFileRotatingStream::GetNumRotatingLogFiles(
    size_t max_total_log_size) {
  // At minimum have two rotating files. Otherwise split the available log size
  // evenly across 1MB files.
  return std::max((size_t)2,
                  (max_total_log_size / 2) / kRotatingLogFileDefaultSize);
}

FileRotatingStreamReader::FileRotatingStreamReader(
    const std::string& dir_path,
    const std::string& file_prefix) {
  file_names_ = GetFilesWithPrefix(AddTrailingPathDelimiterIfNeeded(dir_path),
                                   file_prefix);

  // Plain sort of the file names would sort by age, i.e., oldest last. Using
  // std::greater gives us the desired chronological older, oldest first.
  absl::c_sort(file_names_, std::greater<std::string>());
}

FileRotatingStreamReader::~FileRotatingStreamReader() = default;

size_t FileRotatingStreamReader::GetSize() const {
  size_t total_size = 0;
  for (const auto& file_name : file_names_) {
    total_size += GetFileSize(file_name).value_or(0);
  }
  return total_size;
}

size_t FileRotatingStreamReader::ReadAll(void* buffer, size_t size) const {
  size_t done = 0;
  for (const auto& file_name : file_names_) {
    if (done < size) {
      webrtc::FileWrapper f = webrtc::FileWrapper::OpenReadOnly(file_name);
      if (!f.is_open()) {
        break;
      }
      done += f.Read(static_cast<char*>(buffer) + done, size - done);
    } else {
      break;
    }
  }
  return done;
}

CallSessionFileRotatingStreamReader::CallSessionFileRotatingStreamReader(
    const std::string& dir_path)
    : FileRotatingStreamReader(dir_path, kCallSessionLogPrefix) {}

}  // namespace rtc
