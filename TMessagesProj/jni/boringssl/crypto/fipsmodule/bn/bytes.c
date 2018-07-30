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

#include <assert.h>
#include <limits.h>

#include "internal.h"


BIGNUM *BN_bin2bn(const uint8_t *in, size_t len, BIGNUM *ret) {
  size_t num_words;
  unsigned m;
  BN_ULONG word = 0;
  BIGNUM *bn = NULL;

  if (ret == NULL) {
    ret = bn = BN_new();
  }

  if (ret == NULL) {
    return NULL;
  }

  if (len == 0) {
    ret->top = 0;
    return ret;
  }

  num_words = ((len - 1) / BN_BYTES) + 1;
  m = (len - 1) % BN_BYTES;
  if (!bn_wexpand(ret, num_words)) {
    if (bn) {
      BN_free(bn);
    }
    return NULL;
  }

  // |bn_wexpand| must check bounds on |num_words| to write it into
  // |ret->dmax|.
  assert(num_words <= INT_MAX);
  ret->top = (int)num_words;
  ret->neg = 0;

  while (len--) {
    word = (word << 8) | *(in++);
    if (m-- == 0) {
      ret->d[--num_words] = word;
      word = 0;
      m = BN_BYTES - 1;
    }
  }

  // need to call this due to clear byte at top if avoiding having the top bit
  // set (-ve number)
  bn_correct_top(ret);
  return ret;
}

BIGNUM *BN_le2bn(const uint8_t *in, size_t len, BIGNUM *ret) {
  BIGNUM *bn = NULL;
  if (ret == NULL) {
    bn = BN_new();
    ret = bn;
  }

  if (ret == NULL) {
    return NULL;
  }

  if (len == 0) {
    ret->top = 0;
    ret->neg = 0;
    return ret;
  }

  // Reserve enough space in |ret|.
  size_t num_words = ((len - 1) / BN_BYTES) + 1;
  if (!bn_wexpand(ret, num_words)) {
    BN_free(bn);
    return NULL;
  }
  ret->top = num_words;

  // Make sure the top bytes will be zeroed.
  ret->d[num_words - 1] = 0;

  // We only support little-endian platforms, so we can simply memcpy the
  // internal representation.
  OPENSSL_memcpy(ret->d, in, len);

  bn_correct_top(ret);
  return ret;
}

size_t BN_bn2bin(const BIGNUM *in, uint8_t *out) {
  size_t n, i;
  BN_ULONG l;

  n = i = BN_num_bytes(in);
  while (i--) {
    l = in->d[i / BN_BYTES];
    *(out++) = (unsigned char)(l >> (8 * (i % BN_BYTES))) & 0xff;
  }
  return n;
}

int BN_bn2le_padded(uint8_t *out, size_t len, const BIGNUM *in) {
  // If we don't have enough space, fail out.
  size_t num_bytes = BN_num_bytes(in);
  if (len < num_bytes) {
    return 0;
  }

  // We only support little-endian platforms, so we can simply memcpy into the
  // internal representation.
  OPENSSL_memcpy(out, in->d, num_bytes);

  // Pad out the rest of the buffer with zeroes.
  OPENSSL_memset(out + num_bytes, 0, len - num_bytes);

  return 1;
}

// constant_time_select_ulong returns |x| if |v| is 1 and |y| if |v| is 0. Its
// behavior is undefined if |v| takes any other value.
static BN_ULONG constant_time_select_ulong(int v, BN_ULONG x, BN_ULONG y) {
  BN_ULONG mask = v;
  mask--;

  return (~mask & x) | (mask & y);
}

// constant_time_le_size_t returns 1 if |x| <= |y| and 0 otherwise. |x| and |y|
// must not have their MSBs set.
static int constant_time_le_size_t(size_t x, size_t y) {
  return ((x - y - 1) >> (sizeof(size_t) * 8 - 1)) & 1;
}

// read_word_padded returns the |i|'th word of |in|, if it is not out of
// bounds. Otherwise, it returns 0. It does so without branches on the size of
// |in|, however it necessarily does not have the same memory access pattern. If
// the access would be out of bounds, it reads the last word of |in|. |in| must
// not be zero.
static BN_ULONG read_word_padded(const BIGNUM *in, size_t i) {
  // Read |in->d[i]| if valid. Otherwise, read the last word.
  BN_ULONG l = in->d[constant_time_select_ulong(
      constant_time_le_size_t(in->dmax, i), in->dmax - 1, i)];

  // Clamp to zero if above |d->top|.
  return constant_time_select_ulong(constant_time_le_size_t(in->top, i), 0, l);
}

int BN_bn2bin_padded(uint8_t *out, size_t len, const BIGNUM *in) {
  // Special case for |in| = 0. Just branch as the probability is negligible.
  if (BN_is_zero(in)) {
    OPENSSL_memset(out, 0, len);
    return 1;
  }

  // Check if the integer is too big. This case can exit early in non-constant
  // time.
  if ((size_t)in->top > (len + (BN_BYTES - 1)) / BN_BYTES) {
    return 0;
  }
  if ((len % BN_BYTES) != 0) {
    BN_ULONG l = read_word_padded(in, len / BN_BYTES);
    if (l >> (8 * (len % BN_BYTES)) != 0) {
      return 0;
    }
  }

  // Write the bytes out one by one. Serialization is done without branching on
  // the bits of |in| or on |in->top|, but if the routine would otherwise read
  // out of bounds, the memory access pattern can't be fixed. However, for an
  // RSA key of size a multiple of the word size, the probability of BN_BYTES
  // leading zero octets is low.
  //
  // See Falko Stenzke, "Manger's Attack revisited", ICICS 2010.
  size_t i = len;
  while (i--) {
    BN_ULONG l = read_word_padded(in, i / BN_BYTES);
    *(out++) = (uint8_t)(l >> (8 * (i % BN_BYTES))) & 0xff;
  }
  return 1;
}

BN_ULONG BN_get_word(const BIGNUM *bn) {
  switch (bn->top) {
    case 0:
      return 0;
    case 1:
      return bn->d[0];
    default:
      return BN_MASK2;
  }
}

int BN_get_u64(const BIGNUM *bn, uint64_t *out) {
  switch (bn->top) {
    case 0:
      *out = 0;
      return 1;
    case 1:
      *out = bn->d[0];
      return 1;
#if defined(OPENSSL_32_BIT)
    case 2:
      *out = (uint64_t) bn->d[0] | (((uint64_t) bn->d[1]) << 32);
      return 1;
#endif
    default:
      return 0;
  }
}
