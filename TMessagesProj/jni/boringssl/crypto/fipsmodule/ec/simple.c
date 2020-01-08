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
  group->a_is_minus3 = 0;
  return 1;
}

void ec_GFp_simple_group_finish(EC_GROUP *group) {
  BN_free(&group->field);
}

int ec_GFp_simple_group_set_curve(EC_GROUP *group, const BIGNUM *p,
                                  const BIGNUM *a, const BIGNUM *b,
                                  BN_CTX *ctx) {
  int ret = 0;
  BN_CTX *new_ctx = NULL;

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
  BIGNUM *tmp = BN_CTX_get(ctx);
  if (tmp == NULL) {
    goto err;
  }

  // group->field
  if (!BN_copy(&group->field, p)) {
    goto err;
  }
  BN_set_negative(&group->field, 0);
  // Store the field in minimal form, so it can be used with |BN_ULONG| arrays.
  bn_set_minimal_width(&group->field);

  // group->a
  if (!BN_nnmod(tmp, a, &group->field, ctx) ||
      !ec_bignum_to_felem(group, &group->a, tmp)) {
    goto err;
  }

  // group->a_is_minus3
  if (!BN_add_word(tmp, 3)) {
    goto err;
  }
  group->a_is_minus3 = (0 == BN_cmp(tmp, &group->field));

  // group->b
  if (!BN_nnmod(tmp, b, &group->field, ctx) ||
      !ec_bignum_to_felem(group, &group->b, tmp)) {
    goto err;
  }

  if (!ec_bignum_to_felem(group, &group->one, BN_value_one())) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  BN_CTX_free(new_ctx);
  return ret;
}

int ec_GFp_simple_group_get_curve(const EC_GROUP *group, BIGNUM *p, BIGNUM *a,
                                  BIGNUM *b) {
  if ((p != NULL && !BN_copy(p, &group->field)) ||
      (a != NULL && !ec_felem_to_bignum(group, a, &group->a)) ||
      (b != NULL && !ec_felem_to_bignum(group, b, &group->b))) {
    return 0;
  }
  return 1;
}

void ec_GFp_simple_point_init(EC_RAW_POINT *point) {
  OPENSSL_memset(&point->X, 0, sizeof(EC_FELEM));
  OPENSSL_memset(&point->Y, 0, sizeof(EC_FELEM));
  OPENSSL_memset(&point->Z, 0, sizeof(EC_FELEM));
}

void ec_GFp_simple_point_copy(EC_RAW_POINT *dest, const EC_RAW_POINT *src) {
  OPENSSL_memcpy(&dest->X, &src->X, sizeof(EC_FELEM));
  OPENSSL_memcpy(&dest->Y, &src->Y, sizeof(EC_FELEM));
  OPENSSL_memcpy(&dest->Z, &src->Z, sizeof(EC_FELEM));
}

void ec_GFp_simple_point_set_to_infinity(const EC_GROUP *group,
                                         EC_RAW_POINT *point) {
  // Although it is strictly only necessary to zero Z, we zero the entire point
  // in case |point| was stack-allocated and yet to be initialized.
  ec_GFp_simple_point_init(point);
}

int ec_GFp_simple_point_set_affine_coordinates(const EC_GROUP *group,
                                               EC_RAW_POINT *point,
                                               const BIGNUM *x,
                                               const BIGNUM *y) {
  if (x == NULL || y == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }

  if (!ec_bignum_to_felem(group, &point->X, x) ||
      !ec_bignum_to_felem(group, &point->Y, y)) {
    return 0;
  }
  OPENSSL_memcpy(&point->Z, &group->one, sizeof(EC_FELEM));

  return 1;
}

void ec_GFp_simple_invert(const EC_GROUP *group, EC_RAW_POINT *point) {
  ec_felem_neg(group, &point->Y, &point->Y);
}

int ec_GFp_simple_is_at_infinity(const EC_GROUP *group,
                                 const EC_RAW_POINT *point) {
  return ec_felem_non_zero_mask(group, &point->Z) == 0;
}

int ec_GFp_simple_is_on_curve(const EC_GROUP *group,
                              const EC_RAW_POINT *point) {
  if (ec_GFp_simple_is_at_infinity(group, point)) {
    return 1;
  }

  // We have a curve defined by a Weierstrass equation
  //      y^2 = x^3 + a*x + b.
  // The point to consider is given in Jacobian projective coordinates
  // where  (X, Y, Z)  represents  (x, y) = (X/Z^2, Y/Z^3).
  // Substituting this and multiplying by  Z^6  transforms the above equation
  // into
  //      Y^2 = X^3 + a*X*Z^4 + b*Z^6.
  // To test this, we add up the right-hand side in 'rh'.

  void (*const felem_mul)(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a,
                          const EC_FELEM *b) = group->meth->felem_mul;
  void (*const felem_sqr)(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a) =
      group->meth->felem_sqr;

  // rh := X^2
  EC_FELEM rh;
  felem_sqr(group, &rh, &point->X);

  EC_FELEM tmp, Z4, Z6;
  if (!ec_felem_equal(group, &point->Z, &group->one)) {
    felem_sqr(group, &tmp, &point->Z);
    felem_sqr(group, &Z4, &tmp);
    felem_mul(group, &Z6, &Z4, &tmp);

    // rh := (rh + a*Z^4)*X
    if (group->a_is_minus3) {
      ec_felem_add(group, &tmp, &Z4, &Z4);
      ec_felem_add(group, &tmp, &tmp, &Z4);
      ec_felem_sub(group, &rh, &rh, &tmp);
      felem_mul(group, &rh, &rh, &point->X);
    } else {
      felem_mul(group, &tmp, &Z4, &group->a);
      ec_felem_add(group, &rh, &rh, &tmp);
      felem_mul(group, &rh, &rh, &point->X);
    }

    // rh := rh + b*Z^6
    felem_mul(group, &tmp, &group->b, &Z6);
    ec_felem_add(group, &rh, &rh, &tmp);
  } else {
    // rh := (rh + a)*X
    ec_felem_add(group, &rh, &rh, &group->a);
    felem_mul(group, &rh, &rh, &point->X);
    // rh := rh + b
    ec_felem_add(group, &rh, &rh, &group->b);
  }

  // 'lh' := Y^2
  felem_sqr(group, &tmp, &point->Y);
  return ec_felem_equal(group, &tmp, &rh);
}

