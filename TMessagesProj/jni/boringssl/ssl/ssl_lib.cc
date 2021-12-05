/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.]
 */
/* ====================================================================
 * Copyright (c) 1998-2007 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com).
 *
 */
/* ====================================================================
 * Copyright 2002 Sun Microsystems, Inc. ALL RIGHTS RESERVED.
 * ECC cipher suite support in OpenSSL originally developed by
 * SUN MICROSYSTEMS, INC., and contributed to the OpenSSL project.
 */
/* ====================================================================
 * Copyright 2005 Nokia. All rights reserved.
 *
 * The portions of the attached software ("Contribution") is developed by
 * Nokia Corporation and is licensed pursuant to the OpenSSL open source
 * license.
 *
 * The Contribution, originally written by Mika Kousa and Pasi Eronen of
 * Nokia Corporation, consists of the "PSK" (Pre-Shared Key) ciphersuites
 * support (see RFC 4279) to OpenSSL.
 *
 * No patent licenses or other rights except those expressly stated in
 * the OpenSSL open source license shall be deemed granted or received
 * expressly, by implication, estoppel, or otherwise.
 *
 * No assurances are provided by Nokia that the Contribution does not
 * infringe the patent or other intellectual property rights of any third
 * party or that the license provides you with all the necessary rights
 * to make use of the Contribution.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND. IN
 * ADDITION TO THE DISCLAIMERS INCLUDED IN THE LICENSE, NOKIA
 * SPECIFICALLY DISCLAIMS ANY LIABILITY FOR CLAIMS BROUGHT BY YOU OR ANY
 * OTHER ENTITY BASED ON INFRINGEMENT OF INTELLECTUAL PROPERTY RIGHTS OR
 * OTHERWISE. */

#include <openssl/ssl.h>

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/bytestring.h>
#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/lhash.h>
#include <openssl/mem.h>
#include <openssl/rand.h>

#include "internal.h"
#include "../crypto/internal.h"

#if defined(OPENSSL_WINDOWS)
#include <sys/timeb.h>
#else
#include <sys/socket.h>
#include <sys/time.h>
#endif


BSSL_NAMESPACE_BEGIN

// |SSL_R_UNKNOWN_PROTOCOL| is no longer emitted, but continue to define it
// to avoid downstream churn.
OPENSSL_DECLARE_ERROR_REASON(SSL, UNKNOWN_PROTOCOL)

// The following errors are no longer emitted, but are used in nginx without
// #ifdefs.
OPENSSL_DECLARE_ERROR_REASON(SSL, BLOCK_CIPHER_PAD_IS_WRONG)
OPENSSL_DECLARE_ERROR_REASON(SSL, NO_CIPHERS_SPECIFIED)

// Some error codes are special. Ensure the make_errors.go script never
// regresses this.
static_assert(SSL_R_TLSV1_ALERT_NO_RENEGOTIATION ==
                  SSL_AD_NO_RENEGOTIATION + SSL_AD_REASON_OFFSET,
              "alert reason code mismatch");

// kMaxHandshakeSize is the maximum size, in bytes, of a handshake message.
static const size_t kMaxHandshakeSize = (1u << 24) - 1;

static CRYPTO_EX_DATA_CLASS g_ex_data_class_ssl =
    CRYPTO_EX_DATA_CLASS_INIT_WITH_APP_DATA;
static CRYPTO_EX_DATA_CLASS g_ex_data_class_ssl_ctx =
    CRYPTO_EX_DATA_CLASS_INIT_WITH_APP_DATA;

bool CBBFinishArray(CBB *cbb, Array<uint8_t> *out) {
  uint8_t *ptr;
  size_t len;
  if (!CBB_finish(cbb, &ptr, &len)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return false;
  }
  out->Reset(ptr, len);
  return true;
}

void ssl_reset_error_state(SSL *ssl) {
  // Functions which use |SSL_get_error| must reset I/O and error state on
  // entry.
  ssl->s3->rwstate = SSL_ERROR_NONE;
  ERR_clear_error();
  ERR_clear_system_error();
}

void ssl_set_read_error(SSL* ssl) {
  ssl->s3->read_shutdown = ssl_shutdown_error;
  ssl->s3->read_error.reset(ERR_save_state());
}

static bool check_read_error(const SSL *ssl) {
  if (ssl->s3->read_shutdown == ssl_shutdown_error) {
    ERR_restore_state(ssl->s3->read_error.get());
    return false;
  }
  return true;
}

bool ssl_can_write(const SSL *ssl) {
  return !SSL_in_init(ssl) || ssl->s3->hs->can_early_write;
}

bool ssl_can_read(const SSL *ssl) {
  return !SSL_in_init(ssl) || ssl->s3->hs->can_early_read;
}

ssl_open_record_t ssl_open_handshake(SSL *ssl, size_t *out_consumed,
                                     uint8_t *out_alert, Span<uint8_t> in) {
  *out_consumed = 0;
  if (!check_read_error(ssl)) {
    *out_alert = 0;
    return ssl_open_record_error;
  }
  auto ret = ssl->method->open_handshake(ssl, out_consumed, out_alert, in);
  if (ret == ssl_open_record_error) {
    ssl_set_read_error(ssl);
  }
  return ret;
}

ssl_open_record_t ssl_open_change_cipher_spec(SSL *ssl, size_t *out_consumed,
                                              uint8_t *out_alert,
                                              Span<uint8_t> in) {
  *out_consumed = 0;
  if (!check_read_error(ssl)) {
    *out_alert = 0;
    return ssl_open_record_error;
  }
  auto ret =
      ssl->method->open_change_cipher_spec(ssl, out_consumed, out_alert, in);
  if (ret == ssl_open_record_error) {
    ssl_set_read_error(ssl);
  }
  return ret;
}

ssl_open_record_t ssl_open_app_data(SSL *ssl, Span<uint8_t> *out,
                                    size_t *out_consumed, uint8_t *out_alert,
                                    Span<uint8_t> in) {
  *out_consumed = 0;
  if (!check_read_error(ssl)) {
    *out_alert = 0;
    return ssl_open_record_error;
  }
  auto ret = ssl->method->open_app_data(ssl, out, out_consumed, out_alert, in);
  if (ret == ssl_open_record_error) {
    ssl_set_read_error(ssl);
  }
  return ret;
}

void ssl_update_cache(SSL_HANDSHAKE *hs, int mode) {
  SSL *const ssl = hs->ssl;
  SSL_CTX *ctx = ssl->session_ctx.get();
  // Never cache sessions with empty session IDs.
  if (ssl->s3->established_session->session_id_length == 0 ||
      ssl->s3->established_session->not_resumable ||
      (ctx->session_cache_mode & mode) != mode) {
    return;
  }

  // Clients never use the internal session cache.
  int use_internal_cache = ssl->server && !(ctx->session_cache_mode &
                                            SSL_SESS_CACHE_NO_INTERNAL_STORE);

  // A client may see new sessions on abbreviated handshakes if the server
  // decides to renew the ticket. Once the handshake is completed, it should be
  // inserted into the cache.
  if (ssl->s3->established_session.get() != ssl->session.get() ||
      (!ssl->server && hs->ticket_expected)) {
    if (use_internal_cache) {
      SSL_CTX_add_session(ctx, ssl->s3->established_session.get());
    }
    if (ctx->new_session_cb != NULL) {
      UniquePtr<SSL_SESSION> ref = UpRef(ssl->s3->established_session);
      if (ctx->new_session_cb(ssl, ref.get())) {
        // |new_session_cb|'s return value signals whether it took ownership.
        ref.release();
      }
    }
  }

  if (use_internal_cache &&
      !(ctx->session_cache_mode & SSL_SESS_CACHE_NO_AUTO_CLEAR)) {
    // Automatically flush the internal session cache every 255 connections.
    int flush_cache = 0;
    CRYPTO_MUTEX_lock_write(&ctx->lock);
    ctx->handshakes_since_cache_flush++;
    if (ctx->handshakes_since_cache_flush >= 255) {
      flush_cache = 1;
      ctx->handshakes_since_cache_flush = 0;
    }
    CRYPTO_MUTEX_unlock_write(&ctx->lock);

    if (flush_cache) {
      struct OPENSSL_timeval now;
      ssl_get_current_time(ssl, &now);
      SSL_CTX_flush_sessions(ctx, now.tv_sec);
    }
  }
}

static bool cbb_add_hex(CBB *cbb, Span<const uint8_t> in) {
  static const char hextable[] = "0123456789abcdef";
  uint8_t *out;

  if (!CBB_add_space(cbb, &out, in.size() * 2)) {
    return false;
  }

  for (uint8_t b : in) {
    *(out++) = (uint8_t)hextable[b >> 4];
    *(out++) = (uint8_t)hextable[b & 0xf];
  }

  return true;
}

bool ssl_log_secret(const SSL *ssl, const char *label,
                    Span<const uint8_t> secret) {
  if (ssl->ctx->keylog_callback == NULL) {
    return true;
  }

  ScopedCBB cbb;
  Array<uint8_t> line;
  if (!CBB_init(cbb.get(), strlen(label) + 1 + SSL3_RANDOM_SIZE * 2 + 1 +
                               secret.size() * 2 + 1) ||
      !CBB_add_bytes(cbb.get(), reinterpret_cast<const uint8_t *>(label),
                     strlen(label)) ||
      !CBB_add_u8(cbb.get(), ' ') ||
      !cbb_add_hex(cbb.get(), ssl->s3->client_random) ||
      !CBB_add_u8(cbb.get(), ' ') ||
      !cbb_add_hex(cbb.get(), secret) ||
      !CBB_add_u8(cbb.get(), 0 /* NUL */) ||
      !CBBFinishArray(cbb.get(), &line)) {
    return false;
  }

  ssl->ctx->keylog_callback(ssl, reinterpret_cast<const char *>(line.data()));
  return true;
}

void ssl_do_info_callback(const SSL *ssl, int type, int value) {
  void (*cb)(const SSL *ssl, int type, int value) = NULL;
  if (ssl->info_callback != NULL) {
    cb = ssl->info_callback;
  } else if (ssl->ctx->info_callback != NULL) {
    cb = ssl->ctx->info_callback;
  }

  if (cb != NULL) {
    cb(ssl, type, value);
  }
}

void ssl_do_msg_callback(const SSL *ssl, int is_write, int content_type,
                         Span<const uint8_t> in) {
  if (ssl->msg_callback == NULL) {
    return;
  }

  // |version| is zero when calling for |SSL3_RT_HEADER| and |SSL2_VERSION| for
  // a V2ClientHello.
  int version;
  switch (content_type) {
    case 0:
      // V2ClientHello
      version = SSL2_VERSION;
      break;
    case SSL3_RT_HEADER:
      version = 0;
      break;
    default:
      version = SSL_version(ssl);
  }

  ssl->msg_callback(is_write, version, content_type, in.data(), in.size(),
                    const_cast<SSL *>(ssl), ssl->msg_callback_arg);
}

void ssl_get_current_time(const SSL *ssl, struct OPENSSL_timeval *out_clock) {
  // TODO(martinkr): Change callers to |ssl_ctx_get_current_time| and drop the
  // |ssl| arg from |current_time_cb| if possible.
  ssl_ctx_get_current_time(ssl->ctx.get(), out_clock);
}

void ssl_ctx_get_current_time(const SSL_CTX *ctx,
                              struct OPENSSL_timeval *out_clock) {
  if (ctx->current_time_cb != NULL) {
    // TODO(davidben): Update current_time_cb to use OPENSSL_timeval. See
    // https://crbug.com/boringssl/155.
    struct timeval clock;
    ctx->current_time_cb(nullptr /* ssl */, &clock);
    if (clock.tv_sec < 0) {
      assert(0);
      out_clock->tv_sec = 0;
      out_clock->tv_usec = 0;
    } else {
      out_clock->tv_sec = (uint64_t)clock.tv_sec;
      out_clock->tv_usec = (uint32_t)clock.tv_usec;
    }
    return;
  }

#if defined(BORINGSSL_UNSAFE_DETERMINISTIC_MODE)
  out_clock->tv_sec = 1234;
  out_clock->tv_usec = 1234;
#elif defined(OPENSSL_WINDOWS)
  struct _timeb time;
  _ftime(&time);
  if (time.time < 0) {
    assert(0);
    out_clock->tv_sec = 0;
    out_clock->tv_usec = 0;
  } else {
    out_clock->tv_sec = time.time;
    out_clock->tv_usec = time.millitm * 1000;
  }
#else
  struct timeval clock;
  gettimeofday(&clock, NULL);
  if (clock.tv_sec < 0) {
    assert(0);
    out_clock->tv_sec = 0;
    out_clock->tv_usec = 0;
  } else {
    out_clock->tv_sec = (uint64_t)clock.tv_sec;
    out_clock->tv_usec = (uint32_t)clock.tv_usec;
  }
#endif
}

