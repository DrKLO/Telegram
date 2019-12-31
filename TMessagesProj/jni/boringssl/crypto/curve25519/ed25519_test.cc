/* Copyright (c) 2015, Google Inc.
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
#include <string.h>

#include <gtest/gtest.h>

#include <openssl/curve25519.h>

#include "../internal.h"
#include "../test/file_test.h"
#include "../test/test_util.h"


TEST(Ed25519Test, TestVectors) {
  FileTestGTest("crypto/curve25519/ed25519_tests.txt", [](FileTest *t) {
    std::vector<uint8_t> private_key, public_key, message, expected_signature;
    ASSERT_TRUE(t->GetBytes(&private_key, "PRIV"));
    ASSERT_EQ(64u, private_key.size());
    ASSERT_TRUE(t->GetBytes(&public_key, "PUB"));
    ASSERT_EQ(32u, public_key.size());
    ASSERT_TRUE(t->GetBytes(&message, "MESSAGE"));
    ASSERT_TRUE(t->GetBytes(&expected_signature, "SIG"));
    ASSERT_EQ(64u, expected_signature.size());

    uint8_t signature[64];
    ASSERT_TRUE(ED25519_sign(signature, message.data(), message.size(),
                             private_key.data()));
    EXPECT_EQ(Bytes(expected_signature), Bytes(signature));
    EXPECT_TRUE(ED25519_verify(message.data(), message.size(), signature,
                               public_key.data()));
  });
}

TEST(Ed25519Test, Malleability) {
  // https://tools.ietf.org/html/rfc8032#section-5.1.7 adds an additional test
  // that s be in [0, order). This prevents someone from adding a multiple of
  // order to s and obtaining a second valid signature for the same message.
  static const uint8_t kMsg[] = {0x54, 0x65, 0x73, 0x74};
  static const uint8_t kSig[] = {
      0x7c, 0x38, 0xe0, 0x26, 0xf2, 0x9e, 0x14, 0xaa, 0xbd, 0x05, 0x9a,
      0x0f, 0x2d, 0xb8, 0xb0, 0xcd, 0x78, 0x30, 0x40, 0x60, 0x9a, 0x8b,
      0xe6, 0x84, 0xdb, 0x12, 0xf8, 0x2a, 0x27, 0x77, 0x4a, 0xb0, 0x67,
      0x65, 0x4b, 0xce, 0x38, 0x32, 0xc2, 0xd7, 0x6f, 0x8f, 0x6f, 0x5d,
      0xaf, 0xc0, 0x8d, 0x93, 0x39, 0xd4, 0xee, 0xf6, 0x76, 0x57, 0x33,
      0x36, 0xa5, 0xc5, 0x1e, 0xb6, 0xf9, 0x46, 0xb3, 0x1d,
  };
  static const uint8_t kPub[] = {
      0x7d, 0x4d, 0x0e, 0x7f, 0x61, 0x53, 0xa6, 0x9b, 0x62, 0x42, 0xb5,
      0x22, 0xab, 0xbe, 0xe6, 0x85, 0xfd, 0xa4, 0x42, 0x0f, 0x88, 0x34,
      0xb1, 0x08, 0xc3, 0xbd, 0xae, 0x36, 0x9e, 0xf5, 0x49, 0xfa,
  };

  EXPECT_FALSE(ED25519_verify(kMsg, sizeof(kMsg), kSig, kPub));
}

TEST(Ed25519Test, KeypairFromSeed) {
  uint8_t public_key1[32], private_key1[64];
  ED25519_keypair(public_key1, private_key1);

  uint8_t seed[32];
  OPENSSL_memcpy(seed, private_key1, sizeof(seed));

  uint8_t public_key2[32], private_key2[64];
  ED25519_keypair_from_seed(public_key2, private_key2, seed);

  EXPECT_EQ(Bytes(public_key1), Bytes(public_key2));
  EXPECT_EQ(Bytes(private_key1), Bytes(private_key2));
}
