// Copyright 2006-2019 The OpenSSL Project Authors. All Rights Reserved.
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

#include <assert.h>

#include <openssl/dh.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"


namespace {
typedef struct dh_pkey_ctx_st {
  int pad;
} DH_PKEY_CTX;
}  // namespace

static int pkey_dh_init(EVP_PKEY_CTX *ctx) {
  DH_PKEY_CTX *dctx =
      reinterpret_cast<DH_PKEY_CTX *>(OPENSSL_zalloc(sizeof(DH_PKEY_CTX)));
  if (dctx == NULL) {
    return 0;
  }

  ctx->data = dctx;
  return 1;
}

static int pkey_dh_copy(EVP_PKEY_CTX *dst, EVP_PKEY_CTX *src) {
  if (!pkey_dh_init(dst)) {
    return 0;
  }

  const DH_PKEY_CTX *sctx = reinterpret_cast<DH_PKEY_CTX *>(src->data);
  DH_PKEY_CTX *dctx = reinterpret_cast<DH_PKEY_CTX *>(dst->data);
  dctx->pad = sctx->pad;
  return 1;
}

static void pkey_dh_cleanup(EVP_PKEY_CTX *ctx) {
  OPENSSL_free(ctx->data);
  ctx->data = NULL;
}

static int pkey_dh_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  DH *dh = DH_new();
  if (dh == NULL || !EVP_PKEY_assign_DH(pkey, dh)) {
    DH_free(dh);
    return 0;
  }

  if (ctx->pkey != NULL && !EVP_PKEY_copy_parameters(pkey, ctx->pkey)) {
    return 0;
  }

  return DH_generate_key(dh);
}

static int pkey_dh_derive(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *out_len) {
  DH_PKEY_CTX *dctx = reinterpret_cast<DH_PKEY_CTX *>(ctx->data);
  if (ctx->pkey == NULL || ctx->peerkey == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  DH *our_key = reinterpret_cast<DH *>(ctx->pkey->pkey);
  DH *peer_key = reinterpret_cast<DH *>(ctx->peerkey->pkey);
  if (our_key == NULL || peer_key == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  const BIGNUM *pub_key = DH_get0_pub_key(peer_key);
  if (pub_key == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  if (out == NULL) {
    *out_len = DH_size(our_key);
    return 1;
  }

  if (*out_len < (size_t)DH_size(our_key)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  int ret = dctx->pad ? DH_compute_key_padded(out, pub_key, our_key)
                      : DH_compute_key(out, pub_key, our_key);
  if (ret < 0) {
    return 0;
  }

  assert(ret <= DH_size(our_key));
  *out_len = (size_t)ret;
  return 1;
}

static int pkey_dh_ctrl(EVP_PKEY_CTX *ctx, int type, int p1, void *p2) {
  DH_PKEY_CTX *dctx = reinterpret_cast<DH_PKEY_CTX *>(ctx->data);
  switch (type) {
    case EVP_PKEY_CTRL_PEER_KEY:
      // |EVP_PKEY_derive_set_peer| requires the key implement this command,
      // even if it is a no-op.
      return 1;

    case EVP_PKEY_CTRL_DH_PAD:
      dctx->pad = p1;
      return 1;

    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_COMMAND_NOT_SUPPORTED);
      return 0;
  }
}

const EVP_PKEY_METHOD dh_pkey_meth = {
    /*pkey_id=*/EVP_PKEY_DH,
    /*init=*/pkey_dh_init,
    /*copy=*/pkey_dh_copy,
    /*cleanup=*/pkey_dh_cleanup,
    /*keygen=*/pkey_dh_keygen,
    /*sign=*/nullptr,
    /*sign_message=*/nullptr,
    /*verify=*/nullptr,
    /*verify_message=*/nullptr,
    /*verify_recover=*/nullptr,
    /*encrypt=*/nullptr,
    /*decrypt=*/nullptr,
    /*derive=*/pkey_dh_derive,
    /*paramgen=*/nullptr,
    /*ctrl=*/pkey_dh_ctrl,
};

int EVP_PKEY_CTX_set_dh_pad(EVP_PKEY_CTX *ctx, int pad) {
  return EVP_PKEY_CTX_ctrl(ctx, EVP_PKEY_DH, EVP_PKEY_OP_DERIVE,
                           EVP_PKEY_CTRL_DH_PAD, pad, NULL);
}
