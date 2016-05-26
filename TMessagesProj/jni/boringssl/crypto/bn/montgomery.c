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

#include <string.h>

#include <openssl/mem.h>
#include <openssl/thread.h>

#include "internal.h"
#include "../internal.h"


#if !defined(OPENSSL_NO_ASM) && \
    (defined(OPENSSL_X86) || defined(OPENSSL_X86_64))
#define OPENSSL_BN_ASM_MONT
#endif

BN_MONT_CTX *BN_MONT_CTX_new(void) {
  BN_MONT_CTX *ret = OPENSSL_malloc(sizeof(BN_MONT_CTX));

  if (ret == NULL) {
    return NULL;
  }

  BN_MONT_CTX_init(ret);
  ret->flags = BN_FLG_MALLOCED;
  return ret;
}

void BN_MONT_CTX_init(BN_MONT_CTX *mont) {
  memset(mont, 0, sizeof(BN_MONT_CTX));
  BN_init(&mont->RR);
  BN_init(&mont->N);
  BN_init(&mont->Ni);
}

void BN_MONT_CTX_free(BN_MONT_CTX *mont) {
  if (mont == NULL) {
    return;
  }

  BN_free(&mont->RR);
  BN_free(&mont->N);
  BN_free(&mont->Ni);
  if (mont->flags & BN_FLG_MALLOCED) {
    OPENSSL_free(mont);
  }
}

BN_MONT_CTX *BN_MONT_CTX_copy(BN_MONT_CTX *to, BN_MONT_CTX *from) {
  if (to == from) {
    return to;
  }

  if (!BN_copy(&to->RR, &from->RR) ||
      !BN_copy(&to->N, &from->N) ||
      !BN_copy(&to->Ni, &from->Ni)) {
    return NULL;
  }
  to->ri = from->ri;
  to->n0[0] = from->n0[0];
  to->n0[1] = from->n0[1];
  return to;
}

int BN_MONT_CTX_set(BN_MONT_CTX *mont, const BIGNUM *mod, BN_CTX *ctx) {
  int ret = 0;
  BIGNUM *Ri, *R;
  BIGNUM tmod;
  BN_ULONG buf[2];

  BN_CTX_start(ctx);
  Ri = BN_CTX_get(ctx);
  if (Ri == NULL) {
    goto err;
  }
  R = &mont->RR; /* grab RR as a temp */
  if (!BN_copy(&mont->N, mod)) {
    goto err; /* Set N */
  }
  mont->N.neg = 0;

  BN_init(&tmod);
  tmod.d = buf;
  tmod.dmax = 2;
  tmod.neg = 0;

  mont->ri = (BN_num_bits(mod) + (BN_BITS2 - 1)) / BN_BITS2 * BN_BITS2;

#if defined(OPENSSL_BN_ASM_MONT) && (BN_BITS2 <= 32)
  /* Only certain BN_BITS2<=32 platforms actually make use of
   * n0[1], and we could use the #else case (with a shorter R
   * value) for the others.  However, currently only the assembler
   * files do know which is which. */

  BN_zero(R);
  if (!BN_set_bit(R, 2 * BN_BITS2)) {
    goto err;
  }

  tmod.top = 0;
  if ((buf[0] = mod->d[0])) {
    tmod.top = 1;
  }
  if ((buf[1] = mod->top > 1 ? mod->d[1] : 0)) {
    tmod.top = 2;
  }

  if (BN_mod_inverse(Ri, R, &tmod, ctx) == NULL) {
    goto err;
  }
  if (!BN_lshift(Ri, Ri, 2 * BN_BITS2)) {
    goto err; /* R*Ri */
  }
  if (!BN_is_zero(Ri)) {
    if (!BN_sub_word(Ri, 1)) {
      goto err;
    }
  } else {
    /* if N mod word size == 1 */
    if (bn_expand(Ri, (int)sizeof(BN_ULONG) * 2) == NULL) {
      goto err;
    }
    /* Ri-- (mod double word size) */
    Ri->neg = 0;
    Ri->d[0] = BN_MASK2;
    Ri->d[1] = BN_MASK2;
    Ri->top = 2;
  }

  if (!BN_div(Ri, NULL, Ri, &tmod, ctx)) {
    goto err;
  }
  /* Ni = (R*Ri-1)/N,
   * keep only couple of least significant words: */
  mont->n0[0] = (Ri->top > 0) ? Ri->d[0] : 0;
  mont->n0[1] = (Ri->top > 1) ? Ri->d[1] : 0;
#else
  BN_zero(R);
  if (!BN_set_bit(R, BN_BITS2)) {
    goto err; /* R */
  }

  buf[0] = mod->d[0]; /* tmod = N mod word size */
  buf[1] = 0;
  tmod.top = buf[0] != 0 ? 1 : 0;
  /* Ri = R^-1 mod N*/
  if (BN_mod_inverse(Ri, R, &tmod, ctx) == NULL) {
    goto err;
  }
  if (!BN_lshift(Ri, Ri, BN_BITS2)) {
    goto err; /* R*Ri */
  }
  if (!BN_is_zero(Ri)) {
    if (!BN_sub_word(Ri, 1)) {
      goto err;
    }
  } else {
    /* if N mod word size == 1 */
    if (!BN_set_word(Ri, BN_MASK2)) {
      goto err; /* Ri-- (mod word size) */
    }
  }
  if (!BN_div(Ri, NULL, Ri, &tmod, ctx)) {
    goto err;
  }
  /* Ni = (R*Ri-1)/N,
   * keep only least significant word: */
  mont->n0[0] = (Ri->top > 0) ? Ri->d[0] : 0;
  mont->n0[1] = 0;
#endif

  /* setup RR for conversions */
  BN_zero(&(mont->RR));
  if (!BN_set_bit(&(mont->RR), mont->ri * 2)) {
    goto err;
  }
  if (!BN_mod(&(mont->RR), &(mont->RR), &(mont->N), ctx)) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  return ret;
}

