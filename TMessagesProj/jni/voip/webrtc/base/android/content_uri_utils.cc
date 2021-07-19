// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/content_uri_utils.h"

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/ContentUriUtils_jni.h"

using base::android::ConvertUTF8ToJavaString;
using base::android::ScopedJavaLocalRef;

namespace base {

bool ContentUriExists(const FilePath& content_uri) {
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_uri =
      ConvertUTF8ToJavaString(env, content_uri.value());
  return Java_ContentUriUtils_contentUriExists(env, j_uri);
}

File OpenContentUriForRead(const FilePath& content_uri) {
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_uri =
      ConvertUTF8ToJavaString(env, content_uri.value());
  jint fd = Java_ContentUriUtils_openContentUriForRead(env, j_uri);
  if (fd < 0)
    return File();
  return File(fd);
}

std::string GetContentUriMimeType(const FilePath& content_uri) {
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_uri =
      ConvertUTF8ToJavaString(env, content_uri.value());
  ScopedJavaLocalRef<jstring> j_mime =
      Java_ContentUriUtils_getMimeType(env, j_uri);
  if (j_mime.is_null())
    return std::string();

  return base::android::ConvertJavaStringToUTF8(env, j_mime.obj());
}

bool MaybeGetFileDisplayName(const FilePath& content_uri,
                             base::string16* file_display_name) {
  if (!content_uri.IsContentUri())
    return false;

  DCHECK(file_display_name);

  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_uri =
      ConvertUTF8ToJavaString(env, content_uri.value());
  ScopedJavaLocalRef<jstring> j_display_name =
      Java_ContentUriUtils_maybeGetDisplayName(env, j_uri);

  if (j_display_name.is_null())
    return false;

  *file_display_name = base::android::ConvertJavaStringToUTF16(j_display_name);
  return true;
}

bool DeleteContentUri(const FilePath& content_uri) {
  DCHECK(content_uri.IsContentUri());
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_uri =
      ConvertUTF8ToJavaString(env, content_uri.value());

  return Java_ContentUriUtils_delete(env, j_uri);
}

FilePath GetContentUriFromFilePath(const FilePath& file_path) {
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> j_file_path =
      ConvertUTF8ToJavaString(env, file_path.value());
  ScopedJavaLocalRef<jstring> j_content_uri =
      Java_ContentUriUtils_getContentUriFromFilePath(env, j_file_path);
  if (j_content_uri.is_null())
    return FilePath();

  return FilePath(base::android::ConvertJavaStringToUTF8(env, j_content_uri));
}

}  // namespace base
