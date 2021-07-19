// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Defines base::PathProviderAndroid which replaces base::PathProviderPosix for
// Android in base/path_service.cc.

#include <limits.h>
#include <unistd.h>

#include "base/android/jni_android.h"
#include "base/android/path_utils.h"
#include "base/base_paths.h"
#include "base/files/file_path.h"
#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/process/process_metrics.h"

namespace base {

bool PathProviderAndroid(int key, FilePath* result) {
  switch (key) {
    case base::FILE_EXE: {
      FilePath bin_dir;
      if (!ReadSymbolicLink(FilePath(kProcSelfExe), &bin_dir)) {
        NOTREACHED() << "Unable to resolve " << kProcSelfExe << ".";
        return false;
      }
      *result = bin_dir;
      return true;
    }
    case base::FILE_MODULE:
      // dladdr didn't work in Android as only the file name was returned.
      NOTIMPLEMENTED();
      return false;
    case base::DIR_MODULE:
      return base::android::GetNativeLibraryDirectory(result);
    case base::DIR_SOURCE_ROOT:
      // Used only by tests.
      // In that context, hooked up via base/test/test_support_android.cc.
      NOTIMPLEMENTED();
      return false;
    case base::DIR_USER_DESKTOP:
      // Android doesn't support GetUserDesktop.
      NOTIMPLEMENTED();
      return false;
    case base::DIR_CACHE:
      return base::android::GetCacheDirectory(result);
    case base::DIR_ASSETS:
      // On Android assets are normally loaded from the APK using
      // base::android::OpenApkAsset(). In tests, since the assets are no
      // packaged, DIR_ASSETS is overridden to point to the build directory.
      return false;
    case base::DIR_ANDROID_APP_DATA:
      return base::android::GetDataDirectory(result);
    case base::DIR_ANDROID_EXTERNAL_STORAGE:
      return base::android::GetExternalStorageDirectory(result);
    default:
      // Note: the path system expects this function to override the default
      // behavior. So no need to log an error if we don't support a given
      // path. The system will just use the default.
      return false;
  }
}

}  // namespace base
