/* Copyright (c) 2017, Google Inc.
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

#if defined(OPENSSL_FUCHSIA) && !defined(BORINGSSL_UNSAFE_DETERMINISTIC_MODE)

#include <limits.h>
#include <stdlib.h>

#include <zircon/syscalls.h>

#include "../fipsmodule/rand/internal.h"

void CRYPTO_sysrand(uint8_t *out, size_t requested) {
  while (requested > 0) {
    size_t output_bytes_this_pass = ZX_CPRNG_DRAW_MAX_LEN;
    if (requested < output_bytes_this_pass) {
      output_bytes_this_pass = requested;
    }
    size_t bytes_drawn;
    zx_status_t status =
        zx_cprng_draw(out, output_bytes_this_pass, &bytes_drawn);
    if (status != ZX_OK) {
      abort();
    }
    requested -= bytes_drawn;
    out += bytes_drawn;
  }
}

#endif  // OPENSSL_FUCHSIA && !BORINGSSL_UNSAFE_DETERMINISTIC_MODE
