// Copyright (c) 2017, Google Inc.
//
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
// OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <openssl/cipher.h>

#include <string.h>

#include <openssl/aes.h>
#include <openssl/obj.h>

#include "../../crypto/internal.h"

typedef struct {
  AES_KEY ks;
} EVP_CFB_CTX;

static int aes_cfb_init_key(EVP_CIPHER_CTX *ctx, const uint8_t *key,
                            const uint8_t *iv, int enc) {
  if (key) {
    EVP_CFB_CTX *cfb_ctx = ctx->cipher_data;
    AES_set_encrypt_key(key, ctx->key_len * 8, &cfb_ctx->ks);
  }

  return 1;
}

static int aes_cfb128_cipher(EVP_CIPHER_CTX *ctx, uint8_t *out,
                             const uint8_t *in, size_t len) {
  if (!out || !in) {
    return 0;
  }

  EVP_CFB_CTX *cfb_ctx = ctx->cipher_data;
  int num = ctx->num;
  AES_cfb128_encrypt(in, out, len, &cfb_ctx->ks, ctx->iv, &num,
                     ctx->encrypt ? AES_ENCRYPT : AES_DECRYPT);
  ctx->num = num;

  return 1;
}

static const EVP_CIPHER aes_128_cfb128 = {
    NID_aes_128_cfb128,  1 /* block_size */,  16 /* key_size */,
    16 /* iv_len */,     sizeof(EVP_CFB_CTX), EVP_CIPH_CFB_MODE,
    NULL /* app_data */, aes_cfb_init_key,    aes_cfb128_cipher,
    NULL /* cleanup */,  NULL /* ctrl */,
};

static const EVP_CIPHER aes_256_cfb128 = {
    NID_aes_256_cfb128,  1 /* block_size */,  32 /* key_size */,
    16 /* iv_len */,     sizeof(EVP_CFB_CTX), EVP_CIPH_CFB_MODE,
    NULL /* app_data */, aes_cfb_init_key,    aes_cfb128_cipher,
    NULL /* cleanup */,  NULL /* ctrl */,
};

const EVP_CIPHER *EVP_aes_128_cfb128(void) { return &aes_128_cfb128; }
const EVP_CIPHER *EVP_aes_256_cfb128(void) { return &aes_256_cfb128; }
