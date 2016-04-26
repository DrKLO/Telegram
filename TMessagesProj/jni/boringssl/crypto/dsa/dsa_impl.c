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
 *
 * The DSS routines are based on patches supplied by
 * Steven Schoch <schoch@sheba.arc.nasa.gov>. */

#include <openssl/dsa.h>

#include <string.h>

#include <openssl/bn.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/thread.h>

#include "internal.h"

#define OPENSSL_DSA_MAX_MODULUS_BITS 10000

/* Primality test according to FIPS PUB 186[-1], Appendix 2.1: 50 rounds of
 * Rabin-Miller */
#define DSS_prime_checks 50

static int sign_setup(const DSA *dsa, BN_CTX *ctx_in, BIGNUM **kinvp,
                      BIGNUM **rp, const uint8_t *digest, size_t digest_len) {
  BN_CTX *ctx;
  BIGNUM k, kq, *K, *kinv = NULL, *r = NULL;
  int ret = 0;

  if (!dsa->p || !dsa->q || !dsa->g) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_MISSING_PARAMETERS);
    return 0;
  }

  BN_init(&k);
  BN_init(&kq);

  ctx = ctx_in;
  if (ctx == NULL) {
    ctx = BN_CTX_new();
    if (ctx == NULL) {
      goto err;
    }
  }

  r = BN_new();
  if (r == NULL) {
    goto err;
  }

  /* Get random k */
  do {
    /* If possible, we'll include the private key and message digest in the k
     * generation. The |digest| argument is only empty if |DSA_sign_setup| is
     * being used. */
    int ok;

    if (digest_len > 0) {
      ok = BN_generate_dsa_nonce(&k, dsa->q, dsa->priv_key, digest, digest_len,
                                 ctx);
    } else {
      ok = BN_rand_range(&k, dsa->q);
    }
    if (!ok) {
      goto err;
    }
  } while (BN_is_zero(&k));

  BN_set_flags(&k, BN_FLG_CONSTTIME);

  if (BN_MONT_CTX_set_locked((BN_MONT_CTX **)&dsa->method_mont_p,
                             (CRYPTO_MUTEX *)&dsa->method_mont_p_lock, dsa->p,
                             ctx) == NULL) {
    goto err;
  }

  /* Compute r = (g^k mod p) mod q */
  if (!BN_copy(&kq, &k)) {
    goto err;
  }

  /* We do not want timing information to leak the length of k,
   * so we compute g^k using an equivalent exponent of fixed length.
   *
   * (This is a kludge that we need because the BN_mod_exp_mont()
   * does not let us specify the desired timing behaviour.) */

  if (!BN_add(&kq, &kq, dsa->q)) {
    goto err;
  }
  if (BN_num_bits(&kq) <= BN_num_bits(dsa->q) && !BN_add(&kq, &kq, dsa->q)) {
    goto err;
  }

  K = &kq;

  if (!BN_mod_exp_mont(r, dsa->g, K, dsa->p, ctx, dsa->method_mont_p)) {
    goto err;
  }
  if (!BN_mod(r, r, dsa->q, ctx)) {
    goto err;
  }

  /* Compute  part of 's = inv(k) (m + xr) mod q' */
  kinv = BN_mod_inverse(NULL, &k, dsa->q, ctx);
  if (kinv == NULL) {
    goto err;
  }

  BN_clear_free(*kinvp);
  *kinvp = kinv;
  kinv = NULL;
  BN_clear_free(*rp);
  *rp = r;
  ret = 1;

err:
  if (!ret) {
    OPENSSL_PUT_ERROR(DSA, ERR_R_BN_LIB);
    if (r != NULL) {
      BN_clear_free(r);
    }
  }

  if (ctx_in == NULL) {
    BN_CTX_free(ctx);
  }
  BN_clear_free(&k);
  BN_clear_free(&kq);
  return ret;
}

static DSA_SIG *sign(const uint8_t *digest, size_t digest_len, DSA *dsa) {
  BIGNUM *kinv = NULL, *r = NULL, *s = NULL;
  BIGNUM m;
  BIGNUM xr;
  BN_CTX *ctx = NULL;
  int reason = ERR_R_BN_LIB;
  DSA_SIG *ret = NULL;
  int noredo = 0;

  BN_init(&m);
  BN_init(&xr);

  if (!dsa->p || !dsa->q || !dsa->g) {
    reason = DSA_R_MISSING_PARAMETERS;
    goto err;
  }

  s = BN_new();
  if (s == NULL) {
    goto err;
  }
  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }

