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
 * Copyright (c) 1998-2001 The OpenSSL Project.  All rights reserved.
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

#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/rand.h>
#include <openssl/type_check.h>

#include "internal.h"
#include "../../internal.h"
#include "../rand/internal.h"


static const uint8_t kDefaultAdditionalData[32] = {0};

int BN_rand(BIGNUM *rnd, int bits, int top, int bottom) {
  uint8_t *buf = NULL;
  int ret = 0, bit, bytes, mask;

  if (rnd == NULL) {
    return 0;
  }

  if (top != BN_RAND_TOP_ANY && top != BN_RAND_TOP_ONE &&
      top != BN_RAND_TOP_TWO) {
    OPENSSL_PUT_ERROR(BN, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  if (bottom != BN_RAND_BOTTOM_ANY && bottom != BN_RAND_BOTTOM_ODD) {
    OPENSSL_PUT_ERROR(BN, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return 0;
  }

  if (bits == 0) {
    BN_zero(rnd);
    return 1;
  }

  bytes = (bits + 7) / 8;
  bit = (bits - 1) % 8;
  mask = 0xff << (bit + 1);

  buf = OPENSSL_malloc(bytes);
  if (buf == NULL) {
    OPENSSL_PUT_ERROR(BN, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  // Make a random number and set the top and bottom bits.
  RAND_bytes(buf, bytes);

  if (top != BN_RAND_TOP_ANY) {
    if (top == BN_RAND_TOP_TWO && bits > 1) {
      if (bit == 0) {
        buf[0] = 1;
        buf[1] |= 0x80;
      } else {
        buf[0] |= (3 << (bit - 1));
      }
    } else {
      buf[0] |= (1 << bit);
    }
  }

  buf[0] &= ~mask;

  // Set the bottom bit if requested,
  if (bottom == BN_RAND_BOTTOM_ODD)  {
    buf[bytes - 1] |= 1;
  }

  if (!BN_bin2bn(buf, bytes, rnd)) {
    goto err;
  }

  ret = 1;

err:
  OPENSSL_free(buf);
  return ret;
}

int BN_pseudo_rand(BIGNUM *rnd, int bits, int top, int bottom) {
  return BN_rand(rnd, bits, top, bottom);
}

// bn_less_than_word returns one if the number represented by |len| words at |a|
// is less than |b| and zero otherwise. It performs this computation in time
// independent of the value of |a|. |b| is assumed public.
static int bn_less_than_word(const BN_ULONG *a, size_t len, BN_ULONG b) {
  if (b == 0) {
    return 0;
  }
  if (len == 0) {
    return 1;
  }

  // |a| < |b| iff a[1..len-1] are all zero and a[0] < b.
  OPENSSL_COMPILE_ASSERT(sizeof(BN_ULONG) <= sizeof(crypto_word_t),
                         crypto_word_t_too_small);
  crypto_word_t mask = 0;
  for (size_t i = 1; i < len; i++) {
    mask |= a[i];
  }
  // |mask| is now zero iff a[1..len-1] are all zero.
  mask = constant_time_is_zero_w(mask);
  mask &= constant_time_lt_w(a[0], b);
  return constant_time_select_int(mask, 1, 0);
}

int bn_in_range_words(const BN_ULONG *a, BN_ULONG min_inclusive,
                      const BN_ULONG *max_exclusive, size_t len) {
  return bn_less_than_words(a, max_exclusive, len) &&
         !bn_less_than_word(a, len, min_inclusive);
}

int bn_rand_range_words(BN_ULONG *out, BN_ULONG min_inclusive,
                        const BN_ULONG *max_exclusive, size_t len,
                        const uint8_t additional_data[32]) {
  // This function implements the equivalent of steps 4 through 7 of FIPS 186-4
  // appendices B.4.2 and B.5.2. When called in those contexts, |max_exclusive|
  // is n and |min_inclusive| is one.

  // Compute the bit length of |max_exclusive| (step 1), in terms of a number of
  // |words| worth of entropy to fill and a mask of bits to clear in the top
  // word.
  size_t words = len;
  while (words > 0 && max_exclusive[words - 1] == 0) {
    words--;
  }
  if (words == 0 ||
      (words == 1 && max_exclusive[0] <= min_inclusive)) {
    OPENSSL_PUT_ERROR(BN, BN_R_INVALID_RANGE);
    return 0;
  }
  BN_ULONG mask = max_exclusive[words - 1];
  // This sets all bits in |mask| below the most significant bit.
  mask |= mask >> 1;
  mask |= mask >> 2;
  mask |= mask >> 4;
  mask |= mask >> 8;
  mask |= mask >> 16;
#if defined(OPENSSL_64_BIT)
  mask |= mask >> 32;
#endif

  // Fill any unused words with zero.
  OPENSSL_memset(out + words, 0, (len - words) * sizeof(BN_ULONG));

  unsigned count = 100;
  do {
    if (!--count) {
      OPENSSL_PUT_ERROR(BN, BN_R_TOO_MANY_ITERATIONS);
      return 0;
    }

    // Steps 4 and 5. Use |words| and |mask| together to obtain a string of N
    // bits, where N is the bit length of |max_exclusive|.
    RAND_bytes_with_additional_data((uint8_t *)out, words * sizeof(BN_ULONG),
                                    additional_data);
    out[words - 1] &= mask;

    // If out >= max_exclusive or out < min_inclusive, retry. This implements
    // the equivalent of steps 6 and 7 without leaking the value of |out|.
  } while (!bn_in_range_words(out, min_inclusive, max_exclusive, words));
  return 1;
}

int BN_rand_range_ex(BIGNUM *r, BN_ULONG min_inclusive,
                     const BIGNUM *max_exclusive) {
  if (!bn_wexpand(r, max_exclusive->top) ||
      !bn_rand_range_words(r->d, min_inclusive, max_exclusive->d,
                           max_exclusive->top, kDefaultAdditionalData)) {
    return 0;
  }

  r->neg = 0;
  r->top = max_exclusive->top;
  bn_correct_top(r);
  return 1;
}

int BN_rand_range(BIGNUM *r, const BIGNUM *range) {
  return BN_rand_range_ex(r, 0, range);
}

int BN_pseudo_rand_range(BIGNUM *r, const BIGNUM *range) {
  return BN_rand_range(r, range);
}
