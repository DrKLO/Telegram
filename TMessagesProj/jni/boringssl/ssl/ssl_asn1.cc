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
 * OTHERWISE. */

#include <openssl/ssl.h>

#include <limits.h>
#include <string.h>

#include <utility>

#include <openssl/buf.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/x509.h>

#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

// An SSL_SESSION is serialized as the following ASN.1 structure:
//
// SSLSession ::= SEQUENCE {
//     version                     INTEGER (1),  -- session structure version
//     sslVersion                  INTEGER,      -- protocol version number
//     cipher                      OCTET STRING, -- two bytes long
//     sessionID                   OCTET STRING,
//     masterKey                   OCTET STRING,
//     time                    [1] INTEGER, -- seconds since UNIX epoch
//     timeout                 [2] INTEGER, -- in seconds
//     peer                    [3] Certificate OPTIONAL,
//     sessionIDContext        [4] OCTET STRING OPTIONAL,
//     verifyResult            [5] INTEGER OPTIONAL,  -- one of X509_V_* codes
//     pskIdentity             [8] OCTET STRING OPTIONAL,
//     ticketLifetimeHint      [9] INTEGER OPTIONAL,       -- client-only
//     ticket                  [10] OCTET STRING OPTIONAL, -- client-only
//     peerSHA256              [13] OCTET STRING OPTIONAL,
//     originalHandshakeHash   [14] OCTET STRING OPTIONAL,
//     signedCertTimestampList [15] OCTET STRING OPTIONAL,
//                                  -- contents of SCT extension
//     ocspResponse            [16] OCTET STRING OPTIONAL,
//                                  -- stapled OCSP response from the server
//     extendedMasterSecret    [17] BOOLEAN OPTIONAL,
//     groupID                 [18] INTEGER OPTIONAL,
//     certChain               [19] SEQUENCE OF Certificate OPTIONAL,
//     ticketAgeAdd            [21] OCTET STRING OPTIONAL,
//     isServer                [22] BOOLEAN DEFAULT TRUE,
//     peerSignatureAlgorithm  [23] INTEGER OPTIONAL,
//     ticketMaxEarlyData      [24] INTEGER OPTIONAL,
//     authTimeout             [25] INTEGER OPTIONAL, -- defaults to timeout
//     earlyALPN               [26] OCTET STRING OPTIONAL,
// }
//
// Note: historically this serialization has included other optional
// fields. Their presence is currently treated as a parse error, except for
// hostName, which is ignored.
//
//     keyArg                  [0] IMPLICIT OCTET STRING OPTIONAL,
//     hostName                [6] OCTET STRING OPTIONAL,
//     pskIdentityHint         [7] OCTET STRING OPTIONAL,
//     compressionMethod       [11] OCTET STRING OPTIONAL,
//     srpUsername             [12] OCTET STRING OPTIONAL,
//     ticketFlags             [20] INTEGER OPTIONAL,

static const unsigned kVersion = 1;

static const unsigned kTimeTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 1;
static const unsigned kTimeoutTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 2;
static const unsigned kPeerTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 3;
static const unsigned kSessionIDContextTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 4;
static const unsigned kVerifyResultTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 5;
static const unsigned kHostNameTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 6;
static const unsigned kPSKIdentityTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 8;
static const unsigned kTicketLifetimeHintTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 9;
static const unsigned kTicketTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 10;
static const unsigned kPeerSHA256Tag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 13;
static const unsigned kOriginalHandshakeHashTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 14;
static const unsigned kSignedCertTimestampListTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 15;
static const unsigned kOCSPResponseTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 16;
static const unsigned kExtendedMasterSecretTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 17;
static const unsigned kGroupIDTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 18;
static const unsigned kCertChainTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 19;
static const unsigned kTicketAgeAddTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 21;
static const unsigned kIsServerTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 22;
static const unsigned kPeerSignatureAlgorithmTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 23;
static const unsigned kTicketMaxEarlyDataTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 24;
static const unsigned kAuthTimeoutTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 25;
static const unsigned kEarlyALPNTag =
    CBS_ASN1_CONSTRUCTED | CBS_ASN1_CONTEXT_SPECIFIC | 26;

