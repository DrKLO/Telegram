/* ====================================================================
 * Copyright (c) 2011 The OpenSSL Project.  All rights reserved.
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
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
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
 * ==================================================================== */

#include <openssl/evp.h>

#include <string.h>

#include <openssl/aes.h>
#include <openssl/cipher.h>

#include "../crypto/fipsmodule/modes/internal.h"


typedef struct xts128_context {
  AES_KEY *key1, *key2;
  block128_f block1, block2;
} XTS128_CONTEXT;

static size_t CRYPTO_xts128_encrypt(const XTS128_CONTEXT *ctx,
                                    const uint8_t iv[16], const uint8_t *inp,
                                    uint8_t *out, size_t len, int enc) {
  union {
    uint64_t u[2];
    uint32_t d[4];
    uint8_t c[16];
  } tweak, scratch;
  unsigned int i;

  if (len < 16) return 0;

  OPENSSL_memcpy(tweak.c, iv, 16);

  (*ctx->block2)(tweak.c, tweak.c, ctx->key2);

  if (!enc && (len % 16)) len -= 16;

  while (len >= 16) {
    OPENSSL_memcpy(scratch.c, inp, 16);
    scratch.u[0] ^= tweak.u[0];
    scratch.u[1] ^= tweak.u[1];
    (*ctx->block1)(scratch.c, scratch.c, ctx->key1);
    scratch.u[0] ^= tweak.u[0];
    scratch.u[1] ^= tweak.u[1];
    OPENSSL_memcpy(out, scratch.c, 16);
    inp += 16;
    out += 16;
    len -= 16;

    if (len == 0) return 1;

    unsigned int carry, res;

    res = 0x87 & (((int)tweak.d[3]) >> 31);
    carry = (unsigned int)(tweak.u[0] >> 63);
    tweak.u[0] = (tweak.u[0] << 1) ^ res;
    tweak.u[1] = (tweak.u[1] << 1) | carry;
  }
  if (enc) {
    for (i = 0; i < len; ++i) {
      uint8_t c = inp[i];
      out[i] = scratch.c[i];
      scratch.c[i] = c;
    }
    scratch.u[0] ^= tweak.u[0];
    scratch.u[1] ^= tweak.u[1];
    (*ctx->block1)(scratch.c, scratch.c, ctx->key1);
    scratch.u[0] ^= tweak.u[0];
    scratch.u[1] ^= tweak.u[1];
    OPENSSL_memcpy(out - 16, scratch.c, 16);
  } else {
    union {
      uint64_t u[2];
      uint8_t c[16];
    } tweak1;

    unsigned int carry, res;

    res = 0x87 & (((int)tweak.d[3]) >> 31);
    carry = (unsigned int)(tweak.u[0] >> 63);
    tweak1.u[0] = (tweak.u[0] << 1) ^ res;
    tweak1.u[1] = (tweak.u[1] << 1) | carry;
    OPENSSL_memcpy(scratch.c, inp, 16);
    scratch.u[0] ^= tweak1.u[0];
    scratch.u[1] ^= tweak1.u[1];
    (*ctx->block1)(scratch.c, scratch.c, ctx->key1);
    scratch.u[0] ^= tweak1.u[0];
    scratch.u[1] ^= tweak1.u[1];

    for (i = 0; i < len; ++i) {
      uint8_t c = inp[16 + i];
      out[16 + i] = scratch.c[i];
      scratch.c[i] = c;
    }
    scratch.u[0] ^= tweak.u[0];
    scratch.u[1] ^= tweak.u[1];
    (*ctx->block1)(scratch.c, scratch.c, ctx->key1);
    scratch.u[0] ^= tweak.u[0];
    scratch.u[1] ^= tweak.u[1];
    OPENSSL_memcpy(out, scratch.c, 16);
  }

  return 1;
}

typedef struct {
  union {
    double align;
    AES_KEY ks;
  } ks1, ks2;  // AES key schedules to use
  XTS128_CONTEXT xts;
} EVP_AES_XTS_CTX;

static int aes_xts_init_key(EVP_CIPHER_CTX *ctx, const uint8_t *key,
                            const uint8_t *iv, int enc) {
  EVP_AES_XTS_CTX *xctx = ctx->cipher_data;
  if (!iv && !key) {
    return 1;
  }

  if (key) {
    // key_len is two AES keys
    if (enc) {
      AES_set_encrypt_key(key, ctx->key_len * 4, &xctx->ks1.ks);
      xctx->xts.block1 = AES_encrypt;
    } else {
      AES_set_decrypt_key(key, ctx->key_len * 4, &xctx->ks1.ks);
      xctx->xts.block1 = AES_decrypt;
    }

    AES_set_encrypt_key(key + ctx->key_len / 2,
                        ctx->key_len * 4, &xctx->ks2.ks);
    xctx->xts.block2 = AES_encrypt;
    xctx->xts.key1 = &xctx->ks1.ks;
  }

  if (iv) {
    xctx->xts.key2 = &xctx->ks2.ks;
    OPENSSL_memcpy(ctx->iv, iv, 16);
  }

  return 1;
}

static int aes_xts_cipher(EVP_CIPHER_CTX *ctx, uint8_t *out,
                          const uint8_t *in, size_t len) {
  EVP_AES_XTS_CTX *xctx = ctx->cipher_data;
  if (!xctx->xts.key1 ||
      !xctx->xts.key2 ||
      !out ||
      !in ||
      len < AES_BLOCK_SIZE ||
      !CRYPTO_xts128_encrypt(&xctx->xts, ctx->iv, in, out, len, ctx->encrypt)) {
    return 0;
  }
  return 1;
}

static int aes_xts_ctrl(EVP_CIPHER_CTX *c, int type, int arg, void *ptr) {
  EVP_AES_XTS_CTX *xctx = c->cipher_data;
  if (type == EVP_CTRL_COPY) {
    EVP_CIPHER_CTX *out = ptr;
    EVP_AES_XTS_CTX *xctx_out = out->cipher_data;
    if (xctx->xts.key1) {
      if (xctx->xts.key1 != &xctx->ks1.ks) {
        return 0;
      }
      xctx_out->xts.key1 = &xctx_out->ks1.ks;
    }
    if (xctx->xts.key2) {
      if (xctx->xts.key2 != &xctx->ks2.ks) {
        return 0;
      }
      xctx_out->xts.key2 = &xctx_out->ks2.ks;
    }
    return 1;
  } else if (type != EVP_CTRL_INIT) {
    return -1;
  }
  // key1 and key2 are used as an indicator both key and IV are set
  xctx->xts.key1 = NULL;
  xctx->xts.key2 = NULL;
  return 1;
}

static const EVP_CIPHER aes_256_xts = {
    NID_aes_256_xts,     1 /* block_size */,  64 /* key_size (2 AES keys) */,
    16 /* iv_len */,     sizeof(EVP_AES_XTS_CTX),
    EVP_CIPH_XTS_MODE | EVP_CIPH_CUSTOM_IV | EVP_CIPH_ALWAYS_CALL_INIT |
        EVP_CIPH_CTRL_INIT | EVP_CIPH_CUSTOM_COPY,
    NULL /* app_data */, aes_xts_init_key,    aes_xts_cipher,
    NULL /* cleanup */,  aes_xts_ctrl};

const EVP_CIPHER *EVP_aes_256_xts(void) { return &aes_256_xts; }
