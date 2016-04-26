/* Originally written by Bodo Moeller for the OpenSSL project.
 * ====================================================================
 * Copyright (c) 1998-2005 The OpenSSL Project.  All rights reserved.
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
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
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
 * Hudson (tjh@cryptsoft.com).
 *
 */
/* ====================================================================
 * Copyright 2002 Sun Microsystems, Inc. ALL RIGHTS RESERVED.
 *
 * Portions of the attached software ("Contribution") are developed by
 * SUN MICROSYSTEMS, INC., and are contributed to the OpenSSL project.
 *
 * The Contribution is licensed pursuant to the OpenSSL open source
 * license provided above.
 *
 * The elliptic curve binary polynomial software is originally written by
 * Sheueling Chang Shantz and Douglas Stebila of Sun Microsystems
 * Laboratories. */

#include <openssl/ec_key.h>

#include <string.h>

#include <openssl/ec.h>
#include <openssl/engine.h>
#include <openssl/err.h>
#include <openssl/ex_data.h>
#include <openssl/mem.h>
#include <openssl/thread.h>

#include "internal.h"
#include "../internal.h"


static CRYPTO_EX_DATA_CLASS g_ex_data_class = CRYPTO_EX_DATA_CLASS_INIT;

EC_KEY *EC_KEY_new(void) { return EC_KEY_new_method(NULL); }

EC_KEY *EC_KEY_new_method(const ENGINE *engine) {
  EC_KEY *ret = (EC_KEY *)OPENSSL_malloc(sizeof(EC_KEY));
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  memset(ret, 0, sizeof(EC_KEY));

  if (engine) {
    ret->ecdsa_meth = ENGINE_get_ECDSA_method(engine);
  }
  if (ret->ecdsa_meth) {
    METHOD_ref(ret->ecdsa_meth);
  }

  ret->version = 1;
  ret->conv_form = POINT_CONVERSION_UNCOMPRESSED;
  ret->references = 1;

  if (!CRYPTO_new_ex_data(&g_ex_data_class, ret, &ret->ex_data)) {
    goto err1;
  }

  if (ret->ecdsa_meth && ret->ecdsa_meth->init && !ret->ecdsa_meth->init(ret)) {
    goto err2;
  }

  return ret;

err2:
  CRYPTO_free_ex_data(&g_ex_data_class, ret, &ret->ex_data);
err1:
  if (ret->ecdsa_meth) {
    METHOD_unref(ret->ecdsa_meth);
  }
  OPENSSL_free(ret);
  return NULL;
}

EC_KEY *EC_KEY_new_by_curve_name(int nid) {
  EC_KEY *ret = EC_KEY_new();
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  ret->group = EC_GROUP_new_by_curve_name(nid);
  if (ret->group == NULL) {
    EC_KEY_free(ret);
    return NULL;
  }
  return ret;
}

void EC_KEY_free(EC_KEY *r) {
  if (r == NULL) {
    return;
  }

  if (!CRYPTO_refcount_dec_and_test_zero(&r->references)) {
    return;
  }

  if (r->ecdsa_meth) {
    if (r->ecdsa_meth->finish) {
      r->ecdsa_meth->finish(r);
    }
    METHOD_unref(r->ecdsa_meth);
  }

  EC_GROUP_free(r->group);
  EC_POINT_free(r->pub_key);
  BN_clear_free(r->priv_key);

  CRYPTO_free_ex_data(&g_ex_data_class, r, &r->ex_data);

  OPENSSL_cleanse((void *)r, sizeof(EC_KEY));
  OPENSSL_free(r);
}

EC_KEY *EC_KEY_copy(EC_KEY *dest, const EC_KEY *src) {
  if (dest == NULL || src == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return NULL;
  }
  /* Copy the parameters. */
  if (src->group) {
    /* TODO(fork): duplicating the group seems wasteful. */
    EC_GROUP_free(dest->group);
    dest->group = EC_GROUP_dup(src->group);
    if (dest->group == NULL) {
      return NULL;
    }
  }

  /* Copy the public key. */
  if (src->pub_key && src->group) {
    EC_POINT_free(dest->pub_key);
    dest->pub_key = EC_POINT_dup(src->pub_key, src->group);
    if (dest->pub_key == NULL) {
      return NULL;
    }
  }

  /* copy the private key */
  if (src->priv_key) {
    if (dest->priv_key == NULL) {
      dest->priv_key = BN_new();
      if (dest->priv_key == NULL) {
        return NULL;
      }
    }
    if (!BN_copy(dest->priv_key, src->priv_key)) {
      return NULL;
    }
  }
  /* copy method/extra data */
  if (src->ecdsa_meth) {
      METHOD_unref(dest->ecdsa_meth);
      dest->ecdsa_meth = src->ecdsa_meth;
      METHOD_ref(dest->ecdsa_meth);
  }
  CRYPTO_free_ex_data(&g_ex_data_class, dest, &dest->ex_data);
  if (!CRYPTO_dup_ex_data(&g_ex_data_class, &dest->ex_data,
                          &src->ex_data)) {
    return NULL;
  }

  /* copy the rest */
  dest->enc_flag = src->enc_flag;
  dest->conv_form = src->conv_form;
  dest->version = src->version;
  dest->flags = src->flags;

  return dest;
}

