/* Copyright (c) 2016, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <openssl/ssl.h>

#include <assert.h>
#include <string.h>

#include <algorithm>
#include <utility>

#include <openssl/aead.h>
#include <openssl/bytestring.h>
#include <openssl/digest.h>
#include <openssl/hkdf.h>
#include <openssl/hmac.h>
#include <openssl/mem.h>

#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

static bool init_key_schedule(SSL_HANDSHAKE *hs, uint16_t version,
                              const SSL_CIPHER *cipher) {
  if (!hs->transcript.InitHash(version, cipher)) {
    return false;
  }

  // Initialize the secret to the zero key.
  hs->ResizeSecrets(hs->transcript.DigestLen());
  OPENSSL_memset(hs->secret().data(), 0, hs->secret().size());

  return true;
}

static bool hkdf_extract_to_secret(SSL_HANDSHAKE *hs, Span<const uint8_t> in) {
  size_t len;
  if (!HKDF_extract(hs->secret().data(), &len, hs->transcript.Digest(),
                    in.data(), in.size(), hs->secret().data(),
                    hs->secret().size())) {
    return false;
  }
  assert(len == hs->secret().size());
  return true;
}

bool tls13_init_key_schedule(SSL_HANDSHAKE *hs, Span<const uint8_t> psk) {
  if (!init_key_schedule(hs, ssl_protocol_version(hs->ssl), hs->new_cipher)) {
    return false;
  }

  hs->transcript.FreeBuffer();
  return hkdf_extract_to_secret(hs, psk);
}

bool tls13_init_early_key_schedule(SSL_HANDSHAKE *hs, Span<const uint8_t> psk) {
  SSL *const ssl = hs->ssl;
  return init_key_schedule(hs, ssl_session_protocol_version(ssl->session.get()),
                           ssl->session->cipher) &&
         hkdf_extract_to_secret(hs, psk);
}

static Span<const char> label_to_span(const char *label) {
  return MakeConstSpan(label, strlen(label));
}

static bool hkdf_expand_label(Span<uint8_t> out, const EVP_MD *digest,
                              Span<const uint8_t> secret,
                              Span<const char> label,
                              Span<const uint8_t> hash) {
  Span<const char> protocol_label = label_to_span("tls13 ");
  ScopedCBB cbb;
  CBB child;
  Array<uint8_t> hkdf_label;
  if (!CBB_init(cbb.get(), 2 + 1 + protocol_label.size() + label.size() + 1 +
                               hash.size()) ||
      !CBB_add_u16(cbb.get(), out.size()) ||
      !CBB_add_u8_length_prefixed(cbb.get(), &child) ||
      !CBB_add_bytes(&child,
                     reinterpret_cast<const uint8_t *>(protocol_label.data()),
                     protocol_label.size()) ||
      !CBB_add_bytes(&child, reinterpret_cast<const uint8_t *>(label.data()),
                     label.size()) ||
      !CBB_add_u8_length_prefixed(cbb.get(), &child) ||
      !CBB_add_bytes(&child, hash.data(), hash.size()) ||
      !CBBFinishArray(cbb.get(), &hkdf_label)) {
    return false;
  }

  return HKDF_expand(out.data(), out.size(), digest, secret.data(),
                     secret.size(), hkdf_label.data(), hkdf_label.size());
}

static const char kTLS13LabelDerived[] = "derived";

bool tls13_advance_key_schedule(SSL_HANDSHAKE *hs, Span<const uint8_t> in) {
  uint8_t derive_context[EVP_MAX_MD_SIZE];
  unsigned derive_context_len;
  return EVP_Digest(nullptr, 0, derive_context, &derive_context_len,
                    hs->transcript.Digest(), nullptr) &&
         hkdf_expand_label(hs->secret(), hs->transcript.Digest(), hs->secret(),
                           label_to_span(kTLS13LabelDerived),
                           MakeConstSpan(derive_context, derive_context_len)) &&
         hkdf_extract_to_secret(hs, in);
}

// derive_secret derives a secret of length |out.size()| and writes the result
// in |out| with the given label, the current base secret, and the most
// recently-saved handshake context. It returns true on success and false on
// error.
static bool derive_secret(SSL_HANDSHAKE *hs, Span<uint8_t> out,
                          Span<const char> label) {
  uint8_t context_hash[EVP_MAX_MD_SIZE];
  size_t context_hash_len;
  if (!hs->transcript.GetHash(context_hash, &context_hash_len)) {
    return false;
  }

  return hkdf_expand_label(out, hs->transcript.Digest(), hs->secret(), label,
                           MakeConstSpan(context_hash, context_hash_len));
}

bool tls13_set_traffic_key(SSL *ssl, enum ssl_encryption_level_t level,
                           enum evp_aead_direction_t direction,
                           Span<const uint8_t> traffic_secret) {
  const SSL_SESSION *session = SSL_get_session(ssl);
  uint16_t version = ssl_session_protocol_version(session);

  UniquePtr<SSLAEADContext> traffic_aead;
  if (ssl->quic_method == nullptr) {
    // Look up cipher suite properties.
    const EVP_AEAD *aead;
    size_t discard;
    if (!ssl_cipher_get_evp_aead(&aead, &discard, &discard, session->cipher,
                                 version, SSL_is_dtls(ssl))) {
      return false;
    }

    const EVP_MD *digest = ssl_session_get_digest(session);

    // Derive the key.
    size_t key_len = EVP_AEAD_key_length(aead);
    uint8_t key_buf[EVP_AEAD_MAX_KEY_LENGTH];
    auto key = MakeSpan(key_buf, key_len);
    if (!hkdf_expand_label(key, digest, traffic_secret, label_to_span("key"),
                           {})) {
      return false;
    }

    // Derive the IV.
    size_t iv_len = EVP_AEAD_nonce_length(aead);
    uint8_t iv_buf[EVP_AEAD_MAX_NONCE_LENGTH];
    auto iv = MakeSpan(iv_buf, iv_len);
    if (!hkdf_expand_label(iv, digest, traffic_secret, label_to_span("iv"),
                           {})) {
      return false;
    }


    traffic_aead = SSLAEADContext::Create(direction, session->ssl_version,
                                          SSL_is_dtls(ssl), session->cipher,
                                          key, Span<const uint8_t>(), iv);
  } else {
    // Install a placeholder SSLAEADContext so that SSL accessors work. The
    // encryption itself will be handled by the SSL_QUIC_METHOD.
    traffic_aead =
        SSLAEADContext::CreatePlaceholderForQUIC(version, session->cipher);
    // QUIC never installs early data keys at the TLS layer.
    assert(level != ssl_encryption_early_data);
  }

  if (!traffic_aead) {
    return false;
  }

  if (direction == evp_aead_open) {
    if (!ssl->method->set_read_state(ssl, std::move(traffic_aead))) {
      return false;
    }
  } else {
    if (!ssl->method->set_write_state(ssl, std::move(traffic_aead))) {
      return false;
    }
  }

  // Save the traffic secret.
  if (traffic_secret.size() >
          OPENSSL_ARRAY_SIZE(ssl->s3->read_traffic_secret) ||
      traffic_secret.size() >
          OPENSSL_ARRAY_SIZE(ssl->s3->write_traffic_secret)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return false;
  }
  if (direction == evp_aead_open) {
    OPENSSL_memmove(ssl->s3->read_traffic_secret, traffic_secret.data(),
                    traffic_secret.size());
    ssl->s3->read_traffic_secret_len = traffic_secret.size();
    ssl->s3->read_level = level;
  } else {
    OPENSSL_memmove(ssl->s3->write_traffic_secret, traffic_secret.data(),
                    traffic_secret.size());
    ssl->s3->write_traffic_secret_len = traffic_secret.size();
    ssl->s3->write_level = level;
  }

  return true;
}


static const char kTLS13LabelExporter[] = "exp master";

static const char kTLS13LabelClientEarlyTraffic[] = "c e traffic";
static const char kTLS13LabelClientHandshakeTraffic[] = "c hs traffic";
static const char kTLS13LabelServerHandshakeTraffic[] = "s hs traffic";
static const char kTLS13LabelClientApplicationTraffic[] = "c ap traffic";
static const char kTLS13LabelServerApplicationTraffic[] = "s ap traffic";

bool tls13_derive_early_secret(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  if (!derive_secret(hs, hs->early_traffic_secret(),
                     label_to_span(kTLS13LabelClientEarlyTraffic)) ||
      !ssl_log_secret(ssl, "CLIENT_EARLY_TRAFFIC_SECRET",
                      hs->early_traffic_secret())) {
    return false;
  }
  return true;
}

bool tls13_set_early_secret_for_quic(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  if (ssl->quic_method == nullptr) {
    return true;
  }
  if (ssl->server) {
    if (!ssl->quic_method->set_encryption_secrets(
            ssl, ssl_encryption_early_data, hs->early_traffic_secret().data(),
            /*write_secret=*/nullptr, hs->early_traffic_secret().size())) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_QUIC_INTERNAL_ERROR);
      return false;
    }
  } else {
    if (!ssl->quic_method->set_encryption_secrets(
            ssl, ssl_encryption_early_data, /*read_secret=*/nullptr,
            hs->early_traffic_secret().data(),
            hs->early_traffic_secret().size())) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_QUIC_INTERNAL_ERROR);
      return false;
    }
  }
  return true;
}

