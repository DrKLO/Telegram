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

#include "modules/video_coding/codecs/av1/libaom_av1_decoder.h"
#include "modules/video_coding/codecs/av1/libaom_av1_encoder_supported.h"
#include "sdk/android/generated_libaom_av1_jni/LibaomAv1Decoder_jni.h"
#include "sdk/android/generated_libaom_av1_jni/LibaomAv1Encoder_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static jlong JNI_LibaomAv1Encoder_CreateEncoder(JNIEnv* jni) {
  return jlongFromPointer(
      webrtc::CreateLibaomAv1EncoderIfSupported().release());
}

static jboolean JNI_LibaomAv1Encoder_IsSupported(JNIEnv* jni) {
  return webrtc::kIsLibaomAv1EncoderSupported;
}

static jlong JNI_LibaomAv1Decoder_CreateDecoder(JNIEnv* jni) {
  return jlongFromPointer(webrtc::CreateLibaomAv1Decoder().release());
}

static jboolean JNI_LibaomAv1Decoder_IsSupported(JNIEnv* jni) {
  return webrtc::kIsLibaomAv1DecoderSupported;
}

}  // namespace jni
}  // namespace webrtc
