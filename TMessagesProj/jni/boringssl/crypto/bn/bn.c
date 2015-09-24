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

#include <limits.h>
#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"


BIGNUM *BN_new(void) {
  BIGNUM *bn = OPENSSL_malloc(sizeof(BIGNUM));

  if (bn == NULL) {
    OPENSSL_PUT_ERROR(BN, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  memset(bn, 0, sizeof(BIGNUM));
  bn->flags = BN_FLG_MALLOCED;

  return bn;
}

void BN_init(BIGNUM *bn) {
  memset(bn, 0, sizeof(BIGNUM));
}

void BN_free(BIGNUM *bn) {
  if (bn == NULL) {
    return;
  }

  if ((bn->flags & BN_FLG_STATIC_DATA) == 0) {
    OPENSSL_free(bn->d);
  }

  if (bn->flags & BN_FLG_MALLOCED) {
    OPENSSL_free(bn);
  } else {
    bn->d = NULL;
  }
}

void BN_clear_free(BIGNUM *bn) {
  char should_free;

  if (bn == NULL) {
    return;
  }

  if (bn->d != NULL) {
    OPENSSL_cleanse(bn->d, bn->dmax * sizeof(bn->d[0]));
    if ((bn->flags & BN_FLG_STATIC_DATA) == 0) {
      OPENSSL_free(bn->d);
    }
  }

  should_free = (bn->flags & BN_FLG_MALLOCED) != 0;
  OPENSSL_cleanse(bn, sizeof(BIGNUM));
  if (should_free) {
    OPENSSL_free(bn);
  }
}

BIGNUM *BN_dup(const BIGNUM *src) {
  BIGNUM *copy;

  if (src == NULL) {
    return NULL;
  }

  copy = BN_new();
  if (copy == NULL) {
    return NULL;
  }

  if (!BN_copy(copy, src)) {
    BN_free(copy);
    return NULL;
  }

  return copy;
}

BIGNUM *BN_copy(BIGNUM *dest, const BIGNUM *src) {
  if (src == dest) {
    return dest;
  }

  if (bn_wexpand(dest, src->top) == NULL) {
    return NULL;
  }

  memcpy(dest->d, src->d, sizeof(src->d[0]) * src->top);

  dest->top = src->top;
  dest->neg = src->neg;
  return dest;
}

void BN_clear(BIGNUM *bn) {
  if (bn->d != NULL) {
    memset(bn->d, 0, bn->dmax * sizeof(bn->d[0]));
  }

  bn->top = 0;
  bn->neg = 0;
}

const BIGNUM *BN_value_one(void) {
  static const BN_ULONG data_one = 1;
  static const BIGNUM const_one = {(BN_ULONG *)&data_one, 1, 1, 0,
                                   BN_FLG_STATIC_DATA};

  return &const_one;
}

void BN_with_flags(BIGNUM *out, const BIGNUM *in, int flags) {
  memcpy(out, in, sizeof(BIGNUM));
  out->flags &= ~BN_FLG_MALLOCED;
  out->flags |= BN_FLG_STATIC_DATA | flags;
}

/* BN_num_bits_word returns the minimum number of bits needed to represent the
 * value in |l|. */
unsigned BN_num_bits_word(BN_ULONG l) {
  static const unsigned char bits[256] = {
      0, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5,
      5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
      8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
      8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
      8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
      8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
      8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8};

#if defined(OPENSSL_64_BIT)
  if (l & 0xffffffff00000000L) {
    if (l & 0xffff000000000000L) {
      if (l & 0xff00000000000000L) {
        return (bits[(int)(l >> 56)] + 56);
      } else {
        return (bits[(int)(l >> 48)] + 48);
      }
    } else {
      if (l & 0x0000ff0000000000L) {
        return (bits[(int)(l >> 40)] + 40);
      } else {
        return (bits[(int)(l >> 32)] + 32);
      }
    }
  } else
#endif
  {
    if (l & 0xffff0000L) {
      if (l & 0xff000000L) {
        return (bits[(int)(l >> 24L)] + 24);
      } else {
        return (bits[(int)(l >> 16L)] + 16);
      }
    } else {
      if (l & 0xff00L) {
        return (bits[(int)(l >> 8)] + 8);
      } else {
        return (bits[(int)(l)]);
      }
    }
  }
}

unsigned BN_num_bits(const BIGNUM *bn) {
  const int max = bn->top - 1;

  if (BN_is_zero(bn)) {
    return 0;
  }

  return max*BN_BITS2 + BN_num_bits_word(bn->d[max]);
}

unsigned BN_num_bytes(const BIGNUM *bn) {
  return (BN_num_bits(bn) + 7) / 8;
}

void BN_zero(BIGNUM *bn) {
  bn->top = bn->neg = 0;
}

int BN_one(BIGNUM *bn) {
  return BN_set_word(bn, 1);
}

int BN_set_word(BIGNUM *bn, BN_ULONG value) {
  if (value == 0) {
    BN_zero(bn);
    return 1;
  }

  if (bn_wexpand(bn, 1) == NULL) {
    return 0;
  }

  bn->neg = 0;
  bn->d[0] = value;
  bn->top = 1;
  return 1;
}

int BN_is_negative(const BIGNUM *bn) {
  return bn->neg != 0;
}

void BN_set_negative(BIGNUM *bn, int sign) {
  if (sign && !BN_is_zero(bn)) {
    bn->neg = 1;
  } else {
    bn->neg = 0;
  }
}

BIGNUM *bn_wexpand(BIGNUM *bn, unsigned words) {
  BN_ULONG *a;

  if (words <= (unsigned) bn->dmax) {
    return bn;
  }

  if (words > (INT_MAX / (4 * BN_BITS2))) {
    OPENSSL_PUT_ERROR(BN, BN_R_BIGNUM_TOO_LONG);
    return NULL;
  }

  if (bn->flags & BN_FLG_STATIC_DATA) {
    OPENSSL_PUT_ERROR(BN, BN_R_EXPAND_ON_STATIC_BIGNUM_DATA);
    return NULL;
  }

  a = (BN_ULONG *)OPENSSL_malloc(sizeof(BN_ULONG) * words);
  if (a == NULL) {
    OPENSSL_PUT_ERROR(BN, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  memcpy(a, bn->d, sizeof(BN_ULONG) * bn->top);

  OPENSSL_free(bn->d);
  bn->d = a;
  bn->dmax = words;

  return bn;
}

BIGNUM *bn_expand(BIGNUM *bn, unsigned bits) {
  return bn_wexpand(bn, (bits+BN_BITS2-1)/BN_BITS2);
}

void bn_correct_top(BIGNUM *bn) {
  BN_ULONG *ftl;
  int tmp_top = bn->top;

  if (tmp_top > 0) {
    for (ftl = &(bn->d[tmp_top - 1]); tmp_top > 0; tmp_top--) {
      if (*(ftl--)) {
        break;
      }
    }
    bn->top = tmp_top;
  }
}

int BN_get_flags(const BIGNUM *bn, int flags) {
  return bn->flags & flags;
}

void BN_set_flags(BIGNUM *bn, int flags) {
  bn->flags |= flags;
}
