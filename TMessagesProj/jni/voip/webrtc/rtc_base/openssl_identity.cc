/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/openssl_identity.h"

#include <memory>
#include <utility>
#include <vector>

#if defined(WEBRTC_WIN)
// Must be included first before openssl headers.
#include "rtc_base/win32.h"  // NOLINT
#endif                       // WEBRTC_WIN

#include <openssl/bio.h>
#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/rsa.h>
#include <stdint.h>

#include "absl/memory/memory.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
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

OpenSSLKeyPair* OpenSSLKeyPair::Generate(const KeyParams& key_params) {
  EVP_PKEY* pkey = MakeKey(key_params);
  if (!pkey) {
    openssl::LogSSLErrors("Generating key pair");
    return nullptr;
  }
  return new OpenSSLKeyPair(pkey);
}

OpenSSLKeyPair* OpenSSLKeyPair::FromPrivateKeyPEMString(
    const std::string& pem_string) {
  BIO* bio = BIO_new_mem_buf(const_cast<char*>(pem_string.c_str()), -1);
  if (!bio) {
    RTC_LOG(LS_ERROR) << "Failed to create a new BIO buffer.";
    return nullptr;
  }
  BIO_set_mem_eof_return(bio, 0);
  EVP_PKEY* pkey =
      PEM_read_bio_PrivateKey(bio, nullptr, nullptr, const_cast<char*>("\0"));
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
  return new OpenSSLKeyPair(pkey);
}

OpenSSLKeyPair::~OpenSSLKeyPair() {
  EVP_PKEY_free(pkey_);
}

OpenSSLKeyPair* OpenSSLKeyPair::GetReference() {
  AddReference();
  return new OpenSSLKeyPair(pkey_);
}

void OpenSSLKeyPair::AddReference() {
  EVP_PKEY_up_ref(pkey_);
}

std::string OpenSSLKeyPair::PrivateKeyToPEMString() const {
  BIO* temp_memory_bio = BIO_new(BIO_s_mem());
  if (!temp_memory_bio) {
    RTC_LOG_F(LS_ERROR) << "Failed to allocate temporary memory bio";
    RTC_NOTREACHED();
    return "";
  }
  if (!PEM_write_bio_PrivateKey(temp_memory_bio, pkey_, nullptr, nullptr, 0,
                                nullptr, nullptr)) {
    RTC_LOG_F(LS_ERROR) << "Failed to write private key";
    BIO_free(temp_memory_bio);
    RTC_NOTREACHED();
    return "";
  }
  BIO_write(temp_memory_bio, "\0", 1);
  char* buffer;
  BIO_get_mem_data(temp_memory_bio, &buffer);
  std::string priv_key_str = buffer;
  BIO_free(temp_memory_bio);
  return priv_key_str;
}

