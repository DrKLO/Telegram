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

// cavp_main is a wrapper that invokes the main entry function of one of the
// CAVP validation suite binaries.

#include <stdlib.h>
#include <cstdio>
#include <string>

#include <openssl/crypto.h>

#include "cavp_test_util.h"


static int usage(char *arg) {
  fprintf(stderr, "usage: %s <validation suite> <args ...>\n", arg);
  return 1;
}

struct TestSuite {
  std::string name;
  int (*main_func)(int argc, char **argv);
};

static TestSuite all_test_suites[] = {
    {"aes", &cavp_aes_test_main},
    {"aes_gcm", &cavp_aes_gcm_test_main},
    {"ctr_drbg", &cavp_ctr_drbg_test_main},
    {"ecdsa2_keypair", &cavp_ecdsa2_keypair_test_main},
    {"ecdsa2_pkv", &cavp_ecdsa2_pkv_test_main},
    {"ecdsa2_siggen", &cavp_ecdsa2_siggen_test_main},
    {"ecdsa2_sigver", &cavp_ecdsa2_sigver_test_main},
    {"hmac", &cavp_hmac_test_main},
    {"kas", &cavp_kas_test_main},
    {"keywrap", &cavp_keywrap_test_main},
    {"rsa2_keygen", &cavp_rsa2_keygen_test_main},
    {"rsa2_siggen", &cavp_rsa2_siggen_test_main},
    {"rsa2_sigver", &cavp_rsa2_sigver_test_main},
    {"tlskdf", &cavp_tlskdf_test_main},
    {"sha", &cavp_sha_test_main},
    {"sha_monte", &cavp_sha_monte_test_main},
    {"tdes", &cavp_tdes_test_main}
};

int main(int argc, char **argv) {
  CRYPTO_library_init();

  if (argc < 3) {
    return usage(argv[0]);
  }

  const std::string suite(argv[1]);
  for (const TestSuite &s : all_test_suites) {
    if (s.name == suite) {
      return s.main_func(argc - 1, &argv[1]);
    }
  }

  fprintf(stderr, "invalid test suite: %s\n\n", argv[1]);
  return usage(argv[0]);
}
