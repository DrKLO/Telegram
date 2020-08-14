/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/media_stream.h"

#include <memory>

#include "sdk/android/generated_peerconnection_jni/MediaStream_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

JavaMediaStream::JavaMediaStream(
    JNIEnv* env,
    rtc::scoped_refptr<MediaStreamInterface> media_stream)
    : j_media_stream_(
          env,
          Java_MediaStream_Constructor(env,
                                       jlongFromPointer(media_stream.get()))),
      observer_(std::make_unique<MediaStreamObserver>(media_stream)) {
  for (rtc::scoped_refptr<AudioTrackInterface> track :
       media_stream->GetAudioTracks()) {
    Java_MediaStream_addNativeAudioTrack(env, j_media_stream_,
                                         jlongFromPointer(track.release()));
  }
  for (rtc::scoped_refptr<VideoTrackInterface> track :
       media_stream->GetVideoTracks()) {
    Java_MediaStream_addNativeVideoTrack(env, j_media_stream_,
                                         jlongFromPointer(track.release()));
  }

  // Create an observer to update the Java stream when the native stream's set
  // of tracks changes.
  observer_->SignalAudioTrackRemoved.connect(
      this, &JavaMediaStream::OnAudioTrackRemovedFromStream);
  observer_->SignalVideoTrackRemoved.connect(
      this, &JavaMediaStream::OnVideoTrackRemovedFromStream);
  observer_->SignalAudioTrackAdded.connect(
      this, &JavaMediaStream::OnAudioTrackAddedToStream);
  observer_->SignalVideoTrackAdded.connect(
      this, &JavaMediaStream::OnVideoTrackAddedToStream);

  // |j_media_stream| holds one reference. Corresponding Release() is in
  // MediaStream_free, triggered by MediaStream.dispose().
  media_stream.release();
}

JavaMediaStream::~JavaMediaStream() {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  // Remove the observer first, so it doesn't react to events during deletion.
  observer_ = nullptr;
  Java_MediaStream_dispose(env, j_media_stream_);
}

void JavaMediaStream::OnAudioTrackAddedToStream(AudioTrackInterface* track,
                                                MediaStreamInterface* stream) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedLocalRefFrame local_ref_frame(env);
  track->AddRef();
  Java_MediaStream_addNativeAudioTrack(env, j_media_stream_,
                                       jlongFromPointer(track));
}

void JavaMediaStream::OnVideoTrackAddedToStream(VideoTrackInterface* track,
                                                MediaStreamInterface* stream) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedLocalRefFrame local_ref_frame(env);
  track->AddRef();
  Java_MediaStream_addNativeVideoTrack(env, j_media_stream_,
                                       jlongFromPointer(track));
}

void JavaMediaStream::OnAudioTrackRemovedFromStream(
    AudioTrackInterface* track,
    MediaStreamInterface* stream) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedLocalRefFrame local_ref_frame(env);
  Java_MediaStream_removeAudioTrack(env, j_media_stream_,
                                    jlongFromPointer(track));
}

void JavaMediaStream::OnVideoTrackRemovedFromStream(
    VideoTrackInterface* track,
    MediaStreamInterface* stream) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedLocalRefFrame local_ref_frame(env);
  Java_MediaStream_removeVideoTrack(env, j_media_stream_,
                                    jlongFromPointer(track));
}

jclass GetMediaStreamClass(JNIEnv* env) {
  return org_webrtc_MediaStream_clazz(env);
}

static jboolean JNI_MediaStream_AddAudioTrackToNativeStream(
    JNIEnv* jni,
    jlong pointer,
    jlong j_audio_track_pointer) {
  return reinterpret_cast<MediaStreamInterface*>(pointer)->AddTrack(
      reinterpret_cast<AudioTrackInterface*>(j_audio_track_pointer));
}

static jboolean JNI_MediaStream_AddVideoTrackToNativeStream(
    JNIEnv* jni,
    jlong pointer,
    jlong j_video_track_pointer) {
  return reinterpret_cast<MediaStreamInterface*>(pointer)->AddTrack(
      reinterpret_cast<VideoTrackInterface*>(j_video_track_pointer));
}

static jboolean JNI_MediaStream_RemoveAudioTrack(JNIEnv* jni,
                                                 jlong pointer,
                                                 jlong j_audio_track_pointer) {
  return reinterpret_cast<MediaStreamInterface*>(pointer)->RemoveTrack(
      reinterpret_cast<AudioTrackInterface*>(j_audio_track_pointer));
}

static jboolean JNI_MediaStream_RemoveVideoTrack(JNIEnv* jni,
                                                 jlong pointer,
                                                 jlong j_video_track_pointer) {
  return reinterpret_cast<MediaStreamInterface*>(pointer)->RemoveTrack(
      reinterpret_cast<VideoTrackInterface*>(j_video_track_pointer));
}

static ScopedJavaLocalRef<jstring> JNI_MediaStream_GetId(JNIEnv* jni,
                                                         jlong j_p) {
  return NativeToJavaString(jni,
                            reinterpret_cast<MediaStreamInterface*>(j_p)->id());
}

}  // namespace jni
}  // namespace webrtc
