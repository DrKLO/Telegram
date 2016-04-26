/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 1999.
 */
/* ====================================================================
 * Copyright (c) 1999 The OpenSSL Project.  All rights reserved.
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
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/evp.h>

#include <string.h>

#include <openssl/hmac.h>


int PKCS5_PBKDF2_HMAC(const char *password, size_t password_len,
                      const uint8_t *salt, size_t salt_len, unsigned iterations,
                      const EVP_MD *digest, size_t key_len, uint8_t *out_key) {
  uint8_t digest_tmp[EVP_MAX_MD_SIZE], *p, itmp[4];
  size_t cplen, mdlen, tkeylen, k;
  unsigned j;
  uint32_t i = 1;
  HMAC_CTX hctx_tpl, hctx;

  mdlen = EVP_MD_size(digest);
  HMAC_CTX_init(&hctx_tpl);
  p = out_key;
  tkeylen = key_len;
  if (!HMAC_Init_ex(&hctx_tpl, password, password_len, digest, NULL)) {
    HMAC_CTX_cleanup(&hctx_tpl);
    return 0;
  }
  while (tkeylen) {
    if (tkeylen > mdlen) {
      cplen = mdlen;
    } else {
      cplen = tkeylen;
    }
    /* We are unlikely to ever use more than 256 blocks (5120 bits!)
     * but just in case... */
    itmp[0] = (uint8_t)((i >> 24) & 0xff);
    itmp[1] = (uint8_t)((i >> 16) & 0xff);
    itmp[2] = (uint8_t)((i >> 8) & 0xff);
    itmp[3] = (uint8_t)(i & 0xff);
    if (!HMAC_CTX_copy(&hctx, &hctx_tpl)) {
      HMAC_CTX_cleanup(&hctx_tpl);
      return 0;
    }
    if (!HMAC_Update(&hctx, salt, salt_len) ||
        !HMAC_Update(&hctx, itmp, 4) ||
        !HMAC_Final(&hctx, digest_tmp, NULL)) {
      HMAC_CTX_cleanup(&hctx_tpl);
      HMAC_CTX_cleanup(&hctx);
      return 0;
    }
    HMAC_CTX_cleanup(&hctx);
    memcpy(p, digest_tmp, cplen);
    for (j = 1; j < iterations; j++) {
      if (!HMAC_CTX_copy(&hctx, &hctx_tpl)) {
        HMAC_CTX_cleanup(&hctx_tpl);
        return 0;
      }
      if (!HMAC_Update(&hctx, digest_tmp, mdlen) ||
          !HMAC_Final(&hctx, digest_tmp, NULL)) {
        HMAC_CTX_cleanup(&hctx_tpl);
        HMAC_CTX_cleanup(&hctx);
        return 0;
      }
      HMAC_CTX_cleanup(&hctx);
      for (k = 0; k < cplen; k++) {
        p[k] ^= digest_tmp[k];
      }
    }
    tkeylen -= cplen;
    i++;
    p += cplen;
  }
  HMAC_CTX_cleanup(&hctx_tpl);
  return 1;
}

int PKCS5_PBKDF2_HMAC_SHA1(const char *password, size_t password_len,
                           const uint8_t *salt, size_t salt_len,
                           unsigned iterations, size_t key_len,
                           uint8_t *out_key) {
  return PKCS5_PBKDF2_HMAC(password, password_len, salt, salt_len, iterations,
                           EVP_sha1(), key_len, out_key);
}
