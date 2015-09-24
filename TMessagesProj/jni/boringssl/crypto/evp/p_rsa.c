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

#include <limits.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/buf.h>
#include <openssl/bytestring.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/rsa.h>

#include "../rsa/internal.h"
#include "internal.h"


typedef struct {
  /* Key gen parameters */
  int nbits;
  BIGNUM *pub_exp;
  /* RSA padding mode */
  int pad_mode;
  /* message digest */
  const EVP_MD *md;
  /* message digest for MGF1 */
  const EVP_MD *mgf1md;
  /* PSS salt length */
  int saltlen;
  /* tbuf is a buffer which is either NULL, or is the size of the RSA modulus.
   * It's used to store the output of RSA operations. */
  uint8_t *tbuf;
  /* OAEP label */
  uint8_t *oaep_label;
  size_t oaep_labellen;
} RSA_PKEY_CTX;

static int pkey_rsa_init(EVP_PKEY_CTX *ctx) {
  RSA_PKEY_CTX *rctx;
  rctx = OPENSSL_malloc(sizeof(RSA_PKEY_CTX));
  if (!rctx) {
    return 0;
  }
  memset(rctx, 0, sizeof(RSA_PKEY_CTX));

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
  sctx = src->data;
  dctx = dst->data;
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
  if (sctx->oaep_label) {
    OPENSSL_free(dctx->oaep_label);
    dctx->oaep_label = BUF_memdup(sctx->oaep_label, sctx->oaep_labellen);
    if (!dctx->oaep_label) {
      return 0;
    }
    dctx->oaep_labellen = sctx->oaep_labellen;
  }

  return 1;
}

static void pkey_rsa_cleanup(EVP_PKEY_CTX *ctx) {
  RSA_PKEY_CTX *rctx = ctx->data;

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
  ctx->tbuf = OPENSSL_malloc(EVP_PKEY_size(pk->pkey));
  if (!ctx->tbuf) {
    return 0;
  }
  return 1;
}

static int pkey_rsa_sign(EVP_PKEY_CTX *ctx, uint8_t *sig, size_t *siglen,
                         const uint8_t *tbs, size_t tbslen) {
  RSA_PKEY_CTX *rctx = ctx->data;
  RSA *rsa = ctx->pkey->pkey.rsa;
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
    unsigned int out_len;

    if (tbslen != EVP_MD_size(rctx->md)) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_DIGEST_LENGTH);
      return 0;
    }

    if (EVP_MD_type(rctx->md) == NID_mdc2) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_NO_MDC2_SUPPORT);
      return 0;
    }

    switch (rctx->pad_mode) {
      case RSA_PKCS1_PADDING:
        if (!RSA_sign(EVP_MD_type(rctx->md), tbs, tbslen, sig, &out_len, rsa)) {
          return 0;
        }
        *siglen = out_len;
        return 1;

      case RSA_PKCS1_PSS_PADDING:
        if (!setup_tbuf(rctx, ctx) ||
            !RSA_padding_add_PKCS1_PSS_mgf1(rsa, rctx->tbuf, tbs, rctx->md,
                                            rctx->mgf1md, rctx->saltlen) ||
            !RSA_sign_raw(rsa, siglen, sig, *siglen, rctx->tbuf, key_len,
                          RSA_NO_PADDING)) {
          return 0;
        }
        return 1;

      default:
        return 0;
    }
  }

  return RSA_sign_raw(rsa, siglen, sig, *siglen, tbs, tbslen, rctx->pad_mode);
}

static int pkey_rsa_verify(EVP_PKEY_CTX *ctx, const uint8_t *sig,
                           size_t siglen, const uint8_t *tbs,
                           size_t tbslen) {
  RSA_PKEY_CTX *rctx = ctx->data;
  RSA *rsa = ctx->pkey->pkey.rsa;
  size_t rslen;
  const size_t key_len = EVP_PKEY_size(ctx->pkey);

  if (rctx->md) {
    switch (rctx->pad_mode) {
      case RSA_PKCS1_PADDING:
        return RSA_verify(EVP_MD_type(rctx->md), tbs, tbslen, sig, siglen, rsa);

      case RSA_PKCS1_PSS_PADDING:
        if (!setup_tbuf(rctx, ctx) ||
            !RSA_verify_raw(rsa, &rslen, rctx->tbuf, key_len, sig, siglen,
                            RSA_NO_PADDING) ||
            !RSA_verify_PKCS1_PSS_mgf1(rsa, tbs, rctx->md, rctx->mgf1md,
                                       rctx->tbuf, rctx->saltlen)) {
          return 0;
        }
        return 1;

      default:
        return 0;
    }
  }

  if (!setup_tbuf(rctx, ctx) ||
      !RSA_verify_raw(rsa, &rslen, rctx->tbuf, key_len, sig, siglen,
                      rctx->pad_mode) ||
      rslen != tbslen ||
      CRYPTO_memcmp(tbs, rctx->tbuf, rslen) != 0) {
    return 0;
  }

  return 1;
}

