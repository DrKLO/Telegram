/* ====================================================================
 * Copyright (c) 1998-2005 The OpenSSL Project.  All rights reserved.
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
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
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

#include <openssl/ecdsa.h>

#include <assert.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "../ec/internal.h"


int ECDSA_sign(int type, const uint8_t *digest, size_t digest_len, uint8_t *sig,
               unsigned int *sig_len, EC_KEY *eckey) {
  if (eckey->ecdsa_meth && eckey->ecdsa_meth->sign) {
    return eckey->ecdsa_meth->sign(digest, digest_len, sig, sig_len, eckey);
  }

  return ECDSA_sign_ex(type, digest, digest_len, sig, sig_len, NULL, NULL,
                       eckey);
}

int ECDSA_verify(int type, const uint8_t *digest, size_t digest_len,
                 const uint8_t *sig, size_t sig_len, EC_KEY *eckey) {
  ECDSA_SIG *s;
  int ret = 0;
  uint8_t *der = NULL;

  if (eckey->ecdsa_meth && eckey->ecdsa_meth->verify) {
    return eckey->ecdsa_meth->verify(digest, digest_len, sig, sig_len, eckey);
  }

  /* Decode the ECDSA signature. */
  s = ECDSA_SIG_from_bytes(sig, sig_len);
  if (s == NULL) {
    goto err;
  }

  /* Defend against potential laxness in the DER parser. */
  size_t der_len;
  if (!ECDSA_SIG_to_bytes(&der, &der_len, s) ||
      der_len != sig_len || memcmp(sig, der, sig_len) != 0) {
    /* This should never happen. crypto/bytestring is strictly DER. */
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  ret = ECDSA_do_verify(digest, digest_len, s, eckey);

err:
  OPENSSL_free(der);
  ECDSA_SIG_free(s);
  return ret;
}

/* digest_to_bn interprets |digest_len| bytes from |digest| as a big-endian
 * number and sets |out| to that value. It then truncates |out| so that it's,
 * at most, as long as |order|. It returns one on success and zero otherwise. */
static int digest_to_bn(BIGNUM *out, const uint8_t *digest, size_t digest_len,
                        const BIGNUM *order) {
  size_t num_bits;

  num_bits = BN_num_bits(order);
  /* Need to truncate digest if it is too long: first truncate whole
   * bytes. */
  if (8 * digest_len > num_bits) {
    digest_len = (num_bits + 7) / 8;
  }
  if (!BN_bin2bn(digest, digest_len, out)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    return 0;
  }

  /* If still too long truncate remaining bits with a shift */
  if ((8 * digest_len > num_bits) &&
      !BN_rshift(out, out, 8 - (num_bits & 0x7))) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    return 0;
  }

  return 1;
}

ECDSA_SIG *ECDSA_do_sign(const uint8_t *digest, size_t digest_len,
                         EC_KEY *key) {
  return ECDSA_do_sign_ex(digest, digest_len, NULL, NULL, key);
}

