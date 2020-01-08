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
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/sha.h>
#include <openssl/type_check.h>

#include "../bn/internal.h"
#include "../ec/internal.h"
#include "../../internal.h"


// digest_to_scalar interprets |digest_len| bytes from |digest| as a scalar for
// ECDSA. Note this value is not fully reduced modulo the order, only the
// correct number of bits.
static void digest_to_scalar(const EC_GROUP *group, EC_SCALAR *out,
                             const uint8_t *digest, size_t digest_len) {
  const BIGNUM *order = &group->order;
  size_t num_bits = BN_num_bits(order);
  // Need to truncate digest if it is too long: first truncate whole bytes.
  size_t num_bytes = (num_bits + 7) / 8;
  if (digest_len > num_bytes) {
    digest_len = num_bytes;
  }
  OPENSSL_memset(out, 0, sizeof(EC_SCALAR));
  for (size_t i = 0; i < digest_len; i++) {
    out->bytes[i] = digest[digest_len - 1 - i];
  }

  // If it is still too long, truncate remaining bits with a shift.
  if (8 * digest_len > num_bits) {
    bn_rshift_words(out->words, out->words, 8 - (num_bits & 0x7), order->width);
  }

  // |out| now has the same bit width as |order|, but this only bounds by
  // 2*|order|. Subtract the order if out of range.
  //
  // Montgomery multiplication accepts the looser bounds, so this isn't strictly
  // necessary, but it is a cleaner abstraction and has no performance impact.
  BN_ULONG tmp[EC_MAX_WORDS];
  bn_reduce_once_in_place(out->words, 0 /* no carry */, order->d, tmp,
                          order->width);
}

ECDSA_SIG *ECDSA_SIG_new(void) {
  ECDSA_SIG *sig = OPENSSL_malloc(sizeof(ECDSA_SIG));
  if (sig == NULL) {
    return NULL;
  }
  sig->r = BN_new();
  sig->s = BN_new();
  if (sig->r == NULL || sig->s == NULL) {
    ECDSA_SIG_free(sig);
    return NULL;
  }
  return sig;
}

void ECDSA_SIG_free(ECDSA_SIG *sig) {
  if (sig == NULL) {
    return;
  }

  BN_free(sig->r);
  BN_free(sig->s);
  OPENSSL_free(sig);
}

void ECDSA_SIG_get0(const ECDSA_SIG *sig, const BIGNUM **out_r,
                    const BIGNUM **out_s) {
  if (out_r != NULL) {
    *out_r = sig->r;
  }
  if (out_s != NULL) {
    *out_s = sig->s;
  }
}

int ECDSA_SIG_set0(ECDSA_SIG *sig, BIGNUM *r, BIGNUM *s) {
  if (r == NULL || s == NULL) {
    return 0;
  }
  BN_free(sig->r);
  BN_free(sig->s);
  sig->r = r;
  sig->s = s;
  return 1;
}

int ECDSA_do_verify(const uint8_t *digest, size_t digest_len,
                    const ECDSA_SIG *sig, const EC_KEY *eckey) {
  const EC_GROUP *group = EC_KEY_get0_group(eckey);
  const EC_POINT *pub_key = EC_KEY_get0_public_key(eckey);
  if (group == NULL || pub_key == NULL || sig == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_MISSING_PARAMETERS);
    return 0;
  }

  EC_SCALAR r, s, u1, u2, s_inv_mont, m;
  if (BN_is_zero(sig->r) ||
      !ec_bignum_to_scalar(group, &r, sig->r) ||
      BN_is_zero(sig->s) ||
      !ec_bignum_to_scalar(group, &s, sig->s)) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_BAD_SIGNATURE);
    return 0;
  }

  // s_inv_mont = s^-1 in the Montgomery domain. This is
  ec_scalar_inv_montgomery_vartime(group, &s_inv_mont, &s);

  // u1 = m * s^-1 mod order
  // u2 = r * s^-1 mod order
  //
  // |s_inv_mont| is in Montgomery form while |m| and |r| are not, so |u1| and
  // |u2| will be taken out of Montgomery form, as desired.
  digest_to_scalar(group, &m, digest, digest_len);
  ec_scalar_mul_montgomery(group, &u1, &m, &s_inv_mont);
  ec_scalar_mul_montgomery(group, &u2, &r, &s_inv_mont);

  EC_RAW_POINT point;
  if (!ec_point_mul_scalar_public(group, &point, &u1, &pub_key->raw, &u2)) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_EC_LIB);
    return 0;
  }

  if (!ec_cmp_x_coordinate(group, &point, &r)) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_BAD_SIGNATURE);
    return 0;
  }

  return 1;
}

