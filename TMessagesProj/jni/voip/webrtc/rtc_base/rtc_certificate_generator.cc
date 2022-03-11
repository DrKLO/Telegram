/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/rtc_certificate_generator.h"

#include <time.h>

#include <algorithm>
#include <memory>
#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/ssl_identity.h"

namespace rtc {

namespace {

// A certificates' subject and issuer name.
const char kIdentityName[] = "WebRTC";
const uint64_t kYearInSeconds = 365 * 24 * 60 * 60;

}  // namespace

// static
scoped_refptr<RTCCertificate> RTCCertificateGenerator::GenerateCertificate(
    const KeyParams& key_params,
    const absl::optional<uint64_t>& expires_ms) {
  if (!key_params.IsValid()) {
    return nullptr;
  }

  std::unique_ptr<SSLIdentity> identity;
  if (!expires_ms) {
    identity = SSLIdentity::Create(kIdentityName, key_params);
  } else {
    uint64_t expires_s = *expires_ms / 1000;
    // Limit the expiration time to something reasonable (a year). This was
    // somewhat arbitrarily chosen. It also ensures that the value is not too
    // large for the unspecified `time_t`.
    expires_s = std::min(expires_s, kYearInSeconds);
    // TODO(torbjorng): Stop using `time_t`, its type is unspecified. It it safe
    // to assume it can hold up to a year's worth of seconds (and more), but
    // `SSLIdentity::Create` should stop relying on `time_t`.
    // See bugs.webrtc.org/5720.
    time_t cert_lifetime_s = static_cast<time_t>(expires_s);
    identity = SSLIdentity::Create(kIdentityName, key_params, cert_lifetime_s);
  }
  if (!identity) {
    return nullptr;
  }
  return RTCCertificate::Create(std::move(identity));
}

RTCCertificateGenerator::RTCCertificateGenerator(Thread* signaling_thread,
                                                 Thread* worker_thread)
    : signaling_thread_(signaling_thread), worker_thread_(worker_thread) {
  RTC_DCHECK(signaling_thread_);
  RTC_DCHECK(worker_thread_);
}

void RTCCertificateGenerator::GenerateCertificateAsync(
    const KeyParams& key_params,
    const absl::optional<uint64_t>& expires_ms,
    const scoped_refptr<RTCCertificateGeneratorCallback>& callback) {
  RTC_DCHECK(signaling_thread_->IsCurrent());
  RTC_DCHECK(callback);

  // Create a new `RTCCertificateGenerationTask` for this generation request. It
  // is reference counted and referenced by the message data, ensuring it lives
  // until the task has completed (independent of `RTCCertificateGenerator`).
  worker_thread_->PostTask([key_params, expires_ms,
                            signaling_thread = signaling_thread_,
                            cb = callback]() {
    scoped_refptr<RTCCertificate> certificate =
        RTCCertificateGenerator::GenerateCertificate(key_params, expires_ms);
    signaling_thread->PostTask(
        [cert = std::move(certificate), cb = std::move(cb)]() {
          cert ? cb->OnSuccess(cert) : cb->OnFailure();
        });
  });
}

}  // namespace rtc
