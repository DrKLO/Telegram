// Copyright (c) 2006-2008 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/base_paths.h"

#include "base/files/file_path.h"
#include "base/files/file_util.h"
#include "base/path_service.h"

namespace base {

bool PathProvider(int key, FilePath* result) {
  // NOTE: DIR_CURRENT is a special case in PathService::Get

  switch (key) {
    case DIR_EXE:
      if (!PathService::Get(FILE_EXE, result))
        return false;
      *result = result->DirName();
      return true;
    case DIR_MODULE:
      if (!PathService::Get(FILE_MODULE, result))
        return false;
      *result = result->DirName();
      return true;
    case DIR_ASSETS:
      return PathService::Get(DIR_MODULE, result);
    case DIR_TEMP:
      return GetTempDir(result);
    case base::DIR_HOME:
      *result = GetHomeDir();
      return true;
    case DIR_TEST_DATA: {
      FilePath test_data_path;
      if (!PathService::Get(DIR_SOURCE_ROOT, &test_data_path))
        return false;
      test_data_path = test_data_path.Append(FILE_PATH_LITERAL("base"));
      test_data_path = test_data_path.Append(FILE_PATH_LITERAL("test"));
      test_data_path = test_data_path.Append(FILE_PATH_LITERAL("data"));
      if (!PathExists(test_data_path))  // We don't want to create this.
        return false;
      *result = test_data_path;
      return true;
    }
    default:
      return false;
  }
}

}  // namespace base
