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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <memory>
#include <vector>

#include <gtest/gtest.h>

#include <openssl/aes.h>
#include <openssl/rand.h>

#include "internal.h"
#include "../../internal.h"
#include "../../test/abi_test.h"
#include "../../test/file_test.h"
#include "../../test/test_util.h"
#include "../../test/wycheproof_util.h"


static void TestRaw(FileTest *t) {
  std::vector<uint8_t> key, plaintext, ciphertext;
  ASSERT_TRUE(t->GetBytes(&key, "Key"));
  ASSERT_TRUE(t->GetBytes(&plaintext, "Plaintext"));
  ASSERT_TRUE(t->GetBytes(&ciphertext, "Ciphertext"));

  ASSERT_EQ(static_cast<unsigned>(AES_BLOCK_SIZE), plaintext.size());
  ASSERT_EQ(static_cast<unsigned>(AES_BLOCK_SIZE), ciphertext.size());

  AES_KEY aes_key;
  ASSERT_EQ(0, AES_set_encrypt_key(key.data(), 8 * key.size(), &aes_key));

  // Test encryption.
  uint8_t block[AES_BLOCK_SIZE];
  AES_encrypt(plaintext.data(), block, &aes_key);
  EXPECT_EQ(Bytes(ciphertext), Bytes(block));

  // Test in-place encryption.
  OPENSSL_memcpy(block, plaintext.data(), AES_BLOCK_SIZE);
  AES_encrypt(block, block, &aes_key);
  EXPECT_EQ(Bytes(ciphertext), Bytes(block));

  ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes_key));

  // Test decryption.
  AES_decrypt(ciphertext.data(), block, &aes_key);
  EXPECT_EQ(Bytes(plaintext), Bytes(block));

  // Test in-place decryption.
  OPENSSL_memcpy(block, ciphertext.data(), AES_BLOCK_SIZE);
  AES_decrypt(block, block, &aes_key);
  EXPECT_EQ(Bytes(plaintext), Bytes(block));
}

static void TestKeyWrap(FileTest *t) {
  // All test vectors use the default IV, so test both with implicit and
  // explicit IV.
  //
  // TODO(davidben): Find test vectors that use a different IV.
  static const uint8_t kDefaultIV[] = {
      0xa6, 0xa6, 0xa6, 0xa6, 0xa6, 0xa6, 0xa6, 0xa6,
  };

  std::vector<uint8_t> key, plaintext, ciphertext;
  ASSERT_TRUE(t->GetBytes(&key, "Key"));
  ASSERT_TRUE(t->GetBytes(&plaintext, "Plaintext"));
  ASSERT_TRUE(t->GetBytes(&ciphertext, "Ciphertext"));

  ASSERT_EQ(plaintext.size() + 8, ciphertext.size())
      << "Invalid Plaintext and Ciphertext lengths.";

  // Test encryption.
  AES_KEY aes_key;
  ASSERT_EQ(0, AES_set_encrypt_key(key.data(), 8 * key.size(), &aes_key));

  // Test with implicit IV.
  std::unique_ptr<uint8_t[]> buf(new uint8_t[ciphertext.size()]);
  int len = AES_wrap_key(&aes_key, nullptr /* iv */, buf.get(),
                         plaintext.data(), plaintext.size());
  ASSERT_GE(len, 0);
  EXPECT_EQ(Bytes(ciphertext), Bytes(buf.get(), static_cast<size_t>(len)));

  // Test with explicit IV.
  OPENSSL_memset(buf.get(), 0, ciphertext.size());
  len = AES_wrap_key(&aes_key, kDefaultIV, buf.get(), plaintext.data(),
                     plaintext.size());
  ASSERT_GE(len, 0);
  EXPECT_EQ(Bytes(ciphertext), Bytes(buf.get(), static_cast<size_t>(len)));

  // Test decryption.
  ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes_key));

  // Test with implicit IV.
  buf.reset(new uint8_t[plaintext.size()]);
  len = AES_unwrap_key(&aes_key, nullptr /* iv */, buf.get(), ciphertext.data(),
                       ciphertext.size());
  ASSERT_GE(len, 0);
  EXPECT_EQ(Bytes(plaintext), Bytes(buf.get(), static_cast<size_t>(len)));

  // Test with explicit IV.
  OPENSSL_memset(buf.get(), 0, plaintext.size());
  len = AES_unwrap_key(&aes_key, kDefaultIV, buf.get(), ciphertext.data(),
                       ciphertext.size());
  ASSERT_GE(len, 0);

  // Test corrupted ciphertext.
  ciphertext[0] ^= 1;
  EXPECT_EQ(-1, AES_unwrap_key(&aes_key, nullptr /* iv */, buf.get(),
                               ciphertext.data(), ciphertext.size()));
}

