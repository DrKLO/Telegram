// Copyright 2015 The BoringSSL Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "internal.h"

#include <assert.h>
#include <stdlib.h>


// See comment above the typedef of CRYPTO_refcount_t about these tests.
static_assert(alignof(CRYPTO_refcount_t) == alignof(CRYPTO_atomic_u32),
              "CRYPTO_refcount_t does not match CRYPTO_atomic_u32 alignment");
static_assert(sizeof(CRYPTO_refcount_t) == sizeof(CRYPTO_atomic_u32),
              "CRYPTO_refcount_t does not match CRYPTO_atomic_u32 size");

static_assert((CRYPTO_refcount_t)-1 == CRYPTO_REFCOUNT_MAX,
              "CRYPTO_REFCOUNT_MAX is incorrect");

void CRYPTO_refcount_inc(CRYPTO_refcount_t *in_count) {
  CRYPTO_atomic_u32 *count = (CRYPTO_atomic_u32 *)in_count;
  uint32_t expected = CRYPTO_atomic_load_u32(count);

  while (expected != CRYPTO_REFCOUNT_MAX) {
    uint32_t new_value = expected + 1;
    if (CRYPTO_atomic_compare_exchange_weak_u32(count, &expected, new_value)) {
      break;
    }
  }
}

int CRYPTO_refcount_dec_and_test_zero(CRYPTO_refcount_t *in_count) {
  CRYPTO_atomic_u32 *count = (CRYPTO_atomic_u32 *)in_count;
  uint32_t expected = CRYPTO_atomic_load_u32(count);

  for (;;) {
    if (expected == 0) {
      abort();
    } else if (expected == CRYPTO_REFCOUNT_MAX) {
      return 0;
    } else {
      const uint32_t new_value = expected - 1;
      if (CRYPTO_atomic_compare_exchange_weak_u32(count, &expected,
                                                  new_value)) {
        return new_value == 0;
      }
    }
  }
}
