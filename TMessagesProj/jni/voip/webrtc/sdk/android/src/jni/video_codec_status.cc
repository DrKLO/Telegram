/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_codec_status.h"

#include "sdk/android/generated_video_jni/VideoCodecStatus_jni.h"

namespace webrtc {
namespace jni {

int32_t JavaToNativeVideoCodecStatus(
    JNIEnv* env,
    const JavaRef<jobject>& j_video_codec_status) {
  return Java_VideoCodecStatus_getNumber(env, j_video_codec_status);
}

}  // namespace jni
}  // namespace webrtc
