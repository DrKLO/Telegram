/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/native_capturer_observer.h"

#include "rtc_base/logging.h"
#include "sdk/android/generated_video_jni/NativeCapturerObserver_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/android_video_track_source.h"

namespace webrtc {
namespace jni {

ScopedJavaLocalRef<jobject> CreateJavaNativeCapturerObserver(
    JNIEnv* env,
    rtc::scoped_refptr<AndroidVideoTrackSource> native_source) {
  return Java_NativeCapturerObserver_Constructor(
      env, NativeToJavaPointer(native_source.release()));
}

}  // namespace jni
}  // namespace webrtc
