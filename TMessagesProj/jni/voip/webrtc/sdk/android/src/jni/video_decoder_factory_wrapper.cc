/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_decoder_factory_wrapper.h"

#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder.h"
#include "rtc_base/logging.h"
#include "sdk/android/generated_video_jni/VideoDecoderFactory_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/video_codec_info.h"
#include "sdk/android/src/jni/video_decoder_wrapper.h"

namespace webrtc {
namespace jni {

VideoDecoderFactoryWrapper::VideoDecoderFactoryWrapper(
    JNIEnv* jni,
    const JavaRef<jobject>& decoder_factory)
    : decoder_factory_(jni, decoder_factory) {}
VideoDecoderFactoryWrapper::~VideoDecoderFactoryWrapper() = default;

std::unique_ptr<VideoDecoder> VideoDecoderFactoryWrapper::CreateVideoDecoder(
    const SdpVideoFormat& format) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_codec_info =
      SdpVideoFormatToVideoCodecInfo(jni, format);
  ScopedJavaLocalRef<jobject> decoder = Java_VideoDecoderFactory_createDecoder(
      jni, decoder_factory_, j_codec_info);
  if (!decoder.obj())
    return nullptr;
  return JavaToNativeVideoDecoder(jni, decoder);
}

std::vector<SdpVideoFormat> VideoDecoderFactoryWrapper::GetSupportedFormats()
    const {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  return JavaToNativeVector<SdpVideoFormat>(
      env, Java_VideoDecoderFactory_getSupportedCodecs(env, decoder_factory_),
      &VideoCodecInfoToSdpVideoFormat);
}

}  // namespace jni
}  // namespace webrtc