redo:
  if (dsa->kinv == NULL || dsa->r == NULL) {
    if (!DSA_sign_setup(dsa, ctx, &kinv, &r)) {
      goto err;
    }
  } else {
    kinv = dsa->kinv;
    dsa->kinv = NULL;
    r = dsa->r;
    dsa->r = NULL;
    noredo = 1;
  }

  if (digest_len > BN_num_bytes(dsa->q)) {
    /* if the digest length is greater than the size of q use the
     * BN_num_bits(dsa->q) leftmost bits of the digest, see
     * fips 186-3, 4.2 */
    digest_len = BN_num_bytes(dsa->q);
  }

  if (BN_bin2bn(digest, digest_len, &m) == NULL) {
    goto err;
  }

  /* Compute  s = inv(k) (m + xr) mod q */
  if (!BN_mod_mul(&xr, dsa->priv_key, r, dsa->q, ctx)) {
    goto err; /* s = xr */
  }
  if (!BN_add(s, &xr, &m)) {
    goto err; /* s = m + xr */
  }
  if (BN_cmp(s, dsa->q) > 0) {
    if (!BN_sub(s, s, dsa->q)) {
      goto err;
    }
  }
  if (!BN_mod_mul(s, s, kinv, dsa->q, ctx)) {
    goto err;
  }

  ret = DSA_SIG_new();
  if (ret == NULL) {
    goto err;
  }
  /* Redo if r or s is zero as required by FIPS 186-3: this is
   * very unlikely. */
  if (BN_is_zero(r) || BN_is_zero(s)) {
    if (noredo) {
      reason = DSA_R_NEED_NEW_SETUP_VALUES;
      goto err;
    }
    goto redo;
  }
  ret->r = r;
  ret->s = s;

err:
  if (!ret) {
    OPENSSL_PUT_ERROR(DSA, reason);
    BN_free(r);
    BN_free(s);
  }
  BN_CTX_free(ctx);
  BN_clear_free(&m);
  BN_clear_free(&xr);
  BN_clear_free(kinv);

  return ret;
}

static int verify(int *out_valid, const uint8_t *dgst, size_t digest_len,
                  DSA_SIG *sig, const DSA *dsa) {
  BN_CTX *ctx;
  BIGNUM u1, u2, t1;
  BN_MONT_CTX *mont = NULL;
  int ret = 0;
  unsigned i;

  *out_valid = 0;

  if (!dsa->p || !dsa->q || !dsa->g) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_MISSING_PARAMETERS);
    return 0;
  }

  i = BN_num_bits(dsa->q);
  /* fips 186-3 allows only different sizes for q */
  if (i != 160 && i != 224 && i != 256) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_BAD_Q_VALUE);
    return 0;
  }

  if (BN_num_bits(dsa->p) > OPENSSL_DSA_MAX_MODULUS_BITS) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_MODULUS_TOO_LARGE);
    return 0;
  }

  BN_init(&u1);
  BN_init(&u2);
  BN_init(&t1);

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }

  if (BN_is_zero(sig->r) || BN_is_negative(sig->r) ||
      BN_ucmp(sig->r, dsa->q) >= 0) {
    ret = 1;
    goto err;
  }
  if (BN_is_zero(sig->s) || BN_is_negative(sig->s) ||
      BN_ucmp(sig->s, dsa->q) >= 0) {
    ret = 1;
    goto err;
  }

  /* Calculate W = inv(S) mod Q
   * save W in u2 */
  if (BN_mod_inverse(&u2, sig->s, dsa->q, ctx) == NULL) {
    goto err;
  }

  /* save M in u1 */
  if (digest_len > (i >> 3)) {
    /* if the digest length is greater than the size of q use the
     * BN_num_bits(dsa->q) leftmost bits of the digest, see
     * fips 186-3, 4.2 */
    digest_len = (i >> 3);
  }

  if (BN_bin2bn(dgst, digest_len, &u1) == NULL) {
    goto err;
  }

  /* u1 = M * w mod q */
  if (!BN_mod_mul(&u1, &u1, &u2, dsa->q, ctx)) {
    goto err;
  }

  /* u2 = r * w mod q */
  if (!BN_mod_mul(&u2, sig->r, &u2, dsa->q, ctx)) {
    goto err;
  }

  mont = BN_MONT_CTX_set_locked((BN_MONT_CTX **)&dsa->method_mont_p,
                                (CRYPTO_MUTEX *)&dsa->method_mont_p_lock,
                                dsa->p, ctx);
  if (!mont) {
    goto err;
  }

  if (!BN_mod_exp2_mont(&t1, dsa->g, &u1, dsa->pub_key, &u2, dsa->p, ctx,
                        mont)) {
    goto err;
  }

  /* BN_copy(&u1,&t1); */
  /* let u1 = u1 mod q */
  if (!BN_mod(&u1, &t1, dsa->q, ctx)) {
    goto err;
  }

  /* V is now in u1.  If the signature is correct, it will be
   * equal to R. */
  *out_valid = BN_ucmp(&u1, sig->r) == 0;
  ret = 1;

