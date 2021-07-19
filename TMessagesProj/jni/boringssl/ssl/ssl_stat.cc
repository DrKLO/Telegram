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
 * OTHERWISE.
 */

#include <openssl/ssl.h>

#include <assert.h>

#include "internal.h"


const char *SSL_state_string_long(const SSL *ssl) {
  if (ssl->s3->hs == nullptr) {
    return "SSL negotiation finished successfully";
  }

  return ssl->server ? ssl_server_handshake_state(ssl->s3->hs.get())
                     : ssl_client_handshake_state(ssl->s3->hs.get());
}

const char *SSL_state_string(const SSL *ssl) {
  return "!!!!!!";
}

const char *SSL_alert_type_string_long(int value) {
  value >>= 8;
  if (value == SSL3_AL_WARNING) {
    return "warning";
  } else if (value == SSL3_AL_FATAL) {
    return "fatal";
  }

  return "unknown";
}

const char *SSL_alert_type_string(int value) {
  return "!";
}

const char *SSL_alert_desc_string(int value) {
  return "!!";
}

const char *SSL_alert_desc_string_long(int value) {
  switch (value & 0xff) {
    case SSL3_AD_CLOSE_NOTIFY:
      return "close notify";

    case SSL3_AD_UNEXPECTED_MESSAGE:
      return "unexpected_message";

    case SSL3_AD_BAD_RECORD_MAC:
      return "bad record mac";

    case SSL3_AD_DECOMPRESSION_FAILURE:
      return "decompression failure";

    case SSL3_AD_HANDSHAKE_FAILURE:
      return "handshake failure";

    case SSL3_AD_NO_CERTIFICATE:
      return "no certificate";

    case SSL3_AD_BAD_CERTIFICATE:
      return "bad certificate";

    case SSL3_AD_UNSUPPORTED_CERTIFICATE:
      return "unsupported certificate";

    case SSL3_AD_CERTIFICATE_REVOKED:
      return "certificate revoked";

    case SSL3_AD_CERTIFICATE_EXPIRED:
      return "certificate expired";

    case SSL3_AD_CERTIFICATE_UNKNOWN:
      return "certificate unknown";

    case SSL3_AD_ILLEGAL_PARAMETER:
      return "illegal parameter";

    case TLS1_AD_DECRYPTION_FAILED:
      return "decryption failed";

    case TLS1_AD_RECORD_OVERFLOW:
      return "record overflow";

    case TLS1_AD_UNKNOWN_CA:
      return "unknown CA";

    case TLS1_AD_ACCESS_DENIED:
      return "access denied";

    case TLS1_AD_DECODE_ERROR:
      return "decode error";

    case TLS1_AD_DECRYPT_ERROR:
      return "decrypt error";

    case TLS1_AD_EXPORT_RESTRICTION:
      return "export restriction";

    case TLS1_AD_PROTOCOL_VERSION:
      return "protocol version";

    case TLS1_AD_INSUFFICIENT_SECURITY:
      return "insufficient security";

    case TLS1_AD_INTERNAL_ERROR:
      return "internal error";

    case SSL3_AD_INAPPROPRIATE_FALLBACK:
      return "inappropriate fallback";

    case TLS1_AD_USER_CANCELLED:
      return "user canceled";

    case TLS1_AD_NO_RENEGOTIATION:
      return "no renegotiation";

    case TLS1_AD_UNSUPPORTED_EXTENSION:
      return "unsupported extension";

    case TLS1_AD_CERTIFICATE_UNOBTAINABLE:
      return "certificate unobtainable";

    case TLS1_AD_UNRECOGNIZED_NAME:
      return "unrecognized name";

    case TLS1_AD_BAD_CERTIFICATE_STATUS_RESPONSE:
      return "bad certificate status response";

    case TLS1_AD_BAD_CERTIFICATE_HASH_VALUE:
      return "bad certificate hash value";

    case TLS1_AD_UNKNOWN_PSK_IDENTITY:
      return "unknown PSK identity";

    case TLS1_AD_CERTIFICATE_REQUIRED:
      return "certificate required";

    default:
      return "unknown";
  }
}
