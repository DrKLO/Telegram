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

// cavp_tdes_test processes a NIST TMOVS test vector request file and emits the
// corresponding response.

#include <stdlib.h>

#include <openssl/cipher.h>
#include <openssl/crypto.h>
#include <openssl/err.h>

#include "../crypto/test/file_test.h"
#include "cavp_test_util.h"


namespace {

struct TestCtx {
  const EVP_CIPHER *cipher;
  enum Mode {
    kKAT,  // Known Answer Test
    kMCT,  // Monte Carlo Test
  };
  bool has_iv;
  Mode mode;
};

}

static bool TestKAT(FileTest *t, void *arg) {
  TestCtx *ctx = reinterpret_cast<TestCtx *>(arg);

  if (t->HasInstruction("ENCRYPT") == t->HasInstruction("DECRYPT")) {
    t->PrintLine("Want either ENCRYPT or DECRYPT");
    return false;
  }
  enum {
    kEncrypt,
    kDecrypt,
  } operation = t->HasInstruction("ENCRYPT") ? kEncrypt : kDecrypt;

  if (t->HasAttribute("NumKeys")) {
    // Another file format quirk: NumKeys is a single attribute line immediately
    // following an instruction and should probably have been an instruction
    // instead. If it is present, the file has separate attributes "KEY{1,2,3}".
    // If it is not, the keys are concatenated in a single attribute "KEYs".
    std::string num_keys;
    t->GetAttribute(&num_keys, "NumKeys");
    t->InjectInstruction("NumKeys", num_keys);

    std::string header = operation == kEncrypt ? "[ENCRYPT]" : "[DECRYPT]";
    printf("%s\r\n\r\n", header.c_str());

    return true;
  }

  enum {
    kNotPresent,
    kTwo,
    kThree,
  } num_keys = kNotPresent;
  if (t->HasInstruction("NumKeys")) {
    std::string num_keys_str;
    t->GetInstruction(&num_keys_str, "NumKeys");
    const int n = strtoul(num_keys_str.c_str(), nullptr, 0);
    if (n == 2) {
      num_keys = kTwo;
    } else if (n == 3) {
      num_keys = kThree;
    } else {
      t->PrintLine("invalid NumKeys value");
      return false;
    }
  }

  std::string count;
  std::vector<uint8_t> keys, key1, key2, key3, iv, in, result;
  const std::string in_label =
      operation == kEncrypt ? "PLAINTEXT" : "CIPHERTEXT";
  // clang-format off
  if (!t->GetAttribute(&count, "COUNT") ||
      (num_keys == 0 && !t->GetBytes(&keys, "KEYs")) ||
      (num_keys > 0 &&
       (!t->GetBytes(&key1, "KEY1") ||
        !t->GetBytes(&key2, "KEY2") ||
        !t->GetBytes(&key3, "KEY3"))) ||
      (ctx->has_iv && !t->GetBytes(&iv, "IV")) ||
      !t->GetBytes(&in, in_label)) {
    return false;
  }
  // clang-format on
  std::vector<uint8_t> key;
  if (num_keys != kNotPresent) {
    key.insert(key.end(), key1.begin(), key1.end());
    key.insert(key.end(), key2.begin(), key2.end());
    if (num_keys == kThree) {
      key.insert(key.end(), key3.begin(), key3.end());
    }
  } else {
    key.insert(key.end(), keys.begin(), keys.end());
    key.insert(key.end(), keys.begin(), keys.end());
    key.insert(key.end(), keys.begin(), keys.end());
  }

  if (!CipherOperation(ctx->cipher, &result, operation == kEncrypt, key, iv,
                       in)) {
    return false;
  }

  // TDES fax files output format differs from file to file, and the input
  // format is inconsistent with the output, so we construct the output manually
  // rather than printing CurrentTestToString().
  if (t->IsAtNewInstructionBlock() && num_keys == kNotPresent) {
    // If NumKeys is present, header is printed when parsing NumKeys.
    std::string header = operation == kEncrypt ? "[ENCRYPT]" : "[DECRYPT]";
    printf("%s\r\n", header.c_str());
  }
  const std::string result_label =
      operation == kEncrypt ? "CIPHERTEXT" : "PLAINTEXT";
  printf("COUNT = %s\r\n", count.c_str());
  if (num_keys == kNotPresent) {
    printf("KEYs = %s\r\n", EncodeHex(keys.data(), keys.size()).c_str());
  } else {
    printf("KEY1 = %s\r\nKEY2 = %s\r\nKEY3 = %s\r\n",
           EncodeHex(key1.data(), key1.size()).c_str(),
           EncodeHex(key2.data(), key2.size()).c_str(),
           EncodeHex(key3.data(), key3.size()).c_str());
  }
  if (ctx->has_iv) {
    printf("IV = %s\r\n", EncodeHex(iv.data(), iv.size()).c_str());
  }
  printf("%s = %s\r\n", in_label.c_str(),
         EncodeHex(in.data(), in.size()).c_str());
  printf("%s = %s\r\n\r\n", result_label.c_str(),
         EncodeHex(result.data(), result.size()).c_str());

  return true;
}

