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
 * [including the GNU Public Licence.] */

#include <openssl/evp.h>

#include <assert.h>
#include <string.h>

#include <openssl/bio.h>
#include <openssl/dh.h>
#include <openssl/dsa.h>
#include <openssl/ec.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/rsa.h>
#include <openssl/thread.h>

#include "internal.h"
#include "../internal.h"


extern const EVP_PKEY_ASN1_METHOD dsa_asn1_meth;
extern const EVP_PKEY_ASN1_METHOD ec_asn1_meth;
extern const EVP_PKEY_ASN1_METHOD rsa_asn1_meth;

EVP_PKEY *EVP_PKEY_new(void) {
  EVP_PKEY *ret;

  ret = OPENSSL_malloc(sizeof(EVP_PKEY));
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  memset(ret, 0, sizeof(EVP_PKEY));
  ret->type = EVP_PKEY_NONE;
  ret->references = 1;

  return ret;
}

static void free_it(EVP_PKEY *pkey) {
  if (pkey->ameth && pkey->ameth->pkey_free) {
    pkey->ameth->pkey_free(pkey);
    pkey->pkey.ptr = NULL;
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

EVP_PKEY *EVP_PKEY_up_ref(EVP_PKEY *pkey) {
  CRYPTO_refcount_inc(&pkey->references);
  return pkey;
}

int EVP_PKEY_is_opaque(const EVP_PKEY *pkey) {
  if (pkey->ameth && pkey->ameth->pkey_opaque) {
    return pkey->ameth->pkey_opaque(pkey);
  }
  return 0;
}

int EVP_PKEY_supports_digest(const EVP_PKEY *pkey, const EVP_MD *md) {
  if (pkey->ameth && pkey->ameth->pkey_supports_digest) {
    return pkey->ameth->pkey_supports_digest(pkey, md);
  }
  return 1;
}

int EVP_PKEY_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  if (a->type != b->type) {
    return -1;
  }

  if (a->ameth) {
    int ret;
    /* Compare parameters if the algorithm has them */
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
  if (to->type != from->type) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DIFFERENT_KEY_TYPES);
    goto err;
  }

  if (EVP_PKEY_missing_parameters(from)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_MISSING_PARAMETERS);
    goto err;
  }

  if (from->ameth && from->ameth->param_copy) {
    return from->ameth->param_copy(to, from);
  }

err:
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

int EVP_PKEY_bits(EVP_PKEY *pkey) {
  if (pkey && pkey->ameth && pkey->ameth->pkey_bits) {
    return pkey->ameth->pkey_bits(pkey);
  }
  return 0;
}

int EVP_PKEY_id(const EVP_PKEY *pkey) {
  return pkey->type;
}

/* TODO(fork): remove the first argument. */
const EVP_PKEY_ASN1_METHOD *EVP_PKEY_asn1_find(ENGINE **pengine, int nid) {
  switch (nid) {
    case EVP_PKEY_RSA:
    case EVP_PKEY_RSA2:
      return &rsa_asn1_meth;
    case EVP_PKEY_EC:
      return &ec_asn1_meth;
    case EVP_PKEY_DSA:
      return &dsa_asn1_meth;
    default:
      return NULL;
  }
}

int EVP_PKEY_type(int nid) {
  const EVP_PKEY_ASN1_METHOD *meth = EVP_PKEY_asn1_find(NULL, nid);
  if (meth == NULL) {
    return NID_undef;
  }
  return meth->pkey_id;
}

int EVP_PKEY_set1_RSA(EVP_PKEY *pkey, RSA *key) {
  if (EVP_PKEY_assign_RSA(pkey, key)) {
    RSA_up_ref(key);
    return 1;
  }
  return 0;
}

int EVP_PKEY_assign_RSA(EVP_PKEY *pkey, RSA *key) {
  return EVP_PKEY_assign(pkey, EVP_PKEY_RSA, key);
}

RSA *EVP_PKEY_get1_RSA(EVP_PKEY *pkey) {
  if (pkey->type != EVP_PKEY_RSA) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_EXPECTING_AN_RSA_KEY);
    return NULL;
  }
  RSA_up_ref(pkey->pkey.rsa);
  return pkey->pkey.rsa;
}

int EVP_PKEY_set1_DSA(EVP_PKEY *pkey, DSA *key) {
  if (EVP_PKEY_assign_DSA(pkey, key)) {
    DSA_up_ref(key);
    return 1;
  }
  return 0;
}

int EVP_PKEY_assign_DSA(EVP_PKEY *pkey, DSA *key) {
  return EVP_PKEY_assign(pkey, EVP_PKEY_DSA, key);
}

DSA *EVP_PKEY_get1_DSA(EVP_PKEY *pkey) {
  if (pkey->type != EVP_PKEY_DSA) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_EXPECTING_A_DSA_KEY);
    return NULL;
  }
  DSA_up_ref(pkey->pkey.dsa);
  return pkey->pkey.dsa;
}

