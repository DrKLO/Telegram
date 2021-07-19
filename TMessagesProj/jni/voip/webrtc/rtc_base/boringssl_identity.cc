/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/boringssl_identity.h"

#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/pool.h>
#include <stdint.h>
#include <string.h>

#include <memory>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/openssl.h"
#include "rtc_base/openssl_utility.h"

namespace rtc {

BoringSSLIdentity::BoringSSLIdentity(
    std::unique_ptr<OpenSSLKeyPair> key_pair,
    std::unique_ptr<BoringSSLCertificate> certificate)
    : key_pair_(std::move(key_pair)) {
  RTC_DCHECK(key_pair_ != nullptr);
  RTC_DCHECK(certificate != nullptr);
  std::vector<std::unique_ptr<SSLCertificate>> certs;
  certs.push_back(std::move(certificate));
  cert_chain_.reset(new SSLCertChain(std::move(certs)));
}

BoringSSLIdentity::BoringSSLIdentity(std::unique_ptr<OpenSSLKeyPair> key_pair,
                                     std::unique_ptr<SSLCertChain> cert_chain)
    : key_pair_(std::move(key_pair)), cert_chain_(std::move(cert_chain)) {
  RTC_DCHECK(key_pair_ != nullptr);
  RTC_DCHECK(cert_chain_ != nullptr);
}

BoringSSLIdentity::~BoringSSLIdentity() = default;

std::unique_ptr<BoringSSLIdentity> BoringSSLIdentity::CreateInternal(
    const SSLIdentityParams& params) {
  auto key_pair = OpenSSLKeyPair::Generate(params.key_params);
  if (key_pair) {
    std::unique_ptr<BoringSSLCertificate> certificate(
        BoringSSLCertificate::Generate(key_pair.get(), params));
    if (certificate != nullptr) {
      return absl::WrapUnique(
          new BoringSSLIdentity(std::move(key_pair), std::move(certificate)));
    }
  }
  RTC_LOG(LS_ERROR) << "Identity generation failed.";
  return nullptr;
}

// static
std::unique_ptr<BoringSSLIdentity> BoringSSLIdentity::CreateWithExpiration(
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

std::unique_ptr<BoringSSLIdentity> BoringSSLIdentity::CreateForTest(
    const SSLIdentityParams& params) {
  return CreateInternal(params);
}

std::unique_ptr<SSLIdentity> BoringSSLIdentity::CreateFromPEMStrings(
    const std::string& private_key,
    const std::string& certificate) {
  std::unique_ptr<BoringSSLCertificate> cert(
      BoringSSLCertificate::FromPEMString(certificate));
  if (!cert) {
    RTC_LOG(LS_ERROR)
        << "Failed to create BoringSSLCertificate from PEM string.";
    return nullptr;
  }

  auto key_pair = OpenSSLKeyPair::FromPrivateKeyPEMString(private_key);
  if (!key_pair) {
    RTC_LOG(LS_ERROR) << "Failed to create key pair from PEM string.";
    return nullptr;
  }

  return absl::WrapUnique(
      new BoringSSLIdentity(std::move(key_pair), std::move(cert)));
}

std::unique_ptr<SSLIdentity> BoringSSLIdentity::CreateFromPEMChainStrings(
    const std::string& private_key,
    const std::string& certificate_chain) {
  bssl::UniquePtr<BIO> bio(
      BIO_new_mem_buf(certificate_chain.data(),
                      rtc::dchecked_cast<int>(certificate_chain.size())));
  if (!bio) {
    return nullptr;
  }
  BIO_set_mem_eof_return(bio.get(), 0);
  std::vector<std::unique_ptr<SSLCertificate>> certs;
  while (true) {
    char* name;
    char* header;
    unsigned char* data;
    long len;  // NOLINT
    int ret = PEM_read_bio(bio.get(), &name, &header, &data, &len);
    if (ret == 0) {
      uint32_t err = ERR_peek_error();
      if (ERR_GET_LIB(err) == ERR_LIB_PEM &&
          ERR_GET_REASON(err) == PEM_R_NO_START_LINE) {
        break;
      }
      RTC_LOG(LS_ERROR) << "Failed to parse certificate from PEM string.";
      return nullptr;
    }
    bssl::UniquePtr<char> owned_name(name);
    bssl::UniquePtr<char> owned_header(header);
    bssl::UniquePtr<unsigned char> owned_data(data);
    if (strcmp(owned_name.get(), PEM_STRING_X509) != 0) {
      RTC_LOG(LS_ERROR)
          << "Non-certificate found while parsing certificate chain: "
          << owned_name.get();
      return nullptr;
    }
    bssl::UniquePtr<CRYPTO_BUFFER> crypto_buffer(
        CRYPTO_BUFFER_new(data, len, openssl::GetBufferPool()));
    if (!crypto_buffer) {
      return nullptr;
    }
    certs.emplace_back(new BoringSSLCertificate(std::move(crypto_buffer)));
  }
  if (certs.empty()) {
    RTC_LOG(LS_ERROR) << "Found no certificates in PEM string.";
    return nullptr;
  }

  auto key_pair = OpenSSLKeyPair::FromPrivateKeyPEMString(private_key);
  if (!key_pair) {
    RTC_LOG(LS_ERROR) << "Failed to create key pair from PEM string.";
    return nullptr;
  }

  return absl::WrapUnique(new BoringSSLIdentity(
      std::move(key_pair), std::make_unique<SSLCertChain>(std::move(certs))));
}

const BoringSSLCertificate& BoringSSLIdentity::certificate() const {
  return *static_cast<const BoringSSLCertificate*>(&cert_chain_->Get(0));
}

const SSLCertChain& BoringSSLIdentity::cert_chain() const {
  return *cert_chain_.get();
}

std::unique_ptr<SSLIdentity> BoringSSLIdentity::CloneInternal() const {
  // We cannot use std::make_unique here because the referenced
  // BoringSSLIdentity constructor is private.
  return absl::WrapUnique(
      new BoringSSLIdentity(key_pair_->Clone(), cert_chain_->Clone()));
}

bool BoringSSLIdentity::ConfigureIdentity(SSL_CTX* ctx) {
  std::vector<CRYPTO_BUFFER*> cert_buffers;
  for (size_t i = 0; i < cert_chain_->GetSize(); ++i) {
    cert_buffers.push_back(
        static_cast<const BoringSSLCertificate*>(&cert_chain_->Get(i))
            ->cert_buffer());
  }
  // 1 is the documented success return code.
  if (1 != SSL_CTX_set_chain_and_key(ctx, &cert_buffers[0], cert_buffers.size(),
                                     key_pair_->pkey(), nullptr)) {
    openssl::LogSSLErrors("Configuring key and certificate");
    return false;
  }
  return true;
}

std::string BoringSSLIdentity::PrivateKeyToPEMString() const {
  return key_pair_->PrivateKeyToPEMString();
}

std::string BoringSSLIdentity::PublicKeyToPEMString() const {
  return key_pair_->PublicKeyToPEMString();
}

bool BoringSSLIdentity::operator==(const BoringSSLIdentity& other) const {
  return *this->key_pair_ == *other.key_pair_ &&
         this->certificate() == other.certificate();
}

bool BoringSSLIdentity::operator!=(const BoringSSLIdentity& other) const {
  return !(*this == other);
}

}  // namespace rtc
