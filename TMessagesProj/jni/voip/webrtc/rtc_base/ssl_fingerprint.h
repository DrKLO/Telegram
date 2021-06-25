/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SSL_FINGERPRINT_H_
#define RTC_BASE_SSL_FINGERPRINT_H_

#include <stddef.h>
#include <stdint.h>
#include <string>

#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/system/rtc_export.h"

namespace rtc {

class RTCCertificate;
class SSLCertificate;
class SSLIdentity;

struct RTC_EXPORT SSLFingerprint {
  // TODO(steveanton): Remove once downstream projects have moved off of this.
  static SSLFingerprint* Create(const std::string& algorithm,
                                const rtc::SSLIdentity* identity);
  // TODO(steveanton): Rename to Create once projects have migrated.
  static std::unique_ptr<SSLFingerprint> CreateUnique(
      const std::string& algorithm,
      const rtc::SSLIdentity& identity);

  static std::unique_ptr<SSLFingerprint> Create(
      const std::string& algorithm,
      const rtc::SSLCertificate& cert);

  // TODO(steveanton): Remove once downstream projects have moved off of this.
  static SSLFingerprint* CreateFromRfc4572(const std::string& algorithm,
                                           const std::string& fingerprint);
  // TODO(steveanton): Rename to CreateFromRfc4572 once projects have migrated.
  static std::unique_ptr<SSLFingerprint> CreateUniqueFromRfc4572(
      const std::string& algorithm,
      const std::string& fingerprint);

  // Creates a fingerprint from a certificate, using the same digest algorithm
  // as the certificate's signature.
  static std::unique_ptr<SSLFingerprint> CreateFromCertificate(
      const RTCCertificate& cert);

  SSLFingerprint(const std::string& algorithm,
                 ArrayView<const uint8_t> digest_view);
  // TODO(steveanton): Remove once downstream projects have moved off of this.
  SSLFingerprint(const std::string& algorithm,
                 const uint8_t* digest_in,
                 size_t digest_len);

  SSLFingerprint(const SSLFingerprint& from) = default;
  SSLFingerprint& operator=(const SSLFingerprint& from) = default;

  bool operator==(const SSLFingerprint& other) const;

  std::string GetRfc4572Fingerprint() const;

  std::string ToString() const;

  std::string algorithm;
  rtc::CopyOnWriteBuffer digest;
};

}  // namespace rtc

#endif  // RTC_BASE_SSL_FINGERPRINT_H_
