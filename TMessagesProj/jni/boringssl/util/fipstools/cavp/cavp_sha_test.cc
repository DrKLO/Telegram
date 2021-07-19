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

// cavp_sha_test processes a NIST CAVP SHA test vector request file and emits
// the corresponding response.

#include <stdlib.h>

#include <openssl/crypto.h>
#include <openssl/digest.h>

#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"

namespace {

struct TestCtx {
  std::string hash;
};

}

static bool TestSHA(FileTest *t, void *arg) {
  TestCtx *ctx = reinterpret_cast<TestCtx *>(arg);

  const EVP_MD *md = EVP_get_digestbyname(ctx->hash.c_str());
  if (md == nullptr) {
    return false;
  }
  const size_t md_len = EVP_MD_size(md);

  std::string out_len;
  if (!t->GetInstruction(&out_len, "L") ||
      md_len != strtoul(out_len.c_str(), nullptr, 0)) {
    return false;
  }

  std::string msg_len_str;
  std::vector<uint8_t> msg;
  if (!t->GetAttribute(&msg_len_str, "Len") ||
      !t->GetBytes(&msg, "Msg")) {
    return false;
  }

  size_t msg_len = strtoul(msg_len_str.c_str(), nullptr, 0);
  if (msg_len % 8 != 0 ||
      msg_len / 8 > msg.size()) {
    return false;
  }
  msg_len /= 8;

  std::vector<uint8_t> out;
  out.resize(md_len);
  unsigned digest_len;
  if (!EVP_Digest(msg.data(), msg_len, out.data(), &digest_len, md, nullptr) ||
      digest_len != out.size()) {
    return false;
  }

  printf("%s", t->CurrentTestToString().c_str());
  printf("MD = %s\r\n\r\n", EncodeHex(out.data(), out.size()).c_str());

  return true;
}

static int usage(char *arg) {
  fprintf(stderr, "usage: %s <hash> <test file>\n", arg);
  return 1;
}

int cavp_sha_test_main(int argc, char **argv) {
  if (argc != 3) {
    return usage(argv[0]);
  }

  TestCtx ctx = {std::string(argv[1])};

  FileTest::Options opts;
  opts.path = argv[2];
  opts.callback = TestSHA;
  opts.arg = &ctx;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
