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

#include <string.h>

#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/thread.h>

#include "internal.h"
#include "../internal.h"


#define OPENSSL_RSA_MAX_MODULUS_BITS 16384
#define OPENSSL_RSA_SMALL_MODULUS_BITS 3072
#define OPENSSL_RSA_MAX_PUBEXP_BITS \
  64 /* exponent limit enforced for "large" modulus only */


static int finish(RSA *rsa) {
  BN_MONT_CTX_free(rsa->_method_mod_n);
  BN_MONT_CTX_free(rsa->_method_mod_p);
  BN_MONT_CTX_free(rsa->_method_mod_q);

  if (rsa->additional_primes != NULL) {
    size_t i;
    for (i = 0; i < sk_RSA_additional_prime_num(rsa->additional_primes); i++) {
      RSA_additional_prime *ap =
          sk_RSA_additional_prime_value(rsa->additional_primes, i);
      BN_MONT_CTX_free(ap->method_mod);
    }
  }

  return 1;
}

static size_t size(const RSA *rsa) {
  return BN_num_bytes(rsa->n);
}

static int encrypt(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                   const uint8_t *in, size_t in_len, int padding) {
  const unsigned rsa_size = RSA_size(rsa);
  BIGNUM *f, *result;
  uint8_t *buf = NULL;
  BN_CTX *ctx = NULL;
  int i, ret = 0;

  if (rsa_size > OPENSSL_RSA_MAX_MODULUS_BITS) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_MODULUS_TOO_LARGE);
    return 0;
  }

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  if (BN_ucmp(rsa->n, rsa->e) <= 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_E_VALUE);
    return 0;
  }

  /* for large moduli, enforce exponent limit */
  if (BN_num_bits(rsa->n) > OPENSSL_RSA_SMALL_MODULUS_BITS &&
      BN_num_bits(rsa->e) > OPENSSL_RSA_MAX_PUBEXP_BITS) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_E_VALUE);
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
      /* Use the default parameters: SHA-1 for both hashes and no label. */
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
    /* usually the padding functions would catch this */
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_TOO_LARGE_FOR_MODULUS);
    goto err;
  }

  if (rsa->flags & RSA_FLAG_CACHE_PUBLIC) {
    if (BN_MONT_CTX_set_locked(&rsa->_method_mod_n, &rsa->lock, rsa->n, ctx) ==
        NULL) {
      goto err;
    }
  }

  if (!rsa->meth->bn_mod_exp(result, f, rsa->e, rsa->n, ctx,
                             rsa->_method_mod_n)) {
    goto err;
  }

  /* put in leading 0 bytes if the number is less than the length of the
   * modulus */
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
  if (buf != NULL) {
    OPENSSL_cleanse(buf, rsa_size);
    OPENSSL_free(buf);
  }

  return ret;
}

/* MAX_BLINDINGS_PER_RSA defines the maximum number of cached BN_BLINDINGs per
 * RSA*. Then this limit is exceeded, BN_BLINDING objects will be created and
 * destroyed as needed. */
#define MAX_BLINDINGS_PER_RSA 1024

/* rsa_blinding_get returns a BN_BLINDING to use with |rsa|. It does this by
 * allocating one of the cached BN_BLINDING objects in |rsa->blindings|. If
 * none are free, the cache will be extended by a extra element and the new
 * BN_BLINDING is returned.
 *
 * On success, the index of the assigned BN_BLINDING is written to
 * |*index_used| and must be passed to |rsa_blinding_release| when finished. */
