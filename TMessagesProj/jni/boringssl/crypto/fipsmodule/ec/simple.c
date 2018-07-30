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

#include <openssl/ec.h>

#include <string.h>

#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"
#include "../../internal.h"


// Most method functions in this file are designed to work with non-trivial
// representations of field elements if necessary (see ecp_mont.c): while
// standard modular addition and subtraction are used, the field_mul and
// field_sqr methods will be used for multiplication, and field_encode and
// field_decode (if defined) will be used for converting between
// representations.
//
// Functions here specifically assume that if a non-trivial representation is
// used, it is a Montgomery representation (i.e. 'encoding' means multiplying
// by some factor R).

int ec_GFp_simple_group_init(EC_GROUP *group) {
  BN_init(&group->field);
  BN_init(&group->a);
  BN_init(&group->b);
  BN_init(&group->one);
  group->a_is_minus3 = 0;
  return 1;
}

void ec_GFp_simple_group_finish(EC_GROUP *group) {
  BN_free(&group->field);
  BN_free(&group->a);
  BN_free(&group->b);
  BN_free(&group->one);
}

int ec_GFp_simple_group_set_curve(EC_GROUP *group, const BIGNUM *p,
                                  const BIGNUM *a, const BIGNUM *b,
                                  BN_CTX *ctx) {
  int ret = 0;
  BN_CTX *new_ctx = NULL;
  BIGNUM *tmp_a;

  // p must be a prime > 3
  if (BN_num_bits(p) <= 2 || !BN_is_odd(p)) {
    OPENSSL_PUT_ERROR(EC, EC_R_INVALID_FIELD);
    return 0;
  }

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  BN_CTX_start(ctx);
  tmp_a = BN_CTX_get(ctx);
  if (tmp_a == NULL) {
    goto err;
  }

  // group->field
  if (!BN_copy(&group->field, p)) {
    goto err;
  }
  BN_set_negative(&group->field, 0);

  // group->a
  if (!BN_nnmod(tmp_a, a, p, ctx)) {
    goto err;
  }
  if (group->meth->field_encode) {
    if (!group->meth->field_encode(group, &group->a, tmp_a, ctx)) {
      goto err;
    }
  } else if (!BN_copy(&group->a, tmp_a)) {
    goto err;
  }

  // group->b
  if (!BN_nnmod(&group->b, b, p, ctx)) {
    goto err;
  }
  if (group->meth->field_encode &&
      !group->meth->field_encode(group, &group->b, &group->b, ctx)) {
    goto err;
  }

  // group->a_is_minus3
  if (!BN_add_word(tmp_a, 3)) {
    goto err;
  }
  group->a_is_minus3 = (0 == BN_cmp(tmp_a, &group->field));

  if (group->meth->field_encode != NULL) {
    if (!group->meth->field_encode(group, &group->one, BN_value_one(), ctx)) {
      goto err;
    }
  } else if (!BN_copy(&group->one, BN_value_one())) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_group_get_curve(const EC_GROUP *group, BIGNUM *p, BIGNUM *a,
                                  BIGNUM *b, BN_CTX *ctx) {
  int ret = 0;
  BN_CTX *new_ctx = NULL;

  if (p != NULL && !BN_copy(p, &group->field)) {
    return 0;
  }

  if (a != NULL || b != NULL) {
    if (group->meth->field_decode) {
      if (ctx == NULL) {
        ctx = new_ctx = BN_CTX_new();
        if (ctx == NULL) {
          return 0;
        }
      }
      if (a != NULL && !group->meth->field_decode(group, a, &group->a, ctx)) {
        goto err;
      }
      if (b != NULL && !group->meth->field_decode(group, b, &group->b, ctx)) {
        goto err;
      }
    } else {
      if (a != NULL && !BN_copy(a, &group->a)) {
        goto err;
      }
      if (b != NULL && !BN_copy(b, &group->b)) {
        goto err;
      }
    }
  }

  ret = 1;

err:
  BN_CTX_free(new_ctx);
  return ret;
}

unsigned ec_GFp_simple_group_get_degree(const EC_GROUP *group) {
  return BN_num_bits(&group->field);
}

int ec_GFp_simple_point_init(EC_POINT *point) {
  BN_init(&point->X);
  BN_init(&point->Y);
  BN_init(&point->Z);

  return 1;
}

void ec_GFp_simple_point_finish(EC_POINT *point) {
  BN_free(&point->X);
  BN_free(&point->Y);
  BN_free(&point->Z);
}

int ec_GFp_simple_point_copy(EC_POINT *dest, const EC_POINT *src) {
  if (!BN_copy(&dest->X, &src->X) ||
      !BN_copy(&dest->Y, &src->Y) ||
      !BN_copy(&dest->Z, &src->Z)) {
    return 0;
  }

  return 1;
}

int ec_GFp_simple_point_set_to_infinity(const EC_GROUP *group,
                                        EC_POINT *point) {
  BN_zero(&point->Z);
  return 1;
}

static int set_Jprojective_coordinate_GFp(const EC_GROUP *group, BIGNUM *out,
                                          const BIGNUM *in, BN_CTX *ctx) {
  if (in == NULL) {
    return 1;
  }
  if (BN_is_negative(in) ||
      BN_cmp(in, &group->field) >= 0) {
    OPENSSL_PUT_ERROR(EC, EC_R_COORDINATES_OUT_OF_RANGE);
    return 0;
  }
  if (group->meth->field_encode) {
    return group->meth->field_encode(group, out, in, ctx);
  }
  return BN_copy(out, in) != NULL;
}

int ec_GFp_simple_point_set_affine_coordinates(const EC_GROUP *group,
                                               EC_POINT *point, const BIGNUM *x,
                                               const BIGNUM *y, BN_CTX *ctx) {
  if (x == NULL || y == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }

  BN_CTX *new_ctx = NULL;
  int ret = 0;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  if (!set_Jprojective_coordinate_GFp(group, &point->X, x, ctx) ||
      !set_Jprojective_coordinate_GFp(group, &point->Y, y, ctx) ||
      !BN_copy(&point->Z, &group->one)) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_add(const EC_GROUP *group, EC_POINT *r, const EC_POINT *a,
                      const EC_POINT *b, BN_CTX *ctx) {
  int (*field_mul)(const EC_GROUP *, BIGNUM *, const BIGNUM *, const BIGNUM *,
                   BN_CTX *);
  int (*field_sqr)(const EC_GROUP *, BIGNUM *, const BIGNUM *, BN_CTX *);
  const BIGNUM *p;
  BN_CTX *new_ctx = NULL;
  BIGNUM *n0, *n1, *n2, *n3, *n4, *n5, *n6;
  int ret = 0;

  if (a == b) {
    return EC_POINT_dbl(group, r, a, ctx);
  }
  if (EC_POINT_is_at_infinity(group, a)) {
    return EC_POINT_copy(r, b);
  }
  if (EC_POINT_is_at_infinity(group, b)) {
    return EC_POINT_copy(r, a);
  }

  field_mul = group->meth->field_mul;
  field_sqr = group->meth->field_sqr;
  p = &group->field;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  BN_CTX_start(ctx);
  n0 = BN_CTX_get(ctx);
  n1 = BN_CTX_get(ctx);
  n2 = BN_CTX_get(ctx);
  n3 = BN_CTX_get(ctx);
  n4 = BN_CTX_get(ctx);
  n5 = BN_CTX_get(ctx);
  n6 = BN_CTX_get(ctx);
  if (n6 == NULL) {
    goto end;
  }

  // Note that in this function we must not read components of 'a' or 'b'
  // once we have written the corresponding components of 'r'.
  // ('r' might be one of 'a' or 'b'.)

  // n1, n2
  int b_Z_is_one = BN_cmp(&b->Z, &group->one) == 0;

  if (b_Z_is_one) {
    if (!BN_copy(n1, &a->X) || !BN_copy(n2, &a->Y)) {
      goto end;
    }
    // n1 = X_a
    // n2 = Y_a
  } else {
    if (!field_sqr(group, n0, &b->Z, ctx) ||
        !field_mul(group, n1, &a->X, n0, ctx)) {
      goto end;
    }
    // n1 = X_a * Z_b^2

    if (!field_mul(group, n0, n0, &b->Z, ctx) ||
        !field_mul(group, n2, &a->Y, n0, ctx)) {
      goto end;
    }
    // n2 = Y_a * Z_b^3
  }

  // n3, n4
  int a_Z_is_one = BN_cmp(&a->Z, &group->one) == 0;
  if (a_Z_is_one) {
    if (!BN_copy(n3, &b->X) || !BN_copy(n4, &b->Y)) {
      goto end;
    }
    // n3 = X_b
    // n4 = Y_b
  } else {
    if (!field_sqr(group, n0, &a->Z, ctx) ||
        !field_mul(group, n3, &b->X, n0, ctx)) {
      goto end;
    }
    // n3 = X_b * Z_a^2

    if (!field_mul(group, n0, n0, &a->Z, ctx) ||
        !field_mul(group, n4, &b->Y, n0, ctx)) {
      goto end;
    }
    // n4 = Y_b * Z_a^3
  }

  // n5, n6
  if (!BN_mod_sub_quick(n5, n1, n3, p) ||
      !BN_mod_sub_quick(n6, n2, n4, p)) {
    goto end;
  }
  // n5 = n1 - n3
  // n6 = n2 - n4

  if (BN_is_zero(n5)) {
    if (BN_is_zero(n6)) {
      // a is the same point as b
      BN_CTX_end(ctx);
      ret = EC_POINT_dbl(group, r, a, ctx);
      ctx = NULL;
      goto end;
    } else {
      // a is the inverse of b
      BN_zero(&r->Z);
      ret = 1;
      goto end;
    }
  }

  // 'n7', 'n8'
  if (!BN_mod_add_quick(n1, n1, n3, p) ||
      !BN_mod_add_quick(n2, n2, n4, p)) {
    goto end;
  }
  // 'n7' = n1 + n3
  // 'n8' = n2 + n4

  // Z_r
  if (a_Z_is_one && b_Z_is_one) {
    if (!BN_copy(&r->Z, n5)) {
      goto end;
    }
  } else {
    if (a_Z_is_one) {
      if (!BN_copy(n0, &b->Z)) {
        goto end;
      }
    } else if (b_Z_is_one) {
      if (!BN_copy(n0, &a->Z)) {
        goto end;
      }
    } else if (!field_mul(group, n0, &a->Z, &b->Z, ctx)) {
      goto end;
    }
    if (!field_mul(group, &r->Z, n0, n5, ctx)) {
      goto end;
    }
  }

  // Z_r = Z_a * Z_b * n5

  // X_r
  if (!field_sqr(group, n0, n6, ctx) ||
      !field_sqr(group, n4, n5, ctx) ||
      !field_mul(group, n3, n1, n4, ctx) ||
      !BN_mod_sub_quick(&r->X, n0, n3, p)) {
    goto end;
  }
  // X_r = n6^2 - n5^2 * 'n7'

  // 'n9'
  if (!BN_mod_lshift1_quick(n0, &r->X, p) ||
      !BN_mod_sub_quick(n0, n3, n0, p)) {
    goto end;
  }
  // n9 = n5^2 * 'n7' - 2 * X_r

  // Y_r
  if (!field_mul(group, n0, n0, n6, ctx) ||
      !field_mul(group, n5, n4, n5, ctx)) {
    goto end;  // now n5 is n5^3
  }
  if (!field_mul(group, n1, n2, n5, ctx) ||
      !BN_mod_sub_quick(n0, n0, n1, p)) {
    goto end;
  }
  if (BN_is_odd(n0) && !BN_add(n0, n0, p)) {
    goto end;
  }
  // now  0 <= n0 < 2*p,  and n0 is even
  if (!BN_rshift1(&r->Y, n0)) {
    goto end;
  }
  // Y_r = (n6 * 'n9' - 'n8' * 'n5^3') / 2

  ret = 1;

end:
  if (ctx) {
    // otherwise we already called BN_CTX_end
    BN_CTX_end(ctx);
  }
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_dbl(const EC_GROUP *group, EC_POINT *r, const EC_POINT *a,
                      BN_CTX *ctx) {
  int (*field_mul)(const EC_GROUP *, BIGNUM *, const BIGNUM *, const BIGNUM *,
                   BN_CTX *);
  int (*field_sqr)(const EC_GROUP *, BIGNUM *, const BIGNUM *, BN_CTX *);
  const BIGNUM *p;
  BN_CTX *new_ctx = NULL;
  BIGNUM *n0, *n1, *n2, *n3;
  int ret = 0;

  if (EC_POINT_is_at_infinity(group, a)) {
    BN_zero(&r->Z);
    return 1;
  }

  field_mul = group->meth->field_mul;
  field_sqr = group->meth->field_sqr;
  p = &group->field;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  BN_CTX_start(ctx);
  n0 = BN_CTX_get(ctx);
  n1 = BN_CTX_get(ctx);
  n2 = BN_CTX_get(ctx);
  n3 = BN_CTX_get(ctx);
  if (n3 == NULL) {
    goto err;
  }

  // Note that in this function we must not read components of 'a'
  // once we have written the corresponding components of 'r'.
  // ('r' might the same as 'a'.)

  // n1
  if (BN_cmp(&a->Z, &group->one) == 0) {
    if (!field_sqr(group, n0, &a->X, ctx) ||
        !BN_mod_lshift1_quick(n1, n0, p) ||
        !BN_mod_add_quick(n0, n0, n1, p) ||
        !BN_mod_add_quick(n1, n0, &group->a, p)) {
      goto err;
    }
    // n1 = 3 * X_a^2 + a_curve
  } else if (group->a_is_minus3) {
    if (!field_sqr(group, n1, &a->Z, ctx) ||
        !BN_mod_add_quick(n0, &a->X, n1, p) ||
        !BN_mod_sub_quick(n2, &a->X, n1, p) ||
        !field_mul(group, n1, n0, n2, ctx) ||
        !BN_mod_lshift1_quick(n0, n1, p) ||
        !BN_mod_add_quick(n1, n0, n1, p)) {
      goto err;
    }
    // n1 = 3 * (X_a + Z_a^2) * (X_a - Z_a^2)
    //    = 3 * X_a^2 - 3 * Z_a^4
  } else {
    if (!field_sqr(group, n0, &a->X, ctx) ||
        !BN_mod_lshift1_quick(n1, n0, p) ||
        !BN_mod_add_quick(n0, n0, n1, p) ||
        !field_sqr(group, n1, &a->Z, ctx) ||
        !field_sqr(group, n1, n1, ctx) ||
        !field_mul(group, n1, n1, &group->a, ctx) ||
        !BN_mod_add_quick(n1, n1, n0, p)) {
      goto err;
    }
    // n1 = 3 * X_a^2 + a_curve * Z_a^4
  }

  // Z_r
  if (BN_cmp(&a->Z, &group->one) == 0) {
    if (!BN_copy(n0, &a->Y)) {
      goto err;
    }
  } else if (!field_mul(group, n0, &a->Y, &a->Z, ctx)) {
    goto err;
  }
  if (!BN_mod_lshift1_quick(&r->Z, n0, p)) {
    goto err;
  }
  // Z_r = 2 * Y_a * Z_a

  // n2
  if (!field_sqr(group, n3, &a->Y, ctx) ||
      !field_mul(group, n2, &a->X, n3, ctx) ||
      !BN_mod_lshift_quick(n2, n2, 2, p)) {
    goto err;
  }
  // n2 = 4 * X_a * Y_a^2

  // X_r
  if (!BN_mod_lshift1_quick(n0, n2, p) ||
      !field_sqr(group, &r->X, n1, ctx) ||
      !BN_mod_sub_quick(&r->X, &r->X, n0, p)) {
    goto err;
  }
  // X_r = n1^2 - 2 * n2

  // n3
  if (!field_sqr(group, n0, n3, ctx) ||
      !BN_mod_lshift_quick(n3, n0, 3, p)) {
    goto err;
  }
  // n3 = 8 * Y_a^4

  // Y_r
  if (!BN_mod_sub_quick(n0, n2, &r->X, p) ||
      !field_mul(group, n0, n1, n0, ctx) ||
      !BN_mod_sub_quick(&r->Y, n0, n3, p)) {
    goto err;
  }
  // Y_r = n1 * (n2 - X_r) - n3

  ret = 1;

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_invert(const EC_GROUP *group, EC_POINT *point, BN_CTX *ctx) {
  if (EC_POINT_is_at_infinity(group, point) || BN_is_zero(&point->Y)) {
    // point is its own inverse
    return 1;
  }

  return BN_usub(&point->Y, &group->field, &point->Y);
}

int ec_GFp_simple_is_at_infinity(const EC_GROUP *group, const EC_POINT *point) {
  return BN_is_zero(&point->Z);
}

int ec_GFp_simple_is_on_curve(const EC_GROUP *group, const EC_POINT *point,
                              BN_CTX *ctx) {
  int (*field_mul)(const EC_GROUP *, BIGNUM *, const BIGNUM *, const BIGNUM *,
                   BN_CTX *);
  int (*field_sqr)(const EC_GROUP *, BIGNUM *, const BIGNUM *, BN_CTX *);
  const BIGNUM *p;
  BN_CTX *new_ctx = NULL;
  BIGNUM *rh, *tmp, *Z4, *Z6;
  int ret = 0;

  if (EC_POINT_is_at_infinity(group, point)) {
    return 1;
  }

  field_mul = group->meth->field_mul;
  field_sqr = group->meth->field_sqr;
  p = &group->field;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  BN_CTX_start(ctx);
  rh = BN_CTX_get(ctx);
  tmp = BN_CTX_get(ctx);
  Z4 = BN_CTX_get(ctx);
  Z6 = BN_CTX_get(ctx);
  if (Z6 == NULL) {
    goto err;
  }

  // We have a curve defined by a Weierstrass equation
  //      y^2 = x^3 + a*x + b.
  // The point to consider is given in Jacobian projective coordinates
  // where  (X, Y, Z)  represents  (x, y) = (X/Z^2, Y/Z^3).
  // Substituting this and multiplying by  Z^6  transforms the above equation
  // into
  //      Y^2 = X^3 + a*X*Z^4 + b*Z^6.
  // To test this, we add up the right-hand side in 'rh'.

  // rh := X^2
  if (!field_sqr(group, rh, &point->X, ctx)) {
    goto err;
  }

  if (BN_cmp(&point->Z, &group->one) != 0) {
    if (!field_sqr(group, tmp, &point->Z, ctx) ||
        !field_sqr(group, Z4, tmp, ctx) ||
        !field_mul(group, Z6, Z4, tmp, ctx)) {
      goto err;
    }

    // rh := (rh + a*Z^4)*X
    if (group->a_is_minus3) {
      if (!BN_mod_lshift1_quick(tmp, Z4, p) ||
          !BN_mod_add_quick(tmp, tmp, Z4, p) ||
          !BN_mod_sub_quick(rh, rh, tmp, p) ||
          !field_mul(group, rh, rh, &point->X, ctx)) {
        goto err;
      }
    } else {
      if (!field_mul(group, tmp, Z4, &group->a, ctx) ||
          !BN_mod_add_quick(rh, rh, tmp, p) ||
          !field_mul(group, rh, rh, &point->X, ctx)) {
        goto err;
      }
    }

    // rh := rh + b*Z^6
    if (!field_mul(group, tmp, &group->b, Z6, ctx) ||
        !BN_mod_add_quick(rh, rh, tmp, p)) {
      goto err;
    }
  } else {
    // rh := (rh + a)*X
    if (!BN_mod_add_quick(rh, rh, &group->a, p) ||
        !field_mul(group, rh, rh, &point->X, ctx)) {
      goto err;
    }
    // rh := rh + b
    if (!BN_mod_add_quick(rh, rh, &group->b, p)) {
      goto err;
    }
  }

  // 'lh' := Y^2
  if (!field_sqr(group, tmp, &point->Y, ctx)) {
    goto err;
  }

  ret = (0 == BN_ucmp(tmp, rh));

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_cmp(const EC_GROUP *group, const EC_POINT *a,
                      const EC_POINT *b, BN_CTX *ctx) {
  // return values:
  //  -1   error
  //   0   equal (in affine coordinates)
  //   1   not equal

  int (*field_mul)(const EC_GROUP *, BIGNUM *, const BIGNUM *, const BIGNUM *,
                   BN_CTX *);
  int (*field_sqr)(const EC_GROUP *, BIGNUM *, const BIGNUM *, BN_CTX *);
  BN_CTX *new_ctx = NULL;
  BIGNUM *tmp1, *tmp2, *Za23, *Zb23;
  const BIGNUM *tmp1_, *tmp2_;
  int ret = -1;

  if (ec_GFp_simple_is_at_infinity(group, a)) {
    return ec_GFp_simple_is_at_infinity(group, b) ? 0 : 1;
  }

  if (ec_GFp_simple_is_at_infinity(group, b)) {
    return 1;
  }

  int a_Z_is_one = BN_cmp(&a->Z, &group->one) == 0;
  int b_Z_is_one = BN_cmp(&b->Z, &group->one) == 0;

  if (a_Z_is_one && b_Z_is_one) {
    return ((BN_cmp(&a->X, &b->X) == 0) && BN_cmp(&a->Y, &b->Y) == 0) ? 0 : 1;
  }

  field_mul = group->meth->field_mul;
  field_sqr = group->meth->field_sqr;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return -1;
    }
  }

  BN_CTX_start(ctx);
  tmp1 = BN_CTX_get(ctx);
  tmp2 = BN_CTX_get(ctx);
  Za23 = BN_CTX_get(ctx);
  Zb23 = BN_CTX_get(ctx);
  if (Zb23 == NULL) {
    goto end;
  }

  // We have to decide whether
  //     (X_a/Z_a^2, Y_a/Z_a^3) = (X_b/Z_b^2, Y_b/Z_b^3),
  // or equivalently, whether
  //     (X_a*Z_b^2, Y_a*Z_b^3) = (X_b*Z_a^2, Y_b*Z_a^3).

  if (!b_Z_is_one) {
    if (!field_sqr(group, Zb23, &b->Z, ctx) ||
        !field_mul(group, tmp1, &a->X, Zb23, ctx)) {
      goto end;
    }
    tmp1_ = tmp1;
  } else {
    tmp1_ = &a->X;
  }
  if (!a_Z_is_one) {
    if (!field_sqr(group, Za23, &a->Z, ctx) ||
        !field_mul(group, tmp2, &b->X, Za23, ctx)) {
      goto end;
    }
    tmp2_ = tmp2;
  } else {
    tmp2_ = &b->X;
  }

  // compare  X_a*Z_b^2  with  X_b*Z_a^2
  if (BN_cmp(tmp1_, tmp2_) != 0) {
    ret = 1;  // points differ
    goto end;
  }


  if (!b_Z_is_one) {
    if (!field_mul(group, Zb23, Zb23, &b->Z, ctx) ||
        !field_mul(group, tmp1, &a->Y, Zb23, ctx)) {
      goto end;
    }
    // tmp1_ = tmp1
  } else {
    tmp1_ = &a->Y;
  }
  if (!a_Z_is_one) {
    if (!field_mul(group, Za23, Za23, &a->Z, ctx) ||
        !field_mul(group, tmp2, &b->Y, Za23, ctx)) {
      goto end;
    }
    // tmp2_ = tmp2
  } else {
    tmp2_ = &b->Y;
  }

  // compare  Y_a*Z_b^3  with  Y_b*Z_a^3
  if (BN_cmp(tmp1_, tmp2_) != 0) {
    ret = 1;  // points differ
    goto end;
  }

  // points are equal
  ret = 0;

end:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_make_affine(const EC_GROUP *group, EC_POINT *point,
                              BN_CTX *ctx) {
  BN_CTX *new_ctx = NULL;
  BIGNUM *x, *y;
  int ret = 0;

  if (BN_cmp(&point->Z, &group->one) == 0 ||
      EC_POINT_is_at_infinity(group, point)) {
    return 1;
  }

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  BN_CTX_start(ctx);
  x = BN_CTX_get(ctx);
  y = BN_CTX_get(ctx);
  if (y == NULL) {
    goto err;
  }

  if (!EC_POINT_get_affine_coordinates_GFp(group, point, x, y, ctx) ||
      !EC_POINT_set_affine_coordinates_GFp(group, point, x, y, ctx)) {
    goto err;
  }
  if (BN_cmp(&point->Z, &group->one) != 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_points_make_affine(const EC_GROUP *group, size_t num,
                                     EC_POINT *points[], BN_CTX *ctx) {
  BN_CTX *new_ctx = NULL;
  BIGNUM *tmp, *tmp_Z;
  BIGNUM **prod_Z = NULL;
  int ret = 0;

  if (num == 0) {
    return 1;
  }

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      return 0;
    }
  }

  BN_CTX_start(ctx);
  tmp = BN_CTX_get(ctx);
  tmp_Z = BN_CTX_get(ctx);
  if (tmp == NULL || tmp_Z == NULL) {
    goto err;
  }

  prod_Z = OPENSSL_malloc(num * sizeof(prod_Z[0]));
  if (prod_Z == NULL) {
    goto err;
  }
  OPENSSL_memset(prod_Z, 0, num * sizeof(prod_Z[0]));
  for (size_t i = 0; i < num; i++) {
    prod_Z[i] = BN_new();
    if (prod_Z[i] == NULL) {
      goto err;
    }
  }

  // Set each prod_Z[i] to the product of points[0]->Z .. points[i]->Z,
  // skipping any zero-valued inputs (pretend that they're 1).

  if (!BN_is_zero(&points[0]->Z)) {
    if (!BN_copy(prod_Z[0], &points[0]->Z)) {
      goto err;
    }
  } else {
    if (BN_copy(prod_Z[0], &group->one) == NULL) {
      goto err;
    }
  }

  for (size_t i = 1; i < num; i++) {
    if (!BN_is_zero(&points[i]->Z)) {
      if (!group->meth->field_mul(group, prod_Z[i], prod_Z[i - 1],
                                  &points[i]->Z, ctx)) {
        goto err;
      }
    } else {
      if (!BN_copy(prod_Z[i], prod_Z[i - 1])) {
        goto err;
      }
    }
  }

  // Now use a single explicit inversion to replace every non-zero points[i]->Z
  // by its inverse. We use |BN_mod_inverse_odd| instead of doing a constant-
  // time inversion using Fermat's Little Theorem because this function is
  // usually only used for converting multiples of a public key point to
  // affine, and a public key point isn't secret. If we were to use Fermat's
  // Little Theorem then the cost of the inversion would usually be so high
  // that converting the multiples to affine would be counterproductive.
  int no_inverse;
  if (!BN_mod_inverse_odd(tmp, &no_inverse, prod_Z[num - 1], &group->field,
                          ctx)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
    goto err;
  }

  if (group->meth->field_encode != NULL) {
    // In the Montgomery case, we just turned R*H (representing H)
    // into 1/(R*H), but we need R*(1/H) (representing 1/H);
    // i.e. we need to multiply by the Montgomery factor twice.
    if (!group->meth->field_encode(group, tmp, tmp, ctx) ||
        !group->meth->field_encode(group, tmp, tmp, ctx)) {
      goto err;
    }
  }

  for (size_t i = num - 1; i > 0; --i) {
    // Loop invariant: tmp is the product of the inverses of
    // points[0]->Z .. points[i]->Z (zero-valued inputs skipped).
    if (BN_is_zero(&points[i]->Z)) {
      continue;
    }

    // Set tmp_Z to the inverse of points[i]->Z (as product
    // of Z inverses 0 .. i, Z values 0 .. i - 1).
    if (!group->meth->field_mul(group, tmp_Z, prod_Z[i - 1], tmp, ctx) ||
        // Update tmp to satisfy the loop invariant for i - 1.
        !group->meth->field_mul(group, tmp, tmp, &points[i]->Z, ctx) ||
        // Replace points[i]->Z by its inverse.
        !BN_copy(&points[i]->Z, tmp_Z)) {
      goto err;
    }
  }

  // Replace points[0]->Z by its inverse.
  if (!BN_is_zero(&points[0]->Z) && !BN_copy(&points[0]->Z, tmp)) {
    goto err;
  }

  // Finally, fix up the X and Y coordinates for all points.
  for (size_t i = 0; i < num; i++) {
    EC_POINT *p = points[i];

    if (!BN_is_zero(&p->Z)) {
      // turn (X, Y, 1/Z) into (X/Z^2, Y/Z^3, 1).
      if (!group->meth->field_sqr(group, tmp, &p->Z, ctx) ||
          !group->meth->field_mul(group, &p->X, &p->X, tmp, ctx) ||
          !group->meth->field_mul(group, tmp, tmp, &p->Z, ctx) ||
          !group->meth->field_mul(group, &p->Y, &p->Y, tmp, ctx)) {
        goto err;
      }

      if (BN_copy(&p->Z, &group->one) == NULL) {
        goto err;
      }
    }
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  if (prod_Z != NULL) {
    for (size_t i = 0; i < num; i++) {
      if (prod_Z[i] == NULL) {
        break;
      }
      BN_clear_free(prod_Z[i]);
    }
    OPENSSL_free(prod_Z);
  }

  return ret;
}

int ec_GFp_simple_field_mul(const EC_GROUP *group, BIGNUM *r, const BIGNUM *a,
                            const BIGNUM *b, BN_CTX *ctx) {
  return BN_mod_mul(r, a, b, &group->field, ctx);
}

int ec_GFp_simple_field_sqr(const EC_GROUP *group, BIGNUM *r, const BIGNUM *a,
                            BN_CTX *ctx) {
  return BN_mod_sqr(r, a, &group->field, ctx);
}
