// Copyright 2015 The BoringSSL Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <gtest/gtest.h>

#include <openssl/curve25519.h>

#include "internal.h"
#include "../internal.h"
#include "../test/abi_test.h"
#include "../test/file_test.h"
#include "../test/test_util.h"
#include "../test/wycheproof_util.h"
#include "internal.h"

static inline int ctwrapX25519(uint8_t out_shared_key[32],
                               const uint8_t private_key[32],
                               const uint8_t peer_public_value[32]) {
  uint8_t scalar[32], point[32];
  // Copy all the secrets into a temporary buffer, so we can run constant-time
  // validation on them.
  OPENSSL_memcpy(scalar, private_key, sizeof(scalar));
  OPENSSL_memcpy(point, peer_public_value, sizeof(point));

  // X25519 should not leak the private key.
  CONSTTIME_SECRET(scalar, sizeof(scalar));
  // All other inputs are also marked as secret. This is not to support any
  // particular use case for calling X25519 with a secret *point*, but
  // rather to ensure that the choice of the point cannot influence whether
  // the scalar is leaked or not. Same for the initial contents of the
  // output buffer. This conservative choice may be revised in the future.
  CONSTTIME_SECRET(point, sizeof(point));
  CONSTTIME_SECRET(out_shared_key, 32);
  int r = X25519(out_shared_key, scalar, point);
  CONSTTIME_DECLASSIFY(out_shared_key, 32);
  return r;
}