int ECDSA_do_verify(const uint8_t *digest, size_t digest_len,
                    const ECDSA_SIG *sig, EC_KEY *eckey) {
  int ret = 0;
  BN_CTX *ctx;
  BIGNUM *order, *u1, *u2, *m, *X;
  EC_POINT *point = NULL;
  const EC_GROUP *group;
  const EC_POINT *pub_key;

  if (eckey->ecdsa_meth && eckey->ecdsa_meth->verify) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_NOT_IMPLEMENTED);
    return 0;
  }

  /* check input values */
  if ((group = EC_KEY_get0_group(eckey)) == NULL ||
      (pub_key = EC_KEY_get0_public_key(eckey)) == NULL ||
      sig == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_MISSING_PARAMETERS);
    return 0;
  }

  ctx = BN_CTX_new();
  if (!ctx) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  BN_CTX_start(ctx);
  order = BN_CTX_get(ctx);
  u1 = BN_CTX_get(ctx);
  u2 = BN_CTX_get(ctx);
  m = BN_CTX_get(ctx);
  X = BN_CTX_get(ctx);
  if (order == NULL || u1 == NULL || u2 == NULL || m == NULL || X == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    goto err;
  }

  if (!EC_GROUP_get_order(group, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    goto err;
  }

  if (BN_is_zero(sig->r) || BN_is_negative(sig->r) ||
      BN_ucmp(sig->r, order) >= 0 || BN_is_zero(sig->s) ||
      BN_is_negative(sig->s) || BN_ucmp(sig->s, order) >= 0) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_BAD_SIGNATURE);
    ret = 0; /* signature is invalid */
    goto err;
  }
  /* calculate tmp1 = inv(S) mod order */
  if (!BN_mod_inverse(u2, sig->s, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    goto err;
  }
  if (!digest_to_bn(m, digest, digest_len, order)) {
    goto err;
  }
  /* u1 = m * tmp mod order */
  if (!BN_mod_mul(u1, m, u2, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    goto err;
  }
  /* u2 = r * w mod q */
  if (!BN_mod_mul(u2, sig->r, u2, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    goto err;
  }

  point = EC_POINT_new(group);
  if (point == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }
  if (!EC_POINT_mul(group, point, u1, pub_key, u2, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    goto err;
  }
  if (!EC_POINT_get_affine_coordinates_GFp(group, point, X, NULL, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    goto err;
  }
  if (!BN_nnmod(u1, X, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    goto err;
  }
  /* if the signature is correct u1 is equal to sig->r */
  ret = (BN_ucmp(u1, sig->r) == 0);

err:
  BN_CTX_end(ctx);
  BN_CTX_free(ctx);
  EC_POINT_free(point);
  return ret;
}

static int ecdsa_sign_setup(EC_KEY *eckey, BN_CTX *ctx_in, BIGNUM **kinvp,
                            BIGNUM **rp, const uint8_t *digest,
                            size_t digest_len) {
  BN_CTX *ctx = NULL;
  BIGNUM *k = NULL, *r = NULL, *order = NULL, *X = NULL;
  EC_POINT *tmp_point = NULL;
  const EC_GROUP *group;
  int ret = 0;

  if (eckey == NULL || (group = EC_KEY_get0_group(eckey)) == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }

  if (ctx_in == NULL) {
    if ((ctx = BN_CTX_new()) == NULL) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  } else {
    ctx = ctx_in;
  }

  k = BN_new(); /* this value is later returned in *kinvp */
  r = BN_new(); /* this value is later returned in *rp    */
  order = BN_new();
  X = BN_new();
  if (!k || !r || !order || !X) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }
  tmp_point = EC_POINT_new(group);
  if (tmp_point == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    goto err;
  }
  if (!EC_GROUP_get_order(group, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    goto err;
  }

  do {
    /* If possible, we'll include the private key and message digest in the k
     * generation. The |digest| argument is only empty if |ECDSA_sign_setup| is
     * being used. */
    do {
      int ok;

      if (digest_len > 0) {
        ok = BN_generate_dsa_nonce(k, order, EC_KEY_get0_private_key(eckey),
                                   digest, digest_len, ctx);
      } else {
        ok = BN_rand_range(k, order);
      }
      if (!ok) {
        OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_RANDOM_NUMBER_GENERATION_FAILED);
        goto err;
      }
    } while (BN_is_zero(k));

    /* We do not want timing information to leak the length of k,
     * so we compute G*k using an equivalent scalar of fixed
     * bit-length. */

    if (!BN_add(k, k, order)) {
      goto err;
    }
    if (BN_num_bits(k) <= BN_num_bits(order)) {
      if (!BN_add(k, k, order)) {
        goto err;
      }
    }

    /* compute r the x-coordinate of generator * k */
    if (!EC_POINT_mul(group, tmp_point, k, NULL, NULL, ctx)) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
      goto err;
    }
    if (!EC_POINT_get_affine_coordinates_GFp(group, tmp_point, X, NULL, ctx)) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
      goto err;
    }

    if (!BN_nnmod(r, X, order, ctx)) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
      goto err;
    }
  } while (BN_is_zero(r));

  /* compute the inverse of k */
  if (!BN_mod_inverse(k, k, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
    goto err;
  }
  /* clear old values if necessary */
  BN_clear_free(*rp);
  BN_clear_free(*kinvp);

  /* save the pre-computed values  */
  *rp = r;
  *kinvp = k;
  ret = 1;

err:
  if (!ret) {
    BN_clear_free(k);
    BN_clear_free(r);
  }
  if (ctx_in == NULL) {
    BN_CTX_free(ctx);
  }
  BN_free(order);
  EC_POINT_free(tmp_point);
  BN_clear_free(X);
  return ret;
}

int ECDSA_sign_setup(EC_KEY *eckey, BN_CTX *ctx, BIGNUM **kinv, BIGNUM **rp) {
  return ecdsa_sign_setup(eckey, ctx, kinv, rp, NULL, 0);
}

ECDSA_SIG *ECDSA_do_sign_ex(const uint8_t *digest, size_t digest_len,
                            const BIGNUM *in_kinv, const BIGNUM *in_r,
                            EC_KEY *eckey) {
  int ok = 0;
  BIGNUM *kinv = NULL, *s, *m = NULL, *tmp = NULL, *order = NULL;
  const BIGNUM *ckinv;
  BN_CTX *ctx = NULL;
  const EC_GROUP *group;
  ECDSA_SIG *ret;
  const BIGNUM *priv_key;

  if (eckey->ecdsa_meth && eckey->ecdsa_meth->sign) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_NOT_IMPLEMENTED);
    return NULL;
  }

  group = EC_KEY_get0_group(eckey);
  priv_key = EC_KEY_get0_private_key(eckey);

  if (group == NULL || priv_key == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_PASSED_NULL_PARAMETER);
    return NULL;
  }

  ret = ECDSA_SIG_new();
  if (!ret) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  s = ret->s;

  if ((ctx = BN_CTX_new()) == NULL || (order = BN_new()) == NULL ||
      (tmp = BN_new()) == NULL || (m = BN_new()) == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (!EC_GROUP_get_order(group, order, ctx)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    goto err;
  }
  if (!digest_to_bn(m, digest, digest_len, order)) {
    goto err;
  }
  for (;;) {
    if (in_kinv == NULL || in_r == NULL) {
      if (!ecdsa_sign_setup(eckey, ctx, &kinv, &ret->r, digest, digest_len)) {
        OPENSSL_PUT_ERROR(ECDSA, ERR_R_ECDSA_LIB);
        goto err;
      }
      ckinv = kinv;
    } else {
      ckinv = in_kinv;
      if (BN_copy(ret->r, in_r) == NULL) {
        OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
        goto err;
      }
    }

    if (!BN_mod_mul(tmp, priv_key, ret->r, order, ctx)) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
      goto err;
    }
    if (!BN_mod_add_quick(s, tmp, m, order)) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
      goto err;
    }
    if (!BN_mod_mul(s, s, ckinv, order, ctx)) {
      OPENSSL_PUT_ERROR(ECDSA, ERR_R_BN_LIB);
      goto err;
    }
    if (BN_is_zero(s)) {
      /* if kinv and r have been supplied by the caller
       * don't to generate new kinv and r values */
      if (in_kinv != NULL && in_r != NULL) {
        OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_NEED_NEW_SETUP_VALUES);
        goto err;
      }
    } else {
      /* s != 0 => we have a valid signature */
      break;
    }
  }

  ok = 1;

