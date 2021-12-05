/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2006.
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

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/rsa.h>

#include "../fipsmodule/rsa/internal.h"
#include "internal.h"


static int rsa_pub_encode(CBB *out, const EVP_PKEY *key) {
  // See RFC 3279, section 2.3.1.
  CBB spki, algorithm, oid, null, key_bitstring;
  if (!CBB_add_asn1(out, &spki, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&spki, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, rsa_asn1_meth.oid, rsa_asn1_meth.oid_len) ||
      !CBB_add_asn1(&algorithm, &null, CBS_ASN1_NULL) ||
      !CBB_add_asn1(&spki, &key_bitstring, CBS_ASN1_BITSTRING) ||
      !CBB_add_u8(&key_bitstring, 0 /* padding */) ||
      !RSA_marshal_public_key(&key_bitstring, key->pkey.rsa) ||
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int rsa_pub_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // See RFC 3279, section 2.3.1.

  // The parameters must be NULL.
  CBS null;
  if (!CBS_get_asn1(params, &null, CBS_ASN1_NULL) ||
      CBS_len(&null) != 0 ||
      CBS_len(params) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return 0;
  }

  RSA *rsa = RSA_parse_public_key(key);
  if (rsa == NULL || CBS_len(key) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    RSA_free(rsa);
    return 0;
  }

  EVP_PKEY_assign_RSA(out, rsa);
  return 1;
}

static int rsa_pub_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  return BN_cmp(b->pkey.rsa->n, a->pkey.rsa->n) == 0 &&
         BN_cmp(b->pkey.rsa->e, a->pkey.rsa->e) == 0;
}

static int rsa_priv_encode(CBB *out, const EVP_PKEY *key) {
  CBB pkcs8, algorithm, oid, null, private_key;
  if (!CBB_add_asn1(out, &pkcs8, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&pkcs8, 0 /* version */) ||
      !CBB_add_asn1(&pkcs8, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, rsa_asn1_meth.oid, rsa_asn1_meth.oid_len) ||
      !CBB_add_asn1(&algorithm, &null, CBS_ASN1_NULL) ||
      !CBB_add_asn1(&pkcs8, &private_key, CBS_ASN1_OCTETSTRING) ||
      !RSA_marshal_private_key(&private_key, key->pkey.rsa) ||
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int rsa_priv_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // Per RFC 3447, A.1, the parameters have type NULL.
  CBS null;
  if (!CBS_get_asn1(params, &null, CBS_ASN1_NULL) ||
      CBS_len(&null) != 0 ||
      CBS_len(params) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return 0;
  }

  RSA *rsa = RSA_parse_private_key(key);
  if (rsa == NULL || CBS_len(key) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    RSA_free(rsa);
    return 0;
  }

  EVP_PKEY_assign_RSA(out, rsa);
  return 1;
}

static int rsa_opaque(const EVP_PKEY *pkey) {
  return RSA_is_opaque(pkey->pkey.rsa);
}

static int int_rsa_size(const EVP_PKEY *pkey) {
  return RSA_size(pkey->pkey.rsa);
}

static int rsa_bits(const EVP_PKEY *pkey) {
  return RSA_bits(pkey->pkey.rsa);
}

static void int_rsa_free(EVP_PKEY *pkey) { RSA_free(pkey->pkey.rsa); }

const EVP_PKEY_ASN1_METHOD rsa_asn1_meth = {
  EVP_PKEY_RSA,
  // 1.2.840.113549.1.1.1
  {0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01}, 9,

  rsa_pub_decode,
  rsa_pub_encode,
  rsa_pub_cmp,

  rsa_priv_decode,
  rsa_priv_encode,

  NULL /* set_priv_raw */,
  NULL /* set_pub_raw */,
  NULL /* get_priv_raw */,
  NULL /* get_pub_raw */,

  rsa_opaque,

  int_rsa_size,
  rsa_bits,

  0,0,0,

  int_rsa_free,
};
