/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/media_stream_track.h"

#include "api/media_stream_interface.h"
#include "sdk/android/generated_peerconnection_jni/MediaStreamTrack_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

ScopedJavaLocalRef<jobject> NativeToJavaMediaType(
    JNIEnv* jni,
    cricket::MediaType media_type) {
  return Java_MediaType_fromNativeIndex(jni, media_type);
}

cricket::MediaType JavaToNativeMediaType(JNIEnv* jni,
                                         const JavaRef<jobject>& j_media_type) {
  return static_cast<cricket::MediaType>(
      Java_MediaType_getNative(jni, j_media_type));
}

static ScopedJavaLocalRef<jstring> JNI_MediaStreamTrack_GetId(JNIEnv* jni,
                                                              jlong j_p) {
  return NativeToJavaString(
      jni, reinterpret_cast<MediaStreamTrackInterface*>(j_p)->id());
}

static ScopedJavaLocalRef<jstring> JNI_MediaStreamTrack_GetKind(JNIEnv* jni,
                                                                jlong j_p) {
  return NativeToJavaString(
      jni, reinterpret_cast<MediaStreamTrackInterface*>(j_p)->kind());
}

static jboolean JNI_MediaStreamTrack_GetEnabled(JNIEnv* jni, jlong j_p) {
  return reinterpret_cast<MediaStreamTrackInterface*>(j_p)->enabled();
}

static ScopedJavaLocalRef<jobject> JNI_MediaStreamTrack_GetState(JNIEnv* jni,
                                                                 jlong j_p) {
  return Java_State_fromNativeIndex(
      jni, reinterpret_cast<MediaStreamTrackInterface*>(j_p)->state());
}

static jboolean JNI_MediaStreamTrack_SetEnabled(JNIEnv* jni,
                                                jlong j_p,
                                                jboolean enabled) {
  return reinterpret_cast<MediaStreamTrackInterface*>(j_p)->set_enabled(
      enabled);
}

}  // namespace jni
}  // namespace webrtc
