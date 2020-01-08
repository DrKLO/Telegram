/* Copyright (c) 2018, Google Inc.
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

// cavp_kas_test processes NIST CAVP ECC KAS test vector request files and
// emits the corresponding response.

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/digest.h>
#include <openssl/ecdh.h>
#include <openssl/ecdsa.h>
#include <openssl/ec_key.h>
#include <openssl/err.h>
#include <openssl/nid.h>
#include <openssl/sha.h>

#include "../crypto/internal.h"
#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


static bool TestKAS(FileTest *t, void *arg) {
  const bool validate = *reinterpret_cast<bool *>(arg);

  int nid = NID_undef;
  size_t digest_len = 0;

  if (t->HasInstruction("EB - SHA224")) {
    nid = NID_secp224r1;
    digest_len = SHA224_DIGEST_LENGTH;
  } else if (t->HasInstruction("EC - SHA256")) {
    nid = NID_X9_62_prime256v1;
    digest_len = SHA256_DIGEST_LENGTH;
  } else if (t->HasInstruction("ED - SHA384")) {
    nid = NID_secp384r1;
    digest_len = SHA384_DIGEST_LENGTH;
  } else if (t->HasInstruction("EE - SHA512")) {
    nid = NID_secp521r1;
    digest_len = SHA512_DIGEST_LENGTH;
  } else {
    return false;
  }

  if (!t->HasAttribute("COUNT")) {
    return false;
  }

  bssl::UniquePtr<BIGNUM> their_x(GetBIGNUM(t, "QeCAVSx"));
  bssl::UniquePtr<BIGNUM> their_y(GetBIGNUM(t, "QeCAVSy"));
  bssl::UniquePtr<EC_KEY> ec_key(EC_KEY_new_by_curve_name(nid));
  bssl::UniquePtr<BN_CTX> ctx(BN_CTX_new());
  if (!their_x || !their_y || !ec_key || !ctx) {
    return false;
  }

  const EC_GROUP *const group = EC_KEY_get0_group(ec_key.get());
  bssl::UniquePtr<EC_POINT> their_point(EC_POINT_new(group));
  if (!their_point ||
      !EC_POINT_set_affine_coordinates_GFp(
          group, their_point.get(), their_x.get(), their_y.get(), ctx.get())) {
    return false;
  }

  if (validate) {
    bssl::UniquePtr<BIGNUM> our_k(GetBIGNUM(t, "deIUT"));
    if (!our_k ||
        !EC_KEY_set_private_key(ec_key.get(), our_k.get()) ||
        // These attributes are ignored.
        !t->HasAttribute("QeIUTx") ||
        !t->HasAttribute("QeIUTy")) {
      return false;
    }
  } else if (!EC_KEY_generate_key(ec_key.get())) {
    return false;
  }

  uint8_t digest[EVP_MAX_MD_SIZE];
  if (!ECDH_compute_key_fips(digest, digest_len, their_point.get(),
                             ec_key.get())) {
    return false;
  }

  if (validate) {
    std::vector<uint8_t> expected_shared_bytes;
    if (!t->GetBytes(&expected_shared_bytes, "CAVSHashZZ")) {
      return false;
    }
    const bool ok =
        digest_len == expected_shared_bytes.size() &&
        OPENSSL_memcmp(digest, expected_shared_bytes.data(), digest_len) == 0;

    printf("%sIUTHashZZ = %s\r\nResult = %c\r\n\r\n\r\n",
           t->CurrentTestToString().c_str(),
           EncodeHex(digest, digest_len).c_str(), ok ? 'P' : 'F');
  } else {
    const EC_POINT *pub = EC_KEY_get0_public_key(ec_key.get());
    bssl::UniquePtr<BIGNUM> x(BN_new());
    bssl::UniquePtr<BIGNUM> y(BN_new());
    if (!x || !y ||
        !EC_POINT_get_affine_coordinates_GFp(group, pub, x.get(), y.get(),
                                             ctx.get())) {
      return false;
    }
    bssl::UniquePtr<char> x_hex(BN_bn2hex(x.get()));
    bssl::UniquePtr<char> y_hex(BN_bn2hex(y.get()));

    printf("%sQeIUTx = %s\r\nQeIUTy = %s\r\nHashZZ = %s\r\n",
           t->CurrentTestToString().c_str(), x_hex.get(), y_hex.get(),
           EncodeHex(digest, digest_len).c_str());
  }

  return true;
}

int cavp_kas_test_main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "usage: %s (validity|function) <test file>\n",
            argv[0]);
    return 1;
  }

  bool validity;
  if (strcmp(argv[1], "validity") == 0) {
    validity = true;
  } else if (strcmp(argv[1], "function") == 0) {
    validity = false;
  } else {
    fprintf(stderr, "Unknown test type: %s\n", argv[1]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[2];
  opts.arg = &validity;
  opts.callback = TestKAS;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  opts.is_kas_test = true;
  return FileTestMain(opts);
}
