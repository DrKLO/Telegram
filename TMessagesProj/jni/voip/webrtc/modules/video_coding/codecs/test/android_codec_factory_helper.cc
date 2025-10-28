/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/android_codec_factory_helper.h"

#include <jni.h>
#include <pthread.h>
#include <stddef.h>

#include <memory>

#include "modules/utility/include/jvm_android.h"
#include "rtc_base/checks.h"
#include "sdk/android/native_api/codecs/wrapper.h"
#include "sdk/android/native_api/jni/class_loader.h"
#include "sdk/android/native_api/jni/jvm.h"
#include "sdk/android/native_api/jni/scoped_java_ref.h"
#include "sdk/android/src/jni/jvm.h"

namespace webrtc {
namespace test {

namespace {

static pthread_once_t g_initialize_once = PTHREAD_ONCE_INIT;

void EnsureInitializedOnce() {
  RTC_CHECK(::webrtc::jni::GetJVM() != nullptr);

  JNIEnv* jni = ::webrtc::jni::AttachCurrentThreadIfNeeded();
  JavaVM* jvm = NULL;
  RTC_CHECK_EQ(0, jni->GetJavaVM(&jvm));

  // Initialize the Java environment (currently only used by the audio manager).
  webrtc::JVM::Initialize(jvm);
}

}  // namespace

void InitializeAndroidObjects() {
  RTC_CHECK_EQ(0, pthread_once(&g_initialize_once, &EnsureInitializedOnce));
}

std::unique_ptr<VideoEncoderFactory> CreateAndroidEncoderFactory() {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jclass> factory_class =
      GetClass(env, "org/webrtc/HardwareVideoEncoderFactory");
  jmethodID factory_constructor = env->GetMethodID(
      factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;ZZ)V");
  ScopedJavaLocalRef<jobject> factory_object(
      env, env->NewObject(factory_class.obj(), factory_constructor,
                          nullptr /* shared_context */,
                          false /* enable_intel_vp8_encoder */,
                          true /* enable_h264_high_profile */));
  return JavaToNativeVideoEncoderFactory(env, factory_object.obj());
}

std::unique_ptr<VideoDecoderFactory> CreateAndroidDecoderFactory() {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jclass> factory_class =
      GetClass(env, "org/webrtc/HardwareVideoDecoderFactory");
  jmethodID factory_constructor = env->GetMethodID(
      factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;)V");
  ScopedJavaLocalRef<jobject> factory_object(
      env, env->NewObject(factory_class.obj(), factory_constructor,
                          nullptr /* shared_context */));
  return JavaToNativeVideoDecoderFactory(env, factory_object.obj());
}

}  // namespace test
}  // namespace webrtc
