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
#include "../internal.h"
#include "../fipsmodule/cipher/internal.h"


typedef struct {
  EVP_CIPHER_CTX cipher_ctx;
  EVP_MD_CTX md_ctx;
} AEAD_SSL3_CTX;

static int ssl3_mac(AEAD_SSL3_CTX *ssl3_ctx, uint8_t *out, unsigned *out_len,
                    const uint8_t *ad, size_t ad_len, const uint8_t *in,
                    size_t in_len) {
  size_t md_size = EVP_MD_CTX_size(&ssl3_ctx->md_ctx);
  size_t pad_len = (md_size == 20) ? 40 : 48;

  // To allow for CBC mode which changes cipher length, |ad| doesn't include the
  // length for legacy ciphers.
  uint8_t ad_extra[2];
  ad_extra[0] = (uint8_t)(in_len >> 8);
  ad_extra[1] = (uint8_t)(in_len & 0xff);

  EVP_MD_CTX md_ctx;
  EVP_MD_CTX_init(&md_ctx);

  uint8_t pad[48];
  uint8_t tmp[EVP_MAX_MD_SIZE];
  OPENSSL_memset(pad, 0x36, pad_len);
  if (!EVP_MD_CTX_copy_ex(&md_ctx, &ssl3_ctx->md_ctx) ||
      !EVP_DigestUpdate(&md_ctx, pad, pad_len) ||
      !EVP_DigestUpdate(&md_ctx, ad, ad_len) ||
      !EVP_DigestUpdate(&md_ctx, ad_extra, sizeof(ad_extra)) ||
      !EVP_DigestUpdate(&md_ctx, in, in_len) ||
      !EVP_DigestFinal_ex(&md_ctx, tmp, NULL)) {
    EVP_MD_CTX_cleanup(&md_ctx);
    return 0;
  }

  OPENSSL_memset(pad, 0x5c, pad_len);
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

static size_t aead_ssl3_tag_len(const EVP_AEAD_CTX *ctx, const size_t in_len,
                                const size_t extra_in_len) {
  assert(extra_in_len == 0);
  const AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX*)ctx->aead_state;

  const size_t digest_len = EVP_MD_CTX_size(&ssl3_ctx->md_ctx);
  if (EVP_CIPHER_CTX_mode(&ssl3_ctx->cipher_ctx) != EVP_CIPH_CBC_MODE) {
    // The NULL cipher.
    return digest_len;
  }

  const size_t block_size = EVP_CIPHER_CTX_block_size(&ssl3_ctx->cipher_ctx);
  // An overflow of |in_len + digest_len| doesn't affect the result mod
  // |block_size|, provided that |block_size| is a smaller power of two.
  assert(block_size != 0 && (block_size & (block_size - 1)) == 0);
  const size_t pad_len = block_size - ((in_len + digest_len) % block_size);
  return digest_len + pad_len;
}

