/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/dtmf_sender_interface.h"
#include "sdk/android/generated_peerconnection_jni/DtmfSender_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static jboolean JNI_DtmfSender_CanInsertDtmf(JNIEnv* jni,
                                             jlong j_dtmf_sender_pointer) {
  return reinterpret_cast<DtmfSenderInterface*>(j_dtmf_sender_pointer)
      ->CanInsertDtmf();
}

static jboolean JNI_DtmfSender_InsertDtmf(JNIEnv* jni,
                                          jlong j_dtmf_sender_pointer,
                                          const JavaParamRef<jstring>& tones,
                                          jint duration,
                                          jint inter_tone_gap) {
  return reinterpret_cast<DtmfSenderInterface*>(j_dtmf_sender_pointer)
      ->InsertDtmf(JavaToStdString(jni, tones), duration, inter_tone_gap);
}

static ScopedJavaLocalRef<jstring> JNI_DtmfSender_Tones(
    JNIEnv* jni,
    jlong j_dtmf_sender_pointer) {
  return NativeToJavaString(
      jni,
      reinterpret_cast<DtmfSenderInterface*>(j_dtmf_sender_pointer)->tones());
}

static jint JNI_DtmfSender_Duration(JNIEnv* jni,
                                    jlong j_dtmf_sender_pointer) {
  return reinterpret_cast<DtmfSenderInterface*>(j_dtmf_sender_pointer)
      ->duration();
}

static jint JNI_DtmfSender_InterToneGap(JNIEnv* jni,
                                        jlong j_dtmf_sender_pointer) {
  return reinterpret_cast<DtmfSenderInterface*>(j_dtmf_sender_pointer)
      ->inter_tone_gap();
}

}  // namespace jni
}  // namespace webrtc
