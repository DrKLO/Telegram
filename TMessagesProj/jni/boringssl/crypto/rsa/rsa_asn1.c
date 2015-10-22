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

#include <openssl/asn1.h>
#include <openssl/asn1t.h>
#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"


static int parse_integer(CBS *cbs, BIGNUM **out) {
  assert(*out == NULL);
  *out = BN_new();
  if (*out == NULL) {
    return 0;
  }
  return BN_cbs2unsigned(cbs, *out);
}

static int marshal_integer(CBB *cbb, BIGNUM *bn) {
  if (bn == NULL) {
    /* An RSA object may be missing some components. */
    OPENSSL_PUT_ERROR(RSA, RSA_R_VALUE_MISSING);
    return 0;
  }
  return BN_bn2cbb(cbb, bn);
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

/* kVersionTwoPrime and kVersionMulti are the supported values of the version
 * field of an RSAPrivateKey structure (RFC 3447). */
static const uint64_t kVersionTwoPrime = 0;
static const uint64_t kVersionMulti = 1;

/* rsa_parse_additional_prime parses a DER-encoded OtherPrimeInfo from |cbs| and
 * advances |cbs|. It returns a newly-allocated |RSA_additional_prime| on
 * success or NULL on error. The |r| and |method_mod| fields of the result are
 * set to NULL. */
static RSA_additional_prime *rsa_parse_additional_prime(CBS *cbs) {
  RSA_additional_prime *ret = OPENSSL_malloc(sizeof(RSA_additional_prime));
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  memset(ret, 0, sizeof(RSA_additional_prime));

  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !parse_integer(&child, &ret->prime) ||
      !parse_integer(&child, &ret->exp) ||
      !parse_integer(&child, &ret->coeff) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    RSA_additional_prime_free(ret);
    return NULL;
  }

  return ret;
}

RSA *RSA_parse_private_key(CBS *cbs) {
  BN_CTX *ctx = NULL;
  BIGNUM *product_of_primes_so_far = NULL;
  RSA *ret = RSA_new();
  if (ret == NULL) {
    return NULL;
  }

  CBS child;
  uint64_t version;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&child, &version) ||
      (version != kVersionTwoPrime && version != kVersionMulti) ||
      !parse_integer(&child, &ret->n) ||
      !parse_integer(&child, &ret->e) ||
      !parse_integer(&child, &ret->d) ||
      !parse_integer(&child, &ret->p) ||
      !parse_integer(&child, &ret->q) ||
      !parse_integer(&child, &ret->dmp1) ||
      !parse_integer(&child, &ret->dmq1) ||
      !parse_integer(&child, &ret->iqmp)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_VERSION);
    goto err;
  }

  /* Multi-prime RSA requires a newer version. */
  if (version == kVersionMulti &&
      CBS_peek_asn1_tag(&child, CBS_ASN1_SEQUENCE)) {
    CBS other_prime_infos;
    if (!CBS_get_asn1(&child, &other_prime_infos, CBS_ASN1_SEQUENCE) ||
        CBS_len(&other_prime_infos) == 0) {
      OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
      goto err;
    }
    ret->additional_primes = sk_RSA_additional_prime_new_null();
    if (ret->additional_primes == NULL) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
      goto err;
    }

    ctx = BN_CTX_new();
    product_of_primes_so_far = BN_new();
    if (ctx == NULL ||
        product_of_primes_so_far == NULL ||
        !BN_mul(product_of_primes_so_far, ret->p, ret->q, ctx)) {
      goto err;
    }

    while (CBS_len(&other_prime_infos) > 0) {
      RSA_additional_prime *ap = rsa_parse_additional_prime(&other_prime_infos);
      if (ap == NULL) {
        goto err;
      }
      if (!sk_RSA_additional_prime_push(ret->additional_primes, ap)) {
        OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
        RSA_additional_prime_free(ap);
        goto err;
      }
      ap->r = BN_dup(product_of_primes_so_far);
      if (ap->r == NULL ||
          !BN_mul(product_of_primes_so_far, product_of_primes_so_far,
                  ap->prime, ctx)) {
        goto err;
      }
    }
  }

  if (CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_ENCODING);
    goto err;
  }

  BN_CTX_free(ctx);
  BN_free(product_of_primes_so_far);
  return ret;

