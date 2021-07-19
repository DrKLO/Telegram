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

#ifndef OPENSSL_HEADER_EC_INTERNAL_H
#define OPENSSL_HEADER_EC_INTERNAL_H

#include <openssl/base.h>

#include <openssl/bn.h>
#include <openssl/ex_data.h>
#include <openssl/thread.h>
#include <openssl/type_check.h>

#include "../bn/internal.h"

#if defined(__cplusplus)
extern "C" {
#endif


// Cap the size of all field elements and scalars, including custom curves, to
// 66 bytes, large enough to fit secp521r1 and brainpoolP512r1, which appear to
// be the largest fields anyone plausibly uses.
#define EC_MAX_BYTES 66
#define EC_MAX_WORDS ((EC_MAX_BYTES + BN_BYTES - 1) / BN_BYTES)

OPENSSL_STATIC_ASSERT(EC_MAX_WORDS <= BN_SMALL_MAX_WORDS,
                      "bn_*_small functions not usable");

// An EC_SCALAR is an integer fully reduced modulo the order. Only the first
// |order->width| words are used. An |EC_SCALAR| is specific to an |EC_GROUP|
// and must not be mixed between groups.
typedef union {
  // bytes is the representation of the scalar in little-endian order.
  uint8_t bytes[EC_MAX_BYTES];
  BN_ULONG words[EC_MAX_WORDS];
} EC_SCALAR;

// An EC_FELEM represents a field element. Only the first |field->width| words
// are used. An |EC_FELEM| is specific to an |EC_GROUP| and must not be mixed
// between groups. Additionally, the representation (whether or not elements are
// represented in Montgomery-form) may vary between |EC_METHOD|s.
typedef union {
  // bytes is the representation of the field element in little-endian order.
  uint8_t bytes[EC_MAX_BYTES];
  BN_ULONG words[EC_MAX_WORDS];
} EC_FELEM;

// An EC_RAW_POINT represents an elliptic curve point. Unlike |EC_POINT|, it is
// a plain struct which can be stack-allocated and needs no cleanup. It is
// specific to an |EC_GROUP| and must not be mixed between groups.
typedef struct {
  EC_FELEM X, Y, Z;
  // X, Y, and Z are Jacobian projective coordinates. They represent
  // (X/Z^2, Y/Z^3) if Z != 0 and the point at infinity otherwise.
} EC_RAW_POINT;

struct ec_method_st {
  int (*group_init)(EC_GROUP *);
  void (*group_finish)(EC_GROUP *);
  int (*group_set_curve)(EC_GROUP *, const BIGNUM *p, const BIGNUM *a,
                         const BIGNUM *b, BN_CTX *);

  // point_get_affine_coordinates sets |*x| and |*y| to the affine coordinates
  // of |p|. Either |x| or |y| may be NULL to omit it. It returns one on success
  // and zero if |p| is the point at infinity.
  //
  // Note: unlike |EC_FELEM|s used as intermediate values internal to the
  // |EC_METHOD|, |*x| and |*y| are not encoded in Montgomery form.
  int (*point_get_affine_coordinates)(const EC_GROUP *, const EC_RAW_POINT *p,
                                      EC_FELEM *x, EC_FELEM *y);

  // add sets |r| to |a| + |b|.
  void (*add)(const EC_GROUP *group, EC_RAW_POINT *r, const EC_RAW_POINT *a,
              const EC_RAW_POINT *b);
  // dbl sets |r| to |a| + |a|.
  void (*dbl)(const EC_GROUP *group, EC_RAW_POINT *r, const EC_RAW_POINT *a);

  // mul sets |r| to |scalar|*|p|.
  void (*mul)(const EC_GROUP *group, EC_RAW_POINT *r, const EC_RAW_POINT *p,
              const EC_SCALAR *scalar);
  // mul_base sets |r| to |scalar|*generator.
  void (*mul_base)(const EC_GROUP *group, EC_RAW_POINT *r,
                   const EC_SCALAR *scalar);
  // mul_public sets |r| to |g_scalar|*generator + |p_scalar|*|p|. It assumes
  // that the inputs are public so there is no concern about leaking their
  // values through timing.
  void (*mul_public)(const EC_GROUP *group, EC_RAW_POINT *r,
                     const EC_SCALAR *g_scalar, const EC_RAW_POINT *p,
                     const EC_SCALAR *p_scalar);

  // felem_mul and felem_sqr implement multiplication and squaring,
  // respectively, so that the generic |EC_POINT_add| and |EC_POINT_dbl|
  // implementations can work both with |EC_GFp_mont_method| and the tuned
  // operations.
  //
  // TODO(davidben): This constrains |EC_FELEM|'s internal representation, adds
  // many indirect calls in the middle of the generic code, and a bunch of
  // conversions. If p224-64.c were easily convertable to Montgomery form, we
  // could say |EC_FELEM| is always in Montgomery form. If we routed the rest of
  // simple.c to |EC_METHOD|, we could give |EC_POINT| an |EC_METHOD|-specific
  // representation and say |EC_FELEM| is purely a |EC_GFp_mont_method| type.
  void (*felem_mul)(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a,
                    const EC_FELEM *b);
  void (*felem_sqr)(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a);

  int (*bignum_to_felem)(const EC_GROUP *group, EC_FELEM *out,
                         const BIGNUM *in);
  int (*felem_to_bignum)(const EC_GROUP *group, BIGNUM *out,
                         const EC_FELEM *in);

  // scalar_inv_montgomery sets |out| to |in|^-1, where both input and output
  // are in Montgomery form.
  void (*scalar_inv_montgomery)(const EC_GROUP *group, EC_SCALAR *out,
                                const EC_SCALAR *in);

  // scalar_inv_montgomery_vartime performs the same computation as
  // |scalar_inv_montgomery|. It further assumes that the inputs are public so
  // there is no concern about leaking their values through timing.
  int (*scalar_inv_montgomery_vartime)(const EC_GROUP *group, EC_SCALAR *out,
                                       const EC_SCALAR *in);

  // cmp_x_coordinate compares the x (affine) coordinate of |p|, mod the group
  // order, with |r|. It returns one if the values match and zero if |p| is the
  // point at infinity of the values do not match.
  int (*cmp_x_coordinate)(const EC_GROUP *group, const EC_RAW_POINT *p,
                          const EC_SCALAR *r);
} /* EC_METHOD */;

const EC_METHOD *EC_GFp_mont_method(void);

struct ec_group_st {
  const EC_METHOD *meth;

  // Unlike all other |EC_POINT|s, |generator| does not own |generator->group|
  // to avoid a reference cycle.
  EC_POINT *generator;
  BIGNUM order;

  int curve_name;  // optional NID for named curve

  BN_MONT_CTX *order_mont;  // data for ECDSA inverse

  // The following members are handled by the method functions,
  // even if they appear generic

  BIGNUM field;  // For curves over GF(p), this is the modulus.

  EC_FELEM a, b;  // Curve coefficients.

  // a_is_minus3 is one if |a| is -3 mod |field| and zero otherwise. Point
  // arithmetic is optimized for -3.
  int a_is_minus3;

  // field_greater_than_order is one if |field| is greate than |order| and zero
  // otherwise.
  int field_greater_than_order;

  // field_minus_order, if |field_greater_than_order| is true, is |field| minus
  // |order| represented as an |EC_FELEM|. Otherwise, it is zero.
  //
  // Note: unlike |EC_FELEM|s used as intermediate values internal to the
  // |EC_METHOD|, this value is not encoded in Montgomery form.
  EC_FELEM field_minus_order;

  CRYPTO_refcount_t references;

  BN_MONT_CTX *mont;  // Montgomery structure.

  EC_FELEM one;  // The value one.
} /* EC_GROUP */;

struct ec_point_st {
  // group is an owning reference to |group|, unless this is
  // |group->generator|.
  EC_GROUP *group;
  // raw is the group-specific point data. Functions that take |EC_POINT|
  // typically check consistency with |EC_GROUP| while functions that take
  // |EC_RAW_POINT| do not. Thus accesses to this field should be externally
  // checked for consistency.
  EC_RAW_POINT raw;
} /* EC_POINT */;

EC_GROUP *ec_group_new(const EC_METHOD *meth);

// ec_bignum_to_felem converts |in| to an |EC_FELEM|. It returns one on success
// and zero if |in| is out of range.
int ec_bignum_to_felem(const EC_GROUP *group, EC_FELEM *out, const BIGNUM *in);

// ec_felem_to_bignum converts |in| to a |BIGNUM|. It returns one on success and
// zero on allocation failure.
int ec_felem_to_bignum(const EC_GROUP *group, BIGNUM *out, const EC_FELEM *in);

// ec_felem_neg sets |out| to -|a|.
void ec_felem_neg(const EC_GROUP *group, EC_FELEM *out, const EC_FELEM *a);

// ec_felem_add sets |out| to |a| + |b|.
void ec_felem_add(const EC_GROUP *group, EC_FELEM *out, const EC_FELEM *a,
                  const EC_FELEM *b);

// ec_felem_add sets |out| to |a| - |b|.
void ec_felem_sub(const EC_GROUP *group, EC_FELEM *out, const EC_FELEM *a,
                  const EC_FELEM *b);

// ec_felem_non_zero_mask returns all ones if |a| is non-zero and all zeros
// otherwise.
BN_ULONG ec_felem_non_zero_mask(const EC_GROUP *group, const EC_FELEM *a);

// ec_felem_select, in constant time, sets |out| to |a| if |mask| is all ones
// and |b| if |mask| is all zeros.
void ec_felem_select(const EC_GROUP *group, EC_FELEM *out, BN_ULONG mask,
                     const EC_FELEM *a, const EC_FELEM *b);

// ec_felem_equal returns one if |a| and |b| are equal and zero otherwise. It
// treats |a| and |b| as public and does *not* run in constant time.
int ec_felem_equal(const EC_GROUP *group, const EC_FELEM *a, const EC_FELEM *b);

// ec_bignum_to_scalar converts |in| to an |EC_SCALAR| and writes it to
// |*out|. It returns one on success and zero if |in| is out of range.
OPENSSL_EXPORT int ec_bignum_to_scalar(const EC_GROUP *group, EC_SCALAR *out,
                                       const BIGNUM *in);

// ec_random_nonzero_scalar sets |out| to a uniformly selected random value from
// 1 to |group->order| - 1. It returns one on success and zero on error.
int ec_random_nonzero_scalar(const EC_GROUP *group, EC_SCALAR *out,
                             const uint8_t additional_data[32]);

// ec_scalar_equal_vartime returns one if |a| and |b| are equal and zero
// otherwise. Both values are treated as public.
int ec_scalar_equal_vartime(const EC_GROUP *group, const EC_SCALAR *a,
                            const EC_SCALAR *b);

// ec_scalar_is_zero returns one if |a| is zero and zero otherwise.
int ec_scalar_is_zero(const EC_GROUP *group, const EC_SCALAR *a);

// ec_scalar_add sets |r| to |a| + |b|.
void ec_scalar_add(const EC_GROUP *group, EC_SCALAR *r, const EC_SCALAR *a,
                   const EC_SCALAR *b);

// ec_scalar_to_montgomery sets |r| to |a| in Montgomery form.
void ec_scalar_to_montgomery(const EC_GROUP *group, EC_SCALAR *r,
                             const EC_SCALAR *a);

// ec_scalar_to_montgomery sets |r| to |a| converted from Montgomery form.
void ec_scalar_from_montgomery(const EC_GROUP *group, EC_SCALAR *r,
                               const EC_SCALAR *a);

// ec_scalar_mul_montgomery sets |r| to |a| * |b| where inputs and outputs are
// in Montgomery form.
void ec_scalar_mul_montgomery(const EC_GROUP *group, EC_SCALAR *r,
                              const EC_SCALAR *a, const EC_SCALAR *b);

// ec_scalar_mul_montgomery sets |r| to |a|^-1 where inputs and outputs are in
// Montgomery form.
void ec_scalar_inv_montgomery(const EC_GROUP *group, EC_SCALAR *r,
                              const EC_SCALAR *a);

// ec_scalar_inv_montgomery_vartime performs the same actions as
// |ec_scalar_inv_montgomery|, but in variable time.
int ec_scalar_inv_montgomery_vartime(const EC_GROUP *group, EC_SCALAR *r,
                                     const EC_SCALAR *a);

// ec_point_mul_scalar sets |r| to |p| * |scalar|. Both inputs are considered
// secret.
int ec_point_mul_scalar(const EC_GROUP *group, EC_RAW_POINT *r,
                        const EC_RAW_POINT *p, const EC_SCALAR *scalar);

// ec_point_mul_scalar_base sets |r| to generator * |scalar|. |scalar| is
// treated as secret.
int ec_point_mul_scalar_base(const EC_GROUP *group, EC_RAW_POINT *r,
                             const EC_SCALAR *scalar);

// ec_point_mul_scalar_public performs the same computation as
// ec_point_mul_scalar.  It further assumes that the inputs are public so
// there is no concern about leaking their values through timing.
OPENSSL_EXPORT int ec_point_mul_scalar_public(const EC_GROUP *group,
                                              EC_RAW_POINT *r,
                                              const EC_SCALAR *g_scalar,
                                              const EC_RAW_POINT *p,
                                              const EC_SCALAR *p_scalar);

// ec_cmp_x_coordinate compares the x (affine) coordinate of |p|, mod the group
// order, with |r|. It returns one if the values match and zero if |p| is the
// point at infinity of the values do not match.
int ec_cmp_x_coordinate(const EC_GROUP *group, const EC_RAW_POINT *p,
                        const EC_SCALAR *r);

// ec_get_x_coordinate_as_scalar sets |*out| to |p|'s x-coordinate, modulo
// |group->order|. It returns one on success and zero if |p| is the point at
// infinity.
int ec_get_x_coordinate_as_scalar(const EC_GROUP *group, EC_SCALAR *out,
                                  const EC_RAW_POINT *p);

// ec_point_get_affine_coordinate_bytes writes |p|'s affine coordinates to
// |out_x| and |out_y|, each of which must have at must |max_out| bytes. It sets
// |*out_len| to the number of bytes written in each buffer. Coordinates are
// written big-endian and zero-padded to the size of the field.
//
// Either of |out_x| or |out_y| may be NULL to omit that coordinate. This
// function returns one on success and zero on failure.
int ec_point_get_affine_coordinate_bytes(const EC_GROUP *group, uint8_t *out_x,
                                         uint8_t *out_y, size_t *out_len,
                                         size_t max_out, const EC_RAW_POINT *p);

// ec_field_element_to_scalar reduces |r| modulo |group->order|. |r| must
// previously have been reduced modulo |group->field|.
int ec_field_element_to_scalar(const EC_GROUP *group, BIGNUM *r);

void ec_GFp_mont_mul(const EC_GROUP *group, EC_RAW_POINT *r,
                     const EC_RAW_POINT *p, const EC_SCALAR *scalar);
void ec_GFp_mont_mul_base(const EC_GROUP *group, EC_RAW_POINT *r,
                          const EC_SCALAR *scalar);

// ec_compute_wNAF writes the modified width-(w+1) Non-Adjacent Form (wNAF) of
// |scalar| to |out|. |out| must have room for |bits| + 1 elements, each of
// which will be either zero or odd with an absolute value less than  2^w
// satisfying
//     scalar = \sum_j out[j]*2^j
// where at most one of any  w+1  consecutive digits is non-zero
// with the exception that the most significant digit may be only
// w-1 zeros away from that next non-zero digit.
void ec_compute_wNAF(const EC_GROUP *group, int8_t *out,
                     const EC_SCALAR *scalar, size_t bits, int w);

void ec_GFp_mont_mul_public(const EC_GROUP *group, EC_RAW_POINT *r,
                            const EC_SCALAR *g_scalar, const EC_RAW_POINT *p,
                            const EC_SCALAR *p_scalar);

// method functions in simple.c
int ec_GFp_simple_group_init(EC_GROUP *);
void ec_GFp_simple_group_finish(EC_GROUP *);
int ec_GFp_simple_group_set_curve(EC_GROUP *, const BIGNUM *p, const BIGNUM *a,
                                  const BIGNUM *b, BN_CTX *);
int ec_GFp_simple_group_get_curve(const EC_GROUP *, BIGNUM *p, BIGNUM *a,
                                  BIGNUM *b);
void ec_GFp_simple_point_init(EC_RAW_POINT *);
void ec_GFp_simple_point_copy(EC_RAW_POINT *, const EC_RAW_POINT *);
void ec_GFp_simple_point_set_to_infinity(const EC_GROUP *, EC_RAW_POINT *);
int ec_GFp_simple_point_set_affine_coordinates(const EC_GROUP *, EC_RAW_POINT *,
                                               const BIGNUM *x,
                                               const BIGNUM *y);
void ec_GFp_mont_add(const EC_GROUP *, EC_RAW_POINT *r, const EC_RAW_POINT *a,
                     const EC_RAW_POINT *b);
void ec_GFp_mont_dbl(const EC_GROUP *, EC_RAW_POINT *r, const EC_RAW_POINT *a);
void ec_GFp_simple_invert(const EC_GROUP *, EC_RAW_POINT *);
int ec_GFp_simple_is_at_infinity(const EC_GROUP *, const EC_RAW_POINT *);
int ec_GFp_simple_is_on_curve(const EC_GROUP *, const EC_RAW_POINT *);
int ec_GFp_simple_cmp(const EC_GROUP *, const EC_RAW_POINT *a,
                      const EC_RAW_POINT *b);
void ec_simple_scalar_inv_montgomery(const EC_GROUP *group, EC_SCALAR *r,
                                     const EC_SCALAR *a);

int ec_GFp_simple_mont_inv_mod_ord_vartime(const EC_GROUP *group, EC_SCALAR *r,
                                           const EC_SCALAR *a);

int ec_GFp_simple_cmp_x_coordinate(const EC_GROUP *group, const EC_RAW_POINT *p,
                                   const EC_SCALAR *r);

// method functions in montgomery.c
int ec_GFp_mont_group_init(EC_GROUP *);
int ec_GFp_mont_group_set_curve(EC_GROUP *, const BIGNUM *p, const BIGNUM *a,
                                const BIGNUM *b, BN_CTX *);
void ec_GFp_mont_group_finish(EC_GROUP *);
void ec_GFp_mont_felem_mul(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a,
                           const EC_FELEM *b);
void ec_GFp_mont_felem_sqr(const EC_GROUP *, EC_FELEM *r, const EC_FELEM *a);

int ec_GFp_mont_bignum_to_felem(const EC_GROUP *group, EC_FELEM *out,
                                const BIGNUM *in);
int ec_GFp_mont_felem_to_bignum(const EC_GROUP *group, BIGNUM *out,
                                const EC_FELEM *in);

void ec_GFp_nistp_recode_scalar_bits(uint8_t *sign, uint8_t *digit, uint8_t in);

const EC_METHOD *EC_GFp_nistp224_method(void);
const EC_METHOD *EC_GFp_nistp256_method(void);

// EC_GFp_nistz256_method is a GFp method using montgomery multiplication, with
// x86-64 optimized P256. See http://eprint.iacr.org/2013/816.
const EC_METHOD *EC_GFp_nistz256_method(void);

// An EC_WRAPPED_SCALAR is an |EC_SCALAR| with a parallel |BIGNUM|
// representation. It exists to support the |EC_KEY_get0_private_key| API.
typedef struct {
  BIGNUM bignum;
  EC_SCALAR scalar;
} EC_WRAPPED_SCALAR;

struct ec_key_st {
  EC_GROUP *group;

  EC_POINT *pub_key;
  EC_WRAPPED_SCALAR *priv_key;

  // fixed_k may contain a specific value of 'k', to be used in ECDSA signing.
  // This is only for the FIPS power-on tests.
  BIGNUM *fixed_k;

  unsigned int enc_flag;
  point_conversion_form_t conv_form;

  CRYPTO_refcount_t references;

  ECDSA_METHOD *ecdsa_meth;

  CRYPTO_EX_DATA ex_data;
} /* EC_KEY */;

struct built_in_curve {
  int nid;
  const uint8_t *oid;
  uint8_t oid_len;
  // comment is a human-readable string describing the curve.
  const char *comment;
  // param_len is the number of bytes needed to store a field element.
  uint8_t param_len;
  // params points to an array of 6*|param_len| bytes which hold the field
  // elements of the following (in big-endian order): prime, a, b, generator x,
  // generator y, order.
  const uint8_t *params;
  const EC_METHOD *method;
};

#define OPENSSL_NUM_BUILT_IN_CURVES 4

struct built_in_curves {
  struct built_in_curve curves[OPENSSL_NUM_BUILT_IN_CURVES];
};

// OPENSSL_built_in_curves returns a pointer to static information about
// standard curves. The array is terminated with an entry where |nid| is
// |NID_undef|.
const struct built_in_curves *OPENSSL_built_in_curves(void);

#if defined(__cplusplus)
}  // extern C
#endif

#endif  // OPENSSL_HEADER_EC_INTERNAL_H
