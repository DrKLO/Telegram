/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2006.
 */
/* ====================================================================
 * Copyright (c) 2006 The OpenSSL Project.  All rights reserved.
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
 *    licensing@OpenSSL.org.
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

#include <openssl/evp.h>

#include <string.h>

#include <openssl/asn1.h>
#include <openssl/bn.h>
#include <openssl/buf.h>
#include <openssl/digest.h>
#include <openssl/ec.h>
#include <openssl/ec_key.h>
#include <openssl/ecdh.h>
#include <openssl/ecdsa.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>

#include "internal.h"
#include "../ec/internal.h"


typedef struct {
  /* Key and paramgen group */
  EC_GROUP *gen_group;
  /* message digest */
  const EVP_MD *md;
} EC_PKEY_CTX;


static int pkey_ec_init(EVP_PKEY_CTX *ctx) {
  EC_PKEY_CTX *dctx;
  dctx = OPENSSL_malloc(sizeof(EC_PKEY_CTX));
  if (!dctx) {
    return 0;
  }
  memset(dctx, 0, sizeof(EC_PKEY_CTX));

  ctx->data = dctx;

  return 1;
}

static int pkey_ec_copy(EVP_PKEY_CTX *dst, EVP_PKEY_CTX *src) {
  EC_PKEY_CTX *dctx, *sctx;
  if (!pkey_ec_init(dst)) {
    return 0;
  }
  sctx = src->data;
  dctx = dst->data;

  if (sctx->gen_group) {
    dctx->gen_group = EC_GROUP_dup(sctx->gen_group);
    if (!dctx->gen_group) {
      return 0;
    }
  }
  dctx->md = sctx->md;

  return 1;
}

static void pkey_ec_cleanup(EVP_PKEY_CTX *ctx) {
  EC_PKEY_CTX *dctx = ctx->data;
  if (!dctx) {
    return;
  }

  EC_GROUP_free(dctx->gen_group);
  OPENSSL_free(dctx);
}

static int pkey_ec_sign(EVP_PKEY_CTX *ctx, uint8_t *sig, size_t *siglen,
                        const uint8_t *tbs, size_t tbslen) {
  int type;
  unsigned int sltmp;
  EC_PKEY_CTX *dctx = ctx->data;
  EC_KEY *ec = ctx->pkey->pkey.ec;

  if (!sig) {
    *siglen = ECDSA_size(ec);
    return 1;
  } else if (*siglen < (size_t)ECDSA_size(ec)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  type = NID_sha1;
  if (dctx->md) {
    type = EVP_MD_type(dctx->md);
  }

  if (!ECDSA_sign(type, tbs, tbslen, sig, &sltmp, ec)) {
    return 0;
  }
  *siglen = (size_t)sltmp;
  return 1;
}

static int pkey_ec_verify(EVP_PKEY_CTX *ctx, const uint8_t *sig, size_t siglen,
                          const uint8_t *tbs, size_t tbslen) {
  int type;
  EC_PKEY_CTX *dctx = ctx->data;
  EC_KEY *ec = ctx->pkey->pkey.ec;

  type = NID_sha1;
  if (dctx->md) {
    type = EVP_MD_type(dctx->md);
  }

  return ECDSA_verify(type, tbs, tbslen, sig, siglen, ec);
}

static int pkey_ec_derive(EVP_PKEY_CTX *ctx, uint8_t *key,
                          size_t *keylen) {
  int ret;
  size_t outlen;
  const EC_POINT *pubkey = NULL;
  EC_KEY *eckey;

  if (!ctx->pkey || !ctx->peerkey) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  eckey = ctx->pkey->pkey.ec;

  if (!key) {
    const EC_GROUP *group;
    group = EC_KEY_get0_group(eckey);
    *keylen = (EC_GROUP_get_degree(group) + 7) / 8;
    return 1;
  }
  pubkey = EC_KEY_get0_public_key(ctx->peerkey->pkey.ec);

  /* NB: unlike PKCS#3 DH, if *outlen is less than maximum size this is
   * not an error, the result is truncated. */

  outlen = *keylen;

  ret = ECDH_compute_key(key, outlen, pubkey, eckey, 0);
  if (ret < 0) {
    return 0;
  }
  *keylen = ret;
  return 1;
}

static int pkey_ec_ctrl(EVP_PKEY_CTX *ctx, int type, int p1, void *p2) {
  EC_PKEY_CTX *dctx = ctx->data;
  EC_GROUP *group;

  switch (type) {
    case EVP_PKEY_CTRL_EC_PARAMGEN_CURVE_NID:
      group = EC_GROUP_new_by_curve_name(p1);
      if (group == NULL) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_CURVE);
        return 0;
      }
      EC_GROUP_free(dctx->gen_group);
      dctx->gen_group = group;
      return 1;

    case EVP_PKEY_CTRL_MD:
      if (EVP_MD_type((const EVP_MD *)p2) != NID_sha1 &&
          EVP_MD_type((const EVP_MD *)p2) != NID_ecdsa_with_SHA1 &&
          EVP_MD_type((const EVP_MD *)p2) != NID_sha224 &&
          EVP_MD_type((const EVP_MD *)p2) != NID_sha256 &&
          EVP_MD_type((const EVP_MD *)p2) != NID_sha384 &&
          EVP_MD_type((const EVP_MD *)p2) != NID_sha512) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_DIGEST_TYPE);
        return 0;
      }
      dctx->md = p2;
      return 1;

    case EVP_PKEY_CTRL_GET_MD:
      *(const EVP_MD **)p2 = dctx->md;
      return 1;

    case EVP_PKEY_CTRL_PEER_KEY:
      /* Default behaviour is OK */
      return 1;

    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_COMMAND_NOT_SUPPORTED);
      return 0;
  }
}

