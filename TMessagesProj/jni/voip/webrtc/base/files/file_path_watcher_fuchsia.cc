// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/file_path_watcher.h"

#include "base/macros.h"
#include "base/memory/ptr_util.h"
#include "base/threading/sequenced_task_runner_handle.h"

namespace base {

namespace {

class FilePathWatcherImpl : public FilePathWatcher::PlatformDelegate {
 public:
  FilePathWatcherImpl() {}
  ~FilePathWatcherImpl() override {}

  bool Watch(const FilePath& path,
             bool recursive,
             const FilePathWatcher::Callback& callback) override;

  void Cancel() override;

 private:
  FilePathWatcher::Callback callback_;
  FilePath target_;

  DISALLOW_COPY_AND_ASSIGN(FilePathWatcherImpl);
};

bool FilePathWatcherImpl::Watch(const FilePath& path,
                                bool recursive,
                                const FilePathWatcher::Callback& callback) {
  DCHECK(!callback.is_null());
  DCHECK(callback_.is_null());

  callback_ = callback;
  NOTIMPLEMENTED();
  return false;
}

void FilePathWatcherImpl::Cancel() {
  NOTIMPLEMENTED();
}

}  // namespace

FilePathWatcher::FilePathWatcher() {
  sequence_checker_.DetachFromSequence();
  impl_ = std::make_unique<FilePathWatcherImpl>();
}

}  // namespace base
