/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/add_ice_candidate_observer.h"

#include <utility>

#include "sdk/android/generated_peerconnection_jni/AddIceObserver_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/media_constraints.h"

namespace webrtc {
namespace jni {

AddIceCandidateObserverJni::AddIceCandidateObserverJni(
    JNIEnv* env,
    const JavaRef<jobject>& j_observer)
    : j_observer_global_(env, j_observer) {}

void AddIceCandidateObserverJni::OnComplete(webrtc::RTCError error) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  if (error.ok()) {
    Java_AddIceObserver_onAddSuccess(env, j_observer_global_);
  } else {
    Java_AddIceObserver_onAddFailure(env, j_observer_global_,
                                     NativeToJavaString(env, error.message()));
  }
}

}  // namespace jni
}  // namespace webrtc