void SSL_CTX_set_handoff_mode(SSL_CTX *ctx, bool on) {
  ctx->handoff = on;
}

static bool ssl_can_renegotiate(const SSL *ssl) {
  if (ssl->server || SSL_is_dtls(ssl)) {
    return false;
  }

  if (ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    return false;
  }

  // The config has already been shed.
  if (!ssl->config) {
    return false;
  }

  switch (ssl->renegotiate_mode) {
    case ssl_renegotiate_ignore:
    case ssl_renegotiate_never:
      return false;

    case ssl_renegotiate_freely:
      return true;
    case ssl_renegotiate_once:
      return ssl->s3->total_renegotiations == 0;
  }

  assert(0);
  return false;
}

static void ssl_maybe_shed_handshake_config(SSL *ssl) {
  if (ssl->s3->hs != nullptr ||
      ssl->config == nullptr ||
      !ssl->config->shed_handshake_config ||
      ssl_can_renegotiate(ssl)) {
    return;
  }

  ssl->config.reset();
}

void SSL_set_handoff_mode(SSL *ssl, bool on) {
  if (!ssl->config) {
    return;
  }
  ssl->config->handoff = on;
}

bool SSL_get_traffic_secrets(const SSL *ssl,
                             Span<const uint8_t> *out_read_traffic_secret,
                             Span<const uint8_t> *out_write_traffic_secret) {
  if (SSL_version(ssl) < TLS1_3_VERSION) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_WRONG_SSL_VERSION);
    return false;
  }

  if (!ssl->s3->initial_handshake_complete) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_HANDSHAKE_NOT_COMPLETE);
    return false;
  }

  *out_read_traffic_secret = Span<const uint8_t>(
      ssl->s3->read_traffic_secret, ssl->s3->read_traffic_secret_len);
  *out_write_traffic_secret = Span<const uint8_t>(
      ssl->s3->write_traffic_secret, ssl->s3->write_traffic_secret_len);

  return true;
}

BSSL_NAMESPACE_END

using namespace bssl;

int SSL_library_init(void) {
  CRYPTO_library_init();
  return 1;
}

int OPENSSL_init_ssl(uint64_t opts, const OPENSSL_INIT_SETTINGS *settings) {
  CRYPTO_library_init();
  return 1;
}

static uint32_t ssl_session_hash(const SSL_SESSION *sess) {
  return ssl_hash_session_id(
      MakeConstSpan(sess->session_id, sess->session_id_length));
}

static int ssl_session_cmp(const SSL_SESSION *a, const SSL_SESSION *b) {
  if (a->session_id_length != b->session_id_length) {
    return 1;
  }

  return OPENSSL_memcmp(a->session_id, b->session_id, a->session_id_length);
}

ssl_ctx_st::ssl_ctx_st(const SSL_METHOD *ssl_method)
    : method(ssl_method->method),
      x509_method(ssl_method->x509_method),
      retain_only_sha256_of_client_certs(false),
      quiet_shutdown(false),
      ocsp_stapling_enabled(false),
      signed_cert_timestamps_enabled(false),
      channel_id_enabled(false),
      grease_enabled(false),
      allow_unknown_alpn_protos(false),
      ed25519_enabled(false),
      rsa_pss_rsae_certs_enabled(true),
      false_start_allowed_without_alpn(false),
      ignore_tls13_downgrade(false),
      handoff(false),
      enable_early_data(false),
      pq_experiment_signal(false) {
  CRYPTO_MUTEX_init(&lock);
  CRYPTO_new_ex_data(&ex_data);
}

ssl_ctx_st::~ssl_ctx_st() {
  // Free the internal session cache. Note that this calls the caller-supplied
  // remove callback, so we must do it before clearing ex_data. (See ticket
  // [openssl.org #212].)
  SSL_CTX_flush_sessions(this, 0);

  CRYPTO_free_ex_data(&g_ex_data_class_ssl_ctx, this, &ex_data);

  CRYPTO_MUTEX_cleanup(&lock);
  lh_SSL_SESSION_free(sessions);
  x509_method->ssl_ctx_free(this);
}

SSL_CTX *SSL_CTX_new(const SSL_METHOD *method) {
  if (method == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_NULL_SSL_METHOD_PASSED);
    return nullptr;
  }

  UniquePtr<SSL_CTX> ret = MakeUnique<SSL_CTX>(method);
  if (!ret) {
    return nullptr;
  }

  ret->cert = MakeUnique<CERT>(method->x509_method);
  ret->sessions = lh_SSL_SESSION_new(ssl_session_hash, ssl_session_cmp);
  ret->client_CA.reset(sk_CRYPTO_BUFFER_new_null());
  if (ret->cert == nullptr ||
      ret->sessions == nullptr ||
      ret->client_CA == nullptr ||
      !ret->x509_method->ssl_ctx_new(ret.get())) {
    return nullptr;
  }

  if (!SSL_CTX_set_strict_cipher_list(ret.get(), SSL_DEFAULT_CIPHER_LIST) ||
      // Lock the SSL_CTX to the specified version, for compatibility with
      // legacy uses of SSL_METHOD.
      !SSL_CTX_set_max_proto_version(ret.get(), method->version) ||
      !SSL_CTX_set_min_proto_version(ret.get(), method->version)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return nullptr;
  }

  return ret.release();
}

int SSL_CTX_up_ref(SSL_CTX *ctx) {
  CRYPTO_refcount_inc(&ctx->references);
  return 1;
}

void SSL_CTX_free(SSL_CTX *ctx) {
  if (ctx == NULL ||
      !CRYPTO_refcount_dec_and_test_zero(&ctx->references)) {
    return;
  }

  ctx->~ssl_ctx_st();
  OPENSSL_free(ctx);
}

ssl_st::ssl_st(SSL_CTX *ctx_arg)
    : method(ctx_arg->method),
      max_send_fragment(ctx_arg->max_send_fragment),
      msg_callback(ctx_arg->msg_callback),
      msg_callback_arg(ctx_arg->msg_callback_arg),
      ctx(UpRef(ctx_arg)),
      session_ctx(UpRef(ctx_arg)),
      options(ctx->options),
      mode(ctx->mode),
      max_cert_list(ctx->max_cert_list),
      server(false),
      quiet_shutdown(ctx->quiet_shutdown),
      enable_early_data(ctx->enable_early_data) {
  CRYPTO_new_ex_data(&ex_data);
}

ssl_st::~ssl_st() {
  CRYPTO_free_ex_data(&g_ex_data_class_ssl, this, &ex_data);
  // |config| refers to |this|, so we must release it earlier.
  config.reset();
  if (method != NULL) {
    method->ssl_free(this);
  }
}

SSL *SSL_new(SSL_CTX *ctx) {
  if (ctx == nullptr) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_NULL_SSL_CTX);
    return nullptr;
  }

  UniquePtr<SSL> ssl = MakeUnique<SSL>(ctx);
  if (ssl == nullptr) {
    return nullptr;
  }

  ssl->config = MakeUnique<SSL_CONFIG>(ssl.get());
  if (ssl->config == nullptr) {
    return nullptr;
  }
  ssl->config->conf_min_version = ctx->conf_min_version;
  ssl->config->conf_max_version = ctx->conf_max_version;

  ssl->config->cert = ssl_cert_dup(ctx->cert.get());
  if (ssl->config->cert == nullptr) {
    return nullptr;
  }

  ssl->config->verify_mode = ctx->verify_mode;
  ssl->config->verify_callback = ctx->default_verify_callback;
  ssl->config->custom_verify_callback = ctx->custom_verify_callback;
  ssl->config->retain_only_sha256_of_client_certs =
      ctx->retain_only_sha256_of_client_certs;

  if (!ssl->config->supported_group_list.CopyFrom(ctx->supported_group_list) ||
      !ssl->config->alpn_client_proto_list.CopyFrom(
          ctx->alpn_client_proto_list) ||
      !ssl->config->verify_sigalgs.CopyFrom(ctx->verify_sigalgs)) {
    return nullptr;
  }

  if (ctx->psk_identity_hint) {
    ssl->config->psk_identity_hint.reset(
        BUF_strdup(ctx->psk_identity_hint.get()));
    if (ssl->config->psk_identity_hint == nullptr) {
      return nullptr;
    }
  }
  ssl->config->psk_client_callback = ctx->psk_client_callback;
  ssl->config->psk_server_callback = ctx->psk_server_callback;

  ssl->config->channel_id_enabled = ctx->channel_id_enabled;
  ssl->config->channel_id_private = UpRef(ctx->channel_id_private);

  ssl->config->signed_cert_timestamps_enabled =
      ctx->signed_cert_timestamps_enabled;
  ssl->config->ocsp_stapling_enabled = ctx->ocsp_stapling_enabled;
  ssl->config->handoff = ctx->handoff;
  ssl->config->ignore_tls13_downgrade = ctx->ignore_tls13_downgrade;
  ssl->quic_method = ctx->quic_method;

  if (!ssl->method->ssl_new(ssl.get()) ||
      !ssl->ctx->x509_method->ssl_new(ssl->s3->hs.get())) {
    return nullptr;
  }

  return ssl.release();
}

SSL_CONFIG::SSL_CONFIG(SSL *ssl_arg)
    : ssl(ssl_arg),
      signed_cert_timestamps_enabled(false),
      ocsp_stapling_enabled(false),
      channel_id_enabled(false),
      enforce_rsa_key_usage(false),
      retain_only_sha256_of_client_certs(false),
      handoff(false),
      shed_handshake_config(false),
      ignore_tls13_downgrade(false),
      jdk11_workaround(false) {
  assert(ssl);
}

SSL_CONFIG::~SSL_CONFIG() {
  if (ssl->ctx != nullptr) {
    ssl->ctx->x509_method->ssl_config_free(this);
  }
}

void SSL_free(SSL *ssl) {
  Delete(ssl);
}

void SSL_set_connect_state(SSL *ssl) {
  ssl->server = false;
  ssl->do_handshake = ssl_client_handshake;
}

void SSL_set_accept_state(SSL *ssl) {
  ssl->server = true;
  ssl->do_handshake = ssl_server_handshake;
}

void SSL_set0_rbio(SSL *ssl, BIO *rbio) {
  ssl->rbio.reset(rbio);
}

void SSL_set0_wbio(SSL *ssl, BIO *wbio) {
  ssl->wbio.reset(wbio);
}

void SSL_set_bio(SSL *ssl, BIO *rbio, BIO *wbio) {
  // For historical reasons, this function has many different cases in ownership
  // handling.

  // If nothing has changed, do nothing
  if (rbio == SSL_get_rbio(ssl) && wbio == SSL_get_wbio(ssl)) {
    return;
  }

  // If the two arguments are equal, one fewer reference is granted than
  // taken.
  if (rbio != NULL && rbio == wbio) {
    BIO_up_ref(rbio);
  }

  // If only the wbio is changed, adopt only one reference.
  if (rbio == SSL_get_rbio(ssl)) {
    SSL_set0_wbio(ssl, wbio);
    return;
  }

  // There is an asymmetry here for historical reasons. If only the rbio is
  // changed AND the rbio and wbio were originally different, then we only adopt
  // one reference.
  if (wbio == SSL_get_wbio(ssl) && SSL_get_rbio(ssl) != SSL_get_wbio(ssl)) {
    SSL_set0_rbio(ssl, rbio);
    return;
  }

  // Otherwise, adopt both references.
  SSL_set0_rbio(ssl, rbio);
  SSL_set0_wbio(ssl, wbio);
}

BIO *SSL_get_rbio(const SSL *ssl) { return ssl->rbio.get(); }

BIO *SSL_get_wbio(const SSL *ssl) { return ssl->wbio.get(); }

