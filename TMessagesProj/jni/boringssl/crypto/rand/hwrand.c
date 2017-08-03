/* Copyright (c) 2015, Google Inc.
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

#include <openssl/rand.h>

#include <assert.h>
#include <string.h>

#include <openssl/cpu.h>

#include "internal.h"


#if defined(OPENSSL_X86_64) && !defined(OPENSSL_NO_ASM)

/* These functions are defined in asm/rdrand-x86_64.pl */
extern int CRYPTO_rdrand(uint8_t out[8]);
extern int CRYPTO_rdrand_multiple8_buf(uint8_t *buf, size_t len);

static int have_rdrand(void) {
  return (OPENSSL_ia32cap_P[1] & (1u << 30)) != 0;
}

int CRYPTO_hwrand(uint8_t *buf, size_t len) {
  if (!have_rdrand()) {
    return 0;
  }

  const size_t len_multiple8 = len & ~7;
  if (!CRYPTO_rdrand_multiple8_buf(buf, len_multiple8)) {
    return 0;
  }
  len -= len_multiple8;

  if (len != 0) {
    assert(len < 8);

    uint8_t rand_buf[8];
    if (!CRYPTO_rdrand(rand_buf)) {
      return 0;
    }
    memcpy(buf + len_multiple8, rand_buf, len);
  }

  return 1;
}

#else

int CRYPTO_hwrand(uint8_t *buf, size_t len) {
  return 0;
}

#endif