err:
  if (ret != 1) {
    OPENSSL_PUT_ERROR(DSA, ERR_R_BN_LIB);
  }
  BN_CTX_free(ctx);
  BN_free(&u1);
  BN_free(&u2);
  BN_free(&t1);

  return ret;
}

static int keygen(DSA *dsa) {
  int ok = 0;
  BN_CTX *ctx = NULL;
  BIGNUM *pub_key = NULL, *priv_key = NULL;
  BIGNUM prk;

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }

  priv_key = dsa->priv_key;
  if (priv_key == NULL) {
    priv_key = BN_new();
    if (priv_key == NULL) {
      goto err;
    }
  }

  do {
    if (!BN_rand_range(priv_key, dsa->q)) {
      goto err;
    }
  } while (BN_is_zero(priv_key));

  pub_key = dsa->pub_key;
  if (pub_key == NULL) {
    pub_key = BN_new();
    if (pub_key == NULL) {
      goto err;
    }
  }

  BN_init(&prk);
  BN_with_flags(&prk, priv_key, BN_FLG_CONSTTIME);

  if (!BN_mod_exp(pub_key, dsa->g, &prk, dsa->p, ctx)) {
    goto err;
  }

  dsa->priv_key = priv_key;
  dsa->pub_key = pub_key;
  ok = 1;

err:
  if (dsa->pub_key == NULL) {
    BN_free(pub_key);
  }
  if (dsa->priv_key == NULL) {
    BN_free(priv_key);
  }
  BN_CTX_free(ctx);

  return ok;
}

static int paramgen(DSA *ret, unsigned bits, const uint8_t *seed_in,
                    size_t seed_len, int *counter_ret, unsigned long *h_ret,
                    BN_GENCB *cb) {
  int ok = 0;
  unsigned char seed[SHA256_DIGEST_LENGTH];
  unsigned char md[SHA256_DIGEST_LENGTH];
  unsigned char buf[SHA256_DIGEST_LENGTH], buf2[SHA256_DIGEST_LENGTH];
  BIGNUM *r0, *W, *X, *c, *test;
  BIGNUM *g = NULL, *q = NULL, *p = NULL;
  BN_MONT_CTX *mont = NULL;
  int k, n = 0, m = 0;
  unsigned i;
  int counter = 0;
  int r = 0;
  BN_CTX *ctx = NULL;
  unsigned int h = 2;
  unsigned qbits, qsize;
  const EVP_MD *evpmd;

  if (bits >= 2048) {
    qbits = 256;
    evpmd = EVP_sha256();
  } else {
    qbits = 160;
    evpmd = EVP_sha1();
  }
  qsize = qbits / 8;

  if (qsize != SHA_DIGEST_LENGTH && qsize != SHA224_DIGEST_LENGTH &&
      qsize != SHA256_DIGEST_LENGTH) {
    /* invalid q size */
    return 0;
  }

  if (bits < 512) {
    bits = 512;
  }

  bits = (bits + 63) / 64 * 64;

  /* NB: seed_len == 0 is special case: copy generated seed to
   * seed_in if it is not NULL. */
  if (seed_len && (seed_len < (size_t)qsize)) {
    seed_in = NULL; /* seed buffer too small -- ignore */
  }
  if (seed_len > (size_t)qsize) {
    seed_len = qsize; /* App. 2.2 of FIPS PUB 186 allows larger SEED,
                       * but our internal buffers are restricted to 160 bits*/
  }
  if (seed_in != NULL) {
    memcpy(seed, seed_in, seed_len);
  }

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    goto err;
  }
  BN_CTX_start(ctx);

  mont = BN_MONT_CTX_new();
  if (mont == NULL) {
    goto err;
  }

  r0 = BN_CTX_get(ctx);
  g = BN_CTX_get(ctx);
  W = BN_CTX_get(ctx);
  q = BN_CTX_get(ctx);
  X = BN_CTX_get(ctx);
  c = BN_CTX_get(ctx);
  p = BN_CTX_get(ctx);
  test = BN_CTX_get(ctx);

  if (test == NULL || !BN_lshift(test, BN_value_one(), bits - 1)) {
    goto err;
  }

  for (;;) {
    /* Find q. */
    for (;;) {
      int seed_is_random;

      /* step 1 */
      if (!BN_GENCB_call(cb, 0, m++)) {
        goto err;
      }

      if (!seed_len) {
        if (!RAND_bytes(seed, qsize)) {
          goto err;
        }
        seed_is_random = 1;
      } else {
        seed_is_random = 0;
        seed_len = 0; /* use random seed if 'seed_in' turns out to be bad*/
      }
      memcpy(buf, seed, qsize);
      memcpy(buf2, seed, qsize);
      /* precompute "SEED + 1" for step 7: */
      for (i = qsize - 1; i < qsize; i--) {
        buf[i]++;
        if (buf[i] != 0) {
          break;
        }
      }

      /* step 2 */
      if (!EVP_Digest(seed, qsize, md, NULL, evpmd, NULL) ||
          !EVP_Digest(buf, qsize, buf2, NULL, evpmd, NULL)) {
        goto err;
      }
      for (i = 0; i < qsize; i++) {
        md[i] ^= buf2[i];
      }

      /* step 3 */
      md[0] |= 0x80;
      md[qsize - 1] |= 0x01;
      if (!BN_bin2bn(md, qsize, q)) {
        goto err;
      }

      /* step 4 */
      r = BN_is_prime_fasttest_ex(q, DSS_prime_checks, ctx, seed_is_random, cb);
      if (r > 0) {
        break;
      }
      if (r != 0) {
        goto err;
      }

      /* do a callback call */
      /* step 5 */
    }

    if (!BN_GENCB_call(cb, 2, 0) || !BN_GENCB_call(cb, 3, 0)) {
      goto err;
    }

    /* step 6 */
    counter = 0;
    /* "offset = 2" */

    n = (bits - 1) / 160;

    for (;;) {
      if ((counter != 0) && !BN_GENCB_call(cb, 0, counter)) {
        goto err;
      }

      /* step 7 */
      BN_zero(W);
      /* now 'buf' contains "SEED + offset - 1" */
      for (k = 0; k <= n; k++) {
        /* obtain "SEED + offset + k" by incrementing: */
        for (i = qsize - 1; i < qsize; i--) {
          buf[i]++;
          if (buf[i] != 0) {
            break;
          }
        }

        if (!EVP_Digest(buf, qsize, md, NULL, evpmd, NULL)) {
          goto err;
        }

        /* step 8 */
        if (!BN_bin2bn(md, qsize, r0) ||
            !BN_lshift(r0, r0, (qsize << 3) * k) ||
            !BN_add(W, W, r0)) {
          goto err;
        }
      }

      /* more of step 8 */
      if (!BN_mask_bits(W, bits - 1) ||
          !BN_copy(X, W) ||
          !BN_add(X, X, test)) {
        goto err;
      }

      /* step 9 */
      if (!BN_lshift1(r0, q) ||
          !BN_mod(c, X, r0, ctx) ||
          !BN_sub(r0, c, BN_value_one()) ||
          !BN_sub(p, X, r0)) {
        goto err;
      }

      /* step 10 */
      if (BN_cmp(p, test) >= 0) {
        /* step 11 */
        r = BN_is_prime_fasttest_ex(p, DSS_prime_checks, ctx, 1, cb);
        if (r > 0) {
          goto end; /* found it */
        }
        if (r != 0) {
          goto err;
        }
      }

      /* step 13 */
      counter++;
      /* "offset = offset + n + 1" */

      /* step 14 */
      if (counter >= 4096) {
        break;
      }
    }
  }
