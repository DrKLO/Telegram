/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/media_stream_interface.h"
#include "sdk/android/generated_peerconnection_jni/MediaSource_jni.h"

namespace webrtc {
namespace jni {

static ScopedJavaLocalRef<jobject> JNI_MediaSource_GetState(JNIEnv* jni,
                                                            jlong j_p) {
  return Java_State_fromNativeIndex(
      jni, reinterpret_cast<MediaSourceInterface*>(j_p)->state());
}

}  // namespace jni
}  // namespace webrtc
