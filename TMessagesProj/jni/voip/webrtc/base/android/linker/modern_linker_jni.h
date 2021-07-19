// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_LINKER_MODERN_LINKER_JNI_H_
#define BASE_ANDROID_LINKER_MODERN_LINKER_JNI_H_

#include <jni.h>

namespace chromium_android_linker {

// JNI_OnLoad() initialization hook for the modern linker.
// Sets up JNI and other initializations for native linker code.
// |vm| is the Java VM handle passed to JNI_OnLoad().
// |env| is the current JNI environment handle.
// On success, returns true.
extern bool ModernLinkerJNIInit(JavaVM* vm, JNIEnv* env);

}  // namespace chromium_android_linker

#endif  // BASE_ANDROID_LINKER_MODERN_LINKER_JNI_H_
