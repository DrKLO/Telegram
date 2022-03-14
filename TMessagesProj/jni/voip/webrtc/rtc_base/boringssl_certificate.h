/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_BORINGSSL_CERTIFICATE_H_
#define RTC_BASE_BORINGSSL_CERTIFICATE_H_

#include <openssl/ossl_typ.h>
#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <string>

#include "rtc_base/buffer.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/ssl_identity.h"

namespace rtc {

class OpenSSLKeyPair;

// BoringSSLCertificate encapsulates a BoringSSL CRYPTO_BUFFER object holding a
// certificate, which is also reference counted inside the BoringSSL library.
// This offers binary size and memory improvements over the OpenSSL X509
// object.
class BoringSSLCertificate final : public SSLCertificate {
 public:
  explicit BoringSSLCertificate(bssl::UniquePtr<CRYPTO_BUFFER> cert_buffer);

  static std::unique_ptr<BoringSSLCertificate> Generate(
      OpenSSLKeyPair* key_pair,
      const SSLIdentityParams& params);
  static std::unique_ptr<BoringSSLCertificate> FromPEMString(
      const std::string& pem_string);

  ~BoringSSLCertificate() override;

  std::unique_ptr<SSLCertificate> Clone() const override;

  CRYPTO_BUFFER* cert_buffer() const { return cert_buffer_.get(); }

  std::string ToPEMString() const override;
  void ToDER(Buffer* der_buffer) const override;
  bool operator==(const BoringSSLCertificate& other) const;
  bool operator!=(const BoringSSLCertificate& other) const;

  // Compute the digest of the certificate given `algorithm`.
  bool ComputeDigest(const std::string& algorithm,
                     unsigned char* digest,
                     size_t size,
                     size_t* length) const override;

  // Compute the digest of a certificate as a CRYPTO_BUFFER.
  static bool ComputeDigest(const CRYPTO_BUFFER* cert_buffer,
                            const std::string& algorithm,
                            unsigned char* digest,
                            size_t size,
                            size_t* length);

  bool GetSignatureDigestAlgorithm(std::string* algorithm) const override;

  int64_t CertificateExpirationTime() const override;

 private:
  // A handle to the DER encoded certificate data.
  bssl::UniquePtr<CRYPTO_BUFFER> cert_buffer_;
  RTC_DISALLOW_COPY_AND_ASSIGN(BoringSSLCertificate);
};

}  // namespace rtc

#endif  // RTC_BASE_BORINGSSL_CERTIFICATE_H_
