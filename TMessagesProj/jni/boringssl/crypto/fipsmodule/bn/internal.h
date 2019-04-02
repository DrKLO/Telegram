/* Copyright (C) 1995-1997 Eric Young (eay@cryptsoft.com)
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
 * Hudson (tjh@cryptsoft.com).
 *
 */
/* ====================================================================
 * Copyright 2002 Sun Microsystems, Inc. ALL RIGHTS RESERVED.
 *
 * Portions of the attached software ("Contribution") are developed by
 * SUN MICROSYSTEMS, INC., and are contributed to the OpenSSL project.
 *
 * The Contribution is licensed pursuant to the Eric Young open source
 * license provided above.
 *
 * The binary polynomial arithmetic software is originally written by
 * Sheueling Chang Shantz and Douglas Stebila of Sun Microsystems
 * Laboratories. */

#ifndef OPENSSL_HEADER_BN_INTERNAL_H
#define OPENSSL_HEADER_BN_INTERNAL_H

#include <openssl/base.h>

#if defined(OPENSSL_X86_64) && defined(_MSC_VER)
OPENSSL_MSVC_PRAGMA(warning(push, 3))
#include <intrin.h>
OPENSSL_MSVC_PRAGMA(warning(pop))
#pragma intrinsic(__umulh, _umul128)
#endif

#include "../../internal.h"