err:
  if (!ok) {
    ECDSA_SIG_free(ret);
    ret = NULL;
  }
  BN_CTX_free(ctx);
  BN_clear_free(m);
  BN_clear_free(tmp);
  BN_free(order);
  BN_clear_free(kinv);
  return ret;
}

int ECDSA_sign_ex(int type, const uint8_t *digest, size_t digest_len,
                  uint8_t *sig, unsigned int *sig_len, const BIGNUM *kinv,
                  const BIGNUM *r, EC_KEY *eckey) {
  int ret = 0;
  ECDSA_SIG *s = NULL;

  if (eckey->ecdsa_meth && eckey->ecdsa_meth->sign) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_NOT_IMPLEMENTED);
    *sig_len = 0;
    goto err;
  }

  s = ECDSA_do_sign_ex(digest, digest_len, kinv, r, eckey);
  if (s == NULL) {
    *sig_len = 0;
    goto err;
  }

  CBB cbb;
  CBB_zero(&cbb);
  size_t len;
  if (!CBB_init_fixed(&cbb, sig, ECDSA_size(eckey)) ||
      !ECDSA_SIG_marshal(&cbb, s) ||
      !CBB_finish(&cbb, NULL, &len)) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_ENCODE_ERROR);
    CBB_cleanup(&cbb);
    *sig_len = 0;
    goto err;
  }
  *sig_len = (unsigned)len;
  ret = 1;

err:
  ECDSA_SIG_free(s);
  return ret;
}
