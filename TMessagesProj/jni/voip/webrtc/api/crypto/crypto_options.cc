/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/crypto/crypto_options.h"

#include "rtc_base/ssl_stream_adapter.h"

namespace webrtc {

CryptoOptions::CryptoOptions() {}

CryptoOptions::CryptoOptions(const CryptoOptions& other) {
  srtp = other.srtp;
  sframe = other.sframe;
}

CryptoOptions::~CryptoOptions() {}

// static
CryptoOptions CryptoOptions::NoGcm() {
  CryptoOptions options;
  options.srtp.enable_gcm_crypto_suites = false;
  return options;
}

std::vector<int> CryptoOptions::GetSupportedDtlsSrtpCryptoSuites() const {
  std::vector<int> crypto_suites;
  // Note: SRTP_AES128_CM_SHA1_80 is what is required to be supported (by
  // draft-ietf-rtcweb-security-arch), but SRTP_AES128_CM_SHA1_32 is allowed as
  // well, and saves a few bytes per packet if it ends up selected.
  // As the cipher suite is potentially insecure, it will only be used if
  // enabled by both peers.
  if (srtp.enable_aes128_sha1_32_crypto_cipher) {
    crypto_suites.push_back(rtc::SRTP_AES128_CM_SHA1_32);
  }
  if (srtp.enable_aes128_sha1_80_crypto_cipher) {
    crypto_suites.push_back(rtc::SRTP_AES128_CM_SHA1_80);
  }

  // Note: GCM cipher suites are not the top choice since they increase the
  // packet size. In order to negotiate them the other side must not support
  // SRTP_AES128_CM_SHA1_80.
  if (srtp.enable_gcm_crypto_suites) {
    crypto_suites.push_back(rtc::SRTP_AEAD_AES_256_GCM);
    crypto_suites.push_back(rtc::SRTP_AEAD_AES_128_GCM);
  }
  RTC_CHECK(!crypto_suites.empty());
  return crypto_suites;
}

bool CryptoOptions::operator==(const CryptoOptions& other) const {
  struct data_being_tested_for_equality {
    struct Srtp {
      bool enable_gcm_crypto_suites;
      bool enable_aes128_sha1_32_crypto_cipher;
      bool enable_aes128_sha1_80_crypto_cipher;
      bool enable_encrypted_rtp_header_extensions;
    } srtp;
    struct SFrame {
      bool require_frame_encryption;
    } sframe;
  };
  static_assert(sizeof(data_being_tested_for_equality) == sizeof(*this),
                "Did you add something to CryptoOptions and forget to "
                "update operator==?");

  return srtp.enable_gcm_crypto_suites == other.srtp.enable_gcm_crypto_suites &&
         srtp.enable_aes128_sha1_32_crypto_cipher ==
             other.srtp.enable_aes128_sha1_32_crypto_cipher &&
         srtp.enable_aes128_sha1_80_crypto_cipher ==
             other.srtp.enable_aes128_sha1_80_crypto_cipher &&
         srtp.enable_encrypted_rtp_header_extensions ==
             other.srtp.enable_encrypted_rtp_header_extensions &&
         sframe.require_frame_encryption ==
             other.sframe.require_frame_encryption;
}

bool CryptoOptions::operator!=(const CryptoOptions& other) const {
  return !(*this == other);
}

}  // namespace webrtc
