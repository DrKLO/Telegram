/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/boringssl_certificate.h"

#if defined(WEBRTC_WIN)
// Must be included first before openssl headers.
#include "rtc_base/win32.h"  // NOLINT
#endif                       // WEBRTC_WIN

#include <openssl/asn1.h>
#include <openssl/bytestring.h>
#include <openssl/digest.h>
#include <openssl/evp.h>
#include <openssl/mem.h>
#include <openssl/pool.h>
#include <openssl/rand.h>
#include <time.h>

#include <cstring>
#include <memory>
#include <utility>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/openssl_digest.h"
#include "rtc_base/openssl_key_pair.h"
#include "rtc_base/openssl_utility.h"

namespace rtc {
namespace {

// List of OIDs of signature algorithms accepted by WebRTC.
// Taken from openssl/nid.h.
static const uint8_t kMD5WithRSA[] = {0x2b, 0x0e, 0x03, 0x02, 0x03};
static const uint8_t kMD5WithRSAEncryption[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                                0x0d, 0x01, 0x01, 0x04};
static const uint8_t kECDSAWithSHA1[] = {0x2a, 0x86, 0x48, 0xce,
                                         0x3d, 0x04, 0x01};
static const uint8_t kDSAWithSHA1[] = {0x2a, 0x86, 0x48, 0xce,
                                       0x38, 0x04, 0x03};
static const uint8_t kDSAWithSHA1_2[] = {0x2b, 0x0e, 0x03, 0x02, 0x1b};
static const uint8_t kSHA1WithRSA[] = {0x2b, 0x0e, 0x03, 0x02, 0x1d};
static const uint8_t kSHA1WithRSAEncryption[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                                 0x0d, 0x01, 0x01, 0x05};
static const uint8_t kECDSAWithSHA224[] = {0x2a, 0x86, 0x48, 0xce,
                                           0x3d, 0x04, 0x03, 0x01};
static const uint8_t kSHA224WithRSAEncryption[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                                   0x0d, 0x01, 0x01, 0x0e};
static const uint8_t kDSAWithSHA224[] = {0x60, 0x86, 0x48, 0x01, 0x65,
                                         0x03, 0x04, 0x03, 0x01};
static const uint8_t kECDSAWithSHA256[] = {0x2a, 0x86, 0x48, 0xce,
                                           0x3d, 0x04, 0x03, 0x02};
static const uint8_t kSHA256WithRSAEncryption[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                                   0x0d, 0x01, 0x01, 0x0b};
static const uint8_t kDSAWithSHA256[] = {0x60, 0x86, 0x48, 0x01, 0x65,
                                         0x03, 0x04, 0x03, 0x02};
static const uint8_t kECDSAWithSHA384[] = {0x2a, 0x86, 0x48, 0xce,
                                           0x3d, 0x04, 0x03, 0x03};
static const uint8_t kSHA384WithRSAEncryption[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                                   0x0d, 0x01, 0x01, 0x0c};
static const uint8_t kECDSAWithSHA512[] = {0x2a, 0x86, 0x48, 0xce,
                                           0x3d, 0x04, 0x03, 0x04};
static const uint8_t kSHA512WithRSAEncryption[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                                   0x0d, 0x01, 0x01, 0x0d};

#if !defined(NDEBUG)
// Print a certificate to the log, for debugging.
static void PrintCert(BoringSSLCertificate* cert) {
  // Since we're using CRYPTO_BUFFER, we can't use X509_print_ex, so we'll just
  // print the PEM string.
  RTC_DLOG(LS_VERBOSE) << "PEM representation of certificate:\n"
                       << cert->ToPEMString();
}
#endif

bool AddSHA256SignatureAlgorithm(CBB* cbb, KeyType key_type) {
  // An AlgorithmIdentifier is described in RFC 5280, 4.1.1.2.
  CBB sequence, oid, params;
  if (!CBB_add_asn1(cbb, &sequence, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&sequence, &oid, CBS_ASN1_OBJECT)) {
    return false;
  }

  switch (key_type) {
    case KT_RSA:
      if (!CBB_add_bytes(&oid, kSHA256WithRSAEncryption,
                         sizeof(kSHA256WithRSAEncryption)) ||
          !CBB_add_asn1(&sequence, &params, CBS_ASN1_NULL)) {
        return false;
      }
      break;
    case KT_ECDSA:
      if (!CBB_add_bytes(&oid, kECDSAWithSHA256, sizeof(kECDSAWithSHA256))) {
        return false;
      }
      break;
    default:
      RTC_NOTREACHED();
      return false;
  }
  if (!CBB_flush(cbb)) {
    return false;
  }
  return true;
}

// Adds an X.509 Common Name to |cbb|.
bool AddCommonName(CBB* cbb, const std::string& common_name) {
  // See RFC 4519.
  static const uint8_t kCommonName[] = {0x55, 0x04, 0x03};

  if (common_name.empty()) {
    RTC_LOG(LS_ERROR) << "Common name cannot be empty.";
    return false;
  }

  // See RFC 5280, section 4.1.2.4.
  CBB rdns;
  if (!CBB_add_asn1(cbb, &rdns, CBS_ASN1_SEQUENCE)) {
    return false;
  }

  CBB rdn, attr, type, value;
  if (!CBB_add_asn1(&rdns, &rdn, CBS_ASN1_SET) ||
      !CBB_add_asn1(&rdn, &attr, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&attr, &type, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&type, kCommonName, sizeof(kCommonName)) ||
      !CBB_add_asn1(&attr, &value, CBS_ASN1_UTF8STRING) ||
      !CBB_add_bytes(&value,
                     reinterpret_cast<const uint8_t*>(common_name.c_str()),
                     common_name.size()) ||
      !CBB_flush(cbb)) {
    return false;
  }

  return true;
}

bool AddTime(CBB* cbb, time_t time) {
  bssl::UniquePtr<ASN1_TIME> asn1_time(ASN1_TIME_new());
  if (!asn1_time) {
    return false;
  }

  if (!ASN1_TIME_set(asn1_time.get(), time)) {
    return false;
  }

  unsigned tag;
  switch (asn1_time->type) {
    case V_ASN1_UTCTIME:
      tag = CBS_ASN1_UTCTIME;
      break;
    case V_ASN1_GENERALIZEDTIME:
      tag = CBS_ASN1_GENERALIZEDTIME;
      break;
    default:
      return false;
  }

  CBB child;
  if (!CBB_add_asn1(cbb, &child, tag) ||
      !CBB_add_bytes(&child, asn1_time->data, asn1_time->length) ||
      !CBB_flush(cbb)) {
    return false;
  }

  return true;
}

// Generate a self-signed certificate, with the public key from the
// given key pair. Caller is responsible for freeing the returned object.
static bssl::UniquePtr<CRYPTO_BUFFER> MakeCertificate(
    EVP_PKEY* pkey,
    const SSLIdentityParams& params) {
  RTC_LOG(LS_INFO) << "Making certificate for " << params.common_name;

  // See RFC 5280, section 4.1. First, construct the TBSCertificate.
  bssl::ScopedCBB cbb;
  CBB tbs_cert, version, validity;
  uint8_t* tbs_cert_bytes;
  size_t tbs_cert_len;
  uint64_t serial_number;
  if (!CBB_init(cbb.get(), 64) ||
      !CBB_add_asn1(cbb.get(), &tbs_cert, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&tbs_cert, &version,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      !CBB_add_asn1_uint64(&version, 2) ||
      !RAND_bytes(reinterpret_cast<uint8_t*>(&serial_number),
                  sizeof(serial_number)) ||
      !CBB_add_asn1_uint64(&tbs_cert, serial_number) ||
      !AddSHA256SignatureAlgorithm(&tbs_cert, params.key_params.type()) ||
      !AddCommonName(&tbs_cert, params.common_name) ||  // issuer
      !CBB_add_asn1(&tbs_cert, &validity, CBS_ASN1_SEQUENCE) ||
      !AddTime(&validity, params.not_before) ||
      !AddTime(&validity, params.not_after) ||
      !AddCommonName(&tbs_cert, params.common_name) ||  // subject
      !EVP_marshal_public_key(&tbs_cert, pkey) ||       // subjectPublicKeyInfo
      !CBB_finish(cbb.get(), &tbs_cert_bytes, &tbs_cert_len)) {
    return nullptr;
  }

  bssl::UniquePtr<uint8_t> delete_tbs_cert_bytes(tbs_cert_bytes);

  // Sign the TBSCertificate and write the entire certificate.
  CBB cert, signature;
  bssl::ScopedEVP_MD_CTX ctx;
  uint8_t* sig_out;
  size_t sig_len;
  uint8_t* cert_bytes;
  size_t cert_len;
  if (!CBB_init(cbb.get(), tbs_cert_len) ||
      !CBB_add_asn1(cbb.get(), &cert, CBS_ASN1_SEQUENCE) ||
      !CBB_add_bytes(&cert, tbs_cert_bytes, tbs_cert_len) ||
      !AddSHA256SignatureAlgorithm(&cert, params.key_params.type()) ||
      !CBB_add_asn1(&cert, &signature, CBS_ASN1_BITSTRING) ||
      !CBB_add_u8(&signature, 0 /* no unused bits */) ||
      !EVP_DigestSignInit(ctx.get(), nullptr, EVP_sha256(), nullptr, pkey) ||
      // Compute the maximum signature length.
      !EVP_DigestSign(ctx.get(), nullptr, &sig_len, tbs_cert_bytes,
                      tbs_cert_len) ||
      !CBB_reserve(&signature, &sig_out, sig_len) ||
      // Actually sign the TBSCertificate.
      !EVP_DigestSign(ctx.get(), sig_out, &sig_len, tbs_cert_bytes,
                      tbs_cert_len) ||
      !CBB_did_write(&signature, sig_len) ||
      !CBB_finish(cbb.get(), &cert_bytes, &cert_len)) {
    return nullptr;
  }
  bssl::UniquePtr<uint8_t> delete_cert_bytes(cert_bytes);

  RTC_LOG(LS_INFO) << "Returning certificate";
  return bssl::UniquePtr<CRYPTO_BUFFER>(
      CRYPTO_BUFFER_new(cert_bytes, cert_len, openssl::GetBufferPool()));
}

}  // namespace

BoringSSLCertificate::BoringSSLCertificate(
    bssl::UniquePtr<CRYPTO_BUFFER> cert_buffer)
    : cert_buffer_(std::move(cert_buffer)) {
  RTC_DCHECK(cert_buffer_ != nullptr);
}

std::unique_ptr<BoringSSLCertificate> BoringSSLCertificate::Generate(
    OpenSSLKeyPair* key_pair,
    const SSLIdentityParams& params) {
  SSLIdentityParams actual_params(params);
  if (actual_params.common_name.empty()) {
    // Use a random string, arbitrarily 8 chars long.
    actual_params.common_name = CreateRandomString(8);
  }
  bssl::UniquePtr<CRYPTO_BUFFER> cert_buffer =
      MakeCertificate(key_pair->pkey(), actual_params);
  if (!cert_buffer) {
    openssl::LogSSLErrors("Generating certificate");
    return nullptr;
  }
  auto ret = std::make_unique<BoringSSLCertificate>(std::move(cert_buffer));
#if !defined(NDEBUG)
  PrintCert(ret.get());
#endif
  return ret;
}

std::unique_ptr<BoringSSLCertificate> BoringSSLCertificate::FromPEMString(
    const std::string& pem_string) {
  std::string der;
  if (!SSLIdentity::PemToDer(kPemTypeCertificate, pem_string, &der)) {
    return nullptr;
  }
  bssl::UniquePtr<CRYPTO_BUFFER> cert_buffer(
      CRYPTO_BUFFER_new(reinterpret_cast<const uint8_t*>(der.c_str()),
                        der.length(), openssl::GetBufferPool()));
  if (!cert_buffer) {
    return nullptr;
  }
  return std::make_unique<BoringSSLCertificate>(std::move(cert_buffer));
}

#define OID_MATCHES(oid, oid_other)      \
  (CBS_len(&oid) == sizeof(oid_other) && \
   0 == memcmp(CBS_data(&oid), oid_other, sizeof(oid_other)))

bool BoringSSLCertificate::GetSignatureDigestAlgorithm(
    std::string* algorithm) const {
  CBS oid;
  if (!openssl::ParseCertificate(cert_buffer_.get(), &oid, nullptr)) {
    RTC_LOG(LS_ERROR) << "Failed to parse certificate.";
    return false;
  }
  if (OID_MATCHES(oid, kMD5WithRSA) ||
      OID_MATCHES(oid, kMD5WithRSAEncryption)) {
    *algorithm = DIGEST_MD5;
    return true;
  }
  if (OID_MATCHES(oid, kECDSAWithSHA1) || OID_MATCHES(oid, kDSAWithSHA1) ||
      OID_MATCHES(oid, kDSAWithSHA1_2) || OID_MATCHES(oid, kSHA1WithRSA) ||
      OID_MATCHES(oid, kSHA1WithRSAEncryption)) {
    *algorithm = DIGEST_SHA_1;
    return true;
  }
  if (OID_MATCHES(oid, kECDSAWithSHA224) ||
      OID_MATCHES(oid, kSHA224WithRSAEncryption) ||
      OID_MATCHES(oid, kDSAWithSHA224)) {
    *algorithm = DIGEST_SHA_224;
    return true;
  }
  if (OID_MATCHES(oid, kECDSAWithSHA256) ||
      OID_MATCHES(oid, kSHA256WithRSAEncryption) ||
      OID_MATCHES(oid, kDSAWithSHA256)) {
    *algorithm = DIGEST_SHA_256;
    return true;
  }
  if (OID_MATCHES(oid, kECDSAWithSHA384) ||
      OID_MATCHES(oid, kSHA384WithRSAEncryption)) {
    *algorithm = DIGEST_SHA_384;
    return true;
  }
  if (OID_MATCHES(oid, kECDSAWithSHA512) ||
      OID_MATCHES(oid, kSHA512WithRSAEncryption)) {
    *algorithm = DIGEST_SHA_512;
    return true;
  }
  // Unknown algorithm.  There are several unhandled options that are less
  // common and more complex.
  RTC_LOG(LS_ERROR) << "Unknown signature algorithm.";
  algorithm->clear();
  return false;
}

bool BoringSSLCertificate::ComputeDigest(const std::string& algorithm,
                                         unsigned char* digest,
                                         size_t size,
                                         size_t* length) const {
  return ComputeDigest(cert_buffer_.get(), algorithm, digest, size, length);
}

bool BoringSSLCertificate::ComputeDigest(const CRYPTO_BUFFER* cert_buffer,
                                         const std::string& algorithm,
                                         unsigned char* digest,
                                         size_t size,
                                         size_t* length) {
  const EVP_MD* md = nullptr;
  unsigned int n = 0;
  if (!OpenSSLDigest::GetDigestEVP(algorithm, &md)) {
    return false;
  }
  if (size < static_cast<size_t>(EVP_MD_size(md))) {
    return false;
  }
  if (!EVP_Digest(CRYPTO_BUFFER_data(cert_buffer),
                  CRYPTO_BUFFER_len(cert_buffer), digest, &n, md, nullptr)) {
    return false;
  }
  *length = n;
  return true;
}

BoringSSLCertificate::~BoringSSLCertificate() {}

std::unique_ptr<SSLCertificate> BoringSSLCertificate::Clone() const {
  return std::make_unique<BoringSSLCertificate>(
      bssl::UpRef(cert_buffer_.get()));
}

std::string BoringSSLCertificate::ToPEMString() const {
  return SSLIdentity::DerToPem(kPemTypeCertificate,
                               CRYPTO_BUFFER_data(cert_buffer_.get()),
                               CRYPTO_BUFFER_len(cert_buffer_.get()));
}

void BoringSSLCertificate::ToDER(Buffer* der_buffer) const {
  der_buffer->SetData(CRYPTO_BUFFER_data(cert_buffer_.get()),
                      CRYPTO_BUFFER_len(cert_buffer_.get()));
}

bool BoringSSLCertificate::operator==(const BoringSSLCertificate& other) const {
  return CRYPTO_BUFFER_len(cert_buffer_.get()) ==
             CRYPTO_BUFFER_len(other.cert_buffer_.get()) &&
         0 == memcmp(CRYPTO_BUFFER_data(cert_buffer_.get()),
                     CRYPTO_BUFFER_data(other.cert_buffer_.get()),
                     CRYPTO_BUFFER_len(cert_buffer_.get()));
}

bool BoringSSLCertificate::operator!=(const BoringSSLCertificate& other) const {
  return !(*this == other);
}

int64_t BoringSSLCertificate::CertificateExpirationTime() const {
  int64_t ret;
  if (!openssl::ParseCertificate(cert_buffer_.get(), nullptr, &ret)) {
    RTC_LOG(LS_ERROR) << "Failed to parse certificate.";
    return -1;
  }
  return ret;
}

}  // namespace rtc