// XORKeyWithOddParityLSB sets |*key| to |key| XOR |value| and then writes
// the LSB of each byte to establish odd parity for that byte. This parity-based
// embedded of a DES key into 64 bits is an old tradition and something that
// NIST's tests require.
static void XORKeyWithOddParityLSB(std::vector<uint8_t> *key,
                                        const std::vector<uint8_t> &value) {
  for (size_t i = 0; i < key->size(); i++) {
    uint8_t v = (*key)[i] ^ value[i];

    // Use LSB to establish odd parity.
    v |= 0x01;
    for (uint8_t j = 1; j < 8; j++) {
      v ^= ((v >> j) & 0x01);
    }
    (*key)[i] = v;
  }
}

static bool TestMCT(FileTest *t, void *arg) {
  TestCtx *ctx = reinterpret_cast<TestCtx *>(arg);

  if (t->HasInstruction("ENCRYPT") == t->HasInstruction("DECRYPT")) {
    t->PrintLine("Want either ENCRYPT or DECRYPT");
    return false;
  }
  enum {
    kEncrypt,
    kDecrypt,
  } operation = t->HasInstruction("ENCRYPT") ? kEncrypt : kDecrypt;

  if (t->HasAttribute("NumKeys")) {
    // Another file format quirk: NumKeys is a single attribute line immediately
    // following an instruction and should probably have been an instruction
    // instead.
    std::string num_keys;
    t->GetAttribute(&num_keys, "NumKeys");
    t->InjectInstruction("NumKeys", num_keys);
    return true;
  }

  enum {
    kTwo,
    kThree,
  } num_keys;
  std::string num_keys_str;
  if (!t->GetInstruction(&num_keys_str, "NumKeys")) {
    return false;
  } else {
    const int n = strtoul(num_keys_str.c_str(), nullptr, 0);
    if (n == 2) {
      num_keys = kTwo;
    } else if (n == 3) {
      num_keys = kThree;
    } else {
      t->PrintLine("invalid NumKeys value");
      return false;
    }
  }

  std::string count;
  std::vector<uint8_t> key1, key2, key3, iv, in, result;
  const std::string in_label =
      operation == kEncrypt ? "PLAINTEXT" : "CIPHERTEXT";
  // clang-format off
  if (!t->GetBytes(&key1, "KEY1") ||
      !t->GetBytes(&key2, "KEY2") ||
      !t->GetBytes(&key3, "KEY3") ||
      (ctx->has_iv && !t->GetBytes(&iv, "IV")) ||
      !t->GetBytes(&in, in_label)) {
    return false;
  }
  // clang-format on

  for (int i = 0; i < 400; i++) {
    std::vector<uint8_t> current_iv = iv, current_in = in, prev_result,
                         prev_prev_result;

    std::vector<uint8_t> key(key1);
    key.insert(key.end(), key2.begin(), key2.end());
    key.insert(key.end(), key3.begin(), key3.end());

    for (int j = 0; j < 10000; j++) {
      prev_prev_result = prev_result;
      prev_result = result;
      const EVP_CIPHER *cipher = ctx->cipher;
      if (!CipherOperation(cipher, &result, operation == kEncrypt, key,
                           current_iv, current_in)) {
        t->PrintLine("CipherOperation failed");
        return false;
      }
      if (ctx->has_iv) {
        if (operation == kEncrypt) {
          if (j == 0) {
            current_in = current_iv;
          } else {
            current_in = prev_result;
          }
          current_iv = result;
        } else {  // operation == kDecrypt
          current_iv = current_in;
          current_in = result;
        }
      } else {
        current_in = result;
      }
    }

    // Output result for COUNT = i.
    const std::string result_label =
        operation == kEncrypt ? "CIPHERTEXT" : "PLAINTEXT";
    if (i == 0) {
      const std::string op_label =
          operation == kEncrypt ? "ENCRYPT" : "DECRYPT";
      printf("[%s]\n\n", op_label.c_str());
    }
    printf("COUNT = %d\r\nKEY1 = %s\r\nKEY2 = %s\r\nKEY3 = %s\r\n", i,
           EncodeHex(key1.data(), key1.size()).c_str(),
           EncodeHex(key2.data(), key2.size()).c_str(),
           EncodeHex(key3.data(), key3.size()).c_str());
    if (ctx->has_iv) {
      printf("IV = %s\r\n", EncodeHex(iv.data(), iv.size()).c_str());
    }
    printf("%s = %s\r\n", in_label.c_str(),
           EncodeHex(in.data(), in.size()).c_str());
    printf("%s = %s\r\n\r\n", result_label.c_str(),
           EncodeHex(result.data(), result.size()).c_str());


    XORKeyWithOddParityLSB(&key1, result);
    XORKeyWithOddParityLSB(&key2, prev_result);
    if (num_keys == kThree) {
      XORKeyWithOddParityLSB(&key3, prev_prev_result);
    } else {
      XORKeyWithOddParityLSB(&key3, result);
    }

    if (ctx->has_iv) {
      if (operation == kEncrypt) {
        in = prev_result;
        iv = result;
      } else {
        iv = current_iv;
        in = current_in;
      }
    } else {
      in = result;
    }
  }

  return true;
}

static int usage(char *arg) {
  fprintf(stderr, "usage: %s (kat|mct) <cipher> <test file>\n", arg);
  return 1;
}

int cavp_tdes_test_main(int argc, char **argv) {
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
  bool has_iv = cipher_name != "des-ede" && cipher_name != "des-ede3";
  TestCtx ctx = {cipher, has_iv, test_mode};

  FileTestFunc test_fn = test_mode == TestCtx::kKAT ? &TestKAT : &TestMCT;
  FileTest::Options opts;
  opts.path = argv[3];
  opts.callback = test_fn;
  opts.arg = &ctx;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