end:
  if (!BN_GENCB_call(cb, 2, 1)) {
    goto err;
  }

  /* We now need to generate g */
  /* Set r0=(p-1)/q */
  if (!BN_sub(test, p, BN_value_one()) ||
      !BN_div(r0, NULL, test, q, ctx)) {
    goto err;
  }

  if (!BN_set_word(test, h) ||
      !BN_MONT_CTX_set(mont, p, ctx)) {
    goto err;
  }

  for (;;) {
    /* g=test^r0%p */
    if (!BN_mod_exp_mont(g, test, r0, p, ctx, mont)) {
      goto err;
    }
    if (!BN_is_one(g)) {
      break;
    }
    if (!BN_add(test, test, BN_value_one())) {
      goto err;
    }
    h++;
  }

  if (!BN_GENCB_call(cb, 3, 1)) {
    goto err;
  }

  ok = 1;

err:
  if (ok) {
    BN_free(ret->p);
    BN_free(ret->q);
    BN_free(ret->g);
    ret->p = BN_dup(p);
    ret->q = BN_dup(q);
    ret->g = BN_dup(g);
    if (ret->p == NULL || ret->q == NULL || ret->g == NULL) {
      ok = 0;
      goto err;
    }
    if (counter_ret != NULL) {
      *counter_ret = counter;
    }
    if (h_ret != NULL) {
      *h_ret = h;
    }
  }

  if (ctx) {
    BN_CTX_end(ctx);
    BN_CTX_free(ctx);
  }

  BN_MONT_CTX_free(mont);

  return ok;
}

static int finish(DSA *dsa) {
  BN_MONT_CTX_free(dsa->method_mont_p);
  dsa->method_mont_p = NULL;
  return 1;
}

const struct dsa_method DSA_default_method = {
  {
    0 /* references */,
    1 /* is_static */,
  },
  NULL /* app_data */,

  NULL /* init */,
  finish /* finish */,

  sign,
  sign_setup,
  verify,

  paramgen,
  keygen,
};
