/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/base/init.h"

#include "rtc_base/checks.h"
#include "sdk/android/native_api/jni/class_loader.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {

void InitAndroid(JavaVM* jvm) {
  RTC_CHECK_GE(jni::InitGlobalJniVariables(jvm), 0);
  InitClassLoader(jni::GetEnv());
}

}  // namespace webrtc
