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
 * Copyright (c) 1998-2002 The OpenSSL Project.  All rights reserved.
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
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/ssl.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/mem.h>
#include <openssl/rand.h>

#include "../crypto/err/internal.h"
#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

static int do_ssl3_write(SSL *ssl, int type, const uint8_t *in, unsigned len);

int ssl3_write_app_data(SSL *ssl, bool *out_needs_handshake, const uint8_t *in,
                        int len) {
  assert(ssl_can_write(ssl));
  assert(!ssl->s3->aead_write_ctx->is_null_cipher());

  *out_needs_handshake = false;

  if (ssl->s3->write_shutdown != ssl_shutdown_none) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_PROTOCOL_IS_SHUTDOWN);
    return -1;
  }

  unsigned tot, n, nw;

  assert(ssl->s3->wnum <= INT_MAX);
  tot = ssl->s3->wnum;
  ssl->s3->wnum = 0;

  // Ensure that if we end up with a smaller value of data to write out than
  // the the original len from a write which didn't complete for non-blocking
  // I/O and also somehow ended up avoiding the check for this in
  // ssl3_write_pending/SSL_R_BAD_WRITE_RETRY as it must never be possible to
  // end up with (len-tot) as a large number that will then promptly send
  // beyond the end of the users buffer ... so we trap and report the error in
  // a way the user will notice.
  if (len < 0 || (size_t)len < tot) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_BAD_LENGTH);
    return -1;
  }

  const int is_early_data_write =
      !ssl->server && SSL_in_early_data(ssl) && ssl->s3->hs->can_early_write;

  n = len - tot;
  for (;;) {
    // max contains the maximum number of bytes that we can put into a record.
    unsigned max = ssl->max_send_fragment;
    if (is_early_data_write &&
        max > ssl->session->ticket_max_early_data -
                  ssl->s3->hs->early_data_written) {
      max =
          ssl->session->ticket_max_early_data - ssl->s3->hs->early_data_written;
      if (max == 0) {
        ssl->s3->wnum = tot;
        ssl->s3->hs->can_early_write = false;
        *out_needs_handshake = true;
        return -1;
      }
    }

    if (n > max) {
      nw = max;
    } else {
      nw = n;
    }

    int ret = do_ssl3_write(ssl, SSL3_RT_APPLICATION_DATA, &in[tot], nw);
    if (ret <= 0) {
      ssl->s3->wnum = tot;
      return ret;
    }

    if (is_early_data_write) {
      ssl->s3->hs->early_data_written += ret;
    }

    if (ret == (int)n || (ssl->mode & SSL_MODE_ENABLE_PARTIAL_WRITE)) {
      return tot + ret;
    }

    n -= ret;
    tot += ret;
  }
}

static int ssl3_write_pending(SSL *ssl, int type, const uint8_t *in,
                              unsigned int len) {
  if (ssl->s3->wpend_tot > (int)len ||
      (!(ssl->mode & SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER) &&
       ssl->s3->wpend_buf != in) ||
      ssl->s3->wpend_type != type) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_BAD_WRITE_RETRY);
    return -1;
  }

  int ret = ssl_write_buffer_flush(ssl);
  if (ret <= 0) {
    return ret;
  }
  ssl->s3->wpend_pending = false;
  return ssl->s3->wpend_ret;
}

