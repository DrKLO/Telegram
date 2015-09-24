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

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/aead.h>
#include <openssl/cipher.h>
#include <openssl/err.h>
#include <openssl/hmac.h>
#include <openssl/md5.h>
#include <openssl/mem.h>
#include <openssl/sha.h>

#include "internal.h"


typedef struct {
  EVP_CIPHER_CTX cipher_ctx;
  EVP_MD_CTX md_ctx;
} AEAD_SSL3_CTX;

static int ssl3_mac(AEAD_SSL3_CTX *ssl3_ctx, uint8_t *out, unsigned *out_len,
                    const uint8_t *ad, size_t ad_len, const uint8_t *in,
                    size_t in_len) {
  size_t md_size = EVP_MD_CTX_size(&ssl3_ctx->md_ctx);
  size_t pad_len = (md_size == 20) ? 40 : 48;

  /* To allow for CBC mode which changes cipher length, |ad| doesn't include the
   * length for legacy ciphers. */
  uint8_t ad_extra[2];
  ad_extra[0] = (uint8_t)(in_len >> 8);
  ad_extra[1] = (uint8_t)(in_len & 0xff);

  EVP_MD_CTX md_ctx;
  EVP_MD_CTX_init(&md_ctx);

  uint8_t pad[48];
  uint8_t tmp[EVP_MAX_MD_SIZE];
  memset(pad, 0x36, pad_len);
  if (!EVP_MD_CTX_copy_ex(&md_ctx, &ssl3_ctx->md_ctx) ||
      !EVP_DigestUpdate(&md_ctx, pad, pad_len) ||
      !EVP_DigestUpdate(&md_ctx, ad, ad_len) ||
      !EVP_DigestUpdate(&md_ctx, ad_extra, sizeof(ad_extra)) ||
      !EVP_DigestUpdate(&md_ctx, in, in_len) ||
      !EVP_DigestFinal_ex(&md_ctx, tmp, NULL)) {
    EVP_MD_CTX_cleanup(&md_ctx);
    return 0;
  }

  memset(pad, 0x5c, pad_len);
  if (!EVP_MD_CTX_copy_ex(&md_ctx, &ssl3_ctx->md_ctx) ||
      !EVP_DigestUpdate(&md_ctx, pad, pad_len) ||
      !EVP_DigestUpdate(&md_ctx, tmp, md_size) ||
      !EVP_DigestFinal_ex(&md_ctx, out, out_len)) {
    EVP_MD_CTX_cleanup(&md_ctx);
    return 0;
  }
  EVP_MD_CTX_cleanup(&md_ctx);
  return 1;
}

static void aead_ssl3_cleanup(EVP_AEAD_CTX *ctx) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;
  EVP_CIPHER_CTX_cleanup(&ssl3_ctx->cipher_ctx);
  EVP_MD_CTX_cleanup(&ssl3_ctx->md_ctx);
  OPENSSL_free(ssl3_ctx);
  ctx->aead_state = NULL;
}