int EVP_PKEY_set1_EC_KEY(EVP_PKEY *pkey, EC_KEY *key) {
  if (EVP_PKEY_assign_EC_KEY(pkey, key)) {
    EC_KEY_up_ref(key);
    return 1;
  }
  return 0;
}

int EVP_PKEY_assign_EC_KEY(EVP_PKEY *pkey, EC_KEY *key) {
  return EVP_PKEY_assign(pkey, EVP_PKEY_EC, key);
}

EC_KEY *EVP_PKEY_get1_EC_KEY(EVP_PKEY *pkey) {
  if (pkey->type != EVP_PKEY_EC) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_EXPECTING_AN_EC_KEY_KEY);
    return NULL;
  }
  EC_KEY_up_ref(pkey->pkey.ec);
  return pkey->pkey.ec;
}

int EVP_PKEY_set1_DH(EVP_PKEY *pkey, DH *key) {
  if (EVP_PKEY_assign_DH(pkey, key)) {
    DH_up_ref(key);
    return 1;
  }
  return 0;
}

int EVP_PKEY_assign_DH(EVP_PKEY *pkey, DH *key) {
  return EVP_PKEY_assign(pkey, EVP_PKEY_DH, key);
}

DH *EVP_PKEY_get1_DH(EVP_PKEY *pkey) {
  if (pkey->type != EVP_PKEY_DH) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_EXPECTING_A_DH_KEY);
    return NULL;
  }
  DH_up_ref(pkey->pkey.dh);
  return pkey->pkey.dh;
}

int EVP_PKEY_assign(EVP_PKEY *pkey, int type, void *key) {
  if (!EVP_PKEY_set_type(pkey, type)) {
    return 0;
  }
  pkey->pkey.ptr = key;
  return key != NULL;
}

const EVP_PKEY_ASN1_METHOD *EVP_PKEY_asn1_find_str(ENGINE **pengine,
                                                   const char *name,
                                                   size_t len) {
  if (len == 3 && memcmp(name, "RSA", 3) == 0) {
    return &rsa_asn1_meth;
  } if (len == 2 && memcmp(name, "EC", 2) == 0) {
    return &ec_asn1_meth;
  }
  return NULL;
}

int EVP_PKEY_set_type(EVP_PKEY *pkey, int type) {
  const EVP_PKEY_ASN1_METHOD *ameth;

  if (pkey && pkey->pkey.ptr) {
    free_it(pkey);
  }

  ameth = EVP_PKEY_asn1_find(NULL, type);
  if (ameth == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    ERR_add_error_dataf("algorithm %d (%s)", type, OBJ_nid2sn(type));
    return 0;
  }

  if (pkey) {
    pkey->ameth = ameth;
    pkey->type = pkey->ameth->pkey_id;
  }

  return 1;
}



int EVP_PKEY_cmp_parameters(const EVP_PKEY *a, const EVP_PKEY *b) {
  if (a->type != b->type) {
    return -1;
  }
  if (a->ameth && a->ameth->param_cmp) {
    return a->ameth->param_cmp(a, b);
  }
  return -2;
}

static int print_unsupported(BIO *out, const EVP_PKEY *pkey, int indent,
                             const char *kstr) {
  BIO_indent(out, indent, 128);
  BIO_printf(out, "%s algorithm \"%s\" unsupported\n", kstr,
             OBJ_nid2ln(pkey->type));
  return 1;
}

int EVP_PKEY_print_public(BIO *out, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *pctx) {
  if (pkey->ameth && pkey->ameth->pub_print) {
    return pkey->ameth->pub_print(out, pkey, indent, pctx);
  }

  return print_unsupported(out, pkey, indent, "Public Key");
}

int EVP_PKEY_print_private(BIO *out, const EVP_PKEY *pkey, int indent,
                           ASN1_PCTX *pctx) {
  if (pkey->ameth && pkey->ameth->priv_print) {
    return pkey->ameth->priv_print(out, pkey, indent, pctx);
  }

  return print_unsupported(out, pkey, indent, "Private Key");
}

int EVP_PKEY_print_params(BIO *out, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *pctx) {
  if (pkey->ameth && pkey->ameth->param_print) {
    return pkey->ameth->param_print(out, pkey, indent, pctx);
  }

  return print_unsupported(out, pkey, indent, "Parameters");
}

int EVP_PKEY_CTX_set_signature_md(EVP_PKEY_CTX *ctx, const EVP_MD *md) {
  return EVP_PKEY_CTX_ctrl(ctx, -1, EVP_PKEY_OP_TYPE_SIG, EVP_PKEY_CTRL_MD, 0,
                           (void *)md);
}

int EVP_PKEY_CTX_get_signature_md(EVP_PKEY_CTX *ctx, const EVP_MD **out_md) {
  return EVP_PKEY_CTX_ctrl(ctx, -1, EVP_PKEY_OP_TYPE_SIG, EVP_PKEY_CTRL_GET_MD,
                           0, (void *)out_md);
}

void OpenSSL_add_all_algorithms(void) {}

void OpenSSL_add_all_ciphers(void) {}

void OpenSSL_add_all_digests(void) {}

void EVP_cleanup(void) {}
