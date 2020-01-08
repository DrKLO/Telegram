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
 * [including the GNU Public Licence.] */

#include <openssl/ssl.h>

#include <assert.h>
#include <string.h>

#include <openssl/buf.h>

#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

static void ssl3_on_handshake_complete(SSL *ssl) {
  // The handshake should have released its final message.
  assert(!ssl->s3->has_message);

  // During the handshake, |hs_buf| is retained. Release if it there is no
  // excess in it. There may be excess left if there server sent Finished and
  // HelloRequest in the same record.
  //
  // TODO(davidben): SChannel does not support this. Reject this case.
  if (ssl->s3->hs_buf && ssl->s3->hs_buf->length == 0) {
    ssl->s3->hs_buf.reset();
  }
}

static bool ssl3_set_read_state(SSL *ssl, UniquePtr<SSLAEADContext> aead_ctx) {
  // Cipher changes are forbidden if the current epoch has leftover data.
  if (tls_has_unprocessed_handshake_data(ssl)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_BUFFERED_MESSAGES_ON_CIPHER_CHANGE);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_UNEXPECTED_MESSAGE);
    return false;
  }

  OPENSSL_memset(ssl->s3->read_sequence, 0, sizeof(ssl->s3->read_sequence));
  ssl->s3->aead_read_ctx = std::move(aead_ctx);
  return true;
}

static bool ssl3_set_write_state(SSL *ssl, UniquePtr<SSLAEADContext> aead_ctx) {
  if (!tls_flush_pending_hs_data(ssl)) {
    return false;
  }

  OPENSSL_memset(ssl->s3->write_sequence, 0, sizeof(ssl->s3->write_sequence));
  ssl->s3->aead_write_ctx = std::move(aead_ctx);
  return true;
}

static const SSL_PROTOCOL_METHOD kTLSProtocolMethod = {
    false /* is_dtls */,
    ssl3_new,
    ssl3_free,
    ssl3_get_message,
    ssl3_next_message,
    ssl3_open_handshake,
    ssl3_open_change_cipher_spec,
    ssl3_open_app_data,
    ssl3_write_app_data,
    ssl3_dispatch_alert,
    ssl3_init_message,
    ssl3_finish_message,
    ssl3_add_message,
    ssl3_add_change_cipher_spec,
    ssl3_flush_flight,
    ssl3_on_handshake_complete,
    ssl3_set_read_state,
    ssl3_set_write_state,
};

static bool ssl_noop_x509_check_client_CA_names(
    STACK_OF(CRYPTO_BUFFER) *names) {
  return true;
}

static void ssl_noop_x509_clear(CERT *cert) {}
static void ssl_noop_x509_free(CERT *cert) {}
static void ssl_noop_x509_dup(CERT *new_cert, const CERT *cert) {}
static void ssl_noop_x509_flush_cached_leaf(CERT *cert) {}
static void ssl_noop_x509_flush_cached_chain(CERT *cert) {}
static bool ssl_noop_x509_session_cache_objects(SSL_SESSION *sess) {
  return true;
}
static bool ssl_noop_x509_session_dup(SSL_SESSION *new_session,
                                      const SSL_SESSION *session) {
  return true;
}
static void ssl_noop_x509_session_clear(SSL_SESSION *session) {}
static bool ssl_noop_x509_session_verify_cert_chain(SSL_SESSION *session,
                                                    SSL_HANDSHAKE *hs,
                                                    uint8_t *out_alert) {
  return false;
}

static void ssl_noop_x509_hs_flush_cached_ca_names(SSL_HANDSHAKE *hs) {}
static bool ssl_noop_x509_ssl_new(SSL_HANDSHAKE *hs) { return true; }
static void ssl_noop_x509_ssl_config_free(SSL_CONFIG *cfg) {}
static void ssl_noop_x509_ssl_flush_cached_client_CA(SSL_CONFIG *cfg) {}
static bool ssl_noop_x509_ssl_auto_chain_if_needed(SSL_HANDSHAKE *hs) {
  return true;
}
static bool ssl_noop_x509_ssl_ctx_new(SSL_CTX *ctx) { return true; }
static void ssl_noop_x509_ssl_ctx_free(SSL_CTX *ctx) {}
static void ssl_noop_x509_ssl_ctx_flush_cached_client_CA(SSL_CTX *ctx) {}

const SSL_X509_METHOD ssl_noop_x509_method = {
  ssl_noop_x509_check_client_CA_names,
  ssl_noop_x509_clear,
  ssl_noop_x509_free,
  ssl_noop_x509_dup,
  ssl_noop_x509_flush_cached_chain,
  ssl_noop_x509_flush_cached_leaf,
  ssl_noop_x509_session_cache_objects,
  ssl_noop_x509_session_dup,
  ssl_noop_x509_session_clear,
  ssl_noop_x509_session_verify_cert_chain,
  ssl_noop_x509_hs_flush_cached_ca_names,
  ssl_noop_x509_ssl_new,
  ssl_noop_x509_ssl_config_free,
  ssl_noop_x509_ssl_flush_cached_client_CA,
  ssl_noop_x509_ssl_auto_chain_if_needed,
  ssl_noop_x509_ssl_ctx_new,
  ssl_noop_x509_ssl_ctx_free,
  ssl_noop_x509_ssl_ctx_flush_cached_client_CA,
};

BSSL_NAMESPACE_END

using namespace bssl;

const SSL_METHOD *TLS_method(void) {
  static const SSL_METHOD kMethod = {
      0,
      &kTLSProtocolMethod,
      &ssl_crypto_x509_method,
  };
  return &kMethod;
}

const SSL_METHOD *SSLv23_method(void) {
  return TLS_method();
}

const SSL_METHOD *TLS_with_buffers_method(void) {
  static const SSL_METHOD kMethod = {
      0,
      &kTLSProtocolMethod,
      &ssl_noop_x509_method,
  };
  return &kMethod;
}

// Legacy version-locked methods.

const SSL_METHOD *TLSv1_2_method(void) {
  static const SSL_METHOD kMethod = {
      TLS1_2_VERSION,
      &kTLSProtocolMethod,
      &ssl_crypto_x509_method,
  };
  return &kMethod;
}

const SSL_METHOD *TLSv1_1_method(void) {
  static const SSL_METHOD kMethod = {
      TLS1_1_VERSION,
      &kTLSProtocolMethod,
      &ssl_crypto_x509_method,
  };
  return &kMethod;
}

const SSL_METHOD *TLSv1_method(void) {
  static const SSL_METHOD kMethod = {
      TLS1_VERSION,
      &kTLSProtocolMethod,
      &ssl_crypto_x509_method,
  };
  return &kMethod;
}

// Legacy side-specific methods.

const SSL_METHOD *TLSv1_2_server_method(void) {
  return TLSv1_2_method();
}

const SSL_METHOD *TLSv1_1_server_method(void) {
  return TLSv1_1_method();
}

const SSL_METHOD *TLSv1_server_method(void) {
  return TLSv1_method();
}

const SSL_METHOD *TLSv1_2_client_method(void) {
  return TLSv1_2_method();
}

const SSL_METHOD *TLSv1_1_client_method(void) {
  return TLSv1_1_method();
}

const SSL_METHOD *TLSv1_client_method(void) {
  return TLSv1_method();
}

const SSL_METHOD *SSLv23_server_method(void) {
  return SSLv23_method();
}

const SSL_METHOD *SSLv23_client_method(void) {
  return SSLv23_method();
}

const SSL_METHOD *TLS_server_method(void) {
  return TLS_method();
}

const SSL_METHOD *TLS_client_method(void) {
  return TLS_method();
}
