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

#include <openssl/rsa.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/thread.h>
#include <openssl/type_check.h>

#include "internal.h"
#include "../bn/internal.h"
#include "../../internal.h"
#include "../delocate.h"


static int check_modulus_and_exponent_sizes(const RSA *rsa) {
  unsigned rsa_bits = BN_num_bits(rsa->n);

  if (rsa_bits > 16 * 1024) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_MODULUS_TOO_LARGE);
    return 0;
  }

  // Mitigate DoS attacks by limiting the exponent size. 33 bits was chosen as
  // the limit based on the recommendations in [1] and [2]. Windows CryptoAPI
  // doesn't support values larger than 32 bits [3], so it is unlikely that
  // exponents larger than 32 bits are being used for anything Windows commonly
  // does.
  //
  // [1] https://www.imperialviolet.org/2012/03/16/rsae.html
  // [2] https://www.imperialviolet.org/2012/03/17/rsados.html
  // [3] https://msdn.microsoft.com/en-us/library/aa387685(VS.85).aspx
  static const unsigned kMaxExponentBits = 33;

  if (BN_num_bits(rsa->e) > kMaxExponentBits) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_E_VALUE);
    return 0;
  }

  // Verify |n > e|. Comparing |rsa_bits| to |kMaxExponentBits| is a small
  // shortcut to comparing |n| and |e| directly. In reality, |kMaxExponentBits|
  // is much smaller than the minimum RSA key size that any application should
  // accept.
  if (rsa_bits <= kMaxExponentBits) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_KEY_SIZE_TOO_SMALL);
    return 0;
  }
  assert(BN_ucmp(rsa->n, rsa->e) > 0);

  return 1;
}

size_t rsa_default_size(const RSA *rsa) {
  return BN_num_bytes(rsa->n);
}

int RSA_encrypt(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                const uint8_t *in, size_t in_len, int padding) {
  if (rsa->n == NULL || rsa->e == NULL) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_VALUE_MISSING);
    return 0;
  }

  const unsigned rsa_size = RSA_size(rsa);
  BIGNUM *f, *result;
  uint8_t *buf = NULL;
  BN_CTX *ctx = NULL;
  int i, ret = 0;

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  if (!check_modulus_and_exponent_sizes(rsa)) {
    return 0;
  }

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }

  BN_CTX_start(ctx);
  f = BN_CTX_get(ctx);
  result = BN_CTX_get(ctx);
  buf = OPENSSL_malloc(rsa_size);
  if (!f || !result || !buf) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  switch (padding) {
    case RSA_PKCS1_PADDING:
      i = RSA_padding_add_PKCS1_type_2(buf, rsa_size, in, in_len);
      break;
    case RSA_PKCS1_OAEP_PADDING:
      // Use the default parameters: SHA-1 for both hashes and no label.
      i = RSA_padding_add_PKCS1_OAEP_mgf1(buf, rsa_size, in, in_len,
                                          NULL, 0, NULL, NULL);
      break;
    case RSA_NO_PADDING:
      i = RSA_padding_add_none(buf, rsa_size, in, in_len);
      break;
    default:
      OPENSSL_PUT_ERROR(RSA, RSA_R_UNKNOWN_PADDING_TYPE);
      goto err;
  }

  if (i <= 0) {
    goto err;
  }

  if (BN_bin2bn(buf, rsa_size, f) == NULL) {
    goto err;
  }

  if (BN_ucmp(f, rsa->n) >= 0) {
    // usually the padding functions would catch this
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_TOO_LARGE);
    goto err;
  }

  if (!BN_MONT_CTX_set_locked(&rsa->mont_n, &rsa->lock, rsa->n, ctx) ||
      !BN_mod_exp_mont(result, f, rsa->e, rsa->n, ctx, rsa->mont_n)) {
    goto err;
  }

  // put in leading 0 bytes if the number is less than the length of the
  // modulus
  if (!BN_bn2bin_padded(out, rsa_size, result)) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  *out_len = rsa_size;
  ret = 1;

err:
  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }
  OPENSSL_free(buf);

  return ret;
}

// MAX_BLINDINGS_PER_RSA defines the maximum number of cached BN_BLINDINGs per
// RSA*. Then this limit is exceeded, BN_BLINDING objects will be created and
// destroyed as needed.
#define MAX_BLINDINGS_PER_RSA 1024

