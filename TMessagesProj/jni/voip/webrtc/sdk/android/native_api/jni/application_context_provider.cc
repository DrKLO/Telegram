/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "sdk/android/native_api/jni/application_context_provider.h"

#include "sdk/android/generated_native_api_jni/ApplicationContextProvider_jni.h"
#include "sdk/android/native_api/jni/scoped_java_ref.h"

namespace webrtc {

ScopedJavaLocalRef<jobject> GetAppContext(JNIEnv* jni) {
  return ScopedJavaLocalRef<jobject>(
      jni::Java_ApplicationContextProvider_getApplicationContext(jni));
}

}  // namespace webrtc
