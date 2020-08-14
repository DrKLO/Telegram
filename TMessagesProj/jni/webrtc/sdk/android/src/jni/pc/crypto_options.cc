/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/crypto_options.h"

#include "sdk/android/generated_peerconnection_jni/CryptoOptions_jni.h"

namespace webrtc {
namespace jni {

absl::optional<CryptoOptions> JavaToNativeOptionalCryptoOptions(
    JNIEnv* jni,
    const JavaRef<jobject>& j_crypto_options) {
  if (j_crypto_options.is_null()) {
    return absl::nullopt;
  }

  ScopedJavaLocalRef<jobject> j_srtp =
      Java_CryptoOptions_getSrtp(jni, j_crypto_options);
  ScopedJavaLocalRef<jobject> j_sframe =
      Java_CryptoOptions_getSFrame(jni, j_crypto_options);

  CryptoOptions native_crypto_options;
  native_crypto_options.srtp.enable_gcm_crypto_suites =
      Java_Srtp_getEnableGcmCryptoSuites(jni, j_srtp);
  native_crypto_options.srtp.enable_aes128_sha1_32_crypto_cipher =
      Java_Srtp_getEnableAes128Sha1_32CryptoCipher(jni, j_srtp);
  native_crypto_options.srtp.enable_encrypted_rtp_header_extensions =
      Java_Srtp_getEnableEncryptedRtpHeaderExtensions(jni, j_srtp);
  native_crypto_options.sframe.require_frame_encryption =
      Java_SFrame_getRequireFrameEncryption(jni, j_sframe);
  return absl::optional<CryptoOptions>(native_crypto_options);
}

}  // namespace jni
}  // namespace webrtc
