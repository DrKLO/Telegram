/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/rtp_receiver.h"

#include "sdk/android/generated_peerconnection_jni/RtpReceiver_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/pc/media_stream_track.h"
#include "sdk/android/src/jni/pc/rtp_parameters.h"

namespace webrtc {
namespace jni {

namespace {

// Adapter between the C++ RtpReceiverObserverInterface and the Java
// RtpReceiver.Observer interface. Wraps an instance of the Java interface and
// dispatches C++ callbacks to Java.
class RtpReceiverObserverJni : public RtpReceiverObserverInterface {
 public:
  RtpReceiverObserverJni(JNIEnv* env, const JavaRef<jobject>& j_observer)
      : j_observer_global_(env, j_observer) {}

  ~RtpReceiverObserverJni() override = default;

  void OnFirstPacketReceived(cricket::MediaType media_type) override {
    JNIEnv* const env = AttachCurrentThreadIfNeeded();
    Java_Observer_onFirstPacketReceived(env, j_observer_global_,
                                        NativeToJavaMediaType(env, media_type));
  }

 private:
  const ScopedJavaGlobalRef<jobject> j_observer_global_;
};

}  // namespace

ScopedJavaLocalRef<jobject> NativeToJavaRtpReceiver(
    JNIEnv* env,
    rtc::scoped_refptr<RtpReceiverInterface> receiver) {
  // Receiver is now owned by Java object, and will be freed from there.
  return Java_RtpReceiver_Constructor(env,
                                      jlongFromPointer(receiver.release()));
}

JavaRtpReceiverGlobalOwner::JavaRtpReceiverGlobalOwner(
    JNIEnv* env,
    const JavaRef<jobject>& j_receiver)
    : j_receiver_(env, j_receiver) {}

JavaRtpReceiverGlobalOwner::JavaRtpReceiverGlobalOwner(
    JavaRtpReceiverGlobalOwner&& other) = default;

JavaRtpReceiverGlobalOwner::~JavaRtpReceiverGlobalOwner() {
  if (j_receiver_.obj())
    Java_RtpReceiver_dispose(AttachCurrentThreadIfNeeded(), j_receiver_);
}

static jlong JNI_RtpReceiver_GetTrack(JNIEnv* jni,
                                      jlong j_rtp_receiver_pointer) {
  // MediaStreamTrack will have shared ownership by the MediaStreamTrack Java
  // object.
  return jlongFromPointer(
      reinterpret_cast<RtpReceiverInterface*>(j_rtp_receiver_pointer)
          ->track()
          .release());
}

static ScopedJavaLocalRef<jobject> JNI_RtpReceiver_GetParameters(
    JNIEnv* jni,
    jlong j_rtp_receiver_pointer) {
  RtpParameters parameters =
      reinterpret_cast<RtpReceiverInterface*>(j_rtp_receiver_pointer)
          ->GetParameters();
  return NativeToJavaRtpParameters(jni, parameters);
}

static ScopedJavaLocalRef<jstring> JNI_RtpReceiver_GetId(
    JNIEnv* jni,
    jlong j_rtp_receiver_pointer) {
  return NativeToJavaString(
      jni,
      reinterpret_cast<RtpReceiverInterface*>(j_rtp_receiver_pointer)->id());
}

static jlong JNI_RtpReceiver_SetObserver(
    JNIEnv* jni,
    jlong j_rtp_receiver_pointer,
    const JavaParamRef<jobject>& j_observer) {
  RtpReceiverObserverJni* rtpReceiverObserver =
      new RtpReceiverObserverJni(jni, j_observer);
  reinterpret_cast<RtpReceiverInterface*>(j_rtp_receiver_pointer)
      ->SetObserver(rtpReceiverObserver);
  return jlongFromPointer(rtpReceiverObserver);
}

static void JNI_RtpReceiver_UnsetObserver(JNIEnv* jni,
                                          jlong j_rtp_receiver_pointer,
                                          jlong j_observer_pointer) {
  reinterpret_cast<RtpReceiverInterface*>(j_rtp_receiver_pointer)
      ->SetObserver(nullptr);
  RtpReceiverObserverJni* observer =
      reinterpret_cast<RtpReceiverObserverJni*>(j_observer_pointer);
  if (observer) {
    delete observer;
  }
}

static void JNI_RtpReceiver_SetFrameDecryptor(JNIEnv* jni,
                                              jlong j_rtp_sender_pointer,
                                              jlong j_frame_decryptor_pointer) {
  reinterpret_cast<RtpReceiverInterface*>(j_rtp_sender_pointer)
      ->SetFrameDecryptor(reinterpret_cast<FrameDecryptorInterface*>(
          j_frame_decryptor_pointer));
}

}  // namespace jni
}  // namespace webrtc