// rsa_blinding_get returns a BN_BLINDING to use with |rsa|. It does this by
// allocating one of the cached BN_BLINDING objects in |rsa->blindings|. If
// none are free, the cache will be extended by a extra element and the new
// BN_BLINDING is returned.
//
// On success, the index of the assigned BN_BLINDING is written to
// |*index_used| and must be passed to |rsa_blinding_release| when finished.
static BN_BLINDING *rsa_blinding_get(RSA *rsa, unsigned *index_used,
                                     BN_CTX *ctx) {
  assert(ctx != NULL);
  assert(rsa->mont_n != NULL);

  BN_BLINDING *ret = NULL;
  BN_BLINDING **new_blindings;
  uint8_t *new_blindings_inuse;
  char overflow = 0;

  CRYPTO_MUTEX_lock_write(&rsa->lock);

  unsigned i;
  for (i = 0; i < rsa->num_blindings; i++) {
    if (rsa->blindings_inuse[i] == 0) {
      rsa->blindings_inuse[i] = 1;
      ret = rsa->blindings[i];
      *index_used = i;
      break;
    }
  }

  if (ret != NULL) {
    CRYPTO_MUTEX_unlock_write(&rsa->lock);
    return ret;
  }

  overflow = rsa->num_blindings >= MAX_BLINDINGS_PER_RSA;

  // We didn't find a free BN_BLINDING to use so increase the length of
  // the arrays by one and use the newly created element.

  CRYPTO_MUTEX_unlock_write(&rsa->lock);
  ret = BN_BLINDING_new();
  if (ret == NULL) {
    return NULL;
  }

  if (overflow) {
    // We cannot add any more cached BN_BLINDINGs so we use |ret|
    // and mark it for destruction in |rsa_blinding_release|.
    *index_used = MAX_BLINDINGS_PER_RSA;
    return ret;
  }

  CRYPTO_MUTEX_lock_write(&rsa->lock);

  new_blindings =
      OPENSSL_malloc(sizeof(BN_BLINDING *) * (rsa->num_blindings + 1));
  if (new_blindings == NULL) {
    goto err1;
  }
  OPENSSL_memcpy(new_blindings, rsa->blindings,
         sizeof(BN_BLINDING *) * rsa->num_blindings);
  new_blindings[rsa->num_blindings] = ret;

  new_blindings_inuse = OPENSSL_malloc(rsa->num_blindings + 1);
  if (new_blindings_inuse == NULL) {
    goto err2;
  }
  OPENSSL_memcpy(new_blindings_inuse, rsa->blindings_inuse, rsa->num_blindings);
  new_blindings_inuse[rsa->num_blindings] = 1;
  *index_used = rsa->num_blindings;

  OPENSSL_free(rsa->blindings);
  rsa->blindings = new_blindings;
  OPENSSL_free(rsa->blindings_inuse);
  rsa->blindings_inuse = new_blindings_inuse;
  rsa->num_blindings++;

  CRYPTO_MUTEX_unlock_write(&rsa->lock);
  return ret;

err2:
  OPENSSL_free(new_blindings);

err1:
  CRYPTO_MUTEX_unlock_write(&rsa->lock);
  BN_BLINDING_free(ret);
  return NULL;
}

// rsa_blinding_release marks the cached BN_BLINDING at the given index as free
// for other threads to use.
static void rsa_blinding_release(RSA *rsa, BN_BLINDING *blinding,
                                 unsigned blinding_index) {
  if (blinding_index == MAX_BLINDINGS_PER_RSA) {
    // This blinding wasn't cached.
    BN_BLINDING_free(blinding);
    return;
  }

  CRYPTO_MUTEX_lock_write(&rsa->lock);
  rsa->blindings_inuse[blinding_index] = 0;
  CRYPTO_MUTEX_unlock_write(&rsa->lock);
}

// signing
int rsa_default_sign_raw(RSA *rsa, size_t *out_len, uint8_t *out,
                         size_t max_out, const uint8_t *in, size_t in_len,
                         int padding) {
  const unsigned rsa_size = RSA_size(rsa);
  uint8_t *buf = NULL;
  int i, ret = 0;

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  buf = OPENSSL_malloc(rsa_size);
  if (buf == NULL) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  switch (padding) {
    case RSA_PKCS1_PADDING:
      i = RSA_padding_add_PKCS1_type_1(buf, rsa_size, in, in_len);
      break;
    case RSA_NO_PADDING:
      i = RSA_padding_add_none(buf, rsa_size, in, in_len);
      break;
    default:
      OPENSSL_PUT_ERROR(RSA, RSA_R_UNKNOWN_PADDING_TYPE);
      goto err;
  }

  if (i <= 0) {
    goto err;
  }

  if (!RSA_private_transform(rsa, out, buf, rsa_size)) {
    goto err;
  }

  *out_len = rsa_size;
  ret = 1;

err:
  OPENSSL_free(buf);

  return ret;
}

