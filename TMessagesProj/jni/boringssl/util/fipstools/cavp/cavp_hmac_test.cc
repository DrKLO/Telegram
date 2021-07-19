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

// cavp_hmac_test processes a NIST CAVP HMAC test vector request file and emits
// the corresponding response.

#include <stdlib.h>

#include <openssl/crypto.h>
#include <openssl/hmac.h>

#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


static bool TestHMAC(FileTest *t, void *arg) {
  std::string md_len_str;
  if (!t->GetInstruction(&md_len_str, "L")) {
    return false;
  }
  const size_t md_len = strtoul(md_len_str.c_str(), nullptr, 0);

  const EVP_MD *md;
  switch (md_len) {
    case 20:
      md = EVP_sha1();
      break;
    case 28:
      md = EVP_sha224();
      break;
    case 32:
      md = EVP_sha256();
      break;
    case 48:
      md = EVP_sha384();
      break;
    case 64:
      md = EVP_sha512();
      break;
    default:
      return false;
  }

  std::string count_str, k_len_str, t_len_str;
  std::vector<uint8_t> key, msg;
  if (!t->GetAttribute(&count_str, "Count") ||
      !t->GetAttribute(&k_len_str, "Klen") ||
      !t->GetAttribute(&t_len_str, "Tlen") ||
      !t->GetBytes(&key, "Key") ||
      !t->GetBytes(&msg, "Msg")) {
    return false;
  }

  size_t k_len = strtoul(k_len_str.c_str(), nullptr, 0);
  size_t t_len = strtoul(t_len_str.c_str(), nullptr, 0);
  if (key.size() < k_len) {
    return false;
  }
  unsigned out_len;
  uint8_t out[EVP_MAX_MD_SIZE];
  if (HMAC(md, key.data(), k_len, msg.data(), msg.size(), out, &out_len) ==
      NULL) {
    return false;
  }

  if (out_len < t_len) {
    return false;
  }

  printf("%s", t->CurrentTestToString().c_str());
  printf("Mac = %s\r\n\r\n", EncodeHex(out, t_len).c_str());

  return true;
}

static int usage(char *arg) {
  fprintf(stderr, "usage: %s <test file>\n", arg);
  return 1;
}

int cavp_hmac_test_main(int argc, char **argv) {
  if (argc != 2) {
    return usage(argv[0]);
  }

  FileTest::Options opts;
  opts.path = argv[1];
  opts.callback = TestHMAC;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
