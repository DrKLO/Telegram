/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2000.
 */
/* ====================================================================
 * Copyright (c) 2000-2005 The OpenSSL Project.  All rights reserved.
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
 *    licensing@OpenSSL.org.
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

#include <openssl/rsa.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "../fipsmodule/rsa/internal.h"
#include "../bytestring/internal.h"
#include "../internal.h"


static int parse_integer(CBS *cbs, BIGNUM **out) {
  assert(*out == NULL);
  *out = BN_new();
  if (*out == NULL) {
    return 0;
  }
  return BN_parse_asn1_unsigned(cbs, *out);
}

static int marshal_integer(CBB *cbb, BIGNUM *bn) {
  if (bn == NULL) {
    // An RSA object may be missing some components.
    OPENSSL_PUT_ERROR(RSA, RSA_R_VALUE_MISSING);
    return 0;
  }
  return BN_marshal_asn1(cbb, bn);
}

RSA *RSA_parse_public_key(CBS *cbs) {
  RSA *ret = RSA_new();
  if (ret == NULL) {
    return NULL;
  }
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !parse_integer(&child, &ret->n) ||
      !parse_integer(&child, &ret->e) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    RSA_free(ret);
    return NULL;
  }

  if (!BN_is_odd(ret->e) ||
      BN_num_bits(ret->e) < 2) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_RSA_PARAMETERS);
    RSA_free(ret);
    return NULL;
  }

  return ret;
}

RSA *RSA_public_key_from_bytes(const uint8_t *in, size_t in_len) {
  CBS cbs;
  CBS_init(&cbs, in, in_len);
  RSA *ret = RSA_parse_public_key(&cbs);
  if (ret == NULL || CBS_len(&cbs) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    RSA_free(ret);
    return NULL;
  }
  return ret;
}

int RSA_marshal_public_key(CBB *cbb, const RSA *rsa) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !marshal_integer(&child, rsa->n) ||
      !marshal_integer(&child, rsa->e) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

int RSA_public_key_to_bytes(uint8_t **out_bytes, size_t *out_len,
                            const RSA *rsa) {
  CBB cbb;
  CBB_zero(&cbb);
  if (!CBB_init(&cbb, 0) ||
      !RSA_marshal_public_key(&cbb, rsa) ||
      !CBB_finish(&cbb, out_bytes, out_len)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
    CBB_cleanup(&cbb);
    return 0;
  }
  return 1;
}

// kVersionTwoPrime is the value of the version field for a two-prime
// RSAPrivateKey structure (RFC 3447).
static const uint64_t kVersionTwoPrime = 0;

RSA *RSA_parse_private_key(CBS *cbs) {
  RSA *ret = RSA_new();
  if (ret == NULL) {
    return NULL;
  }

  CBS child;
  uint64_t version;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&child, &version)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    goto err;
  }

  if (version != kVersionTwoPrime) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_VERSION);
    goto err;
  }

  if (!parse_integer(&child, &ret->n) ||
      !parse_integer(&child, &ret->e) ||
      !parse_integer(&child, &ret->d) ||
      !parse_integer(&child, &ret->p) ||
      !parse_integer(&child, &ret->q) ||
      !parse_integer(&child, &ret->dmp1) ||
      !parse_integer(&child, &ret->dmq1) ||
      !parse_integer(&child, &ret->iqmp)) {
    goto err;
  }

  if (CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    goto err;
  }

  if (!RSA_check_key(ret)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_RSA_PARAMETERS);
    goto err;
  }

  return ret;

err:
  RSA_free(ret);
  return NULL;
}

RSA *RSA_private_key_from_bytes(const uint8_t *in, size_t in_len) {
  CBS cbs;
  CBS_init(&cbs, in, in_len);
  RSA *ret = RSA_parse_private_key(&cbs);
  if (ret == NULL || CBS_len(&cbs) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    RSA_free(ret);
    return NULL;
  }
  return ret;
}

int RSA_marshal_private_key(CBB *cbb, const RSA *rsa) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&child, kVersionTwoPrime) ||
      !marshal_integer(&child, rsa->n) ||
      !marshal_integer(&child, rsa->e) ||
      !marshal_integer(&child, rsa->d) ||
      !marshal_integer(&child, rsa->p) ||
      !marshal_integer(&child, rsa->q) ||
      !marshal_integer(&child, rsa->dmp1) ||
      !marshal_integer(&child, rsa->dmq1) ||
      !marshal_integer(&child, rsa->iqmp) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

int RSA_private_key_to_bytes(uint8_t **out_bytes, size_t *out_len,
                             const RSA *rsa) {
  CBB cbb;
  CBB_zero(&cbb);
  if (!CBB_init(&cbb, 0) ||
      !RSA_marshal_private_key(&cbb, rsa) ||
      !CBB_finish(&cbb, out_bytes, out_len)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
    CBB_cleanup(&cbb);
    return 0;
  }
  return 1;
}

RSA *d2i_RSAPublicKey(RSA **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  RSA *ret = RSA_parse_public_key(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    RSA_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_RSAPublicKey(const RSA *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !RSA_marshal_public_key(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

RSA *d2i_RSAPrivateKey(RSA **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  RSA *ret = RSA_parse_private_key(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    RSA_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_RSAPrivateKey(const RSA *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !RSA_marshal_private_key(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

RSA *RSAPublicKey_dup(const RSA *rsa) {
  uint8_t *der;
  size_t der_len;
  if (!RSA_public_key_to_bytes(&der, &der_len, rsa)) {
    return NULL;
  }
  RSA *ret = RSA_public_key_from_bytes(der, der_len);
  OPENSSL_free(der);
  return ret;
}

RSA *RSAPrivateKey_dup(const RSA *rsa) {
  uint8_t *der;
  size_t der_len;
  if (!RSA_private_key_to_bytes(&der, &der_len, rsa)) {
    return NULL;
  }
  RSA *ret = RSA_private_key_from_bytes(der, der_len);
  OPENSSL_free(der);
  return ret;
}
