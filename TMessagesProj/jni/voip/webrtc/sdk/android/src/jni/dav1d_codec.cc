/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <jni.h>

#include "modules/video_coding/codecs/av1/dav1d_decoder.h"
#include "sdk/android/generated_dav1d_jni/Dav1dDecoder_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static jlong JNI_Dav1dDecoder_CreateDecoder(JNIEnv* jni) {
  return jlongFromPointer(webrtc::CreateDav1dDecoder().release());
}

}  // namespace jni
}  // namespace webrtc
