/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "sdk/android/src/jni/logging/log_sink.h"

#include "sdk/android/generated_logging_jni/JNILogging_jni.h"

namespace webrtc {
namespace jni {

JNILogSink::JNILogSink(JNIEnv* env, const JavaRef<jobject>& j_logging)
    : j_logging_(env, j_logging) {}
JNILogSink::~JNILogSink() = default;

void JNILogSink::OnLogMessage(const std::string& msg,
                              rtc::LoggingSeverity severity,
                              const char* tag) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_JNILogging_logToInjectable(env, j_logging_, NativeToJavaString(env, msg),
                                  NativeToJavaInteger(env, severity),
                                  NativeToJavaString(env, tag));
}

void JNILogSink::OnLogMessage(const std::string& msg) {
  RTC_NOTREACHED();
}

}  // namespace jni
}  // namespace webrtc
