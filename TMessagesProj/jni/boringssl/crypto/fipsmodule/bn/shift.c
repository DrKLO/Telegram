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
 * [including the GNU Public Licence.] */

#include <openssl/bn.h>

#include <string.h>

#include <openssl/err.h>

#include "internal.h"


int BN_lshift(BIGNUM *r, const BIGNUM *a, int n) {
  int i, nw, lb, rb;
  BN_ULONG *t, *f;
  BN_ULONG l;

  if (n < 0) {
    OPENSSL_PUT_ERROR(BN, BN_R_NEGATIVE_NUMBER);
    return 0;
  }

  r->neg = a->neg;
  nw = n / BN_BITS2;
  if (!bn_wexpand(r, a->top + nw + 1)) {
    return 0;
  }
  lb = n % BN_BITS2;
  rb = BN_BITS2 - lb;
  f = a->d;
  t = r->d;
  t[a->top + nw] = 0;
  if (lb == 0) {
    for (i = a->top - 1; i >= 0; i--) {
      t[nw + i] = f[i];
    }
  } else {
    for (i = a->top - 1; i >= 0; i--) {
      l = f[i];
      t[nw + i + 1] |= l >> rb;
      t[nw + i] = l << lb;
    }
  }
  OPENSSL_memset(t, 0, nw * sizeof(t[0]));
  r->top = a->top + nw + 1;
  bn_correct_top(r);

  return 1;
}

int BN_lshift1(BIGNUM *r, const BIGNUM *a) {
  BN_ULONG *ap, *rp, t, c;
  int i;

  if (r != a) {
    r->neg = a->neg;
    if (!bn_wexpand(r, a->top + 1)) {
      return 0;
    }
    r->top = a->top;
  } else {
    if (!bn_wexpand(r, a->top + 1)) {
      return 0;
    }
  }
  ap = a->d;
  rp = r->d;
  c = 0;
  for (i = 0; i < a->top; i++) {
    t = *(ap++);
    *(rp++) = (t << 1) | c;
    c = t >> (BN_BITS2 - 1);
  }
  if (c) {
    *rp = 1;
    r->top++;
  }

  return 1;
}

int BN_rshift(BIGNUM *r, const BIGNUM *a, int n) {
  int i, j, nw, lb, rb;
  BN_ULONG *t, *f;
  BN_ULONG l, tmp;

  if (n < 0) {
    OPENSSL_PUT_ERROR(BN, BN_R_NEGATIVE_NUMBER);
    return 0;
  }

  nw = n / BN_BITS2;
  rb = n % BN_BITS2;
  lb = BN_BITS2 - rb;
  if (nw >= a->top || a->top == 0) {
    BN_zero(r);
    return 1;
  }
  i = (BN_num_bits(a) - n + (BN_BITS2 - 1)) / BN_BITS2;
  if (r != a) {
    r->neg = a->neg;
    if (!bn_wexpand(r, i)) {
      return 0;
    }
  } else {
    if (n == 0) {
      return 1;  // or the copying loop will go berserk
    }
  }

  f = &(a->d[nw]);
  t = r->d;
  j = a->top - nw;
  r->top = i;

  if (rb == 0) {
    for (i = j; i != 0; i--) {
      *(t++) = *(f++);
    }
  } else {
    l = *(f++);
    for (i = j - 1; i != 0; i--) {
      tmp = l >> rb;
      l = *(f++);
      *(t++) = tmp | (l << lb);
    }
    l >>= rb;
    if (l) {
      *(t) = l;
    }
  }

  if (r->top == 0) {
    r->neg = 0;
  }

  return 1;
}

int BN_rshift1(BIGNUM *r, const BIGNUM *a) {
  BN_ULONG *ap, *rp, t, c;
  int i, j;

  if (BN_is_zero(a)) {
    BN_zero(r);
    return 1;
  }
  i = a->top;
  ap = a->d;
  j = i - (ap[i - 1] == 1);
  if (a != r) {
    if (!bn_wexpand(r, j)) {
      return 0;
    }
    r->neg = a->neg;
  }
  rp = r->d;
  t = ap[--i];
  c = t << (BN_BITS2 - 1);
  if (t >>= 1) {
    rp[i] = t;
  }
  while (i > 0) {
    t = ap[--i];
    rp[i] = (t >> 1) | c;
    c = t << (BN_BITS2 - 1);
  }
  r->top = j;

  if (r->top == 0) {
    r->neg = 0;
  }

  return 1;
}

int BN_set_bit(BIGNUM *a, int n) {
  if (n < 0) {
    return 0;
  }

  int i = n / BN_BITS2;
  int j = n % BN_BITS2;
  if (a->top <= i) {
    if (!bn_wexpand(a, i + 1)) {
      return 0;
    }
    for (int k = a->top; k < i + 1; k++) {
      a->d[k] = 0;
    }
    a->top = i + 1;
  }

  a->d[i] |= (((BN_ULONG)1) << j);

  return 1;
}

int BN_clear_bit(BIGNUM *a, int n) {
  int i, j;

  if (n < 0) {
    return 0;
  }

  i = n / BN_BITS2;
  j = n % BN_BITS2;
  if (a->top <= i) {
    return 0;
  }

  a->d[i] &= (~(((BN_ULONG)1) << j));
  bn_correct_top(a);
  return 1;
}

int bn_is_bit_set_words(const BN_ULONG *a, size_t num, unsigned bit) {
  unsigned i = bit / BN_BITS2;
  unsigned j = bit % BN_BITS2;
  if (i >= num) {
    return 0;
  }
  return (a[i] >> j) & 1;
}

int BN_is_bit_set(const BIGNUM *a, int n) {
  if (n < 0) {
    return 0;
  }
  return bn_is_bit_set_words(a->d, a->top, n);
}

int BN_mask_bits(BIGNUM *a, int n) {
  if (n < 0) {
    return 0;
  }

  int w = n / BN_BITS2;
  int b = n % BN_BITS2;
  if (w >= a->top) {
    return 0;
  }
  if (b == 0) {
    a->top = w;
  } else {
    a->top = w + 1;
    a->d[w] &= ~(BN_MASK2 << b);
  }

  bn_correct_top(a);
  return 1;
}
