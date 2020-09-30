/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/openssl_session_cache.h"

#include "rtc_base/checks.h"
#include "rtc_base/openssl.h"

namespace rtc {

OpenSSLSessionCache::OpenSSLSessionCache(SSLMode ssl_mode, SSL_CTX* ssl_ctx)
    : ssl_mode_(ssl_mode), ssl_ctx_(ssl_ctx) {
  // It is invalid to pass in a null context.
  RTC_DCHECK(ssl_ctx != nullptr);
  SSL_CTX_up_ref(ssl_ctx);
}

OpenSSLSessionCache::~OpenSSLSessionCache() {
  for (const auto& it : sessions_) {
    SSL_SESSION_free(it.second);
  }
  SSL_CTX_free(ssl_ctx_);
}

SSL_SESSION* OpenSSLSessionCache::LookupSession(
    const std::string& hostname) const {
  auto it = sessions_.find(hostname);
  return (it != sessions_.end()) ? it->second : nullptr;
}

void OpenSSLSessionCache::AddSession(const std::string& hostname,
                                     SSL_SESSION* new_session) {
  SSL_SESSION* old_session = LookupSession(hostname);
  SSL_SESSION_free(old_session);
  sessions_[hostname] = new_session;
}

SSL_CTX* OpenSSLSessionCache::GetSSLContext() const {
  return ssl_ctx_;
}

SSLMode OpenSSLSessionCache::GetSSLMode() const {
  return ssl_mode_;
}

}  // namespace rtc
