/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/generated_builtin_audio_codecs_jni/BuiltinAudioEncoderFactoryFactory_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

#include "api/audio_codecs/builtin_audio_encoder_factory.h"

namespace webrtc {
namespace jni {

static jlong
JNI_BuiltinAudioEncoderFactoryFactory_CreateBuiltinAudioEncoderFactory(
    JNIEnv* env) {
  return NativeToJavaPointer(CreateBuiltinAudioEncoderFactory().release());
}

}  // namespace jni
}  // namespace webrtc
