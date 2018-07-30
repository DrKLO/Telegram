/* Originally written by Bodo Moeller and Nils Larsch for the OpenSSL project.
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

#include <openssl/ec.h>

#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "../bn/internal.h"
#include "../delocate.h"
#include "internal.h"


int ec_GFp_mont_group_init(EC_GROUP *group) {
  int ok;

  ok = ec_GFp_simple_group_init(group);
  group->mont = NULL;
  return ok;
}

void ec_GFp_mont_group_finish(EC_GROUP *group) {
  BN_MONT_CTX_free(group->mont);
  group->mont = NULL;
  ec_GFp_simple_group_finish(group);
}

int ec_GFp_mont_group_set_curve(EC_GROUP *group, const BIGNUM *p,
                                const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx) {
  BN_CTX *new_ctx = NULL;
  BN_MONT_CTX *mont = NULL;
  int ret = 0;

  BN_MONT_CTX_free(group->mont);
  group->mont = NULL;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  mont = BN_MONT_CTX_new();
  if (mont == NULL) {
    goto err;
  }
  if (!BN_MONT_CTX_set(mont, p, ctx)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
    goto err;
  }

  group->mont = mont;
  mont = NULL;

  ret = ec_GFp_simple_group_set_curve(group, p, a, b, ctx);

  if (!ret) {
    BN_MONT_CTX_free(group->mont);
    group->mont = NULL;
  }

err:
  BN_CTX_free(new_ctx);
  BN_MONT_CTX_free(mont);
  return ret;
}

int ec_GFp_mont_field_mul(const EC_GROUP *group, BIGNUM *r, const BIGNUM *a,
                          const BIGNUM *b, BN_CTX *ctx) {
  if (group->mont == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_NOT_INITIALIZED);
    return 0;
  }

  return BN_mod_mul_montgomery(r, a, b, group->mont, ctx);
}

int ec_GFp_mont_field_sqr(const EC_GROUP *group, BIGNUM *r, const BIGNUM *a,
                          BN_CTX *ctx) {
  if (group->mont == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_NOT_INITIALIZED);
    return 0;
  }

  return BN_mod_mul_montgomery(r, a, a, group->mont, ctx);
}

int ec_GFp_mont_field_encode(const EC_GROUP *group, BIGNUM *r, const BIGNUM *a,
                             BN_CTX *ctx) {
  if (group->mont == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_NOT_INITIALIZED);
    return 0;
  }

  return BN_to_montgomery(r, a, group->mont, ctx);
}

int ec_GFp_mont_field_decode(const EC_GROUP *group, BIGNUM *r, const BIGNUM *a,
                             BN_CTX *ctx) {
  if (group->mont == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_NOT_INITIALIZED);
    return 0;
  }

  return BN_from_montgomery(r, a, group->mont, ctx);
}

static int ec_GFp_mont_point_get_affine_coordinates(const EC_GROUP *group,
                                                    const EC_POINT *point,
                                                    BIGNUM *x, BIGNUM *y,
                                                    BN_CTX *ctx) {
  if (EC_POINT_is_at_infinity(group, point)) {
    OPENSSL_PUT_ERROR(EC, EC_R_POINT_AT_INFINITY);
    return 0;
  }

  BN_CTX *new_ctx = NULL;
  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  int ret = 0;

  BN_CTX_start(ctx);

  if (BN_cmp(&point->Z, &group->one) == 0) {
    // |point| is already affine.
    if (x != NULL && !BN_from_montgomery(x, &point->X, group->mont, ctx)) {
      goto err;
    }
    if (y != NULL && !BN_from_montgomery(y, &point->Y, group->mont, ctx)) {
      goto err;
    }
  } else {
    // transform  (X, Y, Z)  into  (x, y) := (X/Z^2, Y/Z^3)

    BIGNUM *Z_1 = BN_CTX_get(ctx);
    BIGNUM *Z_2 = BN_CTX_get(ctx);
    BIGNUM *Z_3 = BN_CTX_get(ctx);
    if (Z_1 == NULL ||
        Z_2 == NULL ||
        Z_3 == NULL) {
      goto err;
    }

    // The straightforward way to calculate the inverse of a Montgomery-encoded
    // value where the result is Montgomery-encoded is:
    //
    //    |BN_from_montgomery| + invert + |BN_to_montgomery|.
    //
    // This is equivalent, but more efficient, because |BN_from_montgomery|
    // is more efficient (at least in theory) than |BN_to_montgomery|, since it
    // doesn't have to do the multiplication before the reduction.
    //
    // Use Fermat's Little Theorem instead of |BN_mod_inverse_odd| since this
    // inversion may be done as the final step of private key operations.
    // Unfortunately, this is suboptimal for ECDSA verification.
    if (!BN_from_montgomery(Z_1, &point->Z, group->mont, ctx) ||
        !BN_from_montgomery(Z_1, Z_1, group->mont, ctx) ||
        !bn_mod_inverse_prime(Z_1, Z_1, &group->field, ctx, group->mont)) {
      goto err;
    }

    if (!BN_mod_mul_montgomery(Z_2, Z_1, Z_1, group->mont, ctx)) {
      goto err;
    }

    // Instead of using |BN_from_montgomery| to convert the |x| coordinate
    // and then calling |BN_from_montgomery| again to convert the |y|
    // coordinate below, convert the common factor |Z_2| once now, saving one
    // reduction.
    if (!BN_from_montgomery(Z_2, Z_2, group->mont, ctx)) {
      goto err;
    }

    if (x != NULL) {
      if (!BN_mod_mul_montgomery(x, &point->X, Z_2, group->mont, ctx)) {
        goto err;
      }
    }

    if (y != NULL) {
      if (!BN_mod_mul_montgomery(Z_3, Z_2, Z_1, group->mont, ctx) ||
          !BN_mod_mul_montgomery(y, &point->Y, Z_3, group->mont, ctx)) {
        goto err;
      }
    }
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

DEFINE_METHOD_FUNCTION(EC_METHOD, EC_GFp_mont_method) {
  out->group_init = ec_GFp_mont_group_init;
  out->group_finish = ec_GFp_mont_group_finish;
  out->group_set_curve = ec_GFp_mont_group_set_curve;
  out->point_get_affine_coordinates = ec_GFp_mont_point_get_affine_coordinates;
  out->mul = ec_wNAF_mul /* XXX: Not constant time. */;
  out->mul_public = ec_wNAF_mul;
  out->field_mul = ec_GFp_mont_field_mul;
  out->field_sqr = ec_GFp_mont_field_sqr;
  out->field_encode = ec_GFp_mont_field_encode;
  out->field_decode = ec_GFp_mont_field_decode;
}