static int SSL_SESSION_to_bytes_full(const SSL_SESSION *in, CBB *cbb,
                                     int for_ticket) {
  if (in == NULL || in->cipher == NULL) {
    return 0;
  }

  CBB session, child, child2;
  if (!CBB_add_asn1(cbb, &session, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&session, kVersion) ||
      !CBB_add_asn1_uint64(&session, in->ssl_version) ||
      !CBB_add_asn1(&session, &child, CBS_ASN1_OCTETSTRING) ||
      !CBB_add_u16(&child, (uint16_t)(in->cipher->id & 0xffff)) ||
      // The session ID is irrelevant for a session ticket.
      !CBB_add_asn1_octet_string(&session, in->session_id,
                                 for_ticket ? 0 : in->session_id_length) ||
      !CBB_add_asn1_octet_string(&session, in->master_key,
                                 in->master_key_length) ||
      !CBB_add_asn1(&session, &child, kTimeTag) ||
      !CBB_add_asn1_uint64(&child, in->time) ||
      !CBB_add_asn1(&session, &child, kTimeoutTag) ||
      !CBB_add_asn1_uint64(&child, in->timeout)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  // The peer certificate is only serialized if the SHA-256 isn't
  // serialized instead.
  if (sk_CRYPTO_BUFFER_num(in->certs.get()) > 0 && !in->peer_sha256_valid) {
    const CRYPTO_BUFFER *buffer = sk_CRYPTO_BUFFER_value(in->certs.get(), 0);
    if (!CBB_add_asn1(&session, &child, kPeerTag) ||
        !CBB_add_bytes(&child, CRYPTO_BUFFER_data(buffer),
                       CRYPTO_BUFFER_len(buffer))) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  // Although it is OPTIONAL and usually empty, OpenSSL has
  // historically always encoded the sid_ctx.
  if (!CBB_add_asn1(&session, &child, kSessionIDContextTag) ||
      !CBB_add_asn1_octet_string(&child, in->sid_ctx, in->sid_ctx_length)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  if (in->verify_result != X509_V_OK) {
    if (!CBB_add_asn1(&session, &child, kVerifyResultTag) ||
        !CBB_add_asn1_uint64(&child, in->verify_result)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->psk_identity) {
    if (!CBB_add_asn1(&session, &child, kPSKIdentityTag) ||
        !CBB_add_asn1_octet_string(&child,
                                   (const uint8_t *)in->psk_identity.get(),
                                   strlen(in->psk_identity.get()))) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->ticket_lifetime_hint > 0) {
    if (!CBB_add_asn1(&session, &child, kTicketLifetimeHintTag) ||
        !CBB_add_asn1_uint64(&child, in->ticket_lifetime_hint)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (!in->ticket.empty() && !for_ticket) {
    if (!CBB_add_asn1(&session, &child, kTicketTag) ||
        !CBB_add_asn1_octet_string(&child, in->ticket.data(),
                                   in->ticket.size())) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->peer_sha256_valid) {
    if (!CBB_add_asn1(&session, &child, kPeerSHA256Tag) ||
        !CBB_add_asn1_octet_string(&child, in->peer_sha256,
                                   sizeof(in->peer_sha256))) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->original_handshake_hash_len > 0) {
    if (!CBB_add_asn1(&session, &child, kOriginalHandshakeHashTag) ||
        !CBB_add_asn1_octet_string(&child, in->original_handshake_hash,
                                   in->original_handshake_hash_len)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->signed_cert_timestamp_list != nullptr) {
    if (!CBB_add_asn1(&session, &child, kSignedCertTimestampListTag) ||
        !CBB_add_asn1_octet_string(
            &child, CRYPTO_BUFFER_data(in->signed_cert_timestamp_list.get()),
            CRYPTO_BUFFER_len(in->signed_cert_timestamp_list.get()))) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->ocsp_response != nullptr) {
    if (!CBB_add_asn1(&session, &child, kOCSPResponseTag) ||
        !CBB_add_asn1_octet_string(
            &child, CRYPTO_BUFFER_data(in->ocsp_response.get()),
            CRYPTO_BUFFER_len(in->ocsp_response.get()))) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->extended_master_secret) {
    if (!CBB_add_asn1(&session, &child, kExtendedMasterSecretTag) ||
        !CBB_add_asn1_bool(&child, true)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->group_id > 0 &&
      (!CBB_add_asn1(&session, &child, kGroupIDTag) ||
       !CBB_add_asn1_uint64(&child, in->group_id))) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  // The certificate chain is only serialized if the leaf's SHA-256 isn't
  // serialized instead.
  if (in->certs != NULL &&
      !in->peer_sha256_valid &&
      sk_CRYPTO_BUFFER_num(in->certs.get()) >= 2) {
    if (!CBB_add_asn1(&session, &child, kCertChainTag)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    for (size_t i = 1; i < sk_CRYPTO_BUFFER_num(in->certs.get()); i++) {
      const CRYPTO_BUFFER *buffer = sk_CRYPTO_BUFFER_value(in->certs.get(), i);
      if (!CBB_add_bytes(&child, CRYPTO_BUFFER_data(buffer),
                         CRYPTO_BUFFER_len(buffer))) {
        OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
        return 0;
      }
    }
  }

  if (in->ticket_age_add_valid) {
    if (!CBB_add_asn1(&session, &child, kTicketAgeAddTag) ||
        !CBB_add_asn1(&child, &child2, CBS_ASN1_OCTETSTRING) ||
        !CBB_add_u32(&child2, in->ticket_age_add)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (!in->is_server) {
    if (!CBB_add_asn1(&session, &child, kIsServerTag) ||
        !CBB_add_asn1_bool(&child, false)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  if (in->peer_signature_algorithm != 0 &&
      (!CBB_add_asn1(&session, &child, kPeerSignatureAlgorithmTag) ||
       !CBB_add_asn1_uint64(&child, in->peer_signature_algorithm))) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  if (in->ticket_max_early_data != 0 &&
      (!CBB_add_asn1(&session, &child, kTicketMaxEarlyDataTag) ||
       !CBB_add_asn1_uint64(&child, in->ticket_max_early_data))) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  if (in->timeout != in->auth_timeout &&
      (!CBB_add_asn1(&session, &child, kAuthTimeoutTag) ||
       !CBB_add_asn1_uint64(&child, in->auth_timeout))) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  if (!in->early_alpn.empty()) {
    if (!CBB_add_asn1(&session, &child, kEarlyALPNTag) ||
        !CBB_add_asn1_octet_string(&child, in->early_alpn.data(),
                                   in->early_alpn.size())) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  return CBB_flush(cbb);
}

// SSL_SESSION_parse_string gets an optional ASN.1 OCTET STRING explicitly
// tagged with |tag| from |cbs| and saves it in |*out|. If the element was not
// found, it sets |*out| to NULL. It returns one on success, whether or not the
// element was found, and zero on decode error.
static int SSL_SESSION_parse_string(CBS *cbs, UniquePtr<char> *out, unsigned tag) {
  CBS value;
  int present;
  if (!CBS_get_optional_asn1_octet_string(cbs, &value, &present, tag)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return 0;
  }
  if (present) {
    if (CBS_contains_zero_byte(&value)) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
      return 0;
    }
    char *raw = nullptr;
    if (!CBS_strdup(&value, &raw)) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    out->reset(raw);
  } else {
    out->reset();
  }
  return 1;
}

// SSL_SESSION_parse_octet_string gets an optional ASN.1 OCTET STRING explicitly
// tagged with |tag| from |cbs| and stows it in |*out|. It returns one on
// success, whether or not the element was found, and zero on decode error.
static bool SSL_SESSION_parse_octet_string(CBS *cbs, Array<uint8_t> *out,
                                           unsigned tag) {
  CBS value;
  if (!CBS_get_optional_asn1_octet_string(cbs, &value, NULL, tag)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return false;
  }
  return out->CopyFrom(value);
}

static int SSL_SESSION_parse_crypto_buffer(CBS *cbs,
                                           UniquePtr<CRYPTO_BUFFER> *out,
                                           unsigned tag,
                                           CRYPTO_BUFFER_POOL *pool) {
  if (!CBS_peek_asn1_tag(cbs, tag)) {
    return 1;
  }

  CBS child, value;
  if (!CBS_get_asn1(cbs, &child, tag) ||
      !CBS_get_asn1(&child, &value, CBS_ASN1_OCTETSTRING) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return 0;
  }
  out->reset(CRYPTO_BUFFER_new_from_CBS(&value, pool));
  if (*out == nullptr) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  return 1;
}

// SSL_SESSION_parse_bounded_octet_string parses an optional ASN.1 OCTET STRING
// explicitly tagged with |tag| of size at most |max_out|.
static int SSL_SESSION_parse_bounded_octet_string(
    CBS *cbs, uint8_t *out, uint8_t *out_len, uint8_t max_out, unsigned tag) {
  CBS value;
  if (!CBS_get_optional_asn1_octet_string(cbs, &value, NULL, tag) ||
      CBS_len(&value) > max_out) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return 0;
  }
  OPENSSL_memcpy(out, CBS_data(&value), CBS_len(&value));
  *out_len = (uint8_t)CBS_len(&value);
  return 1;
}

static int SSL_SESSION_parse_long(CBS *cbs, long *out, unsigned tag,
                                  long default_value) {
  uint64_t value;
  if (!CBS_get_optional_asn1_uint64(cbs, &value, tag,
                                    (uint64_t)default_value) ||
      value > LONG_MAX) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return 0;
  }
  *out = (long)value;
  return 1;
}

static int SSL_SESSION_parse_u32(CBS *cbs, uint32_t *out, unsigned tag,
                                 uint32_t default_value) {
  uint64_t value;
  if (!CBS_get_optional_asn1_uint64(cbs, &value, tag,
                                    (uint64_t)default_value) ||
      value > 0xffffffff) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return 0;
  }
  *out = (uint32_t)value;
  return 1;
}

static int SSL_SESSION_parse_u16(CBS *cbs, uint16_t *out, unsigned tag,
                                 uint16_t default_value) {
  uint64_t value;
  if (!CBS_get_optional_asn1_uint64(cbs, &value, tag,
                                    (uint64_t)default_value) ||
      value > 0xffff) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return 0;
  }
  *out = (uint16_t)value;
  return 1;
}

UniquePtr<SSL_SESSION> SSL_SESSION_parse(CBS *cbs,
                                         const SSL_X509_METHOD *x509_method,
                                         CRYPTO_BUFFER_POOL *pool) {
  UniquePtr<SSL_SESSION> ret = ssl_session_new(x509_method);
  if (!ret) {
    return nullptr;
  }

  CBS session;
  uint64_t version, ssl_version;
  uint16_t unused;
  if (!CBS_get_asn1(cbs, &session, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&session, &version) ||
      version != kVersion ||
      !CBS_get_asn1_uint64(&session, &ssl_version) ||
      // Require sessions have versions valid in either TLS or DTLS. The session
      // will not be used by the handshake if not applicable, but, for
      // simplicity, never parse a session that does not pass
      // |ssl_protocol_version_from_wire|.
      ssl_version > UINT16_MAX ||
      !ssl_protocol_version_from_wire(&unused, ssl_version)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  ret->ssl_version = ssl_version;

  CBS cipher;
  uint16_t cipher_value;
  if (!CBS_get_asn1(&session, &cipher, CBS_ASN1_OCTETSTRING) ||
      !CBS_get_u16(&cipher, &cipher_value) ||
      CBS_len(&cipher) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  ret->cipher = SSL_get_cipher_by_value(cipher_value);
  if (ret->cipher == NULL) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_UNSUPPORTED_CIPHER);
    return nullptr;
  }

  CBS session_id, master_key;
  if (!CBS_get_asn1(&session, &session_id, CBS_ASN1_OCTETSTRING) ||
      CBS_len(&session_id) > SSL3_MAX_SSL_SESSION_ID_LENGTH ||
      !CBS_get_asn1(&session, &master_key, CBS_ASN1_OCTETSTRING) ||
      CBS_len(&master_key) > SSL_MAX_MASTER_KEY_LENGTH) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  OPENSSL_memcpy(ret->session_id, CBS_data(&session_id), CBS_len(&session_id));
  ret->session_id_length = CBS_len(&session_id);
  OPENSSL_memcpy(ret->master_key, CBS_data(&master_key), CBS_len(&master_key));
  ret->master_key_length = CBS_len(&master_key);

  CBS child;
  uint64_t timeout;
  if (!CBS_get_asn1(&session, &child, kTimeTag) ||
      !CBS_get_asn1_uint64(&child, &ret->time) ||
      !CBS_get_asn1(&session, &child, kTimeoutTag) ||
      !CBS_get_asn1_uint64(&child, &timeout) ||
      timeout > UINT32_MAX) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }

  ret->timeout = (uint32_t)timeout;

  CBS peer;
  int has_peer;
  if (!CBS_get_optional_asn1(&session, &peer, &has_peer, kPeerTag) ||
      (has_peer && CBS_len(&peer) == 0)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  // |peer| is processed with the certificate chain.

  if (!SSL_SESSION_parse_bounded_octet_string(
          &session, ret->sid_ctx, &ret->sid_ctx_length, sizeof(ret->sid_ctx),
          kSessionIDContextTag) ||
      !SSL_SESSION_parse_long(&session, &ret->verify_result, kVerifyResultTag,
                              X509_V_OK)) {
    return nullptr;
  }

  // Skip the historical hostName field.
  CBS unused_hostname;
  if (!CBS_get_optional_asn1(&session, &unused_hostname, nullptr,
                             kHostNameTag)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }

  if (!SSL_SESSION_parse_string(&session, &ret->psk_identity,
                                kPSKIdentityTag) ||
      !SSL_SESSION_parse_u32(&session, &ret->ticket_lifetime_hint,
                             kTicketLifetimeHintTag, 0) ||
      !SSL_SESSION_parse_octet_string(&session, &ret->ticket, kTicketTag)) {
    return nullptr;
  }

  if (CBS_peek_asn1_tag(&session, kPeerSHA256Tag)) {
    CBS peer_sha256;
    if (!CBS_get_asn1(&session, &child, kPeerSHA256Tag) ||
        !CBS_get_asn1(&child, &peer_sha256, CBS_ASN1_OCTETSTRING) ||
        CBS_len(&peer_sha256) != sizeof(ret->peer_sha256) ||
        CBS_len(&child) != 0) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
      return nullptr;
    }
    OPENSSL_memcpy(ret->peer_sha256, CBS_data(&peer_sha256),
                   sizeof(ret->peer_sha256));
    ret->peer_sha256_valid = 1;
  } else {
    ret->peer_sha256_valid = 0;
  }

  if (!SSL_SESSION_parse_bounded_octet_string(
          &session, ret->original_handshake_hash,
          &ret->original_handshake_hash_len,
          sizeof(ret->original_handshake_hash), kOriginalHandshakeHashTag) ||
      !SSL_SESSION_parse_crypto_buffer(&session,
                                       &ret->signed_cert_timestamp_list,
                                       kSignedCertTimestampListTag, pool) ||
      !SSL_SESSION_parse_crypto_buffer(&session, &ret->ocsp_response,
                                       kOCSPResponseTag, pool)) {
    return nullptr;
  }

  int extended_master_secret;
  if (!CBS_get_optional_asn1_bool(&session, &extended_master_secret,
                                  kExtendedMasterSecretTag,
                                  0 /* default to false */)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  ret->extended_master_secret = !!extended_master_secret;

  if (!SSL_SESSION_parse_u16(&session, &ret->group_id, kGroupIDTag, 0)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }

  CBS cert_chain;
  CBS_init(&cert_chain, NULL, 0);
  int has_cert_chain;
  if (!CBS_get_optional_asn1(&session, &cert_chain, &has_cert_chain,
                             kCertChainTag) ||
      (has_cert_chain && CBS_len(&cert_chain) == 0)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  if (has_cert_chain && !has_peer) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  if (has_peer || has_cert_chain) {
    ret->certs.reset(sk_CRYPTO_BUFFER_new_null());
    if (ret->certs == nullptr) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
      return nullptr;
    }

    if (has_peer) {
      UniquePtr<CRYPTO_BUFFER> buffer(CRYPTO_BUFFER_new_from_CBS(&peer, pool));
      if (!buffer ||
          !PushToStack(ret->certs.get(), std::move(buffer))) {
        OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
        return nullptr;
      }
    }

    while (CBS_len(&cert_chain) > 0) {
      CBS cert;
      if (!CBS_get_any_asn1_element(&cert_chain, &cert, NULL, NULL) ||
          CBS_len(&cert) == 0) {
        OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
        return nullptr;
      }

      UniquePtr<CRYPTO_BUFFER> buffer(CRYPTO_BUFFER_new_from_CBS(&cert, pool));
      if (buffer == nullptr ||
          !PushToStack(ret->certs.get(), std::move(buffer))) {
        OPENSSL_PUT_ERROR(SSL, ERR_R_MALLOC_FAILURE);
        return nullptr;
      }
    }
  }

  CBS age_add;
  int age_add_present;
  if (!CBS_get_optional_asn1_octet_string(&session, &age_add, &age_add_present,
                                          kTicketAgeAddTag) ||
      (age_add_present &&
       !CBS_get_u32(&age_add, &ret->ticket_age_add)) ||
      CBS_len(&age_add) != 0) {
    return nullptr;
  }
  ret->ticket_age_add_valid = age_add_present != 0;

  int is_server;
  if (!CBS_get_optional_asn1_bool(&session, &is_server, kIsServerTag,
                                  1 /* default to true */)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }
  /* TODO: in time we can include |is_server| for servers too, then we can
     enforce that client and server sessions are never mixed up. */

  ret->is_server = is_server;

  if (!SSL_SESSION_parse_u16(&session, &ret->peer_signature_algorithm,
                             kPeerSignatureAlgorithmTag, 0) ||
      !SSL_SESSION_parse_u32(&session, &ret->ticket_max_early_data,
                             kTicketMaxEarlyDataTag, 0) ||
      !SSL_SESSION_parse_u32(&session, &ret->auth_timeout, kAuthTimeoutTag,
                             ret->timeout) ||
      !SSL_SESSION_parse_octet_string(&session, &ret->early_alpn,
                                      kEarlyALPNTag) ||
      CBS_len(&session) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }

  if (!x509_method->session_cache_objects(ret.get())) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return nullptr;
  }

  return ret;
}

int ssl_session_serialize(const SSL_SESSION *in, CBB *cbb) {
  return SSL_SESSION_to_bytes_full(in, cbb, 0);
}

BSSL_NAMESPACE_END

using namespace bssl;

int SSL_SESSION_to_bytes(const SSL_SESSION *in, uint8_t **out_data,
                         size_t *out_len) {
  if (in->not_resumable) {
    // If the caller has an unresumable session, e.g. if |SSL_get_session| were
    // called on a TLS 1.3 or False Started connection, serialize with a
    // placeholder value so it is not accidentally deserialized into a resumable
    // one.
    static const char kNotResumableSession[] = "NOT RESUMABLE";

    *out_len = strlen(kNotResumableSession);
    *out_data = (uint8_t *)BUF_memdup(kNotResumableSession, *out_len);
    if (*out_data == NULL) {
      return 0;
    }

    return 1;
  }

  ScopedCBB cbb;
  if (!CBB_init(cbb.get(), 256) ||
      !SSL_SESSION_to_bytes_full(in, cbb.get(), 0) ||
      !CBB_finish(cbb.get(), out_data, out_len)) {
    return 0;
  }

  return 1;
}

int SSL_SESSION_to_bytes_for_ticket(const SSL_SESSION *in, uint8_t **out_data,
                                    size_t *out_len) {
  ScopedCBB cbb;
  if (!CBB_init(cbb.get(), 256) ||
      !SSL_SESSION_to_bytes_full(in, cbb.get(), 1) ||
      !CBB_finish(cbb.get(), out_data, out_len)) {
    return 0;
  }

  return 1;
}

int i2d_SSL_SESSION(SSL_SESSION *in, uint8_t **pp) {
  uint8_t *out;
  size_t len;

  if (!SSL_SESSION_to_bytes(in, &out, &len)) {
    return -1;
  }

  if (len > INT_MAX) {
    OPENSSL_free(out);
    OPENSSL_PUT_ERROR(SSL, ERR_R_OVERFLOW);
    return -1;
  }

  if (pp) {
    OPENSSL_memcpy(*pp, out, len);
    *pp += len;
  }
  OPENSSL_free(out);

  return len;
}

SSL_SESSION *SSL_SESSION_from_bytes(const uint8_t *in, size_t in_len,
                                    const SSL_CTX *ctx) {
  CBS cbs;
  CBS_init(&cbs, in, in_len);
  UniquePtr<SSL_SESSION> ret =
      SSL_SESSION_parse(&cbs, ctx->x509_method, ctx->pool);
  if (!ret) {
    return NULL;
  }
  if (CBS_len(&cbs) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_SSL_SESSION);
    return NULL;
  }
  return ret.release();
}
