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

#include <pthread.h>

#include "rtc_base/ignore_wundef.h"

// Note: this dependency is dangerous since it reaches into Chromium's base.
// There's a risk of e.g. macro clashes. This file may only be used in tests.
RTC_PUSH_IGNORING_WUNDEF()
#include "base/android/jni_android.h"
RTC_POP_IGNORING_WUNDEF()
#include "modules/audio_device/android/audio_record_jni.h"
#include "modules/audio_device/android/audio_track_jni.h"
#include "modules/utility/include/jvm_android.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace audiodevicemodule {

static pthread_once_t g_initialize_once = PTHREAD_ONCE_INIT;

void EnsureInitializedOnce() {
  RTC_CHECK(::base::android::IsVMInitialized());
  JNIEnv* jni = ::base::android::AttachCurrentThread();
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
