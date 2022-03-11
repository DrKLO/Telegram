/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <map>
#include <memory>

#include "sdk/android/generated_metrics_jni/Metrics_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "system_wrappers/include/metrics.h"

// Enables collection of native histograms and creating them.
namespace webrtc {
namespace jni {

static void JNI_Metrics_Enable(JNIEnv* jni) {
  metrics::Enable();
}

// Gets and clears native histograms.
static ScopedJavaLocalRef<jobject> JNI_Metrics_GetAndReset(JNIEnv* jni) {
  ScopedJavaLocalRef<jobject> j_metrics = Java_Metrics_Constructor(jni);

  std::map<std::string, std::unique_ptr<metrics::SampleInfo>> histograms;
  metrics::GetAndReset(&histograms);
  for (const auto& kv : histograms) {
    // Create and add samples to `HistogramInfo`.
    ScopedJavaLocalRef<jobject> j_info = Java_HistogramInfo_Constructor(
        jni, kv.second->min, kv.second->max,
        static_cast<int>(kv.second->bucket_count));
    for (const auto& sample : kv.second->samples) {
      Java_HistogramInfo_addSample(jni, j_info, sample.first, sample.second);
    }
    // Add `HistogramInfo` to `Metrics`.
    ScopedJavaLocalRef<jstring> j_name = NativeToJavaString(jni, kv.first);
    Java_Metrics_add(jni, j_metrics, j_name, j_info);
  }
  CHECK_EXCEPTION(jni);
  return j_metrics;
}

}  // namespace jni
}  // namespace webrtc
