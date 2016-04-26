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

#include <openssl/aead.h>

#include <assert.h>
#include <string.h>

#include <openssl/cipher.h>
#include <openssl/cpu.h>
#include <openssl/err.h>
#include <openssl/md5.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/rc4.h>

#include "internal.h"


static int rc4_init_key(EVP_CIPHER_CTX *ctx, const uint8_t *key,
                        const uint8_t *iv, int enc) {
  RC4_KEY *rc4key = (RC4_KEY *)ctx->cipher_data;

  RC4_set_key(rc4key, EVP_CIPHER_CTX_key_length(ctx), key);
  return 1;
}

static int rc4_cipher(EVP_CIPHER_CTX *ctx, uint8_t *out, const uint8_t *in,
                      size_t in_len) {
  RC4_KEY *rc4key = (RC4_KEY *)ctx->cipher_data;

  RC4(rc4key, in_len, in, out);
  return 1;
}

static const EVP_CIPHER rc4 = {
    NID_rc4,             1 /* block_size */, 16 /* key_size */,
    0 /* iv_len */,      sizeof(RC4_KEY),    EVP_CIPH_VARIABLE_LENGTH,
    NULL /* app_data */, rc4_init_key,       rc4_cipher,
    NULL /* cleanup */,  NULL /* ctrl */, };

const EVP_CIPHER *EVP_rc4(void) { return &rc4; }


struct aead_rc4_md5_tls_ctx {
  RC4_KEY rc4;
  MD5_CTX head, tail, md;
  size_t payload_length;
  unsigned char tag_len;
};


static int
aead_rc4_md5_tls_init(EVP_AEAD_CTX *ctx, const uint8_t *key, size_t key_len,
                      size_t tag_len) {
  struct aead_rc4_md5_tls_ctx *rc4_ctx;
  size_t i;
  uint8_t hmac_key[MD5_CBLOCK];

  if (tag_len == EVP_AEAD_DEFAULT_TAG_LENGTH) {
    tag_len = MD5_DIGEST_LENGTH;
  }

  if (tag_len > MD5_DIGEST_LENGTH) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  /* The keys consists of |MD5_DIGEST_LENGTH| bytes of HMAC(MD5) key followed
   * by some number of bytes of RC4 key. */
  if (key_len <= MD5_DIGEST_LENGTH) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_KEY_LENGTH);
    return 0;
  }

  rc4_ctx = OPENSSL_malloc(sizeof(struct aead_rc4_md5_tls_ctx));
  if (rc4_ctx == NULL) {
    OPENSSL_PUT_ERROR(CIPHER, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  memset(rc4_ctx, 0, sizeof(struct aead_rc4_md5_tls_ctx));

  RC4_set_key(&rc4_ctx->rc4, key_len - MD5_DIGEST_LENGTH,
              key + MD5_DIGEST_LENGTH);

  memset(hmac_key, 0, sizeof(hmac_key));
  memcpy(hmac_key, key, MD5_DIGEST_LENGTH);
  for (i = 0; i < sizeof(hmac_key); i++) {
    hmac_key[i] ^= 0x36;
  }
  MD5_Init(&rc4_ctx->head);
  MD5_Update(&rc4_ctx->head, hmac_key, sizeof(hmac_key));
  for (i = 0; i < sizeof(hmac_key); i++) {
    hmac_key[i] ^= 0x36 ^ 0x5c;
  }
  MD5_Init(&rc4_ctx->tail);
  MD5_Update(&rc4_ctx->tail, hmac_key, sizeof(hmac_key));

  rc4_ctx->tag_len = tag_len;
  ctx->aead_state = rc4_ctx;

  return 1;
}

static void aead_rc4_md5_tls_cleanup(EVP_AEAD_CTX *ctx) {
  struct aead_rc4_md5_tls_ctx *rc4_ctx = ctx->aead_state;
  OPENSSL_cleanse(rc4_ctx, sizeof(struct aead_rc4_md5_tls_ctx));
  OPENSSL_free(rc4_ctx);
}

#if !defined(OPENSSL_NO_ASM) && defined(OPENSSL_X86_64)
#define STITCHED_CALL

/* rc4_md5_enc is defined in rc4_md5-x86_64.pl */
void rc4_md5_enc(RC4_KEY *key, const void *in0, void *out, MD5_CTX *ctx,
                 const void *inp, size_t blocks);
#endif

static int aead_rc4_md5_tls_seal(const EVP_AEAD_CTX *ctx, uint8_t *out,
                                 size_t *out_len, size_t max_out_len,
                                 const uint8_t *nonce, size_t nonce_len,
                                 const uint8_t *in, size_t in_len,
                                 const uint8_t *ad, size_t ad_len) {
  struct aead_rc4_md5_tls_ctx *rc4_ctx = ctx->aead_state;
  MD5_CTX md;
#if defined(STITCHED_CALL)
  size_t rc4_off, md5_off, blocks;
#else
  const size_t rc4_off = 0;
  const size_t md5_off = 0;
#endif
  uint8_t digest[MD5_DIGEST_LENGTH];

  if (in_len + rc4_ctx->tag_len < in_len) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  if (nonce_len != 0) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_IV_TOO_LARGE);
    return 0;
  }

  if (max_out_len < in_len + rc4_ctx->tag_len) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BUFFER_TOO_SMALL);
    return 0;
  }

  if (nonce_len != 0) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  memcpy(&md, &rc4_ctx->head, sizeof(MD5_CTX));
  /* The MAC's payload begins with the additional data. See
   * https://tools.ietf.org/html/rfc5246#section-6.2.3.1 */
  MD5_Update(&md, ad, ad_len);

  /* To allow for CBC mode which changes cipher length, |ad| doesn't include the
   * length for legacy ciphers. */
  uint8_t ad_extra[2];
  ad_extra[0] = (uint8_t)(in_len >> 8);
  ad_extra[1] = (uint8_t)(in_len & 0xff);
  MD5_Update(&md, ad_extra, sizeof(ad_extra));

