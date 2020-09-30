/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/video/wrapper.h"

#include <memory>

#include "sdk/android/native_api/jni/scoped_java_ref.h"
#include "sdk/android/src/jni/video_frame.h"
#include "sdk/android/src/jni/video_sink.h"

namespace webrtc {

std::unique_ptr<rtc::VideoSinkInterface<VideoFrame>> JavaToNativeVideoSink(
    JNIEnv* jni,
    jobject video_sink) {
  return std::make_unique<jni::VideoSinkWrapper>(
      jni, JavaParamRef<jobject>(video_sink));
}

ScopedJavaLocalRef<jobject> NativeToJavaVideoFrame(JNIEnv* jni,
                                                   const VideoFrame& frame) {
  return jni::NativeToJavaVideoFrame(jni, frame);
}

}  // namespace webrtc
