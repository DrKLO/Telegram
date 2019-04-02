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
#include <openssl/thread.h>

#include "internal.h"
#include "../../internal.h"


// This file implements the wNAF-based interleaving multi-exponentiation method
// at:
//   http://link.springer.com/chapter/10.1007%2F3-540-45537-X_13
//   http://www.bmoeller.de/pdf/TI-01-08.multiexp.pdf

// Determine the modified width-(w+1) Non-Adjacent Form (wNAF) of 'scalar'.
// This is an array  r[]  of values that are either zero or odd with an
// absolute value less than  2^w  satisfying
//     scalar = \sum_j r[j]*2^j
// where at most one of any  w+1  consecutive digits is non-zero
// with the exception that the most significant digit may be only
// w-1 zeros away from that next non-zero digit.
static int8_t *compute_wNAF(const BIGNUM *scalar, int w, size_t *ret_len) {
  int window_val;
  int ok = 0;
  int8_t *r = NULL;
  int sign = 1;
  int bit, next_bit, mask;
  size_t len = 0, j;

  if (BN_is_zero(scalar)) {
    r = OPENSSL_malloc(1);
    if (!r) {
      OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
      goto err;
    }
    r[0] = 0;
    *ret_len = 1;
    return r;
  }

  // 'int8_t' can represent integers with absolute values less than 2^7.
  if (w <= 0 || w > 7) {
    OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
    goto err;
  }
  bit = 1 << w;         // at most 128
  next_bit = bit << 1;  // at most 256
  mask = next_bit - 1;  // at most 255

  if (BN_is_negative(scalar)) {
    sign = -1;
  }

  len = BN_num_bits(scalar);
  // The modified wNAF may be one digit longer than binary representation
  // (*ret_len will be set to the actual length, i.e. at most
  // BN_num_bits(scalar) + 1).
  r = OPENSSL_malloc(len + 1);
  if (r == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    goto err;
  }
  window_val = scalar->d[0] & mask;
  j = 0;
  // If j+w+1 >= len, window_val will not increase.
  while (window_val != 0 || j + w + 1 < len) {
    int digit = 0;

    // 0 <= window_val <= 2^(w+1)

    if (window_val & 1) {
      // 0 < window_val < 2^(w+1)

      if (window_val & bit) {
        digit = window_val - next_bit;  // -2^w < digit < 0

#if 1  // modified wNAF
        if (j + w + 1 >= len) {
          // special case for generating modified wNAFs:
          // no new bits will be added into window_val,
          // so using a positive digit here will decrease
          // the total length of the representation

          digit = window_val & (mask >> 1);  // 0 < digit < 2^w
        }
#endif
      } else {
        digit = window_val;  // 0 < digit < 2^w
      }

      if (digit <= -bit || digit >= bit || !(digit & 1)) {
        OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
        goto err;
      }

      window_val -= digit;

      // Now window_val is 0 or 2^(w+1) in standard wNAF generation;
      // for modified window NAFs, it may also be 2^w.
      if (window_val != 0 && window_val != next_bit && window_val != bit) {
        OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
        goto err;
      }
    }

    r[j++] = sign * digit;

    window_val >>= 1;
    window_val += bit * BN_is_bit_set(scalar, j + w);

    if (window_val > next_bit) {
      OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
      goto err;
    }
  }

  if (j > len + 1) {
    OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
    goto err;
  }
  len = j;
  ok = 1;

err:
  if (!ok) {
    OPENSSL_free(r);
    r = NULL;
  }
  if (ok) {
    *ret_len = len;
  }
  return r;
}


// TODO: table should be optimised for the wNAF-based implementation,
//       sometimes smaller windows will give better performance
//       (thus the boundaries should be increased)
static size_t window_bits_for_scalar_size(size_t b) {
  if (b >= 2000) {
    return 6;
  }

  if (b >= 800) {
    return 5;
  }

  if (b >= 300) {
    return 4;
  }

  if (b >= 70) {
    return 3;
  }

  if (b >= 20) {
    return 2;
  }

  return 1;
}

