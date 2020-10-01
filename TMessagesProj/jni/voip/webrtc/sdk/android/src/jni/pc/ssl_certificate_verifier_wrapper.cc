/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/ssl_certificate_verifier_wrapper.h"
#include "sdk/android/generated_peerconnection_jni/SSLCertificateVerifier_jni.h"
#include "sdk/android/native_api/jni/class_loader.h"
#include "sdk/android/native_api/jni/java_types.h"

namespace webrtc {
namespace jni {

SSLCertificateVerifierWrapper::SSLCertificateVerifierWrapper(
    JNIEnv* jni,
    const JavaRef<jobject>& ssl_certificate_verifier)
    : ssl_certificate_verifier_(jni, ssl_certificate_verifier) {}

SSLCertificateVerifierWrapper::~SSLCertificateVerifierWrapper() = default;

bool SSLCertificateVerifierWrapper::Verify(
    const rtc::SSLCertificate& certificate) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();

  // Serialize the der encoding of the cert into a jbyteArray
  rtc::Buffer cert_der_buffer;
  certificate.ToDER(&cert_der_buffer);
  ScopedJavaLocalRef<jbyteArray> jni_buffer(
      jni, jni->NewByteArray(cert_der_buffer.size()));
  jni->SetByteArrayRegion(
      jni_buffer.obj(), 0, cert_der_buffer.size(),
      reinterpret_cast<const jbyte*>(cert_der_buffer.data()));

  return Java_SSLCertificateVerifier_verify(jni, ssl_certificate_verifier_,
                                            jni_buffer);
}

}  // namespace jni
}  // namespace webrtc