static int pkey_ec_paramgen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  EC_KEY *ec = NULL;
  EC_PKEY_CTX *dctx = ctx->data;
  int ret = 0;

  if (dctx->gen_group == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NO_PARAMETERS_SET);
    return 0;
  }
  ec = EC_KEY_new();
  if (!ec) {
    return 0;
  }
  ret = EC_KEY_set_group(ec, dctx->gen_group);
  if (ret) {
    EVP_PKEY_assign_EC_KEY(pkey, ec);
  } else {
    EC_KEY_free(ec);
  }
  return ret;
}

static int pkey_ec_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  EC_KEY *ec = NULL;
  EC_PKEY_CTX *dctx = ctx->data;
  if (ctx->pkey == NULL && dctx->gen_group == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NO_PARAMETERS_SET);
    return 0;
  }
  ec = EC_KEY_new();
  if (!ec) {
    return 0;
  }
  EVP_PKEY_assign_EC_KEY(pkey, ec);
  if (ctx->pkey) {
    /* Note: if error return, pkey is freed by parent routine */
    if (!EVP_PKEY_copy_parameters(pkey, ctx->pkey)) {
      return 0;
    }
  } else {
    if (!EC_KEY_set_group(ec, dctx->gen_group)) {
      return 0;
    }
  }
  return EC_KEY_generate_key(pkey->pkey.ec);
}

const EVP_PKEY_METHOD ec_pkey_meth = {
    EVP_PKEY_EC,          0 /* flags */,        pkey_ec_init,
    pkey_ec_copy,         pkey_ec_cleanup,      0 /* paramgen_init */,
    pkey_ec_paramgen,     0 /* keygen_init */,  pkey_ec_keygen,
    0 /* sign_init */,    pkey_ec_sign,         0 /* verify_init */,
    pkey_ec_verify,       0 /* encrypt_init */, 0 /* encrypt */,
    0 /* decrypt_init */, 0 /* decrypt */,      0 /* derive_init */,
    pkey_ec_derive,       pkey_ec_ctrl,
};
