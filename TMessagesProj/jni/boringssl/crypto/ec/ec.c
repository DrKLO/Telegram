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
#include <openssl/obj.h>

#include "internal.h"


static const struct curve_data P224 = {
    "NIST P-224",
    28,
    1,
    {/* p */
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
     0x00, 0x00, 0x00, 0x01,
     /* a */
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFE, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFE,
     /* b */
     0xB4, 0x05, 0x0A, 0x85, 0x0C, 0x04, 0xB3, 0xAB, 0xF5, 0x41, 0x32, 0x56,
     0x50, 0x44, 0xB0, 0xB7, 0xD7, 0xBF, 0xD8, 0xBA, 0x27, 0x0B, 0x39, 0x43,
     0x23, 0x55, 0xFF, 0xB4,
     /* x */
     0xB7, 0x0E, 0x0C, 0xBD, 0x6B, 0xB4, 0xBF, 0x7F, 0x32, 0x13, 0x90, 0xB9,
     0x4A, 0x03, 0xC1, 0xD3, 0x56, 0xC2, 0x11, 0x22, 0x34, 0x32, 0x80, 0xD6,
     0x11, 0x5C, 0x1D, 0x21,
     /* y */
     0xbd, 0x37, 0x63, 0x88, 0xb5, 0xf7, 0x23, 0xfb, 0x4c, 0x22, 0xdf, 0xe6,
     0xcd, 0x43, 0x75, 0xa0, 0x5a, 0x07, 0x47, 0x64, 0x44, 0xd5, 0x81, 0x99,
     0x85, 0x00, 0x7e, 0x34,
     /* order */
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0x16, 0xA2, 0xE0, 0xB8, 0xF0, 0x3E, 0x13, 0xDD, 0x29, 0x45,
     0x5C, 0x5C, 0x2A, 0x3D,
    }};

static const struct curve_data P256 = {
    "NIST P-256",
    32,
    1,
    {/* p */
     0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     /* a */
     0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC,
     /* b */
     0x5A, 0xC6, 0x35, 0xD8, 0xAA, 0x3A, 0x93, 0xE7, 0xB3, 0xEB, 0xBD, 0x55,
     0x76, 0x98, 0x86, 0xBC, 0x65, 0x1D, 0x06, 0xB0, 0xCC, 0x53, 0xB0, 0xF6,
     0x3B, 0xCE, 0x3C, 0x3E, 0x27, 0xD2, 0x60, 0x4B,
     /* x */
     0x6B, 0x17, 0xD1, 0xF2, 0xE1, 0x2C, 0x42, 0x47, 0xF8, 0xBC, 0xE6, 0xE5,
     0x63, 0xA4, 0x40, 0xF2, 0x77, 0x03, 0x7D, 0x81, 0x2D, 0xEB, 0x33, 0xA0,
     0xF4, 0xA1, 0x39, 0x45, 0xD8, 0x98, 0xC2, 0x96,
     /* y */
     0x4f, 0xe3, 0x42, 0xe2, 0xfe, 0x1a, 0x7f, 0x9b, 0x8e, 0xe7, 0xeb, 0x4a,
     0x7c, 0x0f, 0x9e, 0x16, 0x2b, 0xce, 0x33, 0x57, 0x6b, 0x31, 0x5e, 0xce,
     0xcb, 0xb6, 0x40, 0x68, 0x37, 0xbf, 0x51, 0xf5,
     /* order */
     0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xBC, 0xE6, 0xFA, 0xAD, 0xA7, 0x17, 0x9E, 0x84,
     0xF3, 0xB9, 0xCA, 0xC2, 0xFC, 0x63, 0x25, 0x51}};