static void TestKeyWrapWithPadding(FileTest *t) {
  std::vector<uint8_t> key, plaintext, ciphertext;
  ASSERT_TRUE(t->GetBytes(&key, "Key"));
  ASSERT_TRUE(t->GetBytes(&plaintext, "Plaintext"));
  ASSERT_TRUE(t->GetBytes(&ciphertext, "Ciphertext"));

  // Test encryption.
  AES_KEY aes_key;
  ASSERT_EQ(0, AES_set_encrypt_key(key.data(), 8 * key.size(), &aes_key));
  std::unique_ptr<uint8_t[]> buf(new uint8_t[plaintext.size() + 15]);
  size_t len;
  ASSERT_TRUE(AES_wrap_key_padded(&aes_key, buf.get(), &len,
                                  plaintext.size() + 15, plaintext.data(),
                                  plaintext.size()));
  EXPECT_EQ(Bytes(ciphertext), Bytes(buf.get(), static_cast<size_t>(len)));

  // Test decryption
  ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes_key));
  buf.reset(new uint8_t[ciphertext.size() - 8]);
  ASSERT_TRUE(AES_unwrap_key_padded(&aes_key, buf.get(), &len,
                                    ciphertext.size() - 8, ciphertext.data(),
                                    ciphertext.size()));
  ASSERT_EQ(len, plaintext.size());
  EXPECT_EQ(Bytes(plaintext), Bytes(buf.get(), static_cast<size_t>(len)));
}

TEST(AESTest, TestVectors) {
  FileTestGTest("crypto/fipsmodule/aes/aes_tests.txt", [](FileTest *t) {
    if (t->GetParameter() == "Raw") {
      TestRaw(t);
    } else if (t->GetParameter() == "KeyWrap") {
      TestKeyWrap(t);
    } else if (t->GetParameter() == "KeyWrapWithPadding") {
      TestKeyWrapWithPadding(t);
    } else {
      ADD_FAILURE() << "Unknown mode " << t->GetParameter();
    }
  });
}

TEST(AESTest, WycheproofKeyWrap) {
  FileTestGTest("third_party/wycheproof_testvectors/kw_test.txt",
                [](FileTest *t) {
    std::string key_size;
    ASSERT_TRUE(t->GetInstruction(&key_size, "keySize"));
    std::vector<uint8_t> ct, key, msg;
    ASSERT_TRUE(t->GetBytes(&ct, "ct"));
    ASSERT_TRUE(t->GetBytes(&key, "key"));
    ASSERT_TRUE(t->GetBytes(&msg, "msg"));
    ASSERT_EQ(static_cast<unsigned>(atoi(key_size.c_str())), key.size() * 8);
    WycheproofResult result;
    ASSERT_TRUE(GetWycheproofResult(t, &result));

    if (result != WycheproofResult::kInvalid) {
      ASSERT_GE(ct.size(), 8u);

      AES_KEY aes;
      ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes));
      std::vector<uint8_t> out(ct.size() - 8);
      int len = AES_unwrap_key(&aes, nullptr, out.data(), ct.data(), ct.size());
      ASSERT_EQ(static_cast<int>(out.size()), len);
      EXPECT_EQ(Bytes(msg), Bytes(out));

      out.resize(msg.size() + 8);
      ASSERT_EQ(0, AES_set_encrypt_key(key.data(), 8 * key.size(), &aes));
      len = AES_wrap_key(&aes, nullptr, out.data(), msg.data(), msg.size());
      ASSERT_EQ(static_cast<int>(out.size()), len);
      EXPECT_EQ(Bytes(ct), Bytes(out));
    } else {
      AES_KEY aes;
      ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes));
      std::vector<uint8_t> out(ct.size() < 8 ? 0 : ct.size() - 8);
      int len = AES_unwrap_key(&aes, nullptr, out.data(), ct.data(), ct.size());
      EXPECT_EQ(-1, len);
    }
  });
}