size_t SSL_quic_max_handshake_flight_len(const SSL *ssl,
                                         enum ssl_encryption_level_t level) {
  // Limits flights to 16K by default when there are no large
  // (certificate-carrying) messages.
  static const size_t kDefaultLimit = 16384;

  switch (level) {
    case ssl_encryption_initial:
      return kDefaultLimit;
    case ssl_encryption_early_data:
      // QUIC does not send EndOfEarlyData.
      return 0;
    case ssl_encryption_handshake:
      if (ssl->server) {
        // Servers may receive Certificate message if configured to request
        // client certificates.
        if (!!(ssl->config->verify_mode & SSL_VERIFY_PEER) &&
            ssl->max_cert_list > kDefaultLimit) {
          return ssl->max_cert_list;
        }
      } else {
        // Clients may receive both Certificate message and a CertificateRequest
        // message.
        if (2*ssl->max_cert_list > kDefaultLimit) {
          return 2*ssl->max_cert_list;
        }
      }
      return kDefaultLimit;
    case ssl_encryption_application:
      // Note there is not actually a bound on the number of NewSessionTickets
      // one may send in a row. This level may need more involved flow
      // control. See https://github.com/quicwg/base-drafts/issues/1834.
      return kDefaultLimit;
  }

  return 0;
}

enum ssl_encryption_level_t SSL_quic_read_level(const SSL *ssl) {
  return ssl->s3->read_level;
}

enum ssl_encryption_level_t SSL_quic_write_level(const SSL *ssl) {
  return ssl->s3->write_level;
}

int SSL_provide_quic_data(SSL *ssl, enum ssl_encryption_level_t level,
                          const uint8_t *data, size_t len) {
  if (ssl->quic_method == nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  if (level != ssl->s3->read_level) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_WRONG_ENCRYPTION_LEVEL_RECEIVED);
    return 0;
  }

  size_t new_len = (ssl->s3->hs_buf ? ssl->s3->hs_buf->length : 0) + len;
  if (new_len < len ||
      new_len > SSL_quic_max_handshake_flight_len(ssl, level)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_EXCESSIVE_MESSAGE_SIZE);
    return 0;
  }

  return tls_append_handshake_data(ssl, MakeConstSpan(data, len));
}

int SSL_do_handshake(SSL *ssl) {
  ssl_reset_error_state(ssl);

  if (ssl->do_handshake == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_CONNECTION_TYPE_NOT_SET);
    return -1;
  }

  if (!SSL_in_init(ssl)) {
    return 1;
  }

  // Run the handshake.
  SSL_HANDSHAKE *hs = ssl->s3->hs.get();

  bool early_return = false;
  int ret = ssl_run_handshake(hs, &early_return);
  ssl_do_info_callback(
      ssl, ssl->server ? SSL_CB_ACCEPT_EXIT : SSL_CB_CONNECT_EXIT, ret);
  if (ret <= 0) {
    return ret;
  }

  // Destroy the handshake object if the handshake has completely finished.
  if (!early_return) {
    ssl->s3->hs.reset();
    ssl_maybe_shed_handshake_config(ssl);
  }

  return 1;
}

int SSL_connect(SSL *ssl) {
  if (ssl->do_handshake == NULL) {
    // Not properly initialized yet
    SSL_set_connect_state(ssl);
  }

  return SSL_do_handshake(ssl);
}

int SSL_accept(SSL *ssl) {
  if (ssl->do_handshake == NULL) {
    // Not properly initialized yet
    SSL_set_accept_state(ssl);
  }

  return SSL_do_handshake(ssl);
}

static int ssl_do_post_handshake(SSL *ssl, const SSLMessage &msg) {
  if (ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    return tls13_post_handshake(ssl, msg);
  }

  // Check for renegotiation on the server before parsing to use the correct
  // error. Renegotiation is triggered by a different message for servers.
  if (ssl->server) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_NO_RENEGOTIATION);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_NO_RENEGOTIATION);
    return 0;
  }

  if (msg.type != SSL3_MT_HELLO_REQUEST || CBS_len(&msg.body) != 0) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
    OPENSSL_PUT_ERROR(SSL, SSL_R_BAD_HELLO_REQUEST);
    return 0;
  }

  if (ssl->renegotiate_mode == ssl_renegotiate_ignore) {
    return 1;  // Ignore the HelloRequest.
  }

  if (!ssl_can_renegotiate(ssl) ||
      // Renegotiation is only supported at quiescent points in the application
      // protocol, namely in HTTPS, just before reading the HTTP response.
      // Require the record-layer be idle and avoid complexities of sending a
      // handshake record while an application_data record is being written.
      !ssl->s3->write_buffer.empty() ||
      ssl->s3->write_shutdown != ssl_shutdown_none) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_NO_RENEGOTIATION);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_NO_RENEGOTIATION);
    return 0;
  }

  // Begin a new handshake.
  if (ssl->s3->hs != nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return 0;
  }
  ssl->s3->hs = ssl_handshake_new(ssl);
  if (ssl->s3->hs == nullptr) {
    return 0;
  }

  ssl->s3->total_renegotiations++;
  return 1;
}

int SSL_process_quic_post_handshake(SSL *ssl) {
  ssl_reset_error_state(ssl);

  if (SSL_in_init(ssl)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  // Replay post-handshake message errors.
  if (!check_read_error(ssl)) {
    return 0;
  }

  // Process any buffered post-handshake messages.
  SSLMessage msg;
  while (ssl->method->get_message(ssl, &msg)) {
    // Handle the post-handshake message and try again.
    if (!ssl_do_post_handshake(ssl, msg)) {
      ssl_set_read_error(ssl);
      return 0;
    }
    ssl->method->next_message(ssl);
  }

  return 1;
}

static int ssl_read_impl(SSL *ssl) {
  ssl_reset_error_state(ssl);

  if (ssl->do_handshake == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNINITIALIZED);
    return -1;
  }

  // Replay post-handshake message errors.
  if (!check_read_error(ssl)) {
    return -1;
  }

  while (ssl->s3->pending_app_data.empty()) {
    // Complete the current handshake, if any. False Start will cause
    // |SSL_do_handshake| to return mid-handshake, so this may require multiple
    // iterations.
    while (!ssl_can_read(ssl)) {
      int ret = SSL_do_handshake(ssl);
      if (ret < 0) {
        return ret;
      }
      if (ret == 0) {
        OPENSSL_PUT_ERROR(SSL, SSL_R_SSL_HANDSHAKE_FAILURE);
        return -1;
      }
    }

    // Process any buffered post-handshake messages.
    SSLMessage msg;
    if (ssl->method->get_message(ssl, &msg)) {
      // If we received an interrupt in early read (EndOfEarlyData), loop again
      // for the handshake to process it.
      if (SSL_in_init(ssl)) {
        ssl->s3->hs->can_early_read = false;
        continue;
      }

      // Handle the post-handshake message and try again.
      if (!ssl_do_post_handshake(ssl, msg)) {
        ssl_set_read_error(ssl);
        return -1;
      }
      ssl->method->next_message(ssl);
      continue;  // Loop again. We may have begun a new handshake.
    }

    uint8_t alert = SSL_AD_DECODE_ERROR;
    size_t consumed = 0;
    auto ret = ssl_open_app_data(ssl, &ssl->s3->pending_app_data, &consumed,
                                 &alert, ssl->s3->read_buffer.span());
    bool retry;
    int bio_ret = ssl_handle_open_record(ssl, &retry, ret, consumed, alert);
    if (bio_ret <= 0) {
      return bio_ret;
    }
    if (!retry) {
      assert(!ssl->s3->pending_app_data.empty());
      ssl->s3->key_update_count = 0;
    }
  }

  return 1;
}

int SSL_read(SSL *ssl, void *buf, int num) {
  int ret = SSL_peek(ssl, buf, num);
  if (ret <= 0) {
    return ret;
  }
  // TODO(davidben): In DTLS, should the rest of the record be discarded?  DTLS
  // is not a stream. See https://crbug.com/boringssl/65.
  ssl->s3->pending_app_data =
      ssl->s3->pending_app_data.subspan(static_cast<size_t>(ret));
  if (ssl->s3->pending_app_data.empty()) {
    ssl->s3->read_buffer.DiscardConsumed();
  }
  return ret;
}

int SSL_peek(SSL *ssl, void *buf, int num) {
  if (ssl->quic_method != nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  int ret = ssl_read_impl(ssl);
  if (ret <= 0) {
    return ret;
  }
  if (num <= 0) {
    return num;
  }
  size_t todo =
      std::min(ssl->s3->pending_app_data.size(), static_cast<size_t>(num));
  OPENSSL_memcpy(buf, ssl->s3->pending_app_data.data(), todo);
  return static_cast<int>(todo);
}

int SSL_write(SSL *ssl, const void *buf, int num) {
  ssl_reset_error_state(ssl);

  if (ssl->quic_method != nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  if (ssl->do_handshake == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNINITIALIZED);
    return -1;
  }

  if (ssl->s3->write_shutdown != ssl_shutdown_none) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_PROTOCOL_IS_SHUTDOWN);
    return -1;
  }

  int ret = 0;
  bool needs_handshake = false;
  do {
    // If necessary, complete the handshake implicitly.
    if (!ssl_can_write(ssl)) {
      ret = SSL_do_handshake(ssl);
      if (ret < 0) {
        return ret;
      }
      if (ret == 0) {
        OPENSSL_PUT_ERROR(SSL, SSL_R_SSL_HANDSHAKE_FAILURE);
        return -1;
      }
    }

    ret = ssl->method->write_app_data(ssl, &needs_handshake,
                                      (const uint8_t *)buf, num);
  } while (needs_handshake);
  return ret;
}

int SSL_key_update(SSL *ssl, int request_type) {
  ssl_reset_error_state(ssl);

  if (ssl->do_handshake == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNINITIALIZED);
    return 0;
  }

  if (ssl->ctx->quic_method != nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  if (!ssl->s3->initial_handshake_complete) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_HANDSHAKE_NOT_COMPLETE);
    return 0;
  }

  if (ssl_protocol_version(ssl) < TLS1_3_VERSION) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_WRONG_SSL_VERSION);
    return 0;
  }

  if (!ssl->s3->key_update_pending &&
      !tls13_add_key_update(ssl, request_type)) {
    return 0;
  }

  return 1;
}

int SSL_shutdown(SSL *ssl) {
  ssl_reset_error_state(ssl);

  if (ssl->do_handshake == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNINITIALIZED);
    return -1;
  }

  // If we are in the middle of a handshake, silently succeed. Consumers often
  // call this function before |SSL_free|, whether the handshake succeeded or
  // not. We assume the caller has already handled failed handshakes.
  if (SSL_in_init(ssl)) {
    return 1;
  }

  if (ssl->quiet_shutdown) {
    // Do nothing if configured not to send a close_notify.
    ssl->s3->write_shutdown = ssl_shutdown_close_notify;
    ssl->s3->read_shutdown = ssl_shutdown_close_notify;
    return 1;
  }

  // This function completes in two stages. It sends a close_notify and then it
  // waits for a close_notify to come in. Perform exactly one action and return
  // whether or not it succeeds.

  if (ssl->s3->write_shutdown != ssl_shutdown_close_notify) {
    // Send a close_notify.
    if (ssl_send_alert_impl(ssl, SSL3_AL_WARNING, SSL_AD_CLOSE_NOTIFY) <= 0) {
      return -1;
    }
  } else if (ssl->s3->alert_dispatch) {
    // Finish sending the close_notify.
    if (ssl->method->dispatch_alert(ssl) <= 0) {
      return -1;
    }
  } else if (ssl->s3->read_shutdown != ssl_shutdown_close_notify) {
    if (SSL_is_dtls(ssl)) {
      // Bidirectional shutdown doesn't make sense for an unordered
      // transport. DTLS alerts also aren't delivered reliably, so we may even
      // time out because the peer never received our close_notify. Report to
      // the caller that the channel has fully shut down.
      if (ssl->s3->read_shutdown == ssl_shutdown_error) {
        ERR_restore_state(ssl->s3->read_error.get());
        return -1;
      }
      ssl->s3->read_shutdown = ssl_shutdown_close_notify;
    } else {
      // Process records until an error, close_notify, or application data.
      if (ssl_read_impl(ssl) > 0) {
        // We received some unexpected application data.
        OPENSSL_PUT_ERROR(SSL, SSL_R_APPLICATION_DATA_ON_SHUTDOWN);
        return -1;
      }
      if (ssl->s3->read_shutdown != ssl_shutdown_close_notify) {
        return -1;
      }
    }
  }

  // Return 0 for unidirectional shutdown and 1 for bidirectional shutdown.
  return ssl->s3->read_shutdown == ssl_shutdown_close_notify;
}

