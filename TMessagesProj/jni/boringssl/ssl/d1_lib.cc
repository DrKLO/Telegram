/*
 * DTLS implementation written by Nagendra Modadugu
 * (nagendra@cs.stanford.edu) for the OpenSSL project 2005.
 */
/* ====================================================================
 * Copyright (c) 1999-2005 The OpenSSL Project.  All rights reserved.
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
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
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

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/nid.h>

#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

// DTLS1_MTU_TIMEOUTS is the maximum number of timeouts to expire
// before starting to decrease the MTU.
#define DTLS1_MTU_TIMEOUTS                     2

// DTLS1_MAX_TIMEOUTS is the maximum number of timeouts to expire
// before failing the DTLS handshake.
#define DTLS1_MAX_TIMEOUTS                     12

DTLS1_STATE::DTLS1_STATE()
    : has_change_cipher_spec(false),
      outgoing_messages_complete(false),
      flight_has_reply(false) {}

DTLS1_STATE::~DTLS1_STATE() {}

bool dtls1_new(SSL *ssl) {
  if (!ssl3_new(ssl)) {
    return false;
  }
  UniquePtr<DTLS1_STATE> d1 = MakeUnique<DTLS1_STATE>();
  if (!d1) {
    ssl3_free(ssl);
    return false;
  }

  ssl->d1 = d1.release();

  // Set the version to the highest supported version.
  //
  // TODO(davidben): Move this field into |s3|, have it store the normalized
  // protocol version, and implement this pre-negotiation quirk in |SSL_version|
  // at the API boundary rather than in internal state.
  ssl->version = DTLS1_2_VERSION;
  return true;
}

void dtls1_free(SSL *ssl) {
  ssl3_free(ssl);

  if (ssl == NULL) {
    return;
  }

  Delete(ssl->d1);
  ssl->d1 = NULL;
}

void dtls1_start_timer(SSL *ssl) {
  // If timer is not set, initialize duration (by default, 1 second)
  if (ssl->d1->next_timeout.tv_sec == 0 && ssl->d1->next_timeout.tv_usec == 0) {
    ssl->d1->timeout_duration_ms = ssl->initial_timeout_duration_ms;
  }

  // Set timeout to current time
  ssl_get_current_time(ssl, &ssl->d1->next_timeout);

  // Add duration to current time
  ssl->d1->next_timeout.tv_sec += ssl->d1->timeout_duration_ms / 1000;
  ssl->d1->next_timeout.tv_usec += (ssl->d1->timeout_duration_ms % 1000) * 1000;
  if (ssl->d1->next_timeout.tv_usec >= 1000000) {
    ssl->d1->next_timeout.tv_sec++;
    ssl->d1->next_timeout.tv_usec -= 1000000;
  }
}

bool dtls1_is_timer_expired(SSL *ssl) {
  struct timeval timeleft;

  // Get time left until timeout, return false if no timer running
  if (!DTLSv1_get_timeout(ssl, &timeleft)) {
    return false;
  }

  // Return false if timer is not expired yet
  if (timeleft.tv_sec > 0 || timeleft.tv_usec > 0) {
    return false;
  }

  // Timer expired, so return true
  return true;
}

static void dtls1_double_timeout(SSL *ssl) {
  ssl->d1->timeout_duration_ms *= 2;
  if (ssl->d1->timeout_duration_ms > 60000) {
    ssl->d1->timeout_duration_ms = 60000;
  }
}

void dtls1_stop_timer(SSL *ssl) {
  ssl->d1->num_timeouts = 0;
  OPENSSL_memset(&ssl->d1->next_timeout, 0, sizeof(ssl->d1->next_timeout));
  ssl->d1->timeout_duration_ms = ssl->initial_timeout_duration_ms;
}

bool dtls1_check_timeout_num(SSL *ssl) {
  ssl->d1->num_timeouts++;

  // Reduce MTU after 2 unsuccessful retransmissions
  if (ssl->d1->num_timeouts > DTLS1_MTU_TIMEOUTS &&
      !(SSL_get_options(ssl) & SSL_OP_NO_QUERY_MTU)) {
    long mtu =
        BIO_ctrl(ssl->wbio.get(), BIO_CTRL_DGRAM_GET_FALLBACK_MTU, 0, nullptr);
    if (mtu >= 0 && mtu <= (1 << 30) && (unsigned)mtu >= dtls1_min_mtu()) {
      ssl->d1->mtu = (unsigned)mtu;
    }
  }

  if (ssl->d1->num_timeouts > DTLS1_MAX_TIMEOUTS) {
    // fail the connection, enough alerts have been sent
    OPENSSL_PUT_ERROR(SSL, SSL_R_READ_TIMEOUT_EXPIRED);
    return false;
  }

  return true;
}

BSSL_NAMESPACE_END

using namespace bssl;

void DTLSv1_set_initial_timeout_duration(SSL *ssl, unsigned int duration_ms) {
  ssl->initial_timeout_duration_ms = duration_ms;
}

int DTLSv1_get_timeout(const SSL *ssl, struct timeval *out) {
  if (!SSL_is_dtls(ssl)) {
    return 0;
  }

  // If no timeout is set, just return 0.
  if (ssl->d1->next_timeout.tv_sec == 0 && ssl->d1->next_timeout.tv_usec == 0) {
    return 0;
  }

  struct OPENSSL_timeval timenow;
  ssl_get_current_time(ssl, &timenow);

  // If timer already expired, set remaining time to 0.
  if (ssl->d1->next_timeout.tv_sec < timenow.tv_sec ||
      (ssl->d1->next_timeout.tv_sec == timenow.tv_sec &&
       ssl->d1->next_timeout.tv_usec <= timenow.tv_usec)) {
    OPENSSL_memset(out, 0, sizeof(*out));
    return 1;
  }

  // Calculate time left until timer expires.
  struct OPENSSL_timeval ret;
  OPENSSL_memcpy(&ret, &ssl->d1->next_timeout, sizeof(ret));
  ret.tv_sec -= timenow.tv_sec;
  if (ret.tv_usec >= timenow.tv_usec) {
    ret.tv_usec -= timenow.tv_usec;
  } else {
    ret.tv_usec = 1000000 + ret.tv_usec - timenow.tv_usec;
    ret.tv_sec--;
  }

  // If remaining time is less than 15 ms, set it to 0 to prevent issues
  // because of small divergences with socket timeouts.
  if (ret.tv_sec == 0 && ret.tv_usec < 15000) {
    OPENSSL_memset(&ret, 0, sizeof(ret));
  }

  // Clamp the result in case of overflow.
  if (ret.tv_sec > INT_MAX) {
    assert(0);
    out->tv_sec = INT_MAX;
  } else {
    out->tv_sec = ret.tv_sec;
  }

  out->tv_usec = ret.tv_usec;
  return 1;
}

int DTLSv1_handle_timeout(SSL *ssl) {
  ssl_reset_error_state(ssl);

  if (!SSL_is_dtls(ssl)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return -1;
  }

  // If no timer is expired, don't do anything.
  if (!dtls1_is_timer_expired(ssl)) {
    return 0;
  }

  if (!dtls1_check_timeout_num(ssl)) {
    return -1;
  }

  dtls1_double_timeout(ssl);
  dtls1_start_timer(ssl);
  return dtls1_retransmit_outgoing_messages(ssl);
}