TEST(AESTest, WycheproofKeyWrapWithPadding) {
  FileTestGTest("third_party/wycheproof_testvectors/kwp_test.txt",
                [](FileTest *t) {
    std::string key_size;
    ASSERT_TRUE(t->GetInstruction(&key_size, "keySize"));
    std::vector<uint8_t> ct, key, msg;
    ASSERT_TRUE(t->GetBytes(&ct, "ct"));
    ASSERT_TRUE(t->GetBytes(&key, "key"));
    ASSERT_TRUE(t->GetBytes(&msg, "msg"));
    ASSERT_EQ(static_cast<unsigned>(atoi(key_size.c_str())), key.size() * 8);
    WycheproofResult result;
    ASSERT_TRUE(GetWycheproofResult(t, &result));

    // Wycheproof contains test vectors with empty messages that it believes
    // should pass. However, both RFC 5649 and SP 800-38F section 5.3.1 say that
    // the minimum length is one. Therefore we consider test cases with an empty
    // message to be invalid.
    if (result != WycheproofResult::kInvalid && !msg.empty()) {
      AES_KEY aes;
      ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes));
      std::vector<uint8_t> out(ct.size() - 8);
      size_t len;
      ASSERT_TRUE(AES_unwrap_key_padded(&aes, out.data(), &len, ct.size() - 8,
                                        ct.data(), ct.size()));
      EXPECT_EQ(Bytes(msg), Bytes(out.data(), len));

      out.resize(msg.size() + 15);
      ASSERT_EQ(0, AES_set_encrypt_key(key.data(), 8 * key.size(), &aes));
      ASSERT_TRUE(AES_wrap_key_padded(&aes, out.data(), &len, msg.size() + 15,
                                      msg.data(), msg.size()));
      EXPECT_EQ(Bytes(ct), Bytes(out.data(), len));
    } else {
      AES_KEY aes;
      ASSERT_EQ(0, AES_set_decrypt_key(key.data(), 8 * key.size(), &aes));
      std::vector<uint8_t> out(ct.size());
      size_t len;
      ASSERT_FALSE(AES_unwrap_key_padded(&aes, out.data(), &len, ct.size(),
                                         ct.data(), ct.size()));
    }
  });
}

TEST(AESTest, WrapBadLengths) {
  uint8_t key[128/8] = {0};
  AES_KEY aes;
  ASSERT_EQ(0, AES_set_encrypt_key(key, 128, &aes));

  // Input lengths to |AES_wrap_key| must be a multiple of 8 and at least 16.
  static const size_t kLengths[] = {0, 1,  2,  3,  4,  5,  6,  7, 8,
                                    9, 10, 11, 12, 13, 14, 15, 20};
  for (size_t len : kLengths) {
    SCOPED_TRACE(len);
    std::vector<uint8_t> in(len);
    std::vector<uint8_t> out(len + 8);
    EXPECT_EQ(-1,
              AES_wrap_key(&aes, nullptr, out.data(), in.data(), in.size()));
  }
}

TEST(AESTest, InvalidKeySize) {
  static const uint8_t kZero[8] = {0};
  AES_KEY key;
  EXPECT_LT(AES_set_encrypt_key(kZero, 42, &key), 0);
  EXPECT_LT(AES_set_decrypt_key(kZero, 42, &key), 0);
}

