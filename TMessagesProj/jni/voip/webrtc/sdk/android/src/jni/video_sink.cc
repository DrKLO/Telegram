/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_sink.h"

#include "sdk/android/generated_video_jni/VideoSink_jni.h"
#include "sdk/android/src/jni/video_frame.h"

namespace webrtc {
namespace jni {

VideoSinkWrapper::VideoSinkWrapper(JNIEnv* jni, const JavaRef<jobject>& j_sink)
    : j_sink_(jni, j_sink) {}

VideoSinkWrapper::~VideoSinkWrapper() {}

void VideoSinkWrapper::OnFrame(const VideoFrame& frame) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_frame = NativeToJavaVideoFrame(jni, frame);
  Java_VideoSink_onFrame(jni, j_sink_, j_frame);
  ReleaseJavaVideoFrame(jni, j_frame);
}

}  // namespace jni
}  // namespace webrtc
