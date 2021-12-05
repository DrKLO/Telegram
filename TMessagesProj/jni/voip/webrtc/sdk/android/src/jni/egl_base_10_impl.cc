/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <EGL/egl.h>

#include "sdk/android/generated_video_egl_jni/EglBase10Impl_jni.h"

namespace webrtc {
namespace jni {

static jlong JNI_EglBase10Impl_GetCurrentNativeEGLContext(JNIEnv* jni) {
  return reinterpret_cast<jlong>(eglGetCurrentContext());
}

}  // namespace jni
}  // namespace webrtc
