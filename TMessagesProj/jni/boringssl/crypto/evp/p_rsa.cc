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

#include <limits.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/nid.h>
#include <openssl/rsa.h>

#include "../internal.h"
#include "../rsa/internal.h"
#include "internal.h"


typedef struct {
  // Key gen parameters
  int nbits;
  BIGNUM *pub_exp;
  // RSA padding mode
  int pad_mode;
  // message digest
  const EVP_MD *md;
  // message digest for MGF1
  const EVP_MD *mgf1md;
  // PSS salt length
  int saltlen;
  // tbuf is a buffer which is either NULL, or is the size of the RSA modulus.
  // It's used to store the output of RSA operations.
  uint8_t *tbuf;
  // OAEP label
  uint8_t *oaep_label;
  size_t oaep_labellen;
} RSA_PKEY_CTX;

typedef struct {
  uint8_t *data;
  size_t len;
} RSA_OAEP_LABEL_PARAMS;

static int pkey_rsa_init(EVP_PKEY_CTX *ctx) {
  RSA_PKEY_CTX *rctx =
      reinterpret_cast<RSA_PKEY_CTX *>(OPENSSL_zalloc(sizeof(RSA_PKEY_CTX)));
  if (!rctx) {
    return 0;
  }

  rctx->nbits = 2048;
  rctx->pad_mode = RSA_PKCS1_PADDING;
  rctx->saltlen = -2;

  ctx->data = rctx;

  return 1;
}

static int pkey_rsa_copy(EVP_PKEY_CTX *dst, EVP_PKEY_CTX *src) {
  RSA_PKEY_CTX *dctx, *sctx;
  if (!pkey_rsa_init(dst)) {
    return 0;
  }
  sctx = reinterpret_cast<RSA_PKEY_CTX *>(src->data);
  dctx = reinterpret_cast<RSA_PKEY_CTX *>(dst->data);
  dctx->nbits = sctx->nbits;
  if (sctx->pub_exp) {
    dctx->pub_exp = BN_dup(sctx->pub_exp);
    if (!dctx->pub_exp) {
      return 0;
    }
  }

  dctx->pad_mode = sctx->pad_mode;
  dctx->md = sctx->md;
  dctx->mgf1md = sctx->mgf1md;
  dctx->saltlen = sctx->saltlen;
  if (sctx->oaep_label) {
    OPENSSL_free(dctx->oaep_label);
    dctx->oaep_label = reinterpret_cast<uint8_t *>(
        OPENSSL_memdup(sctx->oaep_label, sctx->oaep_labellen));
    if (!dctx->oaep_label) {
      return 0;
    }
    dctx->oaep_labellen = sctx->oaep_labellen;
  }

  return 1;
}

static void pkey_rsa_cleanup(EVP_PKEY_CTX *ctx) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);

  if (rctx == NULL) {
    return;
  }

  BN_free(rctx->pub_exp);
  OPENSSL_free(rctx->tbuf);
  OPENSSL_free(rctx->oaep_label);
  OPENSSL_free(rctx);
}

static int setup_tbuf(RSA_PKEY_CTX *ctx, EVP_PKEY_CTX *pk) {
  if (ctx->tbuf) {
    return 1;
  }
  ctx->tbuf =
      reinterpret_cast<uint8_t *>(OPENSSL_malloc(EVP_PKEY_size(pk->pkey)));
  if (!ctx->tbuf) {
    return 0;
  }
  return 1;
}

static int pkey_rsa_sign(EVP_PKEY_CTX *ctx, uint8_t *sig, size_t *siglen,
                         const uint8_t *tbs, size_t tbslen) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);
  RSA *rsa = reinterpret_cast<RSA *>(ctx->pkey->pkey);
  const size_t key_len = EVP_PKEY_size(ctx->pkey);

  if (!sig) {
    *siglen = key_len;
    return 1;
  }

  if (*siglen < key_len) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (rctx->md) {
    unsigned out_len;
    switch (rctx->pad_mode) {
      case RSA_PKCS1_PADDING:
        if (!RSA_sign(EVP_MD_type(rctx->md), tbs, tbslen, sig, &out_len, rsa)) {
          return 0;
        }
        *siglen = out_len;
        return 1;

      case RSA_PKCS1_PSS_PADDING:
        return RSA_sign_pss_mgf1(rsa, siglen, sig, *siglen, tbs, tbslen,
                                 rctx->md, rctx->mgf1md, rctx->saltlen);

      default:
        return 0;
    }
  }

  return RSA_sign_raw(rsa, siglen, sig, *siglen, tbs, tbslen, rctx->pad_mode);
}

