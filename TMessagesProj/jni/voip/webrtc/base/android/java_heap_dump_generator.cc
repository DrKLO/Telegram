// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/java_heap_dump_generator.h"

#include <jni.h>

#include "base/android/jni_string.h"
#include "base/base_jni_headers/JavaHeapDumpGenerator_jni.h"

namespace base {
namespace android {

bool WriteJavaHeapDumpToPath(base::StringPiece filePath) {
  JNIEnv* env = AttachCurrentThread();
  return Java_JavaHeapDumpGenerator_generateHprof(
      env, base::android::ConvertUTF8ToJavaString(env, filePath));
}

}  // namespace android
}  // namespace base