TEST(X25519Test, TestVector) {
  // Taken from https://www.rfc-editor.org/rfc/rfc7748#section-5.2
  static const uint8_t kScalar1[32] = {
      0xa5, 0x46, 0xe3, 0x6b, 0xf0, 0x52, 0x7c, 0x9d, 0x3b, 0x16, 0x15,
      0x4b, 0x82, 0x46, 0x5e, 0xdd, 0x62, 0x14, 0x4c, 0x0a, 0xc1, 0xfc,
      0x5a, 0x18, 0x50, 0x6a, 0x22, 0x44, 0xba, 0x44, 0x9a, 0xc4,
  };
  static const uint8_t kPoint1[32] = {
      0xe6, 0xdb, 0x68, 0x67, 0x58, 0x30, 0x30, 0xdb, 0x35, 0x94, 0xc1,
      0xa4, 0x24, 0xb1, 0x5f, 0x7c, 0x72, 0x66, 0x24, 0xec, 0x26, 0xb3,
      0x35, 0x3b, 0x10, 0xa9, 0x03, 0xa6, 0xd0, 0xab, 0x1c, 0x4c,
  };

  uint8_t out[32], secret[32];
  EXPECT_TRUE(ctwrapX25519(out, kScalar1, kPoint1));
  static const uint8_t kExpected1[32] = {
      0xc3, 0xda, 0x55, 0x37, 0x9d, 0xe9, 0xc6, 0x90, 0x8e, 0x94, 0xea,
      0x4d, 0xf2, 0x8d, 0x08, 0x4f, 0x32, 0xec, 0xcf, 0x03, 0x49, 0x1c,
      0x71, 0xf7, 0x54, 0xb4, 0x07, 0x55, 0x77, 0xa2, 0x85, 0x52,
  };
  EXPECT_EQ(Bytes(kExpected1), Bytes(out));

  static const uint8_t kScalar2[32] = {
      0x4b, 0x66, 0xe9, 0xd4, 0xd1, 0xb4, 0x67, 0x3c, 0x5a, 0xd2, 0x26,
      0x91, 0x95, 0x7d, 0x6a, 0xf5, 0xc1, 0x1b, 0x64, 0x21, 0xe0, 0xea,
      0x01, 0xd4, 0x2c, 0xa4, 0x16, 0x9e, 0x79, 0x18, 0xba, 0x0d,
  };
  static const uint8_t kPoint2[32] = {
      0xe5, 0x21, 0x0f, 0x12, 0x78, 0x68, 0x11, 0xd3, 0xf4, 0xb7, 0x95,
      0x9d, 0x05, 0x38, 0xae, 0x2c, 0x31, 0xdb, 0xe7, 0x10, 0x6f, 0xc0,
      0x3c, 0x3e, 0xfc, 0x4c, 0xd5, 0x49, 0xc7, 0x15, 0xa4, 0x93,
  };
  EXPECT_TRUE(ctwrapX25519(out, kScalar2, kPoint2));
  static const uint8_t kExpected2[32] = {
      0x95, 0xcb, 0xde, 0x94, 0x76, 0xe8, 0x90, 0x7d, 0x7a, 0xad, 0xe4,
      0x5c, 0xb4, 0xb8, 0x73, 0xf8, 0x8b, 0x59, 0x5a, 0x68, 0x79, 0x9f,
      0xa1, 0x52, 0xe6, 0xf8, 0xf7, 0x64, 0x7a, 0xac, 0x79, 0x57,
  };
  EXPECT_EQ(Bytes(kExpected2), Bytes(out));

  // Taken from https://www.rfc-editor.org/rfc/rfc7748.html#section-6.1
  static const uint8_t kPrivateA[32] = {
      0x77, 0x07, 0x6d, 0x0a, 0x73, 0x18, 0xa5, 0x7d, 0x3c, 0x16, 0xc1,
      0x72, 0x51, 0xb2, 0x66, 0x45, 0xdf, 0x4c, 0x2f, 0x87, 0xeb, 0xc0,
      0x99, 0x2a, 0xb1, 0x77, 0xfb, 0xa5, 0x1d, 0xb9, 0x2c, 0x2a,
  };
  static const uint8_t kPublicA[32] = {
      0x85, 0x20, 0xf0, 0x09, 0x89, 0x30, 0xa7, 0x54, 0x74, 0x8b, 0x7d,
      0xdc, 0xb4, 0x3e, 0xf7, 0x5a, 0x0d, 0xbf, 0x3a, 0x0d, 0x26, 0x38,
      0x1a, 0xf4, 0xeb, 0xa4, 0xa9, 0x8e, 0xaa, 0x9b, 0x4e, 0x6a,
  };
  static const uint8_t kPrivateB[32] = {
      0x5d, 0xab, 0x08, 0x7e, 0x62, 0x4a, 0x8a, 0x4b, 0x79, 0xe1, 0x7f,
      0x8b, 0x83, 0x80, 0x0e, 0xe6, 0x6f, 0x3b, 0xb1, 0x29, 0x26, 0x18,
      0xb6, 0xfd, 0x1c, 0x2f, 0x8b, 0x27, 0xff, 0x88, 0xe0, 0xeb,
  };
  static const uint8_t kPublicB[32] = {
      0xde, 0x9e, 0xdb, 0x7d, 0x7b, 0x7d, 0xc1, 0xb4, 0xd3, 0x5b, 0x61,
      0xc2, 0xec, 0xe4, 0x35, 0x37, 0x3f, 0x83, 0x43, 0xc8, 0x5b, 0x78,
      0x67, 0x4d, 0xad, 0xfc, 0x7e, 0x14, 0x6f, 0x88, 0x2b, 0x4f,
  };
  static const uint8_t kSecret[32] = {
      0x4a, 0x5d, 0x9d, 0x5b, 0xa4, 0xce, 0x2d, 0xe1, 0x72, 0x8e, 0x3b,
      0xf4, 0x80, 0x35, 0x0f, 0x25, 0xe0, 0x7e, 0x21, 0xc9, 0x47, 0xd1,
      0x9e, 0x33, 0x76, 0xf0, 0x9b, 0x3c, 0x1e, 0x16, 0x17, 0x42,
  };

  OPENSSL_memcpy(secret, kPrivateA, sizeof(secret));
  CONSTTIME_SECRET(secret, sizeof(secret));
  X25519_public_from_private(out, secret);
  CONSTTIME_DECLASSIFY(out, sizeof(out));
  EXPECT_EQ(Bytes(out), Bytes(kPublicA));

  OPENSSL_memcpy(secret, kPrivateB, sizeof(secret));
  CONSTTIME_SECRET(secret, sizeof(secret));
  X25519_public_from_private(out, secret);
  CONSTTIME_DECLASSIFY(out, sizeof(out));
  EXPECT_EQ(Bytes(out), Bytes(kPublicB));

  ctwrapX25519(out, kPrivateA, kPublicB);
  EXPECT_EQ(Bytes(out), Bytes(kSecret));

  ctwrapX25519(out, kPrivateB, kPublicA);
  EXPECT_EQ(Bytes(out), Bytes(kSecret));
}

