/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/scoped_java_ref_counted.h"

#include "sdk/android/generated_base_jni/RefCounted_jni.h"

namespace webrtc {
namespace jni {

// static
ScopedJavaRefCounted ScopedJavaRefCounted::Retain(
    JNIEnv* jni,
    const JavaRef<jobject>& j_object) {
  Java_RefCounted_retain(jni, j_object);
  CHECK_EXCEPTION(jni)
      << "Unexpected java exception from java JavaRefCounted.retain()";
  return Adopt(jni, j_object);
}

ScopedJavaRefCounted::~ScopedJavaRefCounted() {
  if (!j_object_.is_null()) {
    JNIEnv* jni = AttachCurrentThreadIfNeeded();
    Java_RefCounted_release(jni, j_object_);
    CHECK_EXCEPTION(jni)
        << "Unexpected java exception from java RefCounted.release()";
  }
}

}  // namespace jni
}  // namespace webrtc
