/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/stats_observer.h"

#include <vector>

#include "sdk/android/generated_peerconnection_jni/StatsObserver_jni.h"
#include "sdk/android/generated_peerconnection_jni/StatsReport_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

namespace {

ScopedJavaLocalRef<jobject> NativeToJavaStatsReportValue(
    JNIEnv* env,
    const rtc::scoped_refptr<StatsReport::Value>& value_ptr) {
  // Should we use the '.name' enum value here instead of converting the
  // name to a string?
  return Java_Value_Constructor(
      env, NativeToJavaString(env, value_ptr->display_name()),
      NativeToJavaString(env, value_ptr->ToString()));
}

ScopedJavaLocalRef<jobjectArray> NativeToJavaStatsReportValueArray(
    JNIEnv* env,
    const StatsReport::Values& value_map) {
  // Ignore the keys and make an array out of the values.
  std::vector<StatsReport::ValuePtr> values;
  for (const auto& it : value_map)
    values.push_back(it.second);
  return NativeToJavaObjectArray(env, values,
                                 org_webrtc_StatsReport_00024Value_clazz(env),
                                 &NativeToJavaStatsReportValue);
}

ScopedJavaLocalRef<jobject> NativeToJavaStatsReport(JNIEnv* env,
                                                    const StatsReport& report) {
  return Java_StatsReport_Constructor(
      env, NativeToJavaString(env, report.id()->ToString()),
      NativeToJavaString(env, report.TypeToString()), report.timestamp(),
      NativeToJavaStatsReportValueArray(env, report.values()));
}

}  // namespace

StatsObserverJni::StatsObserverJni(JNIEnv* jni,
                                   const JavaRef<jobject>& j_observer)
    : j_observer_global_(jni, j_observer) {}

StatsObserverJni::~StatsObserverJni() = default;

void StatsObserverJni::OnComplete(const StatsReports& reports) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobjectArray> j_reports =
      NativeToJavaObjectArray(env, reports, org_webrtc_StatsReport_clazz(env),
                              [](JNIEnv* env, const StatsReport* report) {
                                return NativeToJavaStatsReport(env, *report);
                              });
  Java_StatsObserver_onComplete(env, j_observer_global_, j_reports);
}

}  // namespace jni
}  // namespace webrtc
