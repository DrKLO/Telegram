/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/codecs/wrapper.h"

#include <memory>

#include "sdk/android/native_api/jni/scoped_java_ref.h"
#include "sdk/android/src/jni/video_codec_info.h"
#include "sdk/android/src/jni/video_decoder_factory_wrapper.h"
#include "sdk/android/src/jni/video_encoder_factory_wrapper.h"
#include "sdk/android/src/jni/video_encoder_wrapper.h"

namespace webrtc {

SdpVideoFormat JavaToNativeVideoCodecInfo(JNIEnv* jni, jobject codec_info) {
  return jni::VideoCodecInfoToSdpVideoFormat(jni,
                                             JavaParamRef<jobject>(codec_info));
}

std::unique_ptr<VideoDecoderFactory> JavaToNativeVideoDecoderFactory(
    JNIEnv* jni,
    jobject decoder_factory) {
  return std::make_unique<jni::VideoDecoderFactoryWrapper>(
      jni, JavaParamRef<jobject>(decoder_factory));
}

std::unique_ptr<VideoEncoderFactory> JavaToNativeVideoEncoderFactory(
    JNIEnv* jni,
    jobject encoder_factory) {
  return std::make_unique<jni::VideoEncoderFactoryWrapper>(
      jni, JavaParamRef<jobject>(encoder_factory));
}

std::vector<VideoEncoder::ResolutionBitrateLimits>
JavaToNativeResolutionBitrateLimits(JNIEnv* jni,
                                    const jobjectArray j_bitrate_limits_array) {
  return jni::JavaToNativeResolutionBitrateLimits(
      jni, JavaParamRef<jobjectArray>(j_bitrate_limits_array));
}

}  // namespace webrtc
