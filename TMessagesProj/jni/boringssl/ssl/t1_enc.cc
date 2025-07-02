// Copyright 1995-2016 The OpenSSL Project Authors. All Rights Reserved.
// Copyright 2005 Nokia. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <openssl/ssl.h>

#include <assert.h>
#include <string.h>

#include <string_view>
#include <utility>

#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/md5.h>
#include <openssl/mem.h>
#include <openssl/nid.h>
#include <openssl/rand.h>

#include "../crypto/fipsmodule/tls/internal.h"
#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

bool tls1_prf(const EVP_MD *digest, Span<uint8_t> out,
              Span<const uint8_t> secret, std::string_view label,
              Span<const uint8_t> seed1, Span<const uint8_t> seed2) {
  return 1 == CRYPTO_tls1_prf(digest, out.data(), out.size(), secret.data(),
                              secret.size(), label.data(), label.size(),
                              seed1.data(), seed1.size(), seed2.data(),
                              seed2.size());
}

static bool get_key_block_lengths(const SSL *ssl, size_t *out_mac_secret_len,
                                  size_t *out_key_len, size_t *out_iv_len,
                                  const SSL_CIPHER *cipher) {
  const EVP_AEAD *aead = NULL;
  if (!ssl_cipher_get_evp_aead(&aead, out_mac_secret_len, out_iv_len, cipher,
                               ssl_protocol_version(ssl))) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_CIPHER_OR_HASH_UNAVAILABLE);
    return false;
  }

  *out_key_len = EVP_AEAD_key_length(aead);
  if (*out_mac_secret_len > 0) {
    // For "stateful" AEADs (i.e. compatibility with pre-AEAD cipher suites) the
    // key length reported by |EVP_AEAD_key_length| will include the MAC key
    // bytes and initial implicit IV.
    if (*out_key_len < *out_mac_secret_len + *out_iv_len) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
      return false;
    }
    *out_key_len -= *out_mac_secret_len + *out_iv_len;
  }

  return true;
}

static bool generate_key_block(const SSL *ssl, Span<uint8_t> out,
                               const SSL_SESSION *session) {
  const EVP_MD *digest = ssl_session_get_digest(session);
  // Note this function assumes that |session|'s key material corresponds to
  // |ssl->s3->client_random| and |ssl->s3->server_random|.
  return tls1_prf(digest, out, session->secret, "key expansion",
                  ssl->s3->server_random, ssl->s3->client_random);
}

bool tls1_configure_aead(SSL *ssl, evp_aead_direction_t direction,
                         Array<uint8_t> *key_block_cache,
                         const SSL_SESSION *session,
                         Span<const uint8_t> iv_override) {
  size_t mac_secret_len, key_len, iv_len;
  if (!get_key_block_lengths(ssl, &mac_secret_len, &key_len, &iv_len,
                             session->cipher)) {
    return false;
  }

  // Ensure that |key_block_cache| is set up.
  const size_t key_block_size = 2 * (mac_secret_len + key_len + iv_len);
  if (key_block_cache->empty()) {
    if (!key_block_cache->InitForOverwrite(key_block_size) ||
        !generate_key_block(ssl, Span(*key_block_cache), session)) {
      return false;
    }
  }
  assert(key_block_cache->size() == key_block_size);

  Span<const uint8_t> key_block = *key_block_cache;
  Span<const uint8_t> mac_secret, key, iv;
  if (direction == (ssl->server ? evp_aead_open : evp_aead_seal)) {
    // Use the client write (server read) keys.
    mac_secret = key_block.subspan(0, mac_secret_len);
    key = key_block.subspan(2 * mac_secret_len, key_len);
    iv = key_block.subspan(2 * mac_secret_len + 2 * key_len, iv_len);
  } else {
    // Use the server write (client read) keys.
    mac_secret = key_block.subspan(mac_secret_len, mac_secret_len);
    key = key_block.subspan(2 * mac_secret_len + key_len, key_len);
    iv = key_block.subspan(2 * mac_secret_len + 2 * key_len + iv_len, iv_len);
  }

  if (!iv_override.empty()) {
    if (iv_override.size() != iv_len) {
      return false;
    }
    iv = iv_override;
  }

  UniquePtr<SSLAEADContext> aead_ctx = SSLAEADContext::Create(
      direction, ssl->s3->version, session->cipher, key, mac_secret, iv);
  if (!aead_ctx) {
    return false;
  }

  if (direction == evp_aead_open) {
    return ssl->method->set_read_state(ssl, ssl_encryption_application,
                                       std::move(aead_ctx),
                                       /*traffic_secret=*/{});
  }

  return ssl->method->set_write_state(ssl, ssl_encryption_application,
                                      std::move(aead_ctx),
                                      /*traffic_secret=*/{});
}