static bool set_quic_secrets(SSL_HANDSHAKE *hs, ssl_encryption_level_t level,
                             Span<const uint8_t> client_write_secret,
                             Span<const uint8_t> server_write_secret) {
  SSL *const ssl = hs->ssl;
  assert(client_write_secret.size() == server_write_secret.size());
  if (ssl->quic_method == nullptr) {
    return true;
  }
  if (!ssl->server) {
    std::swap(client_write_secret, server_write_secret);
  }
  return ssl->quic_method->set_encryption_secrets(
      ssl, level,
      /*read_secret=*/client_write_secret.data(),
      /*write_secret=*/server_write_secret.data(), client_write_secret.size());
}

bool tls13_derive_handshake_secrets(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  if (!derive_secret(hs, hs->client_handshake_secret(),
                     label_to_span(kTLS13LabelClientHandshakeTraffic)) ||
      !ssl_log_secret(ssl, "CLIENT_HANDSHAKE_TRAFFIC_SECRET",
                      hs->client_handshake_secret()) ||
      !derive_secret(hs, hs->server_handshake_secret(),
                     label_to_span(kTLS13LabelServerHandshakeTraffic)) ||
      !ssl_log_secret(ssl, "SERVER_HANDSHAKE_TRAFFIC_SECRET",
                      hs->server_handshake_secret()) ||
      !set_quic_secrets(hs, ssl_encryption_handshake,
                        hs->client_handshake_secret(),
                        hs->server_handshake_secret())) {
    return false;
  }

  return true;
}