// do_ssl3_write writes an SSL record of the given type.
static int do_ssl3_write(SSL *ssl, int type, const uint8_t *in, unsigned len) {
  // If there is still data from the previous record, flush it.
  if (ssl->s3->wpend_pending) {
    return ssl3_write_pending(ssl, type, in, len);
  }

  SSLBuffer *buf = &ssl->s3->write_buffer;
  if (len > SSL3_RT_MAX_PLAIN_LENGTH || buf->size() > 0) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return -1;
  }

  if (!tls_flush_pending_hs_data(ssl)) {
    return -1;
  }

  size_t flight_len = 0;
  if (ssl->s3->pending_flight != nullptr) {
    flight_len =
        ssl->s3->pending_flight->length - ssl->s3->pending_flight_offset;
  }

  size_t max_out = flight_len;
  if (len > 0) {
    const size_t max_ciphertext_len = len + SSL_max_seal_overhead(ssl);
    if (max_ciphertext_len < len || max_out + max_ciphertext_len < max_out) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_OVERFLOW);
      return -1;
    }
    max_out += max_ciphertext_len;
  }

  if (max_out == 0) {
    return 0;
  }

  if (!buf->EnsureCap(flight_len + ssl_seal_align_prefix_len(ssl), max_out)) {
    return -1;
  }

  // Add any unflushed handshake data as a prefix. This may be a KeyUpdate
  // acknowledgment or 0-RTT key change messages. |pending_flight| must be clear
  // when data is added to |write_buffer| or it will be written in the wrong
  // order.
  if (ssl->s3->pending_flight != nullptr) {
    OPENSSL_memcpy(
        buf->remaining().data(),
        ssl->s3->pending_flight->data + ssl->s3->pending_flight_offset,
        flight_len);
    ssl->s3->pending_flight.reset();
    ssl->s3->pending_flight_offset = 0;
    buf->DidWrite(flight_len);
  }

  if (len > 0) {
    size_t ciphertext_len;
    if (!tls_seal_record(ssl, buf->remaining().data(), &ciphertext_len,
                         buf->remaining().size(), type, in, len)) {
      return -1;
    }
    buf->DidWrite(ciphertext_len);
  }

  // Now that we've made progress on the connection, uncork KeyUpdate
  // acknowledgments.
  ssl->s3->key_update_pending = false;

  // Memorize arguments so that ssl3_write_pending can detect bad write retries
  // later.
  ssl->s3->wpend_tot = len;
  ssl->s3->wpend_buf = in;
  ssl->s3->wpend_type = type;
  ssl->s3->wpend_ret = len;
  ssl->s3->wpend_pending = true;

  // We now just need to write the buffer.
  return ssl3_write_pending(ssl, type, in, len);
}

ssl_open_record_t ssl3_open_app_data(SSL *ssl, Span<uint8_t> *out,
                                     size_t *out_consumed, uint8_t *out_alert,
                                     Span<uint8_t> in) {
  assert(ssl_can_read(ssl));
  assert(!ssl->s3->aead_read_ctx->is_null_cipher());

  uint8_t type;
  Span<uint8_t> body;
  auto ret = tls_open_record(ssl, &type, &body, out_consumed, out_alert, in);
  if (ret != ssl_open_record_success) {
    return ret;
  }

  const bool is_early_data_read = ssl->server && SSL_in_early_data(ssl);

  if (type == SSL3_RT_HANDSHAKE) {
    // Post-handshake data prior to TLS 1.3 is always renegotiation, which we
    // never accept as a server. Otherwise |ssl3_get_message| will send
    // |SSL_R_EXCESSIVE_MESSAGE_SIZE|.
    if (ssl->server && ssl_protocol_version(ssl) < TLS1_3_VERSION) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_NO_RENEGOTIATION);
      *out_alert = SSL_AD_NO_RENEGOTIATION;
      return ssl_open_record_error;
    }

    if (!tls_append_handshake_data(ssl, body)) {
      *out_alert = SSL_AD_INTERNAL_ERROR;
      return ssl_open_record_error;
    }
    return ssl_open_record_discard;
  }

  if (type != SSL3_RT_APPLICATION_DATA) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNEXPECTED_RECORD);
    *out_alert = SSL_AD_UNEXPECTED_MESSAGE;
    return ssl_open_record_error;
  }

  if (is_early_data_read) {
    if (body.size() > kMaxEarlyDataAccepted - ssl->s3->hs->early_data_read) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_TOO_MUCH_READ_EARLY_DATA);
      *out_alert = SSL3_AD_UNEXPECTED_MESSAGE;
      return ssl_open_record_error;
    }

    ssl->s3->hs->early_data_read += body.size();
  }

  if (body.empty()) {
    return ssl_open_record_discard;
  }

  *out = body;
  return ssl_open_record_success;
}

