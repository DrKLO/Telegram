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

#include "internal.h"

#include <stdlib.h>

#include <openssl/type_check.h>


#if !defined(OPENSSL_C11_ATOMIC)

OPENSSL_STATIC_ASSERT((CRYPTO_refcount_t)-1 == CRYPTO_REFCOUNT_MAX,
                      "CRYPTO_REFCOUNT_MAX is incorrect");

static struct CRYPTO_STATIC_MUTEX g_refcount_lock = CRYPTO_STATIC_MUTEX_INIT;

void CRYPTO_refcount_inc(CRYPTO_refcount_t *count) {
  CRYPTO_STATIC_MUTEX_lock_write(&g_refcount_lock);
  if (*count < CRYPTO_REFCOUNT_MAX) {
    (*count)++;
  }
  CRYPTO_STATIC_MUTEX_unlock_write(&g_refcount_lock);
}

int CRYPTO_refcount_dec_and_test_zero(CRYPTO_refcount_t *count) {
  int ret;

  CRYPTO_STATIC_MUTEX_lock_write(&g_refcount_lock);
  if (*count == 0) {
    abort();
  }
  if (*count < CRYPTO_REFCOUNT_MAX) {
    (*count)--;
  }
  ret = (*count == 0);
  CRYPTO_STATIC_MUTEX_unlock_write(&g_refcount_lock);

  return ret;
}

#endif  // OPENSSL_C11_ATOMIC
