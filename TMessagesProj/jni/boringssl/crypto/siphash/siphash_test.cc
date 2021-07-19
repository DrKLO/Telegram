/* Copyright (c) 2019, Google Inc.
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

#include <stdint.h>

#include <gtest/gtest.h>

#include <openssl/siphash.h>

#include "../test/file_test.h"
#include "../test/test_util.h"

TEST(SipHash, Basic) {
  // This is the example from appendix A of the SipHash paper.
  union {
    uint8_t bytes[16];
    uint64_t words[2];
  } key;

  for (unsigned i = 0; i < 16; i++) {
    key.bytes[i] = i;
  }

  uint8_t input[15];
  for (unsigned i = 0; i < sizeof(input); i++) {
    input[i] = i;
  }

  EXPECT_EQ(UINT64_C(0xa129ca6149be45e5),
            SIPHASH_24(key.words, input, sizeof(input)));
}

TEST(SipHash, Vectors) {
  FileTestGTest("crypto/siphash/siphash_tests.txt", [](FileTest *t) {
    std::vector<uint8_t> key, msg, hash;
    ASSERT_TRUE(t->GetBytes(&key, "KEY"));
    ASSERT_TRUE(t->GetBytes(&msg, "IN"));
    ASSERT_TRUE(t->GetBytes(&hash, "HASH"));
    ASSERT_EQ(16u, key.size());
    ASSERT_EQ(8u, hash.size());

    uint64_t key_words[2];
    memcpy(key_words, key.data(), key.size());
    uint64_t result = SIPHASH_24(key_words, msg.data(), msg.size());
    EXPECT_EQ(Bytes(reinterpret_cast<uint8_t *>(&result), sizeof(result)),
              Bytes(hash));
  });
}