int rsa_default_decrypt(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                        const uint8_t *in, size_t in_len, int padding) {
  const unsigned rsa_size = RSA_size(rsa);
  uint8_t *buf = NULL;
  int ret = 0;

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  if (padding == RSA_NO_PADDING) {
    buf = out;
  } else {
    // Allocate a temporary buffer to hold the padded plaintext.
    buf = OPENSSL_malloc(rsa_size);
    if (buf == NULL) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
      goto err;
    }
  }

  if (in_len != rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_LEN_NOT_EQUAL_TO_MOD_LEN);
    goto err;
  }

  if (!RSA_private_transform(rsa, buf, in, rsa_size)) {
    goto err;
  }

  switch (padding) {
    case RSA_PKCS1_PADDING:
      ret =
          RSA_padding_check_PKCS1_type_2(out, out_len, rsa_size, buf, rsa_size);
      break;
    case RSA_PKCS1_OAEP_PADDING:
      // Use the default parameters: SHA-1 for both hashes and no label.
      ret = RSA_padding_check_PKCS1_OAEP_mgf1(out, out_len, rsa_size, buf,
                                              rsa_size, NULL, 0, NULL, NULL);
      break;
    case RSA_NO_PADDING:
      *out_len = rsa_size;
      ret = 1;
      break;
    default:
      OPENSSL_PUT_ERROR(RSA, RSA_R_UNKNOWN_PADDING_TYPE);
      goto err;
  }

  if (!ret) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_PADDING_CHECK_FAILED);
  }

err:
  if (padding != RSA_NO_PADDING) {
    OPENSSL_free(buf);
  }

  return ret;
}

static int mod_exp(BIGNUM *r0, const BIGNUM *I, RSA *rsa, BN_CTX *ctx);

int RSA_verify_raw(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                   const uint8_t *in, size_t in_len, int padding) {
  if (rsa->n == NULL || rsa->e == NULL) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_VALUE_MISSING);
    return 0;
  }

  const unsigned rsa_size = RSA_size(rsa);
  BIGNUM *f, *result;

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  if (in_len != rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_LEN_NOT_EQUAL_TO_MOD_LEN);
    return 0;
  }

  if (!check_modulus_and_exponent_sizes(rsa)) {
    return 0;
  }

  BN_CTX *ctx = BN_CTX_new();
  if (ctx == NULL) {
    return 0;
  }

  int ret = 0;
  uint8_t *buf = NULL;

  BN_CTX_start(ctx);
  f = BN_CTX_get(ctx);
  result = BN_CTX_get(ctx);
  if (f == NULL || result == NULL) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (padding == RSA_NO_PADDING) {
    buf = out;
  } else {
    // Allocate a temporary buffer to hold the padded plaintext.
    buf = OPENSSL_malloc(rsa_size);
    if (buf == NULL) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
      goto err;
    }
  }

  if (BN_bin2bn(in, in_len, f) == NULL) {
    goto err;
  }

  if (BN_ucmp(f, rsa->n) >= 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_TOO_LARGE);
    goto err;
  }

  if (!BN_MONT_CTX_set_locked(&rsa->mont_n, &rsa->lock, rsa->n, ctx) ||
      !BN_mod_exp_mont(result, f, rsa->e, rsa->n, ctx, rsa->mont_n)) {
    goto err;
  }

  if (!BN_bn2bin_padded(buf, rsa_size, result)) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  switch (padding) {
    case RSA_PKCS1_PADDING:
      ret =
          RSA_padding_check_PKCS1_type_1(out, out_len, rsa_size, buf, rsa_size);
      break;
    case RSA_NO_PADDING:
      ret = 1;
      *out_len = rsa_size;
      break;
    default:
      OPENSSL_PUT_ERROR(RSA, RSA_R_UNKNOWN_PADDING_TYPE);
      goto err;
  }

  if (!ret) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_PADDING_CHECK_FAILED);
    goto err;
  }

err:
  BN_CTX_end(ctx);
  BN_CTX_free(ctx);
  if (buf != out) {
    OPENSSL_free(buf);
  }
  return ret;
}

