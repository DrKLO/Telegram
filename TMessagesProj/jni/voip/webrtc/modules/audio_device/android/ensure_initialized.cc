/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/android/ensure_initialized.h"

#include <jni.h>
#include <pthread.h>
#include <stddef.h>

#include "modules/utility/include/jvm_android.h"
#include "rtc_base/checks.h"
#include "sdk/android/src/jni/jvm.h"

namespace webrtc {
namespace audiodevicemodule {

static pthread_once_t g_initialize_once = PTHREAD_ONCE_INIT;

void EnsureInitializedOnce() {
  RTC_CHECK(::webrtc::jni::GetJVM() != nullptr);

  JNIEnv* jni = ::webrtc::jni::AttachCurrentThreadIfNeeded();
  JavaVM* jvm = NULL;
  RTC_CHECK_EQ(0, jni->GetJavaVM(&jvm));

  // Initialize the Java environment (currently only used by the audio manager).
  webrtc::JVM::Initialize(jvm);
}

void EnsureInitialized() {
  RTC_CHECK_EQ(0, pthread_once(&g_initialize_once, &EnsureInitializedOnce));
}

}  // namespace audiodevicemodule
}  // namespace webrtc
