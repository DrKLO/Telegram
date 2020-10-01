// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/base_paths.h"

#include <stdlib.h>

#include "base/base_paths_fuchsia.h"
#include "base/command_line.h"
#include "base/files/file_util.h"
#include "base/fuchsia/file_utils.h"
#include "base/path_service.h"
#include "base/process/process.h"

namespace base {

bool PathProviderFuchsia(int key, FilePath* result) {
  switch (key) {
    case FILE_MODULE:
      NOTIMPLEMENTED_LOG_ONCE() << " for FILE_MODULE.";
      return false;
    case FILE_EXE:
      *result = CommandLine::ForCurrentProcess()->GetProgram();
      return true;
    case DIR_APP_DATA:
    case DIR_CACHE:
      *result = base::FilePath(base::fuchsia::kPersistedDataDirectoryPath);
      return true;
    case DIR_ASSETS:
    case DIR_SOURCE_ROOT:
      *result = base::FilePath(base::fuchsia::kPackageRootDirectoryPath);
      return true;
  }
  return false;
}

}  // namespace base