bool tls13_derive_application_secrets(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  ssl->s3->exporter_secret_len = hs->transcript.DigestLen();
  if (!derive_secret(hs, hs->client_traffic_secret_0(),
                     label_to_span(kTLS13LabelClientApplicationTraffic)) ||
      !ssl_log_secret(ssl, "CLIENT_TRAFFIC_SECRET_0",
                      hs->client_traffic_secret_0()) ||
      !derive_secret(hs, hs->server_traffic_secret_0(),
                     label_to_span(kTLS13LabelServerApplicationTraffic)) ||
      !ssl_log_secret(ssl, "SERVER_TRAFFIC_SECRET_0",
                      hs->server_traffic_secret_0()) ||
      !derive_secret(
          hs, MakeSpan(ssl->s3->exporter_secret, ssl->s3->exporter_secret_len),
          label_to_span(kTLS13LabelExporter)) ||
      !ssl_log_secret(ssl, "EXPORTER_SECRET",
                      MakeConstSpan(ssl->s3->exporter_secret,
                                    ssl->s3->exporter_secret_len)) ||
      !set_quic_secrets(hs, ssl_encryption_application,
                        hs->client_traffic_secret_0(),
                        hs->server_traffic_secret_0())) {
    return false;
  }

  return true;
}

static const char kTLS13LabelApplicationTraffic[] = "traffic upd";

bool tls13_rotate_traffic_key(SSL *ssl, enum evp_aead_direction_t direction) {
  Span<uint8_t> secret;
  if (direction == evp_aead_open) {
    secret = MakeSpan(ssl->s3->read_traffic_secret,
                      ssl->s3->read_traffic_secret_len);
  } else {
    secret = MakeSpan(ssl->s3->write_traffic_secret,
                      ssl->s3->write_traffic_secret_len);
  }

  const EVP_MD *digest = ssl_session_get_digest(SSL_get_session(ssl));
  return hkdf_expand_label(secret, digest, secret,
                           label_to_span(kTLS13LabelApplicationTraffic), {}) &&
         tls13_set_traffic_key(ssl, ssl_encryption_application, direction,
                               secret);
}

