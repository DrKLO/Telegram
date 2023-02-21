/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_OPENSSL_CERTIFICATE_H_
#define RTC_BASE_OPENSSL_CERTIFICATE_H_

#include <openssl/ossl_typ.h>
#include <stddef.h>
#include <stdint.h>

#include <string>

#include "rtc_base/buffer.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/ssl_identity.h"

namespace rtc {

class OpenSSLKeyPair;

// OpenSSLCertificate encapsulates an OpenSSL X509* certificate object,
// which is also reference counted inside the OpenSSL library.
class OpenSSLCertificate final : public SSLCertificate {
 public:
  // X509 object has its reference count incremented. So the caller and
  // OpenSSLCertificate share ownership.
  explicit OpenSSLCertificate(X509* x509);

  static std::unique_ptr<OpenSSLCertificate> Generate(
      OpenSSLKeyPair* key_pair,
      const SSLIdentityParams& params);
  static std::unique_ptr<OpenSSLCertificate> FromPEMString(
      absl::string_view pem_string);

  ~OpenSSLCertificate() override;

  OpenSSLCertificate(const OpenSSLCertificate&) = delete;
  OpenSSLCertificate& operator=(const OpenSSLCertificate&) = delete;

  std::unique_ptr<SSLCertificate> Clone() const override;

  X509* x509() const { return x509_; }

  std::string ToPEMString() const override;
  void ToDER(Buffer* der_buffer) const override;
  bool operator==(const OpenSSLCertificate& other) const;
  bool operator!=(const OpenSSLCertificate& other) const;

  // Compute the digest of the certificate given algorithm
  bool ComputeDigest(absl::string_view algorithm,
                     unsigned char* digest,
                     size_t size,
                     size_t* length) const override;

  // Compute the digest of a certificate as an X509 *
  static bool ComputeDigest(const X509* x509,
                            absl::string_view algorithm,
                            unsigned char* digest,
                            size_t size,
                            size_t* length);

  bool GetSignatureDigestAlgorithm(std::string* algorithm) const override;

  int64_t CertificateExpirationTime() const override;

 private:
  X509* x509_;  // NOT OWNED
};

}  // namespace rtc

#endif  // RTC_BASE_OPENSSL_CERTIFICATE_H_
