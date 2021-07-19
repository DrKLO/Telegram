/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL project
 * 2006.
 */
/* ====================================================================
 * Copyright (c) 2006 The OpenSSL Project.  All rights reserved.
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

#include <openssl/evp.h>

#include <openssl/digest.h>
#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/dsa.h>
#include <openssl/err.h>

#include "internal.h"


static int dsa_pub_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // See RFC 3279, section 2.3.2.

  // Parameters may or may not be present.
  DSA *dsa;
  if (CBS_len(params) == 0) {
    dsa = DSA_new();
    if (dsa == NULL) {
      return 0;
    }
  } else {
    dsa = DSA_parse_parameters(params);
    if (dsa == NULL || CBS_len(params) != 0) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
      goto err;
    }
  }

  dsa->pub_key = BN_new();
  if (dsa->pub_key == NULL) {
    goto err;
  }

  if (!BN_parse_asn1_unsigned(key, dsa->pub_key) ||
      CBS_len(key) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    goto err;
  }

  EVP_PKEY_assign_DSA(out, dsa);
  return 1;

err:
  DSA_free(dsa);
  return 0;
}

static int dsa_pub_encode(CBB *out, const EVP_PKEY *key) {
  const DSA *dsa = key->pkey.dsa;
  const int has_params = dsa->p != NULL && dsa->q != NULL && dsa->g != NULL;

  // See RFC 5480, section 2.
  CBB spki, algorithm, oid, key_bitstring;
  if (!CBB_add_asn1(out, &spki, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&spki, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, dsa_asn1_meth.oid, dsa_asn1_meth.oid_len) ||
      (has_params &&
       !DSA_marshal_parameters(&algorithm, dsa)) ||
      !CBB_add_asn1(&spki, &key_bitstring, CBS_ASN1_BITSTRING) ||
      !CBB_add_u8(&key_bitstring, 0 /* padding */) ||
      !BN_marshal_asn1(&key_bitstring, dsa->pub_key) ||
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int dsa_priv_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // See PKCS#11, v2.40, section 2.5.

  // Decode parameters.
  BN_CTX *ctx = NULL;
  DSA *dsa = DSA_parse_parameters(params);
  if (dsa == NULL || CBS_len(params) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    goto err;
  }

  dsa->priv_key = BN_new();
  dsa->pub_key = BN_new();
  if (dsa->priv_key == NULL || dsa->pub_key == NULL) {
    goto err;
  }

  // Decode the key.
  if (!BN_parse_asn1_unsigned(key, dsa->priv_key) ||
      CBS_len(key) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    goto err;
  }

  // Calculate the public key.
  ctx = BN_CTX_new();
  if (ctx == NULL ||
      !BN_mod_exp_mont_consttime(dsa->pub_key, dsa->g, dsa->priv_key, dsa->p,
                                 ctx, NULL)) {
    goto err;
  }

  BN_CTX_free(ctx);
  EVP_PKEY_assign_DSA(out, dsa);
  return 1;

err:
  BN_CTX_free(ctx);
  DSA_free(dsa);
  return 0;
}

static int dsa_priv_encode(CBB *out, const EVP_PKEY *key) {
  const DSA *dsa = key->pkey.dsa;
  if (dsa == NULL || dsa->priv_key == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_MISSING_PARAMETERS);
    return 0;
  }

  // See PKCS#11, v2.40, section 2.5.
  CBB pkcs8, algorithm, oid, private_key;
  if (!CBB_add_asn1(out, &pkcs8, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&pkcs8, 0 /* version */) ||
      !CBB_add_asn1(&pkcs8, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, dsa_asn1_meth.oid, dsa_asn1_meth.oid_len) ||
      !DSA_marshal_parameters(&algorithm, dsa) ||
      !CBB_add_asn1(&pkcs8, &private_key, CBS_ASN1_OCTETSTRING) ||
      !BN_marshal_asn1(&private_key, dsa->priv_key) ||
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int int_dsa_size(const EVP_PKEY *pkey) {
  return DSA_size(pkey->pkey.dsa);
}

static int dsa_bits(const EVP_PKEY *pkey) {
  return BN_num_bits(pkey->pkey.dsa->p);
}

static int dsa_missing_parameters(const EVP_PKEY *pkey) {
  DSA *dsa;
  dsa = pkey->pkey.dsa;
  if (dsa->p == NULL || dsa->q == NULL || dsa->g == NULL) {
    return 1;
  }
  return 0;
}

static int dup_bn_into(BIGNUM **out, BIGNUM *src) {
  BIGNUM *a;

  a = BN_dup(src);
  if (a == NULL) {
    return 0;
  }
  BN_free(*out);
  *out = a;

  return 1;
}

static int dsa_copy_parameters(EVP_PKEY *to, const EVP_PKEY *from) {
  if (!dup_bn_into(&to->pkey.dsa->p, from->pkey.dsa->p) ||
      !dup_bn_into(&to->pkey.dsa->q, from->pkey.dsa->q) ||
      !dup_bn_into(&to->pkey.dsa->g, from->pkey.dsa->g)) {
    return 0;
  }

  return 1;
}

static int dsa_cmp_parameters(const EVP_PKEY *a, const EVP_PKEY *b) {
  return BN_cmp(a->pkey.dsa->p, b->pkey.dsa->p) == 0 &&
         BN_cmp(a->pkey.dsa->q, b->pkey.dsa->q) == 0 &&
         BN_cmp(a->pkey.dsa->g, b->pkey.dsa->g) == 0;
}

static int dsa_pub_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  return BN_cmp(b->pkey.dsa->pub_key, a->pkey.dsa->pub_key) == 0;
}

static void int_dsa_free(EVP_PKEY *pkey) { DSA_free(pkey->pkey.dsa); }

const EVP_PKEY_ASN1_METHOD dsa_asn1_meth = {
  EVP_PKEY_DSA,
  // 1.2.840.10040.4.1
  {0x2a, 0x86, 0x48, 0xce, 0x38, 0x04, 0x01}, 7,

  dsa_pub_decode,
  dsa_pub_encode,
  dsa_pub_cmp,

  dsa_priv_decode,
  dsa_priv_encode,

  NULL /* set_priv_raw */,
  NULL /* set_pub_raw */,
  NULL /* get_priv_raw */,
  NULL /* get_pub_raw */,

  NULL /* pkey_opaque */,

  int_dsa_size,
  dsa_bits,

  dsa_missing_parameters,
  dsa_copy_parameters,
  dsa_cmp_parameters,

  int_dsa_free,
};
