// Copyright 2006-2021 The OpenSSL Project Authors. All Rights Reserved.
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
#include <openssl/dh.h>
#include <openssl/err.h>

#include "../internal.h"
#include "internal.h"


static void dh_free(EVP_PKEY *pkey) {
  DH_free(reinterpret_cast<DH *>(pkey->pkey));
  pkey->pkey = NULL;
}

static int dh_size(const EVP_PKEY *pkey) {
  return DH_size(reinterpret_cast<const DH *>(pkey->pkey));
}

static int dh_bits(const EVP_PKEY *pkey) {
  return DH_bits(reinterpret_cast<const DH *>(pkey->pkey));
}

static int dh_param_missing(const EVP_PKEY *pkey) {
  const DH *dh = reinterpret_cast<const DH *>(pkey->pkey);
  return dh == NULL || DH_get0_p(dh) == NULL || DH_get0_g(dh) == NULL;
}

static int dh_param_copy(EVP_PKEY *to, const EVP_PKEY *from) {
  if (dh_param_missing(from)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_MISSING_PARAMETERS);
    return 0;
  }

  const DH *dh = reinterpret_cast<DH *>(from->pkey);
  const BIGNUM *q_old = DH_get0_q(dh);
  BIGNUM *p = BN_dup(DH_get0_p(dh));
  BIGNUM *q = q_old == NULL ? NULL : BN_dup(q_old);
  BIGNUM *g = BN_dup(DH_get0_g(dh));
  if (p == NULL || (q_old != NULL && q == NULL) || g == NULL ||
      !DH_set0_pqg(reinterpret_cast<DH *>(to->pkey), p, q, g)) {
    BN_free(p);
    BN_free(q);
    BN_free(g);
    return 0;
  }

  // |DH_set0_pqg| took ownership of |p|, |q|, and |g|.
  return 1;
}

static int dh_param_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  if (dh_param_missing(a) || dh_param_missing(b)) {
    return -2;
  }

  // Matching OpenSSL, only compare p and g for PKCS#3-style Diffie-Hellman.
  // OpenSSL only checks q in X9.42-style Diffie-Hellman ("DHX").
  const DH *a_dh = reinterpret_cast<const DH *>(a->pkey);
  const DH *b_dh = reinterpret_cast<const DH *>(b->pkey);
  return BN_cmp(DH_get0_p(a_dh), DH_get0_p(b_dh)) == 0 &&
         BN_cmp(DH_get0_g(a_dh), DH_get0_g(b_dh)) == 0;
}

static int dh_pub_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  if (dh_param_cmp(a, b) <= 0) {
    return 0;
  }

  const DH *a_dh = reinterpret_cast<const DH *>(a->pkey);
  const DH *b_dh = reinterpret_cast<const DH *>(b->pkey);
  return BN_cmp(DH_get0_pub_key(a_dh), DH_get0_pub_key(b_dh)) == 0;
}

const EVP_PKEY_ASN1_METHOD dh_asn1_meth = {
    /*pkey_id=*/EVP_PKEY_DH,
    /*oid=*/{0},
    /*oid_len=*/0,
    /*pkey_method=*/&dh_pkey_meth,
    /*pub_decode=*/nullptr,
    /*pub_encode=*/nullptr,
    /*pub_cmp=*/dh_pub_cmp,
    /*priv_decode=*/nullptr,
    /*priv_encode=*/nullptr,
    /*set_priv_raw=*/nullptr,
    /*set_pub_raw=*/nullptr,
    /*get_priv_raw=*/nullptr,
    /*get_pub_raw=*/nullptr,
    /*set1_tls_encodedpoint=*/nullptr,
    /*get1_tls_encodedpoint=*/nullptr,
    /*pkey_opaque=*/nullptr,
    /*pkey_size=*/dh_size,
    /*pkey_bits=*/dh_bits,
    /*param_missing=*/dh_param_missing,
    /*param_copy=*/dh_param_copy,
    /*param_cmp=*/dh_param_cmp,
    /*pkey_free=*/dh_free,
};

int EVP_PKEY_set1_DH(EVP_PKEY *pkey, DH *key) {
  if (EVP_PKEY_assign_DH(pkey, key)) {
    DH_up_ref(key);
    return 1;
  }
  return 0;
}

int EVP_PKEY_assign_DH(EVP_PKEY *pkey, DH *key) {
  evp_pkey_set_method(pkey, &dh_asn1_meth);
  pkey->pkey = key;
  return key != NULL;
}

DH *EVP_PKEY_get0_DH(const EVP_PKEY *pkey) {
  if (pkey->type != EVP_PKEY_DH) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_EXPECTING_A_DH_KEY);
    return NULL;
  }
  return reinterpret_cast<DH *>(const_cast<EVP_PKEY *>(pkey)->pkey);
}

DH *EVP_PKEY_get1_DH(const EVP_PKEY *pkey) {
  DH *dh = EVP_PKEY_get0_DH(pkey);
  if (dh != NULL) {
    DH_up_ref(dh);
  }
  return dh;
}
