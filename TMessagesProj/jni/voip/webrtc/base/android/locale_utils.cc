// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/locale_utils.h"

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/LocaleUtils_jni.h"

namespace base {
namespace android {

std::string GetDefaultCountryCode() {
  JNIEnv* env = base::android::AttachCurrentThread();
  return ConvertJavaStringToUTF8(Java_LocaleUtils_getDefaultCountryCode(env));
}

std::string GetDefaultLocaleString() {
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> locale =
      Java_LocaleUtils_getDefaultLocaleString(env);
  return ConvertJavaStringToUTF8(locale);
}

std::string GetDefaultLocaleListString() {
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jstring> locales =
      Java_LocaleUtils_getDefaultLocaleListString(env);
  return ConvertJavaStringToUTF8(locales);
}

}  // namespace android
}  // namespace base
