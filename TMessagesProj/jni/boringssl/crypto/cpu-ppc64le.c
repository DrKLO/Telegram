/* Copyright (c) 2016, Google Inc.
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

#if defined(OPENSSL_PPC64LE)

#include <sys/auxv.h>

#include "internal.h"


#if !defined(PPC_FEATURE2_HAS_VCRYPTO)
// PPC_FEATURE2_HAS_VCRYPTO was taken from section 4.1.2.3 of the “OpenPOWER
// ABI for Linux Supplement”.
#define PPC_FEATURE2_HAS_VCRYPTO 0x02000000
#endif

void OPENSSL_cpuid_setup(void) {
  OPENSSL_ppc64le_hwcap2 = getauxval(AT_HWCAP2);
}

int CRYPTO_is_PPC64LE_vcrypto_capable(void) {
  return (OPENSSL_ppc64le_hwcap2 & PPC_FEATURE2_HAS_VCRYPTO) != 0;
}

#endif  // OPENSSL_PPC64LE
