/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/rtc_stats_collector_callback_wrapper.h"

#include <string>
#include <vector>

#include "rtc_base/string_encode.h"
#include "sdk/android/generated_external_classes_jni/BigInteger_jni.h"
#include "sdk/android/generated_peerconnection_jni/RTCStatsCollectorCallback_jni.h"
#include "sdk/android/generated_peerconnection_jni/RTCStatsReport_jni.h"
#include "sdk/android/generated_peerconnection_jni/RTCStats_jni.h"
#include "sdk/android/native_api/jni/java_types.h"

namespace webrtc {
namespace jni {

namespace {

ScopedJavaLocalRef<jobject> NativeToJavaBigInteger(JNIEnv* env, uint64_t u) {
#ifdef RTC_JNI_GENERATOR_LEGACY_SYMBOLS
  return JNI_BigInteger::Java_BigInteger_ConstructorJMBI_JLS(
      env, NativeToJavaString(env, rtc::ToString(u)));
#else
  return JNI_BigInteger::Java_BigInteger_Constructor__String(
      env, NativeToJavaString(env, rtc::ToString(u)));
#endif
}

ScopedJavaLocalRef<jobjectArray> NativeToJavaBigIntegerArray(
    JNIEnv* env,
    const std::vector<uint64_t>& container) {
  return NativeToJavaObjectArray(
      env, container, java_math_BigInteger_clazz(env), &NativeToJavaBigInteger);
}

ScopedJavaLocalRef<jobject> AttributeToJava(JNIEnv* env,
                                            const Attribute& attribute) {
  if (attribute.holds_alternative<bool>()) {
    return NativeToJavaBoolean(env, attribute.get<bool>());
  } else if (attribute.holds_alternative<int32_t>()) {
    return NativeToJavaInteger(env, attribute.get<int32_t>());
  } else if (attribute.holds_alternative<uint32_t>()) {
    return NativeToJavaLong(env, attribute.get<uint32_t>());
  } else if (attribute.holds_alternative<int64_t>()) {
    return NativeToJavaLong(env, attribute.get<int64_t>());
  } else if (attribute.holds_alternative<uint64_t>()) {
    return NativeToJavaBigInteger(env, attribute.get<uint64_t>());
  } else if (attribute.holds_alternative<double>()) {
    return NativeToJavaDouble(env, attribute.get<double>());
  } else if (attribute.holds_alternative<std::string>()) {
    return NativeToJavaString(env, attribute.get<std::string>());
  } else if (attribute.holds_alternative<std::vector<bool>>()) {
    return NativeToJavaBooleanArray(env, attribute.get<std::vector<bool>>());
  } else if (attribute.holds_alternative<std::vector<int32_t>>()) {
    return NativeToJavaIntegerArray(env, attribute.get<std::vector<int32_t>>());
  } else if (attribute.holds_alternative<std::vector<uint32_t>>()) {
    const std::vector<uint32_t>& v = attribute.get<std::vector<uint32_t>>();
    return NativeToJavaLongArray(env, std::vector<int64_t>(v.begin(), v.end()));
  } else if (attribute.holds_alternative<std::vector<int64_t>>()) {
    return NativeToJavaLongArray(env, attribute.get<std::vector<int64_t>>());
  } else if (attribute.holds_alternative<std::vector<uint64_t>>()) {
    return NativeToJavaBigIntegerArray(env,
                                       attribute.get<std::vector<uint64_t>>());
  } else if (attribute.holds_alternative<std::vector<double>>()) {
    return NativeToJavaDoubleArray(env, attribute.get<std::vector<double>>());
  } else if (attribute.holds_alternative<std::vector<std::string>>()) {
    return NativeToJavaStringArray(env,
                                   attribute.get<std::vector<std::string>>());
  } else if (attribute.holds_alternative<std::map<std::string, uint64_t>>()) {
    return NativeToJavaMap(
        env, attribute.get<std::map<std::string, uint64_t>>(),
        [](JNIEnv* env, const auto& entry) {
          return std::make_pair(NativeToJavaString(env, entry.first),
                                NativeToJavaBigInteger(env, entry.second));
        });
  } else if (attribute.holds_alternative<std::map<std::string, double>>()) {
    return NativeToJavaMap(env, attribute.get<std::map<std::string, double>>(),
                           [](JNIEnv* env, const auto& entry) {
                             return std::make_pair(
                                 NativeToJavaString(env, entry.first),
                                 NativeToJavaDouble(env, entry.second));
                           });
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

ScopedJavaLocalRef<jobject> NativeToJavaRtcStats(JNIEnv* env,
                                                 const RTCStats& stats) {
  JavaMapBuilder builder(env);
  for (const auto& attribute : stats.Attributes()) {
    if (!attribute.has_value())
      continue;
    builder.put(NativeToJavaString(env, attribute.name()),
                AttributeToJava(env, attribute));
  }
  return Java_RTCStats_create(
      env, stats.timestamp().us(), NativeToJavaString(env, stats.type()),
      NativeToJavaString(env, stats.id()), builder.GetJavaMap());
}

ScopedJavaLocalRef<jobject> NativeToJavaRtcStatsReport(
    JNIEnv* env,
    const rtc::scoped_refptr<const RTCStatsReport>& report) {
  ScopedJavaLocalRef<jobject> j_stats_map =
      NativeToJavaMap(env, *report, [](JNIEnv* env, const RTCStats& stats) {
        return std::make_pair(NativeToJavaString(env, stats.id()),
                              NativeToJavaRtcStats(env, stats));
      });
  return Java_RTCStatsReport_create(env, report->timestamp().us(), j_stats_map);
}

}  // namespace

RTCStatsCollectorCallbackWrapper::RTCStatsCollectorCallbackWrapper(
    JNIEnv* jni,
    const JavaRef<jobject>& j_callback)
    : j_callback_global_(jni, j_callback) {}

RTCStatsCollectorCallbackWrapper::~RTCStatsCollectorCallbackWrapper() = default;

void RTCStatsCollectorCallbackWrapper::OnStatsDelivered(
    const rtc::scoped_refptr<const RTCStatsReport>& report) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  Java_RTCStatsCollectorCallback_onStatsDelivered(
      jni, j_callback_global_, NativeToJavaRtcStatsReport(jni, report));
}

}  // namespace jni
}  // namespace webrtc
