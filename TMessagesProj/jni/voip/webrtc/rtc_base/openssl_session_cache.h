/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_OPENSSL_SESSION_CACHE_H_
#define RTC_BASE_OPENSSL_SESSION_CACHE_H_

#include <openssl/ossl_typ.h>

#include <map>
#include <string>

#include "rtc_base/constructor_magic.h"
#include "rtc_base/ssl_stream_adapter.h"

#ifndef OPENSSL_IS_BORINGSSL
typedef struct ssl_session_st SSL_SESSION;
#endif

namespace rtc {

// The OpenSSLSessionCache maps hostnames to SSL_SESSIONS. This cache is
// owned by the OpenSSLAdapterFactory and is passed down to each OpenSSLAdapter
// created with the factory.
class OpenSSLSessionCache final {
 public:
  // Creates a new OpenSSLSessionCache using the provided the SSL_CTX and
  // the ssl_mode. The SSL_CTX will be up_refed. ssl_ctx cannot be nullptr,
  // the constructor immediately dchecks this.
  OpenSSLSessionCache(SSLMode ssl_mode, SSL_CTX* ssl_ctx);
  // Frees the cached SSL_SESSIONS and then frees the SSL_CTX.
  ~OpenSSLSessionCache();
  // Looks up a session by hostname. The returned SSL_SESSION is not up_refed.
  SSL_SESSION* LookupSession(const std::string& hostname) const;
  // Adds a session to the cache, and up_refs it. Any existing session with the
  // same hostname is replaced.
  void AddSession(const std::string& hostname, SSL_SESSION* session);
  // Returns the true underlying SSL Context that holds these cached sessions.
  SSL_CTX* GetSSLContext() const;
  // The SSL Mode tht the OpenSSLSessionCache was constructed with. This cannot
  // be changed after launch.
  SSLMode GetSSLMode() const;

 private:
  // Holds the SSL Mode that the OpenSSLCache was initialized with. This is
  // immutable after creation and cannot change.
  const SSLMode ssl_mode_;
  /// SSL Context for all shared cached sessions. This SSL_CTX is initialized
  //  with SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_CLIENT); Meaning
  //  all client sessions will be added to the cache internal to the context.
  SSL_CTX* ssl_ctx_ = nullptr;
  // Map of hostnames to SSL_SESSIONs; holds references to the SSL_SESSIONs,
  // which are cleaned up when the factory is destroyed.
  // TODO(juberti): Add LRU eviction to keep the cache from growing forever.
  std::map<std::string, SSL_SESSION*> sessions_;
  // The cache should never be copied or assigned directly.
  RTC_DISALLOW_COPY_AND_ASSIGN(OpenSSLSessionCache);
};

}  // namespace rtc

#endif  // RTC_BASE_OPENSSL_SESSION_CACHE_H_
