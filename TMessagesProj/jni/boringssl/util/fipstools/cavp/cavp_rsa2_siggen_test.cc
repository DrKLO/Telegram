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

// cavp_rsa2_siggen_test processes NIST CAVP RSA2 SigGen test vector request
// files and emits the corresponding response.

#include <vector>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/digest.h>
#include <openssl/rsa.h>

#include "../crypto/internal.h"
#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"

namespace {

struct TestCtx {
  bssl::UniquePtr<RSA> key;
  bool is_pss;
};

}

static bool TestRSA2SigGen(FileTest *t, void *arg) {
  TestCtx *ctx = reinterpret_cast<TestCtx *>(arg);

  std::string mod_str, hash;
  std::vector<uint8_t> msg;
  if (!t->GetInstruction(&mod_str, "mod") ||
      !t->GetAttribute(&hash, "SHAAlg") ||
      !t->GetBytes(&msg, "Msg")) {
    return false;
  }

  std::string test = t->CurrentTestToString();
  if (t->IsAtNewInstructionBlock()) {
    int mod_bits = strtoul(mod_str.c_str(), nullptr, 0);
    ctx->key = bssl::UniquePtr<RSA>(RSA_new());
    if (ctx->key == nullptr ||
        mod_bits == 0 ||
        !RSA_generate_key_fips(ctx->key.get(), mod_bits, nullptr)) {
      return false;
    }

    const BIGNUM *n, *e;
    RSA_get0_key(ctx->key.get(), &n, &e, nullptr);

    std::vector<uint8_t> n_bytes(BN_num_bytes(n));
    std::vector<uint8_t> e_bytes(BN_num_bytes(e));
    if (!BN_bn2bin_padded(n_bytes.data(), n_bytes.size(), n) ||
        !BN_bn2bin_padded(e_bytes.data(), e_bytes.size(), e)) {
      return false;
    }

    printf("[mod = %s]\r\n\r\nn = %s\r\n\r\ne = %s",
           mod_str.c_str(),
           EncodeHex(n_bytes.data(), n_bytes.size()).c_str(),
           EncodeHex(e_bytes.data(), e_bytes.size()).c_str());
    test = test.substr(test.find("]") + 3);
  }

  const EVP_MD *md = EVP_get_digestbyname(hash.c_str());
  uint8_t digest_buf[EVP_MAX_MD_SIZE];
  std::vector<uint8_t> sig(RSA_size(ctx->key.get()));
  unsigned digest_len;
  size_t sig_len;
  if (md == NULL ||
      !EVP_Digest(msg.data(), msg.size(), digest_buf, &digest_len, md, NULL)) {
    return false;
  }

  if (ctx->is_pss) {
    if (!RSA_sign_pss_mgf1(ctx->key.get(), &sig_len, sig.data(), sig.size(),
                           digest_buf, digest_len, md, md, -1)) {
      return false;
    }
  } else {
    unsigned sig_len_u;
    if (!RSA_sign(EVP_MD_type(md), digest_buf, digest_len, sig.data(),
                  &sig_len_u, ctx->key.get())) {
      return false;
    }
    sig_len = sig_len_u;
  }

  printf("%sS = %s\r\n\r\n", test.c_str(),
         EncodeHex(sig.data(), sig_len).c_str());
  return true;
}

int cavp_rsa2_siggen_test_main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "usage: %s (pkcs15|pss) <test file>\n",
            argv[0]);
    return 1;
  }

  TestCtx ctx;
  if (strcmp(argv[1], "pkcs15") == 0) {
    ctx = {nullptr, false};
  } else if (strcmp(argv[1], "pss") == 0) {
    ctx = {nullptr, true};
  } else {
    fprintf(stderr, "Unknown test type: %s\n", argv[1]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[2];
  opts.callback = TestRSA2SigGen;
  opts.arg = &ctx;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
