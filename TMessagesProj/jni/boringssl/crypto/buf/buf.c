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

#include <openssl/buf.h>

#include <string.h>

#include <openssl/mem.h>
#include <openssl/err.h>

#include "../internal.h"


BUF_MEM *BUF_MEM_new(void) {
  BUF_MEM *ret;

  ret = OPENSSL_malloc(sizeof(BUF_MEM));
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  OPENSSL_memset(ret, 0, sizeof(BUF_MEM));
  return ret;
}

void BUF_MEM_free(BUF_MEM *buf) {
  if (buf == NULL) {
    return;
  }

  OPENSSL_free(buf->data);
  OPENSSL_free(buf);
}

int BUF_MEM_reserve(BUF_MEM *buf, size_t cap) {
  if (buf->max >= cap) {
    return 1;
  }

  size_t n = cap + 3;
  if (n < cap) {
    // overflow
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  n = n / 3;
  size_t alloc_size = n * 4;
  if (alloc_size / 4 != n) {
    // overflow
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  char *new_buf = OPENSSL_realloc(buf->data, alloc_size);
  if (new_buf == NULL) {
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  buf->data = new_buf;
  buf->max = alloc_size;
  return 1;
}

size_t BUF_MEM_grow(BUF_MEM *buf, size_t len) {
  if (!BUF_MEM_reserve(buf, len)) {
    return 0;
  }
  if (buf->length < len) {
    OPENSSL_memset(&buf->data[buf->length], 0, len - buf->length);
  }
  buf->length = len;
  return len;
}

size_t BUF_MEM_grow_clean(BUF_MEM *buf, size_t len) {
  return BUF_MEM_grow(buf, len);
}

int BUF_MEM_append(BUF_MEM *buf, const void *in, size_t len) {
  size_t new_len = buf->length + len;
  if (new_len < len) {
    OPENSSL_PUT_ERROR(BUF, ERR_R_OVERFLOW);
    return 0;
  }
  if (!BUF_MEM_reserve(buf, new_len)) {
    return 0;
  }
  OPENSSL_memcpy(buf->data + buf->length, in, len);
  buf->length = new_len;
  return 1;
}

char *BUF_strdup(const char *str) {
  if (str == NULL) {
    return NULL;
  }

  return BUF_strndup(str, strlen(str));
}

size_t BUF_strnlen(const char *str, size_t max_len) {
  size_t i;

  for (i = 0; i < max_len; i++) {
    if (str[i] == 0) {
      break;
    }
  }

  return i;
}

char *BUF_strndup(const char *str, size_t size) {
  char *ret;
  size_t alloc_size;

  if (str == NULL) {
    return NULL;
  }

  size = BUF_strnlen(str, size);

  alloc_size = size + 1;
  if (alloc_size < size) {
    // overflow
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  ret = OPENSSL_malloc(alloc_size);
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  OPENSSL_memcpy(ret, str, size);
  ret[size] = '\0';
  return ret;
}

size_t BUF_strlcpy(char *dst, const char *src, size_t dst_size) {
  size_t l = 0;

  for (; dst_size > 1 && *src; dst_size--) {
    *dst++ = *src++;
    l++;
  }

  if (dst_size) {
    *dst = 0;
  }

  return l + strlen(src);
}

size_t BUF_strlcat(char *dst, const char *src, size_t dst_size) {
  size_t l = 0;
  for (; dst_size > 0 && *dst; dst_size--, dst++) {
    l++;
  }
  return l + BUF_strlcpy(dst, src, dst_size);
}

void *BUF_memdup(const void *data, size_t size) {
  if (size == 0) {
    return NULL;
  }

  void *ret = OPENSSL_malloc(size);
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(BUF, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  OPENSSL_memcpy(ret, data, size);
  return ret;
}
