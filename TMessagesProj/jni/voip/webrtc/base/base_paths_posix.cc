// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Defines base::PathProviderPosix, default path provider on POSIX OSes that
// don't have their own base_paths_OS.cc implementation (i.e. all but Mac and
// Android).

#include "base/base_paths.h"

#include <limits.h>
#include <stddef.h>

#include <memory>
#include <ostream>
#include <string>

#include "base/environment.h"
#include "base/files/file_path.h"
#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/nix/xdg_util.h"
#include "base/path_service.h"
#include "base/process/process_metrics.h"
#include "build/build_config.h"

#if defined(OS_FREEBSD)
#include <sys/param.h>
#include <sys/sysctl.h>
#elif defined(OS_SOLARIS) || defined(OS_AIX)
#include <stdlib.h>
#endif

namespace base {

bool PathProviderPosix(int key, FilePath* result) {
  switch (key) {
    case FILE_EXE:
    case FILE_MODULE: {  // TODO(evanm): is this correct?
#if defined(OS_LINUX)
      FilePath bin_dir;
      if (!ReadSymbolicLink(FilePath(kProcSelfExe), &bin_dir)) {
        NOTREACHED() << "Unable to resolve " << kProcSelfExe << ".";
        return false;
      }
      *result = bin_dir;
      return true;
#elif defined(OS_FREEBSD)
      int name[] = { CTL_KERN, KERN_PROC, KERN_PROC_PATHNAME, -1 };
      char bin_dir[PATH_MAX + 1];
      size_t length = sizeof(bin_dir);
      // Upon return, |length| is the number of bytes written to |bin_dir|
      // including the string terminator.
      int error = sysctl(name, 4, bin_dir, &length, NULL, 0);
      if (error < 0 || length <= 1) {
        NOTREACHED() << "Unable to resolve path.";
        return false;
      }
      *result = FilePath(FilePath::StringType(bin_dir, length - 1));
      return true;
#elif defined(OS_SOLARIS)
      char bin_dir[PATH_MAX + 1];
      if (realpath(getexecname(), bin_dir) == NULL) {
        NOTREACHED() << "Unable to resolve " << getexecname() << ".";
        return false;
      }
      *result = FilePath(bin_dir);
      return true;
#elif defined(OS_OPENBSD) || defined(OS_AIX)
      // There is currently no way to get the executable path on OpenBSD
      char* cpath;
      if ((cpath = getenv("CHROME_EXE_PATH")) != NULL)
        *result = FilePath(cpath);
      else
        *result = FilePath("/usr/local/chrome/chrome");
      return true;
#endif
    }
    case DIR_SOURCE_ROOT: {
      // Allow passing this in the environment, for more flexibility in build
      // tree configurations (sub-project builds, gyp --output_dir, etc.)
      std::unique_ptr<Environment> env(Environment::Create());
      std::string cr_source_root;
      FilePath path;
      if (env->GetVar("CR_SOURCE_ROOT", &cr_source_root)) {
        path = FilePath(cr_source_root);
        if (PathExists(path)) {
          *result = path;
          return true;
        }
        DLOG(WARNING) << "CR_SOURCE_ROOT is set, but it appears to not "
                      << "point to a directory.";
      }
      // On POSIX, unit tests execute two levels deep from the source root.
      // For example:  out/{Debug|Release}/net_unittest
      if (PathService::Get(DIR_EXE, &path)) {
        *result = path.DirName().DirName();
        return true;
      }

      DLOG(ERROR) << "Couldn't find your source root.  "
                  << "Try running from your chromium/src directory.";
      return false;
    }
    case DIR_USER_DESKTOP:
      *result = nix::GetXDGUserDirectory("DESKTOP", "Desktop");
      return true;
    case DIR_CACHE: {
      std::unique_ptr<Environment> env(Environment::Create());
      FilePath cache_dir(
          nix::GetXDGDirectory(env.get(), "XDG_CACHE_HOME", ".cache"));
      *result = cache_dir;
      return true;
    }
  }
  return false;
}

}  // namespace base
