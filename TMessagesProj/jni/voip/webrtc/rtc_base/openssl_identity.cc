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
#include <openssl/err.h>
#include <openssl/pem.h>
#include <stdint.h>

#include "absl/memory/memory.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/openssl.h"
#include "rtc_base/openssl_utility.h"

namespace rtc {

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
  auto key_pair = OpenSSLKeyPair::Generate(params.key_params);
  if (key_pair) {
    std::unique_ptr<OpenSSLCertificate> certificate(
        OpenSSLCertificate::Generate(key_pair.get(), params));
    if (certificate != nullptr) {
      return absl::WrapUnique(
          new OpenSSLIdentity(std::move(key_pair), std::move(certificate)));
    }
  }
  RTC_LOG(LS_ERROR) << "Identity generation failed";
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

  auto key_pair = OpenSSLKeyPair::FromPrivateKeyPEMString(private_key);
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

  auto key_pair = OpenSSLKeyPair::FromPrivateKeyPEMString(private_key);
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
  return absl::WrapUnique(
      new OpenSSLIdentity(key_pair_->Clone(), cert_chain_->Clone()));
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
