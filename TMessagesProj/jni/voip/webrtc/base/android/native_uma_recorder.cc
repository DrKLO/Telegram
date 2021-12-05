// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/NativeUmaRecorder_jni.h"
#include "base/lazy_instance.h"
#include "base/macros.h"
#include "base/metrics/histogram.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/sparse_histogram.h"
#include "base/metrics/user_metrics.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/lock.h"
#include "base/time/time.h"

namespace base {
namespace android {

namespace {

// Simple thread-safe wrapper for caching histograms. This avoids
// relatively expensive JNI string translation for each recording.
class HistogramCache {
 public:
  HistogramCache() {}

  std::string HistogramConstructionParamsToString(HistogramBase* histogram) {
    std::string params_str = histogram->histogram_name();
    switch (histogram->GetHistogramType()) {
      case HISTOGRAM:
      case LINEAR_HISTOGRAM:
      case BOOLEAN_HISTOGRAM:
      case CUSTOM_HISTOGRAM: {
        Histogram* hist = static_cast<Histogram*>(histogram);
        params_str += StringPrintf("/%d/%d/%d", hist->declared_min(),
                                   hist->declared_max(), hist->bucket_count());
        break;
      }
      case SPARSE_HISTOGRAM:
      case DUMMY_HISTOGRAM:
        break;
    }
    return params_str;
  }

  void CheckHistogramArgs(JNIEnv* env,
                          jstring j_histogram_name,
                          int32_t expected_min,
                          int32_t expected_max,
                          uint32_t expected_bucket_count,
                          HistogramBase* histogram) {
    std::string histogram_name = ConvertJavaStringToUTF8(env, j_histogram_name);
    bool valid_arguments = Histogram::InspectConstructionArguments(
        histogram_name, &expected_min, &expected_max, &expected_bucket_count);
    DCHECK(valid_arguments);
    DCHECK(histogram->HasConstructionArguments(expected_min, expected_max,
                                               expected_bucket_count))
        << histogram_name << "/" << expected_min << "/" << expected_max << "/"
        << expected_bucket_count << " vs. "
        << HistogramConstructionParamsToString(histogram);
  }

  HistogramBase* BooleanHistogram(JNIEnv* env,
                                  jstring j_histogram_name,
                                  jlong j_histogram_hint) {
    DCHECK(j_histogram_name);
    HistogramBase* histogram = HistogramFromHint(j_histogram_hint);
    if (histogram)
      return histogram;

    std::string histogram_name = ConvertJavaStringToUTF8(env, j_histogram_name);
    histogram = BooleanHistogram::FactoryGet(
        histogram_name, HistogramBase::kUmaTargetedHistogramFlag);
    return histogram;
  }

  HistogramBase* ExponentialHistogram(JNIEnv* env,
                                      jstring j_histogram_name,
                                      jlong j_histogram_hint,
                                      jint j_min,
                                      jint j_max,
                                      jint j_num_buckets) {
    DCHECK(j_histogram_name);
    int32_t min = static_cast<int32_t>(j_min);
    int32_t max = static_cast<int32_t>(j_max);
    int32_t num_buckets = static_cast<int32_t>(j_num_buckets);
    HistogramBase* histogram = HistogramFromHint(j_histogram_hint);
    if (histogram) {
      CheckHistogramArgs(env, j_histogram_name, min, max, num_buckets,
                         histogram);
      return histogram;
    }

    DCHECK_GE(min, 1) << "The min expected sample must be >= 1";

    std::string histogram_name = ConvertJavaStringToUTF8(env, j_histogram_name);
    histogram = Histogram::FactoryGet(histogram_name, min, max, num_buckets,
                                      HistogramBase::kUmaTargetedHistogramFlag);
    return histogram;
  }

  HistogramBase* LinearHistogram(JNIEnv* env,
                                 jstring j_histogram_name,
                                 jlong j_histogram_hint,
                                 jint j_min,
                                 jint j_max,
                                 jint j_num_buckets) {
    DCHECK(j_histogram_name);
    int32_t min = static_cast<int32_t>(j_min);
    int32_t max = static_cast<int32_t>(j_max);
    int32_t num_buckets = static_cast<int32_t>(j_num_buckets);
    HistogramBase* histogram = HistogramFromHint(j_histogram_hint);
    if (histogram) {
      CheckHistogramArgs(env, j_histogram_name, min, max, num_buckets,
                         histogram);
      return histogram;
    }

    std::string histogram_name = ConvertJavaStringToUTF8(env, j_histogram_name);
    histogram =
        LinearHistogram::FactoryGet(histogram_name, min, max, num_buckets,
                                    HistogramBase::kUmaTargetedHistogramFlag);
    return histogram;
  }