err:
  BN_CTX_free(ctx);
  BN_free(product_of_primes_so_far);
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
  const int is_multiprime =
      sk_RSA_additional_prime_num(rsa->additional_primes) > 0;

  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&child,
                           is_multiprime ? kVersionMulti : kVersionTwoPrime) ||
      !marshal_integer(&child, rsa->n) ||
      !marshal_integer(&child, rsa->e) ||
      !marshal_integer(&child, rsa->d) ||
      !marshal_integer(&child, rsa->p) ||
      !marshal_integer(&child, rsa->q) ||
      !marshal_integer(&child, rsa->dmp1) ||
      !marshal_integer(&child, rsa->dmq1) ||
      !marshal_integer(&child, rsa->iqmp)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
    return 0;
  }

  if (is_multiprime) {
    CBB other_prime_infos;
    if (!CBB_add_asn1(&child, &other_prime_infos, CBS_ASN1_SEQUENCE)) {
      OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
      return 0;
    }
    size_t i;
    for (i = 0; i < sk_RSA_additional_prime_num(rsa->additional_primes); i++) {
      RSA_additional_prime *ap =
              sk_RSA_additional_prime_value(rsa->additional_primes, i);
      CBB other_prime_info;
      if (!CBB_add_asn1(&other_prime_infos, &other_prime_info,
                        CBS_ASN1_SEQUENCE) ||
          !marshal_integer(&other_prime_info, ap->prime) ||
          !marshal_integer(&other_prime_info, ap->exp) ||
          !marshal_integer(&other_prime_info, ap->coeff)) {
        OPENSSL_PUT_ERROR(RSA, RSA_R_ENCODE_ERROR);
        return 0;
      }
    }
  }

  if (!CBB_flush(cbb)) {
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
  *inp += (size_t)len - CBS_len(&cbs);
  return ret;
}

int i2d_RSAPublicKey(const RSA *in, uint8_t **outp) {
  uint8_t *der;
  size_t der_len;
  if (!RSA_public_key_to_bytes(&der, &der_len, in)) {
    return -1;
  }
  if (der_len > INT_MAX) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_OVERFLOW);
    OPENSSL_free(der);
    return -1;
  }
  if (outp != NULL) {
    if (*outp == NULL) {
      *outp = der;
      der = NULL;
    } else {
      memcpy(*outp, der, der_len);
      *outp += der_len;
    }
  }
  OPENSSL_free(der);
  return (int)der_len;
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
  *inp += (size_t)len - CBS_len(&cbs);
  return ret;
}

int i2d_RSAPrivateKey(const RSA *in, uint8_t **outp) {
  uint8_t *der;
  size_t der_len;
  if (!RSA_private_key_to_bytes(&der, &der_len, in)) {
    return -1;
  }
  if (der_len > INT_MAX) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_OVERFLOW);
    OPENSSL_free(der);
    return -1;
  }
  if (outp != NULL) {
    if (*outp == NULL) {
      *outp = der;
      der = NULL;
    } else {
      memcpy(*outp, der, der_len);
      *outp += der_len;
    }
  }
  OPENSSL_free(der);
  return (int)der_len;
}

ASN1_SEQUENCE(RSA_PSS_PARAMS) = {
  ASN1_EXP_OPT(RSA_PSS_PARAMS, hashAlgorithm, X509_ALGOR,0),
  ASN1_EXP_OPT(RSA_PSS_PARAMS, maskGenAlgorithm, X509_ALGOR,1),
  ASN1_EXP_OPT(RSA_PSS_PARAMS, saltLength, ASN1_INTEGER,2),
  ASN1_EXP_OPT(RSA_PSS_PARAMS, trailerField, ASN1_INTEGER,3),
} ASN1_SEQUENCE_END(RSA_PSS_PARAMS);

IMPLEMENT_ASN1_FUNCTIONS(RSA_PSS_PARAMS);

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
