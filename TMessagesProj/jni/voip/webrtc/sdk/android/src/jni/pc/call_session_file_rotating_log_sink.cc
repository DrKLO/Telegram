/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/log_sinks.h"
#include "sdk/android/generated_peerconnection_jni/CallSessionFileRotatingLogSink_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static jlong JNI_CallSessionFileRotatingLogSink_AddSink(
    JNIEnv* jni,
    const JavaParamRef<jstring>& j_dirPath,
    jint j_maxFileSize,
    jint j_severity) {
  std::string dir_path = JavaToStdString(jni, j_dirPath);
  rtc::CallSessionFileRotatingLogSink* sink =
      new rtc::CallSessionFileRotatingLogSink(dir_path, j_maxFileSize);
  if (!sink->Init()) {
    RTC_LOG_V(rtc::LoggingSeverity::LS_WARNING)
        << "Failed to init CallSessionFileRotatingLogSink for path "
        << dir_path;
    delete sink;
    return 0;
  }
  rtc::LogMessage::AddLogToStream(
      sink, static_cast<rtc::LoggingSeverity>(j_severity));
  return jlongFromPointer(sink);
}

static void JNI_CallSessionFileRotatingLogSink_DeleteSink(JNIEnv* jni,
                                                          jlong j_sink) {
  rtc::CallSessionFileRotatingLogSink* sink =
      reinterpret_cast<rtc::CallSessionFileRotatingLogSink*>(j_sink);
  rtc::LogMessage::RemoveLogToStream(sink);
  delete sink;
}

static ScopedJavaLocalRef<jbyteArray>
JNI_CallSessionFileRotatingLogSink_GetLogData(
    JNIEnv* jni,
    const JavaParamRef<jstring>& j_dirPath) {
  std::string dir_path = JavaToStdString(jni, j_dirPath);
  rtc::CallSessionFileRotatingStreamReader file_reader(dir_path);
  size_t log_size = file_reader.GetSize();
  if (log_size == 0) {
    RTC_LOG_V(rtc::LoggingSeverity::LS_WARNING)
        << "CallSessionFileRotatingStream returns 0 size for path " << dir_path;
    return ScopedJavaLocalRef<jbyteArray>(jni, jni->NewByteArray(0));
  }

  // TODO(nisse, sakal): To avoid copying, change api to use ByteBuffer.
  std::unique_ptr<jbyte> buffer(static_cast<jbyte*>(malloc(log_size)));
  size_t read = file_reader.ReadAll(buffer.get(), log_size);

  ScopedJavaLocalRef<jbyteArray> result =
      ScopedJavaLocalRef<jbyteArray>(jni, jni->NewByteArray(read));
  jni->SetByteArrayRegion(result.obj(), 0, read, buffer.get());

  return result;
}

}  // namespace jni
}  // namespace webrtc
