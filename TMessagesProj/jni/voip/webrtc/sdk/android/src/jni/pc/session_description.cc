/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/session_description.h"

#include <string>

#include "rtc_base/logging.h"
#include "sdk/android/generated_peerconnection_jni/SessionDescription_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

std::unique_ptr<SessionDescriptionInterface> JavaToNativeSessionDescription(
    JNIEnv* jni,
    const JavaRef<jobject>& j_sdp) {
  std::string std_type = JavaToStdString(
      jni, Java_SessionDescription_getTypeInCanonicalForm(jni, j_sdp));
  std::string std_description =
      JavaToStdString(jni, Java_SessionDescription_getDescription(jni, j_sdp));
  absl::optional<SdpType> sdp_type_maybe = SdpTypeFromString(std_type);
  if (!sdp_type_maybe) {
    RTC_LOG(LS_ERROR) << "Unexpected SDP type: " << std_type;
    return nullptr;
  }
  return CreateSessionDescription(*sdp_type_maybe, std_description);
}

ScopedJavaLocalRef<jobject> NativeToJavaSessionDescription(
    JNIEnv* jni,
    const std::string& sdp,
    const std::string& type) {
  return Java_SessionDescription_Constructor(
      jni, Java_Type_fromCanonicalForm(jni, NativeToJavaString(jni, type)),
      NativeToJavaString(jni, sdp));
}

}  // namespace jni
}  // namespace webrtc