EC_KEY *EC_KEY_dup(const EC_KEY *ec_key) {
  EC_KEY *ret = EC_KEY_new();
  if (ret == NULL) {
    return NULL;
  }
  if (EC_KEY_copy(ret, ec_key) == NULL) {
    EC_KEY_free(ret);
    return NULL;
  }
  return ret;
}

int EC_KEY_up_ref(EC_KEY *r) {
  CRYPTO_refcount_inc(&r->references);
  return 1;
}

int EC_KEY_is_opaque(const EC_KEY *key) {
  return key->ecdsa_meth && (key->ecdsa_meth->flags & ECDSA_FLAG_OPAQUE);
}

const EC_GROUP *EC_KEY_get0_group(const EC_KEY *key) { return key->group; }

int EC_KEY_set_group(EC_KEY *key, const EC_GROUP *group) {
  EC_GROUP_free(key->group);
  /* TODO(fork): duplicating the group seems wasteful but see
   * |EC_KEY_set_conv_form|. */
  key->group = EC_GROUP_dup(group);
  return (key->group == NULL) ? 0 : 1;
}

const BIGNUM *EC_KEY_get0_private_key(const EC_KEY *key) {
  return key->priv_key;
}

int EC_KEY_set_private_key(EC_KEY *key, const BIGNUM *priv_key) {
  BN_clear_free(key->priv_key);
  key->priv_key = BN_dup(priv_key);
  return (key->priv_key == NULL) ? 0 : 1;
}

const EC_POINT *EC_KEY_get0_public_key(const EC_KEY *key) {
  return key->pub_key;
}

int EC_KEY_set_public_key(EC_KEY *key, const EC_POINT *pub_key) {
  EC_POINT_free(key->pub_key);
  key->pub_key = EC_POINT_dup(pub_key, key->group);
  return (key->pub_key == NULL) ? 0 : 1;
}

unsigned int EC_KEY_get_enc_flags(const EC_KEY *key) { return key->enc_flag; }

void EC_KEY_set_enc_flags(EC_KEY *key, unsigned int flags) {
  key->enc_flag = flags;
}

point_conversion_form_t EC_KEY_get_conv_form(const EC_KEY *key) {
  return key->conv_form;
}

void EC_KEY_set_conv_form(EC_KEY *key, point_conversion_form_t cform) {
  key->conv_form = cform;
}

int EC_KEY_precompute_mult(EC_KEY *key, BN_CTX *ctx) {
  if (key->group == NULL) {
    return 0;
  }
  return EC_GROUP_precompute_mult(key->group, ctx);
}

int EC_KEY_check_key(const EC_KEY *eckey) {
  int ok = 0;
  BN_CTX *ctx = NULL;
  const BIGNUM *order = NULL;
  EC_POINT *point = NULL;

  if (!eckey || !eckey->group || !eckey->pub_key) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }

  if (EC_POINT_is_at_infinity(eckey->group, eckey->pub_key)) {
    OPENSSL_PUT_ERROR(EC, EC_R_POINT_AT_INFINITY);
    goto err;
  }

  ctx = BN_CTX_new();
  point = EC_POINT_new(eckey->group);

  if (ctx == NULL ||
      point == NULL) {
    goto err;
  }

  /* testing whether the pub_key is on the elliptic curve */
  if (!EC_POINT_is_on_curve(eckey->group, eckey->pub_key, ctx)) {
    OPENSSL_PUT_ERROR(EC, EC_R_POINT_IS_NOT_ON_CURVE);
    goto err;
  }
  /* testing whether pub_key * order is the point at infinity */
  /* TODO(fork): can this be skipped if the cofactor is one or if we're about
   * to check the private key, below? */
  order = &eckey->group->order;
  if (BN_is_zero(order)) {
    OPENSSL_PUT_ERROR(EC, EC_R_INVALID_GROUP_ORDER);
    goto err;
  }
  if (!EC_POINT_mul(eckey->group, point, NULL, eckey->pub_key, order, ctx)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    goto err;
  }
  if (!EC_POINT_is_at_infinity(eckey->group, point)) {
    OPENSSL_PUT_ERROR(EC, EC_R_WRONG_ORDER);
    goto err;
  }
  /* in case the priv_key is present :
   * check if generator * priv_key == pub_key
   */
  if (eckey->priv_key) {
    if (BN_cmp(eckey->priv_key, order) >= 0) {
      OPENSSL_PUT_ERROR(EC, EC_R_WRONG_ORDER);
      goto err;
    }
    if (!EC_POINT_mul(eckey->group, point, eckey->priv_key, NULL, NULL, ctx)) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }
    if (EC_POINT_cmp(eckey->group, point, eckey->pub_key, ctx) != 0) {
      OPENSSL_PUT_ERROR(EC, EC_R_INVALID_PRIVATE_KEY);
      goto err;
    }
  }
  ok = 1;

