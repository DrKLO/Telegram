/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/fake_ssl_identity.h"

#include <memory>
#include <string>
#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/message_digest.h"

namespace rtc {

FakeSSLCertificate::FakeSSLCertificate(const std::string& pem_string)
    : pem_string_(pem_string),
      digest_algorithm_(DIGEST_SHA_1),
      expiration_time_(-1) {}

FakeSSLCertificate::FakeSSLCertificate(const FakeSSLCertificate&) = default;

FakeSSLCertificate::~FakeSSLCertificate() = default;

std::unique_ptr<SSLCertificate> FakeSSLCertificate::Clone() const {
  return std::make_unique<FakeSSLCertificate>(*this);
}

std::string FakeSSLCertificate::ToPEMString() const {
  return pem_string_;
}

void FakeSSLCertificate::ToDER(Buffer* der_buffer) const {
  std::string der_string;
  RTC_CHECK(
      SSLIdentity::PemToDer(kPemTypeCertificate, pem_string_, &der_string));
  der_buffer->SetData(der_string.c_str(), der_string.size());
}

int64_t FakeSSLCertificate::CertificateExpirationTime() const {
  return expiration_time_;
}

void FakeSSLCertificate::SetCertificateExpirationTime(int64_t expiration_time) {
  expiration_time_ = expiration_time;
}

void FakeSSLCertificate::set_digest_algorithm(const std::string& algorithm) {
  digest_algorithm_ = algorithm;
}

bool FakeSSLCertificate::GetSignatureDigestAlgorithm(
    std::string* algorithm) const {
  *algorithm = digest_algorithm_;
  return true;
}

bool FakeSSLCertificate::ComputeDigest(const std::string& algorithm,
                                       unsigned char* digest,
                                       size_t size,
                                       size_t* length) const {
  *length = rtc::ComputeDigest(algorithm, pem_string_.c_str(),
                               pem_string_.size(), digest, size);
  return (*length != 0);
}

FakeSSLIdentity::FakeSSLIdentity(const std::string& pem_string)
    : FakeSSLIdentity(FakeSSLCertificate(pem_string)) {}

FakeSSLIdentity::FakeSSLIdentity(const std::vector<std::string>& pem_strings) {
  std::vector<std::unique_ptr<SSLCertificate>> certs;
  certs.reserve(pem_strings.size());
  for (const std::string& pem_string : pem_strings) {
    certs.push_back(std::make_unique<FakeSSLCertificate>(pem_string));
  }
  cert_chain_ = std::make_unique<SSLCertChain>(std::move(certs));
}

FakeSSLIdentity::FakeSSLIdentity(const FakeSSLCertificate& cert)
    : cert_chain_(std::make_unique<SSLCertChain>(cert.Clone())) {}

FakeSSLIdentity::FakeSSLIdentity(const FakeSSLIdentity& o)
    : cert_chain_(o.cert_chain_->Clone()) {}

FakeSSLIdentity::~FakeSSLIdentity() = default;

std::unique_ptr<SSLIdentity> FakeSSLIdentity::CloneInternal() const {
  return std::make_unique<FakeSSLIdentity>(*this);
}

const SSLCertificate& FakeSSLIdentity::certificate() const {
  return cert_chain_->Get(0);
}

const SSLCertChain& FakeSSLIdentity::cert_chain() const {
  return *cert_chain_.get();
}

std::string FakeSSLIdentity::PrivateKeyToPEMString() const {
  RTC_NOTREACHED();  // Not implemented.
  return "";
}

std::string FakeSSLIdentity::PublicKeyToPEMString() const {
  RTC_NOTREACHED();  // Not implemented.
  return "";
}

bool FakeSSLIdentity::operator==(const SSLIdentity& other) const {
  RTC_NOTREACHED();  // Not implemented.
  return false;
}

}  // namespace rtc
