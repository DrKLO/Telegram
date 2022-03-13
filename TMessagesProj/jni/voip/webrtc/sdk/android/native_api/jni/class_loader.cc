/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/native_api/jni/class_loader.h"

#include <algorithm>
#include <string>

#include "rtc_base/checks.h"
#include "sdk/android/generated_native_api_jni/WebRtcClassLoader_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/native_api/jni/scoped_java_ref.h"

// Abort the process if `jni` has a Java exception pending. This macros uses the
// comma operator to execute ExceptionDescribe and ExceptionClear ignoring their
// return values and sending "" to the error stream.
#define CHECK_EXCEPTION(jni)        \
  RTC_CHECK(!jni->ExceptionCheck()) \
      << (jni->ExceptionDescribe(), jni->ExceptionClear(), "")

namespace webrtc {

namespace {

class ClassLoader {
 public:
  explicit ClassLoader(JNIEnv* env)
      : class_loader_(jni::Java_WebRtcClassLoader_getClassLoader(env)) {
    class_loader_class_ = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("java/lang/ClassLoader")));
    CHECK_EXCEPTION(env);
    load_class_method_ =
        env->GetMethodID(class_loader_class_, "loadClass",
                         "(Ljava/lang/String;)Ljava/lang/Class;");
    CHECK_EXCEPTION(env);
  }

  ScopedJavaLocalRef<jclass> FindClass(JNIEnv* env, const char* c_name) {
    // ClassLoader.loadClass expects a classname with components separated by
    // dots instead of the slashes that JNIEnv::FindClass expects.
    std::string name(c_name);
    std::replace(name.begin(), name.end(), '/', '.');
    ScopedJavaLocalRef<jstring> j_name = NativeToJavaString(env, name);
    const jclass clazz = static_cast<jclass>(env->CallObjectMethod(
        class_loader_.obj(), load_class_method_, j_name.obj()));
    CHECK_EXCEPTION(env);
    return ScopedJavaLocalRef<jclass>(env, clazz);
  }

 private:
  ScopedJavaGlobalRef<jobject> class_loader_;
  jclass class_loader_class_;
  jmethodID load_class_method_;
};

static ClassLoader* g_class_loader = nullptr;

}  // namespace

void InitClassLoader(JNIEnv* env) {
  RTC_CHECK(g_class_loader == nullptr);
  g_class_loader = new ClassLoader(env);
}

ScopedJavaLocalRef<jclass> GetClass(JNIEnv* env, const char* name) {
  // The class loader will be null in the JNI code called from the ClassLoader
  // ctor when we are bootstrapping ourself.
  return (g_class_loader == nullptr)
             ? ScopedJavaLocalRef<jclass>(env, env->FindClass(name))
             : g_class_loader->FindClass(env, name);
}

}  // namespace webrtc