int SSL_send_fatal_alert(SSL *ssl, uint8_t alert) {
  if (ssl->s3->alert_dispatch) {
    if (ssl->s3->send_alert[0] != SSL3_AL_FATAL ||
        ssl->s3->send_alert[1] != alert) {
      // We are already attempting to write a different alert.
      OPENSSL_PUT_ERROR(SSL, SSL_R_PROTOCOL_IS_SHUTDOWN);
      return -1;
    }
    return ssl->method->dispatch_alert(ssl);
  }

  return ssl_send_alert_impl(ssl, SSL3_AL_FATAL, alert);
}

void SSL_CTX_enable_pq_experiment_signal(SSL_CTX *ctx) {
  ctx->pq_experiment_signal = true;
}

int SSL_pq_experiment_signal_seen(const SSL *ssl) {
  return ssl->s3->pq_experiment_signal_seen;
}

int SSL_set_quic_transport_params(SSL *ssl, const uint8_t *params,
                                  size_t params_len) {
  return ssl->config && ssl->config->quic_transport_params.CopyFrom(
                            MakeConstSpan(params, params_len));
}

void SSL_get_peer_quic_transport_params(const SSL *ssl,
                                        const uint8_t **out_params,
                                        size_t *out_params_len) {
  *out_params = ssl->s3->peer_quic_transport_params.data();
  *out_params_len = ssl->s3->peer_quic_transport_params.size();
}

void SSL_CTX_set_early_data_enabled(SSL_CTX *ctx, int enabled) {
  ctx->enable_early_data = !!enabled;
}

void SSL_set_early_data_enabled(SSL *ssl, int enabled) {
  ssl->enable_early_data = !!enabled;
}

int SSL_in_early_data(const SSL *ssl) {
  if (ssl->s3->hs == NULL) {
    return 0;
  }
  return ssl->s3->hs->in_early_data;
}

int SSL_early_data_accepted(const SSL *ssl) {
  return ssl->s3->early_data_accepted;
}

void SSL_reset_early_data_reject(SSL *ssl) {
  SSL_HANDSHAKE *hs = ssl->s3->hs.get();
  if (hs == NULL ||
      hs->wait != ssl_hs_early_data_rejected) {
    abort();
  }

  hs->wait = ssl_hs_ok;
  hs->in_early_data = false;
  hs->early_session.reset();

  // Discard any unfinished writes from the perspective of |SSL_write|'s
  // retry. The handshake will transparently flush out the pending record
  // (discarded by the server) to keep the framing correct.
  ssl->s3->wpend_pending = false;
}

enum ssl_early_data_reason_t SSL_get_early_data_reason(const SSL *ssl) {
  return ssl->s3->early_data_reason;
}

static int bio_retry_reason_to_error(int reason) {
  switch (reason) {
    case BIO_RR_CONNECT:
      return SSL_ERROR_WANT_CONNECT;
    case BIO_RR_ACCEPT:
      return SSL_ERROR_WANT_ACCEPT;
    default:
      return SSL_ERROR_SYSCALL;
  }
}

int SSL_get_error(const SSL *ssl, int ret_code) {
  if (ret_code > 0) {
    return SSL_ERROR_NONE;
  }

  // Make things return SSL_ERROR_SYSCALL when doing SSL_do_handshake etc,
  // where we do encode the error
  uint32_t err = ERR_peek_error();
  if (err != 0) {
    if (ERR_GET_LIB(err) == ERR_LIB_SYS) {
      return SSL_ERROR_SYSCALL;
    }
    return SSL_ERROR_SSL;
  }

  if (ret_code == 0) {
    if (ssl->s3->read_shutdown == ssl_shutdown_close_notify) {
      return SSL_ERROR_ZERO_RETURN;
    }
    // An EOF was observed which violates the protocol, and the underlying
    // transport does not participate in the error queue. Bubble up to the
    // caller.
    return SSL_ERROR_SYSCALL;
  }

  switch (ssl->s3->rwstate) {
    case SSL_ERROR_PENDING_SESSION:
    case SSL_ERROR_PENDING_CERTIFICATE:
    case SSL_ERROR_HANDOFF:
    case SSL_ERROR_HANDBACK:
    case SSL_ERROR_WANT_X509_LOOKUP:
    case SSL_ERROR_WANT_CHANNEL_ID_LOOKUP:
    case SSL_ERROR_WANT_PRIVATE_KEY_OPERATION:
    case SSL_ERROR_PENDING_TICKET:
    case SSL_ERROR_EARLY_DATA_REJECTED:
    case SSL_ERROR_WANT_CERTIFICATE_VERIFY:
      return ssl->s3->rwstate;

    case SSL_ERROR_WANT_READ: {
      if (ssl->quic_method) {
        return SSL_ERROR_WANT_READ;
      }
      BIO *bio = SSL_get_rbio(ssl);
      if (BIO_should_read(bio)) {
        return SSL_ERROR_WANT_READ;
      }

      if (BIO_should_write(bio)) {
        // TODO(davidben): OpenSSL historically checked for writes on the read
        // BIO. Can this be removed?
        return SSL_ERROR_WANT_WRITE;
      }

      if (BIO_should_io_special(bio)) {
        return bio_retry_reason_to_error(BIO_get_retry_reason(bio));
      }

      break;
    }

    case SSL_ERROR_WANT_WRITE: {
      BIO *bio = SSL_get_wbio(ssl);
      if (BIO_should_write(bio)) {
        return SSL_ERROR_WANT_WRITE;
      }

      if (BIO_should_read(bio)) {
        // TODO(davidben): OpenSSL historically checked for reads on the write
        // BIO. Can this be removed?
        return SSL_ERROR_WANT_READ;
      }

      if (BIO_should_io_special(bio)) {
        return bio_retry_reason_to_error(BIO_get_retry_reason(bio));
      }

      break;
    }
  }

  return SSL_ERROR_SYSCALL;
}

const char *SSL_error_description(int err) {
  switch (err) {
    case SSL_ERROR_NONE:
      return "NONE";
    case SSL_ERROR_SSL:
      return "SSL";
    case SSL_ERROR_WANT_READ:
      return "WANT_READ";
    case SSL_ERROR_WANT_WRITE:
      return "WANT_WRITE";
    case SSL_ERROR_WANT_X509_LOOKUP:
      return "WANT_X509_LOOKUP";
    case SSL_ERROR_SYSCALL:
      return "SYSCALL";
    case SSL_ERROR_ZERO_RETURN:
      return "ZERO_RETURN";
    case SSL_ERROR_WANT_CONNECT:
      return "WANT_CONNECT";
    case SSL_ERROR_WANT_ACCEPT:
      return "WANT_ACCEPT";
    case SSL_ERROR_WANT_CHANNEL_ID_LOOKUP:
      return "WANT_CHANNEL_ID_LOOKUP";
    case SSL_ERROR_PENDING_SESSION:
      return "PENDING_SESSION";
    case SSL_ERROR_PENDING_CERTIFICATE:
      return "PENDING_CERTIFICATE";
    case SSL_ERROR_WANT_PRIVATE_KEY_OPERATION:
      return "WANT_PRIVATE_KEY_OPERATION";
    case SSL_ERROR_PENDING_TICKET:
      return "PENDING_TICKET";
    case SSL_ERROR_EARLY_DATA_REJECTED:
      return "EARLY_DATA_REJECTED";
    case SSL_ERROR_WANT_CERTIFICATE_VERIFY:
      return "WANT_CERTIFICATE_VERIFY";
    case SSL_ERROR_HANDOFF:
      return "HANDOFF";
    case SSL_ERROR_HANDBACK:
      return "HANDBACK";
    default:
      return nullptr;
  }
}

uint32_t SSL_CTX_set_options(SSL_CTX *ctx, uint32_t options) {
  ctx->options |= options;
  return ctx->options;
}

uint32_t SSL_CTX_clear_options(SSL_CTX *ctx, uint32_t options) {
  ctx->options &= ~options;
  return ctx->options;
}

uint32_t SSL_CTX_get_options(const SSL_CTX *ctx) { return ctx->options; }

uint32_t SSL_set_options(SSL *ssl, uint32_t options) {
  ssl->options |= options;
  return ssl->options;
}

uint32_t SSL_clear_options(SSL *ssl, uint32_t options) {
  ssl->options &= ~options;
  return ssl->options;
}

uint32_t SSL_get_options(const SSL *ssl) { return ssl->options; }

uint32_t SSL_CTX_set_mode(SSL_CTX *ctx, uint32_t mode) {
  ctx->mode |= mode;
  return ctx->mode;
}

uint32_t SSL_CTX_clear_mode(SSL_CTX *ctx, uint32_t mode) {
  ctx->mode &= ~mode;
  return ctx->mode;
}

uint32_t SSL_CTX_get_mode(const SSL_CTX *ctx) { return ctx->mode; }

uint32_t SSL_set_mode(SSL *ssl, uint32_t mode) {
  ssl->mode |= mode;
  return ssl->mode;
}

uint32_t SSL_clear_mode(SSL *ssl, uint32_t mode) {
  ssl->mode &= ~mode;
  return ssl->mode;
}

uint32_t SSL_get_mode(const SSL *ssl) { return ssl->mode; }

void SSL_CTX_set0_buffer_pool(SSL_CTX *ctx, CRYPTO_BUFFER_POOL *pool) {
  ctx->pool = pool;
}

int SSL_get_tls_unique(const SSL *ssl, uint8_t *out, size_t *out_len,
                       size_t max_out) {
  *out_len = 0;
  OPENSSL_memset(out, 0, max_out);

  // tls-unique is not defined for TLS 1.3.
  if (!ssl->s3->initial_handshake_complete ||
      ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    return 0;
  }

  // The tls-unique value is the first Finished message in the handshake, which
  // is the client's in a full handshake and the server's for a resumption. See
  // https://tools.ietf.org/html/rfc5929#section-3.1.
  const uint8_t *finished = ssl->s3->previous_client_finished;
  size_t finished_len = ssl->s3->previous_client_finished_len;
  if (ssl->session != NULL) {
    // tls-unique is broken for resumed sessions unless EMS is used.
    if (!ssl->session->extended_master_secret) {
      return 0;
    }
    finished = ssl->s3->previous_server_finished;
    finished_len = ssl->s3->previous_server_finished_len;
  }

  *out_len = finished_len;
  if (finished_len > max_out) {
    *out_len = max_out;
  }

  OPENSSL_memcpy(out, finished, *out_len);
  return 1;
}

static int set_session_id_context(CERT *cert, const uint8_t *sid_ctx,
                                   size_t sid_ctx_len) {
  if (sid_ctx_len > sizeof(cert->sid_ctx)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_SSL_SESSION_ID_CONTEXT_TOO_LONG);
    return 0;
  }

  static_assert(sizeof(cert->sid_ctx) < 256, "sid_ctx too large");
  cert->sid_ctx_length = (uint8_t)sid_ctx_len;
  OPENSSL_memcpy(cert->sid_ctx, sid_ctx, sid_ctx_len);
  return 1;
}

int SSL_CTX_set_session_id_context(SSL_CTX *ctx, const uint8_t *sid_ctx,
                                   size_t sid_ctx_len) {
  return set_session_id_context(ctx->cert.get(), sid_ctx, sid_ctx_len);
}

int SSL_set_session_id_context(SSL *ssl, const uint8_t *sid_ctx,
                               size_t sid_ctx_len) {
  if (!ssl->config) {
    return 0;
  }
  return set_session_id_context(ssl->config->cert.get(), sid_ctx, sid_ctx_len);
}

const uint8_t *SSL_get0_session_id_context(const SSL *ssl, size_t *out_len) {
  if (!ssl->config) {
    assert(ssl->config);
    *out_len = 0;
    return NULL;
  }
  *out_len = ssl->config->cert->sid_ctx_length;
  return ssl->config->cert->sid_ctx;
}

void SSL_certs_clear(SSL *ssl) {
  if (!ssl->config) {
    return;
  }
  ssl_cert_clear_certs(ssl->config->cert.get());
}

