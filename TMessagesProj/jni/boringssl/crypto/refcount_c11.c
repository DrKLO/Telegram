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


#if defined(OPENSSL_C11_ATOMIC)

#include <assert.h>
#include <stdalign.h>
#include <stdatomic.h>
#include <stdlib.h>

#include <openssl/type_check.h>


/* See comment above the typedef of CRYPTO_refcount_t about these tests. */
static_assert(alignof(CRYPTO_refcount_t) == alignof(_Atomic CRYPTO_refcount_t),
              "_Atomic alters the needed alignment of a reference count");
static_assert(sizeof(CRYPTO_refcount_t) == sizeof(_Atomic CRYPTO_refcount_t),
              "_Atomic alters the size of a reference count");

static_assert((CRYPTO_refcount_t)-1 == CRYPTO_REFCOUNT_MAX,
              "CRYPTO_REFCOUNT_MAX is incorrect");

void CRYPTO_refcount_inc(CRYPTO_refcount_t *in_count) {
  _Atomic CRYPTO_refcount_t *count = (_Atomic CRYPTO_refcount_t *) in_count;
  uint32_t expected = atomic_load(count);

  while (expected != CRYPTO_REFCOUNT_MAX) {
    uint32_t new_value = expected + 1;
    if (atomic_compare_exchange_weak(count, &expected, new_value)) {
      break;
    }
  }
}

int CRYPTO_refcount_dec_and_test_zero(CRYPTO_refcount_t *in_count) {
  _Atomic CRYPTO_refcount_t *count = (_Atomic CRYPTO_refcount_t *)in_count;
  uint32_t expected = atomic_load(count);

  for (;;) {
    if (expected == 0) {
      abort();
    } else if (expected == CRYPTO_REFCOUNT_MAX) {
      return 0;
    } else {
      const uint32_t new_value = expected - 1;
      if (atomic_compare_exchange_weak(count, &expected, new_value)) {
        return new_value == 0;
      }
    }
  }
}

#endif  /* OPENSSL_C11_ATOMIC */
