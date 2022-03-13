/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/ssl_stream_adapter.h"

#include "absl/memory/memory.h"
#include "rtc_base/openssl_stream_adapter.h"

///////////////////////////////////////////////////////////////////////////////

namespace rtc {

// TODO(guoweis): Move this to SDP layer and use int form internally.
// webrtc:5043.
const char kCsAesCm128HmacSha1_80[] = "AES_CM_128_HMAC_SHA1_80";
const char kCsAesCm128HmacSha1_32[] = "AES_CM_128_HMAC_SHA1_32";
const char kCsAeadAes128Gcm[] = "AEAD_AES_128_GCM";
const char kCsAeadAes256Gcm[] = "AEAD_AES_256_GCM";

std::string SrtpCryptoSuiteToName(int crypto_suite) {
  switch (crypto_suite) {
    case kSrtpAes128CmSha1_32:
      return kCsAesCm128HmacSha1_32;
    case kSrtpAes128CmSha1_80:
      return kCsAesCm128HmacSha1_80;
    case kSrtpAeadAes128Gcm:
      return kCsAeadAes128Gcm;
    case kSrtpAeadAes256Gcm:
      return kCsAeadAes256Gcm;
    default:
      return std::string();
  }
}

int SrtpCryptoSuiteFromName(const std::string& crypto_suite) {
  if (crypto_suite == kCsAesCm128HmacSha1_32)
    return kSrtpAes128CmSha1_32;
  if (crypto_suite == kCsAesCm128HmacSha1_80)
    return kSrtpAes128CmSha1_80;
  if (crypto_suite == kCsAeadAes128Gcm)
    return kSrtpAeadAes128Gcm;
  if (crypto_suite == kCsAeadAes256Gcm)
    return kSrtpAeadAes256Gcm;
  return kSrtpInvalidCryptoSuite;
}

bool GetSrtpKeyAndSaltLengths(int crypto_suite,
                              int* key_length,
                              int* salt_length) {
  switch (crypto_suite) {
    case kSrtpAes128CmSha1_32:
    case kSrtpAes128CmSha1_80:
      // SRTP_AES128_CM_HMAC_SHA1_32 and SRTP_AES128_CM_HMAC_SHA1_80 are defined
      // in RFC 5764 to use a 128 bits key and 112 bits salt for the cipher.
      *key_length = 16;
      *salt_length = 14;
      break;
    case kSrtpAeadAes128Gcm:
      // kSrtpAeadAes128Gcm is defined in RFC 7714 to use a 128 bits key and
      // a 96 bits salt for the cipher.
      *key_length = 16;
      *salt_length = 12;
      break;
    case kSrtpAeadAes256Gcm:
      // kSrtpAeadAes256Gcm is defined in RFC 7714 to use a 256 bits key and
      // a 96 bits salt for the cipher.
      *key_length = 32;
      *salt_length = 12;
      break;
    default:
      return false;
  }
  return true;
}

bool IsGcmCryptoSuite(int crypto_suite) {
  return (crypto_suite == kSrtpAeadAes256Gcm ||
          crypto_suite == kSrtpAeadAes128Gcm);
}

bool IsGcmCryptoSuiteName(const std::string& crypto_suite) {
  return (crypto_suite == kCsAeadAes256Gcm || crypto_suite == kCsAeadAes128Gcm);
}

std::unique_ptr<SSLStreamAdapter> SSLStreamAdapter::Create(
    std::unique_ptr<StreamInterface> stream) {
  return std::make_unique<OpenSSLStreamAdapter>(std::move(stream));
}

bool SSLStreamAdapter::GetSslCipherSuite(int* cipher_suite) {
  return false;
}

bool SSLStreamAdapter::ExportKeyingMaterial(const std::string& label,
                                            const uint8_t* context,
                                            size_t context_len,
                                            bool use_context,
                                            uint8_t* result,
                                            size_t result_len) {
  return false;  // Default is unsupported
}

bool SSLStreamAdapter::SetDtlsSrtpCryptoSuites(
    const std::vector<int>& crypto_suites) {
  return false;
}

bool SSLStreamAdapter::GetDtlsSrtpCryptoSuite(int* crypto_suite) {
  return false;
}

bool SSLStreamAdapter::IsBoringSsl() {
  return OpenSSLStreamAdapter::IsBoringSsl();
}
bool SSLStreamAdapter::IsAcceptableCipher(int cipher, KeyType key_type) {
  return OpenSSLStreamAdapter::IsAcceptableCipher(cipher, key_type);
}
bool SSLStreamAdapter::IsAcceptableCipher(const std::string& cipher,
                                          KeyType key_type) {
  return OpenSSLStreamAdapter::IsAcceptableCipher(cipher, key_type);
}
std::string SSLStreamAdapter::SslCipherSuiteToName(int cipher_suite) {
  return OpenSSLStreamAdapter::SslCipherSuiteToName(cipher_suite);
}

///////////////////////////////////////////////////////////////////////////////
// Test only settings
///////////////////////////////////////////////////////////////////////////////

void SSLStreamAdapter::EnableTimeCallbackForTesting() {
  OpenSSLStreamAdapter::EnableTimeCallbackForTesting();
}

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
