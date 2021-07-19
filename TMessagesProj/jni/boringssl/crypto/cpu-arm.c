/* Copyright (c) 2014, Google Inc.
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

#include <openssl/cpu.h>

#if (defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64)) && \
    !defined(OPENSSL_STATIC_ARMCAP)

#include <openssl/arm_arch.h>


extern uint32_t OPENSSL_armcap_P;

char CRYPTO_is_NEON_capable_at_runtime(void) {
  return (OPENSSL_armcap_P & ARMV7_NEON) != 0;
}

int CRYPTO_is_ARMv8_AES_capable(void) {
  return (OPENSSL_armcap_P & ARMV8_AES) != 0;
}

int CRYPTO_is_ARMv8_PMULL_capable(void) {
  return (OPENSSL_armcap_P & ARMV8_PMULL) != 0;
}

#endif  /* (defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64)) &&
           !defined(OPENSSL_STATIC_ARMCAP) */