static int pkey_rsa_verify(EVP_PKEY_CTX *ctx, const uint8_t *sig, size_t siglen,
                           const uint8_t *tbs, size_t tbslen) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);
  RSA *rsa = reinterpret_cast<RSA *>(ctx->pkey->pkey);

  if (rctx->md) {
    switch (rctx->pad_mode) {
      case RSA_PKCS1_PADDING:
        return RSA_verify(EVP_MD_type(rctx->md), tbs, tbslen, sig, siglen, rsa);

      case RSA_PKCS1_PSS_PADDING:
        return RSA_verify_pss_mgf1(rsa, tbs, tbslen, rctx->md, rctx->mgf1md,
                                   rctx->saltlen, sig, siglen);

      default:
        return 0;
    }
  }

  size_t rslen;
  const size_t key_len = EVP_PKEY_size(ctx->pkey);
  if (!setup_tbuf(rctx, ctx) ||
      !RSA_verify_raw(rsa, &rslen, rctx->tbuf, key_len, sig, siglen,
                      rctx->pad_mode)) {
    return 0;
  }
  if (rslen != tbslen || CRYPTO_memcmp(tbs, rctx->tbuf, rslen) != 0) {
    OPENSSL_PUT_ERROR(RSA, RSA_R_BAD_SIGNATURE);
    return 0;
  }

  return 1;
}

static int pkey_rsa_verify_recover(EVP_PKEY_CTX *ctx, uint8_t *out,
                                   size_t *out_len, const uint8_t *sig,
                                   size_t sig_len) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);
  RSA *rsa = reinterpret_cast<RSA *>(ctx->pkey->pkey);
  const size_t key_len = EVP_PKEY_size(ctx->pkey);

  if (out == NULL) {
    *out_len = key_len;
    return 1;
  }

  if (*out_len < key_len) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (rctx->md == NULL) {
    return RSA_verify_raw(rsa, out_len, out, *out_len, sig, sig_len,
                          rctx->pad_mode);
  }

  if (rctx->pad_mode != RSA_PKCS1_PADDING) {
    return 0;
  }

  // Assemble the encoded hash, using a placeholder hash value.
  static const uint8_t kDummyHash[EVP_MAX_MD_SIZE] = {0};
  const size_t hash_len = EVP_MD_size(rctx->md);
  uint8_t *asn1_prefix;
  size_t asn1_prefix_len;
  int asn1_prefix_allocated;
  if (!setup_tbuf(rctx, ctx) ||
      !RSA_add_pkcs1_prefix(&asn1_prefix, &asn1_prefix_len,
                            &asn1_prefix_allocated, EVP_MD_type(rctx->md),
                            kDummyHash, hash_len)) {
    return 0;
  }

  size_t rslen;
  int ok = 1;
  if (!RSA_verify_raw(rsa, &rslen, rctx->tbuf, key_len, sig, sig_len,
                      RSA_PKCS1_PADDING) ||
      rslen != asn1_prefix_len ||
      // Compare all but the hash suffix.
      CRYPTO_memcmp(rctx->tbuf, asn1_prefix, asn1_prefix_len - hash_len) != 0) {
    ok = 0;
  }

  if (asn1_prefix_allocated) {
    OPENSSL_free(asn1_prefix);
  }

  if (!ok) {
    return 0;
  }

  if (out != NULL) {
    OPENSSL_memcpy(out, rctx->tbuf + rslen - hash_len, hash_len);
  }
  *out_len = hash_len;

  return 1;
}

