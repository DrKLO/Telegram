/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/test/android_test_initializer.h"

#include <jni.h>
#include <pthread.h>
#include <stddef.h>

#include "modules/utility/include/jvm_android.h"
#include "rtc_base/checks.h"
#include "sdk/android/src/jni/jvm.h"
// TODO(phoglund): This include is to a target we can't really depend on.
// We need to either break it out into a smaller target or find some way to
// not use it.
#include "rtc_base/ssl_adapter.h"

namespace webrtc {

namespace {

static pthread_once_t g_initialize_once = PTHREAD_ONCE_INIT;

// There can only be one JNI_OnLoad in each binary. So since this is a GTEST
// C++ runner binary, we want to initialize the same global objects we normally
// do if this had been a Java binary.
void EnsureInitializedOnce() {
  RTC_CHECK(::webrtc::jni::GetJVM() != nullptr);
  JNIEnv* jni = ::webrtc::jni::AttachCurrentThreadIfNeeded();
  JavaVM* jvm = NULL;
  RTC_CHECK_EQ(0, jni->GetJavaVM(&jvm));

  RTC_CHECK(rtc::InitializeSSL()) << "Failed to InitializeSSL()";

  JVM::Initialize(jvm);
}

}  // anonymous namespace

void InitializeAndroidObjects() {
  RTC_CHECK_EQ(0, pthread_once(&g_initialize_once, &EnsureInitializedOnce));
}

}  // namespace webrtc
