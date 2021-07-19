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

// cavp_ecdsa2_pkv_test processes a NIST CAVP ECDSA2 PKV test vector request file
// and emits the corresponding response.

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/ec_key.h>
#include <openssl/err.h>
#include <openssl/nid.h>

#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


static bool TestECDSA2PKV(FileTest *t, void *arg) {
  int nid = GetECGroupNIDFromInstruction(t);
  if (nid == NID_undef) {
    return false;
  }
  bssl::UniquePtr<EC_KEY> key(EC_KEY_new_by_curve_name(nid));
  bssl::UniquePtr<BIGNUM> qx = GetBIGNUM(t, "Qx");
  bssl::UniquePtr<BIGNUM> qy = GetBIGNUM(t, "Qy");
  if (!key || !qx || !qy) {
    return false;
  }

  if (EC_KEY_set_public_key_affine_coordinates(key.get(), qx.get(), qy.get())) {
    printf("%sResult = P\r\n\r\n", t->CurrentTestToString().c_str());
  } else {
    char buf[256];
    ERR_error_string_n(ERR_get_error(), buf, sizeof(buf));
    printf("%sResult = F (%s)\r\n\r\n", t->CurrentTestToString().c_str(), buf);
  }
  ERR_clear_error();
  return true;
}

int cavp_ecdsa2_pkv_test_main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "usage: %s <test file>\n",
            argv[0]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[1];
  opts.callback = TestECDSA2PKV;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
