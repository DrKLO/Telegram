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
 * [including the GNU Public Licence.]
 */
/* ====================================================================
 * Copyright (c) 1998-2006 The OpenSSL Project.  All rights reserved.
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
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/bn.h>

#include <assert.h>
#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/thread.h>
#include <openssl/type_check.h>

#include "internal.h"
#include "../../internal.h"


#if !defined(OPENSSL_NO_ASM) &&                         \
    (defined(OPENSSL_X86) || defined(OPENSSL_X86_64) || \
     defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64))
#define OPENSSL_BN_ASM_MONT
#endif

static int bn_mod_mul_montgomery_fallback(BIGNUM *r, const BIGNUM *a,
                                          const BIGNUM *b,
                                          const BN_MONT_CTX *mont, BN_CTX *ctx);


BN_MONT_CTX *BN_MONT_CTX_new(void) {
  BN_MONT_CTX *ret = OPENSSL_malloc(sizeof(BN_MONT_CTX));

  if (ret == NULL) {
    return NULL;
  }

  OPENSSL_memset(ret, 0, sizeof(BN_MONT_CTX));
  BN_init(&ret->RR);
  BN_init(&ret->N);

  return ret;
}

void BN_MONT_CTX_free(BN_MONT_CTX *mont) {
  if (mont == NULL) {
    return;
  }

  BN_free(&mont->RR);
  BN_free(&mont->N);
  OPENSSL_free(mont);
}

BN_MONT_CTX *BN_MONT_CTX_copy(BN_MONT_CTX *to, const BN_MONT_CTX *from) {
  if (to == from) {
    return to;
  }

  if (!BN_copy(&to->RR, &from->RR) ||
      !BN_copy(&to->N, &from->N)) {
    return NULL;
  }
  to->n0[0] = from->n0[0];
  to->n0[1] = from->n0[1];
  return to;
}

OPENSSL_COMPILE_ASSERT(BN_MONT_CTX_N0_LIMBS == 1 || BN_MONT_CTX_N0_LIMBS == 2,
                       BN_MONT_CTX_N0_LIMBS_VALUE_INVALID);
OPENSSL_COMPILE_ASSERT(sizeof(BN_ULONG) * BN_MONT_CTX_N0_LIMBS ==
                       sizeof(uint64_t), BN_MONT_CTX_set_64_bit_mismatch);

int BN_MONT_CTX_set(BN_MONT_CTX *mont, const BIGNUM *mod, BN_CTX *ctx) {
  if (BN_is_zero(mod)) {
    OPENSSL_PUT_ERROR(BN, BN_R_DIV_BY_ZERO);
    return 0;
  }
  if (!BN_is_odd(mod)) {
    OPENSSL_PUT_ERROR(BN, BN_R_CALLED_WITH_EVEN_MODULUS);
    return 0;
  }
  if (BN_is_negative(mod)) {
    OPENSSL_PUT_ERROR(BN, BN_R_NEGATIVE_NUMBER);
    return 0;
  }

  // Save the modulus.
  if (!BN_copy(&mont->N, mod)) {
    OPENSSL_PUT_ERROR(BN, ERR_R_INTERNAL_ERROR);
    return 0;
  }

  // Find n0 such that n0 * N == -1 (mod r).
  //
  // Only certain BN_BITS2<=32 platforms actually make use of n0[1]. For the
  // others, we could use a shorter R value and use faster |BN_ULONG|-based
  // math instead of |uint64_t|-based math, which would be double-precision.
  // However, currently only the assembler files know which is which.
  uint64_t n0 = bn_mont_n0(mod);
  mont->n0[0] = (BN_ULONG)n0;
#if BN_MONT_CTX_N0_LIMBS == 2
  mont->n0[1] = (BN_ULONG)(n0 >> BN_BITS2);
#else
  mont->n0[1] = 0;
#endif

  // Save RR = R**2 (mod N). R is the smallest power of 2**BN_BITS2 such that R
  // > mod. Even though the assembly on some 32-bit platforms works with 64-bit
  // values, using |BN_BITS2| here, rather than |BN_MONT_CTX_N0_LIMBS *
  // BN_BITS2|, is correct because R**2 will still be a multiple of the latter
  // as |BN_MONT_CTX_N0_LIMBS| is either one or two.
  //
  // XXX: This is not constant time with respect to |mont->N|, but it should be.
  unsigned lgBigR = (BN_num_bits(mod) + (BN_BITS2 - 1)) / BN_BITS2 * BN_BITS2;
  if (!bn_mod_exp_base_2_vartime(&mont->RR, lgBigR * 2, &mont->N)) {
    return 0;
  }

  return 1;
}

int BN_MONT_CTX_set_locked(BN_MONT_CTX **pmont, CRYPTO_MUTEX *lock,
                           const BIGNUM *mod, BN_CTX *bn_ctx) {
  CRYPTO_MUTEX_lock_read(lock);
  BN_MONT_CTX *ctx = *pmont;
  CRYPTO_MUTEX_unlock_read(lock);

  if (ctx) {
    return 1;
  }

  CRYPTO_MUTEX_lock_write(lock);
  ctx = *pmont;
  if (ctx) {
    goto out;
  }

  ctx = BN_MONT_CTX_new();
  if (ctx == NULL) {
    goto out;
  }
  if (!BN_MONT_CTX_set(ctx, mod, bn_ctx)) {
    BN_MONT_CTX_free(ctx);
    ctx = NULL;
    goto out;
  }
  *pmont = ctx;

out:
  CRYPTO_MUTEX_unlock_write(lock);
  return ctx != NULL;
}

int BN_to_montgomery(BIGNUM *ret, const BIGNUM *a, const BN_MONT_CTX *mont,
                     BN_CTX *ctx) {
  return BN_mod_mul_montgomery(ret, a, &mont->RR, mont, ctx);
}

static int bn_from_montgomery_in_place(BN_ULONG *r, size_t num_r, BN_ULONG *a,
                                       size_t num_a, const BN_MONT_CTX *mont) {
  const BN_ULONG *n = mont->N.d;
  size_t num_n = mont->N.top;
  if (num_r != num_n || num_a != 2 * num_n) {
    OPENSSL_PUT_ERROR(BN, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  // Add multiples of |n| to |r| until R = 2^(nl * BN_BITS2) divides it. On
  // input, we had |r| < |n| * R, so now |r| < 2 * |n| * R. Note that |r|
  // includes |carry| which is stored separately.
  BN_ULONG n0 = mont->n0[0];
  BN_ULONG carry = 0;
  for (size_t i = 0; i < num_n; i++) {
    BN_ULONG v = bn_mul_add_words(a + i, n, num_n, a[i] * n0);
    v += carry + a[i + num_n];
    carry |= (v != a[i + num_n]);
    carry &= (v <= a[i + num_n]);
    a[i + num_n] = v;
  }

  // Shift |num_n| words to divide by R. We have |a| < 2 * |n|. Note that |a|
  // includes |carry| which is stored separately.
  a += num_n;

  // |a| thus requires at most one additional subtraction |n| to be reduced.
  // Subtract |n| and select the answer in constant time.
  OPENSSL_COMPILE_ASSERT(sizeof(BN_ULONG) <= sizeof(crypto_word_t),
                         crypto_word_t_too_small);
  BN_ULONG v = bn_sub_words(r, a, n, num_n) - carry;
  // |v| is one if |a| - |n| underflowed or zero if it did not. Note |v| cannot
  // be -1. That would imply the subtraction did not fit in |num_n| words, and
  // we know at most one subtraction is needed.
  v = 0u - v;
  for (size_t i = 0; i < num_n; i++) {
    r[i] = constant_time_select_w(v, a[i], r[i]);
    a[i] = 0;
  }
  return 1;
}

static int BN_from_montgomery_word(BIGNUM *ret, BIGNUM *r,
                                   const BN_MONT_CTX *mont) {
  const BIGNUM *n = &mont->N;
  if (n->top == 0) {
    ret->top = 0;
    return 1;
  }

  int max = (2 * n->top);  // carry is stored separately
  if (!bn_wexpand(r, max) ||
      !bn_wexpand(ret, n->top)) {
    return 0;
  }
  // Clear the top words of |r|.
  if (max > r->top) {
    OPENSSL_memset(r->d + r->top, 0, (max - r->top) * sizeof(BN_ULONG));
  }
  r->top = max;
  ret->top = n->top;

  if (!bn_from_montgomery_in_place(ret->d, ret->top, r->d, r->top, mont)) {
    return 0;
  }
  ret->neg = r->neg;

  bn_correct_top(r);
  bn_correct_top(ret);
  return 1;
}

int BN_from_montgomery(BIGNUM *r, const BIGNUM *a, const BN_MONT_CTX *mont,
                       BN_CTX *ctx) {
  int ret = 0;
  BIGNUM *t;

  BN_CTX_start(ctx);
  t = BN_CTX_get(ctx);
  if (t == NULL ||
      !BN_copy(t, a)) {
    goto err;
  }

  ret = BN_from_montgomery_word(r, t, mont);

err:
  BN_CTX_end(ctx);

  return ret;
}

int BN_mod_mul_montgomery(BIGNUM *r, const BIGNUM *a, const BIGNUM *b,
                          const BN_MONT_CTX *mont, BN_CTX *ctx) {
#if !defined(OPENSSL_BN_ASM_MONT)
  return bn_mod_mul_montgomery_fallback(r, a, b, mont, ctx);
#else
  int num = mont->N.top;

  // |bn_mul_mont| requires at least 128 bits of limbs, at least for x86.
  if (num < (128 / BN_BITS2) ||
      a->top != num ||
      b->top != num) {
    return bn_mod_mul_montgomery_fallback(r, a, b, mont, ctx);
  }

  if (!bn_wexpand(r, num)) {
    return 0;
  }
  if (!bn_mul_mont(r->d, a->d, b->d, mont->N.d, mont->n0, num)) {
    // The check above ensures this won't happen.
    assert(0);
    OPENSSL_PUT_ERROR(BN, ERR_R_INTERNAL_ERROR);
    return 0;
  }
  r->neg = a->neg ^ b->neg;
  r->top = num;
  bn_correct_top(r);

  return 1;
#endif
}

static int bn_mod_mul_montgomery_fallback(BIGNUM *r, const BIGNUM *a,
                                          const BIGNUM *b,
                                          const BN_MONT_CTX *mont,
                                          BN_CTX *ctx) {
  int ret = 0;

  BN_CTX_start(ctx);
  BIGNUM *tmp = BN_CTX_get(ctx);
  if (tmp == NULL) {
    goto err;
  }

  if (a == b) {
    if (!BN_sqr(tmp, a, ctx)) {
      goto err;
    }
  } else {
    if (!BN_mul(tmp, a, b, ctx)) {
      goto err;
    }
  }

  // reduce from aRR to aR
  if (!BN_from_montgomery_word(r, tmp, mont)) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  return ret;
}

int bn_to_montgomery_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                           size_t num_a, const BN_MONT_CTX *mont) {
  return bn_mod_mul_montgomery_small(r, num_r, a, num_a, mont->RR.d,
                                     mont->RR.top, mont);
}

int bn_from_montgomery_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                             size_t num_a, const BN_MONT_CTX *mont) {
  size_t num_n = mont->N.top;
  if (num_a > 2 * num_n || num_r != num_n || num_n > BN_SMALL_MAX_WORDS) {
    OPENSSL_PUT_ERROR(BN, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }
  BN_ULONG tmp[BN_SMALL_MAX_WORDS * 2];
  size_t num_tmp = 2 * num_n;
  OPENSSL_memcpy(tmp, a, num_a * sizeof(BN_ULONG));
  OPENSSL_memset(tmp + num_a, 0, (num_tmp - num_a) * sizeof(BN_ULONG));
  int ret = bn_from_montgomery_in_place(r, num_r, tmp, num_tmp, mont);
  OPENSSL_cleanse(tmp, num_tmp * sizeof(BN_ULONG));
  return ret;
}

int bn_mod_mul_montgomery_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                                size_t num_a, const BN_ULONG *b, size_t num_b,
                                const BN_MONT_CTX *mont) {
  size_t num_n = mont->N.top;
  if (num_r != num_n || num_a + num_b > 2 * num_n ||
      num_n > BN_SMALL_MAX_WORDS) {
    OPENSSL_PUT_ERROR(BN, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

#if defined(OPENSSL_BN_ASM_MONT)
  // |bn_mul_mont| requires at least 128 bits of limbs, at least for x86.
  if (num_n >= (128 / BN_BITS2) &&
      num_a == num_n &&
      num_b == num_n) {
    if (!bn_mul_mont(r, a, b, mont->N.d, mont->n0, num_n)) {
      assert(0);  // The check above ensures this won't happen.
      OPENSSL_PUT_ERROR(BN, ERR_R_INTERNAL_ERROR);
      return 0;
    }
    return 1;
  }
#endif

  // Compute the product.
  BN_ULONG tmp[2 * BN_SMALL_MAX_WORDS];
  size_t num_tmp = 2 * num_n;
  size_t num_ab = num_a + num_b;
  if (a == b && num_a == num_b) {
    if (!bn_sqr_small(tmp, num_ab, a, num_a)) {
      return 0;
    }
  } else if (!bn_mul_small(tmp, num_ab, a, num_a, b, num_b)) {
    return 0;
  }

  // Zero-extend to full width and reduce.
  OPENSSL_memset(tmp + num_ab, 0, (num_tmp - num_ab) * sizeof(BN_ULONG));
  int ret = bn_from_montgomery_in_place(r, num_r, tmp, num_tmp, mont);
  OPENSSL_cleanse(tmp, num_tmp * sizeof(BN_ULONG));
  return ret;
}
