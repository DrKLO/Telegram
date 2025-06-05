/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/data_channel.h"

#include <limits>
#include <memory>

#include "api/data_channel_interface.h"
#include "rtc_base/logging.h"
#include "sdk/android/generated_peerconnection_jni/DataChannel_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

namespace {
// Adapter for a Java DataChannel$Observer presenting a C++ DataChannelObserver
// and dispatching the callback from C++ back to Java.
class DataChannelObserverJni : public DataChannelObserver {
 public:
  DataChannelObserverJni(JNIEnv* jni, const JavaRef<jobject>& j_observer);
  ~DataChannelObserverJni() override {}

  void OnBufferedAmountChange(uint64_t previous_amount) override;
  void OnStateChange() override;
  void OnMessage(const DataBuffer& buffer) override;

 private:
  const ScopedJavaGlobalRef<jobject> j_observer_global_;
};

DataChannelObserverJni::DataChannelObserverJni(
    JNIEnv* jni,
    const JavaRef<jobject>& j_observer)
    : j_observer_global_(jni, j_observer) {}

void DataChannelObserverJni::OnBufferedAmountChange(uint64_t previous_amount) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onBufferedAmountChange(env, j_observer_global_,
                                       previous_amount);
}

void DataChannelObserverJni::OnStateChange() {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onStateChange(env, j_observer_global_);
}

void DataChannelObserverJni::OnMessage(const DataBuffer& buffer) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> byte_buffer = NewDirectByteBuffer(
      env, const_cast<char*>(buffer.data.data<char>()), buffer.data.size());
  ScopedJavaLocalRef<jobject> j_buffer =
      Java_Buffer_Constructor(env, byte_buffer, buffer.binary);
  Java_Observer_onMessage(env, j_observer_global_, j_buffer);
}

DataChannelInterface* ExtractNativeDC(JNIEnv* jni,
                                      const JavaParamRef<jobject>& j_dc) {
  return reinterpret_cast<DataChannelInterface*>(
      Java_DataChannel_getNativeDataChannel(jni, j_dc));
}

}  // namespace

DataChannelInit JavaToNativeDataChannelInit(JNIEnv* env,
                                            const JavaRef<jobject>& j_init) {
  DataChannelInit init;
  init.ordered = Java_Init_getOrdered(env, j_init);
  init.maxRetransmitTime = Java_Init_getMaxRetransmitTimeMs(env, j_init);
  init.maxRetransmits = Java_Init_getMaxRetransmits(env, j_init);
  init.protocol = JavaToStdString(env, Java_Init_getProtocol(env, j_init));
  init.negotiated = Java_Init_getNegotiated(env, j_init);
  init.id = Java_Init_getId(env, j_init);
  return init;
}

ScopedJavaLocalRef<jobject> WrapNativeDataChannel(
    JNIEnv* env,
    rtc::scoped_refptr<DataChannelInterface> channel) {
  if (!channel)
    return nullptr;
  // Channel is now owned by Java object, and will be freed from there.
  return Java_DataChannel_Constructor(env, jlongFromPointer(channel.release()));
}

static jlong JNI_DataChannel_RegisterObserver(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_dc,
    const JavaParamRef<jobject>& j_observer) {
  auto observer = std::make_unique<DataChannelObserverJni>(jni, j_observer);
  ExtractNativeDC(jni, j_dc)->RegisterObserver(observer.get());
  return jlongFromPointer(observer.release());
}

static void JNI_DataChannel_UnregisterObserver(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_dc,
    jlong native_observer) {
  ExtractNativeDC(jni, j_dc)->UnregisterObserver();
  delete reinterpret_cast<DataChannelObserverJni*>(native_observer);
}

static ScopedJavaLocalRef<jstring> JNI_DataChannel_Label(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_dc) {
  return NativeToJavaString(jni, ExtractNativeDC(jni, j_dc)->label());
}

static jint JNI_DataChannel_Id(JNIEnv* jni, const JavaParamRef<jobject>& j_dc) {
  int id = ExtractNativeDC(jni, j_dc)->id();
  RTC_CHECK_LE(id, std::numeric_limits<int32_t>::max())
      << "id overflowed jint!";
  return static_cast<jint>(id);
}

static ScopedJavaLocalRef<jobject> JNI_DataChannel_State(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_dc) {
  return Java_State_fromNativeIndex(jni, ExtractNativeDC(jni, j_dc)->state());
}

static jlong JNI_DataChannel_BufferedAmount(JNIEnv* jni,
                                            const JavaParamRef<jobject>& j_dc) {
  uint64_t buffered_amount = ExtractNativeDC(jni, j_dc)->buffered_amount();
  RTC_CHECK_LE(buffered_amount, std::numeric_limits<int64_t>::max())
      << "buffered_amount overflowed jlong!";
  return static_cast<jlong>(buffered_amount);
}

static void JNI_DataChannel_Close(JNIEnv* jni,
                                  const JavaParamRef<jobject>& j_dc) {
  ExtractNativeDC(jni, j_dc)->Close();
}

static jboolean JNI_DataChannel_Send(JNIEnv* jni,
                                     const JavaParamRef<jobject>& j_dc,
                                     const JavaParamRef<jbyteArray>& data,
                                     jboolean binary) {
  std::vector<int8_t> buffer = JavaToNativeByteArray(jni, data);
  bool ret = ExtractNativeDC(jni, j_dc)->Send(
      DataBuffer(rtc::CopyOnWriteBuffer(buffer.data(), buffer.size()), binary));
  return ret;
}

}  // namespace jni
}  // namespace webrtc
