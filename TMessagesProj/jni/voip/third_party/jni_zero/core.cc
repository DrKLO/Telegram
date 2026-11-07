// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "third_party/jni_zero/core.h"

#include <sys/prctl.h>

#include "third_party/jni_zero/logging.h"

namespace jni_zero {
namespace {
// Until we fully migrate base's jni_android, we will maintain a copy of this
// global here and will have base set this variable when it sets its own.
JavaVM* g_jvm = nullptr;

jclass (*g_class_resolver)(JNIEnv*, const char*, const char*) = nullptr;

void (*g_exception_handler_callback)(JNIEnv*) = nullptr;

ScopedJavaLocalRef<jclass> GetClassInternal(JNIEnv* env,
                                            const char* class_name,
                                            const char* split_name) {
  jclass clazz;
  if (g_class_resolver != nullptr) {
    clazz = g_class_resolver(env, class_name, split_name);
  } else {
    clazz = env->FindClass(class_name);
  }
  if (ClearException(env) || !clazz) {
    JNI_ZERO_FLOG("Failed to find class %s", class_name);
  }
  return ScopedJavaLocalRef<jclass>(env, clazz);
}
}  // namespace

JNIEnv* AttachCurrentThread() {
  JNI_ZERO_DCHECK(g_jvm);
  JNIEnv* env = nullptr;
  jint ret = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_2);
  if (ret == JNI_EDETACHED || !env) {
    JavaVMAttachArgs args;
    args.version = JNI_VERSION_1_2;
    args.group = nullptr;

    // 16 is the maximum size for thread names on Android.
    char thread_name[16];
    int err = prctl(PR_GET_NAME, thread_name);
    if (err < 0) {
      JNI_ZERO_ELOG("prctl(PR_GET_NAME)");
      args.name = nullptr;
    } else {
      args.name = thread_name;
    }

#if defined(JNI_ZERO_IS_ROBOLECTRIC)
    ret = g_jvm->AttachCurrentThread(reinterpret_cast<void**>(&env), &args);
#else
    ret = g_jvm->AttachCurrentThread(&env, &args);
#endif
    JNI_ZERO_CHECK(ret == JNI_OK);
  }
  return env;
}

JNIEnv* AttachCurrentThreadWithName(const std::string& thread_name) {
  JNI_ZERO_DCHECK(g_jvm);
  JavaVMAttachArgs args;
  args.version = JNI_VERSION_1_2;
  args.name = const_cast<char*>(thread_name.c_str());
  args.group = nullptr;
  JNIEnv* env = nullptr;
#if defined(JNI_ZERO_IS_ROBOLECTRIC)
  jint ret = g_jvm->AttachCurrentThread(reinterpret_cast<void**>(&env), &args);
#else
  jint ret = g_jvm->AttachCurrentThread(&env, &args);
#endif
  JNI_ZERO_CHECK(ret == JNI_OK);
  return env;
}

void DetachFromVM() {
  // Ignore the return value, if the thread is not attached, DetachCurrentThread
  // will fail. But it is ok as the native thread may never be attached.
  if (g_jvm) {
    g_jvm->DetachCurrentThread();
  }
}

void InitVM(JavaVM* vm) {
  g_jvm = vm;
}

void DisableJvmForTesting() {
  g_jvm = nullptr;
}

bool IsVMInitialized() {
  return g_jvm != nullptr;
}

JavaVM* GetVM() {
  return g_jvm;
}

bool HasException(JNIEnv* env) {
  return env->ExceptionCheck() != JNI_FALSE;
}

bool ClearException(JNIEnv* env) {
  if (!HasException(env)) {
    return false;
  }
  env->ExceptionDescribe();
  env->ExceptionClear();
  return true;
}

void SetExceptionHandler(void (*callback)(JNIEnv*)) {
  g_exception_handler_callback = callback;
}

void CheckException(JNIEnv* env) {
  if (!HasException(env)) {
    return;
  }

  if (g_exception_handler_callback) {
    return g_exception_handler_callback(env);
  }
  JNI_ZERO_FLOG("jni_zero crashing due to uncaught Java exception");
}

void SetClassResolver(jclass (*resolver)(JNIEnv*, const char*, const char*)) {
  g_class_resolver = resolver;
}

