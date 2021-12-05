/* Copyright (c) 2019, Google Inc.
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

#include <openssl/evp.h>

#include <openssl/curve25519.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"


// X25519 has no parameters to copy.
static int pkey_x25519_copy(EVP_PKEY_CTX *dst, EVP_PKEY_CTX *src) { return 1; }

static int pkey_x25519_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey) {
  X25519_KEY *key = OPENSSL_malloc(sizeof(X25519_KEY));
  if (key == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  if (!EVP_PKEY_set_type(pkey, EVP_PKEY_X25519)) {
    OPENSSL_free(key);
    return 0;
  }

  X25519_keypair(key->pub, key->priv);
  key->has_private = 1;

  OPENSSL_free(pkey->pkey.ptr);
  pkey->pkey.ptr = key;
  return 1;
}

static int pkey_x25519_derive(EVP_PKEY_CTX *ctx, uint8_t *out,
                              size_t *out_len) {
  if (ctx->pkey == NULL || ctx->peerkey == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  const X25519_KEY *our_key = ctx->pkey->pkey.ptr;
  const X25519_KEY *peer_key = ctx->peerkey->pkey.ptr;
  if (our_key == NULL || peer_key == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_KEYS_NOT_SET);
    return 0;
  }

  if (!our_key->has_private) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NOT_A_PRIVATE_KEY);
    return 0;
  }

  if (out != NULL) {
    if (*out_len < 32) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
      return 0;
    }
    if (!X25519(out, our_key->priv, peer_key->pub)) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PEER_KEY);
      return 0;
    }
  }

  *out_len = 32;
  return 1;
}

static int pkey_x25519_ctrl(EVP_PKEY_CTX *ctx, int type, int p1, void *p2) {
  switch (type) {
    case EVP_PKEY_CTRL_PEER_KEY:
      // |EVP_PKEY_derive_set_peer| requires the key implement this command,
      // even if it is a no-op.
      return 1;

    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_COMMAND_NOT_SUPPORTED);
      return 0;
  }
}

const EVP_PKEY_METHOD x25519_pkey_meth = {
    EVP_PKEY_X25519,
    NULL /* init */,
    pkey_x25519_copy,
    NULL /* cleanup */,
    pkey_x25519_keygen,
    NULL /* sign */,
    NULL /* sign_message */,
    NULL /* verify */,
    NULL /* verify_message */,
    NULL /* verify_recover */,
    NULL /* encrypt */,
    NULL /* decrypt */,
    pkey_x25519_derive,
    NULL /* paramgen */,
    pkey_x25519_ctrl,
};
