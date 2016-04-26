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

#include <openssl/evp.h>

#include <stdio.h>
#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>

#include "internal.h"


extern const EVP_PKEY_METHOD rsa_pkey_meth;
extern const EVP_PKEY_METHOD ec_pkey_meth;

static const EVP_PKEY_METHOD *const evp_methods[] = {
  &rsa_pkey_meth,
  &ec_pkey_meth,
};

static const EVP_PKEY_METHOD *evp_pkey_meth_find(int type) {
  unsigned i;

  for (i = 0; i < sizeof(evp_methods)/sizeof(EVP_PKEY_METHOD*); i++) {
    if (evp_methods[i]->pkey_id == type) {
      return evp_methods[i];
    }
  }

  return NULL;
}

static EVP_PKEY_CTX *evp_pkey_ctx_new(EVP_PKEY *pkey, ENGINE *e, int id) {
  EVP_PKEY_CTX *ret;
  const EVP_PKEY_METHOD *pmeth;

  if (id == -1) {
    if (!pkey || !pkey->ameth) {
      return NULL;
    }
    id = pkey->ameth->pkey_id;
  }

  pmeth = evp_pkey_meth_find(id);

  if (pmeth == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    const char *name = OBJ_nid2sn(id);
    ERR_add_error_dataf("algorithm %d (%s)", id, name);
    return NULL;
  }

  ret = OPENSSL_malloc(sizeof(EVP_PKEY_CTX));
  if (!ret) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  memset(ret, 0, sizeof(EVP_PKEY_CTX));

  ret->engine = e;
  ret->pmeth = pmeth;
  ret->operation = EVP_PKEY_OP_UNDEFINED;

  if (pkey) {
    ret->pkey = EVP_PKEY_up_ref(pkey);
  }

  if (pmeth->init) {
    if (pmeth->init(ret) <= 0) {
      EVP_PKEY_free(ret->pkey);
      OPENSSL_free(ret);
      return NULL;
    }
  }

  return ret;
}

EVP_PKEY_CTX *EVP_PKEY_CTX_new(EVP_PKEY *pkey, ENGINE *e) {
  return evp_pkey_ctx_new(pkey, e, -1);
}

EVP_PKEY_CTX *EVP_PKEY_CTX_new_id(int id, ENGINE *e) {
  return evp_pkey_ctx_new(NULL, e, id);
}

void EVP_PKEY_CTX_free(EVP_PKEY_CTX *ctx) {
  if (ctx == NULL) {
    return;
  }
  if (ctx->pmeth && ctx->pmeth->cleanup) {
    ctx->pmeth->cleanup(ctx);
  }
  EVP_PKEY_free(ctx->pkey);
  EVP_PKEY_free(ctx->peerkey);
  OPENSSL_free(ctx);
}

EVP_PKEY_CTX *EVP_PKEY_CTX_dup(EVP_PKEY_CTX *pctx) {
  EVP_PKEY_CTX *rctx;

  if (!pctx->pmeth || !pctx->pmeth->copy) {
    return NULL;
  }

  rctx = OPENSSL_malloc(sizeof(EVP_PKEY_CTX));
  if (!rctx) {
    return NULL;
  }

  memset(rctx, 0, sizeof(EVP_PKEY_CTX));

  rctx->pmeth = pctx->pmeth;
  rctx->engine = pctx->engine;
  rctx->operation = pctx->operation;

  if (pctx->pkey) {
    rctx->pkey = EVP_PKEY_up_ref(pctx->pkey);
    if (rctx->pkey == NULL) {
      goto err;
    }
  }

  if (pctx->peerkey) {
    rctx->peerkey = EVP_PKEY_up_ref(pctx->peerkey);
    if (rctx->peerkey == NULL) {
      goto err;
    }
  }

  if (pctx->pmeth->copy(rctx, pctx) > 0) {
    return rctx;
  }

err:
  EVP_PKEY_CTX_free(rctx);
  OPENSSL_PUT_ERROR(EVP, ERR_LIB_EVP);
  return NULL;
}

