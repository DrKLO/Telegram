/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/rtc_certificate.h"

#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/ssl_identity.h"
#include "rtc_base/time_utils.h"

namespace rtc {

scoped_refptr<RTCCertificate> RTCCertificate::Create(
    std::unique_ptr<SSLIdentity> identity) {
  // Explicit new to access proteced constructor.
  return rtc::scoped_refptr<RTCCertificate>(
      new RTCCertificate(identity.release()));
}

RTCCertificate::RTCCertificate(SSLIdentity* identity) : identity_(identity) {
  RTC_DCHECK(identity_);
}

RTCCertificate::~RTCCertificate() = default;

uint64_t RTCCertificate::Expires() const {
  int64_t expires = GetSSLCertificate().CertificateExpirationTime();
  if (expires != -1)
    return static_cast<uint64_t>(expires) * kNumMillisecsPerSec;
  // If the expiration time could not be retrieved return an expired timestamp.
  return 0;  // = 1970-01-01
}

bool RTCCertificate::HasExpired(uint64_t now) const {
  return Expires() <= now;
}

const SSLCertificate& RTCCertificate::GetSSLCertificate() const {
  return identity_->certificate();
}

const SSLCertChain& RTCCertificate::GetSSLCertificateChain() const {
  return identity_->cert_chain();
}

RTCCertificatePEM RTCCertificate::ToPEM() const {
  return RTCCertificatePEM(identity_->PrivateKeyToPEMString(),
                           GetSSLCertificate().ToPEMString());
}

scoped_refptr<RTCCertificate> RTCCertificate::FromPEM(
    const RTCCertificatePEM& pem) {
  std::unique_ptr<SSLIdentity> identity(
      SSLIdentity::CreateFromPEMStrings(pem.private_key(), pem.certificate()));
  if (!identity)
    return nullptr;
  return RTCCertificate::Create(std::move(identity));
}

bool RTCCertificate::operator==(const RTCCertificate& certificate) const {
  return *this->identity_ == *certificate.identity_;
}

bool RTCCertificate::operator!=(const RTCCertificate& certificate) const {
  return !(*this == certificate);
}

}  // namespace rtc