int SSL_get_fd(const SSL *ssl) { return SSL_get_rfd(ssl); }

int SSL_get_rfd(const SSL *ssl) {
  int ret = -1;
  BIO *b = BIO_find_type(SSL_get_rbio(ssl), BIO_TYPE_DESCRIPTOR);
  if (b != NULL) {
    BIO_get_fd(b, &ret);
  }
  return ret;
}

int SSL_get_wfd(const SSL *ssl) {
  int ret = -1;
  BIO *b = BIO_find_type(SSL_get_wbio(ssl), BIO_TYPE_DESCRIPTOR);
  if (b != NULL) {
    BIO_get_fd(b, &ret);
  }
  return ret;
}

int SSL_set_fd(SSL *ssl, int fd) {
  BIO *bio = BIO_new(BIO_s_socket());
  if (bio == NULL) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_BUF_LIB);
    return 0;
  }
  BIO_set_fd(bio, fd, BIO_NOCLOSE);
  SSL_set_bio(ssl, bio, bio);
  return 1;
}

int SSL_set_wfd(SSL *ssl, int fd) {
  BIO *rbio = SSL_get_rbio(ssl);
  if (rbio == NULL || BIO_method_type(rbio) != BIO_TYPE_SOCKET ||
      BIO_get_fd(rbio, NULL) != fd) {
    BIO *bio = BIO_new(BIO_s_socket());
    if (bio == NULL) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_BUF_LIB);
      return 0;
    }
    BIO_set_fd(bio, fd, BIO_NOCLOSE);
    SSL_set0_wbio(ssl, bio);
  } else {
    // Copy the rbio over to the wbio.
    BIO_up_ref(rbio);
    SSL_set0_wbio(ssl, rbio);
  }

  return 1;
}

int SSL_set_rfd(SSL *ssl, int fd) {
  BIO *wbio = SSL_get_wbio(ssl);
  if (wbio == NULL || BIO_method_type(wbio) != BIO_TYPE_SOCKET ||
      BIO_get_fd(wbio, NULL) != fd) {
    BIO *bio = BIO_new(BIO_s_socket());
    if (bio == NULL) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_BUF_LIB);
      return 0;
    }
    BIO_set_fd(bio, fd, BIO_NOCLOSE);
    SSL_set0_rbio(ssl, bio);
  } else {
    // Copy the wbio over to the rbio.
    BIO_up_ref(wbio);
    SSL_set0_rbio(ssl, wbio);
  }
  return 1;
}

static size_t copy_finished(void *out, size_t out_len, const uint8_t *in,
                            size_t in_len) {
  if (out_len > in_len) {
    out_len = in_len;
  }
  OPENSSL_memcpy(out, in, out_len);
  return in_len;
}

size_t SSL_get_finished(const SSL *ssl, void *buf, size_t count) {
  if (!ssl->s3->initial_handshake_complete ||
      ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    return 0;
  }

  if (ssl->server) {
    return copy_finished(buf, count, ssl->s3->previous_server_finished,
                         ssl->s3->previous_server_finished_len);
  }

  return copy_finished(buf, count, ssl->s3->previous_client_finished,
                       ssl->s3->previous_client_finished_len);
}

size_t SSL_get_peer_finished(const SSL *ssl, void *buf, size_t count) {
  if (!ssl->s3->initial_handshake_complete ||
      ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    return 0;
  }

  if (ssl->server) {
    return copy_finished(buf, count, ssl->s3->previous_client_finished,
                         ssl->s3->previous_client_finished_len);
  }

  return copy_finished(buf, count, ssl->s3->previous_server_finished,
                       ssl->s3->previous_server_finished_len);
}

int SSL_get_verify_mode(const SSL *ssl) {
  if (!ssl->config) {
    assert(ssl->config);
    return -1;
  }
  return ssl->config->verify_mode;
}

int SSL_get_extms_support(const SSL *ssl) {
  // TLS 1.3 does not require extended master secret and always reports as
  // supporting it.
  if (!ssl->s3->have_version) {
    return 0;
  }
  if (ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    return 1;
  }

  // If the initial handshake completed, query the established session.
  if (ssl->s3->established_session != NULL) {
    return ssl->s3->established_session->extended_master_secret;
  }

  // Otherwise, query the in-progress handshake.
  if (ssl->s3->hs != NULL) {
    return ssl->s3->hs->extended_master_secret;
  }
  assert(0);
  return 0;
}

int SSL_CTX_get_read_ahead(const SSL_CTX *ctx) { return 0; }

int SSL_get_read_ahead(const SSL *ssl) { return 0; }

int SSL_CTX_set_read_ahead(SSL_CTX *ctx, int yes) { return 1; }

int SSL_set_read_ahead(SSL *ssl, int yes) { return 1; }

int SSL_pending(const SSL *ssl) {
  return static_cast<int>(ssl->s3->pending_app_data.size());
}

int SSL_CTX_check_private_key(const SSL_CTX *ctx) {
  return ssl_cert_check_private_key(ctx->cert.get(),
                                    ctx->cert->privatekey.get());
}

int SSL_check_private_key(const SSL *ssl) {
  if (!ssl->config) {
    return 0;
  }
  return ssl_cert_check_private_key(ssl->config->cert.get(),
                                    ssl->config->cert->privatekey.get());
}

long SSL_get_default_timeout(const SSL *ssl) {
  return SSL_DEFAULT_SESSION_TIMEOUT;
}

int SSL_renegotiate(SSL *ssl) {
  // Caller-initiated renegotiation is not supported.
  OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
  return 0;
}

int SSL_renegotiate_pending(SSL *ssl) {
  return SSL_in_init(ssl) && ssl->s3->initial_handshake_complete;
}

int SSL_total_renegotiations(const SSL *ssl) {
  return ssl->s3->total_renegotiations;
}

size_t SSL_CTX_get_max_cert_list(const SSL_CTX *ctx) {
  return ctx->max_cert_list;
}

void SSL_CTX_set_max_cert_list(SSL_CTX *ctx, size_t max_cert_list) {
  if (max_cert_list > kMaxHandshakeSize) {
    max_cert_list = kMaxHandshakeSize;
  }
  ctx->max_cert_list = (uint32_t)max_cert_list;
}

size_t SSL_get_max_cert_list(const SSL *ssl) {
  return ssl->max_cert_list;
}

void SSL_set_max_cert_list(SSL *ssl, size_t max_cert_list) {
  if (max_cert_list > kMaxHandshakeSize) {
    max_cert_list = kMaxHandshakeSize;
  }
  ssl->max_cert_list = (uint32_t)max_cert_list;
}

int SSL_CTX_set_max_send_fragment(SSL_CTX *ctx, size_t max_send_fragment) {
  if (max_send_fragment < 512) {
    max_send_fragment = 512;
  }
  if (max_send_fragment > SSL3_RT_MAX_PLAIN_LENGTH) {
    max_send_fragment = SSL3_RT_MAX_PLAIN_LENGTH;
  }
  ctx->max_send_fragment = (uint16_t)max_send_fragment;

  return 1;
}

int SSL_set_max_send_fragment(SSL *ssl, size_t max_send_fragment) {
  if (max_send_fragment < 512) {
    max_send_fragment = 512;
  }
  if (max_send_fragment > SSL3_RT_MAX_PLAIN_LENGTH) {
    max_send_fragment = SSL3_RT_MAX_PLAIN_LENGTH;
  }
  ssl->max_send_fragment = (uint16_t)max_send_fragment;

  return 1;
}

int SSL_set_mtu(SSL *ssl, unsigned mtu) {
  if (!SSL_is_dtls(ssl) || mtu < dtls1_min_mtu()) {
    return 0;
  }
  ssl->d1->mtu = mtu;
  return 1;
}

int SSL_get_secure_renegotiation_support(const SSL *ssl) {
  if (!ssl->s3->have_version) {
    return 0;
  }
  return ssl_protocol_version(ssl) >= TLS1_3_VERSION ||
         ssl->s3->send_connection_binding;
}

size_t SSL_CTX_sess_number(const SSL_CTX *ctx) {
  MutexReadLock lock(const_cast<CRYPTO_MUTEX *>(&ctx->lock));
  return lh_SSL_SESSION_num_items(ctx->sessions);
}

unsigned long SSL_CTX_sess_set_cache_size(SSL_CTX *ctx, unsigned long size) {
  unsigned long ret = ctx->session_cache_size;
  ctx->session_cache_size = size;
  return ret;
}

unsigned long SSL_CTX_sess_get_cache_size(const SSL_CTX *ctx) {
  return ctx->session_cache_size;
}

int SSL_CTX_set_session_cache_mode(SSL_CTX *ctx, int mode) {
  int ret = ctx->session_cache_mode;
  ctx->session_cache_mode = mode;
  return ret;
}

int SSL_CTX_get_session_cache_mode(const SSL_CTX *ctx) {
  return ctx->session_cache_mode;
}


int SSL_CTX_get_tlsext_ticket_keys(SSL_CTX *ctx, void *out, size_t len) {
  if (out == NULL) {
    return 48;
  }
  if (len != 48) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_TICKET_KEYS_LENGTH);
    return 0;
  }

  // The default ticket keys are initialized lazily. Trigger a key
  // rotation to initialize them.
  if (!ssl_ctx_rotate_ticket_encryption_key(ctx)) {
    return 0;
  }

  uint8_t *out_bytes = reinterpret_cast<uint8_t *>(out);
  MutexReadLock lock(&ctx->lock);
  OPENSSL_memcpy(out_bytes, ctx->ticket_key_current->name, 16);
  OPENSSL_memcpy(out_bytes + 16, ctx->ticket_key_current->hmac_key, 16);
  OPENSSL_memcpy(out_bytes + 32, ctx->ticket_key_current->aes_key, 16);
  return 1;
}

int SSL_CTX_set_tlsext_ticket_keys(SSL_CTX *ctx, const void *in, size_t len) {
  if (in == NULL) {
    return 48;
  }
  if (len != 48) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_TICKET_KEYS_LENGTH);
    return 0;
  }
  auto key = MakeUnique<TicketKey>();
  if (!key) {
    return 0;
  }
  const uint8_t *in_bytes = reinterpret_cast<const uint8_t *>(in);
  OPENSSL_memcpy(key->name, in_bytes, 16);
  OPENSSL_memcpy(key->hmac_key, in_bytes + 16, 16);
  OPENSSL_memcpy(key->aes_key, in_bytes + 32, 16);
  // Disable automatic key rotation for manually-configured keys. This is now
  // the caller's responsibility.
  key->next_rotation_tv_sec = 0;
  ctx->ticket_key_current = std::move(key);
  ctx->ticket_key_prev.reset();
  return 1;
}

int SSL_CTX_set_tlsext_ticket_key_cb(
    SSL_CTX *ctx, int (*callback)(SSL *ssl, uint8_t *key_name, uint8_t *iv,
                                  EVP_CIPHER_CTX *ctx, HMAC_CTX *hmac_ctx,
                                  int encrypt)) {
  ctx->ticket_key_cb = callback;
  return 1;
}

int SSL_CTX_set1_curves(SSL_CTX *ctx, const int *curves, size_t curves_len) {
  return tls1_set_curves(&ctx->supported_group_list,
                         MakeConstSpan(curves, curves_len));
}

int SSL_set1_curves(SSL *ssl, const int *curves, size_t curves_len) {
  if (!ssl->config) {
    return 0;
  }
  return tls1_set_curves(&ssl->config->supported_group_list,
                         MakeConstSpan(curves, curves_len));
}

int SSL_CTX_set1_curves_list(SSL_CTX *ctx, const char *curves) {
  return tls1_set_curves_list(&ctx->supported_group_list, curves);
}

int SSL_set1_curves_list(SSL *ssl, const char *curves) {
  if (!ssl->config) {
    return 0;
  }
  return tls1_set_curves_list(&ssl->config->supported_group_list, curves);
}

uint16_t SSL_get_curve_id(const SSL *ssl) {
  // TODO(davidben): This checks the wrong session if there is a renegotiation
  // in progress.
  SSL_SESSION *session = SSL_get_session(ssl);
  if (session == NULL) {
    return 0;
  }

  return session->group_id;
}

int SSL_CTX_set_tmp_dh(SSL_CTX *ctx, const DH *dh) {
  return 1;
}

int SSL_set_tmp_dh(SSL *ssl, const DH *dh) {
  return 1;
}

