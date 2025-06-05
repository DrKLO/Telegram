/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/ref_count.h"
#include "sdk/android/generated_base_jni/JniCommon_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static void JNI_JniCommon_AddRef(JNIEnv* jni,
                                 jlong j_native_ref_counted_pointer) {
  reinterpret_cast<rtc::RefCountInterface*>(j_native_ref_counted_pointer)
      ->AddRef();
}

static void JNI_JniCommon_ReleaseRef(JNIEnv* jni,
                                     jlong j_native_ref_counted_pointer) {
  reinterpret_cast<rtc::RefCountInterface*>(j_native_ref_counted_pointer)
      ->Release();
}

static ScopedJavaLocalRef<jobject> JNI_JniCommon_AllocateByteBuffer(JNIEnv* jni,
                                                                    jint size) {
  void* new_data = ::operator new(size);
  return NewDirectByteBuffer(jni, new_data, size);
}

static void JNI_JniCommon_FreeByteBuffer(
    JNIEnv* jni,
    const JavaParamRef<jobject>& byte_buffer) {
  void* data = jni->GetDirectBufferAddress(byte_buffer.obj());
  ::operator delete(data);
}

}  // namespace jni
}  // namespace webrtc
