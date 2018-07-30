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
#include <openssl/mem.h>

#include "internal.h"


int BN_add(BIGNUM *r, const BIGNUM *a, const BIGNUM *b) {
  const BIGNUM *tmp;
  int a_neg = a->neg, ret;

  //  a +  b	a+b
  //  a + -b	a-b
  // -a +  b	b-a
  // -a + -b	-(a+b)
  if (a_neg ^ b->neg) {
    // only one is negative
    if (a_neg) {
      tmp = a;
      a = b;
      b = tmp;
    }

    // we are now a - b
    if (BN_ucmp(a, b) < 0) {
      if (!BN_usub(r, b, a)) {
        return 0;
      }
      r->neg = 1;
    } else {
      if (!BN_usub(r, a, b)) {
        return 0;
      }
      r->neg = 0;
    }
    return 1;
  }

  ret = BN_uadd(r, a, b);
  r->neg = a_neg;
  return ret;
}

int BN_uadd(BIGNUM *r, const BIGNUM *a, const BIGNUM *b) {
  int max, min, dif;
  BN_ULONG *ap, *bp, *rp, carry, t1, t2;
  const BIGNUM *tmp;

  if (a->top < b->top) {
    tmp = a;
    a = b;
    b = tmp;
  }
  max = a->top;
  min = b->top;
  dif = max - min;

  if (!bn_wexpand(r, max + 1)) {
    return 0;
  }

  r->top = max;

  ap = a->d;
  bp = b->d;
  rp = r->d;

  carry = bn_add_words(rp, ap, bp, min);
  rp += min;
  ap += min;
  bp += min;

  if (carry) {
    while (dif) {
      dif--;
      t1 = *(ap++);
      t2 = t1 + 1;
      *(rp++) = t2;
      if (t2) {
        carry = 0;
        break;
      }
    }
    if (carry) {
      // carry != 0 => dif == 0
      *rp = 1;
      r->top++;
    }
  }

  if (dif && rp != ap) {
    while (dif--) {
      // copy remaining words if ap != rp
      *(rp++) = *(ap++);
    }
  }

  r->neg = 0;
  return 1;
}

int BN_add_word(BIGNUM *a, BN_ULONG w) {
  BN_ULONG l;
  int i;

  // degenerate case: w is zero
  if (!w) {
    return 1;
  }

  // degenerate case: a is zero
  if (BN_is_zero(a)) {
    return BN_set_word(a, w);
  }

  // handle 'a' when negative
  if (a->neg) {
    a->neg = 0;
    i = BN_sub_word(a, w);
    if (!BN_is_zero(a)) {
      a->neg = !(a->neg);
    }
    return i;
  }

  for (i = 0; w != 0 && i < a->top; i++) {
    a->d[i] = l = a->d[i] + w;
    w = (w > l) ? 1 : 0;
  }

  if (w && i == a->top) {
    if (!bn_wexpand(a, a->top + 1)) {
      return 0;
    }
    a->top++;
    a->d[i] = w;
  }

  return 1;
}

int BN_sub(BIGNUM *r, const BIGNUM *a, const BIGNUM *b) {
  int max;
  int add = 0, neg = 0;
  const BIGNUM *tmp;

  //  a -  b	a-b
  //  a - -b	a+b
  // -a -  b	-(a+b)
  // -a - -b	b-a
  if (a->neg) {
    if (b->neg) {
      tmp = a;
      a = b;
      b = tmp;
    } else {
      add = 1;
      neg = 1;
    }
  } else {
    if (b->neg) {
      add = 1;
      neg = 0;
    }
  }

  if (add) {
    if (!BN_uadd(r, a, b)) {
      return 0;
    }

    r->neg = neg;
    return 1;
  }

  // We are actually doing a - b :-)

  max = (a->top > b->top) ? a->top : b->top;
  if (!bn_wexpand(r, max)) {
    return 0;
  }

  if (BN_ucmp(a, b) < 0) {
    if (!BN_usub(r, b, a)) {
      return 0;
    }
    r->neg = 1;
  } else {
    if (!BN_usub(r, a, b)) {
      return 0;
    }
    r->neg = 0;
  }

  return 1;
}

int BN_usub(BIGNUM *r, const BIGNUM *a, const BIGNUM *b) {
  int max, min, dif;
  register BN_ULONG t1, t2, *ap, *bp, *rp;
  int i, carry;

  max = a->top;
  min = b->top;
  dif = max - min;

  if (dif < 0)  // hmm... should not be happening
  {
    OPENSSL_PUT_ERROR(BN, BN_R_ARG2_LT_ARG3);
    return 0;
  }

  if (!bn_wexpand(r, max)) {
    return 0;
  }

  ap = a->d;
  bp = b->d;
  rp = r->d;

  carry = 0;
  for (i = min; i != 0; i--) {
    t1 = *(ap++);
    t2 = *(bp++);
    if (carry) {
      carry = (t1 <= t2);
      t1 -= t2 + 1;
    } else {
      carry = (t1 < t2);
      t1 -= t2;
    }
    *(rp++) = t1;
  }

  if (carry)  // subtracted
  {
    if (!dif) {
      // error: a < b
      return 0;
    }

    while (dif) {
      dif--;
      t1 = *(ap++);
      t2 = t1 - 1;
      *(rp++) = t2;
      if (t1) {
        break;
      }
    }
  }

  if (dif > 0 && rp != ap) {
    OPENSSL_memcpy(rp, ap, sizeof(*rp) * dif);
  }

  r->top = max;
  r->neg = 0;
  bn_correct_top(r);

  return 1;
}

int BN_sub_word(BIGNUM *a, BN_ULONG w) {
  int i;

  // degenerate case: w is zero
  if (!w) {
    return 1;
  }

  // degenerate case: a is zero
  if (BN_is_zero(a)) {
    i = BN_set_word(a, w);
    if (i != 0) {
      BN_set_negative(a, 1);
    }
    return i;
  }

  // handle 'a' when negative
  if (a->neg) {
    a->neg = 0;
    i = BN_add_word(a, w);
    a->neg = 1;
    return i;
  }

  if ((a->top == 1) && (a->d[0] < w)) {
    a->d[0] = w - a->d[0];
    a->neg = 1;
    return 1;
  }

  i = 0;
  for (;;) {
    if (a->d[i] >= w) {
      a->d[i] -= w;
      break;
    } else {
      a->d[i] -= w;
      i++;
      w = 1;
    }
  }

  if ((a->d[i] == 0) && (i == (a->top - 1))) {
    a->top--;
  }

  return 1;
}