static int aead_ssl3_init(EVP_AEAD_CTX *ctx, const uint8_t *key, size_t key_len,
                          size_t tag_len, enum evp_aead_direction_t dir,
                          const EVP_CIPHER *cipher, const EVP_MD *md) {
  if (tag_len != EVP_AEAD_DEFAULT_TAG_LENGTH &&
      tag_len != EVP_MD_size(md)) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_UNSUPPORTED_TAG_SIZE);
    return 0;
  }

  if (key_len != EVP_AEAD_key_length(ctx->aead)) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_KEY_LENGTH);
    return 0;
  }

  size_t mac_key_len = EVP_MD_size(md);
  size_t enc_key_len = EVP_CIPHER_key_length(cipher);
  assert(mac_key_len + enc_key_len + EVP_CIPHER_iv_length(cipher) == key_len);
  /* Although EVP_rc4() is a variable-length cipher, the default key size is
   * correct for SSL3. */

  AEAD_SSL3_CTX *ssl3_ctx = OPENSSL_malloc(sizeof(AEAD_SSL3_CTX));
  if (ssl3_ctx == NULL) {
    OPENSSL_PUT_ERROR(CIPHER, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  EVP_CIPHER_CTX_init(&ssl3_ctx->cipher_ctx);
  EVP_MD_CTX_init(&ssl3_ctx->md_ctx);

  ctx->aead_state = ssl3_ctx;
  if (!EVP_CipherInit_ex(&ssl3_ctx->cipher_ctx, cipher, NULL, &key[mac_key_len],
                         &key[mac_key_len + enc_key_len],
                         dir == evp_aead_seal) ||
      !EVP_DigestInit_ex(&ssl3_ctx->md_ctx, md, NULL) ||
      !EVP_DigestUpdate(&ssl3_ctx->md_ctx, key, mac_key_len)) {
    aead_ssl3_cleanup(ctx);
    ctx->aead_state = NULL;
    return 0;
  }
  EVP_CIPHER_CTX_set_padding(&ssl3_ctx->cipher_ctx, 0);

  return 1;
}

static int aead_ssl3_seal(const EVP_AEAD_CTX *ctx, uint8_t *out,
                         size_t *out_len, size_t max_out_len,
                         const uint8_t *nonce, size_t nonce_len,
                         const uint8_t *in, size_t in_len,
                         const uint8_t *ad, size_t ad_len) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;
  size_t total = 0;

  if (!ssl3_ctx->cipher_ctx.encrypt) {
    /* Unlike a normal AEAD, an SSL3 AEAD may only be used in one direction. */
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_INVALID_OPERATION);
    return 0;
  }

  if (in_len + EVP_AEAD_max_overhead(ctx->aead) < in_len ||
      in_len > INT_MAX) {
    /* EVP_CIPHER takes int as input. */
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  if (max_out_len < in_len + EVP_AEAD_max_overhead(ctx->aead)) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (nonce_len != 0) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_IV_TOO_LARGE);
    return 0;
  }

  if (ad_len != 11 - 2 /* length bytes */) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_INVALID_AD_SIZE);
    return 0;
  }

  /* Compute the MAC. This must be first in case the operation is being done
   * in-place. */
  uint8_t mac[EVP_MAX_MD_SIZE];
  unsigned mac_len;
  if (!ssl3_mac(ssl3_ctx, mac, &mac_len, ad, ad_len, in, in_len)) {
    return 0;
  }

  /* Encrypt the input. */
  int len;
  if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, out, &len, in,
                         (int)in_len)) {
    return 0;
  }
  total = len;

  /* Feed the MAC into the cipher. */
  if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, out + total, &len, mac,
                         (int)mac_len)) {
    return 0;
  }
  total += len;

  unsigned block_size = EVP_CIPHER_CTX_block_size(&ssl3_ctx->cipher_ctx);
  if (block_size > 1) {
    assert(block_size <= 256);
    assert(EVP_CIPHER_CTX_mode(&ssl3_ctx->cipher_ctx) == EVP_CIPH_CBC_MODE);

    /* Compute padding and feed that into the cipher. */
    uint8_t padding[256];
    unsigned padding_len = block_size - ((in_len + mac_len) % block_size);
    memset(padding, 0, padding_len - 1);
    padding[padding_len - 1] = padding_len - 1;
    if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, out + total, &len, padding,
                           (int)padding_len)) {
      return 0;
    }
    total += len;
  }

  if (!EVP_EncryptFinal_ex(&ssl3_ctx->cipher_ctx, out + total, &len)) {
    return 0;
  }
  total += len;

  *out_len = total;
  return 1;
}

