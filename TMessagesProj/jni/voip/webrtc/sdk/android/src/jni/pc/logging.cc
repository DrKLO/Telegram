/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/logging.h"

#include <memory>

#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

JNI_FUNCTION_DECLARATION(void,
                         Logging_nativeEnableLogToDebugOutput,
                         JNIEnv* jni,
                         jclass,
                         jint nativeSeverity) {
  if (nativeSeverity >= rtc::LS_VERBOSE && nativeSeverity <= rtc::LS_NONE) {
    rtc::LogMessage::LogToDebug(
        static_cast<rtc::LoggingSeverity>(nativeSeverity));
  }
}

JNI_FUNCTION_DECLARATION(void,
                         Logging_nativeEnableLogThreads,
                         JNIEnv* jni,
                         jclass) {
  rtc::LogMessage::LogThreads(true);
}

JNI_FUNCTION_DECLARATION(void,
                         Logging_nativeEnableLogTimeStamps,
                         JNIEnv* jni,
                         jclass) {
  rtc::LogMessage::LogTimestamps(true);
}

JNI_FUNCTION_DECLARATION(void,
                         Logging_nativeLog,
                         JNIEnv* jni,
                         jclass,
                         jint j_severity,
                         jstring j_tag,
                         jstring j_message) {
  std::string message = JavaToStdString(jni, JavaParamRef<jstring>(j_message));
  std::string tag = JavaToStdString(jni, JavaParamRef<jstring>(j_tag));
  RTC_LOG_TAG(static_cast<rtc::LoggingSeverity>(j_severity), tag.c_str())
      << message;
}

}  // namespace jni
}  // namespace webrtc