int rsa_default_private_transform(RSA *rsa, uint8_t *out, const uint8_t *in,
                                  size_t len) {
  if (rsa->n == NULL || rsa->d == NULL) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_VALUE_MISSING);
    return 0;
  }

  BIGNUM *f, *result;
  BN_CTX *ctx = NULL;
  unsigned blinding_index = 0;
  BN_BLINDING *blinding = NULL;
  int ret = 0;

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }
  BN_CTX_start(ctx);
  f = BN_CTX_get(ctx);
  result = BN_CTX_get(ctx);

  if (f == NULL || result == NULL) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (BN_bin2bn(in, len, f) == NULL) {
    goto err;
  }

  if (BN_ucmp(f, rsa->n) >= 0) {
    // Usually the padding functions would catch this.
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_TOO_LARGE);
    goto err;
  }

  if (!BN_MONT_CTX_set_locked(&rsa->mont_n, &rsa->lock, rsa->n, ctx)) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  const int do_blinding = (rsa->flags & RSA_FLAG_NO_BLINDING) == 0;

  if (rsa->e == NULL && do_blinding) {
    // We cannot do blinding or verification without |e|, and continuing without
    // those countermeasures is dangerous. However, the Java/Android RSA API
    // requires support for keys where only |d| and |n| (and not |e|) are known.
    // The callers that require that bad behavior set |RSA_FLAG_NO_BLINDING|.
    OPENSSL_PUT_ERROR(RSA, RSA_R_NO_PUBLIC_EXPONENT);
    goto err;
  }

  if (do_blinding) {
    blinding = rsa_blinding_get(rsa, &blinding_index, ctx);
    if (blinding == NULL) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
      goto err;
    }
    if (!BN_BLINDING_convert(f, blinding, rsa->e, rsa->mont_n, ctx)) {
      goto err;
    }
  }

  if (rsa->p != NULL && rsa->q != NULL && rsa->e != NULL && rsa->dmp1 != NULL &&
      rsa->dmq1 != NULL && rsa->iqmp != NULL) {
    if (!mod_exp(result, f, rsa, ctx)) {
      goto err;
    }
  } else if (!BN_mod_exp_mont_consttime(result, f, rsa->d, rsa->n, ctx,
                                        rsa->mont_n)) {
    goto err;
  }

  // Verify the result to protect against fault attacks as described in the
  // 1997 paper "On the Importance of Checking Cryptographic Protocols for
  // Faults" by Dan Boneh, Richard A. DeMillo, and Richard J. Lipton. Some
  // implementations do this only when the CRT is used, but we do it in all
  // cases. Section 6 of the aforementioned paper describes an attack that
  // works when the CRT isn't used. That attack is much less likely to succeed
  // than the CRT attack, but there have likely been improvements since 1997.
  //
  // This check is cheap assuming |e| is small; it almost always is.
  if (rsa->e != NULL) {
    BIGNUM *vrfy = BN_CTX_get(ctx);
    if (vrfy == NULL ||
        !BN_mod_exp_mont(vrfy, result, rsa->e, rsa->n, ctx, rsa->mont_n) ||
        !BN_equal_consttime(vrfy, f)) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
      goto err;
    }

  }

  if (do_blinding &&
      !BN_BLINDING_invert(result, blinding, rsa->mont_n, ctx)) {
    goto err;
  }

  if (!BN_bn2bin_padded(out, len, result)) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  ret = 1;

err:
  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }
  if (blinding != NULL) {
    rsa_blinding_release(rsa, blinding, blinding_index);
  }

  return ret;
}

// mod_montgomery sets |r| to |I| mod |p|. |I| must already be fully reduced
// modulo |p| times |q|. It returns one on success and zero on error.
static int mod_montgomery(BIGNUM *r, const BIGNUM *I, const BIGNUM *p,
                          const BN_MONT_CTX *mont_p, const BIGNUM *q,
                          BN_CTX *ctx) {
  // Reduce in constant time with Montgomery reduction, which requires I <= p *
  // R. If p and q are the same size, which is true for any RSA keys we or
  // anyone sane generates, we have q < R and I < p * q, so this holds.
  //
  // If q is too big, fall back to |BN_mod|.
  if (q->top > p->top) {
    return BN_mod(r, I, p, ctx);
  }

  if (// Reduce mod p with Montgomery reduction. This computes I * R^-1 mod p.
      !BN_from_montgomery(r, I, mont_p, ctx) ||
      // Multiply by R^2 and do another Montgomery reduction to compute
      // I * R^-1 * R^2 * R^-1 = I mod p.
      !BN_to_montgomery(r, r, mont_p, ctx)) {
    return 0;
  }

  // By precomputing R^3 mod p (normally |BN_MONT_CTX| only uses R^2 mod p) and
  // adjusting the API for |BN_mod_exp_mont_consttime|, we could instead compute
  // I * R mod p here and save a reduction per prime. But this would require
  // changing the RSAZ code and may not be worth it.
  return 1;
}