std::string OpenSSLKeyPair::PublicKeyToPEMString() const {
  BIO* temp_memory_bio = BIO_new(BIO_s_mem());
  if (!temp_memory_bio) {
    RTC_LOG_F(LS_ERROR) << "Failed to allocate temporary memory bio";
    RTC_NOTREACHED();
    return "";
  }
  if (!PEM_write_bio_PUBKEY(temp_memory_bio, pkey_)) {
    RTC_LOG_F(LS_ERROR) << "Failed to write public key";
    BIO_free(temp_memory_bio);
    RTC_NOTREACHED();
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

OpenSSLIdentity::OpenSSLIdentity(
    std::unique_ptr<OpenSSLKeyPair> key_pair,
    std::unique_ptr<OpenSSLCertificate> certificate)
    : key_pair_(std::move(key_pair)) {
  RTC_DCHECK(key_pair_ != nullptr);
  RTC_DCHECK(certificate != nullptr);
  std::vector<std::unique_ptr<SSLCertificate>> certs;
  certs.push_back(std::move(certificate));
  cert_chain_.reset(new SSLCertChain(std::move(certs)));
}

OpenSSLIdentity::OpenSSLIdentity(std::unique_ptr<OpenSSLKeyPair> key_pair,
                                 std::unique_ptr<SSLCertChain> cert_chain)
    : key_pair_(std::move(key_pair)), cert_chain_(std::move(cert_chain)) {
  RTC_DCHECK(key_pair_ != nullptr);
  RTC_DCHECK(cert_chain_ != nullptr);
}

OpenSSLIdentity::~OpenSSLIdentity() = default;

std::unique_ptr<OpenSSLIdentity> OpenSSLIdentity::CreateInternal(
    const SSLIdentityParams& params) {
  std::unique_ptr<OpenSSLKeyPair> key_pair(
      OpenSSLKeyPair::Generate(params.key_params));
  if (key_pair) {
    std::unique_ptr<OpenSSLCertificate> certificate(
        OpenSSLCertificate::Generate(key_pair.get(), params));
    if (certificate != nullptr) {
      return absl::WrapUnique(
          new OpenSSLIdentity(std::move(key_pair), std::move(certificate)));
    }
  }
  RTC_LOG(LS_INFO) << "Identity generation failed";
  return nullptr;
}

// static
std::unique_ptr<OpenSSLIdentity> OpenSSLIdentity::CreateWithExpiration(
    const std::string& common_name,
    const KeyParams& key_params,
    time_t certificate_lifetime) {
  SSLIdentityParams params;
  params.key_params = key_params;
  params.common_name = common_name;
  time_t now = time(nullptr);
  params.not_before = now + kCertificateWindowInSeconds;
  params.not_after = now + certificate_lifetime;
  if (params.not_before > params.not_after)
    return nullptr;
  return CreateInternal(params);
}

std::unique_ptr<OpenSSLIdentity> OpenSSLIdentity::CreateForTest(
    const SSLIdentityParams& params) {
  return CreateInternal(params);
}

std::unique_ptr<SSLIdentity> OpenSSLIdentity::CreateFromPEMStrings(
    const std::string& private_key,
    const std::string& certificate) {
  std::unique_ptr<OpenSSLCertificate> cert(
      OpenSSLCertificate::FromPEMString(certificate));
  if (!cert) {
    RTC_LOG(LS_ERROR) << "Failed to create OpenSSLCertificate from PEM string.";
    return nullptr;
  }

  std::unique_ptr<OpenSSLKeyPair> key_pair(
      OpenSSLKeyPair::FromPrivateKeyPEMString(private_key));
  if (!key_pair) {
    RTC_LOG(LS_ERROR) << "Failed to create key pair from PEM string.";
    return nullptr;
  }

  return absl::WrapUnique(
      new OpenSSLIdentity(std::move(key_pair), std::move(cert)));
}

std::unique_ptr<SSLIdentity> OpenSSLIdentity::CreateFromPEMChainStrings(
    const std::string& private_key,
    const std::string& certificate_chain) {
  BIO* bio = BIO_new_mem_buf(certificate_chain.data(),
                             rtc::dchecked_cast<int>(certificate_chain.size()));
  if (!bio)
    return nullptr;
  BIO_set_mem_eof_return(bio, 0);
  std::vector<std::unique_ptr<SSLCertificate>> certs;
  while (true) {
    X509* x509 =
        PEM_read_bio_X509(bio, nullptr, nullptr, const_cast<char*>("\0"));
    if (x509 == nullptr) {
      uint32_t err = ERR_peek_error();
      if (ERR_GET_LIB(err) == ERR_LIB_PEM &&
          ERR_GET_REASON(err) == PEM_R_NO_START_LINE) {
        break;
      }
      RTC_LOG(LS_ERROR) << "Failed to parse certificate from PEM string.";
      BIO_free(bio);
      return nullptr;
    }
    certs.emplace_back(new OpenSSLCertificate(x509));
    X509_free(x509);
  }
  BIO_free(bio);
  if (certs.empty()) {
    RTC_LOG(LS_ERROR) << "Found no certificates in PEM string.";
    return nullptr;
  }

  std::unique_ptr<OpenSSLKeyPair> key_pair(
      OpenSSLKeyPair::FromPrivateKeyPEMString(private_key));
  if (!key_pair) {
    RTC_LOG(LS_ERROR) << "Failed to create key pair from PEM string.";
    return nullptr;
  }

  return absl::WrapUnique(new OpenSSLIdentity(
      std::move(key_pair), std::make_unique<SSLCertChain>(std::move(certs))));
}

const OpenSSLCertificate& OpenSSLIdentity::certificate() const {
  return *static_cast<const OpenSSLCertificate*>(&cert_chain_->Get(0));
}

const SSLCertChain& OpenSSLIdentity::cert_chain() const {
  return *cert_chain_.get();
}

std::unique_ptr<SSLIdentity> OpenSSLIdentity::CloneInternal() const {
  // We cannot use std::make_unique here because the referenced OpenSSLIdentity
  // constructor is private.
  return absl::WrapUnique(new OpenSSLIdentity(
      absl::WrapUnique(key_pair_->GetReference()), cert_chain_->Clone()));
}

bool OpenSSLIdentity::ConfigureIdentity(SSL_CTX* ctx) {
  // 1 is the documented success return code.
  const OpenSSLCertificate* cert = &certificate();
  if (SSL_CTX_use_certificate(ctx, cert->x509()) != 1 ||
      SSL_CTX_use_PrivateKey(ctx, key_pair_->pkey()) != 1) {
    openssl::LogSSLErrors("Configuring key and certificate");
    return false;
  }
  // If a chain is available, use it.
  for (size_t i = 1; i < cert_chain_->GetSize(); ++i) {
    cert = static_cast<const OpenSSLCertificate*>(&cert_chain_->Get(i));
    if (SSL_CTX_add1_chain_cert(ctx, cert->x509()) != 1) {
      openssl::LogSSLErrors("Configuring intermediate certificate");
      return false;
    }
  }

  return true;
}

std::string OpenSSLIdentity::PrivateKeyToPEMString() const {
  return key_pair_->PrivateKeyToPEMString();
}

std::string OpenSSLIdentity::PublicKeyToPEMString() const {
  return key_pair_->PublicKeyToPEMString();
}

bool OpenSSLIdentity::operator==(const OpenSSLIdentity& other) const {
  return *this->key_pair_ == *other.key_pair_ &&
         this->certificate() == other.certificate();
}

bool OpenSSLIdentity::operator!=(const OpenSSLIdentity& other) const {
  return !(*this == other);
}

}  // namespace rtc
