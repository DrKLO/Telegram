// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/jni_string.h"
#include "base/base_jni_headers/AnimationFrameTimeHistogram_jni.h"
#include "base/metrics/histogram_macros.h"

using base::android::JavaParamRef;

// static
void JNI_AnimationFrameTimeHistogram_SaveHistogram(
    JNIEnv* env,
    const JavaParamRef<jstring>& j_histogram_name,
    const JavaParamRef<jlongArray>& j_frame_times_ms,
    jint j_count) {
  jlong* frame_times_ms =
      env->GetLongArrayElements(j_frame_times_ms.obj(), nullptr);
  std::string histogram_name = base::android::ConvertJavaStringToUTF8(
      env, j_histogram_name);

  for (int i = 0; i < j_count; ++i) {
    UMA_HISTOGRAM_TIMES(histogram_name.c_str(),
                        base::TimeDelta::FromMilliseconds(frame_times_ms[i]));
  }
  env->ReleaseLongArrayElements(j_frame_times_ms.obj(), frame_times_ms,
                                JNI_ABORT);
}