EVP_PKEY *EVP_PKEY_CTX_get0_pkey(EVP_PKEY_CTX *ctx) { return ctx->pkey; }

void EVP_PKEY_CTX_set_app_data(EVP_PKEY_CTX *ctx, void *data) {
  ctx->app_data = data;
}

void *EVP_PKEY_CTX_get_app_data(EVP_PKEY_CTX *ctx) { return ctx->app_data; }

int EVP_PKEY_CTX_ctrl(EVP_PKEY_CTX *ctx, int keytype, int optype, int cmd,
                      int p1, void *p2) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->ctrl) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_COMMAND_NOT_SUPPORTED);
    return 0;
  }
  if (keytype != -1 && ctx->pmeth->pkey_id != keytype) {
    return 0;
  }

  if (ctx->operation == EVP_PKEY_OP_UNDEFINED) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NO_OPERATION_SET);
    return 0;
  }

  if (optype != -1 && !(ctx->operation & optype)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_OPERATION);
    return 0;
  }

  return ctx->pmeth->ctrl(ctx, cmd, p1, p2);
}

int EVP_PKEY_sign_init(EVP_PKEY_CTX *ctx) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->sign) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }

  ctx->operation = EVP_PKEY_OP_SIGN;
  if (!ctx->pmeth->sign_init) {
    return 1;
  }

  if (!ctx->pmeth->sign_init(ctx)) {
    ctx->operation = EVP_PKEY_OP_UNDEFINED;
    return 0;
  }

  return 1;
}

int EVP_PKEY_sign(EVP_PKEY_CTX *ctx, uint8_t *sig, size_t *sig_len,
                  const uint8_t *data, size_t data_len) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->sign) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_SIGN) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }
  return ctx->pmeth->sign(ctx, sig, sig_len, data, data_len);
}

int EVP_PKEY_verify_init(EVP_PKEY_CTX *ctx) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->verify) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  ctx->operation = EVP_PKEY_OP_VERIFY;
  if (!ctx->pmeth->verify_init) {
    return 1;
  }
  if (!ctx->pmeth->verify_init(ctx)) {
    ctx->operation = EVP_PKEY_OP_UNDEFINED;
    return 0;
  }

  return 1;
}

int EVP_PKEY_verify(EVP_PKEY_CTX *ctx, const uint8_t *sig, size_t sig_len,
                    const uint8_t *data, size_t data_len) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->verify) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_VERIFY) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }
  return ctx->pmeth->verify(ctx, sig, sig_len, data, data_len);
}

int EVP_PKEY_encrypt_init(EVP_PKEY_CTX *ctx) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->encrypt) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  ctx->operation = EVP_PKEY_OP_ENCRYPT;
  if (!ctx->pmeth->encrypt_init) {
    return 1;
  }
  if (!ctx->pmeth->encrypt_init(ctx)) {
    ctx->operation = EVP_PKEY_OP_UNDEFINED;
    return 0;
  }
  return 1;
}

int EVP_PKEY_encrypt(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                     const uint8_t *in, size_t inlen) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->encrypt) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_ENCRYPT) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }
  return ctx->pmeth->encrypt(ctx, out, outlen, in, inlen);
}

int EVP_PKEY_decrypt_init(EVP_PKEY_CTX *ctx) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->decrypt) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  ctx->operation = EVP_PKEY_OP_DECRYPT;
  if (!ctx->pmeth->decrypt_init) {
    return 1;
  }
  if (!ctx->pmeth->decrypt_init(ctx)) {
    ctx->operation = EVP_PKEY_OP_UNDEFINED;
    return 0;
  }
  return 1;
}

int EVP_PKEY_decrypt(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                     const uint8_t *in, size_t inlen) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->decrypt) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_DECRYPT) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }
  return ctx->pmeth->decrypt(ctx, out, outlen, in, inlen);
}

