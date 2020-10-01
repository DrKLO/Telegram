// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_CALLBACK_ANDROID_H_
#define BASE_ANDROID_CALLBACK_ANDROID_H_

#include <jni.h>
#include <string>
#include <vector>

#include "base/android/scoped_java_ref.h"
#include "base/base_export.h"

// Provides helper utility methods that run the given callback with the
// specified argument.
namespace base {
namespace android {

void BASE_EXPORT RunObjectCallbackAndroid(const JavaRef<jobject>& callback,
                                          const JavaRef<jobject>& arg);

void BASE_EXPORT RunBooleanCallbackAndroid(const JavaRef<jobject>& callback,
                                           bool arg);

void BASE_EXPORT RunIntCallbackAndroid(const JavaRef<jobject>& callback,
                                       int arg);

void BASE_EXPORT RunStringCallbackAndroid(const JavaRef<jobject>& callback,
                                          const std::string& arg);

void BASE_EXPORT RunByteArrayCallbackAndroid(const JavaRef<jobject>& callback,
                                             const std::vector<uint8_t>& arg);

void BASE_EXPORT RunRunnableAndroid(const JavaRef<jobject>& runnable);

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_CALLBACK_ANDROID_H_
