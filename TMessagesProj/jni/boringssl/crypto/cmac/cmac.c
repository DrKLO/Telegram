/* ====================================================================
 * Copyright (c) 2010 The OpenSSL Project.  All rights reserved.
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
 * ==================================================================== */

#include <openssl/cmac.h>

#include <assert.h>
#include <string.h>

#include <openssl/aes.h>
#include <openssl/cipher.h>
#include <openssl/mem.h>

#include "../internal.h"


struct cmac_ctx_st {
  EVP_CIPHER_CTX cipher_ctx;
  // k1 and k2 are the CMAC subkeys. See
  // https://tools.ietf.org/html/rfc4493#section-2.3
  uint8_t k1[AES_BLOCK_SIZE];
  uint8_t k2[AES_BLOCK_SIZE];
  // Last (possibly partial) scratch
  uint8_t block[AES_BLOCK_SIZE];
  // block_used contains the number of valid bytes in |block|.
  unsigned block_used;
};

static void CMAC_CTX_init(CMAC_CTX *ctx) {
  EVP_CIPHER_CTX_init(&ctx->cipher_ctx);
}

static void CMAC_CTX_cleanup(CMAC_CTX *ctx) {
  EVP_CIPHER_CTX_cleanup(&ctx->cipher_ctx);
  OPENSSL_cleanse(ctx->k1, sizeof(ctx->k1));
  OPENSSL_cleanse(ctx->k2, sizeof(ctx->k2));
  OPENSSL_cleanse(ctx->block, sizeof(ctx->block));
}

int AES_CMAC(uint8_t out[16], const uint8_t *key, size_t key_len,
             const uint8_t *in, size_t in_len) {
  const EVP_CIPHER *cipher;
  switch (key_len) {
    case 16:
      cipher = EVP_aes_128_cbc();
      break;
    case 32:
      cipher = EVP_aes_256_cbc();
      break;
    default:
      return 0;
  }

  size_t scratch_out_len;
  CMAC_CTX ctx;
  CMAC_CTX_init(&ctx);

  const int ok = CMAC_Init(&ctx, key, key_len, cipher, NULL /* engine */) &&
                 CMAC_Update(&ctx, in, in_len) &&
                 CMAC_Final(&ctx, out, &scratch_out_len);

  CMAC_CTX_cleanup(&ctx);
  return ok;
}

CMAC_CTX *CMAC_CTX_new(void) {
  CMAC_CTX *ctx = OPENSSL_malloc(sizeof(*ctx));
  if (ctx != NULL) {
    CMAC_CTX_init(ctx);
  }
  return ctx;
}

void CMAC_CTX_free(CMAC_CTX *ctx) {
  if (ctx == NULL) {
    return;
  }

  CMAC_CTX_cleanup(ctx);
  OPENSSL_free(ctx);
}

// binary_field_mul_x treats the 128 bits at |in| as an element of GF(2¹²⁸)
// with a hard-coded reduction polynomial and sets |out| as x times the
// input.
//
// See https://tools.ietf.org/html/rfc4493#section-2.3
static void binary_field_mul_x(uint8_t out[16], const uint8_t in[16]) {
  unsigned i;

  // Shift |in| to left, including carry.
  for (i = 0; i < 15; i++) {
    out[i] = (in[i] << 1) | (in[i+1] >> 7);
  }

  // If MSB set fixup with R.
  const uint8_t carry = in[0] >> 7;
  out[i] = (in[i] << 1) ^ ((0 - carry) & 0x87);
}

static const uint8_t kZeroIV[AES_BLOCK_SIZE] = {0};

int CMAC_Init(CMAC_CTX *ctx, const void *key, size_t key_len,
              const EVP_CIPHER *cipher, ENGINE *engine) {
  uint8_t scratch[AES_BLOCK_SIZE];

  if (EVP_CIPHER_block_size(cipher) != AES_BLOCK_SIZE ||
      EVP_CIPHER_key_length(cipher) != key_len ||
      !EVP_EncryptInit_ex(&ctx->cipher_ctx, cipher, NULL, key, kZeroIV) ||
      !EVP_Cipher(&ctx->cipher_ctx, scratch, kZeroIV, AES_BLOCK_SIZE) ||
      // Reset context again ready for first data.
      !EVP_EncryptInit_ex(&ctx->cipher_ctx, NULL, NULL, NULL, kZeroIV)) {
    return 0;
  }

  binary_field_mul_x(ctx->k1, scratch);
  binary_field_mul_x(ctx->k2, ctx->k1);
  ctx->block_used = 0;

  return 1;
}

int CMAC_Reset(CMAC_CTX *ctx) {
  ctx->block_used = 0;
  return EVP_EncryptInit_ex(&ctx->cipher_ctx, NULL, NULL, NULL, kZeroIV);
}

int CMAC_Update(CMAC_CTX *ctx, const uint8_t *in, size_t in_len) {
  uint8_t scratch[AES_BLOCK_SIZE];

  if (ctx->block_used > 0) {
    size_t todo = AES_BLOCK_SIZE - ctx->block_used;
    if (in_len < todo) {
      todo = in_len;
    }

    OPENSSL_memcpy(ctx->block + ctx->block_used, in, todo);
    in += todo;
    in_len -= todo;
    ctx->block_used += todo;

    // If |in_len| is zero then either |ctx->block_used| is less than
    // |AES_BLOCK_SIZE|, in which case we can stop here, or |ctx->block_used|
    // is exactly |AES_BLOCK_SIZE| but there's no more data to process. In the
    // latter case we don't want to process this block now because it might be
    // the last block and that block is treated specially.
    if (in_len == 0) {
      return 1;
    }

    assert(ctx->block_used == AES_BLOCK_SIZE);

    if (!EVP_Cipher(&ctx->cipher_ctx, scratch, ctx->block, AES_BLOCK_SIZE)) {
      return 0;
    }
  }

  // Encrypt all but one of the remaining blocks.
  while (in_len > AES_BLOCK_SIZE) {
    if (!EVP_Cipher(&ctx->cipher_ctx, scratch, in, AES_BLOCK_SIZE)) {
      return 0;
    }
    in += AES_BLOCK_SIZE;
    in_len -= AES_BLOCK_SIZE;
  }

  OPENSSL_memcpy(ctx->block, in, in_len);
  ctx->block_used = in_len;

  return 1;
}

int CMAC_Final(CMAC_CTX *ctx, uint8_t *out, size_t *out_len) {
  *out_len = AES_BLOCK_SIZE;
  if (out == NULL) {
    return 1;
  }

  const uint8_t *mask = ctx->k1;

  if (ctx->block_used != AES_BLOCK_SIZE) {
    // If the last block is incomplete, terminate it with a single 'one' bit
    // followed by zeros.
    ctx->block[ctx->block_used] = 0x80;
    OPENSSL_memset(ctx->block + ctx->block_used + 1, 0,
                   AES_BLOCK_SIZE - (ctx->block_used + 1));

    mask = ctx->k2;
  }

  unsigned i;
  for (i = 0; i < AES_BLOCK_SIZE; i++) {
    out[i] = ctx->block[i] ^ mask[i];
  }

  return EVP_Cipher(&ctx->cipher_ctx, out, out, AES_BLOCK_SIZE);
}
