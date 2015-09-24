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

#include <openssl/crypto.h>

#include "internal.h"


#if !defined(OPENSSL_NO_ASM) && \
    (defined(OPENSSL_X86) || defined(OPENSSL_X86_64) || \
     defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64))
/* x86, x86_64 and the ARMs need to record the result of a cpuid call for the
 * asm to work correctly, unless compiled without asm code. */
#define NEED_CPUID

#else

/* Otherwise, don't emit a static initialiser. */

#if !defined(BORINGSSL_NO_STATIC_INITIALIZER)
#define BORINGSSL_NO_STATIC_INITIALIZER
#endif

#endif  /* !OPENSSL_NO_ASM && (OPENSSL_X86 || OPENSSL_X86_64 ||
                               OPENSSL_ARM || OPENSSL_AARCH64) */


/* The capability variables are defined in this file in order to work around a
 * linker bug. When linking with a .a, if no symbols in a .o are referenced
 * then the .o is discarded, even if it has constructor functions.
 *
 * This still means that any binaries that don't include some functionality
 * that tests the capability values will still skip the constructor but, so
 * far, the init constructor function only sets the capability variables. */

#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64)
/* This value must be explicitly initialised to zero in order to work around a
 * bug in libtool or the linker on OS X.
 *
 * If not initialised then it becomes a "common symbol". When put into an
 * archive, linking on OS X will fail to resolve common symbols. By
 * initialising it to zero, it becomes a "data symbol", which isn't so
 * affected. */
uint32_t OPENSSL_ia32cap_P[4] = {0};
#elif defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64)

#include "arm_arch.h"

#if defined(__ARM_NEON__)
uint32_t OPENSSL_armcap_P = ARMV7_NEON | ARMV7_NEON_FUNCTIONAL;
#else
uint32_t OPENSSL_armcap_P = ARMV7_NEON_FUNCTIONAL;
#endif

#endif


#if defined(OPENSSL_WINDOWS)
#define OPENSSL_CDECL __cdecl
#else
#define OPENSSL_CDECL
#endif

#if !defined(BORINGSSL_NO_STATIC_INITIALIZER)
#if !defined(OPENSSL_WINDOWS)
static void do_library_init(void) __attribute__ ((constructor));
#else
#pragma section(".CRT$XCU", read)
static void __cdecl do_library_init(void);
__declspec(allocate(".CRT$XCU")) void(*library_init_constructor)(void) =
    do_library_init;
#endif
#endif  /* !BORINGSSL_NO_STATIC_INITIALIZER */

/* do_library_init is the actual initialization function. If
 * BORINGSSL_NO_STATIC_INITIALIZER isn't defined, this is set as a static
 * initializer. Otherwise, it is called by CRYPTO_library_init. */
static void OPENSSL_CDECL do_library_init(void) {
 /* WARNING: this function may only configure the capability variables. See the
  * note above about the linker bug. */
#if defined(NEED_CPUID)
  OPENSSL_cpuid_setup();
#endif
}

void CRYPTO_library_init(void) {
  /* TODO(davidben): It would be tidier if this build knob could be replaced
   * with an internal lazy-init mechanism that would handle things correctly
   * in-library. */
#if defined(BORINGSSL_NO_STATIC_INITIALIZER)
  do_library_init();
#endif
}

const char *SSLeay_version(int unused) {
  return "BoringSSL";
}

unsigned long SSLeay(void) {
  return OPENSSL_VERSION_NUMBER;
}
