/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "api/video_codecs/video_encoder.h"
#include "sdk/android/generated_swcodecs_jni/SoftwareVideoEncoderFactory_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/video_codec_info.h"

namespace webrtc {
namespace jni {

static jlong JNI_SoftwareVideoEncoderFactory_CreateFactory(JNIEnv* env) {
  return webrtc::NativeToJavaPointer(
      CreateBuiltinVideoEncoderFactory().release());
}

static jlong JNI_SoftwareVideoEncoderFactory_CreateEncoder(
    JNIEnv* env,
    jlong j_factory,
    const webrtc::JavaParamRef<jobject>& j_video_codec_info) {
  auto* const native_factory =
      reinterpret_cast<webrtc::VideoEncoderFactory*>(j_factory);
  const auto video_format =
      webrtc::jni::VideoCodecInfoToSdpVideoFormat(env, j_video_codec_info);

  auto encoder = native_factory->CreateVideoEncoder(video_format);
  if (encoder == nullptr) {
    return 0;
  }
  return webrtc::NativeToJavaPointer(encoder.release());
}

static webrtc::ScopedJavaLocalRef<jobject>
JNI_SoftwareVideoEncoderFactory_GetSupportedCodecs(JNIEnv* env,
                                                   jlong j_factory) {
  auto* const native_factory =
      reinterpret_cast<webrtc::VideoEncoderFactory*>(j_factory);

  return webrtc::NativeToJavaList(env, native_factory->GetSupportedFormats(),
                                  &webrtc::jni::SdpVideoFormatToVideoCodecInfo);
}

}  // namespace jni
}  // namespace webrtc
