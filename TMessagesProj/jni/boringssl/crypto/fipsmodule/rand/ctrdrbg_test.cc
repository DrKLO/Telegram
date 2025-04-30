// Copyright 2017 The BoringSSL Authors
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

#include <gtest/gtest.h>

#include <openssl/ctrdrbg.h>
#include <openssl/sha.h>

#include "internal.h"
#include "../../test/file_test.h"
#include "../../test/test_util.h"


TEST(CTRDRBGTest, Basic) {
  const uint8_t kSeed[CTR_DRBG_ENTROPY_LEN] = {
      0xe4, 0xbc, 0x23, 0xc5, 0x08, 0x9a, 0x19, 0xd8, 0x6f, 0x41, 0x19, 0xcb,
      0x3f, 0xa0, 0x8c, 0x0a, 0x49, 0x91, 0xe0, 0xa1, 0xde, 0xf1, 0x7e, 0x10,
      0x1e, 0x4c, 0x14, 0xd9, 0xc3, 0x23, 0x46, 0x0a, 0x7c, 0x2f, 0xb5, 0x8e,
      0x0b, 0x08, 0x6c, 0x6c, 0x57, 0xb5, 0x5f, 0x56, 0xca, 0xe2, 0x5b, 0xad,
  };

  CTR_DRBG_STATE drbg;
  ASSERT_TRUE(CTR_DRBG_init(&drbg, kSeed, nullptr, 0));

  const uint8_t kReseed[CTR_DRBG_ENTROPY_LEN] = {
      0xfd, 0x85, 0xa8, 0x36, 0xbb, 0xa8, 0x50, 0x19, 0x88, 0x1e, 0x8c, 0x6b,
      0xad, 0x23, 0xc9, 0x06, 0x1a, 0xdc, 0x75, 0x47, 0x76, 0x59, 0xac, 0xae,
      0xa8, 0xe4, 0xa0, 0x1d, 0xfe, 0x07, 0xa1, 0x83, 0x2d, 0xad, 0x1c, 0x13,
      0x6f, 0x59, 0xd7, 0x0f, 0x86, 0x53, 0xa5, 0xdc, 0x11, 0x86, 0x63, 0xd6,
  };

  ASSERT_TRUE(CTR_DRBG_reseed(&drbg, kReseed, nullptr, 0));

  uint8_t out[64];
  ASSERT_TRUE(CTR_DRBG_generate(&drbg, out, sizeof(out), nullptr, 0));
  ASSERT_TRUE(CTR_DRBG_generate(&drbg, out, sizeof(out), nullptr, 0));

  const uint8_t kExpected[64] = {
      0xb2, 0xcb, 0x89, 0x05, 0xc0, 0x5e, 0x59, 0x50, 0xca, 0x31, 0x89,
      0x50, 0x96, 0xbe, 0x29, 0xea, 0x3d, 0x5a, 0x3b, 0x82, 0xb2, 0x69,
      0x49, 0x55, 0x54, 0xeb, 0x80, 0xfe, 0x07, 0xde, 0x43, 0xe1, 0x93,
      0xb9, 0xe7, 0xc3, 0xec, 0xe7, 0x3b, 0x80, 0xe0, 0x62, 0xb1, 0xc1,
      0xf6, 0x82, 0x02, 0xfb, 0xb1, 0xc5, 0x2a, 0x04, 0x0e, 0xa2, 0x47,
      0x88, 0x64, 0x29, 0x52, 0x82, 0x23, 0x4a, 0xaa, 0xda,
  };

  EXPECT_EQ(Bytes(kExpected), Bytes(out));

  CTR_DRBG_clear(&drbg);
}

TEST(CTRDRBGTest, Allocated) {
  const uint8_t kSeed[CTR_DRBG_ENTROPY_LEN] = {0};

  bssl::UniquePtr<CTR_DRBG_STATE> allocated(CTR_DRBG_new(kSeed, nullptr, 0));
  ASSERT_TRUE(allocated);

  allocated.reset(CTR_DRBG_new(kSeed, nullptr, 1<<20));
  ASSERT_FALSE(allocated);
}

TEST(CTRDRBGTest, Large) {
  const uint8_t kSeed[CTR_DRBG_ENTROPY_LEN] = {0};

  CTR_DRBG_STATE drbg;
  ASSERT_TRUE(CTR_DRBG_init(&drbg, kSeed, nullptr, 0));

  auto buf = std::make_unique<uint8_t[]>(CTR_DRBG_MAX_GENERATE_LENGTH);
  ASSERT_TRUE(CTR_DRBG_generate(&drbg, buf.get(), CTR_DRBG_MAX_GENERATE_LENGTH,
                                nullptr, 0));

  uint8_t digest[SHA256_DIGEST_LENGTH];
  SHA256(buf.get(), CTR_DRBG_MAX_GENERATE_LENGTH, digest);

  const uint8_t kExpected[SHA256_DIGEST_LENGTH] = {
      0x69, 0x78, 0x15, 0x96, 0xca, 0xc0, 0x3f, 0x6a, 0x6d, 0xed, 0x22,
      0x1e, 0x26, 0xd0, 0x75, 0x49, 0xa0, 0x4b, 0x91, 0x58, 0x3c, 0xf4,
      0xe3, 0x6d, 0xff, 0x41, 0xbf, 0xb9, 0xf8, 0xa8, 0x1c, 0x2b,
  };
  EXPECT_EQ(Bytes(kExpected), Bytes(digest));

  CTR_DRBG_clear(&drbg);
}

TEST(CTRDRBGTest, TestVectors) {
  FileTestGTest("crypto/fipsmodule/rand/ctrdrbg_vectors.txt", [](FileTest *t) {
    std::vector<uint8_t> seed, personalisation, reseed, ai_reseed, ai1, ai2,
        expected;
    ASSERT_TRUE(t->GetBytes(&seed, "EntropyInput"));
    ASSERT_TRUE(t->GetBytes(&personalisation, "PersonalizationString"));
    ASSERT_TRUE(t->GetBytes(&reseed, "EntropyInputReseed"));
    ASSERT_TRUE(t->GetBytes(&ai_reseed, "AdditionalInputReseed"));
    ASSERT_TRUE(t->GetBytes(&ai1, "AdditionalInput1"));
    ASSERT_TRUE(t->GetBytes(&ai2, "AdditionalInput2"));
    ASSERT_TRUE(t->GetBytes(&expected, "ReturnedBits"));

    ASSERT_EQ(static_cast<size_t>(CTR_DRBG_ENTROPY_LEN), seed.size());
    ASSERT_EQ(static_cast<size_t>(CTR_DRBG_ENTROPY_LEN), reseed.size());

    CTR_DRBG_STATE drbg;
    CTR_DRBG_init(&drbg, seed.data(),
                  personalisation.empty() ? nullptr : personalisation.data(),
                  personalisation.size());
    CTR_DRBG_reseed(&drbg, reseed.data(),
                    ai_reseed.empty() ? nullptr : ai_reseed.data(),
                    ai_reseed.size());

    std::vector<uint8_t> out;
    out.resize(expected.size());

    CTR_DRBG_generate(&drbg, out.data(), out.size(),
                      ai1.empty() ? nullptr : ai1.data(), ai1.size());
    CTR_DRBG_generate(&drbg, out.data(), out.size(),
                      ai2.empty() ? nullptr : ai2.data(), ai2.size());

    EXPECT_EQ(Bytes(expected), Bytes(out));
  });
}
