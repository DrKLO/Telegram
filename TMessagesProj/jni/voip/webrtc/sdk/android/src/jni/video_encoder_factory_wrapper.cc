/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_encoder_factory_wrapper.h"

#include "api/video_codecs/video_encoder.h"
#include "rtc_base/logging.h"
#include "sdk/android/generated_video_jni/VideoEncoderFactory_jni.h"
#include "sdk/android/native_api/jni/class_loader.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/video_codec_info.h"
#include "sdk/android/src/jni/video_encoder_wrapper.h"

namespace webrtc {
namespace jni {
namespace {
class VideoEncoderSelectorWrapper
    : public VideoEncoderFactory::EncoderSelectorInterface {
 public:
  VideoEncoderSelectorWrapper(JNIEnv* jni,
                              const JavaRef<jobject>& encoder_selector)
      : encoder_selector_(jni, encoder_selector) {}

  void OnCurrentEncoder(const SdpVideoFormat& format) override {
    JNIEnv* jni = AttachCurrentThreadIfNeeded();
    ScopedJavaLocalRef<jobject> j_codec_info =
        SdpVideoFormatToVideoCodecInfo(jni, format);
    Java_VideoEncoderSelector_onCurrentEncoder(jni, encoder_selector_,
                                               j_codec_info);
  }

  absl::optional<SdpVideoFormat> OnAvailableBitrate(
      const DataRate& rate) override {
    JNIEnv* jni = AttachCurrentThreadIfNeeded();
    ScopedJavaLocalRef<jobject> codec_info =
        Java_VideoEncoderSelector_onAvailableBitrate(jni, encoder_selector_,
                                                     rate.kbps<int>());
    if (codec_info.is_null()) {
      return absl::nullopt;
    }
    return VideoCodecInfoToSdpVideoFormat(jni, codec_info);
  }

  absl::optional<SdpVideoFormat> OnEncoderBroken() override {
    JNIEnv* jni = AttachCurrentThreadIfNeeded();
    ScopedJavaLocalRef<jobject> codec_info =
        Java_VideoEncoderSelector_onEncoderBroken(jni, encoder_selector_);
    if (codec_info.is_null()) {
      return absl::nullopt;
    }
    return VideoCodecInfoToSdpVideoFormat(jni, codec_info);
  }

 private:
  const ScopedJavaGlobalRef<jobject> encoder_selector_;
};

}  // namespace

VideoEncoderFactoryWrapper::VideoEncoderFactoryWrapper(
    JNIEnv* jni,
    const JavaRef<jobject>& encoder_factory)
    : encoder_factory_(jni, encoder_factory) {
  const ScopedJavaLocalRef<jobjectArray> j_supported_codecs =
      Java_VideoEncoderFactory_getSupportedCodecs(jni, encoder_factory);
  supported_formats_ = JavaToNativeVector<SdpVideoFormat>(
      jni, j_supported_codecs, &VideoCodecInfoToSdpVideoFormat);
  const ScopedJavaLocalRef<jobjectArray> j_implementations =
      Java_VideoEncoderFactory_getImplementations(jni, encoder_factory);
  implementations_ = JavaToNativeVector<SdpVideoFormat>(
      jni, j_implementations, &VideoCodecInfoToSdpVideoFormat);
}
VideoEncoderFactoryWrapper::~VideoEncoderFactoryWrapper() = default;

std::unique_ptr<VideoEncoder> VideoEncoderFactoryWrapper::CreateVideoEncoder(
    const SdpVideoFormat& format) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_codec_info =
      SdpVideoFormatToVideoCodecInfo(jni, format);
  ScopedJavaLocalRef<jobject> encoder = Java_VideoEncoderFactory_createEncoder(
      jni, encoder_factory_, j_codec_info);
  if (!encoder.obj())
    return nullptr;
  return JavaToNativeVideoEncoder(jni, encoder);
}

std::vector<SdpVideoFormat> VideoEncoderFactoryWrapper::GetSupportedFormats()
    const {
  return supported_formats_;
}

std::vector<SdpVideoFormat> VideoEncoderFactoryWrapper::GetImplementations()
    const {
  return implementations_;
}

std::unique_ptr<VideoEncoderFactory::EncoderSelectorInterface>
VideoEncoderFactoryWrapper::GetEncoderSelector() const {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> selector =
      Java_VideoEncoderFactory_getEncoderSelector(jni, encoder_factory_);
  if (selector.is_null()) {
    return nullptr;
  }

  return std::make_unique<VideoEncoderSelectorWrapper>(jni, selector);
}

}  // namespace jni
}  // namespace webrtc
