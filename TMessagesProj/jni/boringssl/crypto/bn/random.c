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
#include <openssl/sha.h>

int BN_rand(BIGNUM *rnd, int bits, int top, int bottom) {
  uint8_t *buf = NULL;
  int ret = 0, bit, bytes, mask;

  if (rnd == NULL) {
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

  /* Make a random number and set the top and bottom bits. */
  if (!RAND_bytes(buf, bytes)) {
    goto err;
  }

  if (top != -1) {
    if (top && bits > 1) {
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

  /* set bottom bit if requested */
  if (bottom)  {
    buf[bytes - 1] |= 1;
  }

  if (!BN_bin2bn(buf, bytes, rnd)) {
    goto err;
  }

  ret = 1;

err:
  if (buf != NULL) {
    OPENSSL_cleanse(buf, bytes);
    OPENSSL_free(buf);
  }
  return (ret);
}

int BN_pseudo_rand(BIGNUM *rnd, int bits, int top, int bottom) {
  return BN_rand(rnd, bits, top, bottom);
}

int BN_rand_range(BIGNUM *r, const BIGNUM *range) {
  unsigned n;
  unsigned count = 100;

  if (range->neg || BN_is_zero(range)) {
    OPENSSL_PUT_ERROR(BN, BN_R_INVALID_RANGE);
    return 0;
  }

  n = BN_num_bits(range); /* n > 0 */

  /* BN_is_bit_set(range, n - 1) always holds */
  if (n == 1) {
    BN_zero(r);
  } else if (!BN_is_bit_set(range, n - 2) && !BN_is_bit_set(range, n - 3)) {
    /* range = 100..._2,
     * so  3*range (= 11..._2)  is exactly one bit longer than  range */
    do {
      if (!BN_rand(r, n + 1, -1 /* don't set most significant bits */,
                   0 /* don't set least significant bits */)) {
        return 0;
      }

      /* If r < 3*range, use r := r MOD range (which is either r, r - range, or
       * r - 2*range). Otherwise, iterate again. Since 3*range = 11..._2, each
       * iteration succeeds with probability >= .75. */
      if (BN_cmp(r, range) >= 0) {
        if (!BN_sub(r, r, range)) {
          return 0;
        }
        if (BN_cmp(r, range) >= 0) {
          if (!BN_sub(r, r, range)) {
            return 0;
          }
        }
      }

      if (!--count) {
        OPENSSL_PUT_ERROR(BN, BN_R_TOO_MANY_ITERATIONS);
        return 0;
      }
    } while (BN_cmp(r, range) >= 0);
  } else {
    do {
      /* range = 11..._2  or  range = 101..._2 */
      if (!BN_rand(r, n, -1, 0)) {
        return 0;
      }

      if (!--count) {
        OPENSSL_PUT_ERROR(BN, BN_R_TOO_MANY_ITERATIONS);
        return 0;
      }
    } while (BN_cmp(r, range) >= 0);
  }

  return 1;
}

int BN_pseudo_rand_range(BIGNUM *r, const BIGNUM *range) {
  return BN_rand_range(r, range);
}

int BN_generate_dsa_nonce(BIGNUM *out, const BIGNUM *range, const BIGNUM *priv,
                          const uint8_t *message, size_t message_len,
                          BN_CTX *ctx) {
  SHA512_CTX sha;
  /* We use 512 bits of random data per iteration to
   * ensure that we have at least |range| bits of randomness. */
  uint8_t random_bytes[64];
  uint8_t digest[SHA512_DIGEST_LENGTH];
  size_t done, todo, attempt;
  const unsigned num_k_bytes = BN_num_bytes(range);
  const unsigned bits_to_mask = (8 - (BN_num_bits(range) % 8)) % 8;
  uint8_t private_bytes[96];
  uint8_t *k_bytes = NULL;
  int ret = 0;

  if (out == NULL) {
    return 0;
  }

  if (BN_is_zero(range)) {
    OPENSSL_PUT_ERROR(BN, BN_R_DIV_BY_ZERO);
    goto err;
  }

  k_bytes = OPENSSL_malloc(num_k_bytes);
  if (!k_bytes) {
    OPENSSL_PUT_ERROR(BN, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  /* We copy |priv| into a local buffer to avoid furthur exposing its
   * length. */
  todo = sizeof(priv->d[0]) * priv->top;
  if (todo > sizeof(private_bytes)) {
    /* No reasonable DSA or ECDSA key should have a private key
     * this large and we don't handle this case in order to avoid
     * leaking the length of the private key. */
    OPENSSL_PUT_ERROR(BN, BN_R_PRIVATE_KEY_TOO_LARGE);
    goto err;
  }
  memcpy(private_bytes, priv->d, todo);
  memset(private_bytes + todo, 0, sizeof(private_bytes) - todo);

  for (attempt = 0;; attempt++) {
    for (done = 0; done < num_k_bytes;) {
      if (!RAND_bytes(random_bytes, sizeof(random_bytes))) {
        goto err;
      }
      SHA512_Init(&sha);
      SHA512_Update(&sha, &attempt, sizeof(attempt));
      SHA512_Update(&sha, &done, sizeof(done));
      SHA512_Update(&sha, private_bytes, sizeof(private_bytes));
      SHA512_Update(&sha, message, message_len);
      SHA512_Update(&sha, random_bytes, sizeof(random_bytes));
      SHA512_Final(digest, &sha);

      todo = num_k_bytes - done;
      if (todo > SHA512_DIGEST_LENGTH) {
        todo = SHA512_DIGEST_LENGTH;
      }
      memcpy(k_bytes + done, digest, todo);
      done += todo;
    }

    k_bytes[0] &= 0xff >> bits_to_mask;

    if (!BN_bin2bn(k_bytes, num_k_bytes, out)) {
      goto err;
    }
    if (BN_cmp(out, range) < 0) {
      break;
    }
  }

  ret = 1;

err:
  OPENSSL_free(k_bytes);
  return ret;
}
