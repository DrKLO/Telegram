// Copyright 1995-2016 The OpenSSL Project Authors. All Rights Reserved.
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

#include <assert.h>
#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/nid.h>
#include <openssl/thread.h>

#include "../internal.h"
#include "internal.h"


// Node depends on |EVP_R_NOT_XOF_OR_INVALID_LENGTH|.
//
// TODO(davidben): Fix Node to not touch the error queue itself and remove this.
OPENSSL_DECLARE_ERROR_REASON(EVP, NOT_XOF_OR_INVALID_LENGTH)

// The HPKE module uses the EVP error namespace, but it lives in another
// directory.
OPENSSL_DECLARE_ERROR_REASON(EVP, EMPTY_PSK)

EVP_PKEY *EVP_PKEY_new(void) {
  EVP_PKEY *ret =
      reinterpret_cast<EVP_PKEY *>(OPENSSL_zalloc(sizeof(EVP_PKEY)));
  if (ret == NULL) {
    return NULL;
  }

  ret->type = EVP_PKEY_NONE;
  ret->references = 1;
  return ret;
}

static void free_it(EVP_PKEY *pkey) {
  if (pkey->ameth && pkey->ameth->pkey_free) {
    pkey->ameth->pkey_free(pkey);
    pkey->pkey = NULL;
    pkey->type = EVP_PKEY_NONE;
  }
}

void EVP_PKEY_free(EVP_PKEY *pkey) {
  if (pkey == NULL) {
    return;
  }

  if (!CRYPTO_refcount_dec_and_test_zero(&pkey->references)) {
    return;
  }

  free_it(pkey);
  OPENSSL_free(pkey);
}

int EVP_PKEY_up_ref(EVP_PKEY *pkey) {
  CRYPTO_refcount_inc(&pkey->references);
  return 1;
}

int EVP_PKEY_is_opaque(const EVP_PKEY *pkey) {
  if (pkey->ameth && pkey->ameth->pkey_opaque) {
    return pkey->ameth->pkey_opaque(pkey);
  }
  return 0;
}

int EVP_PKEY_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  if (a->type != b->type) {
    return -1;
  }

  if (a->ameth) {
    int ret;
    // Compare parameters if the algorithm has them
    if (a->ameth->param_cmp) {
      ret = a->ameth->param_cmp(a, b);
      if (ret <= 0) {
        return ret;
      }
    }

    if (a->ameth->pub_cmp) {
      return a->ameth->pub_cmp(a, b);
    }
  }

  return -2;
}

int EVP_PKEY_copy_parameters(EVP_PKEY *to, const EVP_PKEY *from) {
  if (to->type == EVP_PKEY_NONE) {
    evp_pkey_set_method(to, from->ameth);
  } else if (to->type != from->type) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DIFFERENT_KEY_TYPES);
    return 0;
  }

  if (EVP_PKEY_missing_parameters(from)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_MISSING_PARAMETERS);
    return 0;
  }

  // Once set, parameters may not change.
  if (!EVP_PKEY_missing_parameters(to)) {
    if (EVP_PKEY_cmp_parameters(to, from) == 1) {
      return 1;
    }
    OPENSSL_PUT_ERROR(EVP, EVP_R_DIFFERENT_PARAMETERS);
    return 0;
  }

  if (from->ameth && from->ameth->param_copy) {
    return from->ameth->param_copy(to, from);
  }

  // TODO(https://crbug.com/boringssl/536): If the algorithm takes no
  // parameters, copying them should vacuously succeed.
  return 0;
}

int EVP_PKEY_missing_parameters(const EVP_PKEY *pkey) {
  if (pkey->ameth && pkey->ameth->param_missing) {
    return pkey->ameth->param_missing(pkey);
  }
  return 0;
}

int EVP_PKEY_size(const EVP_PKEY *pkey) {
  if (pkey && pkey->ameth && pkey->ameth->pkey_size) {
    return pkey->ameth->pkey_size(pkey);
  }
  return 0;
}