#if defined(SUPPORTS_ABI_TEST)
TEST(AESTest, ABI) {
  for (int bits : {128, 192, 256}) {
    SCOPED_TRACE(bits);
    const uint8_t kKey[256/8] = {0};
    AES_KEY key;
    uint8_t block[AES_BLOCK_SIZE];
    uint8_t buf[AES_BLOCK_SIZE * 64] = {0};
    std::vector<int> block_counts;
    if (bits == 128) {
      block_counts = {0, 1, 2, 3, 4, 8, 16, 31};
    } else {
      // Unwind tests are very slow. Assume that the various input sizes do not
      // differ significantly by round count for ABI purposes.
      block_counts = {0, 1, 8};
    }

    CHECK_ABI(aes_nohw_set_encrypt_key, kKey, bits, &key);
    CHECK_ABI(aes_nohw_encrypt, block, block, &key);
#if defined(AES_NOHW_CBC)
    for (size_t blocks : block_counts) {
      SCOPED_TRACE(blocks);
      CHECK_ABI(aes_nohw_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                block, AES_ENCRYPT);
    }
#endif

    CHECK_ABI(aes_nohw_set_decrypt_key, kKey, bits, &key);
    CHECK_ABI(aes_nohw_decrypt, block, block, &key);
#if defined(AES_NOHW_CBC)
    for (size_t blocks : block_counts) {
      SCOPED_TRACE(blocks);
      CHECK_ABI(aes_nohw_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                block, AES_DECRYPT);
    }
#endif

    if (bsaes_capable()) {
      vpaes_set_encrypt_key(kKey, bits, &key);
      CHECK_ABI(vpaes_encrypt_key_to_bsaes, &key, &key);
      for (size_t blocks : block_counts) {
        SCOPED_TRACE(blocks);
        if (blocks != 0) {
          CHECK_ABI(bsaes_ctr32_encrypt_blocks, buf, buf, blocks, &key, block);
        }
      }

      vpaes_set_decrypt_key(kKey, bits, &key);
      CHECK_ABI(vpaes_decrypt_key_to_bsaes, &key, &key);
      for (size_t blocks : block_counts) {
        SCOPED_TRACE(blocks);
        CHECK_ABI(bsaes_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  block, AES_DECRYPT);
      }
    }

    if (vpaes_capable()) {
      CHECK_ABI(vpaes_set_encrypt_key, kKey, bits, &key);
      CHECK_ABI(vpaes_encrypt, block, block, &key);
      for (size_t blocks : block_counts) {
        SCOPED_TRACE(blocks);
#if defined(VPAES_CBC)
        CHECK_ABI(vpaes_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  block, AES_ENCRYPT);
#endif
#if defined(VPAES_CTR32)
        CHECK_ABI(vpaes_ctr32_encrypt_blocks, buf, buf, blocks, &key, block);
#endif
      }

      CHECK_ABI(vpaes_set_decrypt_key, kKey, bits, &key);
      CHECK_ABI(vpaes_decrypt, block, block, &key);
#if defined(VPAES_CBC)
      for (size_t blocks : block_counts) {
        SCOPED_TRACE(blocks);
        CHECK_ABI(vpaes_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  block, AES_DECRYPT);
      }
#endif  // VPAES_CBC
    }

    if (hwaes_capable()) {
      CHECK_ABI(aes_hw_set_encrypt_key, kKey, bits, &key);
      CHECK_ABI(aes_hw_encrypt, block, block, &key);
      for (size_t blocks : block_counts) {
        SCOPED_TRACE(blocks);
        CHECK_ABI(aes_hw_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  block, AES_ENCRYPT);
        CHECK_ABI(aes_hw_ctr32_encrypt_blocks, buf, buf, blocks, &key, block);
#if defined(HWAES_ECB)
        CHECK_ABI(aes_hw_ecb_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  AES_ENCRYPT);
#endif
      }

      CHECK_ABI(aes_hw_set_decrypt_key, kKey, bits, &key);
      CHECK_ABI(aes_hw_decrypt, block, block, &key);
      for (size_t blocks : block_counts) {
        SCOPED_TRACE(blocks);
        CHECK_ABI(aes_hw_cbc_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  block, AES_DECRYPT);
#if defined(HWAES_ECB)
        CHECK_ABI(aes_hw_ecb_encrypt, buf, buf, AES_BLOCK_SIZE * blocks, &key,
                  AES_DECRYPT);
#endif
      }
    }
  }
}
#endif  // SUPPORTS_ABI_TEST

#if defined(BSAES) && !defined(BORINGSSL_SHARED_LIBRARY)
static Bytes AESKeyToBytes(const AES_KEY *key) {
  return Bytes(reinterpret_cast<const uint8_t *>(key), sizeof(*key));
}

TEST(AESTest, VPAESToBSAESConvert) {
  const int kNumIterations = 1000;
  for (int i = 0; i < kNumIterations; i++) {
    uint8_t key[256 / 8];
    RAND_bytes(key, sizeof(key));
    SCOPED_TRACE(Bytes(key));
    for (unsigned bits : {128u, 192u, 256u}) {
      SCOPED_TRACE(bits);
      for (bool enc : {false, true}) {
        SCOPED_TRACE(enc);
        AES_KEY nohw, vpaes, bsaes;
        OPENSSL_memset(&nohw, 0xaa, sizeof(nohw));
        OPENSSL_memset(&vpaes, 0xaa, sizeof(vpaes));
        OPENSSL_memset(&bsaes, 0xaa, sizeof(bsaes));

        if (enc) {
          aes_nohw_set_encrypt_key(key, bits, &nohw);
          vpaes_set_encrypt_key(key, bits, &vpaes);
          vpaes_encrypt_key_to_bsaes(&bsaes, &vpaes);
        } else {
          aes_nohw_set_decrypt_key(key, bits, &nohw);
          vpaes_set_decrypt_key(key, bits, &vpaes);
          vpaes_decrypt_key_to_bsaes(&bsaes, &vpaes);
        }

        // Although not fatal, stop running if this fails, otherwise we'll spam
        // the user's console.
        ASSERT_EQ(AESKeyToBytes(&nohw), AESKeyToBytes(&bsaes));

        // Repeat the test in-place.
        OPENSSL_memcpy(&bsaes, &vpaes, sizeof(AES_KEY));
        if (enc) {
          vpaes_encrypt_key_to_bsaes(&bsaes, &vpaes);
        } else {
          vpaes_decrypt_key_to_bsaes(&bsaes, &vpaes);
        }

        ASSERT_EQ(AESKeyToBytes(&nohw), AESKeyToBytes(&bsaes));
      }
    }
  }
}
#endif  // !NO_ASM && X86_64 && !SHARED_LIBRARY
