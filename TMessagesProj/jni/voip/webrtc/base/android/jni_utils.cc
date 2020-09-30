// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/jni_utils.h"

#include "base/android/scoped_java_ref.h"

#include "base/base_jni_headers/JNIUtils_jni.h"

namespace base {
namespace android {

ScopedJavaLocalRef<jobject> GetClassLoader(JNIEnv* env) {
  return Java_JNIUtils_getClassLoader(env);
}

bool IsSelectiveJniRegistrationEnabled(JNIEnv* env) {
  return Java_JNIUtils_isSelectiveJniRegistrationEnabled(env);
}

}  // namespace android
}  // namespace base