#if defined(STITCHED_CALL)
  /* 32 is $MOD from rc4_md5-x86_64.pl. */
  rc4_off = 32 - 1 - (rc4_ctx->rc4.x & (32 - 1));
  md5_off = MD5_CBLOCK - md.num;
  /* Ensure RC4 is behind MD5. */
  if (rc4_off > md5_off) {
    md5_off += MD5_CBLOCK;
  }
  assert(md5_off >= rc4_off);

  if (in_len > md5_off && (blocks = (in_len - md5_off) / MD5_CBLOCK) &&
      (OPENSSL_ia32cap_P[0] & (1 << 20)) == 0) {
    /* Process the initial portions of the plaintext normally. */
    MD5_Update(&md, in, md5_off);
    RC4(&rc4_ctx->rc4, rc4_off, in, out);

    /* Process the next |blocks| blocks of plaintext with stitched routines. */
    rc4_md5_enc(&rc4_ctx->rc4, in + rc4_off, out + rc4_off, &md, in + md5_off,
                blocks);
    blocks *= MD5_CBLOCK;
    rc4_off += blocks;
    md5_off += blocks;
    md.Nh += blocks >> 29;
    md.Nl += blocks <<= 3;
    if (md.Nl < (unsigned int)blocks) {
      md.Nh++;
    }
  } else {
    rc4_off = 0;
    md5_off = 0;
  }
#endif
  /* Finish computing the MAC. */
  MD5_Update(&md, in + md5_off, in_len - md5_off);
  MD5_Final(digest, &md);

  memcpy(&md, &rc4_ctx->tail, sizeof(MD5_CTX));
  MD5_Update(&md, digest, sizeof(digest));
  if (rc4_ctx->tag_len == MD5_DIGEST_LENGTH) {
    MD5_Final(out + in_len, &md);
  } else {
    MD5_Final(digest, &md);
    memcpy(out + in_len, digest, rc4_ctx->tag_len);
  }

  /* Encrypt the remainder of the plaintext and the MAC. */
  RC4(&rc4_ctx->rc4, in_len - rc4_off, in + rc4_off, out + rc4_off);
  RC4(&rc4_ctx->rc4, MD5_DIGEST_LENGTH, out + in_len, out + in_len);

  *out_len = in_len + rc4_ctx->tag_len;
  return 1;
}