static int aead_ssl3_seal_scatter(const EVP_AEAD_CTX *ctx, uint8_t *out,
                                  uint8_t *out_tag, size_t *out_tag_len,
                                  const size_t max_out_tag_len,
                                  const uint8_t *nonce, const size_t nonce_len,
                                  const uint8_t *in, const size_t in_len,
                                  const uint8_t *extra_in,
                                  const size_t extra_in_len, const uint8_t *ad,
                                  const size_t ad_len) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;

  if (!ssl3_ctx->cipher_ctx.encrypt) {
    // Unlike a normal AEAD, an SSL3 AEAD may only be used in one direction.
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_INVALID_OPERATION);
    return 0;
  }

  if (in_len > INT_MAX) {
    // EVP_CIPHER takes int as input.
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  if (max_out_tag_len < aead_ssl3_tag_len(ctx, in_len, extra_in_len)) {
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

  // Compute the MAC. This must be first in case the operation is being done
  // in-place.
  uint8_t mac[EVP_MAX_MD_SIZE];
  unsigned mac_len;
  if (!ssl3_mac(ssl3_ctx, mac, &mac_len, ad, ad_len, in, in_len)) {
    return 0;
  }

  // Encrypt the input.
  int len;
  if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, out, &len, in,
                         (int)in_len)) {
    return 0;
  }

  const size_t block_size = EVP_CIPHER_CTX_block_size(&ssl3_ctx->cipher_ctx);

  // Feed the MAC into the cipher in two steps. First complete the final partial
  // block from encrypting the input and split the result between |out| and
  // |out_tag|. Then encrypt the remainder.

  size_t early_mac_len = (block_size - (in_len % block_size)) % block_size;
  if (early_mac_len != 0) {
    assert(len + block_size - early_mac_len == in_len);
    uint8_t buf[EVP_MAX_BLOCK_LENGTH];
    int buf_len;
    if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, buf, &buf_len, mac,
                           (int)early_mac_len)) {
      return 0;
    }
    assert(buf_len == (int)block_size);
    OPENSSL_memcpy(out + len, buf, block_size - early_mac_len);
    OPENSSL_memcpy(out_tag, buf + block_size - early_mac_len, early_mac_len);
  }
  size_t tag_len = early_mac_len;

  if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, out_tag + tag_len, &len,
                         mac + tag_len, mac_len - tag_len)) {
    return 0;
  }
  tag_len += len;

  if (block_size > 1) {
    assert(block_size <= 256);
    assert(EVP_CIPHER_CTX_mode(&ssl3_ctx->cipher_ctx) == EVP_CIPH_CBC_MODE);

    // Compute padding and feed that into the cipher.
    uint8_t padding[256];
    size_t padding_len = block_size - ((in_len + mac_len) % block_size);
    OPENSSL_memset(padding, 0, padding_len - 1);
    padding[padding_len - 1] = padding_len - 1;
    if (!EVP_EncryptUpdate(&ssl3_ctx->cipher_ctx, out_tag + tag_len, &len, padding,
                           (int)padding_len)) {
      return 0;
    }
    tag_len += len;
  }

  if (!EVP_EncryptFinal_ex(&ssl3_ctx->cipher_ctx, out_tag + tag_len, &len)) {
    return 0;
  }
  tag_len += len;
  assert(tag_len == aead_ssl3_tag_len(ctx, in_len, extra_in_len));

  *out_tag_len = tag_len;
  return 1;
}