BN_MONT_CTX *BN_MONT_CTX_set_locked(BN_MONT_CTX **pmont, CRYPTO_MUTEX *lock,
                                    const BIGNUM *mod, BN_CTX *bn_ctx) {
  CRYPTO_MUTEX_lock_read(lock);
  BN_MONT_CTX *ctx = *pmont;
  CRYPTO_MUTEX_unlock(lock);

  if (ctx) {
    return ctx;
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
  CRYPTO_MUTEX_unlock(lock);
  return ctx;
}

int BN_to_montgomery(BIGNUM *ret, const BIGNUM *a, const BN_MONT_CTX *mont,
                     BN_CTX *ctx) {
  return BN_mod_mul_montgomery(ret, a, &mont->RR, mont, ctx);
}

#if 0
static int BN_from_montgomery_word(BIGNUM *ret, BIGNUM *r,
                                   const BN_MONT_CTX *mont) {
  const BIGNUM *n;
  BN_ULONG *ap, *np, *rp, n0, v, carry;
  int nl, max, i;

  n = &mont->N;
  nl = n->top;
  if (nl == 0) {
    ret->top = 0;
    return 1;
  }

  max = (2 * nl); /* carry is stored separately */
  if (bn_wexpand(r, max) == NULL) {
    return 0;
  }

  r->neg ^= n->neg;
  np = n->d;
  rp = r->d;

  /* clear the top words of T */
  if (max > r->top) {
    memset(&rp[r->top], 0, (max - r->top) * sizeof(BN_ULONG));
  }

  r->top = max;
  n0 = mont->n0[0];

  for (carry = 0, i = 0; i < nl; i++, rp++) {
    v = bn_mul_add_words(rp, np, nl, (rp[0] * n0) & BN_MASK2);
    v = (v + carry + rp[nl]) & BN_MASK2;
    carry |= (v != rp[nl]);
    carry &= (v <= rp[nl]);
    rp[nl] = v;
  }

  if (bn_wexpand(ret, nl) == NULL) {
    return 0;
  }
  ret->top = nl;
  ret->neg = r->neg;

  rp = ret->d;
  ap = &(r->d[nl]);

  {
    BN_ULONG *nrp;
    size_t m;

    v = bn_sub_words(rp, ap, np, nl) - carry;
    /* if subtraction result is real, then trick unconditional memcpy below to
     * perform in-place "refresh" instead of actual copy. */
    m = (0 - (size_t)v);
    nrp = (BN_ULONG *)(((intptr_t)rp & ~m) | ((intptr_t)ap & m));

    for (i = 0, nl -= 4; i < nl; i += 4) {
      BN_ULONG t1, t2, t3, t4;

      t1 = nrp[i + 0];
      t2 = nrp[i + 1];
      t3 = nrp[i + 2];
      ap[i + 0] = 0;
      t4 = nrp[i + 3];
      ap[i + 1] = 0;
      rp[i + 0] = t1;
      ap[i + 2] = 0;
      rp[i + 1] = t2;
      ap[i + 3] = 0;
      rp[i + 2] = t3;
      rp[i + 3] = t4;
    }

    for (nl += 4; i < nl; i++) {
      rp[i] = nrp[i], ap[i] = 0;
    }
  }

  bn_correct_top(r);
  bn_correct_top(ret);

  return 1;
}
#endif

#define PTR_SIZE_INT size_t

static int BN_from_montgomery_word(BIGNUM *ret, BIGNUM *r, const BN_MONT_CTX *mont)
	{
	BIGNUM *n;
	BN_ULONG *ap,*np,*rp,n0,v,carry;
	int nl,max,i;

	n= (BIGNUM*) &(mont->N);
	nl=n->top;
	if (nl == 0) { ret->top=0; return(1); }

	max=(2*nl); /* carry is stored separately */
	if (bn_wexpand(r,max) == NULL) return(0);

	r->neg^=n->neg;
	np=n->d;
	rp=r->d;

	/* clear the top words of T */
#if 1
	for (i=r->top; i<max; i++) /* memset? XXX */
		rp[i]=0;
#else
	memset(&(rp[r->top]),0,(max-r->top)*sizeof(BN_ULONG)); 
#endif

	r->top=max;
	n0=mont->n0[0];

	for (carry=0, i=0; i<nl; i++, rp++)
		{
		v=bn_mul_add_words(rp,np,nl,(rp[0]*n0)&BN_MASK2);
		v = (v+carry+rp[nl])&BN_MASK2;
		carry |= (v != rp[nl]);
		carry &= (v <= rp[nl]);
		rp[nl]=v;
		}

	if (bn_wexpand(ret,nl) == NULL) return(0);
	ret->top=nl;
	ret->neg=r->neg;

	rp=ret->d;
	ap=&(r->d[nl]);

	{
	BN_ULONG *nrp;
	size_t m;

	v=bn_sub_words(rp,ap,np,nl)-carry;
	/* if subtraction result is real, then
	 * trick unconditional memcpy below to perform in-place
	 * "refresh" instead of actual copy. */
	m=(0-(size_t)v);
	nrp=(BN_ULONG *)(((PTR_SIZE_INT)rp&~m)|((PTR_SIZE_INT)ap&m));

	for (i=0,nl-=4; i<nl; i+=4)
		{
		BN_ULONG t1,t2,t3,t4;
		
		t1=nrp[i+0];
		t2=nrp[i+1];
		t3=nrp[i+2];	ap[i+0]=0;
		t4=nrp[i+3];	ap[i+1]=0;
		rp[i+0]=t1;	ap[i+2]=0;
		rp[i+1]=t2;	ap[i+3]=0;
		rp[i+2]=t3;
		rp[i+3]=t4;
		}
	for (nl+=4; i<nl; i++)
		rp[i]=nrp[i], ap[i]=0;
	}
	bn_correct_top(r);
	bn_correct_top(ret);

	return(1);
	}

int BN_from_montgomery(BIGNUM *ret, const BIGNUM *a, const BN_MONT_CTX *mont,
                       BN_CTX *ctx) {
  int retn = 0;
  BIGNUM *t;

  BN_CTX_start(ctx);
  t = BN_CTX_get(ctx);
  if (t == NULL) {
    return 0;
  }

  if (BN_copy(t, a)) {
    retn = BN_from_montgomery_word(ret, t, mont);
  }
  BN_CTX_end(ctx);

  return retn;
}

int BN_mod_mul_montgomery(BIGNUM *r, const BIGNUM *a, const BIGNUM *b,
                          const BN_MONT_CTX *mont, BN_CTX *ctx) {
  BIGNUM *tmp;
  int ret = 0;

#if defined(OPENSSL_BN_ASM_MONT)
  int num = mont->N.top;

  if (num > 1 && a->top == num && b->top == num) {
    if (bn_wexpand(r, num) == NULL) {
      return 0;
    }
    if (bn_mul_mont(r->d, a->d, b->d, mont->N.d, mont->n0, num)) {
      r->neg = a->neg ^ b->neg;
      r->top = num;
      bn_correct_top(r);
      return 1;
    }
  }
#endif

  BN_CTX_start(ctx);
  tmp = BN_CTX_get(ctx);
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

  /* reduce from aRR to aR */
  if (!BN_from_montgomery_word(r, tmp, mont)) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  return ret;
}
