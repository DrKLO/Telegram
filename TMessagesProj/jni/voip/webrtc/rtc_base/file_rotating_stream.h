/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_FILE_ROTATING_STREAM_H_
#define RTC_BASE_FILE_ROTATING_STREAM_H_

#include <stddef.h>

#include <memory>
#include <string>
#include <vector>

#include "rtc_base/constructor_magic.h"
#include "rtc_base/stream.h"
#include "rtc_base/system/file_wrapper.h"

namespace rtc {

// FileRotatingStream writes to a file in the directory specified in the
// constructor. It rotates the files once the current file is full. The
// individual file size and the number of files used is configurable in the
// constructor. Open() must be called before using this stream.
class FileRotatingStream : public StreamInterface {
 public:
  // Use this constructor for reading a directory previously written to with
  // this stream.
  FileRotatingStream(const std::string& dir_path,
                     const std::string& file_prefix);

  // Use this constructor for writing to a directory. Files in the directory
  // matching the prefix will be deleted on open.
  FileRotatingStream(const std::string& dir_path,
                     const std::string& file_prefix,
                     size_t max_file_size,
                     size_t num_files);

  ~FileRotatingStream() override;

  // StreamInterface methods.
  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t buffer_len,
                    size_t* read,
                    int* error) override;
  StreamResult Write(const void* data,
                     size_t data_len,
                     size_t* written,
                     int* error) override;
  bool Flush() override;
  void Close() override;

  // Opens the appropriate file(s). Call this before using the stream.
  bool Open();

  // Disabling buffering causes writes to block until disk is updated. This is
  // enabled by default for performance.
  bool DisableBuffering();

  // Returns the path used for the i-th newest file, where the 0th file is the
  // newest file. The file may or may not exist, this is just used for
  // formatting. Index must be less than GetNumFiles().
  std::string GetFilePath(size_t index) const;

  // Returns the number of files that will used by this stream.
  size_t GetNumFiles() const { return file_names_.size(); }

 protected:
  size_t GetMaxFileSize() const { return max_file_size_; }

  void SetMaxFileSize(size_t size) { max_file_size_ = size; }

  size_t GetRotationIndex() const { return rotation_index_; }

  void SetRotationIndex(size_t index) { rotation_index_ = index; }

  virtual void OnRotation() {}

 private:
  bool OpenCurrentFile();
  void CloseCurrentFile();

  // Rotates the files by creating a new current file, renaming the
  // existing files, and deleting the oldest one. e.g.
  // file_0 -> file_1
  // file_1 -> file_2
  // file_2 -> delete
  // create new file_0
  void RotateFiles();

  // Private version of GetFilePath.
  std::string GetFilePath(size_t index, size_t num_files) const;

  const std::string dir_path_;
  const std::string file_prefix_;

  // File we're currently writing to.
  webrtc::FileWrapper file_;
  // Convenience storage for file names so we don't generate them over and over.
  std::vector<std::string> file_names_;
  size_t max_file_size_;
  size_t current_file_index_;
  // The rotation index indicates the index of the file that will be
  // deleted first on rotation. Indices lower than this index will be rotated.
  size_t rotation_index_;
  // Number of bytes written to current file. We need this because with
  // buffering the file size read from disk might not be accurate.
  size_t current_bytes_written_;
  bool disable_buffering_;

  RTC_DISALLOW_COPY_AND_ASSIGN(FileRotatingStream);
};

// CallSessionFileRotatingStream is meant to be used in situations where we will
// have limited disk space. Its purpose is to write logs up to a
// maximum size. Once the maximum size is exceeded, logs from the middle are
// deleted whereas logs from the beginning and end are preserved. The reason for
// this is because we anticipate that in WebRTC the beginning and end of the
// logs are most useful for call diagnostics.
//
// This implementation simply writes to a single file until
// |max_total_log_size| / 2 bytes are written to it, and subsequently writes to
// a set of rotating files. We do this by inheriting FileRotatingStream and
// setting the appropriate internal variables so that we don't delete the last
// (earliest) file on rotate, and that that file's size is bigger.
//
// Open() must be called before using this stream.

// To read the logs produced by this class, one can use the companion class
// CallSessionFileRotatingStreamReader.
class CallSessionFileRotatingStream : public FileRotatingStream {
 public:
  // Use this constructor for writing to a directory. Files in the directory
  // matching what's used by the stream will be deleted. |max_total_log_size|
  // must be at least 4.
  CallSessionFileRotatingStream(const std::string& dir_path,
                                size_t max_total_log_size);
  ~CallSessionFileRotatingStream() override {}

 protected:
  void OnRotation() override;

 private:
  static size_t GetRotatingLogSize(size_t max_total_log_size);
  static size_t GetNumRotatingLogFiles(size_t max_total_log_size);
  static const size_t kRotatingLogFileDefaultSize;

  const size_t max_total_log_size_;
  size_t num_rotations_;

  RTC_DISALLOW_COPY_AND_ASSIGN(CallSessionFileRotatingStream);
};

// This is a convenience class, to read all files produced by a
// FileRotatingStream, all in one go. Typical use calls GetSize and ReadData
// only once. The list of file names to read is based on the contents of the log
// directory at construction time.
class FileRotatingStreamReader {
 public:
  FileRotatingStreamReader(const std::string& dir_path,
                           const std::string& file_prefix);
  ~FileRotatingStreamReader();
  size_t GetSize() const;
  size_t ReadAll(void* buffer, size_t size) const;

 private:
  std::vector<std::string> file_names_;
};

class CallSessionFileRotatingStreamReader : public FileRotatingStreamReader {
 public:
  CallSessionFileRotatingStreamReader(const std::string& dir_path);
};

}  // namespace rtc

#endif  // RTC_BASE_FILE_ROTATING_STREAM_H_