int EVP_PKEY_bits(const EVP_PKEY *pkey) {
  if (pkey && pkey->ameth && pkey->ameth->pkey_bits) {
    return pkey->ameth->pkey_bits(pkey);
  }
  return 0;
}

int EVP_PKEY_id(const EVP_PKEY *pkey) { return pkey->type; }

// evp_pkey_asn1_find returns the ASN.1 method table for the given |nid|, which
// should be one of the |EVP_PKEY_*| values. It returns NULL if |nid| is
// unknown.
static const EVP_PKEY_ASN1_METHOD *evp_pkey_asn1_find(int nid) {
  switch (nid) {
    case EVP_PKEY_RSA:
      return &rsa_asn1_meth;
    case EVP_PKEY_EC:
      return &ec_asn1_meth;
    case EVP_PKEY_DSA:
      return &dsa_asn1_meth;
    case EVP_PKEY_ED25519:
      return &ed25519_asn1_meth;
    case EVP_PKEY_X25519:
      return &x25519_asn1_meth;
    default:
      return NULL;
  }
}

void evp_pkey_set_method(EVP_PKEY *pkey, const EVP_PKEY_ASN1_METHOD *method) {
  free_it(pkey);
  pkey->ameth = method;
  pkey->type = pkey->ameth->pkey_id;
}

int EVP_PKEY_type(int nid) {
  // In OpenSSL, this was used to map between type aliases. BoringSSL supports
  // no type aliases, so this function is just the identity.
  return nid;
}

int EVP_PKEY_assign(EVP_PKEY *pkey, int type, void *key) {
  // This function can only be used to assign RSA, DSA, EC, and DH keys. Other
  // key types have internal representations which are not exposed through the
  // public API.
  switch (type) {
    case EVP_PKEY_RSA:
      return EVP_PKEY_assign_RSA(pkey, reinterpret_cast<RSA *>(key));
    case EVP_PKEY_DSA:
      return EVP_PKEY_assign_DSA(pkey, reinterpret_cast<DSA *>(key));
    case EVP_PKEY_EC:
      return EVP_PKEY_assign_EC_KEY(pkey, reinterpret_cast<EC_KEY *>(key));
    case EVP_PKEY_DH:
      return EVP_PKEY_assign_DH(pkey, reinterpret_cast<DH *>(key));
  }

  OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
  ERR_add_error_dataf("algorithm %d", type);
  return 0;
}

int EVP_PKEY_set_type(EVP_PKEY *pkey, int type) {
  if (pkey && pkey->pkey) {
    // This isn't strictly necessary, but historically |EVP_PKEY_set_type| would
    // clear |pkey| even if |evp_pkey_asn1_find| failed, so we preserve that
    // behavior.
    free_it(pkey);
  }

  const EVP_PKEY_ASN1_METHOD *ameth = evp_pkey_asn1_find(type);
  if (ameth == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    ERR_add_error_dataf("algorithm %d", type);
    return 0;
  }

  if (pkey) {
    evp_pkey_set_method(pkey, ameth);
  }

  return 1;
}

EVP_PKEY *EVP_PKEY_new_raw_private_key(int type, ENGINE *unused,
                                       const uint8_t *in, size_t len) {
  // To avoid pulling in all key types, look for specifically the key types that
  // support |set_priv_raw|.
  const EVP_PKEY_ASN1_METHOD *method;
  switch (type) {
    case EVP_PKEY_X25519:
      method = &x25519_asn1_meth;
      break;
    case EVP_PKEY_ED25519:
      method = &ed25519_asn1_meth;
      break;
    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
      return nullptr;
  }

  bssl::UniquePtr<EVP_PKEY> ret(EVP_PKEY_new());
  if (ret == nullptr) {
    return nullptr;
  }
  evp_pkey_set_method(ret.get(), method);

  if (!ret->ameth->set_priv_raw(ret.get(), in, len)) {
    return nullptr;
  }

  return ret.release();
}