static const struct curve_data P384 = {
    "NIST P-384",
    48,
    1,
    {/* p */
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE, 0xFF, 0xFF, 0xFF, 0xFF,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
     /* a */
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE, 0xFF, 0xFF, 0xFF, 0xFF,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFC,
     /* b */
     0xB3, 0x31, 0x2F, 0xA7, 0xE2, 0x3E, 0xE7, 0xE4, 0x98, 0x8E, 0x05, 0x6B,
     0xE3, 0xF8, 0x2D, 0x19, 0x18, 0x1D, 0x9C, 0x6E, 0xFE, 0x81, 0x41, 0x12,
     0x03, 0x14, 0x08, 0x8F, 0x50, 0x13, 0x87, 0x5A, 0xC6, 0x56, 0x39, 0x8D,
     0x8A, 0x2E, 0xD1, 0x9D, 0x2A, 0x85, 0xC8, 0xED, 0xD3, 0xEC, 0x2A, 0xEF,
     /* x */
     0xAA, 0x87, 0xCA, 0x22, 0xBE, 0x8B, 0x05, 0x37, 0x8E, 0xB1, 0xC7, 0x1E,
     0xF3, 0x20, 0xAD, 0x74, 0x6E, 0x1D, 0x3B, 0x62, 0x8B, 0xA7, 0x9B, 0x98,
     0x59, 0xF7, 0x41, 0xE0, 0x82, 0x54, 0x2A, 0x38, 0x55, 0x02, 0xF2, 0x5D,
     0xBF, 0x55, 0x29, 0x6C, 0x3A, 0x54, 0x5E, 0x38, 0x72, 0x76, 0x0A, 0xB7,
     /* y */
     0x36, 0x17, 0xde, 0x4a, 0x96, 0x26, 0x2c, 0x6f, 0x5d, 0x9e, 0x98, 0xbf,
     0x92, 0x92, 0xdc, 0x29, 0xf8, 0xf4, 0x1d, 0xbd, 0x28, 0x9a, 0x14, 0x7c,
     0xe9, 0xda, 0x31, 0x13, 0xb5, 0xf0, 0xb8, 0xc0, 0x0a, 0x60, 0xb1, 0xce,
     0x1d, 0x7e, 0x81, 0x9d, 0x7a, 0x43, 0x1d, 0x7c, 0x90, 0xea, 0x0e, 0x5f,
     /* order */
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xC7, 0x63, 0x4D, 0x81, 0xF4, 0x37, 0x2D, 0xDF, 0x58, 0x1A, 0x0D, 0xB2,
     0x48, 0xB0, 0xA7, 0x7A, 0xEC, 0xEC, 0x19, 0x6A, 0xCC, 0xC5, 0x29, 0x73}};

static const struct curve_data P521 = {
    "NIST P-521",
    66,
    1,
    {/* p */
     0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     /* a */
     0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC,
     /* b */
     0x00, 0x51, 0x95, 0x3E, 0xB9, 0x61, 0x8E, 0x1C, 0x9A, 0x1F, 0x92, 0x9A,
     0x21, 0xA0, 0xB6, 0x85, 0x40, 0xEE, 0xA2, 0xDA, 0x72, 0x5B, 0x99, 0xB3,
     0x15, 0xF3, 0xB8, 0xB4, 0x89, 0x91, 0x8E, 0xF1, 0x09, 0xE1, 0x56, 0x19,
     0x39, 0x51, 0xEC, 0x7E, 0x93, 0x7B, 0x16, 0x52, 0xC0, 0xBD, 0x3B, 0xB1,
     0xBF, 0x07, 0x35, 0x73, 0xDF, 0x88, 0x3D, 0x2C, 0x34, 0xF1, 0xEF, 0x45,
     0x1F, 0xD4, 0x6B, 0x50, 0x3F, 0x00,
     /* x */
     0x00, 0xC6, 0x85, 0x8E, 0x06, 0xB7, 0x04, 0x04, 0xE9, 0xCD, 0x9E, 0x3E,
     0xCB, 0x66, 0x23, 0x95, 0xB4, 0x42, 0x9C, 0x64, 0x81, 0x39, 0x05, 0x3F,
     0xB5, 0x21, 0xF8, 0x28, 0xAF, 0x60, 0x6B, 0x4D, 0x3D, 0xBA, 0xA1, 0x4B,
     0x5E, 0x77, 0xEF, 0xE7, 0x59, 0x28, 0xFE, 0x1D, 0xC1, 0x27, 0xA2, 0xFF,
     0xA8, 0xDE, 0x33, 0x48, 0xB3, 0xC1, 0x85, 0x6A, 0x42, 0x9B, 0xF9, 0x7E,
     0x7E, 0x31, 0xC2, 0xE5, 0xBD, 0x66,
     /* y */
     0x01, 0x18, 0x39, 0x29, 0x6a, 0x78, 0x9a, 0x3b, 0xc0, 0x04, 0x5c, 0x8a,
     0x5f, 0xb4, 0x2c, 0x7d, 0x1b, 0xd9, 0x98, 0xf5, 0x44, 0x49, 0x57, 0x9b,
     0x44, 0x68, 0x17, 0xaf, 0xbd, 0x17, 0x27, 0x3e, 0x66, 0x2c, 0x97, 0xee,
     0x72, 0x99, 0x5e, 0xf4, 0x26, 0x40, 0xc5, 0x50, 0xb9, 0x01, 0x3f, 0xad,
     0x07, 0x61, 0x35, 0x3c, 0x70, 0x86, 0xa2, 0x72, 0xc2, 0x40, 0x88, 0xbe,
     0x94, 0x76, 0x9f, 0xd1, 0x66, 0x50,
     /* order */
     0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
     0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFA, 0x51, 0x86,
     0x87, 0x83, 0xBF, 0x2F, 0x96, 0x6B, 0x7F, 0xCC, 0x01, 0x48, 0xF7, 0x09,
     0xA5, 0xD0, 0x3B, 0xB5, 0xC9, 0xB8, 0x89, 0x9C, 0x47, 0xAE, 0xBB, 0x6F,
     0xB7, 0x1E, 0x91, 0x38, 0x64, 0x09}};