TEST(X25519Test, SmallOrder) {
  static const uint8_t kSmallOrderPoint[32] = {
      0xe0, 0xeb, 0x7a, 0x7c, 0x3b, 0x41, 0xb8, 0xae, 0x16, 0x56, 0xe3,
      0xfa, 0xf1, 0x9f, 0xc4, 0x6a, 0xda, 0x09, 0x8d, 0xeb, 0x9c, 0x32,
      0xb1, 0xfd, 0x86, 0x62, 0x05, 0x16, 0x5f, 0x49, 0xb8,
  };

  uint8_t out[32], private_key[32];
  OPENSSL_memset(private_key, 0x11, sizeof(private_key));

  OPENSSL_memset(out, 0xff, sizeof(out));
  EXPECT_FALSE(ctwrapX25519(out, private_key, kSmallOrderPoint))
      << "X25519 returned success with a small-order input.";

  // For callers which don't check, |out| should still be filled with zeros.
  static const uint8_t kZeros[32] = {0};
  EXPECT_EQ(Bytes(kZeros), Bytes(out));
}

TEST(X25519Test, Iterated) {
  // Taken from https://tools.ietf.org/html/rfc7748#section-5.2.
  uint8_t scalar[32], point[32], out[32];
  // This could simply be `uint8_t scalar[32] = {9}`, but GCC's -Warray-bounds
  // warning is broken. See https://gcc.gnu.org/bugzilla/show_bug.cgi?id=114826.
  OPENSSL_memset(scalar, 0, sizeof(scalar));
  scalar[0] = 9;
  OPENSSL_memset(point, 0, sizeof(point));
  point[0] = 9;

  for (unsigned i = 0; i < 1000; i++) {
    EXPECT_TRUE(ctwrapX25519(out, scalar, point));
    OPENSSL_memcpy(point, scalar, sizeof(point));
    OPENSSL_memcpy(scalar, out, sizeof(scalar));
  }

  static const uint8_t kExpected[32] = {
      0x68, 0x4c, 0xf5, 0x9b, 0xa8, 0x33, 0x09, 0x55, 0x28, 0x00, 0xef,
      0x56, 0x6f, 0x2f, 0x4d, 0x3c, 0x1c, 0x38, 0x87, 0xc4, 0x93, 0x60,
      0xe3, 0x87, 0x5f, 0x2e, 0xb9, 0x4d, 0x99, 0x53, 0x2c, 0x51,
  };

  EXPECT_EQ(Bytes(kExpected), Bytes(scalar));
}

TEST(X25519Test, DISABLED_IteratedLarge) {
  // Taken from https://tools.ietf.org/html/rfc7748#section-5.2.
  uint8_t scalar[32], point[32], out[32];
  // This could simply be `uint8_t scalar[32] = {9}`, but GCC's -Warray-bounds
  // warning is broken. See https://gcc.gnu.org/bugzilla/show_bug.cgi?id=114826.
  OPENSSL_memset(scalar, 0, sizeof(scalar));
  scalar[0] = 9;
  OPENSSL_memset(point, 0, sizeof(point));
  point[0] = 9;

  for (unsigned i = 0; i < 1000000; i++) {
    EXPECT_TRUE(ctwrapX25519(out, scalar, point));
    OPENSSL_memcpy(point, scalar, sizeof(point));
    OPENSSL_memcpy(scalar, out, sizeof(scalar));
  }

  static const uint8_t kExpected[32] = {
      0x7c, 0x39, 0x11, 0xe0, 0xab, 0x25, 0x86, 0xfd, 0x86, 0x44, 0x97,
      0x29, 0x7e, 0x57, 0x5e, 0x6f, 0x3b, 0xc6, 0x01, 0xc0, 0x88, 0x3c,
      0x30, 0xdf, 0x5f, 0x4d, 0xd2, 0xd2, 0x4f, 0x66, 0x54, 0x24,
  };

  EXPECT_EQ(Bytes(kExpected), Bytes(scalar));
}