static int mod_exp(BIGNUM *r0, const BIGNUM *I, RSA *rsa, BN_CTX *ctx) {
  assert(ctx != NULL);

  assert(rsa->n != NULL);
  assert(rsa->e != NULL);
  assert(rsa->d != NULL);
  assert(rsa->p != NULL);
  assert(rsa->q != NULL);
  assert(rsa->dmp1 != NULL);
  assert(rsa->dmq1 != NULL);
  assert(rsa->iqmp != NULL);

  BIGNUM *r1, *m1, *vrfy;
  int ret = 0;

  BN_CTX_start(ctx);
  r1 = BN_CTX_get(ctx);
  m1 = BN_CTX_get(ctx);
  vrfy = BN_CTX_get(ctx);
  if (r1 == NULL ||
      m1 == NULL ||
      vrfy == NULL) {
    goto err;
  }

  if (!BN_MONT_CTX_set_locked(&rsa->mont_p, &rsa->lock, rsa->p, ctx) ||
      !BN_MONT_CTX_set_locked(&rsa->mont_q, &rsa->lock, rsa->q, ctx)) {
    goto err;
  }

  if (!BN_MONT_CTX_set_locked(&rsa->mont_n, &rsa->lock, rsa->n, ctx)) {
    goto err;
  }

  // This is a pre-condition for |mod_montgomery|. It was already checked by the
  // caller.
  assert(BN_ucmp(I, rsa->n) < 0);

  // compute I mod q
  if (!mod_montgomery(r1, I, rsa->q, rsa->mont_q, rsa->p, ctx)) {
    goto err;
  }

  // compute r1^dmq1 mod q
  if (!BN_mod_exp_mont_consttime(m1, r1, rsa->dmq1, rsa->q, ctx, rsa->mont_q)) {
    goto err;
  }

  // compute I mod p
  if (!mod_montgomery(r1, I, rsa->p, rsa->mont_p, rsa->q, ctx)) {
    goto err;
  }

  // compute r1^dmp1 mod p
  if (!BN_mod_exp_mont_consttime(r0, r1, rsa->dmp1, rsa->p, ctx, rsa->mont_p)) {
    goto err;
  }

  // TODO(davidben): The code below is not constant-time, even ignoring
  // |bn_correct_top|. To fix this:
  //
  // 1. Canonicalize keys on p > q. (p > q for keys we generate, but not ones we
  //    import.) We have exposed structs, but we can generalize the
  //    |BN_MONT_CTX_set_locked| trick to do a one-time canonicalization of the
  //    private key where we optionally swap p and q (re-computing iqmp if
  //    necessary) and fill in mont_*. This removes the p < q case below.
  //
  // 2. Compute r0 - m1 (mod p) in constant-time. With (1) done, this is just a
  //    constant-time modular subtraction. It should be doable with
  //    |bn_sub_words| and a select on the borrow bit.
  //
  // 3. When computing mont_*, additionally compute iqmp_mont, iqmp in
  //    Montgomery form. The |BN_mul| and |BN_mod| pair can then be replaced
  //    with |BN_mod_mul_montgomery|.

  if (!BN_sub(r0, r0, m1)) {
    goto err;
  }
  // This will help stop the size of r0 increasing, which does
  // affect the multiply if it optimised for a power of 2 size
  if (BN_is_negative(r0)) {
    if (!BN_add(r0, r0, rsa->p)) {
      goto err;
    }
  }

  if (!BN_mul(r1, r0, rsa->iqmp, ctx)) {
    goto err;
  }

  if (!BN_mod(r0, r1, rsa->p, ctx)) {
    goto err;
  }

  // If p < q it is occasionally possible for the correction of
  // adding 'p' if r0 is negative above to leave the result still
  // negative. This can break the private key operations: the following
  // second correction should *always* correct this rare occurrence.
  // This will *never* happen with OpenSSL generated keys because
  // they ensure p > q [steve]
  if (BN_is_negative(r0)) {
    if (!BN_add(r0, r0, rsa->p)) {
      goto err;
    }
  }
  if (!BN_mul(r1, r0, rsa->q, ctx)) {
    goto err;
  }
  if (!BN_add(r0, r1, m1)) {
    goto err;
  }

  ret = 1;

err:
  BN_CTX_end(ctx);
  return ret;
}

