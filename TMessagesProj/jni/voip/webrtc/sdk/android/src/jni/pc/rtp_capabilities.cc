/*
 *  Copyright 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/rtp_capabilities.h"

#include "sdk/android/generated_peerconnection_jni/RtpCapabilities_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/pc/media_stream_track.h"

namespace webrtc {
namespace jni {

namespace {

ScopedJavaLocalRef<jobject> NativeToJavaRtpCodecParameter(
    JNIEnv* env,
    const RtpCodecCapability& codec) {
  return Java_CodecCapability_Constructor(
      env, codec.preferred_payload_type.value(),
      NativeToJavaString(env, codec.name),
      NativeToJavaMediaType(env, codec.kind),
      NativeToJavaInteger(env, codec.clock_rate),
      NativeToJavaInteger(env, codec.num_channels),
      NativeToJavaString(env, codec.mime_type()),
      NativeToJavaStringMap(env, codec.parameters));
}

ScopedJavaLocalRef<jobject> NativeToJavaRtpHeaderExtensionParameter(
    JNIEnv* env,
    const RtpHeaderExtensionCapability& extension) {
  return Java_HeaderExtensionCapability_Constructor(
      env, NativeToJavaString(env, extension.uri),
      extension.preferred_id.value(), extension.preferred_encrypt);
}
}  // namespace

RtpCapabilities JavaToNativeRtpCapabilities(
    JNIEnv* jni,
    const JavaRef<jobject>& j_capabilities) {
  RtpCapabilities capabilities;

  ScopedJavaLocalRef<jobject> j_header_extensions =
      Java_RtpCapabilities_getHeaderExtensions(jni, j_capabilities);
  for (const JavaRef<jobject>& j_header_extension :
       Iterable(jni, j_header_extensions)) {
    RtpHeaderExtensionCapability header_extension;
    header_extension.uri = JavaToStdString(
        jni, Java_HeaderExtensionCapability_getUri(jni, j_header_extension));
    header_extension.preferred_id =
        Java_HeaderExtensionCapability_getPreferredId(jni, j_header_extension);
    header_extension.preferred_encrypt =
        Java_HeaderExtensionCapability_getPreferredEncrypted(
            jni, j_header_extension);
    capabilities.header_extensions.push_back(header_extension);
  }

  // Convert codecs.
  ScopedJavaLocalRef<jobject> j_codecs =
      Java_RtpCapabilities_getCodecs(jni, j_capabilities);
  for (const JavaRef<jobject>& j_codec : Iterable(jni, j_codecs)) {
    RtpCodecCapability codec;
    codec.preferred_payload_type =
        Java_CodecCapability_getPreferredPayloadType(jni, j_codec);
    codec.name =
        JavaToStdString(jni, Java_CodecCapability_getName(jni, j_codec));
    codec.kind =
        JavaToNativeMediaType(jni, Java_CodecCapability_getKind(jni, j_codec));
    codec.clock_rate = JavaToNativeOptionalInt(
        jni, Java_CodecCapability_getClockRate(jni, j_codec));
    codec.num_channels = JavaToNativeOptionalInt(
        jni, Java_CodecCapability_getNumChannels(jni, j_codec));
    auto parameters_map = JavaToNativeStringMap(
        jni, Java_CodecCapability_getParameters(jni, j_codec));
    codec.parameters.insert(parameters_map.begin(), parameters_map.end());
    capabilities.codecs.push_back(codec);
  }
  return capabilities;
}

ScopedJavaLocalRef<jobject> NativeToJavaRtpCapabilities(
    JNIEnv* env,
    const RtpCapabilities& capabilities) {
  return Java_RtpCapabilities_Constructor(
      env,
      NativeToJavaList(env, capabilities.codecs,
                       &NativeToJavaRtpCodecParameter),
      NativeToJavaList(env, capabilities.header_extensions,
                       &NativeToJavaRtpHeaderExtensionParameter));
}

RtpCodecCapability JavaToNativeRtpCodecCapability(
    JNIEnv* jni,
    const JavaRef<jobject>& j_codec) {
  RtpCodecCapability codec;
  codec.preferred_payload_type =
      Java_CodecCapability_getPreferredPayloadType(jni, j_codec);
  codec.name = JavaToStdString(jni, Java_CodecCapability_getName(jni, j_codec));
  codec.kind =
      JavaToNativeMediaType(jni, Java_CodecCapability_getKind(jni, j_codec));
  codec.clock_rate = JavaToNativeOptionalInt(
      jni, Java_CodecCapability_getClockRate(jni, j_codec));
  codec.num_channels = JavaToNativeOptionalInt(
      jni, Java_CodecCapability_getNumChannels(jni, j_codec));
  auto parameters_map = JavaToNativeStringMap(
      jni, Java_CodecCapability_getParameters(jni, j_codec));
  codec.parameters.insert(parameters_map.begin(), parameters_map.end());
  return codec;
}

}  // namespace jni
}  // namespace webrtc