static int pkey_rsa_encrypt(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                            const uint8_t *in, size_t inlen) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);
  RSA *rsa = reinterpret_cast<RSA *>(ctx->pkey->pkey);
  const size_t key_len = EVP_PKEY_size(ctx->pkey);

  if (!out) {
    *outlen = key_len;
    return 1;
  }

  if (*outlen < key_len) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (rctx->pad_mode == RSA_PKCS1_OAEP_PADDING) {
    if (!setup_tbuf(rctx, ctx) ||
        !RSA_padding_add_PKCS1_OAEP_mgf1(rctx->tbuf, key_len, in, inlen,
                                         rctx->oaep_label, rctx->oaep_labellen,
                                         rctx->md, rctx->mgf1md) ||
        !RSA_encrypt(rsa, outlen, out, *outlen, rctx->tbuf, key_len,
                     RSA_NO_PADDING)) {
      return 0;
    }
    return 1;
  }

  return RSA_encrypt(rsa, outlen, out, *outlen, in, inlen, rctx->pad_mode);
}

static int pkey_rsa_decrypt(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                            const uint8_t *in, size_t inlen) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);
  RSA *rsa = reinterpret_cast<RSA *>(ctx->pkey->pkey);
  const size_t key_len = EVP_PKEY_size(ctx->pkey);

  if (!out) {
    *outlen = key_len;
    return 1;
  }

  if (*outlen < key_len) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (rctx->pad_mode == RSA_PKCS1_OAEP_PADDING) {
    size_t padded_len;
    if (!setup_tbuf(rctx, ctx) ||
        !RSA_decrypt(rsa, &padded_len, rctx->tbuf, key_len, in, inlen,
                     RSA_NO_PADDING) ||
        !RSA_padding_check_PKCS1_OAEP_mgf1(
            out, outlen, key_len, rctx->tbuf, padded_len, rctx->oaep_label,
            rctx->oaep_labellen, rctx->md, rctx->mgf1md)) {
      return 0;
    }
    return 1;
  }

  return RSA_decrypt(rsa, outlen, out, key_len, in, inlen, rctx->pad_mode);
}

static int check_padding_md(const EVP_MD *md, int padding) {
  if (!md) {
    return 1;
  }

  if (padding == RSA_NO_PADDING) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PADDING_MODE);
    return 0;
  }

  return 1;
}

static int is_known_padding(int padding_mode) {
  switch (padding_mode) {
    case RSA_PKCS1_PADDING:
    case RSA_NO_PADDING:
    case RSA_PKCS1_OAEP_PADDING:
    case RSA_PKCS1_PSS_PADDING:
      return 1;
    default:
      return 0;
  }
}

