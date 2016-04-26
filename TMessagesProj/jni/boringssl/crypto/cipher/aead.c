/* Copyright (c) 2014, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <openssl/aead.h>

#include <string.h>

#include <openssl/cipher.h>
#include <openssl/err.h>

#include "internal.h"


size_t EVP_AEAD_key_length(const EVP_AEAD *aead) { return aead->key_len; }

size_t EVP_AEAD_nonce_length(const EVP_AEAD *aead) { return aead->nonce_len; }

size_t EVP_AEAD_max_overhead(const EVP_AEAD *aead) { return aead->overhead; }

size_t EVP_AEAD_max_tag_len(const EVP_AEAD *aead) { return aead->max_tag_len; }

int EVP_AEAD_CTX_init(EVP_AEAD_CTX *ctx, const EVP_AEAD *aead,
                      const uint8_t *key, size_t key_len, size_t tag_len,
                      ENGINE *impl) {
  if (!aead->init) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_NO_DIRECTION_SET);
    ctx->aead = NULL;
    return 0;
  }
  return EVP_AEAD_CTX_init_with_direction(ctx, aead, key, key_len, tag_len,
                                          evp_aead_open);
}

int EVP_AEAD_CTX_init_with_direction(EVP_AEAD_CTX *ctx, const EVP_AEAD *aead,
                                     const uint8_t *key, size_t key_len,
                                     size_t tag_len,
                                     enum evp_aead_direction_t dir) {
  if (key_len != aead->key_len) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_UNSUPPORTED_KEY_SIZE);
    ctx->aead = NULL;
    return 0;
  }

  ctx->aead = aead;

  int ok;
  if (aead->init) {
    ok = aead->init(ctx, key, key_len, tag_len);
  } else {
    ok = aead->init_with_direction(ctx, key, key_len, tag_len, dir);
  }

  if (!ok) {
    ctx->aead = NULL;
  }

  return ok;
}

void EVP_AEAD_CTX_cleanup(EVP_AEAD_CTX *ctx) {
  if (ctx->aead == NULL) {
    return;
  }
  ctx->aead->cleanup(ctx);
  ctx->aead = NULL;
}

/* check_alias returns 0 if |out| points within the buffer determined by |in|
 * and |in_len| and 1 otherwise.
 *
 * When processing, there's only an issue if |out| points within in[:in_len]
 * and isn't equal to |in|. If that's the case then writing the output will
 * stomp input that hasn't been read yet.
 *
 * This function checks for that case. */
static int check_alias(const uint8_t *in, size_t in_len, const uint8_t *out) {
  if (out <= in) {
    return 1;
  } else if (in + in_len <= out) {
    return 1;
  }
  return 0;
}

int EVP_AEAD_CTX_seal(const EVP_AEAD_CTX *ctx, uint8_t *out, size_t *out_len,
                      size_t max_out_len, const uint8_t *nonce,
                      size_t nonce_len, const uint8_t *in, size_t in_len,
                      const uint8_t *ad, size_t ad_len) {
  size_t possible_out_len = in_len + ctx->aead->overhead;

  if (possible_out_len < in_len /* overflow */) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    goto error;
  }

  if (!check_alias(in, in_len, out)) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_OUTPUT_ALIASES_INPUT);
    goto error;
  }

  if (ctx->aead->seal(ctx, out, out_len, max_out_len, nonce, nonce_len, in,
                      in_len, ad, ad_len)) {
    return 1;
  }

error:
  /* In the event of an error, clear the output buffer so that a caller
   * that doesn't check the return value doesn't send raw data. */
  memset(out, 0, max_out_len);
  *out_len = 0;
  return 0;
}

int EVP_AEAD_CTX_open(const EVP_AEAD_CTX *ctx, uint8_t *out, size_t *out_len,
                      size_t max_out_len, const uint8_t *nonce,
                      size_t nonce_len, const uint8_t *in, size_t in_len,
                      const uint8_t *ad, size_t ad_len) {
  if (!check_alias(in, in_len, out)) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_OUTPUT_ALIASES_INPUT);
    goto error;
  }

  if (ctx->aead->open(ctx, out, out_len, max_out_len, nonce, nonce_len, in,
                      in_len, ad, ad_len)) {
    return 1;
  }

error:
  /* In the event of an error, clear the output buffer so that a caller
   * that doesn't check the return value doesn't try and process bad
   * data. */
  memset(out, 0, max_out_len);
  *out_len = 0;
  return 0;
}

int EVP_AEAD_CTX_get_rc4_state(const EVP_AEAD_CTX *ctx, const RC4_KEY **out_key) {
  if (ctx->aead->get_rc4_state == NULL) {
    return 0;
  }

  return ctx->aead->get_rc4_state(ctx, out_key);
}
