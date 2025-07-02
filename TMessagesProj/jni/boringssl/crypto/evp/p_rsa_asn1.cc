// Copyright 2006-2016 The OpenSSL Project Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
  const RSA *rsa = reinterpret_cast<const RSA *>(key->pkey);
  CBB spki, algorithm, oid, null, key_bitstring;
  if (!CBB_add_asn1(out, &spki, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&spki, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, rsa_asn1_meth.oid, rsa_asn1_meth.oid_len) ||
      !CBB_add_asn1(&algorithm, &null, CBS_ASN1_NULL) ||
      !CBB_add_asn1(&spki, &key_bitstring, CBS_ASN1_BITSTRING) ||
      !CBB_add_u8(&key_bitstring, 0 /* padding */) ||
      !RSA_marshal_public_key(&key_bitstring, rsa) ||  //
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
  if (!CBS_get_asn1(params, &null, CBS_ASN1_NULL) || CBS_len(&null) != 0 ||
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
  const RSA *a_rsa = reinterpret_cast<const RSA *>(a->pkey);
  const RSA *b_rsa = reinterpret_cast<const RSA *>(b->pkey);
  return BN_cmp(RSA_get0_n(b_rsa), RSA_get0_n(a_rsa)) == 0 &&
         BN_cmp(RSA_get0_e(b_rsa), RSA_get0_e(a_rsa)) == 0;
}

static int rsa_priv_encode(CBB *out, const EVP_PKEY *key) {
  const RSA *rsa = reinterpret_cast<const RSA *>(key->pkey);
  CBB pkcs8, algorithm, oid, null, private_key;
  if (!CBB_add_asn1(out, &pkcs8, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&pkcs8, 0 /* version */) ||
      !CBB_add_asn1(&pkcs8, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, rsa_asn1_meth.oid, rsa_asn1_meth.oid_len) ||
      !CBB_add_asn1(&algorithm, &null, CBS_ASN1_NULL) ||
      !CBB_add_asn1(&pkcs8, &private_key, CBS_ASN1_OCTETSTRING) ||
      !RSA_marshal_private_key(&private_key, rsa) ||  //
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int rsa_priv_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // Per RFC 3447, A.1, the parameters have type NULL.
  CBS null;
  if (!CBS_get_asn1(params, &null, CBS_ASN1_NULL) || CBS_len(&null) != 0 ||
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
  const RSA *rsa = reinterpret_cast<const RSA *>(pkey->pkey);
  return RSA_is_opaque(rsa);
}

static int int_rsa_size(const EVP_PKEY *pkey) {
  const RSA *rsa = reinterpret_cast<const RSA *>(pkey->pkey);
  return RSA_size(rsa);
}

static int rsa_bits(const EVP_PKEY *pkey) {
  const RSA *rsa = reinterpret_cast<const RSA *>(pkey->pkey);
  return RSA_bits(rsa);
}

static void int_rsa_free(EVP_PKEY *pkey) {
  RSA_free(reinterpret_cast<RSA *>(pkey->pkey));
  pkey->pkey = NULL;
}

const EVP_PKEY_ASN1_METHOD rsa_asn1_meth = {
    EVP_PKEY_RSA,
    // 1.2.840.113549.1.1.1
    {0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01},
    9,

    &rsa_pkey_meth,

    rsa_pub_decode,
    rsa_pub_encode,
    rsa_pub_cmp,

    rsa_priv_decode,
    rsa_priv_encode,

    /*set_priv_raw=*/NULL,
    /*set_pub_raw=*/NULL,
    /*get_priv_raw=*/NULL,
    /*get_pub_raw=*/NULL,
    /*set1_tls_encodedpoint=*/NULL,
    /*get1_tls_encodedpoint=*/NULL,

    rsa_opaque,

    int_rsa_size,
    rsa_bits,

    0,
    0,
    0,

    int_rsa_free,
};

int EVP_PKEY_set1_RSA(EVP_PKEY *pkey, RSA *key) {
  if (EVP_PKEY_assign_RSA(pkey, key)) {
    RSA_up_ref(key);
    return 1;
  }
  return 0;
}

int EVP_PKEY_assign_RSA(EVP_PKEY *pkey, RSA *key) {
  evp_pkey_set_method(pkey, &rsa_asn1_meth);
  pkey->pkey = key;
  return key != NULL;
}

RSA *EVP_PKEY_get0_RSA(const EVP_PKEY *pkey) {
  if (pkey->type != EVP_PKEY_RSA) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_EXPECTING_AN_RSA_KEY);
    return NULL;
  }
  return reinterpret_cast<RSA *>(pkey->pkey);
}

RSA *EVP_PKEY_get1_RSA(const EVP_PKEY *pkey) {
  RSA *rsa = EVP_PKEY_get0_RSA(pkey);
  if (rsa != NULL) {
    RSA_up_ref(rsa);
  }
  return rsa;
}
