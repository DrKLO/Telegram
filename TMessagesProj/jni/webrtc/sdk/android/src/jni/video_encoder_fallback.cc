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

#include "api/video_codecs/video_encoder_software_fallback_wrapper.h"
#include "sdk/android/generated_video_jni/VideoEncoderFallback_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/video_encoder_wrapper.h"

namespace webrtc {
namespace jni {

static jlong JNI_VideoEncoderFallback_CreateEncoder(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_fallback_encoder,
    const JavaParamRef<jobject>& j_primary_encoder) {
  std::unique_ptr<VideoEncoder> fallback_encoder =
      JavaToNativeVideoEncoder(jni, j_fallback_encoder);
  std::unique_ptr<VideoEncoder> primary_encoder =
      JavaToNativeVideoEncoder(jni, j_primary_encoder);

  VideoEncoder* nativeWrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(std::move(fallback_encoder),
                                                std::move(primary_encoder))
          .release();

  return jlongFromPointer(nativeWrapper);
}

}  // namespace jni
}  // namespace webrtc