static int pkey_rsa_ctrl(EVP_PKEY_CTX *ctx, int type, int p1, void *p2) {
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);
  switch (type) {
    case EVP_PKEY_CTRL_RSA_PADDING:
      if (!is_known_padding(p1) || !check_padding_md(rctx->md, p1) ||
          (p1 == RSA_PKCS1_PSS_PADDING &&
           0 == (ctx->operation & (EVP_PKEY_OP_SIGN | EVP_PKEY_OP_VERIFY))) ||
          (p1 == RSA_PKCS1_OAEP_PADDING &&
           0 == (ctx->operation & EVP_PKEY_OP_TYPE_CRYPT))) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_ILLEGAL_OR_UNSUPPORTED_PADDING_MODE);
        return 0;
      }
      if ((p1 == RSA_PKCS1_PSS_PADDING || p1 == RSA_PKCS1_OAEP_PADDING) &&
          rctx->md == NULL) {
        rctx->md = EVP_sha1();
      }
      rctx->pad_mode = p1;
      return 1;

    case EVP_PKEY_CTRL_GET_RSA_PADDING:
      *(int *)p2 = rctx->pad_mode;
      return 1;

    case EVP_PKEY_CTRL_RSA_PSS_SALTLEN:
    case EVP_PKEY_CTRL_GET_RSA_PSS_SALTLEN:
      if (rctx->pad_mode != RSA_PKCS1_PSS_PADDING) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PSS_SALTLEN);
        return 0;
      }
      if (type == EVP_PKEY_CTRL_GET_RSA_PSS_SALTLEN) {
        *(int *)p2 = rctx->saltlen;
      } else {
        if (p1 < -2) {
          return 0;
        }
        rctx->saltlen = p1;
      }
      return 1;

    case EVP_PKEY_CTRL_RSA_KEYGEN_BITS:
      if (p1 < 256) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_KEYBITS);
        return 0;
      }
      rctx->nbits = p1;
      return 1;

    case EVP_PKEY_CTRL_RSA_KEYGEN_PUBEXP:
      if (!p2) {
        return 0;
      }
      BN_free(rctx->pub_exp);
      rctx->pub_exp = reinterpret_cast<BIGNUM *>(p2);
      return 1;

    case EVP_PKEY_CTRL_RSA_OAEP_MD:
    case EVP_PKEY_CTRL_GET_RSA_OAEP_MD:
      if (rctx->pad_mode != RSA_PKCS1_OAEP_PADDING) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PADDING_MODE);
        return 0;
      }
      if (type == EVP_PKEY_CTRL_GET_RSA_OAEP_MD) {
        *(const EVP_MD **)p2 = rctx->md;
      } else {
        rctx->md = reinterpret_cast<EVP_MD *>(p2);
      }
      return 1;

    case EVP_PKEY_CTRL_MD:
      if (!check_padding_md(reinterpret_cast<EVP_MD *>(p2), rctx->pad_mode)) {
        return 0;
      }
      rctx->md = reinterpret_cast<EVP_MD *>(p2);
      return 1;

    case EVP_PKEY_CTRL_GET_MD:
      *(const EVP_MD **)p2 = rctx->md;
      return 1;

    case EVP_PKEY_CTRL_RSA_MGF1_MD:
    case EVP_PKEY_CTRL_GET_RSA_MGF1_MD:
      if (rctx->pad_mode != RSA_PKCS1_PSS_PADDING &&
          rctx->pad_mode != RSA_PKCS1_OAEP_PADDING) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_MGF1_MD);
        return 0;
      }
      if (type == EVP_PKEY_CTRL_GET_RSA_MGF1_MD) {
        if (rctx->mgf1md) {
          *(const EVP_MD **)p2 = rctx->mgf1md;
        } else {
          *(const EVP_MD **)p2 = rctx->md;
        }
      } else {
        rctx->mgf1md = reinterpret_cast<EVP_MD *>(p2);
      }
      return 1;

    case EVP_PKEY_CTRL_RSA_OAEP_LABEL: {
      if (rctx->pad_mode != RSA_PKCS1_OAEP_PADDING) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PADDING_MODE);
        return 0;
      }
      OPENSSL_free(rctx->oaep_label);
      RSA_OAEP_LABEL_PARAMS *params =
          reinterpret_cast<RSA_OAEP_LABEL_PARAMS *>(p2);
      rctx->oaep_label = params->data;
      rctx->oaep_labellen = params->len;
      return 1;
    }

    case EVP_PKEY_CTRL_GET_RSA_OAEP_LABEL:
      if (rctx->pad_mode != RSA_PKCS1_OAEP_PADDING) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PADDING_MODE);
        return 0;
      }
      CBS_init((CBS *)p2, rctx->oaep_label, rctx->oaep_labellen);
      return 1;

    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_COMMAND_NOT_SUPPORTED);
      return 0;
  }
}

static int pkey_rsa_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  RSA *rsa = NULL;
  RSA_PKEY_CTX *rctx = reinterpret_cast<RSA_PKEY_CTX *>(ctx->data);

  if (!rctx->pub_exp) {
    rctx->pub_exp = BN_new();
    if (!rctx->pub_exp || !BN_set_word(rctx->pub_exp, RSA_F4)) {
      return 0;
    }
  }
  rsa = RSA_new();
  if (!rsa) {
    return 0;
  }

  if (!RSA_generate_key_ex(rsa, rctx->nbits, rctx->pub_exp, NULL)) {
    RSA_free(rsa);
    return 0;
  }

  EVP_PKEY_assign_RSA(pkey, rsa);
  return 1;
}

const EVP_PKEY_METHOD rsa_pkey_meth = {
    EVP_PKEY_RSA,
    pkey_rsa_init,
    pkey_rsa_copy,
    pkey_rsa_cleanup,
    pkey_rsa_keygen,
    pkey_rsa_sign,
    NULL /* sign_message */,
    pkey_rsa_verify,
    NULL /* verify_message */,
    pkey_rsa_verify_recover,
    pkey_rsa_encrypt,
    pkey_rsa_decrypt,
    NULL /* derive */,
    NULL /* paramgen */,
    pkey_rsa_ctrl,
};