static const char kTLS13LabelResumption[] = "res master";

bool tls13_derive_resumption_secret(SSL_HANDSHAKE *hs) {
  if (hs->transcript.DigestLen() > SSL_MAX_MASTER_KEY_LENGTH) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return false;
  }
  hs->new_session->master_key_length = hs->transcript.DigestLen();
  return derive_secret(
      hs,
      MakeSpan(hs->new_session->master_key, hs->new_session->master_key_length),
      label_to_span(kTLS13LabelResumption));
}

static const char kTLS13LabelFinished[] = "finished";

// tls13_verify_data sets |out| to be the HMAC of |context| using a derived
// Finished key for both Finished messages and the PSK binder. |out| must have
// space available for |EVP_MAX_MD_SIZE| bytes.
static bool tls13_verify_data(uint8_t *out, size_t *out_len,
                              const EVP_MD *digest, uint16_t version,
                              Span<const uint8_t> secret,
                              Span<const uint8_t> context) {
  uint8_t key_buf[EVP_MAX_MD_SIZE];
  auto key = MakeSpan(key_buf, EVP_MD_size(digest));
  unsigned len;
  if (!hkdf_expand_label(key, digest, secret,
                         label_to_span(kTLS13LabelFinished), {}) ||
      HMAC(digest, key.data(), key.size(), context.data(), context.size(), out,
           &len) == nullptr) {
    return false;
  }
  *out_len = len;
  return true;
}

bool tls13_finished_mac(SSL_HANDSHAKE *hs, uint8_t *out, size_t *out_len,
                        bool is_server) {
  Span<const uint8_t> traffic_secret =
      is_server ? hs->server_handshake_secret() : hs->client_handshake_secret();

  uint8_t context_hash[EVP_MAX_MD_SIZE];
  size_t context_hash_len;
  if (!hs->transcript.GetHash(context_hash, &context_hash_len) ||
      !tls13_verify_data(out, out_len, hs->transcript.Digest(),
                         hs->ssl->version, traffic_secret,
                         MakeConstSpan(context_hash, context_hash_len))) {
    return 0;
  }
  return 1;
}

static const char kTLS13LabelResumptionPSK[] = "resumption";

bool tls13_derive_session_psk(SSL_SESSION *session, Span<const uint8_t> nonce) {
  const EVP_MD *digest = ssl_session_get_digest(session);
  // The session initially stores the resumption_master_secret, which we
  // override with the PSK.
  auto session_key = MakeSpan(session->master_key, session->master_key_length);
  return hkdf_expand_label(session_key, digest, session_key,
                           label_to_span(kTLS13LabelResumptionPSK), nonce);
}

static const char kTLS13LabelExportKeying[] = "exporter";

bool tls13_export_keying_material(SSL *ssl, Span<uint8_t> out,
                                  Span<const uint8_t> secret,
                                  Span<const char> label,
                                  Span<const uint8_t> context) {
  if (secret.empty()) {
    assert(0);
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return false;
  }

  const EVP_MD *digest = ssl_session_get_digest(SSL_get_session(ssl));

  uint8_t hash_buf[EVP_MAX_MD_SIZE];
  uint8_t export_context_buf[EVP_MAX_MD_SIZE];
  unsigned hash_len;
  unsigned export_context_len;
  if (!EVP_Digest(context.data(), context.size(), hash_buf, &hash_len, digest,
                  nullptr) ||
      !EVP_Digest(nullptr, 0, export_context_buf, &export_context_len, digest,
                  nullptr)) {
    return false;
  }

  auto hash = MakeConstSpan(hash_buf, hash_len);
  auto export_context = MakeConstSpan(export_context_buf, export_context_len);
  uint8_t derived_secret_buf[EVP_MAX_MD_SIZE];
  auto derived_secret = MakeSpan(derived_secret_buf, EVP_MD_size(digest));
  return hkdf_expand_label(derived_secret, digest, secret, label,
                           export_context) &&
         hkdf_expand_label(out, digest, derived_secret,
                           label_to_span(kTLS13LabelExportKeying), hash);
}

static const char kTLS13LabelPSKBinder[] = "res binder";