static BN_BLINDING *rsa_blinding_get(RSA *rsa, unsigned *index_used,
                                     BN_CTX *ctx) {
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
    CRYPTO_MUTEX_unlock(&rsa->lock);
    return ret;
  }

  overflow = rsa->num_blindings >= MAX_BLINDINGS_PER_RSA;

  /* We didn't find a free BN_BLINDING to use so increase the length of
   * the arrays by one and use the newly created element. */

  CRYPTO_MUTEX_unlock(&rsa->lock);
  ret = rsa_setup_blinding(rsa, ctx);
  if (ret == NULL) {
    return NULL;
  }

  if (overflow) {
    /* We cannot add any more cached BN_BLINDINGs so we use |ret|
     * and mark it for destruction in |rsa_blinding_release|. */
    *index_used = MAX_BLINDINGS_PER_RSA;
    return ret;
  }

  CRYPTO_MUTEX_lock_write(&rsa->lock);

  new_blindings =
      OPENSSL_malloc(sizeof(BN_BLINDING *) * (rsa->num_blindings + 1));
  if (new_blindings == NULL) {
    goto err1;
  }
  memcpy(new_blindings, rsa->blindings,
         sizeof(BN_BLINDING *) * rsa->num_blindings);
  new_blindings[rsa->num_blindings] = ret;

  new_blindings_inuse = OPENSSL_malloc(rsa->num_blindings + 1);
  if (new_blindings_inuse == NULL) {
    goto err2;
  }
  memcpy(new_blindings_inuse, rsa->blindings_inuse, rsa->num_blindings);
  new_blindings_inuse[rsa->num_blindings] = 1;
  *index_used = rsa->num_blindings;

  OPENSSL_free(rsa->blindings);
  rsa->blindings = new_blindings;
  OPENSSL_free(rsa->blindings_inuse);
  rsa->blindings_inuse = new_blindings_inuse;
  rsa->num_blindings++;

  CRYPTO_MUTEX_unlock(&rsa->lock);
  return ret;

err2:
  OPENSSL_free(new_blindings);

err1:
  CRYPTO_MUTEX_unlock(&rsa->lock);
  BN_BLINDING_free(ret);
  return NULL;
}

/* rsa_blinding_release marks the cached BN_BLINDING at the given index as free
 * for other threads to use. */
static void rsa_blinding_release(RSA *rsa, BN_BLINDING *blinding,
                                 unsigned blinding_index) {
  if (blinding_index == MAX_BLINDINGS_PER_RSA) {
    /* This blinding wasn't cached. */
    BN_BLINDING_free(blinding);
    return;
  }

  CRYPTO_MUTEX_lock_write(&rsa->lock);
  rsa->blindings_inuse[blinding_index] = 0;
  CRYPTO_MUTEX_unlock(&rsa->lock);
}

/* signing */
static int sign_raw(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                    const uint8_t *in, size_t in_len, int padding) {
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
  if (buf != NULL) {
    OPENSSL_cleanse(buf, rsa_size);
    OPENSSL_free(buf);
  }

  return ret;
}

static int decrypt(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                   const uint8_t *in, size_t in_len, int padding) {
  const unsigned rsa_size = RSA_size(rsa);
  int r = -1;
  uint8_t *buf = NULL;
  int ret = 0;

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  if (padding == RSA_NO_PADDING) {
    buf = out;
  } else {
    /* Allocate a temporary buffer to hold the padded plaintext. */
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
      r = RSA_padding_check_PKCS1_type_2(out, rsa_size, buf, rsa_size);
      break;
    case RSA_PKCS1_OAEP_PADDING:
      /* Use the default parameters: SHA-1 for both hashes and no label. */
      r = RSA_padding_check_PKCS1_OAEP_mgf1(out, rsa_size, buf, rsa_size,
                                            NULL, 0, NULL, NULL);
      break;
    case RSA_NO_PADDING:
      r = rsa_size;
      break;
    default:
      OPENSSL_PUT_ERROR(RSA, RSA_R_UNKNOWN_PADDING_TYPE);
      goto err;
  }

  if (r < 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_PADDING_CHECK_FAILED);
  } else {
    *out_len = r;
    ret = 1;
  }

err:
  if (padding != RSA_NO_PADDING && buf != NULL) {
    OPENSSL_cleanse(buf, rsa_size);
    OPENSSL_free(buf);
  }

  return ret;
}