TEST(X25519Test, Wycheproof) {
  FileTestGTest("third_party/wycheproof_testvectors/x25519_test.txt",
                [](FileTest *t) {
      t->IgnoreInstruction("curve");
      t->IgnoreAttribute("curve");

      WycheproofResult result;
      ASSERT_TRUE(GetWycheproofResult(t, &result));
      std::vector<uint8_t> priv, pub, shared;
      ASSERT_TRUE(t->GetBytes(&priv, "private"));
      ASSERT_TRUE(t->GetBytes(&pub, "public"));
      ASSERT_TRUE(t->GetBytes(&shared, "shared"));
      ASSERT_EQ(32u, priv.size());
      ASSERT_EQ(32u, pub.size());

      uint8_t secret[32];
      int ret = ctwrapX25519(secret, priv.data(), pub.data());
      EXPECT_EQ(ret, result.IsValid({"NonCanonicalPublic", "Twist"}) ? 1 : 0);
      EXPECT_EQ(Bytes(secret), Bytes(shared));
  });
}

#if defined(BORINGSSL_X25519_NEON) && defined(SUPPORTS_ABI_TEST)
TEST(X25519Test, NeonABI) {
  if (!CRYPTO_is_NEON_capable()) {
    GTEST_SKIP() << "Can't test ABI of NEON code without NEON";
  }

  static const uint8_t kScalar[32] = {
      0xa5, 0x46, 0xe3, 0x6b, 0xf0, 0x52, 0x7c, 0x9d, 0x3b, 0x16, 0x15,
      0x4b, 0x82, 0x46, 0x5e, 0xdd, 0x62, 0x14, 0x4c, 0x0a, 0xc1, 0xfc,
      0x5a, 0x18, 0x50, 0x6a, 0x22, 0x44, 0xba, 0x44, 0x9a, 0xc4,
  };
  static const uint8_t kPoint[32] = {
      0xe6, 0xdb, 0x68, 0x67, 0x58, 0x30, 0x30, 0xdb, 0x35, 0x94, 0xc1,
      0xa4, 0x24, 0xb1, 0x5f, 0x7c, 0x72, 0x66, 0x24, 0xec, 0x26, 0xb3,
      0x35, 0x3b, 0x10, 0xa9, 0x03, 0xa6, 0xd0, 0xab, 0x1c, 0x4c,
  };
  uint8_t secret[32];
  CHECK_ABI(x25519_NEON, secret, kScalar, kPoint);
}
#endif  // BORINGSSL_X25519_NEON && SUPPORTS_ABI_TEST

#if defined(BORINGSSL_FE25519_ADX) && defined(SUPPORTS_ABI_TEST)
TEST(X25519Test, AdxMulABI) {
  static const uint64_t in1[4] = {0}, in2[4] = {0};
  uint64_t out[4];
  if (CRYPTO_is_BMI1_capable() && CRYPTO_is_BMI2_capable() &&
      CRYPTO_is_ADX_capable()) {
    CHECK_ABI(fiat_curve25519_adx_mul, out, in1, in2);
  } else {
    GTEST_SKIP() << "Can't test ABI of ADX code without ADX";
  }
}

TEST(X25519Test, AdxSquareABI) {
  static const uint64_t in[4] = {0};
  uint64_t out[4];
  if (CRYPTO_is_BMI1_capable() && CRYPTO_is_BMI2_capable() &&
      CRYPTO_is_ADX_capable()) {
    CHECK_ABI(fiat_curve25519_adx_square, out, in);
  } else {
    GTEST_SKIP() << "Can't test ABI of ADX code without ADX";
  }
}
#endif  // BORINGSSL_FE25519_ADX && SUPPORTS_ABI_TEST