int EVP_PKEY_derive_init(EVP_PKEY_CTX *ctx) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->derive) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  ctx->operation = EVP_PKEY_OP_DERIVE;
  if (!ctx->pmeth->derive_init) {
    return 1;
  }
  if (!ctx->pmeth->derive_init(ctx)) {
    ctx->operation = EVP_PKEY_OP_UNDEFINED;
    return 0;
  }
  return 1;
}

int EVP_PKEY_derive_set_peer(EVP_PKEY_CTX *ctx, EVP_PKEY *peer) {
  int ret;
  if (!ctx || !ctx->pmeth ||
      !(ctx->pmeth->derive || ctx->pmeth->encrypt || ctx->pmeth->decrypt) ||
      !ctx->pmeth->ctrl) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_DERIVE &&
      ctx->operation != EVP_PKEY_OP_ENCRYPT &&
      ctx->operation != EVP_PKEY_OP_DECRYPT) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }

  ret = ctx->pmeth->ctrl(ctx, EVP_PKEY_CTRL_PEER_KEY, 0, peer);

  if (ret <= 0) {
    return 0;
  }

  if (ret == 2) {
    return 1;
  }

  if (!ctx->pkey) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NO_KEY_SET);
    return 0;
  }

  if (ctx->pkey->type != peer->type) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DIFFERENT_KEY_TYPES);
    return 0;
  }

  /* ran@cryptocom.ru: For clarity.  The error is if parameters in peer are
   * present (!missing) but don't match.  EVP_PKEY_cmp_parameters may return
   * 1 (match), 0 (don't match) and -2 (comparison is not defined).  -1
   * (different key types) is impossible here because it is checked earlier.
   * -2 is OK for us here, as well as 1, so we can check for 0 only. */
  if (!EVP_PKEY_missing_parameters(peer) &&
      !EVP_PKEY_cmp_parameters(ctx->pkey, peer)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DIFFERENT_PARAMETERS);
    return 0;
  }

  EVP_PKEY_free(ctx->peerkey);
  ctx->peerkey = peer;

  ret = ctx->pmeth->ctrl(ctx, EVP_PKEY_CTRL_PEER_KEY, 1, peer);

  if (ret <= 0) {
    ctx->peerkey = NULL;
    return 0;
  }

  EVP_PKEY_up_ref(peer);
  return 1;
}

int EVP_PKEY_derive(EVP_PKEY_CTX *ctx, uint8_t *key, size_t *out_key_len) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->derive) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_DERIVE) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }
  return ctx->pmeth->derive(ctx, key, out_key_len);
}

int EVP_PKEY_keygen_init(EVP_PKEY_CTX *ctx) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->keygen) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  ctx->operation = EVP_PKEY_OP_KEYGEN;
  if (!ctx->pmeth->keygen_init) {
    return 1;
  }
  if (!ctx->pmeth->keygen_init(ctx)) {
    ctx->operation = EVP_PKEY_OP_UNDEFINED;
    return 0;
  }
  return 1;
}

int EVP_PKEY_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY **ppkey) {
  if (!ctx || !ctx->pmeth || !ctx->pmeth->keygen) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATION_NOT_SUPPORTED_FOR_THIS_KEYTYPE);
    return 0;
  }
  if (ctx->operation != EVP_PKEY_OP_KEYGEN) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_OPERATON_NOT_INITIALIZED);
    return 0;
  }

  if (!ppkey) {
    return 0;
  }

  if (!*ppkey) {
    *ppkey = EVP_PKEY_new();
    if (!*ppkey) {
      OPENSSL_PUT_ERROR(EVP, ERR_LIB_EVP);
      return 0;
    }
  }

  if (!ctx->pmeth->keygen(ctx, *ppkey)) {
    EVP_PKEY_free(*ppkey);
    *ppkey = NULL;
    return 0;
  }
  return 1;
}