static int verify_raw(RSA *rsa, size_t *out_len, uint8_t *out, size_t max_out,
                      const uint8_t *in, size_t in_len, int padding) {
  const unsigned rsa_size = RSA_size(rsa);
  BIGNUM *f, *result;
  int ret = 0;
  int r = -1;
  uint8_t *buf = NULL;
  BN_CTX *ctx = NULL;

  if (BN_num_bits(rsa->n) > OPENSSL_RSA_MAX_MODULUS_BITS) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_MODULUS_TOO_LARGE);
    return 0;
  }

  if (BN_ucmp(rsa->n, rsa->e) <= 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_E_VALUE);
    return 0;
  }

  if (max_out < rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_OUTPUT_BUFFER_TOO_SMALL);
    return 0;
  }

  /* for large moduli, enforce exponent limit */
  if (BN_num_bits(rsa->n) > OPENSSL_RSA_SMALL_MODULUS_BITS &&
      BN_num_bits(rsa->e) > OPENSSL_RSA_MAX_PUBEXP_BITS) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_E_VALUE);
    return 0;
  }

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }

  BN_CTX_start(ctx);
  f = BN_CTX_get(ctx);
  result = BN_CTX_get(ctx);
  if (padding == RSA_NO_PADDING) {
    buf = out;
  } else {
    /* Allocate a temporary buffer to hold the padded plaintext. */
    buf = OPENSSL_malloc(rsa_size);
    if (buf == NULL) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
      goto err;
    }
  }
  if (!f || !result) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (in_len != rsa_size) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_LEN_NOT_EQUAL_TO_MOD_LEN);
    goto err;
  }

  if (BN_bin2bn(in, in_len, f) == NULL) {
    goto err;
  }

  if (BN_ucmp(f, rsa->n) >= 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_TOO_LARGE_FOR_MODULUS);
    goto err;
  }

  if (rsa->flags & RSA_FLAG_CACHE_PUBLIC) {
    if (BN_MONT_CTX_set_locked(&rsa->_method_mod_n, &rsa->lock, rsa->n, ctx) ==
        NULL) {
      goto err;
    }
  }

  if (!rsa->meth->bn_mod_exp(result, f, rsa->e, rsa->n, ctx,
                             rsa->_method_mod_n)) {
    goto err;
  }

  if (!BN_bn2bin_padded(buf, rsa_size, result)) {
    OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  switch (padding) {
    case RSA_PKCS1_PADDING:
      r = RSA_padding_check_PKCS1_type_1(out, rsa_size, buf, rsa_size);
      break;
    case RSA_NO_PADDING:
      r = rsa_size;
      break;
    default:
      OPENSSL_PUT_ERROR(RSA, RSA_R_UNKNOWN_PADDING_TYPE);
      goto err;
  }

  if (r < 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_PADDING_CHECK_FAILED);
  } else {
    *out_len = r;
    ret = 1;
  }

err:
  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }
  if (padding != RSA_NO_PADDING && buf != NULL) {
    OPENSSL_cleanse(buf, rsa_size);
    OPENSSL_free(buf);
  }
  return ret;
}

