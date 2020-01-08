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

// cavp_tlskdf_test processes NIST TLS KDF test vectors and emits the
// corresponding response.
// See https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Algorithm-Validation-Program/documents/components/askdfvs.pdf, section 6.4.

#include <vector>

#include <errno.h>

#include <openssl/digest.h>

#include "cavp_test_util.h"
#include "../crypto/fipsmodule/tls/internal.h"
#include "../crypto/test/file_test.h"


static bool TestTLSKDF(FileTest *t, void *arg) {
  const EVP_MD *md = nullptr;

  if (t->HasInstruction("TLS 1.0/1.1")) {
    md = EVP_md5_sha1();
  } else if (t->HasInstruction("TLS 1.2")) {
    if (t->HasInstruction("SHA-256")) {
      md = EVP_sha256();
    } else if (t->HasInstruction("SHA-384")) {
      md = EVP_sha384();
    } else if (t->HasInstruction("SHA-512")) {
      md = EVP_sha512();
    }
  }

  if (md == nullptr) {
    return false;
  }

  std::string key_block_len_str;
  std::vector<uint8_t> premaster, server_random, client_random,
      key_block_server_random, key_block_client_random;
  if (!t->GetBytes(&premaster, "pre_master_secret") ||
      !t->GetBytes(&server_random, "serverHello_random") ||
      !t->GetBytes(&client_random, "clientHello_random") ||
      // The NIST tests specify different client and server randoms for the
      // expansion step from the master-secret step. This is impossible in TLS.
      !t->GetBytes(&key_block_server_random, "server_random") ||
      !t->GetBytes(&key_block_client_random, "client_random") ||
      !t->GetInstruction(&key_block_len_str, "key block length") ||
      // These are ignored.
      !t->HasAttribute("COUNT") ||
      !t->HasInstruction("pre-master secret length")) {
    return false;
  }

  uint8_t master_secret[48];
  static const char kMasterSecretLabel[] = "master secret";
  if (!CRYPTO_tls1_prf(md, master_secret, sizeof(master_secret),
                       premaster.data(), premaster.size(), kMasterSecretLabel,
                       sizeof(kMasterSecretLabel) - 1, client_random.data(),
                       client_random.size(), server_random.data(),
                       server_random.size())) {
    return false;
  }

  errno = 0;
  const long int key_block_bits =
      strtol(key_block_len_str.c_str(), nullptr, 10);
  if (errno != 0 || key_block_bits <= 0 || (key_block_bits & 7) != 0) {
    return false;
  }
  const size_t key_block_len = key_block_bits / 8;
  std::vector<uint8_t> key_block(key_block_len);
  static const char kLabel[] = "key expansion";
  if (!CRYPTO_tls1_prf(
          md, key_block.data(), key_block.size(), master_secret,
          sizeof(master_secret), kLabel, sizeof(kLabel) - 1,
          key_block_server_random.data(), key_block_server_random.size(),
          key_block_client_random.data(), key_block_client_random.size())) {
    return false;
  }

  printf("%smaster_secret = %s\r\nkey_block = %s\r\n\r\n",
         t->CurrentTestToString().c_str(),
         EncodeHex(master_secret, sizeof(master_secret)).c_str(),
         EncodeHex(key_block.data(), key_block.size()).c_str());

  return true;
}

int cavp_tlskdf_test_main(int argc, char **argv) {
  if (argc != 2) {
    fprintf(stderr, "usage: %s <test file>\n", argv[0]);
    return 1;
  }

  FileTest::Options opts;
  opts.path = argv[1];
  opts.callback = TestTLSKDF;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