static int aead_ssl3_open(const EVP_AEAD_CTX *ctx, uint8_t *out,
                         size_t *out_len, size_t max_out_len,
                         const uint8_t *nonce, size_t nonce_len,
                         const uint8_t *in, size_t in_len,
                         const uint8_t *ad, size_t ad_len) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;

  if (ssl3_ctx->cipher_ctx.encrypt) {
    /* Unlike a normal AEAD, an SSL3 AEAD may only be used in one direction. */
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_INVALID_OPERATION);
    return 0;
  }

  size_t mac_len = EVP_MD_CTX_size(&ssl3_ctx->md_ctx);
  if (in_len < mac_len) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
    return 0;
  }

  if (max_out_len < in_len) {
    /* This requires that the caller provide space for the MAC, even though it
     * will always be removed on return. */
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (nonce_len != 0) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  if (ad_len != 11 - 2 /* length bytes */) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_INVALID_AD_SIZE);
    return 0;
  }

  if (in_len > INT_MAX) {
    /* EVP_CIPHER takes int as input. */
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  /* Decrypt to get the plaintext + MAC + padding. */
  size_t total = 0;
  int len;
  if (!EVP_DecryptUpdate(&ssl3_ctx->cipher_ctx, out, &len, in, (int)in_len)) {
    return 0;
  }
  total += len;
  if (!EVP_DecryptFinal_ex(&ssl3_ctx->cipher_ctx, out + total, &len)) {
    return 0;
  }
  total += len;
  assert(total == in_len);

  /* Remove CBC padding and MAC. This would normally be timing-sensitive, but SSLv3 CBC
   * ciphers are already broken. Support will be removed eventually.
   * https://www.openssl.org/~bodo/ssl-poodle.pdf */
  unsigned data_len;
  if (EVP_CIPHER_CTX_mode(&ssl3_ctx->cipher_ctx) == EVP_CIPH_CBC_MODE) {
    unsigned padding_length = out[total - 1];
    if (total < padding_length + 1 + mac_len) {
      OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
      return 0;
    }
    /* The padding must be minimal. */
    if (padding_length + 1 > EVP_CIPHER_CTX_block_size(&ssl3_ctx->cipher_ctx)) {
      OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
      return 0;
    }
    data_len = total - padding_length - 1 - mac_len;
  } else {
    data_len = total - mac_len;
  }

  /* Compute the MAC and compare against the one in the record. */
  uint8_t mac[EVP_MAX_MD_SIZE];
  if (!ssl3_mac(ssl3_ctx, mac, NULL, ad, ad_len, out, data_len)) {
    return 0;
  }
  if (CRYPTO_memcmp(&out[data_len], mac, mac_len) != 0) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
    return 0;
  }

  *out_len = data_len;
  return 1;
}

static int aead_ssl3_get_rc4_state(const EVP_AEAD_CTX *ctx, const RC4_KEY **out_key) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;
  if (EVP_CIPHER_CTX_cipher(&ssl3_ctx->cipher_ctx) != EVP_rc4()) {
    return 0;
  }

  *out_key = (RC4_KEY*) ssl3_ctx->cipher_ctx.cipher_data;
  return 1;
}

static int aead_rc4_md5_ssl3_init(EVP_AEAD_CTX *ctx, const uint8_t *key,
                                  size_t key_len, size_t tag_len,
                                  enum evp_aead_direction_t dir) {
  return aead_ssl3_init(ctx, key, key_len, tag_len, dir, EVP_rc4(), EVP_md5());
}

static int aead_rc4_sha1_ssl3_init(EVP_AEAD_CTX *ctx, const uint8_t *key,
                                   size_t key_len, size_t tag_len,
                                   enum evp_aead_direction_t dir) {
  return aead_ssl3_init(ctx, key, key_len, tag_len, dir, EVP_rc4(), EVP_sha1());
}

static int aead_aes_128_cbc_sha1_ssl3_init(EVP_AEAD_CTX *ctx, const uint8_t *key,
                                           size_t key_len, size_t tag_len,
                                           enum evp_aead_direction_t dir) {
  return aead_ssl3_init(ctx, key, key_len, tag_len, dir, EVP_aes_128_cbc(),
                        EVP_sha1());
}