static int aead_ssl3_open(const EVP_AEAD_CTX *ctx, uint8_t *out,
                         size_t *out_len, size_t max_out_len,
                         const uint8_t *nonce, size_t nonce_len,
                         const uint8_t *in, size_t in_len,
                         const uint8_t *ad, size_t ad_len) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;

  if (ssl3_ctx->cipher_ctx.encrypt) {
    // Unlike a normal AEAD, an SSL3 AEAD may only be used in one direction.
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_INVALID_OPERATION);
    return 0;
  }

  size_t mac_len = EVP_MD_CTX_size(&ssl3_ctx->md_ctx);
  if (in_len < mac_len) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
    return 0;
  }

  if (max_out_len < in_len) {
    // This requires that the caller provide space for the MAC, even though it
    // will always be removed on return.
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
    // EVP_CIPHER takes int as input.
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  // Decrypt to get the plaintext + MAC + padding.
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

  // Remove CBC padding and MAC. This would normally be timing-sensitive, but
  // SSLv3 CBC ciphers are already broken. Support will be removed eventually.
  // https://www.openssl.org/~bodo/ssl-poodle.pdf
  size_t data_len;
  if (EVP_CIPHER_CTX_mode(&ssl3_ctx->cipher_ctx) == EVP_CIPH_CBC_MODE) {
    unsigned padding_length = out[total - 1];
    if (total < padding_length + 1 + mac_len) {
      OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
      return 0;
    }
    // The padding must be minimal.
    if (padding_length + 1 > EVP_CIPHER_CTX_block_size(&ssl3_ctx->cipher_ctx)) {
      OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
      return 0;
    }
    data_len = total - padding_length - 1 - mac_len;
  } else {
    data_len = total - mac_len;
  }

  // Compute the MAC and compare against the one in the record.
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

static int aead_ssl3_get_iv(const EVP_AEAD_CTX *ctx, const uint8_t **out_iv,
                            size_t *out_iv_len) {
  AEAD_SSL3_CTX *ssl3_ctx = (AEAD_SSL3_CTX *)ctx->aead_state;
  const size_t iv_len = EVP_CIPHER_CTX_iv_length(&ssl3_ctx->cipher_ctx);
  if (iv_len <= 1) {
    return 0;
  }

  *out_iv = ssl3_ctx->cipher_ctx.iv;
  *out_iv_len = iv_len;
  return 1;
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

static int aead_null_sha1_ssl3_init(EVP_AEAD_CTX *ctx, const uint8_t *key,
                                    size_t key_len, size_t tag_len,
                                    enum evp_aead_direction_t dir) {
  return aead_ssl3_init(ctx, key, key_len, tag_len, dir, EVP_enc_null(),
                        EVP_sha1());
}

static const EVP_AEAD aead_aes_128_cbc_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 16 + 16,  // key len (SHA1 + AES128 + IV)
    0,                            // nonce len
    16 + SHA_DIGEST_LENGTH,       // overhead (padding + SHA1)
    SHA_DIGEST_LENGTH,            // max tag length
    0,                            // seal_scatter_supports_extra_in

    NULL,  // init
    aead_aes_128_cbc_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_open,
    aead_ssl3_seal_scatter,
    NULL,  // open_gather
    aead_ssl3_get_iv,
    aead_ssl3_tag_len,
};

static const EVP_AEAD aead_aes_256_cbc_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 32 + 16,  // key len (SHA1 + AES256 + IV)
    0,                            // nonce len
    16 + SHA_DIGEST_LENGTH,       // overhead (padding + SHA1)
    SHA_DIGEST_LENGTH,            // max tag length
    0,                            // seal_scatter_supports_extra_in

    NULL,  // init
    aead_aes_256_cbc_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_open,
    aead_ssl3_seal_scatter,
    NULL,  // open_gather
    aead_ssl3_get_iv,
    aead_ssl3_tag_len,
};

static const EVP_AEAD aead_des_ede3_cbc_sha1_ssl3 = {
    SHA_DIGEST_LENGTH + 24 + 8,  // key len (SHA1 + 3DES + IV)
    0,                           // nonce len
    8 + SHA_DIGEST_LENGTH,       // overhead (padding + SHA1)
    SHA_DIGEST_LENGTH,           // max tag length
    0,                           // seal_scatter_supports_extra_in

    NULL,  // init
    aead_des_ede3_cbc_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_open,
    aead_ssl3_seal_scatter,
    NULL,  // open_gather
    aead_ssl3_get_iv,
    aead_ssl3_tag_len,
};

static const EVP_AEAD aead_null_sha1_ssl3 = {
    SHA_DIGEST_LENGTH,  // key len
    0,                  // nonce len
    SHA_DIGEST_LENGTH,  // overhead (SHA1)
    SHA_DIGEST_LENGTH,  // max tag length
    0,                  // seal_scatter_supports_extra_in

    NULL,  // init
    aead_null_sha1_ssl3_init,
    aead_ssl3_cleanup,
    aead_ssl3_open,
    aead_ssl3_seal_scatter,
    NULL,  // open_gather
    NULL,  // get_iv
    aead_ssl3_tag_len,
};

const EVP_AEAD *EVP_aead_aes_128_cbc_sha1_ssl3(void) {
  return &aead_aes_128_cbc_sha1_ssl3;
}

const EVP_AEAD *EVP_aead_aes_256_cbc_sha1_ssl3(void) {
  return &aead_aes_256_cbc_sha1_ssl3;
}

const EVP_AEAD *EVP_aead_des_ede3_cbc_sha1_ssl3(void) {
  return &aead_des_ede3_cbc_sha1_ssl3;
}

const EVP_AEAD *EVP_aead_null_sha1_ssl3(void) { return &aead_null_sha1_ssl3; }
