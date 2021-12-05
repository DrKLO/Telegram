// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include "base/android/jni_string.h"
#include "base/base_jni_headers/StatisticsRecorderAndroid_jni.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/statistics_recorder.h"
#include "base/system/sys_info.h"

using base::android::JavaParamRef;
using base::android::ConvertUTF8ToJavaString;

namespace base {
namespace android {

static ScopedJavaLocalRef<jstring> JNI_StatisticsRecorderAndroid_ToJson(
    JNIEnv* env,
    jint verbosityLevel) {
  return ConvertUTF8ToJavaString(
      env, base::StatisticsRecorder::ToJSON(
               static_cast<JSONVerbosityLevel>(verbosityLevel)));
}

}  // namespace android
}  // namespace base
