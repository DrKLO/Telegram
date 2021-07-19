// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_NIX_MIME_UTIL_XDG_H_
#define BASE_NIX_MIME_UTIL_XDG_H_

#include <string>

#include "base/base_export.h"
#include "build/build_config.h"

namespace base {

class FilePath;

namespace nix {

// Gets the mime type for a file at |filepath|.
//
// The mime type is calculated based only on the file name of |filepath|.  In
// particular |filepath| will not be touched on disk and |filepath| doesn't even
// have to exist.  This means that the function does not work for directories
// (i.e. |filepath| is assumed to be a path to a file).
//
// Note that this function might need to read from disk the mime-types data
// provided by the OS.  Therefore this function should not be called from
// threads that disallow IO via base::ThreadRestrictions::SetIOAllowed(false).
//
// If the mime type is unknown, this will return application/octet-stream.
BASE_EXPORT std::string GetFileMimeType(const FilePath& filepath);

}  // namespace nix
}  // namespace base

#endif  // BASE_NIX_MIME_UTIL_XDG_H_