static int ensure_bignum(BIGNUM **out) {
  if (*out == NULL) {
    *out = BN_new();
  }
  return *out != NULL;
}

// kBoringSSLRSASqrtTwo is the BIGNUM representation of ⌊2¹⁵³⁵×√2⌋. This is
// chosen to give enough precision for 3072-bit RSA, the largest key size FIPS
// specifies. Key sizes beyond this will round up.
//
// To verify this number, check that n² < 2³⁰⁷¹ < (n+1)², where n is value
// represented here. Note the components are listed in little-endian order. Here
// is some sample Python code to check:
//
//   >>> TOBN = lambda a, b: a << 32 | b
//   >>> l = [ <paste the contents of kSqrtTwo> ]
//   >>> n = sum(a * 2**(64*i) for i, a in enumerate(l))
//   >>> n**2 < 2**3071 < (n+1)**2
//   True
const BN_ULONG kBoringSSLRSASqrtTwo[] = {
    TOBN(0xdea06241, 0xf7aa81c2), TOBN(0xf6a1be3f, 0xca221307),
    TOBN(0x332a5e9f, 0x7bda1ebf), TOBN(0x0104dc01, 0xfe32352f),
    TOBN(0xb8cf341b, 0x6f8236c7), TOBN(0x4264dabc, 0xd528b651),
    TOBN(0xf4d3a02c, 0xebc93e0c), TOBN(0x81394ab6, 0xd8fd0efd),
    TOBN(0xeaa4a089, 0x9040ca4a), TOBN(0xf52f120f, 0x836e582e),
    TOBN(0xcb2a6343, 0x31f3c84d), TOBN(0xc6d5a8a3, 0x8bb7e9dc),
    TOBN(0x460abc72, 0x2f7c4e33), TOBN(0xcab1bc91, 0x1688458a),
    TOBN(0x53059c60, 0x11bc337b), TOBN(0xd2202e87, 0x42af1f4e),
    TOBN(0x78048736, 0x3dfa2768), TOBN(0x0f74a85e, 0x439c7b4a),
    TOBN(0xa8b1fe6f, 0xdc83db39), TOBN(0x4afc8304, 0x3ab8a2c3),
    TOBN(0xed17ac85, 0x83339915), TOBN(0x1d6f60ba, 0x893ba84c),
    TOBN(0x597d89b3, 0x754abe9f), TOBN(0xb504f333, 0xf9de6484),
};
const size_t kBoringSSLRSASqrtTwoLen = OPENSSL_ARRAY_SIZE(kBoringSSLRSASqrtTwo);

int rsa_greater_than_pow2(const BIGNUM *b, int n) {
  if (BN_is_negative(b) || n == INT_MAX) {
    return 0;
  }

  int b_bits = BN_num_bits(b);
  return b_bits > n + 1 || (b_bits == n + 1 && !BN_is_pow2(b));
}

