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

#if !defined(_GNU_SOURCE)
#define _GNU_SOURCE  // needed for syscall() on Linux.
#endif

#include <openssl/crypto.h>

#include <stdlib.h>
#if defined(BORINGSSL_FIPS)
#include <sys/mman.h>
#include <unistd.h>
#endif

#include <openssl/digest.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>

#include "../internal.h"

#include "aes/aes.c"
#include "aes/key_wrap.c"
#include "aes/mode_wrappers.c"
#include "bn/add.c"
#include "bn/asm/x86_64-gcc.c"
#include "bn/bn.c"
#include "bn/bytes.c"
#include "bn/cmp.c"
#include "bn/ctx.c"
#include "bn/div.c"
#include "bn/div_extra.c"
#include "bn/exponentiation.c"
#include "bn/gcd.c"
#include "bn/gcd_extra.c"
#include "bn/generic.c"
#include "bn/jacobi.c"
#include "bn/montgomery.c"
#include "bn/montgomery_inv.c"
#include "bn/mul.c"
#include "bn/prime.c"
#include "bn/random.c"
#include "bn/rsaz_exp.c"
#include "bn/shift.c"
#include "bn/sqrt.c"
#include "cipher/aead.c"
#include "cipher/cipher.c"
#include "cipher/e_aes.c"
#include "cipher/e_des.c"
#include "des/des.c"
#include "digest/digest.c"
#include "digest/digests.c"
#include "ecdh/ecdh.c"
#include "ecdsa/ecdsa.c"
#include "ec/ec.c"
#include "ec/ec_key.c"
#include "ec/ec_montgomery.c"
#include "ec/felem.c"
#include "ec/oct.c"
#include "ec/p224-64.c"
#include "../../third_party/fiat/p256.c"
#include "ec/p256-x86_64.c"
#include "ec/scalar.c"
#include "ec/simple.c"
#include "ec/simple_mul.c"
#include "ec/util.c"
#include "ec/wnaf.c"
#include "hmac/hmac.c"
#include "md4/md4.c"
#include "md5/md5.c"
#include "modes/cbc.c"
#include "modes/cfb.c"
#include "modes/ctr.c"
#include "modes/gcm.c"
#include "modes/ofb.c"
#include "modes/polyval.c"
#include "rand/ctrdrbg.c"
#include "rand/rand.c"
#include "rand/urandom.c"
#include "rsa/blinding.c"
#include "rsa/padding.c"
#include "rsa/rsa.c"
#include "rsa/rsa_impl.c"
#include "self_check/self_check.c"
#include "sha/sha1-altivec.c"
#include "sha/sha1.c"
#include "sha/sha256.c"
#include "sha/sha512.c"
#include "tls/kdf.c"


#if defined(BORINGSSL_FIPS)

#if !defined(OPENSSL_ASAN)

// These symbols are filled in by delocate.go (in static builds) or a linker
// script (in shared builds). They point to the start and end of the module, and
// the location of the integrity hash, respectively.
extern const uint8_t BORINGSSL_bcm_text_start[];
extern const uint8_t BORINGSSL_bcm_text_end[];
extern const uint8_t BORINGSSL_bcm_text_hash[];
#if defined(BORINGSSL_SHARED_LIBRARY)
extern const uint8_t BORINGSSL_bcm_rodata_start[];
extern const uint8_t BORINGSSL_bcm_rodata_end[];
#endif

#if defined(OPENSSL_ANDROID)
static void BORINGSSL_maybe_set_module_text_permissions(int permission) {
  // Android may be compiled in execute-only-memory mode, in which case the
  // .text segment cannot be read. That conflicts with the need for a FIPS
  // module to hash its own contents, therefore |mprotect| is used to make
  // the module's .text readable for the duration of the hashing process. In
  // other build configurations this is a no-op.
  const uintptr_t page_size = getpagesize();
  const uintptr_t page_start =
      ((uintptr_t)BORINGSSL_bcm_text_start) & ~(page_size - 1);

  if (mprotect((void *)page_start,
               ((uintptr_t)BORINGSSL_bcm_text_end) - page_start,
               permission) != 0) {
    perror("BoringSSL: mprotect");
  }
}
#else
static void BORINGSSL_maybe_set_module_text_permissions(int permission) {}
#endif  // !ANDROID

#else
static const uint8_t BORINGSSL_bcm_text_hash[SHA512_DIGEST_LENGTH] = {0};
#endif  // !ASAN

static void __attribute__((constructor))
BORINGSSL_bcm_power_on_self_test(void) {
  CRYPTO_library_init();

#if !defined(OPENSSL_ASAN)
  // Integrity tests cannot run under ASAN because it involves reading the full
  // .text section, which triggers the global-buffer overflow detection.
  const uint8_t *const start = BORINGSSL_bcm_text_start;
  const uint8_t *const end = BORINGSSL_bcm_text_end;
#if defined(BORINGSSL_SHARED_LIBRARY)
  const uint8_t *const rodata_start = BORINGSSL_bcm_rodata_start;
  const uint8_t *const rodata_end = BORINGSSL_bcm_rodata_end;
#endif

  static const uint8_t kHMACKey[64] = {0};
  uint8_t result[SHA512_DIGEST_LENGTH];

  unsigned result_len;
  HMAC_CTX hmac_ctx;
  HMAC_CTX_init(&hmac_ctx);
  if (!HMAC_Init_ex(&hmac_ctx, kHMACKey, sizeof(kHMACKey), EVP_sha512(),
                    NULL /* no ENGINE */)) {
    fprintf(stderr, "HMAC_Init_ex failed.\n");
    goto err;
  }

  BORINGSSL_maybe_set_module_text_permissions(PROT_READ | PROT_EXEC);
#if defined(BORINGSSL_SHARED_LIBRARY)
  uint64_t length = end - start;
  HMAC_Update(&hmac_ctx, (const uint8_t *) &length, sizeof(length));
  HMAC_Update(&hmac_ctx, start, length);

  length = rodata_end - rodata_start;
  HMAC_Update(&hmac_ctx, (const uint8_t *) &length, sizeof(length));
  HMAC_Update(&hmac_ctx, rodata_start, length);
#else
  HMAC_Update(&hmac_ctx, start, end - start);
#endif
  BORINGSSL_maybe_set_module_text_permissions(PROT_EXEC);

  if (!HMAC_Final(&hmac_ctx, result, &result_len) ||
      result_len != sizeof(result)) {
    fprintf(stderr, "HMAC failed.\n");
    goto err;
  }
  HMAC_CTX_cleanup(&hmac_ctx);

  const uint8_t *expected = BORINGSSL_bcm_text_hash;

  if (!check_test(expected, result, sizeof(result), "FIPS integrity test")) {
    goto err;
  }
#endif

  if (!BORINGSSL_self_test(BORINGSSL_bcm_text_hash)) {
    goto err;
  }

  return;

err:
  BORINGSSL_FIPS_abort();
}

void BORINGSSL_FIPS_abort(void) {
  for (;;) {
    abort();
    exit(1);
  }
}

#endif  // BORINGSSL_FIPS