const struct built_in_curve OPENSSL_built_in_curves[] = {
    {NID_secp224r1, &P224, 0},
    {
        NID_X9_62_prime256v1, &P256,
#if defined(OPENSSL_64_BIT) && !defined(OPENSSL_WINDOWS)
        EC_GFp_nistp256_method,
#else
        0,
#endif
    },
    {NID_secp384r1, &P384, 0},
    {NID_secp521r1, &P521, 0},
    {NID_undef, 0, 0},
};

EC_GROUP *ec_group_new(const EC_METHOD *meth) {
  EC_GROUP *ret;

  if (meth == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_SLOT_FULL);
    return NULL;
  }

  if (meth->group_init == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return NULL;
  }

  ret = OPENSSL_malloc(sizeof(EC_GROUP));
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  memset(ret, 0, sizeof(EC_GROUP));

  ret->meth = meth;
  BN_init(&ret->order);
  BN_init(&ret->cofactor);

  if (!meth->group_init(ret)) {
    OPENSSL_free(ret);
    return NULL;
  }

  return ret;
}

EC_GROUP *EC_GROUP_new_curve_GFp(const BIGNUM *p, const BIGNUM *a,
                                 const BIGNUM *b, BN_CTX *ctx) {
  const EC_METHOD *meth = EC_GFp_mont_method();
  EC_GROUP *ret;

  ret = ec_group_new(meth);
  if (ret == NULL) {
    return NULL;
  }

  if (ret->meth->group_set_curve == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (!ret->meth->group_set_curve(ret, p, a, b, ctx)) {
    EC_GROUP_free(ret);
    return NULL;
  }
  return ret;
}

int EC_GROUP_set_generator(EC_GROUP *group, const EC_POINT *generator,
                           const BIGNUM *order, const BIGNUM *cofactor) {
  if (group->curve_name != NID_undef) {
    /* |EC_GROUP_set_generator| should only be used with |EC_GROUP|s returned
     * by |EC_GROUP_new_curve_GFp|. */
    return 0;
  }

  if (group->generator == NULL) {
    group->generator = EC_POINT_new(group);
    if (group->generator == NULL) {
      return 0;
    }
  }

  if (!EC_POINT_copy(group->generator, generator)) {
    return 0;
  }

  if (order != NULL) {
    if (!BN_copy(&group->order, order)) {
      return 0;
    }
  } else {
    BN_zero(&group->order);
  }

  if (cofactor != NULL) {
    if (!BN_copy(&group->cofactor, cofactor)) {
      return 0;
    }
  } else {
    BN_zero(&group->cofactor);
  }

  return 1;
}

static EC_GROUP *ec_group_new_from_data(const struct built_in_curve *curve) {
  EC_GROUP *group = NULL;
  EC_POINT *P = NULL;
  BN_CTX *ctx = NULL;
  BIGNUM *p = NULL, *a = NULL, *b = NULL, *x = NULL, *y = NULL;
  int ok = 0;
  unsigned param_len;
  const EC_METHOD *meth;
  const struct curve_data *data;
  const uint8_t *params;

  if ((ctx = BN_CTX_new()) == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  data = curve->data;
  param_len = data->param_len;
  params = data->data;

  if (!(p = BN_bin2bn(params + 0 * param_len, param_len, NULL)) ||
      !(a = BN_bin2bn(params + 1 * param_len, param_len, NULL)) ||
      !(b = BN_bin2bn(params + 2 * param_len, param_len, NULL))) {
    OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
    goto err;
  }

  if (curve->method != 0) {
    meth = curve->method();
    if (((group = ec_group_new(meth)) == NULL) ||
        (!(group->meth->group_set_curve(group, p, a, b, ctx)))) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }
  } else {
    if ((group = EC_GROUP_new_curve_GFp(p, a, b, ctx)) == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }
  }

  if ((P = EC_POINT_new(group)) == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    goto err;
  }

  if (!(x = BN_bin2bn(params + 3 * param_len, param_len, NULL)) ||
      !(y = BN_bin2bn(params + 4 * param_len, param_len, NULL))) {
    OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
    goto err;
  }

  if (!EC_POINT_set_affine_coordinates_GFp(group, P, x, y, ctx)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    goto err;
  }
  if (!BN_bin2bn(params + 5 * param_len, param_len, &group->order) ||
      !BN_set_word(&group->cofactor, (BN_ULONG)data->cofactor)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
    goto err;
  }

  group->generator = P;
  P = NULL;
  ok = 1;

err:
  if (!ok) {
    EC_GROUP_free(group);
    group = NULL;
  }
  EC_POINT_free(P);
  BN_CTX_free(ctx);
  BN_free(p);
  BN_free(a);
  BN_free(b);
  BN_free(x);
  BN_free(y);
  return group;
}

EC_GROUP *EC_GROUP_new_by_curve_name(int nid) {
  unsigned i;
  const struct built_in_curve *curve;
  EC_GROUP *ret = NULL;

  for (i = 0; OPENSSL_built_in_curves[i].nid != NID_undef; i++) {
    curve = &OPENSSL_built_in_curves[i];
    if (curve->nid == nid) {
      ret = ec_group_new_from_data(curve);
      break;
    }
  }

  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_UNKNOWN_GROUP);
    return NULL;
  }

  ret->curve_name = nid;
  return ret;
}