static bool tls13_psk_binder(uint8_t *out, size_t *out_len, uint16_t version,
                             const EVP_MD *digest, Span<const uint8_t> psk,
                             Span<const uint8_t> context) {
  uint8_t binder_context[EVP_MAX_MD_SIZE];
  unsigned binder_context_len;
  if (!EVP_Digest(NULL, 0, binder_context, &binder_context_len, digest, NULL)) {
    return false;
  }

  uint8_t early_secret[EVP_MAX_MD_SIZE] = {0};
  size_t early_secret_len;
  if (!HKDF_extract(early_secret, &early_secret_len, digest, psk.data(),
                    psk.size(), NULL, 0)) {
    return false;
  }

  uint8_t binder_key_buf[EVP_MAX_MD_SIZE] = {0};
  auto binder_key = MakeSpan(binder_key_buf, EVP_MD_size(digest));
  if (!hkdf_expand_label(binder_key, digest,
                         MakeConstSpan(early_secret, early_secret_len),
                         label_to_span(kTLS13LabelPSKBinder),
                         MakeConstSpan(binder_context, binder_context_len)) ||
      !tls13_verify_data(out, out_len, digest, version, binder_key, context)) {
    return false;
  }

  assert(*out_len == EVP_MD_size(digest));
  return true;
}

static bool hash_transcript_and_truncated_client_hello(
    SSL_HANDSHAKE *hs, uint8_t *out, size_t *out_len, const EVP_MD *digest,
    Span<const uint8_t> client_hello, size_t binders_len) {
  // Truncate the ClientHello.
  if (binders_len + 2 < binders_len || client_hello.size() < binders_len + 2) {
    return false;
  }
  client_hello = client_hello.subspan(0, client_hello.size() - binders_len - 2);

  ScopedEVP_MD_CTX ctx;
  unsigned len;
  if (!hs->transcript.CopyToHashContext(ctx.get(), digest) ||
      !EVP_DigestUpdate(ctx.get(), client_hello.data(), client_hello.size()) ||
      !EVP_DigestFinal_ex(ctx.get(), out, &len)) {
    return false;
  }

  *out_len = len;
  return true;
}

bool tls13_write_psk_binder(SSL_HANDSHAKE *hs, Span<uint8_t> msg) {
  SSL *const ssl = hs->ssl;
  const EVP_MD *digest = ssl_session_get_digest(ssl->session.get());
  size_t hash_len = EVP_MD_size(digest);

  ScopedEVP_MD_CTX ctx;
  uint8_t context[EVP_MAX_MD_SIZE];
  size_t context_len;
  uint8_t verify_data[EVP_MAX_MD_SIZE];
  size_t verify_data_len;
  if (!hash_transcript_and_truncated_client_hello(
          hs, context, &context_len, digest, msg,
          1 /* length prefix */ + hash_len) ||
      !tls13_psk_binder(verify_data, &verify_data_len,
                        ssl->session->ssl_version, digest,
                        MakeConstSpan(ssl->session->master_key,
                                      ssl->session->master_key_length),
                        MakeConstSpan(context, context_len)) ||
      verify_data_len != hash_len) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return false;
  }

  OPENSSL_memcpy(msg.data() + msg.size() - verify_data_len, verify_data,
                 verify_data_len);
  return true;
}

bool tls13_verify_psk_binder(SSL_HANDSHAKE *hs, SSL_SESSION *session,
                             const SSLMessage &msg, CBS *binders) {
  uint8_t context[EVP_MAX_MD_SIZE];
  size_t context_len;
  uint8_t verify_data[EVP_MAX_MD_SIZE];
  size_t verify_data_len;
  CBS binder;
  if (!hash_transcript_and_truncated_client_hello(hs, context, &context_len,
                                                  hs->transcript.Digest(),
                                                  msg.raw, CBS_len(binders)) ||
      !tls13_psk_binder(
          verify_data, &verify_data_len, hs->ssl->version,
          hs->transcript.Digest(),
          MakeConstSpan(session->master_key, session->master_key_length),
          MakeConstSpan(context, context_len)) ||
      // We only consider the first PSK, so compare against the first binder.
      !CBS_get_u8_length_prefixed(binders, &binder)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return false;
  }

  bool binder_ok =
      CBS_len(&binder) == verify_data_len &&
      CRYPTO_memcmp(CBS_data(&binder), verify_data, verify_data_len) == 0;
#if defined(BORINGSSL_UNSAFE_FUZZER_MODE)
  binder_ok = true;
#endif
  if (!binder_ok) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DIGEST_CHECK_FAILED);
    return false;
  }

  return true;
}

BSSL_NAMESPACE_END