  HistogramBase* SparseHistogram(JNIEnv* env,
                                 jstring j_histogram_name,
                                 jlong j_histogram_hint) {
    DCHECK(j_histogram_name);
    HistogramBase* histogram = HistogramFromHint(j_histogram_hint);
    if (histogram)
      return histogram;

    std::string histogram_name = ConvertJavaStringToUTF8(env, j_histogram_name);
    histogram = SparseHistogram::FactoryGet(
        histogram_name, HistogramBase::kUmaTargetedHistogramFlag);
    return histogram;
  }

 private:
  // Convert a jlong |histogram_hint| from Java to a HistogramBase* via a cast.
  // The Java side caches these in a map (see NativeUmaRecorder.java), which is
  // safe to do since C++ Histogram objects are never freed.
  static HistogramBase* HistogramFromHint(jlong j_histogram_hint) {
    return reinterpret_cast<HistogramBase*>(j_histogram_hint);
  }

  DISALLOW_COPY_AND_ASSIGN(HistogramCache);
};

LazyInstance<HistogramCache>::Leaky g_histograms;

}  // namespace

jlong JNI_NativeUmaRecorder_RecordBooleanHistogram(
    JNIEnv* env,
    const JavaParamRef<jstring>& j_histogram_name,
    jlong j_histogram_hint,
    jboolean j_sample) {
  bool sample = static_cast<bool>(j_sample);
  HistogramBase* histogram = g_histograms.Get().BooleanHistogram(
      env, j_histogram_name, j_histogram_hint);
  histogram->AddBoolean(sample);
  return reinterpret_cast<jlong>(histogram);
}

jlong JNI_NativeUmaRecorder_RecordExponentialHistogram(
    JNIEnv* env,
    const JavaParamRef<jstring>& j_histogram_name,
    jlong j_histogram_hint,
    jint j_sample,
    jint j_min,
    jint j_max,
    jint j_num_buckets) {
  int sample = static_cast<int>(j_sample);
  HistogramBase* histogram = g_histograms.Get().ExponentialHistogram(
      env, j_histogram_name, j_histogram_hint, j_min, j_max, j_num_buckets);
  histogram->Add(sample);
  return reinterpret_cast<jlong>(histogram);
}

jlong JNI_NativeUmaRecorder_RecordLinearHistogram(
    JNIEnv* env,
    const JavaParamRef<jstring>& j_histogram_name,
    jlong j_histogram_hint,
    jint j_sample,
    jint j_min,
    jint j_max,
    jint j_num_buckets) {
  int sample = static_cast<int>(j_sample);
  HistogramBase* histogram = g_histograms.Get().LinearHistogram(
      env, j_histogram_name, j_histogram_hint, j_min, j_max, j_num_buckets);
  histogram->Add(sample);
  return reinterpret_cast<jlong>(histogram);
}

jlong JNI_NativeUmaRecorder_RecordSparseHistogram(
    JNIEnv* env,
    const JavaParamRef<jstring>& j_histogram_name,
    jlong j_histogram_hint,
    jint j_sample) {
  int sample = static_cast<int>(j_sample);
  HistogramBase* histogram = g_histograms.Get().SparseHistogram(
      env, j_histogram_name, j_histogram_hint);
  histogram->Add(sample);
  return reinterpret_cast<jlong>(histogram);
}

void JNI_NativeUmaRecorder_RecordUserAction(
    JNIEnv* env,
    const JavaParamRef<jstring>& j_user_action_name,
    jlong j_millis_since_event) {
  // Time values coming from Java need to be synchronized with TimeTick clock.
  RecordComputedActionSince(ConvertJavaStringToUTF8(env, j_user_action_name),
                            TimeDelta::FromMilliseconds(j_millis_since_event));
}

}  // namespace android
}  // namespace base
