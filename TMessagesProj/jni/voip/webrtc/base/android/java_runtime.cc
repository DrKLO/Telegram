// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/java_runtime.h"

#include "base/android_runtime_jni_headers/Runtime_jni.h"

namespace base {
namespace android {

void JavaRuntime::GetMemoryUsage(long* total_memory, long* free_memory) {
  JNIEnv* env = base::android::AttachCurrentThread();
  base::android::ScopedJavaLocalRef<jobject> runtime =
      JNI_Runtime::Java_Runtime_getRuntime(env);
  *total_memory = JNI_Runtime::Java_Runtime_totalMemory(env, runtime);
  *free_memory = JNI_Runtime::Java_Runtime_freeMemory(env, runtime);
}

}  // namespace android
}  // namespace base