static int ecdsa_sign_setup(const EC_KEY *eckey, EC_SCALAR *out_kinv_mont,
                            EC_SCALAR *out_r, const uint8_t *digest,
                            size_t digest_len, const EC_SCALAR *priv_key) {
  // Check that the size of the group order is FIPS compliant (FIPS 186-4
  // B.5.2).
  const EC_GROUP *group = EC_KEY_get0_group(eckey);
  const BIGNUM *order = EC_GROUP_get0_order(group);
  if (BN_num_bits(order) < 160) {
    OPENSSL_PUT_ERROR(ECDSA, EC_R_INVALID_GROUP_ORDER);
    return 0;
  }

  int ret = 0;
  EC_SCALAR k;
  EC_RAW_POINT tmp_point;
  do {
    // Include the private key and message digest in the k generation.
    if (eckey->fixed_k != NULL) {
      if (!ec_bignum_to_scalar(group, &k, eckey->fixed_k)) {
        goto err;
      }
    } else {
      // Pass a SHA512 hash of the private key and digest as additional data
      // into the RBG. This is a hardening measure against entropy failure.
      OPENSSL_STATIC_ASSERT(SHA512_DIGEST_LENGTH >= 32,
                            "additional_data is too large for SHA-512");
      SHA512_CTX sha;
      uint8_t additional_data[SHA512_DIGEST_LENGTH];
      SHA512_Init(&sha);
      SHA512_Update(&sha, priv_key->words, order->width * sizeof(BN_ULONG));
      SHA512_Update(&sha, digest, digest_len);
      SHA512_Final(additional_data, &sha);
      if (!ec_random_nonzero_scalar(group, &k, additional_data)) {
        goto err;
      }
    }

    // Compute k^-1 in the Montgomery domain. This is |ec_scalar_to_montgomery|
    // followed by |ec_scalar_inv_montgomery|, but |ec_scalar_inv_montgomery|
    // followed by |ec_scalar_from_montgomery| is equivalent and slightly more
    // efficient.
    ec_scalar_inv_montgomery(group, out_kinv_mont, &k);
    ec_scalar_from_montgomery(group, out_kinv_mont, out_kinv_mont);

    // Compute r, the x-coordinate of generator * k.
    if (!ec_point_mul_scalar_base(group, &tmp_point, &k) ||
        !ec_get_x_coordinate_as_scalar(group, out_r, &tmp_point)) {
      goto err;
    }
  } while (ec_scalar_is_zero(group, out_r));

  ret = 1;

err:
  OPENSSL_cleanse(&k, sizeof(k));
  return ret;
}

ECDSA_SIG *ECDSA_do_sign(const uint8_t *digest, size_t digest_len,
                         const EC_KEY *eckey) {
  if (eckey->ecdsa_meth && eckey->ecdsa_meth->sign) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_NOT_IMPLEMENTED);
    return NULL;
  }

  const EC_GROUP *group = EC_KEY_get0_group(eckey);
  if (group == NULL || eckey->priv_key == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_PASSED_NULL_PARAMETER);
    return NULL;
  }
  const BIGNUM *order = EC_GROUP_get0_order(group);
  const EC_SCALAR *priv_key = &eckey->priv_key->scalar;

  int ok = 0;
  ECDSA_SIG *ret = ECDSA_SIG_new();
  EC_SCALAR kinv_mont, r_mont, s, m, tmp;
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  digest_to_scalar(group, &m, digest, digest_len);
  for (;;) {
    if (!ecdsa_sign_setup(eckey, &kinv_mont, &r_mont, digest, digest_len,
                          priv_key) ||
        !bn_set_words(ret->r, r_mont.words, order->width)) {
      goto err;
    }

    // Compute priv_key * r (mod order). Note if only one parameter is in the
    // Montgomery domain, |ec_scalar_mod_mul_montgomery| will compute the answer
    // in the normal domain.
    ec_scalar_to_montgomery(group, &r_mont, &r_mont);
    ec_scalar_mul_montgomery(group, &s, priv_key, &r_mont);

    // Compute tmp = m + priv_key * r.
    ec_scalar_add(group, &tmp, &m, &s);

    // Finally, multiply s by k^-1. That was retained in Montgomery form, so the
    // same technique as the previous multiplication works.
    ec_scalar_mul_montgomery(group, &s, &tmp, &kinv_mont);
    if (!bn_set_words(ret->s, s.words, order->width)) {
      goto err;
    }
    if (!BN_is_zero(ret->s)) {
      // s != 0 => we have a valid signature
      break;
    }
  }

  ok = 1;

err:
  if (!ok) {
    ECDSA_SIG_free(ret);
    ret = NULL;
  }
  OPENSSL_cleanse(&kinv_mont, sizeof(kinv_mont));
  OPENSSL_cleanse(&r_mont, sizeof(r_mont));
  OPENSSL_cleanse(&s, sizeof(s));
  OPENSSL_cleanse(&tmp, sizeof(tmp));
  OPENSSL_cleanse(&m, sizeof(m));
  return ret;
}
