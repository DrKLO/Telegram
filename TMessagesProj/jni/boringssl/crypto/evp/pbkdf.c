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

#include "../internal.h"


int PKCS5_PBKDF2_HMAC(const char *password, size_t password_len,
                      const uint8_t *salt, size_t salt_len, unsigned iterations,
                      const EVP_MD *digest, size_t key_len, uint8_t *out_key) {
  // See RFC 8018, section 5.2.
  int ret = 0;
  size_t md_len = EVP_MD_size(digest);
  uint32_t i = 1;
  HMAC_CTX hctx;
  HMAC_CTX_init(&hctx);

  if (!HMAC_Init_ex(&hctx, password, password_len, digest, NULL)) {
    goto err;
  }

  while (key_len > 0) {
    size_t todo = md_len;
    if (todo > key_len) {
      todo = key_len;
    }

    uint8_t i_buf[4];
    i_buf[0] = (uint8_t)((i >> 24) & 0xff);
    i_buf[1] = (uint8_t)((i >> 16) & 0xff);
    i_buf[2] = (uint8_t)((i >> 8) & 0xff);
    i_buf[3] = (uint8_t)(i & 0xff);

    // Compute U_1.
    uint8_t digest_tmp[EVP_MAX_MD_SIZE];
    if (!HMAC_Init_ex(&hctx, NULL, 0, NULL, NULL) ||
        !HMAC_Update(&hctx, salt, salt_len) ||
        !HMAC_Update(&hctx, i_buf, 4) ||
        !HMAC_Final(&hctx, digest_tmp, NULL)) {
      goto err;
    }

    OPENSSL_memcpy(out_key, digest_tmp, todo);
    for (unsigned j = 1; j < iterations; j++) {
      // Compute the remaining U_* values and XOR.
      if (!HMAC_Init_ex(&hctx, NULL, 0, NULL, NULL) ||
          !HMAC_Update(&hctx, digest_tmp, md_len) ||
          !HMAC_Final(&hctx, digest_tmp, NULL)) {
        goto err;
      }
      for (size_t k = 0; k < todo; k++) {
        out_key[k] ^= digest_tmp[k];
      }
    }

    key_len -= todo;
    out_key += todo;
    i++;
  }

  // RFC 8018 describes iterations (c) as being a "positive integer", so a
  // value of 0 is an error.
  //
  // Unfortunately not all consumers of PKCS5_PBKDF2_HMAC() check their return
  // value, expecting it to succeed and unconditionally using |out_key|.  As a
  // precaution for such callsites in external code, the old behavior of
  // iterations < 1 being treated as iterations == 1 is preserved, but
  // additionally an error result is returned.
  //
  // TODO(eroman): Figure out how to remove this compatibility hack, or change
  // the default to something more sensible like 2048.
  if (iterations == 0) {
    goto err;
  }

  ret = 1;

err:
  HMAC_CTX_cleanup(&hctx);
  return ret;
}

int PKCS5_PBKDF2_HMAC_SHA1(const char *password, size_t password_len,
                           const uint8_t *salt, size_t salt_len,
                           unsigned iterations, size_t key_len,
                           uint8_t *out_key) {
  return PKCS5_PBKDF2_HMAC(password, password_len, salt, salt_len, iterations,
                           EVP_sha1(), key_len, out_key);
}
