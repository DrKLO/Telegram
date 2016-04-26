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

#if !defined(_POSIX_C_SOURCE)
#define _POSIX_C_SOURCE 201410L  /* needed for strdup, snprintf, vprintf etc */
#endif

#include <openssl/mem.h>

#include <assert.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#if defined(OPENSSL_WINDOWS)
#pragma warning(push, 3)
#include <windows.h>
#pragma warning(pop)
#else
#include <strings.h>
#endif


void *OPENSSL_realloc_clean(void *ptr, size_t old_size, size_t new_size) {
  void *ret = NULL;

  if (ptr == NULL) {
    return OPENSSL_malloc(new_size);
  }

  if (new_size == 0) {
    return NULL;
  }

  /* We don't support shrinking the buffer. Note the memcpy that copies
   * |old_size| bytes to the new buffer, below. */
  if (new_size < old_size) {
    return NULL;
  }

  ret = OPENSSL_malloc(new_size);
  if (ret == NULL) {
    return NULL;
  }

  memcpy(ret, ptr, old_size);
  OPENSSL_cleanse(ptr, old_size);
  OPENSSL_free(ptr);
  return ret;
}

void OPENSSL_cleanse(void *ptr, size_t len) {
#if defined(OPENSSL_WINDOWS)
	SecureZeroMemory(ptr, len);
#else
	memset(ptr, 0, len);

#if !defined(OPENSSL_NO_ASM)
  /* As best as we can tell, this is sufficient to break any optimisations that
     might try to eliminate "superfluous" memsets. If there's an easy way to
     detect memset_s, it would be better to use that. */
  __asm__ __volatile__("" : : "r"(ptr) : "memory");
#endif
#endif  /* !OPENSSL_NO_ASM */
}

int CRYPTO_memcmp(const void *in_a, const void *in_b, size_t len) {
  size_t i;
  const uint8_t *a = in_a;
  const uint8_t *b = in_b;
  uint8_t x = 0;

  for (i = 0; i < len; i++) {
    x |= a[i] ^ b[i];
  }

  return x;
}

uint32_t OPENSSL_hash32(const void *ptr, size_t len) {
  /* These are the FNV-1a parameters for 32 bits. */
  static const uint32_t kPrime = 16777619u;
  static const uint32_t kOffsetBasis = 2166136261u;

  const uint8_t *in = ptr;
  size_t i;
  uint32_t h = kOffsetBasis;

  for (i = 0; i < len; i++) {
    h ^= in[i];
    h *= kPrime;
  }

  return h;
}

char *OPENSSL_strdup(const char *s) { return strdup(s); }

size_t OPENSSL_strnlen(const char *s, size_t len) {
  size_t i;

  for (i = 0; i < len; i++) {
    if (s[i] == 0) {
      return i;
    }
  }

  return len;
}

#if defined(OPENSSL_WINDOWS)

int OPENSSL_strcasecmp(const char *a, const char *b) {
  return _stricmp(a, b);
}

int OPENSSL_strncasecmp(const char *a, const char *b, size_t n) {
  return _strnicmp(a, b, n);
}

#else

int OPENSSL_strcasecmp(const char *a, const char *b) {
  return strcasecmp(a, b);
}

int OPENSSL_strncasecmp(const char *a, const char *b, size_t n) {
  return strncasecmp(a, b, n);
}

#endif

int BIO_snprintf(char *buf, size_t n, const char *format, ...) {
  va_list args;
  int ret;

  va_start(args, format);

  ret = BIO_vsnprintf(buf, n, format, args);

  va_end(args);
  return ret;
}

int BIO_vsnprintf(char *buf, size_t n, const char *format, va_list args) {
  return vsnprintf(buf, n, format, args);
}
