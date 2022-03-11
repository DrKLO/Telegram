/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/android_video_track_source.h"

#include <utility>

#include "rtc_base/logging.h"
#include "sdk/android/generated_video_jni/NativeAndroidVideoTrackSource_jni.h"
#include "sdk/android/src/jni/video_frame.h"

namespace webrtc {
namespace jni {

namespace {
// MediaCodec wants resolution to be divisible by 2.
const int kRequiredResolutionAlignment = 2;

VideoRotation jintToVideoRotation(jint rotation) {
  RTC_DCHECK(rotation == 0 || rotation == 90 || rotation == 180 ||
             rotation == 270);
  return static_cast<VideoRotation>(rotation);
}

absl::optional<std::pair<int, int>> OptionalAspectRatio(jint j_width,
                                                        jint j_height) {
  if (j_width > 0 && j_height > 0)
    return std::pair<int, int>(j_width, j_height);
  return absl::nullopt;
}

}  // namespace

AndroidVideoTrackSource::AndroidVideoTrackSource(rtc::Thread* signaling_thread,
                                                 JNIEnv* jni,
                                                 bool is_screencast,
                                                 bool align_timestamps)
    : AdaptedVideoTrackSource(kRequiredResolutionAlignment),
      signaling_thread_(signaling_thread),
      is_screencast_(is_screencast),
      align_timestamps_(align_timestamps) {
  RTC_LOG(LS_INFO) << "AndroidVideoTrackSource ctor";
}
AndroidVideoTrackSource::~AndroidVideoTrackSource() = default;

bool AndroidVideoTrackSource::is_screencast() const {
  return is_screencast_.load();
}

absl::optional<bool> AndroidVideoTrackSource::needs_denoising() const {
  return false;
}

void AndroidVideoTrackSource::SetState(JNIEnv* env,
                                       jboolean j_is_live) {
  const SourceState state = j_is_live ? kLive : kEnded;
  if (state_.exchange(state) != state) {
    if (rtc::Thread::Current() == signaling_thread_) {
      FireOnChanged();
    } else {
      // TODO(sakal): Is this even necessary, does FireOnChanged have to be
      // called from signaling thread?
      signaling_thread_->PostTask([this] { FireOnChanged(); });
    }
  }
}

AndroidVideoTrackSource::SourceState AndroidVideoTrackSource::state() const {
  return state_.load();
}

bool AndroidVideoTrackSource::remote() const {
  return false;
}

void AndroidVideoTrackSource::SetIsScreencast(JNIEnv* env,
                                              jboolean j_is_screencast) {
  is_screencast_.store(j_is_screencast);
}

ScopedJavaLocalRef<jobject> AndroidVideoTrackSource::AdaptFrame(
    JNIEnv* env,
    jint j_width,
    jint j_height,
    jint j_rotation,
    jlong j_timestamp_ns) {
  const VideoRotation rotation = jintToVideoRotation(j_rotation);

  const int64_t camera_time_us = j_timestamp_ns / rtc::kNumNanosecsPerMicrosec;
  const int64_t aligned_timestamp_ns =
      align_timestamps_ ? rtc::kNumNanosecsPerMicrosec *
                              timestamp_aligner_.TranslateTimestamp(
                                  camera_time_us, rtc::TimeMicros())
                        : j_timestamp_ns;

  int adapted_width = 0;
  int adapted_height = 0;
  int crop_width = 0;
  int crop_height = 0;
  int crop_x = 0;
  int crop_y = 0;
  bool drop;

  // TODO(magjed): Move this logic to users of NativeAndroidVideoTrackSource
  // instead, in order to keep this native wrapping layer as thin as possible.
  if (rotation % 180 == 0) {
    drop = !rtc::AdaptedVideoTrackSource::AdaptFrame(
        j_width, j_height, camera_time_us, &adapted_width, &adapted_height,
        &crop_width, &crop_height, &crop_x, &crop_y);
  } else {
    // Swap all width/height and x/y.
    drop = !rtc::AdaptedVideoTrackSource::AdaptFrame(
        j_height, j_width, camera_time_us, &adapted_height, &adapted_width,
        &crop_height, &crop_width, &crop_y, &crop_x);
  }

  return Java_NativeAndroidVideoTrackSource_createFrameAdaptationParameters(
      env, crop_x, crop_y, crop_width, crop_height, adapted_width,
      adapted_height, aligned_timestamp_ns, drop);
}

void AndroidVideoTrackSource::OnFrameCaptured(
    JNIEnv* env,
    jint j_rotation,
    jlong j_timestamp_ns,
    const JavaRef<jobject>& j_video_frame_buffer) {
  rtc::scoped_refptr<VideoFrameBuffer> buffer =
      JavaToNativeFrameBuffer(env, j_video_frame_buffer);
  const VideoRotation rotation = jintToVideoRotation(j_rotation);

  // AdaptedVideoTrackSource handles applying rotation for I420 frames.
  if (apply_rotation() && rotation != kVideoRotation_0)
    buffer = buffer->ToI420();

  OnFrame(VideoFrame::Builder()
              .set_video_frame_buffer(buffer)
              .set_rotation(rotation)
              .set_timestamp_us(j_timestamp_ns / rtc::kNumNanosecsPerMicrosec)
              .build());
}

void AndroidVideoTrackSource::AdaptOutputFormat(
    JNIEnv* env,
    jint j_landscape_width,
    jint j_landscape_height,
    const JavaRef<jobject>& j_max_landscape_pixel_count,
    jint j_portrait_width,
    jint j_portrait_height,
    const JavaRef<jobject>& j_max_portrait_pixel_count,
    const JavaRef<jobject>& j_max_fps) {
  video_adapter()->OnOutputFormatRequest(
      OptionalAspectRatio(j_landscape_width, j_landscape_height),
      JavaToNativeOptionalInt(env, j_max_landscape_pixel_count),
      OptionalAspectRatio(j_portrait_width, j_portrait_height),
      JavaToNativeOptionalInt(env, j_max_portrait_pixel_count),
      JavaToNativeOptionalInt(env, j_max_fps));
}

}  // namespace jni
}  // namespace webrtc
