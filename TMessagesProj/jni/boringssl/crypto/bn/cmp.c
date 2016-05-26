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

#include "internal.h"


int BN_ucmp(const BIGNUM *a, const BIGNUM *b) {
  int i;
  BN_ULONG t1, t2, *ap, *bp;

  i = a->top - b->top;
  if (i != 0) {
    return i;
  }

  ap = a->d;
  bp = b->d;
  for (i = a->top - 1; i >= 0; i--) {
    t1 = ap[i];
    t2 = bp[i];
    if (t1 != t2) {
      return (t1 > t2) ? 1 : -1;
    }
  }

  return 0;
}

int BN_cmp(const BIGNUM *a, const BIGNUM *b) {
  int i;
  int gt, lt;
  BN_ULONG t1, t2;

  if ((a == NULL) || (b == NULL)) {
    if (a != NULL) {
      return -1;
    } else if (b != NULL) {
      return 1;
    } else {
      return 0;
    }
  }

  if (a->neg != b->neg) {
    if (a->neg) {
      return -1;
    }
    return 1;
  }
  if (a->neg == 0) {
    gt = 1;
    lt = -1;
  } else {
    gt = -1;
    lt = 1;
  }

  if (a->top > b->top) {
    return gt;
  }
  if (a->top < b->top) {
    return lt;
  }

  for (i = a->top - 1; i >= 0; i--) {
    t1 = a->d[i];
    t2 = b->d[i];
    if (t1 > t2) {
      return gt;
    } if (t1 < t2) {
      return lt;
    }
  }

  return 0;
}

int bn_cmp_words(const BN_ULONG *a, const BN_ULONG *b, int n) {
  int i;
  BN_ULONG aa, bb;

  aa = a[n - 1];
  bb = b[n - 1];
  if (aa != bb) {
    return (aa > bb) ? 1 : -1;
  }

  for (i = n - 2; i >= 0; i--) {
    aa = a[i];
    bb = b[i];
    if (aa != bb) {
      return (aa > bb) ? 1 : -1;
    }
  }
  return 0;
}

int bn_cmp_part_words(const BN_ULONG *a, const BN_ULONG *b, int cl, int dl) {
  int n, i;
  n = cl - 1;

  if (dl < 0) {
    for (i = dl; i < 0; i++) {
      if (b[n - i] != 0) {
        return -1; /* a < b */
      }
    }
  }
  if (dl > 0) {
    for (i = dl; i > 0; i--) {
      if (a[n + i] != 0) {
        return 1; /* a > b */
      }
    }
  }

  return bn_cmp_words(a, b, cl);
}

int BN_abs_is_word(const BIGNUM *bn, BN_ULONG w) {
  switch (bn->top) {
    case 1:
      return bn->d[0] == w;
    case 0:
      return w == 0;
    default:
      return 0;
  }
}

int BN_is_zero(const BIGNUM *bn) {
  return bn->top == 0;
}

int BN_is_one(const BIGNUM *bn) {
  return bn->neg == 0 && BN_abs_is_word(bn, 1);
}

int BN_is_word(const BIGNUM *bn, BN_ULONG w) {
  return BN_abs_is_word(bn, w) && (w == 0 || bn->neg == 0);
}

int BN_is_odd(const BIGNUM *bn) {
  return bn->top > 0 && (bn->d[0] & 1) == 1;
}