ssl_open_record_t ssl3_open_change_cipher_spec(SSL *ssl, size_t *out_consumed,
                                               uint8_t *out_alert,
                                               Span<uint8_t> in) {
  uint8_t type;
  Span<uint8_t> body;
  auto ret = tls_open_record(ssl, &type, &body, out_consumed, out_alert, in);
  if (ret != ssl_open_record_success) {
    return ret;
  }

  if (type != SSL3_RT_CHANGE_CIPHER_SPEC) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNEXPECTED_RECORD);
    *out_alert = SSL_AD_UNEXPECTED_MESSAGE;
    return ssl_open_record_error;
  }

  if (body.size() != 1 || body[0] != SSL3_MT_CCS) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_BAD_CHANGE_CIPHER_SPEC);
    *out_alert = SSL_AD_ILLEGAL_PARAMETER;
    return ssl_open_record_error;
  }

  ssl_do_msg_callback(ssl, 0 /* read */, SSL3_RT_CHANGE_CIPHER_SPEC, body);
  return ssl_open_record_success;
}

void ssl_send_alert(SSL *ssl, int level, int desc) {
  // This function is called in response to a fatal error from the peer. Ignore
  // any failures writing the alert and report only the original error. In
  // particular, if the transport uses |SSL_write|, our existing error will be
  // clobbered so we must save and restore the error queue. See
  // https://crbug.com/959305.
  //
  // TODO(davidben): Return the alert out of the handshake, rather than calling
  // this function internally everywhere.
  //
  // TODO(davidben): This does not allow retrying if the alert hit EAGAIN. See
  // https://crbug.com/boringssl/130.
  UniquePtr<ERR_SAVE_STATE> err_state(ERR_save_state());
  ssl_send_alert_impl(ssl, level, desc);
  ERR_restore_state(err_state.get());
}

int ssl_send_alert_impl(SSL *ssl, int level, int desc) {
  // It is illegal to send an alert when we've already sent a closing one.
  if (ssl->s3->write_shutdown != ssl_shutdown_none) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_PROTOCOL_IS_SHUTDOWN);
    return -1;
  }

  if (level == SSL3_AL_WARNING && desc == SSL_AD_CLOSE_NOTIFY) {
    ssl->s3->write_shutdown = ssl_shutdown_close_notify;
  } else {
    assert(level == SSL3_AL_FATAL);
    assert(desc != SSL_AD_CLOSE_NOTIFY);
    ssl->s3->write_shutdown = ssl_shutdown_error;
  }

  ssl->s3->alert_dispatch = true;
  ssl->s3->send_alert[0] = level;
  ssl->s3->send_alert[1] = desc;
  if (ssl->s3->write_buffer.empty()) {
    // Nothing is being written out, so the alert may be dispatched
    // immediately.
    return ssl->method->dispatch_alert(ssl);
  }

  // The alert will be dispatched later.
  return -1;
}

int ssl3_dispatch_alert(SSL *ssl) {
  if (ssl->quic_method) {
    if (!ssl->quic_method->send_alert(ssl, ssl->s3->write_level,
                                      ssl->s3->send_alert[1])) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_QUIC_INTERNAL_ERROR);
      return 0;
    }
  } else {
    int ret = do_ssl3_write(ssl, SSL3_RT_ALERT, &ssl->s3->send_alert[0], 2);
    if (ret <= 0) {
      return ret;
    }
  }

  ssl->s3->alert_dispatch = false;

  // If the alert is fatal, flush the BIO now.
  if (ssl->s3->send_alert[0] == SSL3_AL_FATAL) {
    BIO_flush(ssl->wbio.get());
  }

  ssl_do_msg_callback(ssl, 1 /* write */, SSL3_RT_ALERT, ssl->s3->send_alert);

  int alert = (ssl->s3->send_alert[0] << 8) | ssl->s3->send_alert[1];
  ssl_do_info_callback(ssl, SSL_CB_WRITE_ALERT, alert);

  return 1;
}

BSSL_NAMESPACE_END