STACK_OF(SSL_CIPHER) *SSL_CTX_get_ciphers(const SSL_CTX *ctx) {
  return ctx->cipher_list->ciphers.get();
}

int SSL_CTX_cipher_in_group(const SSL_CTX *ctx, size_t i) {
  if (i >= sk_SSL_CIPHER_num(ctx->cipher_list->ciphers.get())) {
    return 0;
  }
  return ctx->cipher_list->in_group_flags[i];
}

STACK_OF(SSL_CIPHER) *SSL_get_ciphers(const SSL *ssl) {
  if (ssl == NULL) {
    return NULL;
  }
  if (ssl->config == NULL) {
    assert(ssl->config);
    return NULL;
  }

  return ssl->config->cipher_list ? ssl->config->cipher_list->ciphers.get()
      : ssl->ctx->cipher_list->ciphers.get();
}

const char *SSL_get_cipher_list(const SSL *ssl, int n) {
  if (ssl == NULL) {
    return NULL;
  }

  STACK_OF(SSL_CIPHER) *sk = SSL_get_ciphers(ssl);
  if (sk == NULL || n < 0 || (size_t)n >= sk_SSL_CIPHER_num(sk)) {
    return NULL;
  }

  const SSL_CIPHER *c = sk_SSL_CIPHER_value(sk, n);
  if (c == NULL) {
    return NULL;
  }

  return c->name;
}

int SSL_CTX_set_cipher_list(SSL_CTX *ctx, const char *str) {
  return ssl_create_cipher_list(&ctx->cipher_list, str, false /* not strict */);
}

int SSL_CTX_set_strict_cipher_list(SSL_CTX *ctx, const char *str) {
  return ssl_create_cipher_list(&ctx->cipher_list, str, true /* strict */);
}

int SSL_set_cipher_list(SSL *ssl, const char *str) {
  if (!ssl->config) {
    return 0;
  }
  return ssl_create_cipher_list(&ssl->config->cipher_list, str,
                                false /* not strict */);
}

int SSL_set_strict_cipher_list(SSL *ssl, const char *str) {
  if (!ssl->config) {
    return 0;
  }
  return ssl_create_cipher_list(&ssl->config->cipher_list, str,
                                true /* strict */);
}

const char *SSL_get_servername(const SSL *ssl, const int type) {
  if (type != TLSEXT_NAMETYPE_host_name) {
    return NULL;
  }

  // Historically, |SSL_get_servername| was also the configuration getter
  // corresponding to |SSL_set_tlsext_host_name|.
  if (ssl->hostname != nullptr) {
    return ssl->hostname.get();
  }

  return ssl->s3->hostname.get();
}

int SSL_get_servername_type(const SSL *ssl) {
  if (SSL_get_servername(ssl, TLSEXT_NAMETYPE_host_name) == NULL) {
    return -1;
  }
  return TLSEXT_NAMETYPE_host_name;
}

void SSL_CTX_set_custom_verify(
    SSL_CTX *ctx, int mode,
    enum ssl_verify_result_t (*callback)(SSL *ssl, uint8_t *out_alert)) {
  ctx->verify_mode = mode;
  ctx->custom_verify_callback = callback;
}

void SSL_set_custom_verify(
    SSL *ssl, int mode,
    enum ssl_verify_result_t (*callback)(SSL *ssl, uint8_t *out_alert)) {
  if (!ssl->config) {
    return;
  }
  ssl->config->verify_mode = mode;
  ssl->config->custom_verify_callback = callback;
}

void SSL_CTX_enable_signed_cert_timestamps(SSL_CTX *ctx) {
  ctx->signed_cert_timestamps_enabled = true;
}

void SSL_enable_signed_cert_timestamps(SSL *ssl) {
  if (!ssl->config) {
    return;
  }
  ssl->config->signed_cert_timestamps_enabled = true;
}

void SSL_CTX_enable_ocsp_stapling(SSL_CTX *ctx) {
  ctx->ocsp_stapling_enabled = true;
}

void SSL_enable_ocsp_stapling(SSL *ssl) {
  if (!ssl->config) {
    return;
  }
  ssl->config->ocsp_stapling_enabled = true;
}

void SSL_get0_signed_cert_timestamp_list(const SSL *ssl, const uint8_t **out,
                                         size_t *out_len) {
  SSL_SESSION *session = SSL_get_session(ssl);
  if (ssl->server || !session || !session->signed_cert_timestamp_list) {
    *out_len = 0;
    *out = NULL;
    return;
  }

  *out = CRYPTO_BUFFER_data(session->signed_cert_timestamp_list.get());
  *out_len = CRYPTO_BUFFER_len(session->signed_cert_timestamp_list.get());
}

void SSL_get0_ocsp_response(const SSL *ssl, const uint8_t **out,
                            size_t *out_len) {
  SSL_SESSION *session = SSL_get_session(ssl);
  if (ssl->server || !session || !session->ocsp_response) {
    *out_len = 0;
    *out = NULL;
    return;
  }

  *out = CRYPTO_BUFFER_data(session->ocsp_response.get());
  *out_len = CRYPTO_BUFFER_len(session->ocsp_response.get());
}

int SSL_set_tlsext_host_name(SSL *ssl, const char *name) {
  ssl->hostname.reset();
  if (name == nullptr) {
    return 1;
  }

  size_t len = strlen(name);
  if (len == 0 || len > TLSEXT_MAXLEN_host_name) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_SSL3_EXT_INVALID_SERVERNAME);
    return 0;
  }
  ssl->hostname.reset(BUF_strdup(name));
  if (ssl->hostname == nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  return 1;
}

int SSL_CTX_set_tlsext_servername_callback(
    SSL_CTX *ctx, int (*callback)(SSL *ssl, int *out_alert, void *arg)) {
  ctx->servername_callback = callback;
  return 1;
}

int SSL_CTX_set_tlsext_servername_arg(SSL_CTX *ctx, void *arg) {
  ctx->servername_arg = arg;
  return 1;
}

int SSL_select_next_proto(uint8_t **out, uint8_t *out_len, const uint8_t *peer,
                          unsigned peer_len, const uint8_t *supported,
                          unsigned supported_len) {
  const uint8_t *result;
  int status;

  // For each protocol in peer preference order, see if we support it.
  for (unsigned i = 0; i < peer_len;) {
    for (unsigned j = 0; j < supported_len;) {
      if (peer[i] == supported[j] &&
          OPENSSL_memcmp(&peer[i + 1], &supported[j + 1], peer[i]) == 0) {
        // We found a match
        result = &peer[i];
        status = OPENSSL_NPN_NEGOTIATED;
        goto found;
      }
      j += supported[j];
      j++;
    }
    i += peer[i];
    i++;
  }

  // There's no overlap between our protocols and the peer's list.
  result = supported;
  status = OPENSSL_NPN_NO_OVERLAP;

found:
  *out = (uint8_t *)result + 1;
  *out_len = result[0];
  return status;
}

void SSL_get0_next_proto_negotiated(const SSL *ssl, const uint8_t **out_data,
                                    unsigned *out_len) {
  *out_data = ssl->s3->next_proto_negotiated.data();
  *out_len = ssl->s3->next_proto_negotiated.size();
}

void SSL_CTX_set_next_protos_advertised_cb(
    SSL_CTX *ctx,
    int (*cb)(SSL *ssl, const uint8_t **out, unsigned *out_len, void *arg),
    void *arg) {
  ctx->next_protos_advertised_cb = cb;
  ctx->next_protos_advertised_cb_arg = arg;
}

void SSL_CTX_set_next_proto_select_cb(
    SSL_CTX *ctx, int (*cb)(SSL *ssl, uint8_t **out, uint8_t *out_len,
                            const uint8_t *in, unsigned in_len, void *arg),
    void *arg) {
  ctx->next_proto_select_cb = cb;
  ctx->next_proto_select_cb_arg = arg;
}

int SSL_CTX_set_alpn_protos(SSL_CTX *ctx, const uint8_t *protos,
                            unsigned protos_len) {
  // Note this function's calling convention is backwards.
  return ctx->alpn_client_proto_list.CopyFrom(MakeConstSpan(protos, protos_len))
             ? 0
             : 1;
}

int SSL_set_alpn_protos(SSL *ssl, const uint8_t *protos, unsigned protos_len) {
  // Note this function's calling convention is backwards.
  if (!ssl->config) {
    return 1;
  }
  return ssl->config->alpn_client_proto_list.CopyFrom(
             MakeConstSpan(protos, protos_len))
             ? 0
             : 1;
}

void SSL_CTX_set_alpn_select_cb(SSL_CTX *ctx,
                                int (*cb)(SSL *ssl, const uint8_t **out,
                                          uint8_t *out_len, const uint8_t *in,
                                          unsigned in_len, void *arg),
                                void *arg) {
  ctx->alpn_select_cb = cb;
  ctx->alpn_select_cb_arg = arg;
}

void SSL_get0_alpn_selected(const SSL *ssl, const uint8_t **out_data,
                            unsigned *out_len) {
  if (SSL_in_early_data(ssl) && !ssl->server) {
    *out_data = ssl->s3->hs->early_session->early_alpn.data();
    *out_len = ssl->s3->hs->early_session->early_alpn.size();
  } else {
    *out_data = ssl->s3->alpn_selected.data();
    *out_len = ssl->s3->alpn_selected.size();
  }
}

void SSL_CTX_set_allow_unknown_alpn_protos(SSL_CTX *ctx, int enabled) {
  ctx->allow_unknown_alpn_protos = !!enabled;
}

int SSL_CTX_add_cert_compression_alg(SSL_CTX *ctx, uint16_t alg_id,
                                     ssl_cert_compression_func_t compress,
                                     ssl_cert_decompression_func_t decompress) {
  assert(compress != nullptr || decompress != nullptr);

  for (const auto *alg : ctx->cert_compression_algs.get()) {
    if (alg->alg_id == alg_id) {
      return 0;
    }
  }

  UniquePtr<CertCompressionAlg> alg = MakeUnique<CertCompressionAlg>();
  if (alg == nullptr) {
    return 0;
  }

  alg->alg_id = alg_id;
  alg->compress = compress;
  alg->decompress = decompress;

  if (ctx->cert_compression_algs == nullptr) {
    ctx->cert_compression_algs.reset(sk_CertCompressionAlg_new_null());
    if (ctx->cert_compression_algs == nullptr) {
      return 0;
    }
  }

  if (!PushToStack(ctx->cert_compression_algs.get(), std::move(alg))) {
    if (sk_CertCompressionAlg_num(ctx->cert_compression_algs.get()) == 0) {
      ctx->cert_compression_algs.reset();
    }
    return 0;
  }

  return 1;
}

void SSL_CTX_set_tls_channel_id_enabled(SSL_CTX *ctx, int enabled) {
  ctx->channel_id_enabled = !!enabled;
}

int SSL_CTX_enable_tls_channel_id(SSL_CTX *ctx) {
  SSL_CTX_set_tls_channel_id_enabled(ctx, 1);
  return 1;
}

void SSL_set_tls_channel_id_enabled(SSL *ssl, int enabled) {
  if (!ssl->config) {
    return;
  }
  ssl->config->channel_id_enabled = !!enabled;
}

int SSL_enable_tls_channel_id(SSL *ssl) {
  SSL_set_tls_channel_id_enabled(ssl, 1);
  return 1;
}

static int is_p256_key(EVP_PKEY *private_key) {
  const EC_KEY *ec_key = EVP_PKEY_get0_EC_KEY(private_key);
  return ec_key != NULL &&
         EC_GROUP_get_curve_name(EC_KEY_get0_group(ec_key)) ==
             NID_X9_62_prime256v1;
}

int SSL_CTX_set1_tls_channel_id(SSL_CTX *ctx, EVP_PKEY *private_key) {
  if (!is_p256_key(private_key)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_CHANNEL_ID_NOT_P256);
    return 0;
  }

  ctx->channel_id_private = UpRef(private_key);
  ctx->channel_id_enabled = true;

  return 1;
}

int SSL_set1_tls_channel_id(SSL *ssl, EVP_PKEY *private_key) {
  if (!ssl->config) {
    return 0;
  }
  if (!is_p256_key(private_key)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_CHANNEL_ID_NOT_P256);
    return 0;
  }

  ssl->config->channel_id_private = UpRef(private_key);
  ssl->config->channel_id_enabled = true;

  return 1;
}