// generate_prime sets |out| to a prime with length |bits| such that |out|-1 is
// relatively prime to |e|. If |p| is non-NULL, |out| will also not be close to
// |p|.
static int generate_prime(BIGNUM *out, int bits, const BIGNUM *e,
                          const BIGNUM *p, BN_CTX *ctx, BN_GENCB *cb) {
  if (bits < 128 || (bits % BN_BITS2) != 0) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    return 0;
  }

  // See FIPS 186-4 appendix B.3.3, steps 4 and 5. Note |bits| here is nlen/2.

  // Use the limit from steps 4.7 and 5.8 for most values of |e|. When |e| is 3,
  // the 186-4 limit is too low, so we use a higher one. Note this case is not
  // reachable from |RSA_generate_key_fips|.
  if (bits >= INT_MAX/32) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_MODULUS_TOO_LARGE);
    return 0;
  }
  int limit = BN_is_word(e, 3) ? bits * 32 : bits * 5;

  int ret = 0, tries = 0, rand_tries = 0;
  BN_CTX_start(ctx);
  BIGNUM *tmp = BN_CTX_get(ctx);
  if (tmp == NULL) {
    goto err;
  }

  for (;;) {
    // Generate a random number of length |bits| where the bottom bit is set
    // (steps 4.2, 4.3, 5.2 and 5.3) and the top bit is set (implied by the
    // bound checked below in steps 4.4 and 5.5).
    if (!BN_rand(out, bits, BN_RAND_TOP_ONE, BN_RAND_BOTTOM_ODD) ||
        !BN_GENCB_call(cb, BN_GENCB_GENERATED, rand_tries++)) {
      goto err;
    }

    if (p != NULL) {
      // If |p| and |out| are too close, try again (step 5.4).
      if (!BN_sub(tmp, out, p)) {
        goto err;
      }
      BN_set_negative(tmp, 0);
      if (!rsa_greater_than_pow2(tmp, bits - 100)) {
        continue;
      }
    }

    // If out < 2^(bits-1)×√2, try again (steps 4.4 and 5.5).
    //
    // We check the most significant words, so we retry if ⌊out/2^k⌋ <= ⌊b/2^k⌋,
    // where b = 2^(bits-1)×√2 and k = max(0, bits - 1536). For key sizes up to
    // 3072 (bits = 1536), k = 0, so we are testing that ⌊out⌋ <= ⌊b⌋. out is an
    // integer and b is not, so this is equivalent to out < b. That is, the
    // comparison is exact for FIPS key sizes.
    //
    // For larger keys, the comparison is approximate, leaning towards
    // retrying. That is, we reject a negligible fraction of primes that are
    // within the FIPS bound, but we will never accept a prime outside the
    // bound, ensuring the resulting RSA key is the right size. Specifically, if
    // the FIPS bound holds, we have ⌊out/2^k⌋ < out/2^k < b/2^k. This implies
    // ⌊out/2^k⌋ <= ⌊b/2^k⌋. That is, the FIPS bound implies our bound and so we
    // are slightly tighter.
    size_t out_len = (size_t)out->top;
    assert(out_len == (size_t)bits / BN_BITS2);
    size_t to_check = kBoringSSLRSASqrtTwoLen;
    if (to_check > out_len) {
      to_check = out_len;
    }
    if (!bn_less_than_words(
            kBoringSSLRSASqrtTwo + kBoringSSLRSASqrtTwoLen - to_check,
            out->d + out_len - to_check, to_check)) {
      continue;
    }

    // Check gcd(out-1, e) is one (steps 4.5 and 5.6).
    if (!BN_sub(tmp, out, BN_value_one()) ||
        !BN_gcd(tmp, tmp, e, ctx)) {
      goto err;
    }
    if (BN_is_one(tmp)) {
      // Test |out| for primality (steps 4.5.1 and 5.6.1).
      int is_probable_prime;
      if (!BN_primality_test(&is_probable_prime, out, BN_prime_checks, ctx, 1,
                             cb)) {
        goto err;
      }
      if (is_probable_prime) {
        ret = 1;
        goto err;
      }
    }

    // If we've tried too many times to find a prime, abort (steps 4.7 and
    // 5.8).
    tries++;
    if (tries >= limit) {
      OPENSSL_PUT_ERROR(RSA, RSA_R_TOO_MANY_ITERATIONS);
      goto err;
    }
    if (!BN_GENCB_call(cb, 2, tries)) {
      goto err;
    }
  }

err:
  BN_CTX_end(ctx);
  return ret;
}

