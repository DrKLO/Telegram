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

#if defined(OPENSSL_X86_64) && defined(_MSC_VER) && _MSC_VER >= 1400
#pragma warning(push, 3)
#include <intrin.h>
#pragma warning(pop)
#pragma intrinsic(__umulh, _umul128)
#endif

#if defined(__cplusplus)
extern "C" {
#endif

/* bn_expand acts the same as |BN_wexpand|, but takes a number of bits rather
 * than a number of words. */
BIGNUM *bn_expand(BIGNUM *bn, unsigned bits);

#if defined(OPENSSL_64_BIT)

#if !defined(_MSC_VER)
/* MSVC doesn't support two-word integers on 64-bit. */
#define BN_LLONG	__int128_t
#define BN_ULLONG	__uint128_t
#endif

#define BN_BITS		128
#define BN_BITS2	64
#define BN_BYTES	8
#define BN_BITS4	32
#define BN_MASK		(0xffffffffffffffffffffffffffffffffLL)
#define BN_MASK2	(0xffffffffffffffffL)
#define BN_MASK2l	(0xffffffffL)
#define BN_MASK2h	(0xffffffff00000000L)
#define BN_MASK2h1	(0xffffffff80000000L)
#define BN_TBIT		(0x8000000000000000L)
#define BN_DEC_CONV	(10000000000000000000UL)
#define BN_DEC_NUM	19

#elif defined(OPENSSL_32_BIT)

#define BN_LLONG	int64_t
#define BN_ULLONG	uint64_t
#define BN_MASK		(0xffffffffffffffffLL)
#define BN_BITS		64
#define BN_BITS2	32
#define BN_BYTES	4
#define BN_BITS4	16
#define BN_MASK2	(0xffffffffL)
#define BN_MASK2l	(0xffff)
#define BN_MASK2h1	(0xffff8000L)
#define BN_MASK2h	(0xffff0000L)
#define BN_TBIT		(0x80000000L)
#define BN_DEC_CONV	(1000000000L)
#define BN_DEC_NUM	9

#else
#error "Must define either OPENSSL_32_BIT or OPENSSL_64_BIT"
#endif

/* Pentium pro 16,16,16,32,64 */
/* Alpha       16,16,16,16.64 */
#define BN_MULL_SIZE_NORMAL (16)              /* 32 */
#define BN_MUL_RECURSIVE_SIZE_NORMAL (16)     /* 32 less than */
#define BN_SQR_RECURSIVE_SIZE_NORMAL (16)     /* 32 */
#define BN_MUL_LOW_RECURSIVE_SIZE_NORMAL (32) /* 32 */
#define BN_MONT_CTX_SET_SIZE_WORD (64)        /* 32 */

#if defined(BN_LLONG)
#define Lw(t) (((BN_ULONG)(t))&BN_MASK2)
#define Hw(t) (((BN_ULONG)((t)>>BN_BITS2))&BN_MASK2)
#endif

BN_ULONG bn_mul_add_words(BN_ULONG *rp, const BN_ULONG *ap, int num, BN_ULONG w);
BN_ULONG bn_mul_words(BN_ULONG *rp, const BN_ULONG *ap, int num, BN_ULONG w);
void     bn_sqr_words(BN_ULONG *rp, const BN_ULONG *ap, int num);
BN_ULONG bn_div_words(BN_ULONG h, BN_ULONG l, BN_ULONG d);
BN_ULONG bn_add_words(BN_ULONG *rp, const BN_ULONG *ap, const BN_ULONG *bp,int num);
BN_ULONG bn_sub_words(BN_ULONG *rp, const BN_ULONG *ap, const BN_ULONG *bp,int num);

void bn_mul_comba4(BN_ULONG *r, BN_ULONG *a, BN_ULONG *b);
void bn_mul_comba8(BN_ULONG *r, BN_ULONG *a, BN_ULONG *b);
void bn_sqr_comba8(BN_ULONG *r, const BN_ULONG *a);
void bn_sqr_comba4(BN_ULONG *r, const BN_ULONG *a);

/* bn_cmp_words returns a value less than, equal to or greater than zero if
 * the, length |n|, array |a| is less than, equal to or greater than |b|. */
int bn_cmp_words(const BN_ULONG *a, const BN_ULONG *b, int n);

/* bn_cmp_words returns a value less than, equal to or greater than zero if the
 * array |a| is less than, equal to or greater than |b|. The arrays can be of
 * different lengths: |cl| gives the minimum of the two lengths and |dl| gives
 * the length of |a| minus the length of |b|. */
int bn_cmp_part_words(const BN_ULONG *a, const BN_ULONG *b, int cl, int dl);

int bn_mul_mont(BN_ULONG *rp, const BN_ULONG *ap, const BN_ULONG *bp,
                const BN_ULONG *np, const BN_ULONG *n0, int num);

#if !defined(BN_LLONG)

#define LBITS(a) ((a) & BN_MASK2l)
#define HBITS(a) (((a) >> BN_BITS4) & BN_MASK2l)
#define L2HBITS(a) (((a) << BN_BITS4) & BN_MASK2)

#define LLBITS(a) ((a) & BN_MASKl)
#define LHBITS(a) (((a) >> BN_BITS2) & BN_MASKl)
#define LL2HBITS(a) ((BN_ULLONG)((a) & BN_MASKl) << BN_BITS2)

#define mul64(l, h, bl, bh)       \
  {                               \
    BN_ULONG m, m1, lt, ht;       \
                                  \
    lt = l;                       \
    ht = h;                       \
    m = (bh) * (lt);              \
    lt = (bl) * (lt);             \
    m1 = (bl) * (ht);             \
    ht = (bh) * (ht);             \
    m = (m + m1) & BN_MASK2;      \
    if (m < m1)                   \
      ht += L2HBITS((BN_ULONG)1); \
    ht += HBITS(m);               \
    m1 = L2HBITS(m);              \
    lt = (lt + m1) & BN_MASK2;    \
    if (lt < m1)                  \
      ht++;                       \
    (l) = lt;                     \
    (h) = ht;                     \
  }

#endif  /* !defined(BN_LLONG) */

#if !defined(OPENSSL_NO_ASM) && defined(OPENSSL_X86_64)
# if defined(__GNUC__) && __GNUC__ >= 2
#  define BN_UMULT_HIGH(a,b)	({	\
	register BN_ULONG ret,discard;	\
	__asm__ ("mulq	%3"		\
	     : "=a"(discard),"=d"(ret)	\
	     : "a"(a), "g"(b)		\
	     : "cc");			\
	ret;			})
#  define BN_UMULT_LOHI(low,high,a,b)	\
	__asm__ ("mulq	%3"		\
		: "=a"(low),"=d"(high)	\
		: "a"(a),"g"(b)		\
		: "cc");
# elif defined(_MSC_VER) && _MSC_VER >= 1400
#  define BN_UMULT_HIGH(a, b) __umulh((a), (b))
#  define BN_UMULT_LOHI(low, high, a, b) ((low) = _umul128((a), (b), &(high)))
# endif
#elif !defined(OPENSSL_NO_ASM) && defined(OPENSSL_AARCH64)
# if defined(__GNUC__) && __GNUC__>=2
#  define BN_UMULT_HIGH(a,b)	({	\
	register BN_ULONG ret;		\
	__asm__ ("umulh	%0,%1,%2"	\
	     : "=r"(ret)		\
	     : "r"(a), "r"(b));		\
	ret;			})
# endif
#endif


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_BN_INTERNAL_H */