size_t SSL_get_tls_channel_id(SSL *ssl, uint8_t *out, size_t max_out) {
  if (!ssl->s3->channel_id_valid) {
    return 0;
  }
  OPENSSL_memcpy(out, ssl->s3->channel_id, (max_out < 64) ? max_out : 64);
  return 64;
}

int SSL_set_token_binding_params(SSL *ssl, const uint8_t *params, size_t len) {
  if (!ssl->config) {
    return 0;
  }
  if (len > 256) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_OVERFLOW);
    return 0;
  }
  return ssl->config->token_binding_params.CopyFrom(MakeConstSpan(params, len));
}

int SSL_is_token_binding_negotiated(const SSL *ssl) {
  return ssl->s3->token_binding_negotiated;
}

uint8_t SSL_get_negotiated_token_binding_param(const SSL *ssl) {
  return ssl->s3->negotiated_token_binding_param;
}

size_t SSL_get0_certificate_types(const SSL *ssl, const uint8_t **out_types) {
  Span<const uint8_t> types;
  if (!ssl->server && ssl->s3->hs != nullptr) {
    types = ssl->s3->hs->certificate_types;
  }
  *out_types = types.data();
  return types.size();
}

size_t SSL_get0_peer_verify_algorithms(const SSL *ssl,
                                       const uint16_t **out_sigalgs) {
  Span<const uint16_t> sigalgs;
  if (ssl->s3->hs != nullptr) {
    sigalgs = ssl->s3->hs->peer_sigalgs;
  }
  *out_sigalgs = sigalgs.data();
  return sigalgs.size();
}

EVP_PKEY *SSL_get_privatekey(const SSL *ssl) {
  if (!ssl->config) {
    assert(ssl->config);
    return NULL;
  }
  if (ssl->config->cert != NULL) {
    return ssl->config->cert->privatekey.get();
  }

  return NULL;
}

EVP_PKEY *SSL_CTX_get0_privatekey(const SSL_CTX *ctx) {
  if (ctx->cert != NULL) {
    return ctx->cert->privatekey.get();
  }

  return NULL;
}

const SSL_CIPHER *SSL_get_current_cipher(const SSL *ssl) {
  const SSL_SESSION *session = SSL_get_session(ssl);
  return session == nullptr ? nullptr : session->cipher;
}

int SSL_session_reused(const SSL *ssl) {
  return ssl->s3->session_reused || SSL_in_early_data(ssl);
}

const COMP_METHOD *SSL_get_current_compression(SSL *ssl) { return NULL; }

const COMP_METHOD *SSL_get_current_expansion(SSL *ssl) { return NULL; }

int SSL_get_server_tmp_key(SSL *ssl, EVP_PKEY **out_key) { return 0; }

void SSL_CTX_set_quiet_shutdown(SSL_CTX *ctx, int mode) {
  ctx->quiet_shutdown = (mode != 0);
}

int SSL_CTX_get_quiet_shutdown(const SSL_CTX *ctx) {
  return ctx->quiet_shutdown;
}

void SSL_set_quiet_shutdown(SSL *ssl, int mode) {
  ssl->quiet_shutdown = (mode != 0);
}

int SSL_get_quiet_shutdown(const SSL *ssl) { return ssl->quiet_shutdown; }

void SSL_set_shutdown(SSL *ssl, int mode) {
  // It is an error to clear any bits that have already been set. (We can't try
  // to get a second close_notify or send two.)
  assert((SSL_get_shutdown(ssl) & mode) == SSL_get_shutdown(ssl));

  if (mode & SSL_RECEIVED_SHUTDOWN &&
      ssl->s3->read_shutdown == ssl_shutdown_none) {
    ssl->s3->read_shutdown = ssl_shutdown_close_notify;
  }

  if (mode & SSL_SENT_SHUTDOWN &&
      ssl->s3->write_shutdown == ssl_shutdown_none) {
    ssl->s3->write_shutdown = ssl_shutdown_close_notify;
  }
}

int SSL_get_shutdown(const SSL *ssl) {
  int ret = 0;
  if (ssl->s3->read_shutdown != ssl_shutdown_none) {
    // Historically, OpenSSL set |SSL_RECEIVED_SHUTDOWN| on both close_notify
    // and fatal alert.
    ret |= SSL_RECEIVED_SHUTDOWN;
  }
  if (ssl->s3->write_shutdown == ssl_shutdown_close_notify) {
    // Historically, OpenSSL set |SSL_SENT_SHUTDOWN| on only close_notify.
    ret |= SSL_SENT_SHUTDOWN;
  }
  return ret;
}

SSL_CTX *SSL_get_SSL_CTX(const SSL *ssl) { return ssl->ctx.get(); }

SSL_CTX *SSL_set_SSL_CTX(SSL *ssl, SSL_CTX *ctx) {
  if (!ssl->config) {
    return NULL;
  }
  if (ssl->ctx.get() == ctx) {
    return ssl->ctx.get();
  }

  // One cannot change the X.509 callbacks during a connection.
  if (ssl->ctx->x509_method != ctx->x509_method) {
    assert(0);
    return NULL;
  }

  UniquePtr<CERT> new_cert = ssl_cert_dup(ctx->cert.get());
  if (!new_cert) {
    return nullptr;
  }

  ssl->config->cert = std::move(new_cert);
  ssl->ctx = UpRef(ctx);
  ssl->enable_early_data = ssl->ctx->enable_early_data;

  return ssl->ctx.get();
}

void SSL_set_info_callback(SSL *ssl,
                           void (*cb)(const SSL *ssl, int type, int value)) {
  ssl->info_callback = cb;
}

void (*SSL_get_info_callback(const SSL *ssl))(const SSL *ssl, int type,
                                              int value) {
  return ssl->info_callback;
}

int SSL_state(const SSL *ssl) {
  return SSL_in_init(ssl) ? SSL_ST_INIT : SSL_ST_OK;
}

void SSL_set_state(SSL *ssl, int state) { }

char *SSL_get_shared_ciphers(const SSL *ssl, char *buf, int len) {
  if (len <= 0) {
    return NULL;
  }
  buf[0] = '\0';
  return buf;
}

int SSL_CTX_set_quic_method(SSL_CTX *ctx, const SSL_QUIC_METHOD *quic_method) {
  if (ctx->method->is_dtls) {
    return 0;
  }
  ctx->quic_method = quic_method;
  return 1;
}

int SSL_set_quic_method(SSL *ssl, const SSL_QUIC_METHOD *quic_method) {
  if (ssl->method->is_dtls) {
    return 0;
  }
  ssl->quic_method = quic_method;
  return 1;
}

int SSL_get_ex_new_index(long argl, void *argp, CRYPTO_EX_unused *unused,
                         CRYPTO_EX_dup *dup_unused, CRYPTO_EX_free *free_func) {
  int index;
  if (!CRYPTO_get_ex_new_index(&g_ex_data_class_ssl, &index, argl, argp,
                               free_func)) {
    return -1;
  }
  return index;
}

int SSL_set_ex_data(SSL *ssl, int idx, void *data) {
  return CRYPTO_set_ex_data(&ssl->ex_data, idx, data);
}

void *SSL_get_ex_data(const SSL *ssl, int idx) {
  return CRYPTO_get_ex_data(&ssl->ex_data, idx);
}

int SSL_CTX_get_ex_new_index(long argl, void *argp, CRYPTO_EX_unused *unused,
                             CRYPTO_EX_dup *dup_unused,
                             CRYPTO_EX_free *free_func) {
  int index;
  if (!CRYPTO_get_ex_new_index(&g_ex_data_class_ssl_ctx, &index, argl, argp,
                               free_func)) {
    return -1;
  }
  return index;
}

int SSL_CTX_set_ex_data(SSL_CTX *ctx, int idx, void *data) {
  return CRYPTO_set_ex_data(&ctx->ex_data, idx, data);
}

void *SSL_CTX_get_ex_data(const SSL_CTX *ctx, int idx) {
  return CRYPTO_get_ex_data(&ctx->ex_data, idx);
}

int SSL_want(const SSL *ssl) { return ssl->s3->rwstate; }

void SSL_CTX_set_tmp_rsa_callback(SSL_CTX *ctx,
                                  RSA *(*cb)(SSL *ssl, int is_export,
                                             int keylength)) {}

void SSL_set_tmp_rsa_callback(SSL *ssl, RSA *(*cb)(SSL *ssl, int is_export,
                                                   int keylength)) {}

void SSL_CTX_set_tmp_dh_callback(SSL_CTX *ctx,
                                 DH *(*cb)(SSL *ssl, int is_export,
                                           int keylength)) {}

void SSL_set_tmp_dh_callback(SSL *ssl, DH *(*cb)(SSL *ssl, int is_export,
                                                 int keylength)) {}

static int use_psk_identity_hint(UniquePtr<char> *out,
                                 const char *identity_hint) {
  if (identity_hint != NULL && strlen(identity_hint) > PSK_MAX_IDENTITY_LEN) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DATA_LENGTH_TOO_LONG);
    return 0;
  }

  // Clear currently configured hint, if any.
  out->reset();

  // Treat the empty hint as not supplying one. Plain PSK makes it possible to
  // send either no hint (omit ServerKeyExchange) or an empty hint, while
  // ECDHE_PSK can only spell empty hint. Having different capabilities is odd,
  // so we interpret empty and missing as identical.
  if (identity_hint != NULL && identity_hint[0] != '\0') {
    out->reset(BUF_strdup(identity_hint));
    if (*out == nullptr) {
      return 0;
    }
  }

  return 1;
}

int SSL_CTX_use_psk_identity_hint(SSL_CTX *ctx, const char *identity_hint) {
  return use_psk_identity_hint(&ctx->psk_identity_hint, identity_hint);
}

int SSL_use_psk_identity_hint(SSL *ssl, const char *identity_hint) {
  if (!ssl->config) {
    return 0;
  }
  return use_psk_identity_hint(&ssl->config->psk_identity_hint, identity_hint);
}

const char *SSL_get_psk_identity_hint(const SSL *ssl) {
  if (ssl == NULL) {
    return NULL;
  }
  if (ssl->config == NULL) {
    assert(ssl->config);
    return NULL;
  }
  return ssl->config->psk_identity_hint.get();
}

const char *SSL_get_psk_identity(const SSL *ssl) {
  if (ssl == NULL) {
    return NULL;
  }
  SSL_SESSION *session = SSL_get_session(ssl);
  if (session == NULL) {
    return NULL;
  }
  return session->psk_identity.get();
}

void SSL_set_psk_client_callback(
    SSL *ssl, unsigned (*cb)(SSL *ssl, const char *hint, char *identity,
                             unsigned max_identity_len, uint8_t *psk,
                             unsigned max_psk_len)) {
  if (!ssl->config) {
    return;
  }
  ssl->config->psk_client_callback = cb;
}

void SSL_CTX_set_psk_client_callback(
    SSL_CTX *ctx, unsigned (*cb)(SSL *ssl, const char *hint, char *identity,
                                 unsigned max_identity_len, uint8_t *psk,
                                 unsigned max_psk_len)) {
  ctx->psk_client_callback = cb;
}

void SSL_set_psk_server_callback(
    SSL *ssl, unsigned (*cb)(SSL *ssl, const char *identity, uint8_t *psk,
                             unsigned max_psk_len)) {
  if (!ssl->config) {
    return;
  }
  ssl->config->psk_server_callback = cb;
}

void SSL_CTX_set_psk_server_callback(
    SSL_CTX *ctx, unsigned (*cb)(SSL *ssl, const char *identity,
                                 uint8_t *psk, unsigned max_psk_len)) {
  ctx->psk_server_callback = cb;
}

void SSL_CTX_set_msg_callback(SSL_CTX *ctx,
                              void (*cb)(int write_p, int version,
                                         int content_type, const void *buf,
                                         size_t len, SSL *ssl, void *arg)) {
  ctx->msg_callback = cb;
}

void SSL_CTX_set_msg_callback_arg(SSL_CTX *ctx, void *arg) {
  ctx->msg_callback_arg = arg;
}

void SSL_set_msg_callback(SSL *ssl,
                          void (*cb)(int write_p, int version, int content_type,
                                     const void *buf, size_t len, SSL *ssl,
                                     void *arg)) {
  ssl->msg_callback = cb;
}

