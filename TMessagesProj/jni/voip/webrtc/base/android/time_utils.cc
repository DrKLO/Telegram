// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <stdint.h>

#include "base/base_jni_headers/TimeUtils_jni.h"
#include "base/time/time.h"

namespace base {
namespace android {

static jlong JNI_TimeUtils_GetTimeTicksNowUs(JNIEnv* env) {
  return (TimeTicks::Now() - TimeTicks()).InMicroseconds();
}

}  // namespace android
}  // namespace base
