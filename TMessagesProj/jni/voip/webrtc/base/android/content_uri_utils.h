// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_CONTENT_URI_UTILS_H_
#define BASE_ANDROID_CONTENT_URI_UTILS_H_

#include <jni.h>

#include "base/base_export.h"
#include "base/files/file.h"
#include "base/files/file_path.h"

namespace base {

// Opens a content URI for read and returns the file descriptor to the caller.
// Returns -1 if the URI is invalid.
BASE_EXPORT File OpenContentUriForRead(const FilePath& content_uri);

// Check whether a content URI exists.
BASE_EXPORT bool ContentUriExists(const FilePath& content_uri);

// Gets MIME type from a content URI. Returns an empty string if the URI is
// invalid.
BASE_EXPORT std::string GetContentUriMimeType(const FilePath& content_uri);

// Gets the display name from a content URI. Returns true if the name was found.
BASE_EXPORT bool MaybeGetFileDisplayName(const FilePath& content_uri,
                                         base::string16* file_display_name);

// Deletes a content URI.
BASE_EXPORT bool DeleteContentUri(const FilePath& content_uri);

// Gets content URI's file path (eg: "content://org.chromium...") from normal
// file path (eg: "/data/user/0/...").
BASE_EXPORT FilePath GetContentUriFromFilePath(const FilePath& file_path);

}  // namespace base

#endif  // BASE_ANDROID_CONTENT_URI_UTILS_H_
