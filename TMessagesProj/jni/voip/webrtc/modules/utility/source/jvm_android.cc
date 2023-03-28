/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/utility/include/jvm_android.h"

#include <android/log.h>

#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "tgnet/FileLog.h"

namespace webrtc {

JVM* g_jvm;

// TODO(henrika): add more clases here if needed.
struct {
  const char* name;
  jclass clazz;
} loaded_classes[] = {
    {"org/webrtc/voiceengine/BuildInfo", nullptr},
    {"org/webrtc/voiceengine/WebRtcAudioManager", nullptr},
    {"org/webrtc/voiceengine/WebRtcAudioRecord", nullptr},
    {"org/webrtc/voiceengine/WebRtcAudioTrack", nullptr},
};

// Android's FindClass() is trickier than usual because the app-specific
// ClassLoader is not consulted when there is no app-specific frame on the
// stack.  Consequently, we only look up all classes once in native WebRTC.
// http://developer.android.com/training/articles/perf-jni.html#faq_FindClass
void LoadClasses(JNIEnv* jni) {
  RTC_LOG(LS_INFO) << "LoadClasses:";
  for (auto& c : loaded_classes) {
    jclass localRef = FindClass(jni, c.name);
    RTC_LOG(LS_INFO) << "name: " << c.name;
    CHECK_EXCEPTION(jni) << "Error during FindClass: " << c.name;
    RTC_CHECK(localRef) << c.name;
    DEBUG_REF("webrtc 4 globalref");
    jclass globalRef = reinterpret_cast<jclass>(jni->NewGlobalRef(localRef));
    CHECK_EXCEPTION(jni) << "Error during NewGlobalRef: " << c.name;
    RTC_CHECK(globalRef) << c.name;
    c.clazz = globalRef;
  }
}

void FreeClassReferences(JNIEnv* jni) {
  for (auto& c : loaded_classes) {
    DEBUG_DELREF("FreeClassReferences");
    jni->DeleteGlobalRef(c.clazz);
    c.clazz = nullptr;
  }
}

jclass LookUpClass(const char* name) {
  for (auto& c : loaded_classes) {
    if (strcmp(c.name, name) == 0)
      return c.clazz;
  }
  RTC_CHECK(false) << "Unable to find class in lookup table";
  return 0;
}

// JvmThreadConnector implementation.
JvmThreadConnector::JvmThreadConnector() : attached_(false) {
  RTC_LOG(LS_INFO) << "JvmThreadConnector::ctor";
  JavaVM* jvm = JVM::GetInstance()->jvm();
  RTC_CHECK(jvm);
  JNIEnv* jni = GetEnv(jvm);
  if (!jni) {
    RTC_LOG(LS_INFO) << "Attaching thread to JVM";
    JNIEnv* env = nullptr;
    jint ret = jvm->AttachCurrentThread(&env, nullptr);
    attached_ = (ret == JNI_OK);
  }
}

JvmThreadConnector::~JvmThreadConnector() {
  RTC_LOG(LS_INFO) << "JvmThreadConnector::dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (attached_) {
    RTC_LOG(LS_INFO) << "Detaching thread from JVM";
    jint res = JVM::GetInstance()->jvm()->DetachCurrentThread();
    RTC_CHECK(res == JNI_OK) << "DetachCurrentThread failed: " << res;
  }
}

// GlobalRef implementation.
GlobalRef::GlobalRef(JNIEnv* jni, jobject object)
    : jni_(jni), j_object_(NewGlobalRef(jni, object)) {
  DEBUG_REF("webrtc jvm globalref");
  RTC_LOG(LS_INFO) << "GlobalRef::ctor";
}

GlobalRef::~GlobalRef() {
  RTC_LOG(LS_INFO) << "GlobalRef::dtor";
  DEBUG_DELREF("webrtc jvm globalref");
  DeleteGlobalRef(jni_, j_object_);
}

jboolean GlobalRef::CallBooleanMethod(jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jboolean res = jni_->CallBooleanMethodV(j_object_, methodID, args);
  CHECK_EXCEPTION(jni_) << "Error during CallBooleanMethod";
  va_end(args);
  return res;
}

jint GlobalRef::CallIntMethod(jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jint res = jni_->CallIntMethodV(j_object_, methodID, args);
  CHECK_EXCEPTION(jni_) << "Error during CallIntMethod";
  va_end(args);
  return res;
}

void GlobalRef::CallVoidMethod(jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jni_->CallVoidMethodV(j_object_, methodID, args);
  CHECK_EXCEPTION(jni_) << "Error during CallVoidMethod";
  va_end(args);
}

// NativeRegistration implementation.
NativeRegistration::NativeRegistration(JNIEnv* jni, jclass clazz)
    : JavaClass(jni, clazz), jni_(jni) {
  RTC_LOG(LS_INFO) << "NativeRegistration::ctor";
}

NativeRegistration::~NativeRegistration() {
  RTC_LOG(LS_INFO) << "NativeRegistration::dtor";
  //jni_->UnregisterNatives(j_class_);
  CHECK_EXCEPTION(jni_) << "Error during UnregisterNatives";
}

