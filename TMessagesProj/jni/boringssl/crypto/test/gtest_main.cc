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

#include <stdio.h>
#include <string.h>

#include <gtest/gtest.h>

#include <openssl/cpu.h>
#include <openssl/rand.h>

#include "abi_test.h"
#include "gtest_main.h"
#include "../internal.h"

#if (defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64)) &&       \
    !defined(OPENSSL_STATIC_ARMCAP)
#include <openssl/arm_arch.h>
#define TEST_ARM_CPUS
#endif


int main(int argc, char **argv) {
  testing::InitGoogleTest(&argc, argv);
  bssl::SetupGoogleTest();

  bool unwind_tests = true;
  for (int i = 1; i < argc; i++) {
#if !defined(OPENSSL_WINDOWS)
    if (strcmp(argv[i], "--fork_unsafe_buffering") == 0) {
      RAND_enable_fork_unsafe_buffering(-1);
    }
#endif

#if defined(TEST_ARM_CPUS)
    if (strncmp(argv[i], "--cpu=", 6) == 0) {
      const char *cpu = argv[i] + 6;
      uint32_t armcap;
      if (strcmp(cpu, "none") == 0) {
        armcap = 0;
      } else if (strcmp(cpu, "neon") == 0) {
        armcap = ARMV7_NEON;
      } else if (strcmp(cpu, "crypto") == 0) {
        armcap = ARMV7_NEON | ARMV8_AES | ARMV8_SHA1 | ARMV8_SHA256 | ARMV8_PMULL;
      } else {
        fprintf(stderr, "Unknown CPU: %s\n", cpu);
        exit(1);
      }

      uint32_t *armcap_ptr = OPENSSL_get_armcap_pointer_for_test();
      if ((armcap & *armcap_ptr) != armcap) {
        fprintf(stderr,
                "Host CPU does not support features for testing CPU '%s'.\n",
                cpu);
        exit(89);
      }
      printf("Simulating CPU '%s'\n", cpu);
      *armcap_ptr = armcap;
    }
#endif  // TEST_ARM_CPUS

    if (strcmp(argv[i], "--no_unwind_tests") == 0) {
      unwind_tests = false;
    }
  }

  if (unwind_tests) {
    abi_test::EnableUnwindTests();
  }

  // Run the entire test suite under an ABI check. This is less effective than
  // testing the individual assembly functions, but will catch issues with
  // rarely-used registers.
  abi_test::Result abi;
  int ret = abi_test::Check(&abi, RUN_ALL_TESTS);
  if (!abi.ok()) {
    fprintf(stderr, "ABI failure in test suite:\n");
    for (const auto &error : abi.errors) {
      fprintf(stderr, "    %s\n", error.c_str());
    }
    exit(1);
  }
  return ret;
}
