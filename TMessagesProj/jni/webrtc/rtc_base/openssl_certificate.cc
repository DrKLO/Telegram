/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/openssl_certificate.h"

#if defined(WEBRTC_WIN)
// Must be included first before openssl headers.
#include "rtc_base/win32.h"  // NOLINT
#endif                       // WEBRTC_WIN

#include <openssl/bio.h>
#include <openssl/bn.h>
#include <openssl/pem.h>
#include <time.h>

#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/openssl_digest.h"
#include "rtc_base/openssl_identity.h"
#include "rtc_base/openssl_utility.h"

namespace rtc {
namespace {

// Random bits for certificate serial number
static const int SERIAL_RAND_BITS = 64;

#if !defined(NDEBUG)
// Print a certificate to the log, for debugging.
static void PrintCert(X509* x509) {
  BIO* temp_memory_bio = BIO_new(BIO_s_mem());
  if (!temp_memory_bio) {
    RTC_DLOG_F(LS_ERROR) << "Failed to allocate temporary memory bio";
    return;
  }
  X509_print_ex(temp_memory_bio, x509, XN_FLAG_SEP_CPLUS_SPC, 0);
  BIO_write(temp_memory_bio, "\0", 1);
  char* buffer;
  BIO_get_mem_data(temp_memory_bio, &buffer);
  RTC_DLOG(LS_VERBOSE) << buffer;
  BIO_free(temp_memory_bio);
}
#endif

// Generate a self-signed certificate, with the public key from the
// given key pair. Caller is responsible for freeing the returned object.
static X509* MakeCertificate(EVP_PKEY* pkey, const SSLIdentityParams& params) {
  RTC_LOG(LS_INFO) << "Making certificate for " << params.common_name;

  ASN1_INTEGER* asn1_serial_number = nullptr;
  BIGNUM* serial_number = nullptr;
  X509* x509 = nullptr;
  X509_NAME* name = nullptr;
  time_t epoch_off = 0;  // Time offset since epoch.

  if ((x509 = X509_new()) == nullptr) {
    goto error;
  }
  if (!X509_set_pubkey(x509, pkey)) {
    goto error;
  }
  // serial number - temporary reference to serial number inside x509 struct
  if ((serial_number = BN_new()) == nullptr ||
      !BN_pseudo_rand(serial_number, SERIAL_RAND_BITS, 0, 0) ||
      (asn1_serial_number = X509_get_serialNumber(x509)) == nullptr ||
      !BN_to_ASN1_INTEGER(serial_number, asn1_serial_number)) {
    goto error;
  }
  // Set version to X509.V3
  if (!X509_set_version(x509, 2L)) {
    goto error;
  }

  // There are a lot of possible components for the name entries. In
  // our P2P SSL mode however, the certificates are pre-exchanged
  // (through the secure XMPP channel), and so the certificate
  // identification is arbitrary. It can't be empty, so we set some
  // arbitrary common_name. Note that this certificate goes out in
  // clear during SSL negotiation, so there may be a privacy issue in
  // putting anything recognizable here.
  if ((name = X509_NAME_new()) == nullptr ||
      !X509_NAME_add_entry_by_NID(name, NID_commonName, MBSTRING_UTF8,
                                  (unsigned char*)params.common_name.c_str(),
                                  -1, -1, 0) ||
      !X509_set_subject_name(x509, name) || !X509_set_issuer_name(x509, name)) {
    goto error;
  }
  if (!X509_time_adj(X509_get_notBefore(x509), params.not_before, &epoch_off) ||
      !X509_time_adj(X509_get_notAfter(x509), params.not_after, &epoch_off)) {
    goto error;
  }
  if (!X509_sign(x509, pkey, EVP_sha256())) {
    goto error;
  }

  BN_free(serial_number);
  X509_NAME_free(name);
  RTC_LOG(LS_INFO) << "Returning certificate";
  return x509;

error:
  BN_free(serial_number);
  X509_NAME_free(name);
  X509_free(x509);
  return nullptr;
}

}  // namespace

OpenSSLCertificate::OpenSSLCertificate(X509* x509) : x509_(x509) {
  RTC_DCHECK(x509_ != nullptr);
  X509_up_ref(x509_);
}

std::unique_ptr<OpenSSLCertificate> OpenSSLCertificate::Generate(
    OpenSSLKeyPair* key_pair,
    const SSLIdentityParams& params) {
  SSLIdentityParams actual_params(params);
  if (actual_params.common_name.empty()) {
    // Use a random string, arbitrarily 8chars long.
    actual_params.common_name = CreateRandomString(8);
  }
  X509* x509 = MakeCertificate(key_pair->pkey(), actual_params);
  if (!x509) {
    openssl::LogSSLErrors("Generating certificate");
    return nullptr;
  }
#if !defined(NDEBUG)
  PrintCert(x509);
#endif
  auto ret = std::make_unique<OpenSSLCertificate>(x509);
  X509_free(x509);
  return ret;
}

std::unique_ptr<OpenSSLCertificate> OpenSSLCertificate::FromPEMString(
    const std::string& pem_string) {
  BIO* bio = BIO_new_mem_buf(const_cast<char*>(pem_string.c_str()), -1);
  if (!bio) {
    return nullptr;
  }

  BIO_set_mem_eof_return(bio, 0);
  X509* x509 =
      PEM_read_bio_X509(bio, nullptr, nullptr, const_cast<char*>("\0"));
  BIO_free(bio);  // Frees the BIO, but not the pointed-to string.

  if (!x509) {
    return nullptr;
  }
  auto ret = std::make_unique<OpenSSLCertificate>(x509);
  X509_free(x509);
  return ret;
}

// NOTE: This implementation only functions correctly after InitializeSSL
// and before CleanupSSL.
bool OpenSSLCertificate::GetSignatureDigestAlgorithm(
    std::string* algorithm) const {
  int nid = X509_get_signature_nid(x509_);
  switch (nid) {
    case NID_md5WithRSA:
    case NID_md5WithRSAEncryption:
      *algorithm = DIGEST_MD5;
      break;
    case NID_ecdsa_with_SHA1:
    case NID_dsaWithSHA1:
    case NID_dsaWithSHA1_2:
    case NID_sha1WithRSA:
    case NID_sha1WithRSAEncryption:
      *algorithm = DIGEST_SHA_1;
      break;
    case NID_ecdsa_with_SHA224:
    case NID_sha224WithRSAEncryption:
    case NID_dsa_with_SHA224:
      *algorithm = DIGEST_SHA_224;
      break;
    case NID_ecdsa_with_SHA256:
    case NID_sha256WithRSAEncryption:
    case NID_dsa_with_SHA256:
      *algorithm = DIGEST_SHA_256;
      break;
    case NID_ecdsa_with_SHA384:
    case NID_sha384WithRSAEncryption:
      *algorithm = DIGEST_SHA_384;
      break;
    case NID_ecdsa_with_SHA512:
    case NID_sha512WithRSAEncryption:
      *algorithm = DIGEST_SHA_512;
      break;
    default:
      // Unknown algorithm.  There are several unhandled options that are less
      // common and more complex.
      RTC_LOG(LS_ERROR) << "Unknown signature algorithm NID: " << nid;
      algorithm->clear();
      return false;
  }
  return true;
}

bool OpenSSLCertificate::ComputeDigest(const std::string& algorithm,
                                       unsigned char* digest,
                                       size_t size,
                                       size_t* length) const {
  return ComputeDigest(x509_, algorithm, digest, size, length);
}

bool OpenSSLCertificate::ComputeDigest(const X509* x509,
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
  X509_digest(x509, md, digest, &n);
  *length = n;
  return true;
}

OpenSSLCertificate::~OpenSSLCertificate() {
  X509_free(x509_);
}

std::unique_ptr<SSLCertificate> OpenSSLCertificate::Clone() const {
  return std::make_unique<OpenSSLCertificate>(x509_);
}

std::string OpenSSLCertificate::ToPEMString() const {
  BIO* bio = BIO_new(BIO_s_mem());
  if (!bio) {
    FATAL() << "Unreachable code.";
  }
  if (!PEM_write_bio_X509(bio, x509_)) {
    BIO_free(bio);
    FATAL() << "Unreachable code.";
  }
  BIO_write(bio, "\0", 1);
  char* buffer;
  BIO_get_mem_data(bio, &buffer);
  std::string ret(buffer);
  BIO_free(bio);
  return ret;
}

void OpenSSLCertificate::ToDER(Buffer* der_buffer) const {
  // In case of failure, make sure to leave the buffer empty.
  der_buffer->SetSize(0);
  // Calculates the DER representation of the certificate, from scratch.
  BIO* bio = BIO_new(BIO_s_mem());
  if (!bio) {
    FATAL() << "Unreachable code.";
  }
  if (!i2d_X509_bio(bio, x509_)) {
    BIO_free(bio);
    FATAL() << "Unreachable code.";
  }
  char* data = nullptr;
  size_t length = BIO_get_mem_data(bio, &data);
  der_buffer->SetData(data, length);
  BIO_free(bio);
}

bool OpenSSLCertificate::operator==(const OpenSSLCertificate& other) const {
  return X509_cmp(x509_, other.x509_) == 0;
}

bool OpenSSLCertificate::operator!=(const OpenSSLCertificate& other) const {
  return !(*this == other);
}

int64_t OpenSSLCertificate::CertificateExpirationTime() const {
  ASN1_TIME* expire_time = X509_get_notAfter(x509_);
  bool long_format;
  if (expire_time->type == V_ASN1_UTCTIME) {
    long_format = false;
  } else if (expire_time->type == V_ASN1_GENERALIZEDTIME) {
    long_format = true;
  } else {
    return -1;
  }
  return ASN1TimeToSec(expire_time->data, expire_time->length, long_format);
}

}  // namespace rtc