std::unique_ptr<GlobalRef> NativeRegistration::NewObject(const char* name,
                                                         const char* signature,
                                                         ...) {
  RTC_LOG(LS_INFO) << "NativeRegistration::NewObject";
  va_list args;
  va_start(args, signature);
  jobject obj = jni_->NewObjectV(
      j_class_, GetMethodID(jni_, j_class_, name, signature), args);
  CHECK_EXCEPTION(jni_) << "Error during NewObjectV";
  va_end(args);
  return std::unique_ptr<GlobalRef>(new GlobalRef(jni_, obj));
}

// JavaClass implementation.
jmethodID JavaClass::GetMethodId(const char* name, const char* signature) {
  return GetMethodID(jni_, j_class_, name, signature);
}

jmethodID JavaClass::GetStaticMethodId(const char* name,
                                       const char* signature) {
  return GetStaticMethodID(jni_, j_class_, name, signature);
}

jobject JavaClass::CallStaticObjectMethod(jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jobject res = jni_->CallStaticObjectMethodV(j_class_, methodID, args);
  CHECK_EXCEPTION(jni_) << "Error during CallStaticObjectMethod";
  return res;
}

jint JavaClass::CallStaticIntMethod(jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jint res = jni_->CallStaticIntMethodV(j_class_, methodID, args);
  CHECK_EXCEPTION(jni_) << "Error during CallStaticIntMethod";
  return res;
}

// JNIEnvironment implementation.
JNIEnvironment::JNIEnvironment(JNIEnv* jni) : jni_(jni) {
  RTC_LOG(LS_INFO) << "JNIEnvironment::ctor";
}

JNIEnvironment::~JNIEnvironment() {
  RTC_LOG(LS_INFO) << "JNIEnvironment::dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
}

std::unique_ptr<NativeRegistration> JNIEnvironment::RegisterNatives(
    const char* name,
    const JNINativeMethod* methods,
    int num_methods) {
  RTC_LOG(LS_INFO) << "JNIEnvironment::RegisterNatives: " << name;
  RTC_DCHECK(thread_checker_.IsCurrent());
  jclass clazz = LookUpClass(name);
  jni_->RegisterNatives(clazz, methods, num_methods);
  CHECK_EXCEPTION(jni_) << "Error during RegisterNatives";
  return std::unique_ptr<NativeRegistration>(
      new NativeRegistration(jni_, clazz));
}

std::string JNIEnvironment::JavaToStdString(const jstring& j_string) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  const char* jchars = jni_->GetStringUTFChars(j_string, nullptr);
  CHECK_EXCEPTION(jni_);
  const int size = jni_->GetStringUTFLength(j_string);
  CHECK_EXCEPTION(jni_);
  std::string ret(jchars, size);
  jni_->ReleaseStringUTFChars(j_string, jchars);
  CHECK_EXCEPTION(jni_);
  return ret;
}

// static
void JVM::Initialize(JavaVM* jvm) {
  RTC_LOG(LS_INFO) << "JVM::Initialize";
  RTC_CHECK(!g_jvm);
  g_jvm = new JVM(jvm);
}

void JVM::Initialize(JavaVM* jvm, jobject context) {
  Initialize(jvm);

  // Pass in the context to the new ContextUtils class.
  JNIEnv* jni = g_jvm->jni();
  jclass context_utils = FindClass(jni, "org/webrtc/ContextUtils");
  jmethodID initialize_method = jni->GetStaticMethodID(
      context_utils, "initialize", "(Landroid/content/Context;)V");
  jni->CallStaticVoidMethod(context_utils, initialize_method, context);
}

// static
void JVM::Uninitialize() {
  RTC_LOG(LS_INFO) << "JVM::Uninitialize";
  RTC_DCHECK(g_jvm);
  delete g_jvm;
  g_jvm = nullptr;
}

// static
JVM* JVM::GetInstance() {
  RTC_DCHECK(g_jvm);
  return g_jvm;
}

JVM::JVM(JavaVM* jvm) : jvm_(jvm) {
  RTC_LOG(LS_INFO) << "JVM::JVM";
  RTC_CHECK(jni()) << "AttachCurrentThread() must be called on this thread.";
  LoadClasses(jni());
}

JVM::~JVM() {
  RTC_LOG(LS_INFO) << "JVM::~JVM";
  RTC_DCHECK(thread_checker_.IsCurrent());
  FreeClassReferences(jni());
}

std::unique_ptr<JNIEnvironment> JVM::environment() {
  RTC_LOG(LS_INFO) << "JVM::environment";
  ;
  // The JNIEnv is used for thread-local storage. For this reason, we cannot
  // share a JNIEnv between threads. If a piece of code has no other way to get
  // its JNIEnv, we should share the JavaVM, and use GetEnv to discover the
  // thread's JNIEnv. (Assuming it has one, if not, use AttachCurrentThread).
  // See // http://developer.android.com/training/articles/perf-jni.html.
  JNIEnv* jni = GetEnv(jvm_);
  if (!jni) {
    RTC_LOG(LS_ERROR)
        << "AttachCurrentThread() has not been called on this thread";
    return std::unique_ptr<JNIEnvironment>();
  }
  return std::unique_ptr<JNIEnvironment>(new JNIEnvironment(jni));
}

JavaClass JVM::GetClass(const char* name) {
  RTC_LOG(LS_INFO) << "JVM::GetClass: " << name;
  RTC_DCHECK(thread_checker_.IsCurrent());
  return JavaClass(jni(), LookUpClass(name));
}

}  // namespace webrtc
