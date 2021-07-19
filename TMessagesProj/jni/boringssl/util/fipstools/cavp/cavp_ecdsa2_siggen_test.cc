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

// cavp_ecdsa2_siggen_test processes NIST CAVP ECDSA2 SigGen and
// SigGenComponent test vector request files and emits the corresponding
// response.

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/digest.h>
#include <openssl/ec_key.h>
#include <openssl/ecdsa.h>
#include <openssl/err.h>
#include <openssl/nid.h>

#include "../crypto/internal.h"
#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


static bool TestECDSA2SigGenImpl(FileTest *t, bool is_component) {
  int nid = GetECGroupNIDFromInstruction(t);
  const EVP_MD *md = GetDigestFromInstruction(t);
  if (nid == NID_undef || md == nullptr) {
    return false;
  }
  bssl::UniquePtr<BIGNUM> qx(BN_new()), qy(BN_new());
  bssl::UniquePtr<EC_KEY> key(EC_KEY_new_by_curve_name(nid));
  std::vector<uint8_t> msg;
  if (!qx || !qy || !key ||
      !EC_KEY_generate_key_fips(key.get()) ||
      !EC_POINT_get_affine_coordinates_GFp(EC_KEY_get0_group(key.get()),
                                           EC_KEY_get0_public_key(key.get()),
                                           qx.get(), qy.get(), nullptr) ||
      !t->GetBytes(&msg, "Msg")) {
    return false;
  }

  uint8_t digest[EVP_MAX_MD_SIZE];
  unsigned digest_len;
  if (is_component) {
    if (msg.size() != EVP_MD_size(md)) {
      t->PrintLine("Bad input length.");
      return false;
    }
    digest_len = EVP_MD_size(md);
    OPENSSL_memcpy(digest, msg.data(), msg.size());
  } else if (!EVP_Digest(msg.data(), msg.size(), digest, &digest_len, md,
                         nullptr)) {
    return false;
  }

  bssl::UniquePtr<ECDSA_SIG> sig(ECDSA_do_sign(digest, digest_len, key.get()));
  if (!sig) {
    return false;
  }

  size_t degree_len =
      (EC_GROUP_get_degree(EC_KEY_get0_group(key.get())) + 7) / 8;
  size_t order_len =
      BN_num_bytes(EC_GROUP_get0_order(EC_KEY_get0_group(key.get())));
  std::vector<uint8_t> qx_bytes(degree_len), qy_bytes(degree_len);
  std::vector<uint8_t> r_bytes(order_len), s_bytes(order_len);
  if (!BN_bn2bin_padded(qx_bytes.data(), qx_bytes.size(), qx.get()) ||
      !BN_bn2bin_padded(qy_bytes.data(), qy_bytes.size(), qy.get()) ||
      !BN_bn2bin_padded(r_bytes.data(), r_bytes.size(), sig->r) ||
      !BN_bn2bin_padded(s_bytes.data(), s_bytes.size(), sig->s)) {
    return false;
  }

  printf("%sQx = %s\r\nQy = %s\r\nR = %s\r\nS = %s\r\n\r\n",
         t->CurrentTestToString().c_str(),
         EncodeHex(qx_bytes.data(), qx_bytes.size()).c_str(),
         EncodeHex(qy_bytes.data(), qy_bytes.size()).c_str(),
         EncodeHex(r_bytes.data(), r_bytes.size()).c_str(),
         EncodeHex(s_bytes.data(), s_bytes.size()).c_str());
  return true;
}

static bool TestECDSA2SigGen(FileTest *t, void *arg) {
  return TestECDSA2SigGenImpl(t, false);
}

static bool TestECDSA2SigGenComponent(FileTest *t, void *arg) {
  return TestECDSA2SigGenImpl(t, true);
}

int cavp_ecdsa2_siggen_test_main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "usage: %s (SigGen|SigGenComponent) <test file>\n",
            argv[0]);
    return 1;
  }

  static bool (*test_func)(FileTest *, void *);
  if (strcmp(argv[1], "SigGen") == 0) {
    test_func = TestECDSA2SigGen;
  } else if (strcmp(argv[1], "SigGenComponent") == 0) {
    test_func = TestECDSA2SigGenComponent;
  } else {
    fprintf(stderr, "Unknown test type: %s\n", argv[1]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[2];
  opts.callback = test_func;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