void EC_GROUP_free(EC_GROUP *group) {
  if (!group) {
    return;
  }

  if (group->meth->group_finish != 0) {
    group->meth->group_finish(group);
  }

  ec_pre_comp_free(group->pre_comp);

  EC_POINT_free(group->generator);
  BN_free(&group->order);
  BN_free(&group->cofactor);

  OPENSSL_free(group);
}

int ec_group_copy(EC_GROUP *dest, const EC_GROUP *src) {
  if (dest->meth->group_copy == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (dest->meth != src->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  if (dest == src) {
    return 1;
  }

  ec_pre_comp_free(dest->pre_comp);
  dest->pre_comp = ec_pre_comp_dup(src->pre_comp);

  if (src->generator != NULL) {
    if (dest->generator == NULL) {
      dest->generator = EC_POINT_new(dest);
      if (dest->generator == NULL) {
        return 0;
      }
    }
    if (!EC_POINT_copy(dest->generator, src->generator)) {
      return 0;
    }
  } else {
    /* src->generator == NULL */
    if (dest->generator != NULL) {
      EC_POINT_clear_free(dest->generator);
      dest->generator = NULL;
    }
  }

  if (!BN_copy(&dest->order, &src->order) ||
      !BN_copy(&dest->cofactor, &src->cofactor)) {
    return 0;
  }

  dest->curve_name = src->curve_name;

  return dest->meth->group_copy(dest, src);
}

EC_GROUP *EC_GROUP_dup(const EC_GROUP *a) {
  EC_GROUP *t = NULL;
  int ok = 0;

  if (a == NULL) {
    return NULL;
  }

  t = ec_group_new(a->meth);
  if (t == NULL) {
    return NULL;
  }
  if (!ec_group_copy(t, a)) {
    goto err;
  }

  ok = 1;

err:
  if (!ok) {
    EC_GROUP_free(t);
    return NULL;
  } else {
    return t;
  }
}

int EC_GROUP_cmp(const EC_GROUP *a, const EC_GROUP *b, BN_CTX *ignored) {
  return a->curve_name == NID_undef ||
         b->curve_name == NID_undef ||
         a->curve_name != b->curve_name;
}

const EC_POINT *EC_GROUP_get0_generator(const EC_GROUP *group) {
  return group->generator;
}

int EC_GROUP_get_order(const EC_GROUP *group, BIGNUM *order, BN_CTX *ctx) {
  if (!BN_copy(order, &group->order)) {
    return 0;
  }

  return !BN_is_zero(order);
}

int EC_GROUP_get_cofactor(const EC_GROUP *group, BIGNUM *cofactor,
                          BN_CTX *ctx) {
  if (!BN_copy(cofactor, &group->cofactor)) {
    return 0;
  }

  return !BN_is_zero(&group->cofactor);
}

int EC_GROUP_get_curve_GFp(const EC_GROUP *group, BIGNUM *out_p, BIGNUM *out_a,
                           BIGNUM *out_b, BN_CTX *ctx) {
  if (group->meth->group_get_curve == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  return group->meth->group_get_curve(group, out_p, out_a, out_b, ctx);
}

int EC_GROUP_get_curve_name(const EC_GROUP *group) { return group->curve_name; }

int EC_GROUP_get_degree(const EC_GROUP *group) {
  if (group->meth->group_get_degree == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  return group->meth->group_get_degree(group);
}

int EC_GROUP_precompute_mult(EC_GROUP *group, BN_CTX *ctx) {
  if (group->meth->mul == 0) {
    /* use default */
    return ec_wNAF_precompute_mult(group, ctx);
  }

  if (group->meth->precompute_mult != 0) {
    return group->meth->precompute_mult(group, ctx);
  }

  return 1; /* nothing to do, so report success */
}

int EC_GROUP_have_precompute_mult(const EC_GROUP *group) {
  if (group->meth->mul == 0) {
    /* use default */
    return ec_wNAF_have_precompute_mult(group);
  }

  if (group->meth->have_precompute_mult != 0) {
    return group->meth->have_precompute_mult(group);
  }

  return 0; /* cannot tell whether precomputation has been performed */
}

EC_POINT *EC_POINT_new(const EC_GROUP *group) {
  EC_POINT *ret;

  if (group == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return NULL;
  }
  if (group->meth->point_init == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return NULL;
  }

  ret = OPENSSL_malloc(sizeof *ret);
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  ret->meth = group->meth;

  if (!ret->meth->point_init(ret)) {
    OPENSSL_free(ret);
    return NULL;
  }

  return ret;
}

void EC_POINT_free(EC_POINT *point) {
  if (!point) {
    return;
  }

  if (point->meth->point_finish != 0) {
    point->meth->point_finish(point);
  }
  OPENSSL_free(point);
}

void EC_POINT_clear_free(EC_POINT *point) {
  if (!point) {
    return;
  }

  if (point->meth->point_clear_finish != 0) {
    point->meth->point_clear_finish(point);
  } else if (point->meth->point_finish != 0) {
    point->meth->point_finish(point);
  }
  OPENSSL_cleanse(point, sizeof *point);
  OPENSSL_free(point);
}

int EC_POINT_copy(EC_POINT *dest, const EC_POINT *src) {
  if (dest->meth->point_copy == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (dest->meth != src->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  if (dest == src) {
    return 1;
  }
  return dest->meth->point_copy(dest, src);
}

EC_POINT *EC_POINT_dup(const EC_POINT *a, const EC_GROUP *group) {
  EC_POINT *t;
  int r;

  if (a == NULL) {
    return NULL;
  }

  t = EC_POINT_new(group);
  if (t == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  r = EC_POINT_copy(t, a);
  if (!r) {
    EC_POINT_free(t);
    return NULL;
  } else {
    return t;
  }
}

int EC_POINT_set_to_infinity(const EC_GROUP *group, EC_POINT *point) {
  if (group->meth->point_set_to_infinity == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->point_set_to_infinity(group, point);
}

int EC_POINT_is_at_infinity(const EC_GROUP *group, const EC_POINT *point) {
  if (group->meth->is_at_infinity == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->is_at_infinity(group, point);
}

int EC_POINT_is_on_curve(const EC_GROUP *group, const EC_POINT *point,
                         BN_CTX *ctx) {
  if (group->meth->is_on_curve == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->is_on_curve(group, point, ctx);
}

int EC_POINT_cmp(const EC_GROUP *group, const EC_POINT *a, const EC_POINT *b,
                 BN_CTX *ctx) {
  if (group->meth->point_cmp == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return -1;
  }
  if ((group->meth != a->meth) || (a->meth != b->meth)) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return -1;
  }
  return group->meth->point_cmp(group, a, b, ctx);
}

int EC_POINT_make_affine(const EC_GROUP *group, EC_POINT *point, BN_CTX *ctx) {
  if (group->meth->make_affine == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->make_affine(group, point, ctx);
}

int EC_POINTs_make_affine(const EC_GROUP *group, size_t num, EC_POINT *points[],
                          BN_CTX *ctx) {
  size_t i;

  if (group->meth->points_make_affine == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  for (i = 0; i < num; i++) {
    if (group->meth != points[i]->meth) {
      OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
      return 0;
    }
  }
  return group->meth->points_make_affine(group, num, points, ctx);
}

int EC_POINT_get_affine_coordinates_GFp(const EC_GROUP *group,
                                        const EC_POINT *point, BIGNUM *x,
                                        BIGNUM *y, BN_CTX *ctx) {
  if (group->meth->point_get_affine_coordinates == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->point_get_affine_coordinates(group, point, x, y, ctx);
}

int EC_POINT_set_affine_coordinates_GFp(const EC_GROUP *group, EC_POINT *point,
                                        const BIGNUM *x, const BIGNUM *y,
                                        BN_CTX *ctx) {
  if (group->meth->point_set_affine_coordinates == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->point_set_affine_coordinates(group, point, x, y, ctx);
}

int EC_POINT_add(const EC_GROUP *group, EC_POINT *r, const EC_POINT *a,
                 const EC_POINT *b, BN_CTX *ctx) {
  if (group->meth->add == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if ((group->meth != r->meth) || (r->meth != a->meth) ||
      (a->meth != b->meth)) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->add(group, r, a, b, ctx);
}


int EC_POINT_dbl(const EC_GROUP *group, EC_POINT *r, const EC_POINT *a,
                 BN_CTX *ctx) {
  if (group->meth->dbl == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if ((group->meth != r->meth) || (r->meth != a->meth)) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->dbl(group, r, a, ctx);
}


int EC_POINT_invert(const EC_GROUP *group, EC_POINT *a, BN_CTX *ctx) {
  if (group->meth->invert == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != a->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->invert(group, a, ctx);
}

int EC_POINT_mul(const EC_GROUP *group, EC_POINT *r, const BIGNUM *g_scalar,
                 const EC_POINT *point, const BIGNUM *p_scalar, BN_CTX *ctx) {
  /* just a convenient interface to EC_POINTs_mul() */

  const EC_POINT *points[1];
  const BIGNUM *scalars[1];

  points[0] = point;
  scalars[0] = p_scalar;

  return EC_POINTs_mul(group, r, g_scalar, (point != NULL && p_scalar != NULL),
                       points, scalars, ctx);
}

int EC_POINTs_mul(const EC_GROUP *group, EC_POINT *r, const BIGNUM *scalar,
                  size_t num, const EC_POINT *points[], const BIGNUM *scalars[],
                  BN_CTX *ctx) {
  if (group->meth->mul == 0) {
    /* use default. Warning, not constant-time. */
    return ec_wNAF_mul(group, r, scalar, num, points, scalars, ctx);
  }

  return group->meth->mul(group, r, scalar, num, points, scalars, ctx);
}

int ec_point_set_Jprojective_coordinates_GFp(const EC_GROUP *group, EC_POINT *point,
                                             const BIGNUM *x, const BIGNUM *y,
                                             const BIGNUM *z, BN_CTX *ctx) {
  if (group->meth->point_set_Jprojective_coordinates_GFp == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  if (group->meth != point->meth) {
    OPENSSL_PUT_ERROR(EC, EC_R_INCOMPATIBLE_OBJECTS);
    return 0;
  }
  return group->meth->point_set_Jprojective_coordinates_GFp(group, point, x, y,
                                                            z, ctx);
}

void EC_GROUP_set_asn1_flag(EC_GROUP *group, int flag) {}

const EC_METHOD *EC_GROUP_method_of(const EC_GROUP *group) {
  return NULL;
}

int EC_METHOD_get_field_type(const EC_METHOD *meth) {
  return NID_X9_62_prime_field;
}

void EC_GROUP_set_point_conversion_form(EC_GROUP *group,
                                        point_conversion_form_t form) {
  if (form != POINT_CONVERSION_UNCOMPRESSED) {
    abort();
  }
}
