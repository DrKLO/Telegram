// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <memory>

#include "base/files/file_path_watcher.h"
#include "base/files/file_path_watcher_kqueue.h"
#include "base/macros.h"
#include "base/memory/ptr_util.h"
#include "build/build_config.h"

#if !defined(OS_IOS)
#include "base/files/file_path_watcher_fsevents.h"
#endif

namespace base {

namespace {

class FilePathWatcherImpl : public FilePathWatcher::PlatformDelegate {
 public:
  FilePathWatcherImpl() = default;
  ~FilePathWatcherImpl() override = default;

  bool Watch(const FilePath& path,
             bool recursive,
             const FilePathWatcher::Callback& callback) override {
    // Use kqueue for non-recursive watches and FSEvents for recursive ones.
    DCHECK(!impl_.get());
    if (recursive) {
      if (!FilePathWatcher::RecursiveWatchAvailable())
        return false;
#if !defined(OS_IOS)
      impl_ = std::make_unique<FilePathWatcherFSEvents>();
#endif  // OS_IOS
    } else {
      impl_ = std::make_unique<FilePathWatcherKQueue>();
    }
    DCHECK(impl_.get());
    return impl_->Watch(path, recursive, callback);
  }

  void Cancel() override {
    if (impl_.get())
      impl_->Cancel();
    set_cancelled();
  }

 private:
  std::unique_ptr<PlatformDelegate> impl_;

  DISALLOW_COPY_AND_ASSIGN(FilePathWatcherImpl);
};

}  // namespace

FilePathWatcher::FilePathWatcher() {
  sequence_checker_.DetachFromSequence();
  impl_ = std::make_unique<FilePathWatcherImpl>();
}

}  // namespace base
