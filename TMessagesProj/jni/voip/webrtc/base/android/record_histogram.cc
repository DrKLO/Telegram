// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <stdint.h>

#include <map>
#include <string>

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/RecordHistogram_jni.h"
#include "base/metrics/histogram.h"
#include "base/metrics/statistics_recorder.h"

namespace base {
namespace android {

// This backs a Java test util for testing histograms -
// MetricsUtils.HistogramDelta. It should live in a test-specific file, but we
// currently can't have test-specific native code packaged in test-specific Java
// targets - see http://crbug.com/415945.
jint JNI_RecordHistogram_GetHistogramValueCountForTesting(
    JNIEnv* env,
    const JavaParamRef<jstring>& histogram_name,
    jint sample) {
  HistogramBase* histogram = StatisticsRecorder::FindHistogram(
      android::ConvertJavaStringToUTF8(env, histogram_name));
  if (histogram == nullptr) {
    // No samples have been recorded for this histogram (yet?).
    return 0;
  }

  std::unique_ptr<HistogramSamples> samples = histogram->SnapshotSamples();
  return samples->GetCount(static_cast<int>(sample));
}

jint JNI_RecordHistogram_GetHistogramTotalCountForTesting(
    JNIEnv* env,
    const JavaParamRef<jstring>& histogram_name) {
  HistogramBase* histogram = StatisticsRecorder::FindHistogram(
      android::ConvertJavaStringToUTF8(env, histogram_name));
  if (histogram == nullptr) {
    // No samples have been recorded for this histogram.
    return 0;
  }

  return histogram->SnapshotSamples()->TotalCount();
}

}  // namespace android
}  // namespace base
