// Copyright 2006-2016 The OpenSSL Project Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <openssl/evp.h>

#include <string.h>

#include <openssl/bn.h>
#include <openssl/digest.h>
#include <openssl/ec.h>
#include <openssl/ec_key.h>
#include <openssl/ecdh.h>
#include <openssl/ecdsa.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/nid.h>

#include "../fipsmodule/ec/internal.h"
#include "../internal.h"
#include "internal.h"


typedef struct {
  // message digest
  const EVP_MD *md;
  const EC_GROUP *gen_group;
} EC_PKEY_CTX;


static int pkey_ec_init(EVP_PKEY_CTX *ctx) {
  EC_PKEY_CTX *dctx =
      reinterpret_cast<EC_PKEY_CTX *>(OPENSSL_zalloc(sizeof(EC_PKEY_CTX)));
  if (!dctx) {
    return 0;
  }

  ctx->data = dctx;
  return 1;
}

static int pkey_ec_copy(EVP_PKEY_CTX *dst, EVP_PKEY_CTX *src) {
  if (!pkey_ec_init(dst)) {
    return 0;
  }

  const EC_PKEY_CTX *sctx = reinterpret_cast<EC_PKEY_CTX *>(src->data);
  EC_PKEY_CTX *dctx = reinterpret_cast<EC_PKEY_CTX *>(dst->data);
  dctx->md = sctx->md;
  dctx->gen_group = sctx->gen_group;
  return 1;
}

static void pkey_ec_cleanup(EVP_PKEY_CTX *ctx) {
  EC_PKEY_CTX *dctx = reinterpret_cast<EC_PKEY_CTX *>(ctx->data);
  if (!dctx) {
    return;
  }

  OPENSSL_free(dctx);
}

static int pkey_ec_sign(EVP_PKEY_CTX *ctx, uint8_t *sig, size_t *siglen,
                        const uint8_t *tbs, size_t tbslen) {
  const EC_KEY *ec = reinterpret_cast<EC_KEY *>(ctx->pkey->pkey);
  if (!sig) {
    *siglen = ECDSA_size(ec);
    return 1;
  } else if (*siglen < (size_t)ECDSA_size(ec)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  unsigned int sltmp;
  if (!ECDSA_sign(0, tbs, tbslen, sig, &sltmp, ec)) {
    return 0;
  }
  *siglen = (size_t)sltmp;
  return 1;
}

static int pkey_ec_verify(EVP_PKEY_CTX *ctx, const uint8_t *sig, size_t siglen,
                          const uint8_t *tbs, size_t tbslen) {
  const EC_KEY *ec_key = reinterpret_cast<EC_KEY *>(ctx->pkey->pkey);
  return ECDSA_verify(0, tbs, tbslen, sig, siglen, ec_key);
}

static int pkey_ec_derive(EVP_PKEY_CTX *ctx, uint8_t *key, size_t *keylen) {
  if (!ctx->pkey || !ctx->peerkey) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  const EC_KEY *eckey = reinterpret_cast<EC_KEY *>(ctx->pkey->pkey);
  if (!key) {
    const EC_GROUP *group;
    group = EC_KEY_get0_group(eckey);
    *keylen = (EC_GROUP_get_degree(group) + 7) / 8;
    return 1;
  }

  const EC_KEY *eckey_peer = reinterpret_cast<EC_KEY *>(ctx->peerkey->pkey);
  const EC_POINT *pubkey = EC_KEY_get0_public_key(eckey_peer);

  // NB: unlike PKCS#3 DH, if *outlen is less than maximum size this is
  // not an error, the result is truncated.
  size_t outlen = *keylen;
  int ret = ECDH_compute_key(key, outlen, pubkey, eckey, 0);
  if (ret < 0) {
    return 0;
  }
  *keylen = ret;
  return 1;
}

static int pkey_ec_ctrl(EVP_PKEY_CTX *ctx, int type, int p1, void *p2) {
  EC_PKEY_CTX *dctx = reinterpret_cast<EC_PKEY_CTX *>(ctx->data);

  switch (type) {
    case EVP_PKEY_CTRL_MD: {
      const EVP_MD *md = reinterpret_cast<const EVP_MD *>(p2);
      int md_type = EVP_MD_type(md);
      if (md_type != NID_sha1 && md_type != NID_sha224 &&
          md_type != NID_sha256 && md_type != NID_sha384 &&
          md_type != NID_sha512) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_DIGEST_TYPE);
        return 0;
      }
      dctx->md = md;
      return 1;
    }

    case EVP_PKEY_CTRL_GET_MD:
      *(const EVP_MD **)p2 = dctx->md;
      return 1;

    case EVP_PKEY_CTRL_PEER_KEY:
      // Default behaviour is OK
      return 1;

    case EVP_PKEY_CTRL_EC_PARAMGEN_CURVE_NID: {
      const EC_GROUP *group = EC_GROUP_new_by_curve_name(p1);
      if (group == NULL) {
        return 0;
      }
      dctx->gen_group = group;
      return 1;
    }

    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_COMMAND_NOT_SUPPORTED);
      return 0;
  }
}

static int pkey_ec_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  EC_PKEY_CTX *dctx = reinterpret_cast<EC_PKEY_CTX *>(ctx->data);
  const EC_GROUP *group = dctx->gen_group;
  if (group == NULL) {
    if (ctx->pkey == NULL) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_NO_PARAMETERS_SET);
      return 0;
    }
    group = EC_KEY_get0_group(reinterpret_cast<EC_KEY *>(ctx->pkey->pkey));
  }
  EC_KEY *ec = EC_KEY_new();
  if (ec == NULL || !EC_KEY_set_group(ec, group) || !EC_KEY_generate_key(ec)) {
    EC_KEY_free(ec);
    return 0;
  }
  EVP_PKEY_assign_EC_KEY(pkey, ec);
  return 1;
}

static int pkey_ec_paramgen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  EC_PKEY_CTX *dctx = reinterpret_cast<EC_PKEY_CTX *>(ctx->data);
  if (dctx->gen_group == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NO_PARAMETERS_SET);
    return 0;
  }
  EC_KEY *ec = EC_KEY_new();
  if (ec == NULL || !EC_KEY_set_group(ec, dctx->gen_group)) {
    EC_KEY_free(ec);
    return 0;
  }
  EVP_PKEY_assign_EC_KEY(pkey, ec);
  return 1;
}

const EVP_PKEY_METHOD ec_pkey_meth = {
    EVP_PKEY_EC,
    pkey_ec_init,
    pkey_ec_copy,
    pkey_ec_cleanup,
    pkey_ec_keygen,
    pkey_ec_sign,
    NULL /* sign_message */,
    pkey_ec_verify,
    NULL /* verify_message */,
    NULL /* verify_recover */,
    NULL /* encrypt */,
    NULL /* decrypt */,
    pkey_ec_derive,
    pkey_ec_paramgen,
    pkey_ec_ctrl,
};

int EVP_PKEY_CTX_set_ec_paramgen_curve_nid(EVP_PKEY_CTX *ctx, int nid) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_EC, EVP_PKEY_OP_TYPE_GEN,
                           EVP_PKEY_CTRL_EC_PARAMGEN_CURVE_NID, nid, NULL);
}

int EVP_PKEY_CTX_set_ec_param_enc(EVP_PKEY_CTX *ctx, int encoding) {
  // BoringSSL only supports named curve syntax.
  if (encoding != OPENSSL_EC_NAMED_CURVE) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PARAMETERS);
    return 0;
  }
  return 1;
}
