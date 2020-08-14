// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <cpu-features.h>

#include "base/android/jni_android.h"
#include "base/base_jni_headers/CpuFeatures_jni.h"

namespace base {
namespace android {

jint JNI_CpuFeatures_GetCoreCount(JNIEnv*) {
  return android_getCpuCount();
}

jlong JNI_CpuFeatures_GetCpuFeatures(JNIEnv*) {
  return android_getCpuFeatures();
}

}  // namespace android
}  // namespace base
