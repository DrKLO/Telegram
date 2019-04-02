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

#include <openssl/cpu.h>

#include "internal.h"


#if !defined(OPENSSL_NO_ASM) && !defined(OPENSSL_STATIC_ARMCAP) && \
    (defined(OPENSSL_X86) || defined(OPENSSL_X86_64) || \
     defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64) || \
     defined(OPENSSL_PPC64LE))
// x86, x86_64, the ARMs and ppc64le need to record the result of a
// cpuid/getauxval call for the asm to work correctly, unless compiled without
// asm code.
#define NEED_CPUID

#else

// Otherwise, don't emit a static initialiser.

#if !defined(BORINGSSL_NO_STATIC_INITIALIZER)
#define BORINGSSL_NO_STATIC_INITIALIZER
#endif

#endif  /* !OPENSSL_NO_ASM && (OPENSSL_X86 || OPENSSL_X86_64 ||
                               OPENSSL_ARM || OPENSSL_AARCH64) */


// Our assembly does not use the GOT to reference symbols, which means
// references to visible symbols will often require a TEXTREL. This is
// undesirable, so all assembly-referenced symbols should be hidden. CPU
// capabilities are the only such symbols defined in C. Explicitly hide them,
// rather than rely on being built with -fvisibility=hidden.
#if defined(OPENSSL_WINDOWS)
#define HIDDEN
#else
#define HIDDEN __attribute__((visibility("hidden")))
#endif


// The capability variables are defined in this file in order to work around a
// linker bug. When linking with a .a, if no symbols in a .o are referenced
// then the .o is discarded, even if it has constructor functions.
//
// This still means that any binaries that don't include some functionality
// that tests the capability values will still skip the constructor but, so
// far, the init constructor function only sets the capability variables.

#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64)

// This value must be explicitly initialised to zero in order to work around a
// bug in libtool or the linker on OS X.
//
// If not initialised then it becomes a "common symbol". When put into an
// archive, linking on OS X will fail to resolve common symbols. By
// initialising it to zero, it becomes a "data symbol", which isn't so
// affected.
HIDDEN uint32_t OPENSSL_ia32cap_P[4] = {0};

#elif defined(OPENSSL_PPC64LE)

HIDDEN unsigned long OPENSSL_ppc64le_hwcap2 = 0;

#elif defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64)

#include <openssl/arm_arch.h>

#if defined(OPENSSL_STATIC_ARMCAP)

HIDDEN uint32_t OPENSSL_armcap_P =
#if defined(OPENSSL_STATIC_ARMCAP_NEON) || defined(__ARM_NEON__)
    ARMV7_NEON |
#endif
#if defined(OPENSSL_STATIC_ARMCAP_AES) || defined(__ARM_FEATURE_CRYPTO)
    ARMV8_AES |
#endif
#if defined(OPENSSL_STATIC_ARMCAP_SHA1) || defined(__ARM_FEATURE_CRYPTO)
    ARMV8_SHA1 |
#endif
#if defined(OPENSSL_STATIC_ARMCAP_SHA256) || defined(__ARM_FEATURE_CRYPTO)
    ARMV8_SHA256 |
#endif
#if defined(OPENSSL_STATIC_ARMCAP_PMULL) || defined(__ARM_FEATURE_CRYPTO)
    ARMV8_PMULL |
#endif
    0;

#else
HIDDEN uint32_t OPENSSL_armcap_P = 0;
#endif

#endif

#if defined(BORINGSSL_FIPS)
// In FIPS mode, the power-on self-test function calls |CRYPTO_library_init|
// because we have to ensure that CPUID detection occurs first.
#define BORINGSSL_NO_STATIC_INITIALIZER
#endif

#if defined(OPENSSL_WINDOWS) && !defined(BORINGSSL_NO_STATIC_INITIALIZER)
#define OPENSSL_CDECL __cdecl
#else
#define OPENSSL_CDECL
#endif

#if defined(BORINGSSL_NO_STATIC_INITIALIZER)
static CRYPTO_once_t once = CRYPTO_ONCE_INIT;
#elif defined(_MSC_VER)
#pragma section(".CRT$XCU", read)
static void __cdecl do_library_init(void);
__declspec(allocate(".CRT$XCU")) void(*library_init_constructor)(void) =
    do_library_init;
#else
static void do_library_init(void) __attribute__ ((constructor));
#endif

// do_library_init is the actual initialization function. If
// BORINGSSL_NO_STATIC_INITIALIZER isn't defined, this is set as a static
// initializer. Otherwise, it is called by CRYPTO_library_init.
static void OPENSSL_CDECL do_library_init(void) {
 // WARNING: this function may only configure the capability variables. See the
 // note above about the linker bug.
#if defined(NEED_CPUID)
  OPENSSL_cpuid_setup();
#endif
}

void CRYPTO_library_init(void) {
  // TODO(davidben): It would be tidier if this build knob could be replaced
  // with an internal lazy-init mechanism that would handle things correctly
  // in-library. https://crbug.com/542879
#if defined(BORINGSSL_NO_STATIC_INITIALIZER)
  CRYPTO_once(&once, do_library_init);
#endif
}

int CRYPTO_is_confidential_build(void) {
#if defined(BORINGSSL_CONFIDENTIAL)
  return 1;
#else
  return 0;
#endif
}

int CRYPTO_has_asm(void) {
#if defined(OPENSSL_NO_ASM)
  return 0;
#else
  return 1;
#endif
}

const char *SSLeay_version(int unused) {
  return "BoringSSL";
}

const char *OpenSSL_version(int unused) {
  return "BoringSSL";
}

unsigned long SSLeay(void) {
  return OPENSSL_VERSION_NUMBER;
}

unsigned long OpenSSL_version_num(void) {
  return OPENSSL_VERSION_NUMBER;
}

int CRYPTO_malloc_init(void) {
  return 1;
}

void ENGINE_load_builtin_engines(void) {}

int ENGINE_register_all_complete(void) {
  return 1;
}

void OPENSSL_load_builtin_modules(void) {}

int OPENSSL_init_crypto(uint64_t opts, const OPENSSL_INIT_SETTINGS *settings) {
  CRYPTO_library_init();
  return 1;
}
