/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "sdk/android/generated_builtin_audio_codecs_jni/BuiltinAudioDecoderFactoryFactory_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

static jlong
JNI_BuiltinAudioDecoderFactoryFactory_CreateBuiltinAudioDecoderFactory(
    JNIEnv* env) {
  return NativeToJavaPointer(CreateBuiltinAudioDecoderFactory().release());
}

}  // namespace jni
}  // namespace webrtc