ScopedJavaLocalRef<jclass> GetClass(JNIEnv* env,
                                    const char* class_name,
                                    const char* split_name) {
  return GetClassInternal(env, class_name, split_name);
}

ScopedJavaLocalRef<jclass> GetClass(JNIEnv* env, const char* class_name) {
  return GetClassInternal(env, class_name, "");
}

// This is duplicated with LazyGetClass below because these are performance
// sensitive.
jclass LazyGetClass(JNIEnv* env,
                    const char* class_name,
                    const char* split_name,
                    std::atomic<jclass>* atomic_class_id) {
  const jclass value = atomic_class_id->load(std::memory_order_acquire);
  if (value) {
    return value;
  }
  ScopedJavaGlobalRef<jclass> clazz;
  clazz.Reset(GetClass(env, class_name, split_name));
  jclass cas_result = nullptr;
  if (atomic_class_id->compare_exchange_strong(cas_result, clazz.obj(),
                                               std::memory_order_acq_rel)) {
    // We intentionally leak the global ref since we are now storing it as a raw
    // pointer in |atomic_class_id|.
    return clazz.Release();
  } else {
    return cas_result;
  }
}

// This is duplicated with LazyGetClass above because these are performance
// sensitive.
jclass LazyGetClass(JNIEnv* env,
                    const char* class_name,
                    std::atomic<jclass>* atomic_class_id) {
  const jclass value = atomic_class_id->load(std::memory_order_acquire);
  if (value) {
    return value;
  }
  ScopedJavaGlobalRef<jclass> clazz;
  clazz.Reset(GetClass(env, class_name));
  jclass cas_result = nullptr;
  if (atomic_class_id->compare_exchange_strong(cas_result, clazz.obj(),
                                               std::memory_order_acq_rel)) {
    // We intentionally leak the global ref since we are now storing it as a raw
    // pointer in |atomic_class_id|.
    return clazz.Release();
  } else {
    return cas_result;
  }
}

template <MethodID::Type type>
jmethodID MethodID::Get(JNIEnv* env,
                        jclass clazz,
                        const char* method_name,
                        const char* jni_signature) {
  auto get_method_ptr = type == MethodID::TYPE_STATIC
                            ? &JNIEnv::GetStaticMethodID
                            : &JNIEnv::GetMethodID;
  jmethodID id = (env->*get_method_ptr)(clazz, method_name, jni_signature);
  if (ClearException(env) || !id) {
    JNI_ZERO_FLOG("Failed to find class %smethod %s %s",
                  (type == TYPE_STATIC ? "static " : ""), method_name,
                  jni_signature);
  }
  return id;
}

// If |atomic_method_id| set, it'll return immediately. Otherwise, it'll call
// into ::Get() above. If there's a race, it's ok since the values are the same
// (and the duplicated effort will happen only once).
template <MethodID::Type type>
jmethodID MethodID::LazyGet(JNIEnv* env,
                            jclass clazz,
                            const char* method_name,
                            const char* jni_signature,
                            std::atomic<jmethodID>* atomic_method_id) {
  const jmethodID value = atomic_method_id->load(std::memory_order_acquire);
  if (value) {
    return value;
  }
  jmethodID id = MethodID::Get<type>(env, clazz, method_name, jni_signature);
  atomic_method_id->store(id, std::memory_order_release);
  return id;
}

// Various template instantiations.
template jmethodID MethodID::Get<MethodID::TYPE_STATIC>(
    JNIEnv* env,
    jclass clazz,
    const char* method_name,
    const char* jni_signature);

template jmethodID MethodID::Get<MethodID::TYPE_INSTANCE>(
    JNIEnv* env,
    jclass clazz,
    const char* method_name,
    const char* jni_signature);

template jmethodID MethodID::LazyGet<MethodID::TYPE_STATIC>(
    JNIEnv* env,
    jclass clazz,
    const char* method_name,
    const char* jni_signature,
    std::atomic<jmethodID>* atomic_method_id);

template jmethodID MethodID::LazyGet<MethodID::TYPE_INSTANCE>(
    JNIEnv* env,
    jclass clazz,
    const char* method_name,
    const char* jni_signature,
    std::atomic<jmethodID>* atomic_method_id);

}  // namespace jni_zero
