/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/rtp_transceiver.h"

#include <string>

#include "sdk/android/generated_peerconnection_jni/RtpTransceiver_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/pc/media_stream_track.h"
#include "sdk/android/src/jni/pc/rtp_parameters.h"
#include "sdk/android/src/jni/pc/rtp_receiver.h"
#include "sdk/android/src/jni/pc/rtp_sender.h"

namespace webrtc {
namespace jni {

namespace {

ScopedJavaLocalRef<jobject> NativeToJavaRtpTransceiverDirection(
    JNIEnv* jni,
    RtpTransceiverDirection rtp_transceiver_direction) {
  return Java_RtpTransceiverDirection_fromNativeIndex(
      jni, static_cast<int>(rtp_transceiver_direction));
}

}  // namespace

RtpTransceiverInit JavaToNativeRtpTransceiverInit(
    JNIEnv* jni,
    const JavaRef<jobject>& j_init) {
  RtpTransceiverInit init;

  // Convert the direction.
  init.direction = static_cast<RtpTransceiverDirection>(
      Java_RtpTransceiverInit_getDirectionNativeIndex(jni, j_init));

  // Convert the stream ids.
  ScopedJavaLocalRef<jobject> j_stream_ids =
      Java_RtpTransceiverInit_getStreamIds(jni, j_init);
  init.stream_ids = JavaListToNativeVector<std::string, jstring>(
      jni, j_stream_ids, &JavaToNativeString);

  // Convert the send encodings.
  ScopedJavaLocalRef<jobject> j_send_encodings =
      Java_RtpTransceiverInit_getSendEncodings(jni, j_init);
  init.send_encodings = JavaListToNativeVector<RtpEncodingParameters, jobject>(
      jni, j_send_encodings, &JavaToNativeRtpEncodingParameters);
  return init;
}

ScopedJavaLocalRef<jobject> NativeToJavaRtpTransceiver(
    JNIEnv* env,
    rtc::scoped_refptr<RtpTransceiverInterface> transceiver) {
  if (!transceiver) {
    return nullptr;
  }
  // Transceiver will now have shared ownership by the Java object.
  return Java_RtpTransceiver_Constructor(
      env, jlongFromPointer(transceiver.release()));
}

JavaRtpTransceiverGlobalOwner::JavaRtpTransceiverGlobalOwner(
    JNIEnv* env,
    const JavaRef<jobject>& j_transceiver)
    : j_transceiver_(env, j_transceiver) {}

JavaRtpTransceiverGlobalOwner::JavaRtpTransceiverGlobalOwner(
    JavaRtpTransceiverGlobalOwner&& other) = default;

JavaRtpTransceiverGlobalOwner::~JavaRtpTransceiverGlobalOwner() {
  if (j_transceiver_.obj()) {
    Java_RtpTransceiver_dispose(AttachCurrentThreadIfNeeded(), j_transceiver_);
  }
}

ScopedJavaLocalRef<jobject> JNI_RtpTransceiver_GetMediaType(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer) {
  return NativeToJavaMediaType(
      jni, reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
               ->media_type());
}

ScopedJavaLocalRef<jstring> JNI_RtpTransceiver_GetMid(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer) {
  absl::optional<std::string> mid =
      reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
          ->mid();
  return NativeToJavaString(jni, mid);
}

ScopedJavaLocalRef<jobject> JNI_RtpTransceiver_GetSender(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer) {
  return NativeToJavaRtpSender(
      jni, reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
               ->sender());
}

ScopedJavaLocalRef<jobject> JNI_RtpTransceiver_GetReceiver(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer) {
  return NativeToJavaRtpReceiver(
      jni, reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
               ->receiver());
}

jboolean JNI_RtpTransceiver_Stopped(JNIEnv* jni,
                                    jlong j_rtp_transceiver_pointer) {
  return reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
      ->stopped();
}

ScopedJavaLocalRef<jobject> JNI_RtpTransceiver_Direction(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer) {
  return NativeToJavaRtpTransceiverDirection(
      jni, reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
               ->direction());
}

ScopedJavaLocalRef<jobject> JNI_RtpTransceiver_CurrentDirection(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer) {
  absl::optional<RtpTransceiverDirection> direction =
      reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
          ->current_direction();
  return direction ? NativeToJavaRtpTransceiverDirection(jni, *direction)
                   : nullptr;
}

void JNI_RtpTransceiver_StopInternal(JNIEnv* jni,
                                     jlong j_rtp_transceiver_pointer) {
  reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
      ->StopInternal();
}

void JNI_RtpTransceiver_StopStandard(JNIEnv* jni,
                                     jlong j_rtp_transceiver_pointer) {
  reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
      ->StopStandard();
}

jboolean JNI_RtpTransceiver_SetDirection(
    JNIEnv* jni,
    jlong j_rtp_transceiver_pointer,
    const base::android::JavaParamRef<jobject>& j_rtp_transceiver_direction) {
  if (IsNull(jni, j_rtp_transceiver_direction)) {
    return false;
  }
  RtpTransceiverDirection direction = static_cast<RtpTransceiverDirection>(
      Java_RtpTransceiverDirection_getNativeIndex(jni,
                                                  j_rtp_transceiver_direction));
  webrtc::RTCError error =
      reinterpret_cast<RtpTransceiverInterface*>(j_rtp_transceiver_pointer)
          ->SetDirectionWithError(direction);
  if (!error.ok()) {
    RTC_LOG(LS_WARNING) << "SetDirection failed, code "
                        << ToString(error.type()) << ", message "
                        << error.message();
  }
  return error.ok();
}

}  // namespace jni
}  // namespace webrtc