static int private_transform(RSA *rsa, uint8_t *out, const uint8_t *in,
                             size_t len) {
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
    /* Usually the padding functions would catch this. */
    OPENSSL_PUT_ERROR(RSA, RSA_R_DATA_TOO_LARGE_FOR_MODULUS);
    goto err;
  }

  if (!(rsa->flags & RSA_FLAG_NO_BLINDING)) {
    blinding = rsa_blinding_get(rsa, &blinding_index, ctx);
    if (blinding == NULL) {
      OPENSSL_PUT_ERROR(RSA, ERR_R_INTERNAL_ERROR);
      goto err;
    }
    if (!BN_BLINDING_convert_ex(f, NULL, blinding, ctx)) {
      goto err;
    }
  }

  if ((rsa->flags & RSA_FLAG_EXT_PKEY) ||
      ((rsa->p != NULL) && (rsa->q != NULL) && (rsa->dmp1 != NULL) &&
       (rsa->dmq1 != NULL) && (rsa->iqmp != NULL))) {
    if (!rsa->meth->mod_exp(result, f, rsa, ctx)) {
      goto err;
    }
  } else {
    BIGNUM local_d;
    BIGNUM *d = NULL;

    BN_init(&local_d);
    d = &local_d;
    BN_with_flags(d, rsa->d, BN_FLG_CONSTTIME);

    if (rsa->flags & RSA_FLAG_CACHE_PUBLIC) {
      if (BN_MONT_CTX_set_locked(&rsa->_method_mod_n, &rsa->lock, rsa->n,
                                 ctx) == NULL) {
        goto err;
      }
    }

    if (!rsa->meth->bn_mod_exp(result, f, d, rsa->n, ctx, rsa->_method_mod_n)) {
      goto err;
    }
  }

  if (blinding) {
    if (!BN_BLINDING_invert_ex(result, NULL, blinding, ctx)) {
      goto err;
    }
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

static int mod_exp(BIGNUM *r0, const BIGNUM *I, RSA *rsa, BN_CTX *ctx) {
  BIGNUM *r1, *m1, *vrfy;
  BIGNUM local_dmp1, local_dmq1, local_c, local_r1;
  BIGNUM *dmp1, *dmq1, *c, *pr1;
  int ret = 0;
  size_t i, num_additional_primes = 0;

  if (rsa->additional_primes != NULL) {
    num_additional_primes = sk_RSA_additional_prime_num(rsa->additional_primes);
  }

  BN_CTX_start(ctx);
  r1 = BN_CTX_get(ctx);
  m1 = BN_CTX_get(ctx);
  vrfy = BN_CTX_get(ctx);

  {
    BIGNUM local_p, local_q;
    BIGNUM *p = NULL, *q = NULL;

    /* Make sure BN_mod_inverse in Montgomery intialization uses the
     * BN_FLG_CONSTTIME flag (unless RSA_FLAG_NO_CONSTTIME is set) */
    BN_init(&local_p);
    p = &local_p;
    BN_with_flags(p, rsa->p, BN_FLG_CONSTTIME);

    BN_init(&local_q);
    q = &local_q;
    BN_with_flags(q, rsa->q, BN_FLG_CONSTTIME);

    if (rsa->flags & RSA_FLAG_CACHE_PRIVATE) {
      if (BN_MONT_CTX_set_locked(&rsa->_method_mod_p, &rsa->lock, p, ctx) ==
          NULL) {
        goto err;
      }
      if (BN_MONT_CTX_set_locked(&rsa->_method_mod_q, &rsa->lock, q, ctx) ==
          NULL) {
        goto err;
      }
    }
  }

  if (rsa->flags & RSA_FLAG_CACHE_PUBLIC) {
    if (BN_MONT_CTX_set_locked(&rsa->_method_mod_n, &rsa->lock, rsa->n, ctx) ==
        NULL) {
      goto err;
    }
  }

  /* compute I mod q */
  c = &local_c;
  BN_with_flags(c, I, BN_FLG_CONSTTIME);
  if (!BN_mod(r1, c, rsa->q, ctx)) {
    goto err;
  }

  /* compute r1^dmq1 mod q */
  dmq1 = &local_dmq1;
  BN_with_flags(dmq1, rsa->dmq1, BN_FLG_CONSTTIME);
  if (!rsa->meth->bn_mod_exp(m1, r1, dmq1, rsa->q, ctx, rsa->_method_mod_q)) {
    goto err;
  }

  /* compute I mod p */
  c = &local_c;
  BN_with_flags(c, I, BN_FLG_CONSTTIME);
  if (!BN_mod(r1, c, rsa->p, ctx)) {
    goto err;
  }

  /* compute r1^dmp1 mod p */
  dmp1 = &local_dmp1;
  BN_with_flags(dmp1, rsa->dmp1, BN_FLG_CONSTTIME);
  if (!rsa->meth->bn_mod_exp(r0, r1, dmp1, rsa->p, ctx, rsa->_method_mod_p)) {
    goto err;
  }

  if (!BN_sub(r0, r0, m1)) {
    goto err;
  }
  /* This will help stop the size of r0 increasing, which does
   * affect the multiply if it optimised for a power of 2 size */
  if (BN_is_negative(r0)) {
    if (!BN_add(r0, r0, rsa->p)) {
      goto err;
    }
  }

  if (!BN_mul(r1, r0, rsa->iqmp, ctx)) {
    goto err;
  }

  /* Turn BN_FLG_CONSTTIME flag on before division operation */
  pr1 = &local_r1;
  BN_with_flags(pr1, r1, BN_FLG_CONSTTIME);

  if (!BN_mod(r0, pr1, rsa->p, ctx)) {
    goto err;
  }

  /* If p < q it is occasionally possible for the correction of
   * adding 'p' if r0 is negative above to leave the result still
   * negative. This can break the private key operations: the following
   * second correction should *always* correct this rare occurrence.
   * This will *never* happen with OpenSSL generated keys because
   * they ensure p > q [steve] */
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

  for (i = 0; i < num_additional_primes; i++) {
    /* multi-prime RSA. */
    BIGNUM local_exp, local_prime;
    BIGNUM *exp = &local_exp, *prime = &local_prime;
    RSA_additional_prime *ap =
        sk_RSA_additional_prime_value(rsa->additional_primes, i);

    BN_with_flags(exp, ap->exp, BN_FLG_CONSTTIME);
    BN_with_flags(prime, ap->prime, BN_FLG_CONSTTIME);

    /* c will already point to a BIGNUM with the correct flags. */
    if (!BN_mod(r1, c, prime, ctx)) {
      goto err;
    }

    if ((rsa->flags & RSA_FLAG_CACHE_PRIVATE) &&
        !BN_MONT_CTX_set_locked(&ap->method_mod, &rsa->lock, prime, ctx)) {
      goto err;
    }

    if (!rsa->meth->bn_mod_exp(m1, r1, exp, prime, ctx, ap->method_mod)) {
      goto err;
    }

    BN_set_flags(m1, BN_FLG_CONSTTIME);

    if (!BN_sub(m1, m1, r0) ||
        !BN_mul(m1, m1, ap->coeff, ctx) ||
        !BN_mod(m1, m1, prime, ctx) ||
        (BN_is_negative(m1) && !BN_add(m1, m1, prime)) ||
        !BN_mul(m1, m1, ap->r, ctx) ||
        !BN_add(r0, r0, m1)) {
      goto err;
    }
  }

  if (rsa->e && rsa->n) {
    if (!rsa->meth->bn_mod_exp(vrfy, r0, rsa->e, rsa->n, ctx,
                               rsa->_method_mod_n)) {
      goto err;
    }
    /* If 'I' was greater than (or equal to) rsa->n, the operation
     * will be equivalent to using 'I mod n'. However, the result of
     * the verify will *always* be less than 'n' so we don't check
     * for absolute equality, just congruency. */
    if (!BN_sub(vrfy, vrfy, I)) {
      goto err;
    }
    if (!BN_mod(vrfy, vrfy, rsa->n, ctx)) {
      goto err;
    }
    if (BN_is_negative(vrfy)) {
      if (!BN_add(vrfy, vrfy, rsa->n)) {
        goto err;
      }
    }
    if (!BN_is_zero(vrfy)) {
      /* 'I' and 'vrfy' aren't congruent mod n. Don't leak
       * miscalculated CRT output, just do a raw (slower)
       * mod_exp and return that instead. */

      BIGNUM local_d;
      BIGNUM *d = NULL;

      d = &local_d;
      BN_with_flags(d, rsa->d, BN_FLG_CONSTTIME);
      if (!rsa->meth->bn_mod_exp(r0, I, d, rsa->n, ctx, rsa->_method_mod_n)) {
        goto err;
      }
    }
  }
  ret = 1;

err:
  BN_CTX_end(ctx);
  return ret;
}

static int keygen_multiprime(RSA *rsa, int bits, int num_primes,
                             BIGNUM *e_value, BN_GENCB *cb) {
  BIGNUM *r0 = NULL, *r1 = NULL, *r2 = NULL, *r3 = NULL, *tmp;
  BIGNUM local_r0, local_d, local_p;
  BIGNUM *pr0, *d, *p;
  int prime_bits, ok = -1, n = 0, i, j;
  BN_CTX *ctx = NULL;
  STACK_OF(RSA_additional_prime) *additional_primes = NULL;

  if (num_primes < 2) {
    ok = 0; /* we set our own err */
    OPENSSL_PUT_ERROR(RSA, RSA_R_MUST_HAVE_AT_LEAST_TWO_PRIMES);
    goto err;
  }

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }
  BN_CTX_start(ctx);
  r0 = BN_CTX_get(ctx);
  r1 = BN_CTX_get(ctx);
  r2 = BN_CTX_get(ctx);
  r3 = BN_CTX_get(ctx);
  if (r0 == NULL || r1 == NULL || r2 == NULL || r3 == NULL) {
    goto err;
  }

  if (num_primes > 2) {
    additional_primes = sk_RSA_additional_prime_new_null();
    if (additional_primes == NULL) {
      goto err;
    }
  }

  for (i = 2; i < num_primes; i++) {
    RSA_additional_prime *ap = OPENSSL_malloc(sizeof(RSA_additional_prime));
    if (ap == NULL) {
      goto err;
    }
    memset(ap, 0, sizeof(RSA_additional_prime));
    ap->prime = BN_new();
    ap->exp = BN_new();
    ap->coeff = BN_new();
    ap->r = BN_new();
    if (ap->prime == NULL ||
        ap->exp == NULL ||
        ap->coeff == NULL ||
        ap->r == NULL ||
        !sk_RSA_additional_prime_push(additional_primes, ap)) {
      RSA_additional_prime_free(ap);
      goto err;
    }
  }

  /* We need the RSA components non-NULL */
  if (!rsa->n && ((rsa->n = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->d && ((rsa->d = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->e && ((rsa->e = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->p && ((rsa->p = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->q && ((rsa->q = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->dmp1 && ((rsa->dmp1 = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->dmq1 && ((rsa->dmq1 = BN_new()) == NULL)) {
    goto err;
  }
  if (!rsa->iqmp && ((rsa->iqmp = BN_new()) == NULL)) {
    goto err;
  }

  if (!BN_copy(rsa->e, e_value)) {
    goto err;
  }

  /* generate p and q */
  prime_bits = (bits + (num_primes - 1)) / num_primes;
  for (;;) {
    if (!BN_generate_prime_ex(rsa->p, prime_bits, 0, NULL, NULL, cb) ||
        !BN_sub(r2, rsa->p, BN_value_one()) ||
        !BN_gcd(r1, r2, rsa->e, ctx)) {
      goto err;
    }
    if (BN_is_one(r1)) {
      break;
    }
    if (!BN_GENCB_call(cb, 2, n++)) {
      goto err;
    }
  }
  if (!BN_GENCB_call(cb, 3, 0)) {
    goto err;
  }
  prime_bits = ((bits - prime_bits) + (num_primes - 2)) / (num_primes - 1);
  for (;;) {
    /* When generating ridiculously small keys, we can get stuck
     * continually regenerating the same prime values. Check for
     * this and bail if it happens 3 times. */
    unsigned int degenerate = 0;
    do {
      if (!BN_generate_prime_ex(rsa->q, prime_bits, 0, NULL, NULL, cb)) {
        goto err;
      }
    } while ((BN_cmp(rsa->p, rsa->q) == 0) && (++degenerate < 3));
    if (degenerate == 3) {
      ok = 0; /* we set our own err */
      OPENSSL_PUT_ERROR(RSA, RSA_R_KEY_SIZE_TOO_SMALL);
      goto err;
    }
    if (!BN_sub(r2, rsa->q, BN_value_one()) ||
        !BN_gcd(r1, r2, rsa->e, ctx)) {
      goto err;
    }
    if (BN_is_one(r1)) {
      break;
    }
    if (!BN_GENCB_call(cb, 2, n++)) {
      goto err;
    }
  }

  if (!BN_GENCB_call(cb, 3, 1) ||
      !BN_mul(rsa->n, rsa->p, rsa->q, ctx)) {
    goto err;
  }

  for (i = 2; i < num_primes; i++) {
    RSA_additional_prime *ap =
        sk_RSA_additional_prime_value(additional_primes, i - 2);
    prime_bits = ((bits - BN_num_bits(rsa->n)) + (num_primes - (i + 1))) /
                 (num_primes - i);

    for (;;) {
      if (!BN_generate_prime_ex(ap->prime, prime_bits, 0, NULL, NULL, cb)) {
        goto err;
      }
      if (BN_cmp(rsa->p, ap->prime) == 0 ||
          BN_cmp(rsa->q, ap->prime) == 0) {
        continue;
      }

      for (j = 0; j < i - 2; j++) {
        if (BN_cmp(sk_RSA_additional_prime_value(additional_primes, j)->prime,
                   ap->prime) == 0) {
          break;
        }
      }
      if (j != i - 2) {
        continue;
      }

      if (!BN_sub(r2, ap->prime, BN_value_one()) ||
          !BN_gcd(r1, r2, rsa->e, ctx)) {
        goto err;
      }

      if (!BN_is_one(r1)) {
        continue;
      }
      if (i != num_primes - 1) {
        break;
      }

      /* For the last prime we'll check that it makes n large enough. In the
       * two prime case this isn't a problem because we generate primes with
       * the top two bits set and so the product is always of the expected
       * size. In the multi prime case, this doesn't follow. */
      if (!BN_mul(r1, rsa->n, ap->prime, ctx)) {
        goto err;
      }
      if (BN_num_bits(r1) == bits) {
        break;
      }

      if (!BN_GENCB_call(cb, 2, n++)) {
        goto err;
      }
    }

    /* ap->r is is the product of all the primes prior to the current one
     * (including p and q). */
    if (!BN_copy(ap->r, rsa->n)) {
      goto err;
    }
    if (i == num_primes - 1) {
      /* In the case of the last prime, we calculated n as |r1| in the loop
       * above. */
      if (!BN_copy(rsa->n, r1)) {
        goto err;
      }
    } else if (!BN_mul(rsa->n, rsa->n, ap->prime, ctx)) {
      goto err;
    }

    if (!BN_GENCB_call(cb, 3, 1)) {
      goto err;
    }
  }

  if (BN_cmp(rsa->p, rsa->q) < 0) {
    tmp = rsa->p;
    rsa->p = rsa->q;
    rsa->q = tmp;
  }

  /* calculate d */
  if (!BN_sub(r1, rsa->p, BN_value_one())) {
    goto err; /* p-1 */
  }
  if (!BN_sub(r2, rsa->q, BN_value_one())) {
    goto err; /* q-1 */
  }
  if (!BN_mul(r0, r1, r2, ctx)) {
    goto err; /* (p-1)(q-1) */
  }
  for (i = 2; i < num_primes; i++) {
    RSA_additional_prime *ap =
        sk_RSA_additional_prime_value(additional_primes, i - 2);
    if (!BN_sub(r3, ap->prime, BN_value_one()) ||
        !BN_mul(r0, r0, r3, ctx)) {
      goto err;
    }
  }
  pr0 = &local_r0;
  BN_with_flags(pr0, r0, BN_FLG_CONSTTIME);
  if (!BN_mod_inverse(rsa->d, rsa->e, pr0, ctx)) {
    goto err; /* d */
  }

  /* set up d for correct BN_FLG_CONSTTIME flag */
  d = &local_d;
  BN_with_flags(d, rsa->d, BN_FLG_CONSTTIME);

  /* calculate d mod (p-1) */
  if (!BN_mod(rsa->dmp1, d, r1, ctx)) {
    goto err;
  }

  /* calculate d mod (q-1) */
  if (!BN_mod(rsa->dmq1, d, r2, ctx)) {
    goto err;
  }

  /* calculate inverse of q mod p */
  p = &local_p;
  BN_with_flags(p, rsa->p, BN_FLG_CONSTTIME);

  if (!BN_mod_inverse(rsa->iqmp, rsa->q, p, ctx)) {
    goto err;
  }

  for (i = 2; i < num_primes; i++) {
    RSA_additional_prime *ap =
        sk_RSA_additional_prime_value(additional_primes, i - 2);
    if (!BN_sub(ap->exp, ap->prime, BN_value_one()) ||
        !BN_mod(ap->exp, rsa->d, ap->exp, ctx) ||
        !BN_mod_inverse(ap->coeff, ap->r, ap->prime, ctx)) {
      goto err;
    }
  }

  ok = 1;
  rsa->additional_primes = additional_primes;
  additional_primes = NULL;

err:
  if (ok == -1) {
    OPENSSL_PUT_ERROR(RSA, ERR_LIB_BN);
    ok = 0;
  }
  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }
  sk_RSA_additional_prime_pop_free(additional_primes,
                                   RSA_additional_prime_free);
  return ok;
}

static int keygen(RSA *rsa, int bits, BIGNUM *e_value, BN_GENCB *cb) {
  return keygen_multiprime(rsa, bits, 2 /* num primes */, e_value, cb);
}

const struct rsa_meth_st RSA_default_method = {
  {
    0 /* references */,
    1 /* is_static */,
  },
  NULL /* app_data */,

  NULL /* init */,
  finish,

  size,

  NULL /* sign */,
  NULL /* verify */,

  encrypt,
  sign_raw,
  decrypt,
  verify_raw,

  private_transform,

  mod_exp /* mod_exp */,
  BN_mod_exp_mont /* bn_mod_exp */,

  RSA_FLAG_CACHE_PUBLIC | RSA_FLAG_CACHE_PRIVATE,

  keygen,
  keygen_multiprime,
};
