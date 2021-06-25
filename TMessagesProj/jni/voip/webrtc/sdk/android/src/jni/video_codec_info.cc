/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_codec_info.h"

#include "sdk/android/generated_video_jni/VideoCodecInfo_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

SdpVideoFormat VideoCodecInfoToSdpVideoFormat(JNIEnv* jni,
                                              const JavaRef<jobject>& j_info) {
  std::string codecName =
      JavaToNativeString(jni, Java_VideoCodecInfo_getName(jni, j_info));
  std::string sdpCodecName;
  if (codecName == "AV1") {
    // TODO(yyaroshevich): Undo mapping once AV1 sdp name is standardized
    sdpCodecName = "AV1X";
  } else {
    sdpCodecName = codecName;
  }
  return SdpVideoFormat(
      sdpCodecName,
      JavaToNativeStringMap(jni, Java_VideoCodecInfo_getParams(jni, j_info)));
}

ScopedJavaLocalRef<jobject> SdpVideoFormatToVideoCodecInfo(
    JNIEnv* jni,
    const SdpVideoFormat& format) {
  std::string codecName;
  if (format.name == "AV1X" || format.name == "AV1") {
    codecName = "AV1";
  } else {
    codecName = format.name;
  }
  ScopedJavaLocalRef<jobject> j_params =
      NativeToJavaStringMap(jni, format.parameters);
  return Java_VideoCodecInfo_Constructor(
      jni, NativeToJavaString(jni, codecName), j_params);
}

}  // namespace jni
}  // namespace webrtc