void SSL_set_msg_callback_arg(SSL *ssl, void *arg) {
  ssl->msg_callback_arg = arg;
}

void SSL_CTX_set_keylog_callback(SSL_CTX *ctx,
                                 void (*cb)(const SSL *ssl, const char *line)) {
  ctx->keylog_callback = cb;
}

void (*SSL_CTX_get_keylog_callback(const SSL_CTX *ctx))(const SSL *ssl,
                                                        const char *line) {
  return ctx->keylog_callback;
}

void SSL_CTX_set_current_time_cb(SSL_CTX *ctx,
                                 void (*cb)(const SSL *ssl,
                                            struct timeval *out_clock)) {
  ctx->current_time_cb = cb;
}

int SSL_is_init_finished(const SSL *ssl) {
  return !SSL_in_init(ssl);
}

int SSL_in_init(const SSL *ssl) {
  // This returns false once all the handshake state has been finalized, to
  // allow callbacks and getters based on SSL_in_init to return the correct
  // values.
  SSL_HANDSHAKE *hs = ssl->s3->hs.get();
  return hs != nullptr && !hs->handshake_finalized;
}

int SSL_in_false_start(const SSL *ssl) {
  if (ssl->s3->hs == NULL) {
    return 0;
  }
  return ssl->s3->hs->in_false_start;
}

int SSL_cutthrough_complete(const SSL *ssl) {
  return SSL_in_false_start(ssl);
}

int SSL_is_server(const SSL *ssl) { return ssl->server; }

int SSL_is_dtls(const SSL *ssl) { return ssl->method->is_dtls; }

void SSL_CTX_set_select_certificate_cb(
    SSL_CTX *ctx,
    enum ssl_select_cert_result_t (*cb)(const SSL_CLIENT_HELLO *)) {
  ctx->select_certificate_cb = cb;
}

void SSL_CTX_set_dos_protection_cb(SSL_CTX *ctx,
                                   int (*cb)(const SSL_CLIENT_HELLO *)) {
  ctx->dos_protection_cb = cb;
}

void SSL_CTX_set_reverify_on_resume(SSL_CTX *ctx, int enabled) {
  ctx->reverify_on_resume = !!enabled;
}

void SSL_set_enforce_rsa_key_usage(SSL *ssl, int enabled) {
  if (!ssl->config) {
    return;
  }
  ssl->config->enforce_rsa_key_usage = !!enabled;
}

void SSL_set_renegotiate_mode(SSL *ssl, enum ssl_renegotiate_mode_t mode) {
  ssl->renegotiate_mode = mode;

  // Check if |ssl_can_renegotiate| has changed and the configuration may now be
  // shed. HTTP clients may initially allow renegotiation for HTTP/1.1, and then
  // disable after the handshake once the ALPN protocol is known to be HTTP/2.
  ssl_maybe_shed_handshake_config(ssl);
}

int SSL_get_ivs(const SSL *ssl, const uint8_t **out_read_iv,
                const uint8_t **out_write_iv, size_t *out_iv_len) {
  size_t write_iv_len;
  if (!ssl->s3->aead_read_ctx->GetIV(out_read_iv, out_iv_len) ||
      !ssl->s3->aead_write_ctx->GetIV(out_write_iv, &write_iv_len) ||
      *out_iv_len != write_iv_len) {
    return 0;
  }

  return 1;
}

static uint64_t be_to_u64(const uint8_t in[8]) {
  return (((uint64_t)in[0]) << 56) | (((uint64_t)in[1]) << 48) |
         (((uint64_t)in[2]) << 40) | (((uint64_t)in[3]) << 32) |
         (((uint64_t)in[4]) << 24) | (((uint64_t)in[5]) << 16) |
         (((uint64_t)in[6]) << 8) | ((uint64_t)in[7]);
}

uint64_t SSL_get_read_sequence(const SSL *ssl) {
  // TODO(davidben): Internally represent sequence numbers as uint64_t.
  if (SSL_is_dtls(ssl)) {
    // max_seq_num already includes the epoch.
    assert(ssl->d1->r_epoch == (ssl->d1->bitmap.max_seq_num >> 48));
    return ssl->d1->bitmap.max_seq_num;
  }
  return be_to_u64(ssl->s3->read_sequence);
}

uint64_t SSL_get_write_sequence(const SSL *ssl) {
  uint64_t ret = be_to_u64(ssl->s3->write_sequence);
  if (SSL_is_dtls(ssl)) {
    assert((ret >> 48) == 0);
    ret |= ((uint64_t)ssl->d1->w_epoch) << 48;
  }
  return ret;
}

uint16_t SSL_get_peer_signature_algorithm(const SSL *ssl) {
  // TODO(davidben): This checks the wrong session if there is a renegotiation
  // in progress.
  SSL_SESSION *session = SSL_get_session(ssl);
  if (session == NULL) {
    return 0;
  }

  return session->peer_signature_algorithm;
}

size_t SSL_get_client_random(const SSL *ssl, uint8_t *out, size_t max_out) {
  if (max_out == 0) {
    return sizeof(ssl->s3->client_random);
  }
  if (max_out > sizeof(ssl->s3->client_random)) {
    max_out = sizeof(ssl->s3->client_random);
  }
  OPENSSL_memcpy(out, ssl->s3->client_random, max_out);
  return max_out;
}

size_t SSL_get_server_random(const SSL *ssl, uint8_t *out, size_t max_out) {
  if (max_out == 0) {
    return sizeof(ssl->s3->server_random);
  }
  if (max_out > sizeof(ssl->s3->server_random)) {
    max_out = sizeof(ssl->s3->server_random);
  }
  OPENSSL_memcpy(out, ssl->s3->server_random, max_out);
  return max_out;
}

const SSL_CIPHER *SSL_get_pending_cipher(const SSL *ssl) {
  SSL_HANDSHAKE *hs = ssl->s3->hs.get();
  if (hs == NULL) {
    return NULL;
  }
  return hs->new_cipher;
}

void SSL_set_retain_only_sha256_of_client_certs(SSL *ssl, int enabled) {
  if (!ssl->config) {
    return;
  }
  ssl->config->retain_only_sha256_of_client_certs = !!enabled;
}

void SSL_CTX_set_retain_only_sha256_of_client_certs(SSL_CTX *ctx, int enabled) {
  ctx->retain_only_sha256_of_client_certs = !!enabled;
}

void SSL_CTX_set_grease_enabled(SSL_CTX *ctx, int enabled) {
  ctx->grease_enabled = !!enabled;
}

int32_t SSL_get_ticket_age_skew(const SSL *ssl) {
  return ssl->s3->ticket_age_skew;
}

void SSL_CTX_set_false_start_allowed_without_alpn(SSL_CTX *ctx, int allowed) {
  ctx->false_start_allowed_without_alpn = !!allowed;
}

int SSL_is_tls13_downgrade(const SSL *ssl) { return ssl->s3->tls13_downgrade; }

void SSL_CTX_set_ignore_tls13_downgrade(SSL_CTX *ctx, int ignore) {
  ctx->ignore_tls13_downgrade = !!ignore;
}

void SSL_set_ignore_tls13_downgrade(SSL *ssl, int ignore) {
  if (!ssl->config) {
    return;
  }
  ssl->config->ignore_tls13_downgrade = !!ignore;
}

void SSL_set_shed_handshake_config(SSL *ssl, int enable) {
  if (!ssl->config) {
    return;
  }
  ssl->config->shed_handshake_config = !!enable;
}

void SSL_set_jdk11_workaround(SSL *ssl, int enable) {
  if (!ssl->config) {
    return;
  }
  ssl->config->jdk11_workaround = !!enable;
}

int SSL_clear(SSL *ssl) {
  if (!ssl->config) {
    return 0;  // SSL_clear may not be used after shedding config.
  }

  // In OpenSSL, reusing a client |SSL| with |SSL_clear| causes the previously
  // established session to be offered the next time around. wpa_supplicant
  // depends on this behavior, so emulate it.
  UniquePtr<SSL_SESSION> session;
  if (!ssl->server && ssl->s3->established_session != NULL) {
    session = UpRef(ssl->s3->established_session);
  }

  // The ssl->d1->mtu is simultaneously configuration (preserved across
  // clear) and connection-specific state (gets reset).
  //
  // TODO(davidben): Avoid this.
  unsigned mtu = 0;
  if (ssl->d1 != NULL) {
    mtu = ssl->d1->mtu;
  }

  ssl->method->ssl_free(ssl);
  if (!ssl->method->ssl_new(ssl)) {
    return 0;
  }

  if (SSL_is_dtls(ssl) && (SSL_get_options(ssl) & SSL_OP_NO_QUERY_MTU)) {
    ssl->d1->mtu = mtu;
  }

  if (session != nullptr) {
    SSL_set_session(ssl, session.get());
  }

  return 1;
}

int SSL_CTX_sess_connect(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_connect_good(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_connect_renegotiate(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_accept(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_accept_renegotiate(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_accept_good(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_hits(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_cb_hits(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_misses(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_timeouts(const SSL_CTX *ctx) { return 0; }
int SSL_CTX_sess_cache_full(const SSL_CTX *ctx) { return 0; }

int SSL_num_renegotiations(const SSL *ssl) {
  return SSL_total_renegotiations(ssl);
}

int SSL_CTX_need_tmp_RSA(const SSL_CTX *ctx) { return 0; }
int SSL_need_tmp_RSA(const SSL *ssl) { return 0; }
int SSL_CTX_set_tmp_rsa(SSL_CTX *ctx, const RSA *rsa) { return 1; }
int SSL_set_tmp_rsa(SSL *ssl, const RSA *rsa) { return 1; }
void ERR_load_SSL_strings(void) {}
void SSL_load_error_strings(void) {}
int SSL_cache_hit(SSL *ssl) { return SSL_session_reused(ssl); }

int SSL_CTX_set_tmp_ecdh(SSL_CTX *ctx, const EC_KEY *ec_key) {
  if (ec_key == NULL || EC_KEY_get0_group(ec_key) == NULL) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  int nid = EC_GROUP_get_curve_name(EC_KEY_get0_group(ec_key));
  return SSL_CTX_set1_curves(ctx, &nid, 1);
}

int SSL_set_tmp_ecdh(SSL *ssl, const EC_KEY *ec_key) {
  if (ec_key == NULL || EC_KEY_get0_group(ec_key) == NULL) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  int nid = EC_GROUP_get_curve_name(EC_KEY_get0_group(ec_key));
  return SSL_set1_curves(ssl, &nid, 1);
}

void SSL_CTX_set_ticket_aead_method(SSL_CTX *ctx,
                                    const SSL_TICKET_AEAD_METHOD *aead_method) {
  ctx->ticket_aead_method = aead_method;
}

int SSL_set_tlsext_status_type(SSL *ssl, int type) {
  if (!ssl->config) {
    return 0;
  }
  ssl->config->ocsp_stapling_enabled = type == TLSEXT_STATUSTYPE_ocsp;
  return 1;
}

int SSL_get_tlsext_status_type(const SSL *ssl) {
  if (ssl->server) {
    SSL_HANDSHAKE *hs = ssl->s3->hs.get();
    return hs != nullptr && hs->ocsp_stapling_requested
        ? TLSEXT_STATUSTYPE_ocsp
        : TLSEXT_STATUSTYPE_nothing;
  }

  return ssl->config != nullptr && ssl->config->ocsp_stapling_enabled
             ? TLSEXT_STATUSTYPE_ocsp
             : TLSEXT_STATUSTYPE_nothing;
}

int SSL_set_tlsext_status_ocsp_resp(SSL *ssl, uint8_t *resp, size_t resp_len) {
  if (SSL_set_ocsp_response(ssl, resp, resp_len)) {
    OPENSSL_free(resp);
    return 1;
  }
  return 0;
}

size_t SSL_get_tlsext_status_ocsp_resp(const SSL *ssl, const uint8_t **out) {
  size_t ret;
  SSL_get0_ocsp_response(ssl, out, &ret);
  return ret;
}

int SSL_CTX_set_tlsext_status_cb(SSL_CTX *ctx,
                                 int (*callback)(SSL *ssl, void *arg)) {
  ctx->legacy_ocsp_callback = callback;
  return 1;
}

int SSL_CTX_set_tlsext_status_arg(SSL_CTX *ctx, void *arg) {
  ctx->legacy_ocsp_callback_arg = arg;
  return 1;
}