bool tls1_change_cipher_state(SSL_HANDSHAKE *hs,
                              evp_aead_direction_t direction) {
  return tls1_configure_aead(hs->ssl, direction, &hs->key_block,
                             ssl_handshake_session(hs), {});
}

bool tls1_generate_master_secret(SSL_HANDSHAKE *hs, Span<uint8_t> out,
                                 Span<const uint8_t> premaster) {
  BSSL_CHECK(out.size() == SSL3_MASTER_SECRET_SIZE);

  const SSL *ssl = hs->ssl;
  if (hs->extended_master_secret) {
    uint8_t digests[EVP_MAX_MD_SIZE];
    size_t digests_len;
    if (!hs->transcript.GetHash(digests, &digests_len) ||
        !tls1_prf(hs->transcript.Digest(), out, premaster,
                  "extended master secret", Span(digests, digests_len), {})) {
      return false;
    }
  } else {
    if (!tls1_prf(hs->transcript.Digest(), out, premaster, "master secret",
                  ssl->s3->client_random, ssl->s3->server_random)) {
      return false;
    }
  }

  return true;
}

BSSL_NAMESPACE_END

using namespace bssl;

size_t SSL_get_key_block_len(const SSL *ssl) {
  // See |SSL_generate_key_block|.
  if (SSL_in_init(ssl) || ssl_protocol_version(ssl) > TLS1_2_VERSION) {
    return 0;
  }

  size_t mac_secret_len, key_len, fixed_iv_len;
  if (!get_key_block_lengths(ssl, &mac_secret_len, &key_len, &fixed_iv_len,
                             SSL_get_current_cipher(ssl))) {
    ERR_clear_error();
    return 0;
  }

  return 2 * (mac_secret_len + key_len + fixed_iv_len);
}

int SSL_generate_key_block(const SSL *ssl, uint8_t *out, size_t out_len) {
  // Which cipher state to use is ambiguous during a handshake. In particular,
  // there are points where read and write states are from different epochs.
  // During a handshake, before ChangeCipherSpec, the encryption states may not
  // match |ssl->s3->client_random| and |ssl->s3->server_random|.
  if (SSL_in_init(ssl) || ssl_protocol_version(ssl) > TLS1_2_VERSION) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  return generate_key_block(ssl, Span(out, out_len), SSL_get_session(ssl));
}

int SSL_export_keying_material(const SSL *ssl, uint8_t *out, size_t out_len,
                               const char *label, size_t label_len,
                               const uint8_t *context, size_t context_len,
                               int use_context) {
  auto out_span = Span(out, out_len);
  std::string_view label_sv(label, label_len);
  // In TLS 1.3, the exporter may be used whenever the secret has been derived.
  if (ssl->s3->version != 0 && ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    if (ssl->s3->exporter_secret.empty()) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_HANDSHAKE_NOT_COMPLETE);
      return 0;
    }
    if (!use_context) {
      context = nullptr;
      context_len = 0;
    }
    return tls13_export_keying_material(ssl, out_span, ssl->s3->exporter_secret,
                                        label_sv, Span(context, context_len));
  }

  // Exporters may be used in False Start, where the handshake has progressed
  // enough. Otherwise, they may not be used during a handshake.
  if (SSL_in_init(ssl) && !SSL_in_false_start(ssl)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_HANDSHAKE_NOT_COMPLETE);
    return 0;
  }

  size_t seed_len = 2 * SSL3_RANDOM_SIZE;
  if (use_context) {
    if (context_len >= 1u << 16) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_OVERFLOW);
      return 0;
    }
    seed_len += 2 + context_len;
  }
  Array<uint8_t> seed;
  if (!seed.InitForOverwrite(seed_len)) {
    return 0;
  }

  OPENSSL_memcpy(seed.data(), ssl->s3->client_random, SSL3_RANDOM_SIZE);
  OPENSSL_memcpy(seed.data() + SSL3_RANDOM_SIZE, ssl->s3->server_random,
                 SSL3_RANDOM_SIZE);
  if (use_context) {
    seed[2 * SSL3_RANDOM_SIZE] = static_cast<uint8_t>(context_len >> 8);
    seed[2 * SSL3_RANDOM_SIZE + 1] = static_cast<uint8_t>(context_len);
    OPENSSL_memcpy(seed.data() + 2 * SSL3_RANDOM_SIZE + 2, context,
                   context_len);
  }

  const SSL_SESSION *session = SSL_get_session(ssl);
  const EVP_MD *digest = ssl_session_get_digest(session);
  return tls1_prf(digest, out_span, session->secret, label_sv, seed, {});
}
