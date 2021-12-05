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

// cavp_ecdsa2_keypair_test processes a NIST CAVP ECDSA2 KeyPair test vector
// request file and emits the corresponding response.

#include <stdlib.h>

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/ec_key.h>
#include <openssl/err.h>
#include <openssl/nid.h>

#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


static bool TestECDSA2KeyPair(FileTest *t, void *arg) {
  std::string n_str;
  const char *group_str;
  int nid = GetECGroupNIDFromInstruction(t, &group_str);
  if (nid == NID_undef ||
      !t->GetAttribute(&n_str, "N")) {
    return false;
  }

  // Don't use CurrentTestToString to avoid printing the N.
  printf(
      "[%s]\r\n\r\n[B.4.2 Key Pair Generation by Testing Candidates]\r\n\r\n",
      group_str);

  unsigned long n = strtoul(n_str.c_str(), nullptr, 10);
  for (unsigned long i = 0; i < n; i++) {
    bssl::UniquePtr<BIGNUM> qx(BN_new()), qy(BN_new());
    bssl::UniquePtr<EC_KEY> key(EC_KEY_new_by_curve_name(nid));
    if (!key ||
        !EC_KEY_generate_key_fips(key.get()) ||
        !EC_POINT_get_affine_coordinates_GFp(EC_KEY_get0_group(key.get()),
                                             EC_KEY_get0_public_key(key.get()),
                                             qx.get(), qy.get(), nullptr)) {
      return false;
    }

    size_t degree_len =
        (EC_GROUP_get_degree(EC_KEY_get0_group(key.get())) + 7) / 8;
    size_t order_len =
        BN_num_bytes(EC_GROUP_get0_order(EC_KEY_get0_group(key.get())));
    std::vector<uint8_t> qx_bytes(degree_len), qy_bytes(degree_len);
    std::vector<uint8_t> d_bytes(order_len);
    if (!BN_bn2bin_padded(qx_bytes.data(), qx_bytes.size(), qx.get()) ||
        !BN_bn2bin_padded(qy_bytes.data(), qy_bytes.size(), qy.get()) ||
        !BN_bn2bin_padded(d_bytes.data(), d_bytes.size(),
                          EC_KEY_get0_private_key(key.get()))) {
      return false;
    }

    printf("d = %s\r\nQx = %s\r\nQy = %s\r\n\r\n",
           EncodeHex(d_bytes.data(), d_bytes.size()).c_str(),
           EncodeHex(qx_bytes.data(), qx_bytes.size()).c_str(),
           EncodeHex(qy_bytes.data(), qy_bytes.size()).c_str());
  }

  return true;
}

int cavp_ecdsa2_keypair_test_main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "usage: %s <test file>\n",
            argv[0]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[1];
  opts.callback = TestECDSA2KeyPair;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