static int aead_rc4_md5_tls_open(const EVP_AEAD_CTX *ctx, uint8_t *out,
                                 size_t *out_len, size_t max_out_len,
                                 const uint8_t *nonce, size_t nonce_len,
                                 const uint8_t *in, size_t in_len,
                                 const uint8_t *ad, size_t ad_len) {
  struct aead_rc4_md5_tls_ctx *rc4_ctx = ctx->aead_state;
  MD5_CTX md;
  size_t plaintext_len;
#if defined(STITCHED_CALL)
  unsigned int l;
  size_t rc4_off, md5_off, blocks;
  extern unsigned int OPENSSL_ia32cap_P[];
#else
  const size_t rc4_off = 0;
  const size_t md5_off = 0;
#endif
  uint8_t digest[MD5_DIGEST_LENGTH];

  if (in_len < rc4_ctx->tag_len) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
    return 0;
  }

  plaintext_len = in_len - rc4_ctx->tag_len;

  if (nonce_len != 0) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_TOO_LARGE);
    return 0;
  }

  if (max_out_len < in_len) {
    /* This requires that the caller provide space for the MAC, even though it
     * will always be removed on return. */
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BUFFER_TOO_SMALL);
    return 0;
  }

  memcpy(&md, &rc4_ctx->head, sizeof(MD5_CTX));
  /* The MAC's payload begins with the additional data. See
   * https://tools.ietf.org/html/rfc5246#section-6.2.3.1 */
  MD5_Update(&md, ad, ad_len);

  /* To allow for CBC mode which changes cipher length, |ad| doesn't include the
   * length for legacy ciphers. */
  uint8_t ad_extra[2];
  ad_extra[0] = (uint8_t)(plaintext_len >> 8);
  ad_extra[1] = (uint8_t)(plaintext_len & 0xff);
  MD5_Update(&md, ad_extra, sizeof(ad_extra));

#if defined(STITCHED_CALL)
  rc4_off = 32 - 1 - (rc4_ctx->rc4.x & (32 - 1));
  md5_off = MD5_CBLOCK - md.num;
  /* Ensure MD5 is a full block behind RC4 so it has plaintext to operate on in
   * both normal and stitched routines. */
  if (md5_off > rc4_off) {
    rc4_off += 2 * MD5_CBLOCK;
  } else {
    rc4_off += MD5_CBLOCK;
  }

  if (in_len > rc4_off && (blocks = (in_len - rc4_off) / MD5_CBLOCK) &&
      (OPENSSL_ia32cap_P[0] & (1 << 20)) == 0) {
    /* Decrypt the initial portion of the ciphertext and digest the plaintext
     * normally. */
    RC4(&rc4_ctx->rc4, rc4_off, in, out);
    MD5_Update(&md, out, md5_off);

    /* Decrypt and digest the next |blocks| blocks of ciphertext with the
     * stitched routines. */
    rc4_md5_enc(&rc4_ctx->rc4, in + rc4_off, out + rc4_off, &md, out + md5_off,
                blocks);
    blocks *= MD5_CBLOCK;
    rc4_off += blocks;
    md5_off += blocks;
    l = (md.Nl + (blocks << 3)) & 0xffffffffU;
    if (l < md.Nl) {
      md.Nh++;
    }
    md.Nl = l;
    md.Nh += blocks >> 29;
  } else {
    md5_off = 0;
    rc4_off = 0;
  }
#endif

  /* Process the remainder of the input. */
  RC4(&rc4_ctx->rc4, in_len - rc4_off, in + rc4_off, out + rc4_off);
  MD5_Update(&md, out + md5_off, plaintext_len - md5_off);
  MD5_Final(digest, &md);

  /* Calculate HMAC and verify it */
  memcpy(&md, &rc4_ctx->tail, sizeof(MD5_CTX));
  MD5_Update(&md, digest, MD5_DIGEST_LENGTH);
  MD5_Final(digest, &md);

  if (CRYPTO_memcmp(out + plaintext_len, digest, rc4_ctx->tag_len)) {
    OPENSSL_PUT_ERROR(CIPHER, CIPHER_R_BAD_DECRYPT);
    return 0;
  }

  *out_len = plaintext_len;
  return 1;
}

static int aead_rc4_md5_tls_get_rc4_state(const EVP_AEAD_CTX *ctx,
                                          const RC4_KEY **out_key) {
  struct aead_rc4_md5_tls_ctx *rc4_ctx = ctx->aead_state;
  *out_key = &rc4_ctx->rc4;
  return 1;
}

static const EVP_AEAD aead_rc4_md5_tls = {
    16 + MD5_DIGEST_LENGTH, /* key len (RC4 + MD5) */
    0,                      /* nonce len */
    MD5_DIGEST_LENGTH,      /* overhead */
    MD5_DIGEST_LENGTH,      /* max tag length */
    aead_rc4_md5_tls_init,
    NULL, /* init_with_direction */
    aead_rc4_md5_tls_cleanup,
    aead_rc4_md5_tls_seal,
    aead_rc4_md5_tls_open,
    aead_rc4_md5_tls_get_rc4_state,
};

const EVP_AEAD *EVP_aead_rc4_md5_tls(void) { return &aead_rc4_md5_tls; }
