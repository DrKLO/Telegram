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

// cavp_rsa2_sigver_test processes NIST CAVP RSA2 SigVer test vector request
// files and emits the corresponding response.

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/rsa.h>

#include "../crypto/internal.h"
#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


namespace {

struct TestCtx {
  std::vector<uint8_t> N;
  bool is_pss;
};

}

static bool TestRSA2SigVer(FileTest *t, void *arg) {
  TestCtx *ctx = reinterpret_cast<TestCtx *>(arg);

  std::string mod_str;
  if (!t->GetInstruction(&mod_str, "mod")) {
    return false;
  }

  printf("%s", t->CurrentTestToString().c_str());

  if (t->HasAttribute("n")) {
    printf("\r\n");
    return t->GetBytes(&ctx->N, "n");
  }

  std::string hash;
  std::vector<uint8_t> e_bytes, msg, sig;
  if (!t->GetAttribute(&hash, "SHAAlg") ||
      !t->GetBytes(&e_bytes, "e") ||
      !t->GetBytes(&msg, "Msg") ||
      !t->GetBytes(&sig, "S")) {
    return false;
  }

  bssl::UniquePtr<RSA> key(RSA_new());
  key->n = BN_new();
  key->e = BN_new();
  if (key == nullptr ||
      !BN_bin2bn(ctx->N.data(), ctx->N.size(), key->n) ||
      !BN_bin2bn(e_bytes.data(), e_bytes.size(), key->e)) {
    return false;
  }

  const EVP_MD *md = EVP_get_digestbyname(hash.c_str());
  uint8_t digest_buf[EVP_MAX_MD_SIZE];
  unsigned digest_len;
  if (md == NULL ||
      !EVP_Digest(msg.data(), msg.size(), digest_buf, &digest_len, md, NULL)) {
    return false;
  }

  int ok;
  if (ctx->is_pss) {
    ok = RSA_verify_pss_mgf1(key.get(), digest_buf, digest_len, md, md, -1,
                             sig.data(), sig.size());
  } else {
    ok = RSA_verify(EVP_MD_type(md), digest_buf, digest_len, sig.data(),
                    sig.size(), key.get());
  }

  if (ok) {
    printf("Result = P\r\n\r\n");
  } else {
    char buf[256];
    ERR_error_string_n(ERR_get_error(), buf, sizeof(buf));
    printf("Result = F (%s)\r\n\r\n", buf);
  }
  ERR_clear_error();
  return true;
}

int cavp_rsa2_sigver_test_main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "usage: %s (pkcs15|pss) <test file>\n",
            argv[0]);
    return 1;
  }

  TestCtx ctx;
  if (strcmp(argv[1], "pkcs15") == 0) {
    ctx = {std::vector<uint8_t>(), false};
  } else if (strcmp(argv[1], "pss") == 0) {
    ctx = {std::vector<uint8_t>(), true};
  } else {
    fprintf(stderr, "Unknown test type: %s\n", argv[1]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[2];
  opts.callback = TestRSA2SigVer;
  opts.arg = &ctx;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