#if defined(__cplusplus)
extern "C" {
#endif

#if defined(OPENSSL_64_BIT)

#if defined(BORINGSSL_HAS_UINT128)
// MSVC doesn't support two-word integers on 64-bit.
#define BN_ULLONG uint128_t
#if defined(BORINGSSL_CAN_DIVIDE_UINT128)
#define BN_CAN_DIVIDE_ULLONG
#endif
#endif

#define BN_BITS2 64
#define BN_BYTES 8
#define BN_BITS4 32
#define BN_MASK2 (0xffffffffffffffffUL)
#define BN_MASK2l (0xffffffffUL)
#define BN_MASK2h (0xffffffff00000000UL)
#define BN_MASK2h1 (0xffffffff80000000UL)
#define BN_MONT_CTX_N0_LIMBS 1
#define BN_DEC_CONV (10000000000000000000UL)
#define BN_DEC_NUM 19
#define TOBN(hi, lo) ((BN_ULONG)(hi) << 32 | (lo))

#elif defined(OPENSSL_32_BIT)

#define BN_ULLONG uint64_t
#define BN_CAN_DIVIDE_ULLONG
#define BN_BITS2 32
#define BN_BYTES 4
#define BN_BITS4 16
#define BN_MASK2 (0xffffffffUL)
#define BN_MASK2l (0xffffUL)
#define BN_MASK2h1 (0xffff8000UL)
#define BN_MASK2h (0xffff0000UL)
// On some 32-bit platforms, Montgomery multiplication is done using 64-bit
// arithmetic with SIMD instructions. On such platforms, |BN_MONT_CTX::n0|
// needs to be two words long. Only certain 32-bit platforms actually make use
// of n0[1] and shorter R value would suffice for the others. However,
// currently only the assembly files know which is which.
#define BN_MONT_CTX_N0_LIMBS 2
#define BN_DEC_CONV (1000000000UL)
#define BN_DEC_NUM 9
#define TOBN(hi, lo) (lo), (hi)

#else
#error "Must define either OPENSSL_32_BIT or OPENSSL_64_BIT"
#endif


#define STATIC_BIGNUM(x)                                    \
  {                                                         \
    (BN_ULONG *)(x), sizeof(x) / sizeof(BN_ULONG),          \
        sizeof(x) / sizeof(BN_ULONG), 0, BN_FLG_STATIC_DATA \
  }

#if defined(BN_ULLONG)
#define Lw(t) ((BN_ULONG)(t))
#define Hw(t) ((BN_ULONG)((t) >> BN_BITS2))
#endif

// bn_correct_top decrements |bn->top| until |bn->d[top-1]| is non-zero or
// until |top| is zero. If |bn| is zero, |bn->neg| is set to zero.
void bn_correct_top(BIGNUM *bn);

// bn_wexpand ensures that |bn| has at least |words| works of space without
// altering its value. It returns one on success or zero on allocation
// failure.
int bn_wexpand(BIGNUM *bn, size_t words);

// bn_expand acts the same as |bn_wexpand|, but takes a number of bits rather
// than a number of words.
int bn_expand(BIGNUM *bn, size_t bits);

// bn_set_words sets |bn| to the value encoded in the |num| words in |words|,
// least significant word first.
int bn_set_words(BIGNUM *bn, const BN_ULONG *words, size_t num);

// bn_mul_add_words multiples |ap| by |w|, adds the result to |rp|, and places
// the result in |rp|. |ap| and |rp| must both be |num| words long. It returns
// the carry word of the operation. |ap| and |rp| may be equal but otherwise may
// not alias.
BN_ULONG bn_mul_add_words(BN_ULONG *rp, const BN_ULONG *ap, size_t num,
                          BN_ULONG w);

// bn_mul_words multiples |ap| by |w| and places the result in |rp|. |ap| and
// |rp| must both be |num| words long. It returns the carry word of the
// operation. |ap| and |rp| may be equal but otherwise may not alias.
BN_ULONG bn_mul_words(BN_ULONG *rp, const BN_ULONG *ap, size_t num, BN_ULONG w);

// bn_sqr_words sets |rp[2*i]| and |rp[2*i+1]| to |ap[i]|'s square, for all |i|
// up to |num|. |ap| is an array of |num| words and |rp| an array of |2*num|
// words. |ap| and |rp| may not alias.
//
// This gives the contribution of the |ap[i]*ap[i]| terms when squaring |ap|.
void bn_sqr_words(BN_ULONG *rp, const BN_ULONG *ap, size_t num);

// bn_add_words adds |ap| to |bp| and places the result in |rp|, each of which
// are |num| words long. It returns the carry bit, which is one if the operation
// overflowed and zero otherwise. Any pair of |ap|, |bp|, and |rp| may be equal
// to each other but otherwise may not alias.
BN_ULONG bn_add_words(BN_ULONG *rp, const BN_ULONG *ap, const BN_ULONG *bp,
                      size_t num);

// bn_sub_words subtracts |bp| from |ap| and places the result in |rp|. It
// returns the borrow bit, which is one if the computation underflowed and zero
// otherwise. Any pair of |ap|, |bp|, and |rp| may be equal to each other but
// otherwise may not alias.
BN_ULONG bn_sub_words(BN_ULONG *rp, const BN_ULONG *ap, const BN_ULONG *bp,
                      size_t num);

// bn_mul_comba4 sets |r| to the product of |a| and |b|.
void bn_mul_comba4(BN_ULONG r[8], const BN_ULONG a[4], const BN_ULONG b[4]);

// bn_mul_comba8 sets |r| to the product of |a| and |b|.
void bn_mul_comba8(BN_ULONG r[16], const BN_ULONG a[8], const BN_ULONG b[8]);

// bn_sqr_comba8 sets |r| to |a|^2.
void bn_sqr_comba8(BN_ULONG r[16], const BN_ULONG a[4]);

// bn_sqr_comba4 sets |r| to |a|^2.
void bn_sqr_comba4(BN_ULONG r[8], const BN_ULONG a[4]);

// bn_cmp_words returns a value less than, equal to or greater than zero if
// the, length |n|, array |a| is less than, equal to or greater than |b|.
int bn_cmp_words(const BN_ULONG *a, const BN_ULONG *b, int n);

// bn_cmp_words returns a value less than, equal to or greater than zero if the
// array |a| is less than, equal to or greater than |b|. The arrays can be of
// different lengths: |cl| gives the minimum of the two lengths and |dl| gives
// the length of |a| minus the length of |b|.
int bn_cmp_part_words(const BN_ULONG *a, const BN_ULONG *b, int cl, int dl);

// bn_less_than_words returns one if |a| < |b| and zero otherwise, where |a|
// and |b| both are |len| words long. It runs in constant time.
int bn_less_than_words(const BN_ULONG *a, const BN_ULONG *b, size_t len);

// bn_in_range_words returns one if |min_inclusive| <= |a| < |max_exclusive|,
// where |a| and |max_exclusive| both are |len| words long. This function leaks
// which of [0, min_inclusive), [min_inclusive, max_exclusive), and
// [max_exclusive, 2^(BN_BITS2*len)) contains |a|, but otherwise the value of
// |a| is secret.
int bn_in_range_words(const BN_ULONG *a, BN_ULONG min_inclusive,
                      const BN_ULONG *max_exclusive, size_t len);

// bn_rand_range_words sets |out| to a uniformly distributed random number from
// |min_inclusive| to |max_exclusive|. Both |out| and |max_exclusive| are |len|
// words long.
//
// This function runs in time independent of the result, but |min_inclusive| and
// |max_exclusive| are public data. (Information about the range is unavoidably
// leaked by how many iterations it took to select a number.)
int bn_rand_range_words(BN_ULONG *out, BN_ULONG min_inclusive,
                        const BN_ULONG *max_exclusive, size_t len,
                        const uint8_t additional_data[32]);

int bn_mul_mont(BN_ULONG *rp, const BN_ULONG *ap, const BN_ULONG *bp,
                const BN_ULONG *np, const BN_ULONG *n0, int num);

uint64_t bn_mont_n0(const BIGNUM *n);
int bn_mod_exp_base_2_vartime(BIGNUM *r, unsigned p, const BIGNUM *n);

#if defined(OPENSSL_X86_64) && defined(_MSC_VER)
#define BN_UMULT_LOHI(low, high, a, b) ((low) = _umul128((a), (b), &(high)))
#endif

#if !defined(BN_ULLONG) && !defined(BN_UMULT_LOHI)
#error "Either BN_ULLONG or BN_UMULT_LOHI must be defined on every platform."
#endif

// bn_mod_inverse_prime sets |out| to the modular inverse of |a| modulo |p|,
// computed with Fermat's Little Theorem. It returns one on success and zero on
// error. If |mont_p| is NULL, one will be computed temporarily.
int bn_mod_inverse_prime(BIGNUM *out, const BIGNUM *a, const BIGNUM *p,
                         BN_CTX *ctx, const BN_MONT_CTX *mont_p);

// bn_mod_inverse_secret_prime behaves like |bn_mod_inverse_prime| but uses
// |BN_mod_exp_mont_consttime| instead of |BN_mod_exp_mont| in hopes of
// protecting the exponent.
int bn_mod_inverse_secret_prime(BIGNUM *out, const BIGNUM *a, const BIGNUM *p,
                                BN_CTX *ctx, const BN_MONT_CTX *mont_p);

// bn_jacobi returns the Jacobi symbol of |a| and |b| (which is -1, 0 or 1), or
// -2 on error.
int bn_jacobi(const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);

// bn_is_bit_set_words returns one if bit |bit| is set in |a| and zero
// otherwise.
int bn_is_bit_set_words(const BN_ULONG *a, size_t num, unsigned bit);


// Low-level operations for small numbers.
//
// The following functions implement algorithms suitable for use with scalars
// and field elements in elliptic curves. They rely on the number being small
// both to stack-allocate various temporaries and because they do not implement
// optimizations useful for the larger values used in RSA.

// BN_SMALL_MAX_WORDS is the largest size input these functions handle. This
// limit allows temporaries to be more easily stack-allocated. This limit is set
// to accommodate P-521.
#if defined(OPENSSL_32_BIT)
#define BN_SMALL_MAX_WORDS 17
#else
#define BN_SMALL_MAX_WORDS 9
#endif

// bn_mul_small sets |r| to |a|*|b|. |num_r| must be |num_a| + |num_b|. |r| may
// not alias with |a| or |b|. This function returns one on success and zero if
// lengths are inconsistent.
int bn_mul_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a, size_t num_a,
                 const BN_ULONG *b, size_t num_b);