int ec_wNAF_mul(const EC_GROUP *group, EC_POINT *r,
                const EC_SCALAR *g_scalar_raw, const EC_POINT *p,
                const EC_SCALAR *p_scalar_raw, BN_CTX *ctx) {
  BN_CTX *new_ctx = NULL;
  const EC_POINT *generator = NULL;
  EC_POINT *tmp = NULL;
  size_t total_num = 0;
  size_t i, j;
  int k;
  int r_is_inverted = 0;
  int r_is_at_infinity = 1;
  size_t *wsize = NULL;      // individual window sizes
  int8_t **wNAF = NULL;  // individual wNAFs
  size_t *wNAF_len = NULL;
  size_t max_len = 0;
  size_t num_val = 0;
  EC_POINT **val = NULL;  // precomputation
  EC_POINT **v;
  EC_POINT ***val_sub = NULL;  // pointers to sub-arrays of 'val'
  int ret = 0;

  if (ctx == NULL) {
    ctx = new_ctx = BN_CTX_new();
    if (ctx == NULL) {
      goto err;
    }
  }
  BN_CTX_start(ctx);

  // Convert from |EC_SCALAR| to |BIGNUM|. |BIGNUM| is not constant-time, but
  // neither is the rest of this function.
  BIGNUM *g_scalar = NULL, *p_scalar = NULL;
  if (g_scalar_raw != NULL) {
    g_scalar = BN_CTX_get(ctx);
    if (g_scalar == NULL ||
        !bn_set_words(g_scalar, g_scalar_raw->words, group->order.top)) {
      goto err;
    }
  }
  if (p_scalar_raw != NULL) {
    p_scalar = BN_CTX_get(ctx);
    if (p_scalar == NULL ||
        !bn_set_words(p_scalar, p_scalar_raw->words, group->order.top)) {
      goto err;
    }
  }

  // TODO: This function used to take |points| and |scalars| as arrays of
  // |num| elements. The code below should be simplified to work in terms of |p|
  // and |p_scalar|.
  size_t num = p != NULL ? 1 : 0;
  const EC_POINT **points = p != NULL ? &p : NULL;
  BIGNUM **scalars = p != NULL ? &p_scalar : NULL;

  total_num = num;

  if (g_scalar != NULL) {
    generator = EC_GROUP_get0_generator(group);
    if (generator == NULL) {
      OPENSSL_PUT_ERROR(EC, EC_R_UNDEFINED_GENERATOR);
      goto err;
    }

    ++total_num;  // treat 'g_scalar' like 'num'-th element of 'scalars'
  }


  wsize = OPENSSL_malloc(total_num * sizeof(wsize[0]));
  wNAF_len = OPENSSL_malloc(total_num * sizeof(wNAF_len[0]));
  wNAF = OPENSSL_malloc(total_num * sizeof(wNAF[0]));
  val_sub = OPENSSL_malloc(total_num * sizeof(val_sub[0]));

  // Ensure wNAF is initialised in case we end up going to err.
  if (wNAF != NULL) {
    OPENSSL_memset(wNAF, 0, total_num * sizeof(wNAF[0]));
  }

  if (!wsize || !wNAF_len || !wNAF || !val_sub) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  // num_val will be the total number of temporarily precomputed points
  num_val = 0;

  for (i = 0; i < total_num; i++) {
    size_t bits;

    bits = i < num ? BN_num_bits(scalars[i]) : BN_num_bits(g_scalar);
    wsize[i] = window_bits_for_scalar_size(bits);
    num_val += (size_t)1 << (wsize[i] - 1);
    wNAF[i] =
        compute_wNAF((i < num ? scalars[i] : g_scalar), wsize[i], &wNAF_len[i]);
    if (wNAF[i] == NULL) {
      goto err;
    }
    if (wNAF_len[i] > max_len) {
      max_len = wNAF_len[i];
    }
  }

  // All points we precompute now go into a single array 'val'. 'val_sub[i]' is
  // a pointer to the subarray for the i-th point.
  val = OPENSSL_malloc(num_val * sizeof(val[0]));
  if (val == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    goto err;
  }
  OPENSSL_memset(val, 0, num_val * sizeof(val[0]));

  // allocate points for precomputation
  v = val;
  for (i = 0; i < total_num; i++) {
    val_sub[i] = v;
    for (j = 0; j < ((size_t)1 << (wsize[i] - 1)); j++) {
      *v = EC_POINT_new(group);
      if (*v == NULL) {
        goto err;
      }
      v++;
    }
  }
  if (!(v == val + num_val)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  if (!(tmp = EC_POINT_new(group))) {
    goto err;
  }

  // prepare precomputed values:
  //    val_sub[i][0] :=     points[i]
  //    val_sub[i][1] := 3 * points[i]
  //    val_sub[i][2] := 5 * points[i]
  //    ...
  for (i = 0; i < total_num; i++) {
    if (i < num) {
      if (!EC_POINT_copy(val_sub[i][0], points[i])) {
        goto err;
      }
    } else if (!EC_POINT_copy(val_sub[i][0], generator)) {
      goto err;
    }

    if (wsize[i] > 1) {
      if (!EC_POINT_dbl(group, tmp, val_sub[i][0], ctx)) {
        goto err;
      }
      for (j = 1; j < ((size_t)1 << (wsize[i] - 1)); j++) {
        if (!EC_POINT_add(group, val_sub[i][j], val_sub[i][j - 1], tmp, ctx)) {
          goto err;
        }
      }
    }
  }

#if 1  // optional; window_bits_for_scalar_size assumes we do this step
  if (!EC_POINTs_make_affine(group, num_val, val, ctx)) {
    goto err;
  }
#endif

  r_is_at_infinity = 1;

  for (k = max_len - 1; k >= 0; k--) {
    if (!r_is_at_infinity && !EC_POINT_dbl(group, r, r, ctx)) {
      goto err;
    }

    for (i = 0; i < total_num; i++) {
      if (wNAF_len[i] > (size_t)k) {
        int digit = wNAF[i][k];
        int is_neg;

        if (digit) {
          is_neg = digit < 0;

          if (is_neg) {
            digit = -digit;
          }

          if (is_neg != r_is_inverted) {
            if (!r_is_at_infinity && !EC_POINT_invert(group, r, ctx)) {
              goto err;
            }
            r_is_inverted = !r_is_inverted;
          }

          // digit > 0

          if (r_is_at_infinity) {
            if (!EC_POINT_copy(r, val_sub[i][digit >> 1])) {
              goto err;
            }
            r_is_at_infinity = 0;
          } else {
            if (!EC_POINT_add(group, r, r, val_sub[i][digit >> 1], ctx)) {
              goto err;
            }
          }
        }
      }
    }
  }

  if (r_is_at_infinity) {
    if (!EC_POINT_set_to_infinity(group, r)) {
      goto err;
    }
  } else if (r_is_inverted && !EC_POINT_invert(group, r, ctx)) {
    goto err;
  }

  ret = 1;

err:
  if (ctx != NULL) {
    BN_CTX_end(ctx);
  }
  BN_CTX_free(new_ctx);
  EC_POINT_free(tmp);
  OPENSSL_free(wsize);
  OPENSSL_free(wNAF_len);
  if (wNAF != NULL) {
    for (i = 0; i < total_num; i++) {
      OPENSSL_free(wNAF[i]);
    }

    OPENSSL_free(wNAF);
  }
  if (val != NULL) {
    for (i = 0; i < num_val; i++) {
      EC_POINT_free(val[i]);
    }

    OPENSSL_free(val);
  }
  OPENSSL_free(val_sub);
  return ret;
}
