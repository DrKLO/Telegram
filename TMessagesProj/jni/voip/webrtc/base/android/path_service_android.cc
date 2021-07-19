// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/PathService_jni.h"
#include "base/files/file_path.h"
#include "base/path_service.h"

namespace base {
namespace android {

void JNI_PathService_Override(JNIEnv* env,
                              jint what,
                              const JavaParamRef<jstring>& path) {
  FilePath file_path(ConvertJavaStringToUTF8(env, path));
  PathService::Override(what, file_path);
}

}  // namespace android
}  // namespace base
