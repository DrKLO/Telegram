/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <jni.h>

#include "api/environment/environment.h"
#include "api/video_codecs/video_decoder_software_fallback_wrapper.h"
#include "sdk/android/generated_video_jni/VideoDecoderFallback_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/video_decoder_wrapper.h"

namespace webrtc {
namespace jni {

static jlong JNI_VideoDecoderFallback_Create(
    JNIEnv* jni,
    jlong j_webrtc_env_ref,
    const JavaParamRef<jobject>& j_fallback_decoder,
    const JavaParamRef<jobject>& j_primary_decoder) {
  std::unique_ptr<VideoDecoder> fallback_decoder =
      JavaToNativeVideoDecoder(jni, j_fallback_decoder, j_webrtc_env_ref);
  std::unique_ptr<VideoDecoder> primary_decoder =
      JavaToNativeVideoDecoder(jni, j_primary_decoder, j_webrtc_env_ref);

  VideoDecoder* native_wrapper =
      CreateVideoDecoderSoftwareFallbackWrapper(
          *reinterpret_cast<const Environment*>(j_webrtc_env_ref),
          std::move(fallback_decoder), std::move(primary_decoder))
          .release();

  return NativeToJavaPointer(native_wrapper);
}

}  // namespace jni
}  // namespace webrtc
