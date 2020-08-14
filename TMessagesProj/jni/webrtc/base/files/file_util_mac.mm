// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/file_util.h"

#import <Foundation/Foundation.h>
#include <copyfile.h>
#include <stdlib.h>
#include <string.h>

#include "base/files/file_path.h"
#include "base/logging.h"
#include "base/mac/foundation_util.h"
#include "base/strings/string_util.h"
#include "base/threading/scoped_blocking_call.h"

namespace base {

bool CopyFile(const FilePath& from_path, const FilePath& to_path) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  if (from_path.ReferencesParent() || to_path.ReferencesParent())
    return false;
  return (copyfile(from_path.value().c_str(),
                   to_path.value().c_str(), NULL, COPYFILE_DATA) == 0);
}

bool GetTempDir(base::FilePath* path) {
  // In order to facilitate hermetic runs on macOS, first check
  // $MAC_CHROMIUM_TMPDIR. We check this instead of $TMPDIR because external
  // programs currently set $TMPDIR with no effect, but when we respect it
  // directly it can cause crashes (like crbug.com/698759).
  const char* env_tmpdir = getenv("MAC_CHROMIUM_TMPDIR");
  if (env_tmpdir) {
    DCHECK_LT(strlen(env_tmpdir), 50u)
        << "too-long TMPDIR causes socket name length issues.";
    *path = base::FilePath(env_tmpdir);
    return true;
  }

  // If we didn't find it, fall back to the native function.
  NSString* tmp = NSTemporaryDirectory();
  if (tmp == nil)
    return false;
  *path = base::mac::NSStringToFilePath(tmp);
  return true;
}

FilePath GetHomeDir() {
  NSString* tmp = NSHomeDirectory();
  if (tmp != nil) {
    FilePath mac_home_dir = base::mac::NSStringToFilePath(tmp);
    if (!mac_home_dir.empty())
      return mac_home_dir;
  }

  // Fall back on temp dir if no home directory is defined.
  FilePath rv;
  if (GetTempDir(&rv))
    return rv;

  // Last resort.
  return FilePath("/tmp");
}

}  // namespace base
