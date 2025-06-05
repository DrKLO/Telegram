/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_OPENSSL_KEY_PAIR_H_
#define RTC_BASE_OPENSSL_KEY_PAIR_H_

#include <openssl/ossl_typ.h>

#include <memory>
#include <string>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/ssl_identity.h"

namespace rtc {

// OpenSSLKeyPair encapsulates an OpenSSL EVP_PKEY* keypair object,
// which is reference counted inside the OpenSSL library.
class OpenSSLKeyPair final {
 public:
  // Takes ownership of the key.
  explicit OpenSSLKeyPair(EVP_PKEY* pkey) : pkey_(pkey) {
    RTC_DCHECK(pkey_ != nullptr);
  }

  static std::unique_ptr<OpenSSLKeyPair> Generate(const KeyParams& key_params);
  // Constructs a key pair from the private key PEM string. This must not result
  // in missing public key parameters. Returns null on error.
  static std::unique_ptr<OpenSSLKeyPair> FromPrivateKeyPEMString(
      absl::string_view pem_string);

  ~OpenSSLKeyPair();

  OpenSSLKeyPair(const OpenSSLKeyPair&) = delete;
  OpenSSLKeyPair& operator=(const OpenSSLKeyPair&) = delete;

  std::unique_ptr<OpenSSLKeyPair> Clone();

  EVP_PKEY* pkey() const { return pkey_; }
  std::string PrivateKeyToPEMString() const;
  std::string PublicKeyToPEMString() const;
  bool operator==(const OpenSSLKeyPair& other) const;
  bool operator!=(const OpenSSLKeyPair& other) const;

 private:
  void AddReference();

  EVP_PKEY* pkey_;
};

}  // namespace rtc

#endif  // RTC_BASE_OPENSSL_KEY_PAIR_H_