int ec_GFp_simple_cmp(const EC_GROUP *group, const EC_RAW_POINT *a,
                      const EC_RAW_POINT *b) {
  // Note this function returns zero if |a| and |b| are equal and 1 if they are
  // not equal.
  if (ec_GFp_simple_is_at_infinity(group, a)) {
    return ec_GFp_simple_is_at_infinity(group, b) ? 0 : 1;
  }

  if (ec_GFp_simple_is_at_infinity(group, b)) {
    return 1;
  }

  int a_Z_is_one = ec_felem_equal(group, &a->Z, &group->one);
  int b_Z_is_one = ec_felem_equal(group, &b->Z, &group->one);

  if (a_Z_is_one && b_Z_is_one) {
    return !ec_felem_equal(group, &a->X, &b->X) ||
           !ec_felem_equal(group, &a->Y, &b->Y);
  }

  void (*const felem_mul)(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a,
                          const EC_FELEM *b) = group->meth->felem_mul;
  void (*const felem_sqr)(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a) =
      group->meth->felem_sqr;

  // We have to decide whether
  //     (X_a/Z_a^2, Y_a/Z_a^3) = (X_b/Z_b^2, Y_b/Z_b^3),
  // or equivalently, whether
  //     (X_a*Z_b^2, Y_a*Z_b^3) = (X_b*Z_a^2, Y_b*Z_a^3).

  EC_FELEM tmp1, tmp2, Za23, Zb23;
  const EC_FELEM *tmp1_, *tmp2_;
  if (!b_Z_is_one) {
    felem_sqr(group, &Zb23, &b->Z);
    felem_mul(group, &tmp1, &a->X, &Zb23);
    tmp1_ = &tmp1;
  } else {
    tmp1_ = &a->X;
  }
  if (!a_Z_is_one) {
    felem_sqr(group, &Za23, &a->Z);
    felem_mul(group, &tmp2, &b->X, &Za23);
    tmp2_ = &tmp2;
  } else {
    tmp2_ = &b->X;
  }

  // Compare  X_a*Z_b^2  with  X_b*Z_a^2.
  if (!ec_felem_equal(group, tmp1_, tmp2_)) {
    return 1;  // The points differ.
  }

  if (!b_Z_is_one) {
    felem_mul(group, &Zb23, &Zb23, &b->Z);
    felem_mul(group, &tmp1, &a->Y, &Zb23);
    // tmp1_ = &tmp1
  } else {
    tmp1_ = &a->Y;
  }
  if (!a_Z_is_one) {
    felem_mul(group, &Za23, &Za23, &a->Z);
    felem_mul(group, &tmp2, &b->Y, &Za23);
    // tmp2_ = &tmp2
  } else {
    tmp2_ = &b->Y;
  }

  // Compare  Y_a*Z_b^3  with  Y_b*Z_a^3.
  if (!ec_felem_equal(group, tmp1_, tmp2_)) {
    return 1;  // The points differ.
  }

  // The points are equal.
  return 0;
}

int ec_GFp_simple_mont_inv_mod_ord_vartime(const EC_GROUP *group,
                                           EC_SCALAR *out,
                                           const EC_SCALAR *in) {
  // This implementation (in fact) runs in constant time,
  // even though for this interface it is not mandatory.

  // out = in^-1 in the Montgomery domain. This is
  // |ec_scalar_to_montgomery| followed by |ec_scalar_inv_montgomery|, but
  // |ec_scalar_inv_montgomery| followed by |ec_scalar_from_montgomery| is
  // equivalent and slightly more efficient.
  ec_scalar_inv_montgomery(group, out, in);
  ec_scalar_from_montgomery(group, out, out);
  return 1;
}

int ec_GFp_simple_cmp_x_coordinate(const EC_GROUP *group, const EC_RAW_POINT *p,
                                   const EC_SCALAR *r) {
  if (ec_GFp_simple_is_at_infinity(group, p)) {
    // |ec_get_x_coordinate_as_scalar| will check this internally, but this way
    // we do not push to the error queue.
    return 0;
  }

  EC_SCALAR x;
  return ec_get_x_coordinate_as_scalar(group, &x, p) &&
         ec_scalar_equal_vartime(group, &x, r);
}
