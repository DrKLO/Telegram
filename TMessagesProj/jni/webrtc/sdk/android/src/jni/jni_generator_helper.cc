/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/jni_generator_helper.h"

#include "rtc_base/atomic_ops.h"
#include "sdk/android/native_api/jni/class_loader.h"

namespace webrtc {

// If |atomic_class_id| set, it'll return immediately. Otherwise, it will look
// up the class and store it. If there's a race, we take care to only store one
// global reference (and the duplicated effort will happen only once).
jclass LazyGetClass(JNIEnv* env,
                    const char* class_name,
                    std::atomic<jclass>* atomic_class_id) {
  const jclass value = std::atomic_load(atomic_class_id);
  if (value)
    return value;
  webrtc::ScopedJavaGlobalRef<jclass> clazz(webrtc::GetClass(env, class_name));
  RTC_CHECK(!clazz.is_null()) << class_name;
  jclass cas_result = nullptr;
  if (std::atomic_compare_exchange_strong(atomic_class_id, &cas_result,
                                          clazz.obj())) {
    // We sucessfully stored |clazz| in |atomic_class_id|, so we are
    // intentionally leaking the global ref since it's now stored there.
    return clazz.Release();
  } else {
    // Some other thread came before us and stored a global pointer in
    // |atomic_class_id|. Relase our global ref and return the ref from the
    // other thread.
    return cas_result;
  }
}

// If |atomic_method_id| set, it'll return immediately. Otherwise, it will look
// up the method id and store it. If there's a race, it's ok since the values
// are the same (and the duplicated effort will happen only once).
template <MethodID::Type type>
jmethodID MethodID::LazyGet(JNIEnv* env,
                            jclass clazz,
                            const char* method_name,
                            const char* jni_signature,
                            std::atomic<jmethodID>* atomic_method_id) {
  const jmethodID value = std::atomic_load(atomic_method_id);
  if (value)
    return value;
  auto get_method_ptr = type == MethodID::TYPE_STATIC
                            ? &JNIEnv::GetStaticMethodID
                            : &JNIEnv::GetMethodID;
  jmethodID id = (env->*get_method_ptr)(clazz, method_name, jni_signature);
  CHECK_EXCEPTION(env) << "error during GetMethodID: " << method_name << ", "
                       << jni_signature;
  RTC_CHECK(id) << method_name << ", " << jni_signature;
  std::atomic_store(atomic_method_id, id);
  return id;
}

// Various template instantiations.
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

}  // namespace webrtc
