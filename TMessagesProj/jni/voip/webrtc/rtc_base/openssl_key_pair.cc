/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/openssl_key_pair.h"

#include <memory>
#include <utility>

#include "absl/strings/string_view.h"

#if defined(WEBRTC_WIN)
// Must be included first before openssl headers.
#include "rtc_base/win32.h"  // NOLINT
#endif                       // WEBRTC_WIN

#include <openssl/bio.h>
#include <openssl/bn.h>
#include <openssl/pem.h>
#include <openssl/rsa.h>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/openssl.h"
#include "rtc_base/openssl_utility.h"

namespace rtc {

// We could have exposed a myriad of parameters for the crypto stuff,
// but keeping it simple seems best.

// Generate a key pair. Caller is responsible for freeing the returned object.
static EVP_PKEY* MakeKey(const KeyParams& key_params) {
  RTC_LOG(LS_INFO) << "Making key pair";
  EVP_PKEY* pkey = EVP_PKEY_new();
  if (key_params.type() == KT_RSA) {
    int key_length = key_params.rsa_params().mod_size;
    BIGNUM* exponent = BN_new();
    RSA* rsa = RSA_new();
    if (!pkey || !exponent || !rsa ||
        !BN_set_word(exponent, key_params.rsa_params().pub_exp) ||
        !RSA_generate_key_ex(rsa, key_length, exponent, nullptr) ||
        !EVP_PKEY_assign_RSA(pkey, rsa)) {
      EVP_PKEY_free(pkey);
      BN_free(exponent);
      RSA_free(rsa);
      RTC_LOG(LS_ERROR) << "Failed to make RSA key pair";
      return nullptr;
    }
    // ownership of rsa struct was assigned, don't free it.
    BN_free(exponent);
  } else if (key_params.type() == KT_ECDSA) {
    if (key_params.ec_curve() == EC_NIST_P256) {
      EC_KEY* ec_key = EC_KEY_new_by_curve_name(NID_X9_62_prime256v1);
      if (!ec_key) {
        EVP_PKEY_free(pkey);
        RTC_LOG(LS_ERROR) << "Failed to allocate EC key";
        return nullptr;
      }

      // Ensure curve name is included when EC key is serialized.
      // Without this call, OpenSSL versions before 1.1.0 will create
      // certificates that don't work for TLS.
      // This is a no-op for BoringSSL and OpenSSL 1.1.0+
      EC_KEY_set_asn1_flag(ec_key, OPENSSL_EC_NAMED_CURVE);

      if (!pkey || !ec_key || !EC_KEY_generate_key(ec_key) ||
          !EVP_PKEY_assign_EC_KEY(pkey, ec_key)) {
        EVP_PKEY_free(pkey);
        EC_KEY_free(ec_key);
        RTC_LOG(LS_ERROR) << "Failed to make EC key pair";
        return nullptr;
      }
      // ownership of ec_key struct was assigned, don't free it.
    } else {
      // Add generation of any other curves here.
      EVP_PKEY_free(pkey);
      RTC_LOG(LS_ERROR) << "ECDSA key requested for unknown curve";
      return nullptr;
    }
  } else {
    EVP_PKEY_free(pkey);
    RTC_LOG(LS_ERROR) << "Key type requested not understood";
    return nullptr;
  }

  RTC_LOG(LS_INFO) << "Returning key pair";
  return pkey;
}

std::unique_ptr<OpenSSLKeyPair> OpenSSLKeyPair::Generate(
    const KeyParams& key_params) {
  EVP_PKEY* pkey = MakeKey(key_params);
  if (!pkey) {
    openssl::LogSSLErrors("Generating key pair");
    return nullptr;
  }
  return std::make_unique<OpenSSLKeyPair>(pkey);
}

std::unique_ptr<OpenSSLKeyPair> OpenSSLKeyPair::FromPrivateKeyPEMString(
    absl::string_view pem_string) {
  BIO* bio =
      BIO_new_mem_buf(const_cast<char*>(pem_string.data()), pem_string.size());
  if (!bio) {
    RTC_LOG(LS_ERROR) << "Failed to create a new BIO buffer.";
    return nullptr;
  }
  BIO_set_mem_eof_return(bio, 0);
  EVP_PKEY* pkey = PEM_read_bio_PrivateKey(bio, nullptr, nullptr, nullptr);
  BIO_free(bio);  // Frees the BIO, but not the pointed-to string.
  if (!pkey) {
    RTC_LOG(LS_ERROR) << "Failed to create the private key from PEM string.";
    return nullptr;
  }
  if (EVP_PKEY_missing_parameters(pkey) != 0) {
    RTC_LOG(LS_ERROR)
        << "The resulting key pair is missing public key parameters.";
    EVP_PKEY_free(pkey);
    return nullptr;
  }
  return std::make_unique<OpenSSLKeyPair>(pkey);
}

OpenSSLKeyPair::~OpenSSLKeyPair() {
  EVP_PKEY_free(pkey_);
}

std::unique_ptr<OpenSSLKeyPair> OpenSSLKeyPair::Clone() {
  AddReference();
  return std::make_unique<OpenSSLKeyPair>(pkey_);
}

void OpenSSLKeyPair::AddReference() {
  EVP_PKEY_up_ref(pkey_);
}

std::string OpenSSLKeyPair::PrivateKeyToPEMString() const {
  BIO* temp_memory_bio = BIO_new(BIO_s_mem());
  if (!temp_memory_bio) {
    RTC_LOG_F(LS_ERROR) << "Failed to allocate temporary memory bio";
    RTC_DCHECK_NOTREACHED();
    return "";
  }
  if (!PEM_write_bio_PrivateKey(temp_memory_bio, pkey_, nullptr, nullptr, 0,
                                nullptr, nullptr)) {
    RTC_LOG_F(LS_ERROR) << "Failed to write private key";
    BIO_free(temp_memory_bio);
    RTC_DCHECK_NOTREACHED();
    return "";
  }
  char* buffer;
  size_t len = BIO_get_mem_data(temp_memory_bio, &buffer);
  std::string priv_key_str(buffer, len);
  BIO_free(temp_memory_bio);
  return priv_key_str;
}

std::string OpenSSLKeyPair::PublicKeyToPEMString() const {
  BIO* temp_memory_bio = BIO_new(BIO_s_mem());
  if (!temp_memory_bio) {
    RTC_LOG_F(LS_ERROR) << "Failed to allocate temporary memory bio";
    RTC_DCHECK_NOTREACHED();
    return "";
  }
  if (!PEM_write_bio_PUBKEY(temp_memory_bio, pkey_)) {
    RTC_LOG_F(LS_ERROR) << "Failed to write public key";
    BIO_free(temp_memory_bio);
    RTC_DCHECK_NOTREACHED();
    return "";
  }
  BIO_write(temp_memory_bio, "\0", 1);
  char* buffer;
  BIO_get_mem_data(temp_memory_bio, &buffer);
  std::string pub_key_str = buffer;
  BIO_free(temp_memory_bio);
  return pub_key_str;
}

bool OpenSSLKeyPair::operator==(const OpenSSLKeyPair& other) const {
  return EVP_PKEY_cmp(this->pkey_, other.pkey_) == 1;
}

bool OpenSSLKeyPair::operator!=(const OpenSSLKeyPair& other) const {
  return !(*this == other);
}

}  // namespace rtc
