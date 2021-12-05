/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/wrapped_native_i420_buffer.h"

#include "sdk/android/generated_video_jni/WrappedNativeI420Buffer_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

// TODO(magjed): Write a test for this function.
ScopedJavaLocalRef<jobject> WrapI420Buffer(
    JNIEnv* jni,
    const rtc::scoped_refptr<I420BufferInterface>& i420_buffer) {
  ScopedJavaLocalRef<jobject> y_buffer =
      NewDirectByteBuffer(jni, const_cast<uint8_t*>(i420_buffer->DataY()),
                          i420_buffer->StrideY() * i420_buffer->height());
  ScopedJavaLocalRef<jobject> u_buffer =
      NewDirectByteBuffer(jni, const_cast<uint8_t*>(i420_buffer->DataU()),
                          i420_buffer->StrideU() * i420_buffer->ChromaHeight());
  ScopedJavaLocalRef<jobject> v_buffer =
      NewDirectByteBuffer(jni, const_cast<uint8_t*>(i420_buffer->DataV()),
                          i420_buffer->StrideV() * i420_buffer->ChromaHeight());

  return Java_WrappedNativeI420Buffer_Constructor(
      jni, i420_buffer->width(), i420_buffer->height(), y_buffer,
      i420_buffer->StrideY(), u_buffer, i420_buffer->StrideU(), v_buffer,
      i420_buffer->StrideV(), jlongFromPointer(i420_buffer.get()));
}

}  // namespace jni
}  // namespace webrtc
