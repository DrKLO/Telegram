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

// cavp_rsa2_keygen_test processes NIST CAVP RSA2 KeyGen test vector request
// files and emits the corresponding response.

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/rsa.h>

#include "../crypto/internal.h"
#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


static bool TestRSA2KeyGen(FileTest *t, void *arg) {
  std::string mod_str, table, count_str;
  if (!t->GetInstruction(&mod_str, "mod") ||
      !t->GetInstruction(&table, "Table for M-R Test") ||
      table != "C.2" ||
      !t->GetAttribute(&count_str, "N")) {
    return false;
  }

  printf("[mod = %s]\r\n", mod_str.c_str());
  printf("[Table for M-R Test = %s]\r\n\r\n", table.c_str());

  size_t bits = strtoul(mod_str.c_str(), nullptr, 0);
  size_t count = strtoul(count_str.c_str(), nullptr, 0);
  for (size_t i = 0; i < count; i++) {
    bssl::UniquePtr<RSA> key(RSA_new());
    if (key == nullptr ||
        bits == 0 ||
        !RSA_generate_key_fips(key.get(), bits, nullptr)) {
      return 0;
    }

    const BIGNUM *n, *e, *d, *p, *q;
    RSA_get0_key(key.get(), &n, &e, &d);
    RSA_get0_factors(key.get(), &p, &q);
    std::vector<uint8_t> n_bytes(BN_num_bytes(n)), e_bytes(BN_num_bytes(e)),
        d_bytes((bits + 7) / 8), p_bytes(BN_num_bytes(p)),
        q_bytes(BN_num_bytes(q));
    if (n == NULL ||
        BN_bn2bin(n, n_bytes.data()) != n_bytes.size() ||
        e == NULL ||
        BN_bn2bin(e, e_bytes.data()) != e_bytes.size() ||
        d == NULL ||
        !BN_bn2bin_padded(d_bytes.data(), d_bytes.size(), d) ||
        p == NULL ||
        BN_bn2bin(p, p_bytes.data()) != p_bytes.size() ||
        q == NULL ||
        BN_bn2bin(q, q_bytes.data()) != q_bytes.size()) {
      return false;
    }

    printf("e = %s\r\np = %s\r\nq = %s\r\nn = %s\r\nd = %s\r\n\r\n",
           EncodeHex(e_bytes.data(), e_bytes.size()).c_str(),
           EncodeHex(p_bytes.data(), p_bytes.size()).c_str(),
           EncodeHex(q_bytes.data(), q_bytes.size()).c_str(),
           EncodeHex(n_bytes.data(), n_bytes.size()).c_str(),
           EncodeHex(d_bytes.data(), d_bytes.size()).c_str());
  }

  return true;
}

int cavp_rsa2_keygen_test_main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "usage: %s <test file>\n",
            argv[0]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[1];
  opts.callback = TestRSA2KeyGen;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
