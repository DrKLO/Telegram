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

#include <openssl/dh.h>

#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/thread.h>

#include "internal.h"


#define OPENSSL_DH_MAX_MODULUS_BITS 10000

static int generate_parameters(DH *ret, int prime_bits, int generator, BN_GENCB *cb) {
  /* We generate DH parameters as follows
   * find a prime q which is prime_bits/2 bits long.
   * p=(2*q)+1 or (p-1)/2 = q
   * For this case, g is a generator if
   * g^((p-1)/q) mod p != 1 for values of q which are the factors of p-1.
   * Since the factors of p-1 are q and 2, we just need to check
   * g^2 mod p != 1 and g^q mod p != 1.
   *
   * Having said all that,
   * there is another special case method for the generators 2, 3 and 5.
   * for 2, p mod 24 == 11
   * for 3, p mod 12 == 5  <<<<< does not work for safe primes.
   * for 5, p mod 10 == 3 or 7
   *
   * Thanks to Phil Karn <karn@qualcomm.com> for the pointers about the
   * special generators and for answering some of my questions.
   *
   * I've implemented the second simple method :-).
   * Since DH should be using a safe prime (both p and q are prime),
   * this generator function can take a very very long time to run.
   */

  /* Actually there is no reason to insist that 'generator' be a generator.
   * It's just as OK (and in some sense better) to use a generator of the
   * order-q subgroup.
   */

  BIGNUM *t1, *t2;
  int g, ok = 0;
  BN_CTX *ctx = NULL;

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }
  BN_CTX_start(ctx);
  t1 = BN_CTX_get(ctx);
  t2 = BN_CTX_get(ctx);
  if (t1 == NULL || t2 == NULL) {
    goto err;
  }

  /* Make sure 'ret' has the necessary elements */
  if (!ret->p && ((ret->p = BN_new()) == NULL)) {
    goto err;
  }
  if (!ret->g && ((ret->g = BN_new()) == NULL)) {
    goto err;
  }

  if (generator <= 1) {
    OPENSSL_PUT_ERROR(DH, DH_R_BAD_GENERATOR);
    goto err;
  }
  if (generator == DH_GENERATOR_2) {
    if (!BN_set_word(t1, 24)) {
      goto err;
    }
    if (!BN_set_word(t2, 11)) {
      goto err;
    }
    g = 2;
  } else if (generator == DH_GENERATOR_5) {
    if (!BN_set_word(t1, 10)) {
      goto err;
    }
    if (!BN_set_word(t2, 3)) {
      goto err;
    }
    /* BN_set_word(t3,7); just have to miss
     * out on these ones :-( */
    g = 5;
  } else {
    /* in the general case, don't worry if 'generator' is a
     * generator or not: since we are using safe primes,
     * it will generate either an order-q or an order-2q group,
     * which both is OK */
    if (!BN_set_word(t1, 2)) {
      goto err;
    }
    if (!BN_set_word(t2, 1)) {
      goto err;
    }
    g = generator;
  }

  if (!BN_generate_prime_ex(ret->p, prime_bits, 1, t1, t2, cb)) {
    goto err;
  }
  if (!BN_GENCB_call(cb, 3, 0)) {
    goto err;
  }
  if (!BN_set_word(ret->g, g)) {
    goto err;
  }
  ok = 1;

err:
  if (!ok) {
    OPENSSL_PUT_ERROR(DH, ERR_R_BN_LIB);
  }

  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }
  return ok;
}

static int generate_key(DH *dh) {
  int ok = 0;
  int generate_new_key = 0;
  unsigned l;
  BN_CTX *ctx;
  BN_MONT_CTX *mont = NULL;
  BIGNUM *pub_key = NULL, *priv_key = NULL;
  BIGNUM local_priv;

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }

  if (dh->priv_key == NULL) {
    priv_key = BN_new();
    if (priv_key == NULL) {
      goto err;
    }
    generate_new_key = 1;
  } else {
    priv_key = dh->priv_key;
  }

  if (dh->pub_key == NULL) {
    pub_key = BN_new();
    if (pub_key == NULL) {
      goto err;
    }
  } else {
    pub_key = dh->pub_key;
  }

  mont = BN_MONT_CTX_set_locked(&dh->method_mont_p, &dh->method_mont_p_lock,
                                dh->p, ctx);
  if (!mont) {
    goto err;
  }

  if (generate_new_key) {
    if (dh->q) {
      do {
        if (!BN_rand_range(priv_key, dh->q)) {
          goto err;
        }
      } while (BN_is_zero(priv_key) || BN_is_one(priv_key));
    } else {
      /* secret exponent length */
      DH_check_standard_parameters(dh);
      l = dh->priv_length ? dh->priv_length : BN_num_bits(dh->p) - 1;
      if (!BN_rand(priv_key, l, 0, 0)) {
        goto err;
      }
    }
  }

  BN_with_flags(&local_priv, priv_key, BN_FLG_CONSTTIME);
  if (!BN_mod_exp_mont(pub_key, dh->g, &local_priv, dh->p, ctx, mont)) {
    goto err;
  }

  dh->pub_key = pub_key;
  dh->priv_key = priv_key;
  ok = 1;

err:
  if (ok != 1) {
    OPENSSL_PUT_ERROR(DH, ERR_R_BN_LIB);
  }

  if (dh->pub_key == NULL) {
    BN_free(pub_key);
  }
  if (dh->priv_key == NULL) {
    BN_free(priv_key);
  }
  BN_CTX_free(ctx);
  return ok;
}

static int compute_key(DH *dh, unsigned char *out, const BIGNUM *pub_key) {
  BN_CTX *ctx = NULL;
  BN_MONT_CTX *mont = NULL;
  BIGNUM *shared_key;
  int ret = -1;
  int check_result;
  BIGNUM local_priv;

  if (BN_num_bits(dh->p) > OPENSSL_DH_MAX_MODULUS_BITS) {
    OPENSSL_PUT_ERROR(DH, DH_R_MODULUS_TOO_LARGE);
    goto err;
  }

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }
  BN_CTX_start(ctx);
  shared_key = BN_CTX_get(ctx);
  if (shared_key == NULL) {
    goto err;
  }

  if (dh->priv_key == NULL) {
    OPENSSL_PUT_ERROR(DH, DH_R_NO_PRIVATE_VALUE);
    goto err;
  }

  mont = BN_MONT_CTX_set_locked(&dh->method_mont_p, &dh->method_mont_p_lock,
                                dh->p, ctx);
  if (!mont) {
    goto err;
  }

  if (!DH_check_pub_key(dh, pub_key, &check_result) || check_result) {
    OPENSSL_PUT_ERROR(DH, DH_R_INVALID_PUBKEY);
    goto err;
  }

  BN_with_flags(&local_priv, dh->priv_key, BN_FLG_CONSTTIME);
  if (!BN_mod_exp_mont(shared_key, pub_key, &local_priv, dh->p, ctx,
                       mont)) {
    OPENSSL_PUT_ERROR(DH, ERR_R_BN_LIB);
    goto err;
  }

  ret = BN_bn2bin(shared_key, out);

err:
  if (ctx != NULL) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }

  return ret;
}

const struct dh_method DH_default_method = {
  {
    0 /* references */,
    1 /* is_static */,
  },
  NULL /* app_data */,
  NULL /* init */,
  NULL /* finish */,
  generate_parameters,
  generate_key,
  compute_key,
};