static int pkey_rsa_encrypt(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                            const uint8_t *in, size_t inlen) {
  RSA_PKEY_CTX *rctx = ctx->data;
  RSA *rsa = ctx->pkey->pkey.rsa;
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

static int pkey_rsa_decrypt(EVP_PKEY_CTX *ctx, uint8_t *out,
                            size_t *outlen, const uint8_t *in,
                            size_t inlen) {
  RSA_PKEY_CTX *rctx = ctx->data;
  RSA *rsa = ctx->pkey->pkey.rsa;
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
    size_t plaintext_len;
    int message_len;

    if (!setup_tbuf(rctx, ctx) ||
        !RSA_decrypt(rsa, &plaintext_len, rctx->tbuf, key_len, in, inlen,
                     RSA_NO_PADDING)) {
      return 0;
    }

    message_len = RSA_padding_check_PKCS1_OAEP_mgf1(
        out, key_len, rctx->tbuf, plaintext_len, rctx->oaep_label,
        rctx->oaep_labellen, rctx->md, rctx->mgf1md);
    if (message_len < 0) {
      return 0;
    }
    *outlen = message_len;
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
  RSA_PKEY_CTX *rctx = ctx->data;
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
      rctx->pub_exp = p2;
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
        rctx->md = p2;
      }
      return 1;

    case EVP_PKEY_CTRL_MD:
      if (!check_padding_md(p2, rctx->pad_mode)) {
        return 0;
      }
      rctx->md = p2;
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
        rctx->mgf1md = p2;
      }
      return 1;

    case EVP_PKEY_CTRL_RSA_OAEP_LABEL:
      if (rctx->pad_mode != RSA_PKCS1_OAEP_PADDING) {
        OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PADDING_MODE);
        return 0;
      }
      OPENSSL_free(rctx->oaep_label);
      if (p2 && p1 > 0) {
        /* TODO(fork): this seems wrong. Shouldn't it take a copy of the
         * buffer? */
        rctx->oaep_label = p2;
        rctx->oaep_labellen = p1;
      } else {
        rctx->oaep_label = NULL;
        rctx->oaep_labellen = 0;
      }
      return 1;

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
  RSA_PKEY_CTX *rctx = ctx->data;

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
    EVP_PKEY_RSA,         0 /* flags */,        pkey_rsa_init,
    pkey_rsa_copy,        pkey_rsa_cleanup,     0 /* paramgen_init */,
    0 /* paramgen */,     0 /* keygen_init */,  pkey_rsa_keygen,
    0 /* sign_init */,    pkey_rsa_sign,        0 /* verify_init */,
    pkey_rsa_verify,      0 /* encrypt_init */, pkey_rsa_encrypt,
    0 /* decrypt_init */, pkey_rsa_decrypt,     0 /* derive_init */,
    0 /* derive */,       pkey_rsa_ctrl,
};

int EVP_PKEY_CTX_set_rsa_padding(EVP_PKEY_CTX *ctx, int padding) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, -1, EVP_PKEY_CTRL_RSA_PADDING,
                           padding, NULL);
}

int EVP_PKEY_CTX_get_rsa_padding(EVP_PKEY_CTX *ctx, int *out_padding) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, -1, EVP_PKEY_CTRL_GET_RSA_PADDING,
                           0, out_padding);
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
                           EVP_PKEY_CTRL_GET_RSA_OAEP_MD, 0, (void*) out_md);
}

int EVP_PKEY_CTX_set_rsa_mgf1_md(EVP_PKEY_CTX *ctx, const EVP_MD *md) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA,
                           EVP_PKEY_OP_TYPE_SIG | EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_RSA_MGF1_MD, 0, (void*) md);
}

int EVP_PKEY_CTX_get_rsa_mgf1_md(EVP_PKEY_CTX *ctx, const EVP_MD **out_md) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA,
                           EVP_PKEY_OP_TYPE_SIG | EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_GET_RSA_MGF1_MD, 0, (void*) out_md);
}

int EVP_PKEY_CTX_set0_rsa_oaep_label(EVP_PKEY_CTX *ctx, const uint8_t *label,
                                     size_t label_len) {
  int label_len_int = label_len;
  if (((size_t) label_len_int) != label_len) {
    return 0;
  }

  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_RSA, EVP_PKEY_OP_TYPE_CRYPT,
                           EVP_PKEY_CTRL_RSA_OAEP_LABEL, label_len,
                           (void *)label);
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
