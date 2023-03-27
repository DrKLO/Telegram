/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/video/video_source.h"

#include "sdk/android/src/jni/android_video_track_source.h"
#include "sdk/android/src/jni/native_capturer_observer.h"

namespace webrtc {

namespace {

// Hides full jni::AndroidVideoTrackSource interface and provides an instance of
// NativeCapturerObserver associated with the video source. Does not extend
// AndroidVideoTrackSource to avoid diamond inheritance on
// VideoTrackSourceInterface.
class JavaVideoTrackSourceImpl : public JavaVideoTrackSourceInterface {
 public:
  JavaVideoTrackSourceImpl(JNIEnv* env,
                           rtc::Thread* signaling_thread,
                           bool is_screencast,
                           bool align_timestamps)
      : android_video_track_source_(
            rtc::make_ref_counted<jni::AndroidVideoTrackSource>(
                signaling_thread,
                env,
                is_screencast,
                align_timestamps)),
        native_capturer_observer_(jni::CreateJavaNativeCapturerObserver(
            env,
            android_video_track_source_)) {}

  ScopedJavaLocalRef<jobject> GetJavaVideoCapturerObserver(
      JNIEnv* env) override {
    return ScopedJavaLocalRef<jobject>(env, native_capturer_observer_);
  }

  // Delegate VideoTrackSourceInterface methods to android_video_track_source_.
  void RegisterObserver(ObserverInterface* observer) override {
    android_video_track_source_->RegisterObserver(observer);
  }

  void UnregisterObserver(ObserverInterface* observer) override {
    android_video_track_source_->UnregisterObserver(observer);
  }

  SourceState state() const override {
    return android_video_track_source_->state();
  }

  bool remote() const override { return android_video_track_source_->remote(); }

  void AddOrUpdateSink(rtc::VideoSinkInterface<VideoFrame>* sink,
                       const rtc::VideoSinkWants& wants) override {
    // The method is defined private in the implementation so we have to access
    // it through the interface...
    static_cast<VideoTrackSourceInterface*>(android_video_track_source_.get())
        ->AddOrUpdateSink(sink, wants);
  }

  void RemoveSink(rtc::VideoSinkInterface<VideoFrame>* sink) override {
    // The method is defined private in the implementation so we have to access
    // it through the interface...
    static_cast<VideoTrackSourceInterface*>(android_video_track_source_.get())
        ->RemoveSink(sink);
  }

  bool is_screencast() const override {
    return android_video_track_source_->is_screencast();
  }

  absl::optional<bool> needs_denoising() const override {
    return android_video_track_source_->needs_denoising();
  }

  bool GetStats(Stats* stats) override {
    // The method is defined private in the implementation so we have to access
    // it through the interface...
    return static_cast<VideoTrackSourceInterface*>(
               android_video_track_source_.get())
        ->GetStats(stats);
  }

 private:
  // Encoded sinks not implemented for JavaVideoTrackSourceImpl.
  bool SupportsEncodedOutput() const override { return false; }
  void GenerateKeyFrame() override {}
  void AddEncodedSink(
      rtc::VideoSinkInterface<webrtc::RecordableEncodedFrame>* sink) override {}
  void RemoveEncodedSink(
      rtc::VideoSinkInterface<webrtc::RecordableEncodedFrame>* sink) override {}

  rtc::scoped_refptr<jni::AndroidVideoTrackSource> android_video_track_source_;
  ScopedJavaGlobalRef<jobject> native_capturer_observer_;
};

}  // namespace

rtc::scoped_refptr<JavaVideoTrackSourceInterface> CreateJavaVideoSource(
    JNIEnv* jni,
    rtc::Thread* signaling_thread,
    bool is_screencast,
    bool align_timestamps) {
  return rtc::make_ref_counted<JavaVideoTrackSourceImpl>(
      jni, signaling_thread, is_screencast, align_timestamps);
}

}  // namespace webrtc
