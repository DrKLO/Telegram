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

#if defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64)

#include <inttypes.h>
#include <string.h>

#if !defined(OPENSSL_TRUSTY)
#include <setjmp.h>
#include <signal.h>
#endif

#include "arm_arch.h"


/* We can't include <sys/auxv.h> because the Android SDK version against which
 * Chromium builds is too old to have it. Instead we define all the constants
 * that we need and have a weak pointer to getauxval. */

unsigned long getauxval(unsigned long type) __attribute__((weak));

char CRYPTO_is_NEON_capable(void) {
  return (OPENSSL_armcap_P & ARMV7_NEON) != 0;
}

static char g_set_neon_called = 0;

void CRYPTO_set_NEON_capable(char neon_capable) {
  g_set_neon_called = 1;

  if (neon_capable) {
    OPENSSL_armcap_P |= ARMV7_NEON;
  } else {
    OPENSSL_armcap_P &= ~ARMV7_NEON;
  }
}

char CRYPTO_is_NEON_functional(void) {
  static const uint32_t kWantFlags = ARMV7_NEON | ARMV7_NEON_FUNCTIONAL;
  return (OPENSSL_armcap_P & kWantFlags) == kWantFlags;
}

void CRYPTO_set_NEON_functional(char neon_functional) {
  if (neon_functional) {
    OPENSSL_armcap_P |= ARMV7_NEON_FUNCTIONAL;
  } else {
    OPENSSL_armcap_P &= ~ARMV7_NEON_FUNCTIONAL;
  }
}

#if !defined(OPENSSL_NO_ASM) && defined(OPENSSL_ARM) && !defined(OPENSSL_TRUSTY)

static sigjmp_buf sigill_jmp;

static void sigill_handler(int signal) {
  siglongjmp(sigill_jmp, signal);
}

void CRYPTO_arm_neon_probe(void);

// probe_for_NEON returns 1 if a NEON instruction runs successfully. Because
// getauxval doesn't exist on Android until Jelly Bean, supporting NEON on
// older devices requires this.
static int probe_for_NEON(void) {
  int supported = 0;

  sigset_t sigmask;
  sigfillset(&sigmask);
  sigdelset(&sigmask, SIGILL);
  sigdelset(&sigmask, SIGTRAP);
  sigdelset(&sigmask, SIGFPE);
  sigdelset(&sigmask, SIGBUS);
  sigdelset(&sigmask, SIGSEGV);

  struct sigaction sigill_original_action, sigill_action;
  memset(&sigill_action, 0, sizeof(sigill_action));
  sigill_action.sa_handler = sigill_handler;
  sigill_action.sa_mask = sigmask;

  sigset_t original_sigmask;
  sigprocmask(SIG_SETMASK, &sigmask, &original_sigmask);

  if (sigsetjmp(sigill_jmp, 1 /* save signals */) == 0) {
    sigaction(SIGILL, &sigill_action, &sigill_original_action);

    // This function cannot be inline asm because GCC will refuse to compile
    // inline NEON instructions unless building with -mfpu=neon, which would
    // defeat the point of probing for support at runtime.
    CRYPTO_arm_neon_probe();
    supported = 1;
  }
  // Note that Android up to and including Lollipop doesn't restore the signal
  // mask correctly after returning from a sigsetjmp. So that would need to be
  // set again here if more probes were added.
  // See https://android-review.googlesource.com/#/c/127624/

  sigaction(SIGILL, &sigill_original_action, NULL);
  sigprocmask(SIG_SETMASK, &original_sigmask, NULL);

  return supported;
}

#else

static int probe_for_NEON(void) {
  return 0;
}

#endif  /* !OPENSSL_NO_ASM && OPENSSL_ARM && !OPENSSL_TRUSTY */

void OPENSSL_cpuid_setup(void) {
  if (getauxval == NULL) {
    // On ARM, but not AArch64, try a NEON instruction and see whether it works
    // in order to probe for NEON support.
    //
    // Note that |CRYPTO_is_NEON_capable| can be true even if
    // |CRYPTO_set_NEON_capable| has never been called if the code was compiled
    // with NEON support enabled (e.g. -mfpu=neon).
    if (!g_set_neon_called && !CRYPTO_is_NEON_capable() && probe_for_NEON()) {
      OPENSSL_armcap_P |= ARMV7_NEON;
    }
    return;
  }

  static const unsigned long AT_HWCAP = 16;
  unsigned long hwcap = getauxval(AT_HWCAP);

#if defined(OPENSSL_ARM)
  static const unsigned long kNEON = 1 << 12;
  if ((hwcap & kNEON) == 0) {
    return;
  }

  /* In 32-bit mode, the ARMv8 feature bits are in a different aux vector
   * value. */
  static const unsigned long AT_HWCAP2 = 26;
  hwcap = getauxval(AT_HWCAP2);

  /* See /usr/include/asm/hwcap.h on an ARM installation for the source of
   * these values. */
  static const unsigned long kAES = 1 << 0;
  static const unsigned long kPMULL = 1 << 1;
  static const unsigned long kSHA1 = 1 << 2;
  static const unsigned long kSHA256 = 1 << 3;
#elif defined(OPENSSL_AARCH64)
  /* See /usr/include/asm/hwcap.h on an aarch64 installation for the source of
   * these values. */
  static const unsigned long kNEON = 1 << 1;
  static const unsigned long kAES = 1 << 3;
  static const unsigned long kPMULL = 1 << 4;
  static const unsigned long kSHA1 = 1 << 5;
  static const unsigned long kSHA256 = 1 << 6;

  if ((hwcap & kNEON) == 0) {
    return;
  }
#endif

  OPENSSL_armcap_P |= ARMV7_NEON;

  if (hwcap & kAES) {
    OPENSSL_armcap_P |= ARMV8_AES;
  }
  if (hwcap & kPMULL) {
    OPENSSL_armcap_P |= ARMV8_PMULL;
  }
  if (hwcap & kSHA1) {
    OPENSSL_armcap_P |= ARMV8_SHA1;
  }
  if (hwcap & kSHA256) {
    OPENSSL_armcap_P |= ARMV8_SHA256;
  }
}

#endif  /* defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64) */
