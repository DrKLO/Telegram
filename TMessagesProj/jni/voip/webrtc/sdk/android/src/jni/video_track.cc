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

#include "api/media_stream_interface.h"
#include "sdk/android/generated_video_jni/VideoTrack_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/video_sink.h"

namespace webrtc {
namespace jni {

static void JNI_VideoTrack_AddSink(JNIEnv* jni,
                                   jlong j_native_track,
                                   jlong j_native_sink) {
  reinterpret_cast<VideoTrackInterface*>(j_native_track)
      ->AddOrUpdateSink(
          reinterpret_cast<rtc::VideoSinkInterface<VideoFrame>*>(j_native_sink),
          rtc::VideoSinkWants());
}

static void JNI_VideoTrack_RemoveSink(JNIEnv* jni,
                                      jlong j_native_track,
                                      jlong j_native_sink) {
  reinterpret_cast<VideoTrackInterface*>(j_native_track)
      ->RemoveSink(reinterpret_cast<rtc::VideoSinkInterface<VideoFrame>*>(
          j_native_sink));
}

static jlong JNI_VideoTrack_WrapSink(JNIEnv* jni,
                                     const JavaParamRef<jobject>& sink) {
  return jlongFromPointer(new VideoSinkWrapper(jni, sink));
}

static void JNI_VideoTrack_FreeSink(JNIEnv* jni, jlong j_native_sink) {
  delete reinterpret_cast<rtc::VideoSinkInterface<VideoFrame>*>(j_native_sink);
}

}  // namespace jni
}  // namespace webrtc
