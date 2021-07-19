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

// cavp_aes_test processes a NIST CAVP AES test vector request file and emits
// the corresponding response.

#include <stdlib.h>

#include <openssl/cipher.h>
#include <openssl/crypto.h>
#include <openssl/err.h>

#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


namespace {

struct TestCtx {
  const EVP_CIPHER *cipher;
  bool has_iv;
  enum Mode {
    kKAT,  // Known Answer Test
    kMCT,  // Monte Carlo Test
  };
  Mode mode;
};

}

static bool MonteCarlo(const TestCtx *ctx, FileTest *t,
                       const EVP_CIPHER *cipher, std::vector<uint8_t> *out,
                       bool encrypt, std::vector<uint8_t> key,
                       std::vector<uint8_t> iv, std::vector<uint8_t> in) {
  const std::string in_label = encrypt ? "PLAINTEXT" : "CIPHERTEXT",
                    result_label = encrypt ? "CIPHERTEXT" : "PLAINTEXT";
  std::vector<uint8_t> prev_result, result, prev_in;
  for (int i = 0; i < 100; i++) {
    printf("COUNT = %d\r\nKEY = %s\r\n", i,
           EncodeHex(key.data(), key.size()).c_str());
    if (ctx->has_iv) {
      printf("IV = %s\r\n", EncodeHex(iv.data(), iv.size()).c_str());
    }
    printf("%s = %s\r\n", in_label.c_str(),
           EncodeHex(in.data(), in.size()).c_str());

    if (!ctx->has_iv) {  // ECB mode
      for (int j = 0; j < 1000; j++) {
        prev_result = result;
        if (!CipherOperation(cipher, &result, encrypt, key, iv, in)) {
          return false;
        }
        in = result;
      }
    } else {
      for (int j = 0; j < 1000; j++) {
        prev_result = result;
        if (j > 0) {
          if (encrypt) {
            iv = result;
          } else {
            iv = prev_in;
          }
        }

        if (!CipherOperation(cipher, &result, encrypt, key, iv, in)) {
          return false;
        }

        prev_in = in;

        if (j == 0) {
          in = iv;
        } else {
          in = prev_result;
        }
      }
    }

    printf("%s = %s\r\n\r\n", result_label.c_str(),
           EncodeHex(result.data(), result.size()).c_str());

    const size_t key_len = key.size() * 8;
    if (key_len == 128) {
      for (size_t k = 0; k < key.size(); k++) {
        key[k] ^= result[k];
      }
    } else if (key_len == 192) {
      for (size_t k = 0; k < key.size(); k++) {
        // Key[i+1] = Key[i] xor (last 64-bits of CT[j-1] || CT[j])
        if (k < 8) {
          key[k] ^= prev_result[prev_result.size() - 8 + k];
        } else {
          key[k] ^= result[k - 8];
        }
      }
    } else {  // key_len == 256
      for (size_t k = 0; k < key.size(); k++) {
        // Key[i+1] = Key[i] xor (CT[j-1] || CT[j])
        if (k < 16) {
          key[k] ^= prev_result[k];
        } else {
          key[k] ^= result[k - 16];
        }
      }
    }

    if (ctx->has_iv) {
      iv = result;
      in = prev_result;
    } else {
      in = result;
    }
  }

  return true;
}

static bool TestCipher(FileTest *t, void *arg) {
  TestCtx *ctx = reinterpret_cast<TestCtx *>(arg);

  if (t->HasInstruction("ENCRYPT") == t->HasInstruction("DECRYPT")) {
    t->PrintLine("Want either ENCRYPT or DECRYPT");
    return false;
  }
  enum {
    kEncrypt,
    kDecrypt,
  } operation = t->HasInstruction("ENCRYPT") ? kEncrypt : kDecrypt;

  std::string count;
  std::vector<uint8_t> key, iv, in, result;
  if (!t->GetAttribute(&count, "COUNT") ||
      !t->GetBytes(&key, "KEY") ||
      (ctx->has_iv && !t->GetBytes(&iv, "IV"))) {
    return false;
  }

  const EVP_CIPHER *cipher = ctx->cipher;
  if (operation == kEncrypt) {
    if (!t->GetBytes(&in, "PLAINTEXT")) {
      return false;
    }
  } else {  // operation == kDecrypt
    if (!t->GetBytes(&in, "CIPHERTEXT")) {
      return false;
    }
  }

  if (ctx->mode == TestCtx::kKAT) {
    if (!CipherOperation(cipher, &result, operation == kEncrypt, key, iv, in)) {
      return false;
    }
    const std::string label =
        operation == kEncrypt ? "CIPHERTEXT" : "PLAINTEXT";
    printf("%s%s = %s\r\n\r\n", t->CurrentTestToString().c_str(), label.c_str(),
           EncodeHex(result.data(), result.size()).c_str());
  } else {  // ctx->mode == kMCT
    const std::string op_label =
        operation == kEncrypt ? "[ENCRYPT]" : "[DECRYPT]";
    printf("%s\r\n\r\n", op_label.c_str());
    if (!MonteCarlo(ctx, t, cipher, &result, operation == kEncrypt, key, iv,
                    in)) {
      return false;
    }
    if (operation == kEncrypt) {
      // MCT tests contain a stray blank line after the ENCRYPT section.
      printf("\r\n");
    }
  }

  return true;
}

static int usage(char *arg) {
  fprintf(stderr, "usage: %s (kat|mct) <cipher> <test file>\n", arg);
  return 1;
}

int cavp_aes_test_main(int argc, char **argv) {
  if (argc != 4) {
    return usage(argv[0]);
  }

  const std::string tm(argv[1]);
  enum TestCtx::Mode test_mode;
  if (tm == "kat") {
    test_mode = TestCtx::kKAT;
  } else if (tm == "mct") {
    test_mode = TestCtx::kMCT;
  } else {
    fprintf(stderr, "invalid test_mode: %s\n", tm.c_str());
    return usage(argv[0]);
  }

  const std::string cipher_name(argv[2]);
  const EVP_CIPHER *cipher = GetCipher(argv[2]);
  if (cipher == nullptr) {
    fprintf(stderr, "invalid cipher: %s\n", argv[2]);
    return 1;
  }
  const bool has_iv =
      (cipher_name != "aes-128-ecb" &&
       cipher_name != "aes-192-ecb" &&
       cipher_name != "aes-256-ecb");

  TestCtx ctx = {cipher, has_iv, test_mode};

  FileTest::Options opts;
  opts.path = argv[3];
  opts.callback = TestCipher;
  opts.arg = &ctx;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
