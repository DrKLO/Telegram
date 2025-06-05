/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/av1/libaom_av1_encoder.h"

#include <jni.h>

#include "sdk/android/generated_libaom_av1_encoder_jni/LibaomAv1Encoder_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static jlong JNI_LibaomAv1Encoder_CreateEncoder(JNIEnv* jni) {
  return jlongFromPointer(webrtc::CreateLibaomAv1Encoder().release());
}

}  // namespace jni
}  // namespace webrtc
