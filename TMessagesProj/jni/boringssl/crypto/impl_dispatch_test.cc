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

#include <openssl/base.h>

#if !defined(NDEBUG) && !defined(BORINGSSL_FIPS) && \
    !defined(BORINGSSL_SHARED_LIBRARY)

#include <functional>
#include <utility>
#include <vector>

#include <openssl/aead.h>
#include <openssl/aes.h>
#include <openssl/cpu.h>
#include <openssl/mem.h>

#include <gtest/gtest.h>

#include "internal.h"


class ImplDispatchTest : public ::testing::Test {
 public:
  void SetUp() override {
#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64)
    aesni_ = OPENSSL_ia32cap_P[1] & (1 << (57 - 32));
    avx_movbe_ = ((OPENSSL_ia32cap_P[1] >> 22) & 0x41) == 0x41;
    ssse3_ = OPENSSL_ia32cap_P[1] & (1 << (41 - 32));
    is_x86_64_ =
#if defined(OPENSSL_X86_64)
        true;
#else
        false;
#endif
#endif  // X86 || X86_64
  }

 protected:
  // AssertFunctionsHit takes a list of pairs (flag index, boolean), and a
  // function to test. It runs the given function and asserts, for each flag
  // index, that the boolean reflects whether that flag index was written or
  // not, and that no other flagged functions were triggered.
  void AssertFunctionsHit(std::vector<std::pair<size_t, bool>> flags,
                          std::function<void()> f) {
    OPENSSL_memset(BORINGSSL_function_hit, 0, sizeof(BORINGSSL_function_hit));

    f();

    for (const auto flag : flags) {
      SCOPED_TRACE(flag.first);

      ASSERT_LT(flag.first, sizeof(BORINGSSL_function_hit));
      EXPECT_EQ(flag.second, BORINGSSL_function_hit[flag.first] == 1);
      BORINGSSL_function_hit[flag.first] = 0;
    }

    for (size_t i = 0; i < sizeof(BORINGSSL_function_hit); i++) {
      EXPECT_EQ(0u, BORINGSSL_function_hit[i])
          << "Flag " << i << " unexpectedly hit";
    }
  }

#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64)
  bool aesni_ = false;
  bool avx_movbe_ = false;
  bool ssse3_ = false;
  bool is_x86_64_ = false;
#endif
};

#if !defined(OPENSSL_NO_ASM) && \
    (defined(OPENSSL_X86) || defined(OPENSSL_X86_64))

constexpr size_t kFlag_aes_hw_ctr32_encrypt_blocks = 0;
constexpr size_t kFlag_aes_hw_encrypt = 1;
constexpr size_t kFlag_aesni_gcm_encrypt = 2;
constexpr size_t kFlag_aes_hw_set_encrypt_key = 3;
constexpr size_t kFlag_vpaes_encrypt = 4;
constexpr size_t kFlag_vpaes_set_encrypt_key = 5;

TEST_F(ImplDispatchTest, AEAD_AES_GCM) {
  AssertFunctionsHit(
      {
          {kFlag_aes_hw_ctr32_encrypt_blocks, aesni_},
          {kFlag_aes_hw_encrypt, aesni_},
          {kFlag_aes_hw_set_encrypt_key, aesni_},
          {kFlag_aesni_gcm_encrypt, is_x86_64_ && aesni_ && avx_movbe_},
          {kFlag_vpaes_encrypt, ssse3_ && !aesni_},
          {kFlag_vpaes_set_encrypt_key, ssse3_ && !aesni_},
      },
      [] {
        const uint8_t kZeros[16] = {0};
        const uint8_t kPlaintext[40] = {1, 2, 3, 4, 0};
        uint8_t ciphertext[sizeof(kPlaintext) + 16];
        size_t ciphertext_len;
        bssl::ScopedEVP_AEAD_CTX ctx;
        ASSERT_TRUE(EVP_AEAD_CTX_init(ctx.get(), EVP_aead_aes_128_gcm(), kZeros,
                                      sizeof(kZeros),
                                      EVP_AEAD_DEFAULT_TAG_LENGTH, nullptr));
        ASSERT_TRUE(EVP_AEAD_CTX_seal(
            ctx.get(), ciphertext, &ciphertext_len, sizeof(ciphertext), kZeros,
            EVP_AEAD_nonce_length(EVP_aead_aes_128_gcm()), kPlaintext,
            sizeof(kPlaintext), nullptr, 0));
      });
}

TEST_F(ImplDispatchTest, AES_set_encrypt_key) {
  AssertFunctionsHit(
      {
          {kFlag_aes_hw_set_encrypt_key, aesni_},
          {kFlag_vpaes_set_encrypt_key, ssse3_ && !aesni_},
      },
      [] {
        AES_KEY key;
        static const uint8_t kZeros[16] = {0};
        AES_set_encrypt_key(kZeros, sizeof(kZeros) * 8, &key);
      });
}

TEST_F(ImplDispatchTest, AES_single_block) {
  AES_KEY key;
  static const uint8_t kZeros[16] = {0};
  AES_set_encrypt_key(kZeros, sizeof(kZeros) * 8, &key);

  AssertFunctionsHit(
      {
          {kFlag_aes_hw_encrypt, aesni_},
          {kFlag_vpaes_encrypt, ssse3_ && !aesni_},
      },
      [&key] {
        uint8_t in[AES_BLOCK_SIZE] = {0};
        uint8_t out[AES_BLOCK_SIZE];
        AES_encrypt(in, out, &key);
      });
}

#endif  // X86 || X86_64

#endif  // !NDEBUG && !FIPS && !SHARED_LIBRARY