int RSA_generate_key_ex(RSA *rsa, int bits, BIGNUM *e_value, BN_GENCB *cb) {
  // See FIPS 186-4 appendix B.3. This function implements a generalized version
  // of the FIPS algorithm. |RSA_generate_key_fips| performs additional checks
  // for FIPS-compliant key generation.

  // Always generate RSA keys which are a multiple of 128 bits. Round |bits|
  // down as needed.
  bits &= ~127;

  // Reject excessively small keys.
  if (bits < 256) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_KEY_SIZE_TOO_SMALL);
    return 0;
  }

  int ret = 0;
  BN_CTX *ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto bn_err;
  }
  BN_CTX_start(ctx);
  BIGNUM *totient = BN_CTX_get(ctx);
  BIGNUM *pm1 = BN_CTX_get(ctx);
  BIGNUM *qm1 = BN_CTX_get(ctx);
  BIGNUM *gcd = BN_CTX_get(ctx);
  if (totient == NULL || pm1 == NULL || qm1 == NULL || gcd == NULL) {
    goto bn_err;
  }

  // We need the RSA components non-NULL.
  if (!ensure_bignum(&rsa->n) ||
      !ensure_bignum(&rsa->d) ||
      !ensure_bignum(&rsa->e) ||
      !ensure_bignum(&rsa->p) ||
      !ensure_bignum(&rsa->q) ||
      !ensure_bignum(&rsa->dmp1) ||
      !ensure_bignum(&rsa->dmq1) ||
      !ensure_bignum(&rsa->iqmp)) {
    goto bn_err;
  }

  if (!BN_copy(rsa->e, e_value)) {
    goto bn_err;
  }

  int prime_bits = bits / 2;
  do {
    // Generate p and q, each of size |prime_bits|, using the steps outlined in
    // appendix FIPS 186-4 appendix B.3.3.
    if (!generate_prime(rsa->p, prime_bits, rsa->e, NULL, ctx, cb) ||
        !BN_GENCB_call(cb, 3, 0) ||
        !generate_prime(rsa->q, prime_bits, rsa->e, rsa->p, ctx, cb) ||
        !BN_GENCB_call(cb, 3, 1)) {
      goto bn_err;
    }

    if (BN_cmp(rsa->p, rsa->q) < 0) {
      BIGNUM *tmp = rsa->p;
      rsa->p = rsa->q;
      rsa->q = tmp;
    }

    // Calculate d = e^(-1) (mod lcm(p-1, q-1)), per FIPS 186-4. This differs
    // from typical RSA implementations which use (p-1)*(q-1).
    //
    // Note this means the size of d might reveal information about p-1 and
    // q-1. However, we do operations with Chinese Remainder Theorem, so we only
    // use d (mod p-1) and d (mod q-1) as exponents. Using a minimal totient
    // does not affect those two values.
    if (!BN_sub(pm1, rsa->p, BN_value_one()) ||
        !BN_sub(qm1, rsa->q, BN_value_one()) ||
        !BN_mul(totient, pm1, qm1, ctx) ||
        !BN_gcd(gcd, pm1, qm1, ctx) ||
        !BN_div(totient, NULL, totient, gcd, ctx) ||
        !BN_mod_inverse(rsa->d, rsa->e, totient, ctx)) {
      goto bn_err;
    }

    // Check that |rsa->d| > 2^|prime_bits| and try again if it fails. See
    // appendix B.3.1's guidance on values for d.
  } while (!rsa_greater_than_pow2(rsa->d, prime_bits));

  if (// Calculate n.
      !BN_mul(rsa->n, rsa->p, rsa->q, ctx) ||
      // Calculate d mod (p-1).
      !BN_mod(rsa->dmp1, rsa->d, pm1, ctx) ||
      // Calculate d mod (q-1)
      !BN_mod(rsa->dmq1, rsa->d, qm1, ctx)) {
    goto bn_err;
  }

  // Sanity-check that |rsa->n| has the specified size. This is implied by
  // |generate_prime|'s bounds.
  if (BN_num_bits(rsa->n) != (unsigned)bits) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  // Calculate inverse of q mod p. Note that although RSA key generation is far
  // from constant-time, |bn_mod_inverse_secret_prime| uses the same modular
  // exponentation logic as in RSA private key operations and, if the RSAZ-1024
  // code is enabled, will be optimized for common RSA prime sizes.
  if (!BN_MONT_CTX_set_locked(&rsa->mont_p, &rsa->lock, rsa->p, ctx) ||
      !bn_mod_inverse_secret_prime(rsa->iqmp, rsa->q, rsa->p, ctx,
                                   rsa->mont_p)) {
    goto bn_err;
  }

  // The key generation process is complex and thus error-prone. It could be
  // disastrous to generate and then use a bad key so double-check that the key
  // makes sense.
  if (!RSA_check_key(rsa)) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_INTERNAL_ERROR);
    goto err;
  }

  ret = 1;

bn_err:
  if (!ret) {
    OPENSSL_PUT_ERROR(RSA, ERR_LIB_BN);
  }
err:
  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }
  return ret;
}

int RSA_generate_key_fips(RSA *rsa, int bits, BN_GENCB *cb) {
  // FIPS 186-4 allows 2048-bit and 3072-bit RSA keys (1024-bit and 1536-bit
  // primes, respectively) with the prime generation method we use.
  if (bits != 2048 && bits != 3072) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_RSA_PARAMETERS);
    return 0;
  }

  BIGNUM *e = BN_new();
  int ret = e != NULL &&
            BN_set_word(e, RSA_F4) &&
            RSA_generate_key_ex(rsa, bits, e, cb) &&
            RSA_check_fips(rsa);
  BN_free(e);
  return ret;
}

DEFINE_METHOD_FUNCTION(RSA_METHOD, RSA_default_method) {
  // All of the methods are NULL to make it easier for the compiler/linker to
  // drop unused functions. The wrapper functions will select the appropriate
  // |rsa_default_*| implementation.
  OPENSSL_memset(out, 0, sizeof(RSA_METHOD));
  out->common.is_static = 1;
}