// bn_sqr_small sets |r| to |a|^2. |num_a| must be at most |BN_SMALL_MAX_WORDS|.
// |num_r| must be |num_a|*2. |r| and |a| may not alias. This function returns
// one on success and zero on programmer error.
int bn_sqr_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a, size_t num_a);

// In the following functions, the modulus must be at most |BN_SMALL_MAX_WORDS|
// words long.

// bn_to_montgomery_small sets |r| to |a| translated to the Montgomery domain.
// |num_a| and |num_r| must be the length of the modulus, which is
// |mont->N.top|. |a| must be fully reduced. This function returns one on
// success and zero if lengths are inconsistent. |r| and |a| may alias.
int bn_to_montgomery_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                           size_t num_a, const BN_MONT_CTX *mont);

// bn_from_montgomery_small sets |r| to |a| translated out of the Montgomery
// domain. |num_r| must be the length of the modulus, which is |mont->N.top|.
// |a| must be at most |mont->N.top| * R and |num_a| must be at most 2 *
// |mont->N.top|. This function returns one on success and zero if lengths are
// inconsistent. |r| and |a| may alias.
int bn_from_montgomery_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                             size_t num_a, const BN_MONT_CTX *mont);

// bn_mod_mul_montgomery_small sets |r| to |a| * |b| mod |mont->N|. Both inputs
// and outputs are in the Montgomery domain. |num_r| must be the length of the
// modulus, which is |mont->N.top|. This function returns one on success and
// zero on internal error or inconsistent lengths. Any two of |r|, |a|, and |b|
// may alias.
//
// This function requires |a| * |b| < N * R, where N is the modulus and R is the
// Montgomery divisor, 2^(N.top * BN_BITS2). This should generally be satisfied
// by ensuring |a| and |b| are fully reduced, however ECDSA has one computation
// which requires the more general bound.
int bn_mod_mul_montgomery_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                                size_t num_a, const BN_ULONG *b, size_t num_b,
                                const BN_MONT_CTX *mont);

