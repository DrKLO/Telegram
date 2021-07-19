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

// cavp_ctr_drbg_test processes a NIST CAVP DRBG800-90A test vector request
// file and emits the corresponding response.

#include <openssl/crypto.h>

#include <stdlib.h>

#include "cavp_test_util.h"
#include "../crypto/fipsmodule/rand/internal.h"
#include "../crypto/test/file_test.h"


static bool TestCTRDRBG(FileTest *t, void *arg) {
  std::string test_type, prediction_resistance, entropy_input_len, nonce_len,
      personalization_str_len, additional_input_len, returned_bits_len;
  if (!t->GetInstruction(&test_type, "AES-256 no df") ||
      !t->GetInstruction(&prediction_resistance, "PredictionResistance") ||
      !t->GetInstruction(&entropy_input_len, "EntropyInputLen") ||
      !t->GetInstruction(&nonce_len, "NonceLen") ||
      !t->GetInstruction(&personalization_str_len,
                         "PersonalizationStringLen") ||
      !t->GetInstruction(&additional_input_len, "AdditionalInputLen") ||
      !t->GetInstruction(&returned_bits_len, "ReturnedBitsLen") ||
      !test_type.empty() ||
      prediction_resistance != "False" ||
      strtoul(entropy_input_len.c_str(), nullptr, 0) !=
          CTR_DRBG_ENTROPY_LEN * 8 ||
      nonce_len != "0") {
    return false;
  }

  std::string count;
  std::vector<uint8_t> entropy, nonce, personalization_str, ai1, ai2;
  if (!t->GetAttribute(&count, "COUNT") ||
      !t->GetBytes(&entropy, "EntropyInput") ||
      !t->GetBytes(&nonce, "Nonce") ||
      !t->GetBytes(&personalization_str, "PersonalizationString") ||
      !t->GetBytes(&ai1, "AdditionalInput") ||
      !t->GetBytes(&ai2, "AdditionalInput/2") ||
      entropy.size() * 8 != strtoul(entropy_input_len.c_str(), nullptr, 0) ||
      nonce.size() != 0 ||
      personalization_str.size() * 8 !=
          strtoul(personalization_str_len.c_str(), nullptr, 0) ||
      ai1.size() != ai2.size() ||
      ai1.size() * 8 != strtoul(additional_input_len.c_str(), nullptr, 0)) {
    return false;
  }

  CTR_DRBG_STATE drbg;
  CTR_DRBG_init(&drbg, entropy.data(),
                personalization_str.size() > 0 ? personalization_str.data()
                                               : nullptr,
                personalization_str.size());

  uint64_t out_len = strtoul(returned_bits_len.c_str(), nullptr, 0);
  if (out_len == 0 || (out_len & 7) != 0) {
    return false;
  }
  out_len /= 8;

  std::vector<uint8_t> out;
  out.resize(out_len);

  CTR_DRBG_generate(&drbg, out.data(), out.size(),
                    ai1.size() > 0 ? ai1.data() : nullptr, ai1.size());
  CTR_DRBG_generate(&drbg, out.data(), out.size(),
                    ai2.size() > 0 ? ai2.data() : nullptr, ai2.size());

  printf("%s", t->CurrentTestToString().c_str());
  printf("ReturnedBits = %s\r\n\r\n",
         EncodeHex(out.data(), out.size()).c_str());

  return true;
}

static int usage(char *arg) {
  fprintf(stderr, "usage: %s <test file>\n", arg);
  return 1;
}

int cavp_ctr_drbg_test_main(int argc, char **argv) {
  if (argc != 2) {
    return usage(argv[0]);
  }

  FileTest::Options opts;
  opts.path = argv[1];
  opts.callback = TestCTRDRBG;
  opts.silent = true;
  opts.comment_callback = EchoComment;
  return FileTestMain(opts);
}