err:
  BN_CTX_free(ctx);
  EC_POINT_free(point);
  return ok;
}

int EC_KEY_set_public_key_affine_coordinates(EC_KEY *key, BIGNUM *x,
                                             BIGNUM *y) {
  BN_CTX *ctx = NULL;
  BIGNUM *tx, *ty;
  EC_POINT *point = NULL;
  int ok = 0;

  if (!key || !key->group || !x || !y) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  ctx = BN_CTX_new();
  point = EC_POINT_new(key->group);

  if (ctx == NULL ||
      point == NULL) {
    goto err;
  }

  tx = BN_CTX_get(ctx);
  ty = BN_CTX_get(ctx);

  if (!EC_POINT_set_affine_coordinates_GFp(key->group, point, x, y, ctx) ||
      !EC_POINT_get_affine_coordinates_GFp(key->group, point, tx, ty, ctx)) {
    goto err;
  }

  /* Check if retrieved coordinates match originals: if not values
   * are out of range. */
  if (BN_cmp(x, tx) || BN_cmp(y, ty)) {
    OPENSSL_PUT_ERROR(EC, EC_R_COORDINATES_OUT_OF_RANGE);
    goto err;
  }

  if (!EC_KEY_set_public_key(key, point)) {
    goto err;
  }

  if (EC_KEY_check_key(key) == 0) {
    goto err;
  }

  ok = 1;

err:
  BN_CTX_free(ctx);
  EC_POINT_free(point);
  return ok;
}

int EC_KEY_generate_key(EC_KEY *eckey) {
  int ok = 0;
  BN_CTX *ctx = NULL;
  BIGNUM *priv_key = NULL, *order = NULL;
  EC_POINT *pub_key = NULL;

  if (!eckey || !eckey->group) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }

  order = BN_new();
  ctx = BN_CTX_new();

  if (order == NULL ||
      ctx == NULL) {
    goto err;
  }

  if (eckey->priv_key == NULL) {
    priv_key = BN_new();
    if (priv_key == NULL) {
      goto err;
    }
  } else {
    priv_key = eckey->priv_key;
  }

  if (!EC_GROUP_get_order(eckey->group, order, ctx)) {
    goto err;
  }

  do {
    if (!BN_rand_range(priv_key, order)) {
      goto err;
    }
  } while (BN_is_zero(priv_key));

  if (eckey->pub_key == NULL) {
    pub_key = EC_POINT_new(eckey->group);
    if (pub_key == NULL) {
      goto err;
    }
  } else {
    pub_key = eckey->pub_key;
  }

  if (!EC_POINT_mul(eckey->group, pub_key, priv_key, NULL, NULL, ctx)) {
    goto err;
  }

  eckey->priv_key = priv_key;
  eckey->pub_key = pub_key;

  ok = 1;

err:
  BN_free(order);
  if (eckey->pub_key == NULL) {
    EC_POINT_free(pub_key);
  }
  if (eckey->priv_key == NULL) {
    BN_free(priv_key);
  }
  BN_CTX_free(ctx);
  return ok;
}

int EC_KEY_get_ex_new_index(long argl, void *argp, CRYPTO_EX_new *new_func,
                            CRYPTO_EX_dup *dup_func,
                            CRYPTO_EX_free *free_func) {
  int index;
  if (!CRYPTO_get_ex_new_index(&g_ex_data_class, &index, argl, argp, new_func,
                               dup_func, free_func)) {
    return -1;
  }
  return index;
}

int EC_KEY_set_ex_data(EC_KEY *d, int idx, void *arg) {
  return CRYPTO_set_ex_data(&d->ex_data, idx, arg);
}

void *EC_KEY_get_ex_data(const EC_KEY *d, int idx) {
  return CRYPTO_get_ex_data(&d->ex_data, idx);
}

void EC_KEY_set_asn1_flag(EC_KEY *key, int flag) {}
