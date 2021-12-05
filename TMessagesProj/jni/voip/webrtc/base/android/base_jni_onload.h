// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_BASE_JNI_ONLOAD_H_
#define BASE_ANDROID_BASE_JNI_ONLOAD_H_

#include <jni.h>
#include <vector>

#include "base/base_export.h"
#include "base/callback.h"

namespace base {
namespace android {

// Returns whether initialization succeeded.
BASE_EXPORT bool OnJNIOnLoadInit();

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_BASE_JNI_ONLOAD_H_