EVP_PKEY *EVP_PKEY_new_raw_public_key(int type, ENGINE *unused,
                                      const uint8_t *in, size_t len) {
  // To avoid pulling in all key types, look for specifically the key types that
  // support |set_pub_raw|.
  const EVP_PKEY_ASN1_METHOD *method;
  switch (type) {
    case EVP_PKEY_X25519:
      method = &x25519_asn1_meth;
      break;
    case EVP_PKEY_ED25519:
      method = &ed25519_asn1_meth;
      break;
    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
      return nullptr;
  }

  bssl::UniquePtr<EVP_PKEY> ret(EVP_PKEY_new());
  if (ret == nullptr) {
    return nullptr;
  }
  evp_pkey_set_method(ret.get(), method);

  if (!ret->ameth->set_pub_raw(ret.get(), in, len)) {
    return nullptr;
  }

  return ret.release();
}

int EVP_PKEY_get_raw_private_key(const EVP_PKEY *pkey, uint8_t *out,
                                 size_t *out_len) {
  if (pkey->ameth->get_priv_raw == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }

  return pkey->ameth->get_priv_raw(pkey, out, out_len);
}

int EVP_PKEY_get_raw_public_key(const EVP_PKEY *pkey, uint8_t *out,
                                size_t *out_len) {
  if (pkey->ameth->get_pub_raw == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }

  return pkey->ameth->get_pub_raw(pkey, out, out_len);
}

int EVP_PKEY_cmp_parameters(const EVP_PKEY *a, const EVP_PKEY *b) {
  if (a->type != b->type) {
    return -1;
  }
  if (a->ameth && a->ameth->param_cmp) {
    return a->ameth->param_cmp(a, b);
  }
  // TODO(https://crbug.com/boringssl/536): If the algorithm doesn't use
  // parameters, they should compare as vacuously equal.
  return -2;
}

int EVP_PKEY_CTX_set_signature_md(EVP_PKEY_CTX *ctx, const EVP_MD *md) {
  return EVP_PKEY_CTX_ctrl(ctx, -1, EVP_PKEY_OP_TYPE_SIG, EVP_PKEY_CTRL_MD, 0,
                           (void *)md);
}

int EVP_PKEY_CTX_get_signature_md(EVP_PKEY_CTX *ctx, const EVP_MD **out_md) {
  return EVP_PKEY_CTX_ctrl(ctx, -1, EVP_PKEY_OP_TYPE_SIG, EVP_PKEY_CTRL_GET_MD,
                           0, (void *)out_md);
}

void *EVP_PKEY_get0(const EVP_PKEY *pkey) {
  // Node references, but never calls this function, so for now we return NULL.
  // If other projects require complete support, call |EVP_PKEY_get0_RSA|, etc.,
  // rather than reading |pkey->pkey| directly. This avoids problems if our
  // internal representation does not match the type the caller expects from
  // OpenSSL.
  return NULL;
}

void OpenSSL_add_all_algorithms(void) {}

void OPENSSL_add_all_algorithms_conf(void) {}

void OpenSSL_add_all_ciphers(void) {}

void OpenSSL_add_all_digests(void) {}

void EVP_cleanup(void) {}

int EVP_PKEY_set1_tls_encodedpoint(EVP_PKEY *pkey, const uint8_t *in,
                                   size_t len) {
  if (pkey->ameth->set1_tls_encodedpoint == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }

  return pkey->ameth->set1_tls_encodedpoint(pkey, in, len);
}

size_t EVP_PKEY_get1_tls_encodedpoint(const EVP_PKEY *pkey, uint8_t **out_ptr) {
  if (pkey->ameth->get1_tls_encodedpoint == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }

  return pkey->ameth->get1_tls_encodedpoint(pkey, out_ptr);
}

int EVP_PKEY_base_id(const EVP_PKEY *pkey) {
  // OpenSSL has two notions of key type because it supports multiple OIDs for
  // the same algorithm: NID_rsa vs NID_rsaEncryption and five distinct spelling
  // of DSA. We do not support these, so the base ID is simply the ID.
  return EVP_PKEY_id(pkey);
}