static int aead_aes_256_cbc_sha1_ssl3_init(EVP_AEAD_CTX *ctx, const uint8_t *key,
                                           size_t key_len, size_t tag_len,
                                           enum evp_aead_direction_t dir) {
  return aead_ssl3_init(ctx, key, key_len, tag_len, dir, EVP_aes_256_cbc(),
                        EVP_sha1());
}
static int aead_des_ede3_cbc_sha1_ssl3_init(EVP_AEAD_CTX *ctx,
                                            const uint8_t *key, size_t key_len,
                                            size_t tag_len,
                                            enum evp_aead_direction_t dir) {
  return aead_ssl3_init(ctx, key, key_len, tag_len, dir, EVP_des_ede3_cbc(),
                        EVP_sha1());
}

static const EVP_AEAD aead_rc4_md5_ssl3 = {
    MD5_DIGEST_LENGTH + 16, /* key len (MD5 + RC4) */
    0,                      /* nonce len */
    MD5_DIGEST_LENGTH,      /* overhead */
    MD5_DIGEST_LENGTH,      /* max tag length */
    NULL, /* init */
    aead_rc4_md5_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_seal,
    aead_ssl3_open,
    aead_ssl3_get_rc4_state,
};

static const EVP_AEAD aead_rc4_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 16, /* key len (SHA1 + RC4) */
    0,                      /* nonce len */
    SHA_DIGEST_LENGTH,      /* overhead */
    SHA_DIGEST_LENGTH,      /* max tag length */
    NULL, /* init */
    aead_rc4_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_seal,
    aead_ssl3_open,
    aead_ssl3_get_rc4_state,
};

static const EVP_AEAD aead_aes_128_cbc_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 16 + 16, /* key len (SHA1 + AES128 + IV) */
    0,                           /* nonce len */
    16 + SHA_DIGEST_LENGTH,      /* overhead (padding + SHA1) */
    SHA_DIGEST_LENGTH,           /* max tag length */
    NULL, /* init */
    aead_aes_128_cbc_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_seal,
    aead_ssl3_open,
    NULL,                        /* get_rc4_state */
};

static const EVP_AEAD aead_aes_256_cbc_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 32 + 16, /* key len (SHA1 + AES256 + IV) */
    0,                           /* nonce len */
    16 + SHA_DIGEST_LENGTH,      /* overhead (padding + SHA1) */
    SHA_DIGEST_LENGTH,           /* max tag length */
    NULL, /* init */
    aead_aes_256_cbc_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_seal,
    aead_ssl3_open,
    NULL,                        /* get_rc4_state */
};

static const EVP_AEAD aead_des_ede3_cbc_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 24 + 8, /* key len (SHA1 + 3DES + IV) */
    0,                          /* nonce len */
    8 + SHA_DIGEST_LENGTH,      /* overhead (padding + SHA1) */
    SHA_DIGEST_LENGTH,          /* max tag length */
    NULL, /* init */
    aead_des_ede3_cbc_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_seal,
    aead_ssl3_open,
    NULL,                        /* get_rc4_state */
};

const EVP_AEAD *EVP_aead_rc4_md5_ssl3(void) { return &aead_rc4_md5_ssl3; }

const EVP_AEAD *EVP_aead_rc4_sha1_ssl3(void) { return &aead_rc4_sha1_ssl3; }

const EVP_AEAD *EVP_aead_aes_128_cbc_sha1_ssl3(void) {
  return &aead_aes_128_cbc_sha1_ssl3;
}

const EVP_AEAD *EVP_aead_aes_256_cbc_sha1_ssl3(void) {
  return &aead_aes_256_cbc_sha1_ssl3;
}

const EVP_AEAD *EVP_aead_des_ede3_cbc_sha1_ssl3(void) {
  return &aead_des_ede3_cbc_sha1_ssl3;
}