int EVP_PKEY_CTX_set_rsa_padding(EVP_PKEY_CTX *ctx, int padding) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, -1, EVP_PKEY_CTRL_RSA_PADDING,
                           padding, NULL);
}

int EVP_PKEY_CTX_get_rsa_padding(EVP_PKEY_CTX *ctx, int *out_padding) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, -1, EVP_PKEY_CTRL_GET_RSA_PADDING,
                           0, out_padding);
}

int EVP_PKEY_CTX_set_rsa_pss_keygen_md(EVP_PKEY_CTX *ctx, const EVP_MD *md) {
  return 0;
}

int EVP_PKEY_CTX_set_rsa_pss_keygen_saltlen(EVP_PKEY_CTX *ctx, int salt_len) {
  return 0;
}

int EVP_PKEY_CTX_set_rsa_pss_keygen_mgf1_md(EVP_PKEY_CTX *ctx,
                                            const EVP_MD *md) {
  return 0;
}

int EVP_PKEY_CTX_set_rsa_pss_saltlen(EVP_PKEY_CTX *ctx, int salt_len) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA,
                           (EVP_PKEY_OP_SIGN | EVP_PKEY_OP_VERIFY),
                           EVP_PKEY_CTRL_RSA_PSS_SALTLEN, salt_len, NULL);
}

int EVP_PKEY_CTX_get_rsa_pss_saltlen(EVP_PKEY_CTX *ctx, int *out_salt_len) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA,
                           (EVP_PKEY_OP_SIGN | EVP_PKEY_OP_VERIFY),
                           EVP_PKEY_CTRL_GET_RSA_PSS_SALTLEN, 0, out_salt_len);
}

int EVP_PKEY_CTX_set_rsa_keygen_bits(EVP_PKEY_CTX *ctx, int bits) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_KEYGEN,
                           EVP_PKEY_CTRL_RSA_KEYGEN_BITS, bits, NULL);
}

int EVP_PKEY_CTX_set_rsa_keygen_pubexp(EVP_PKEY_CTX *ctx, BIGNUM *e) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_KEYGEN,
                           EVP_PKEY_CTRL_RSA_KEYGEN_PUBEXP, 0, e);
}

int EVP_PKEY_CTX_set_rsa_oaep_md(EVP_PKEY_CTX *ctx, const EVP_MD *md) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_RSA_OAEP_MD, 0, (void *)md);
}

int EVP_PKEY_CTX_get_rsa_oaep_md(EVP_PKEY_CTX *ctx, const EVP_MD **out_md) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_GET_RSA_OAEP_MD, 0, (void *)out_md);
}

int EVP_PKEY_CTX_set_rsa_mgf1_md(EVP_PKEY_CTX *ctx, const EVP_MD *md) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA,
                           EVP_PKEY_OP_TYPE_SIG | EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_RSA_MGF1_MD, 0, (void *)md);
}

int EVP_PKEY_CTX_get_rsa_mgf1_md(EVP_PKEY_CTX *ctx, const EVP_MD **out_md) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA,
                           EVP_PKEY_OP_TYPE_SIG | EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_GET_RSA_MGF1_MD, 0, (void *)out_md);
}

int EVP_PKEY_CTX_set0_rsa_oaep_label(EVP_PKEY_CTX *ctx, uint8_t *label,
                                     size_t label_len) {
  RSA_OAEP_LABEL_PARAMS params = {label, label_len};
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_RSA_OAEP_LABEL, 0, &params);
}

int EVP_PKEY_CTX_get0_rsa_oaep_label(EVP_PKEY_CTX *ctx,
                                     const uint8_t **out_label) {
  CBS label;
  if (!EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_TYPE_CRYPT,
                         EVP_PKEY_CTRL_GET_RSA_OAEP_LABEL, 0, &label)) {
    return -1;
  }
  if (CBS_len(&label) > INT_MAX) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_OVERFLOW);
    return -1;
  }
  *out_label = CBS_data(&label);
  return (int)CBS_len(&label);
}