// bn_mod_exp_mont_small sets |r| to |a|^|p| mod |mont->N|. It returns one on
// success and zero on programmer or internal error. Both inputs and outputs are
// in the Montgomery domain. |num_r| and |num_a| must be |mont->N.top|, which
// must be at most |BN_SMALL_MAX_WORDS|. |a| must be fully-reduced. This
// function runs in time independent of |a|, but |p| and |mont->N| are public
// values.
//
// Note this function differs from |BN_mod_exp_mont| which uses Montgomery
// reduction but takes input and output outside the Montgomery domain. Combine
// this function with |bn_from_montgomery_small| and |bn_to_montgomery_small|
// if necessary.
int bn_mod_exp_mont_small(BN_ULONG *r, size_t num_r, const BN_ULONG *a,
                          size_t num_a, const BN_ULONG *p, size_t num_p,
                          const BN_MONT_CTX *mont);

// bn_mod_inverse_prime_mont_small sets |r| to |a|^-1 mod |mont->N|. |mont->N|
// must be a prime. |num_r| and |num_a| must be |mont->N.top|, which must be at
// most |BN_SMALL_MAX_WORDS|. |a| must be fully-reduced. This function runs in
// time independent of |a|, but |mont->N| is a public value.
int bn_mod_inverse_prime_mont_small(BN_ULONG *r, size_t num_r,
                                    const BN_ULONG *a, size_t num_a,
                                    const BN_MONT_CTX *mont);


#if defined(__cplusplus)
}  // extern C
#endif

#endif  // OPENSSL_HEADER_BN_INTERNAL_H
