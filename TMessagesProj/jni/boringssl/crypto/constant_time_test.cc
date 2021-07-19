/*
 * Utilities for constant-time cryptography.
 *
 * Author: Emilia Kasper (emilia@openssl.org)
 * Based on previous work by Bodo Moeller, Emilia Kasper, Adam Langley
 * (Google).
 * ====================================================================
 * Copyright (c) 2014 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.]
 */

#include "internal.h"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

#include <limits>

#include <gtest/gtest.h>

#include <openssl/mem.h>
#include <openssl/rand.h>


static uint8_t FromBool8(bool b) {
  return b ? CONSTTIME_TRUE_8 : CONSTTIME_FALSE_8;
}

static crypto_word_t FromBoolW(bool b) {
  return b ? CONSTTIME_TRUE_W : CONSTTIME_FALSE_W;
}

static const uint8_t test_values_8[] = {0, 1, 2, 20, 32, 127, 128, 129, 255};

static crypto_word_t test_values_w[] = {
    0,
    1,
    1024,
    12345,
    32000,
#if defined(OPENSSL_64_BIT)
    0xffffffff / 2 - 1,
    0xffffffff / 2,
    0xffffffff / 2 + 1,
    0xffffffff - 1,
    0xffffffff,
#endif
    std::numeric_limits<crypto_word_t>::max() / 2 - 1,
    std::numeric_limits<crypto_word_t>::max() / 2,
    std::numeric_limits<crypto_word_t>::max() / 2 + 1,
    std::numeric_limits<crypto_word_t>::max() - 1,
    std::numeric_limits<crypto_word_t>::max(),
};

static int signed_test_values[] = {
    0,     1,      -1,      1024,    -1024,       12345,      -12345,
    32000, -32000, INT_MAX, INT_MIN, INT_MAX - 1, INT_MIN + 1};

TEST(ConstantTimeTest, Test) {
  for (crypto_word_t a : test_values_w) {
    SCOPED_TRACE(a);

    EXPECT_EQ(FromBoolW(a == 0), constant_time_is_zero_w(a));
    EXPECT_EQ(FromBool8(a == 0), constant_time_is_zero_8(a));

    for (crypto_word_t b : test_values_w) {
      SCOPED_TRACE(b);

      EXPECT_EQ(FromBoolW(a < b), constant_time_lt_w(a, b));
      EXPECT_EQ(FromBool8(a < b), constant_time_lt_8(a, b));

      EXPECT_EQ(FromBoolW(a >= b), constant_time_ge_w(a, b));
      EXPECT_EQ(FromBool8(a >= b), constant_time_ge_8(a, b));

      EXPECT_EQ(FromBoolW(a == b), constant_time_eq_w(a, b));
      EXPECT_EQ(FromBool8(a == b), constant_time_eq_8(a, b));

      EXPECT_EQ(a, constant_time_select_w(CONSTTIME_TRUE_W, a, b));
      EXPECT_EQ(b, constant_time_select_w(CONSTTIME_FALSE_W, a, b));
    }
  }

  for (int a : signed_test_values) {
    SCOPED_TRACE(a);
    for (int b : signed_test_values) {
      SCOPED_TRACE(b);

      EXPECT_EQ(a, constant_time_select_int(CONSTTIME_TRUE_W, a, b));
      EXPECT_EQ(b, constant_time_select_int(CONSTTIME_FALSE_W, a, b));

      EXPECT_EQ(FromBoolW(a == b), constant_time_eq_int(a, b));
      EXPECT_EQ(FromBool8(a == b), constant_time_eq_int_8(a, b));
    }
  }

  for (uint8_t a : test_values_8) {
    SCOPED_TRACE(static_cast<int>(a));
    for (uint8_t b : test_values_8) {
      SCOPED_TRACE(static_cast<int>(b));
      EXPECT_EQ(a, constant_time_select_8(CONSTTIME_TRUE_8, a, b));
      EXPECT_EQ(b, constant_time_select_8(CONSTTIME_FALSE_8, a, b));
    }
  }
}

TEST(ConstantTimeTest, MemCmp) {
  uint8_t buf[256], copy[256];
  RAND_bytes(buf, sizeof(buf));

  OPENSSL_memcpy(copy, buf, sizeof(buf));
  EXPECT_EQ(0, CRYPTO_memcmp(buf, copy, sizeof(buf)));

  for (size_t i = 0; i < sizeof(buf); i++) {
    for (uint8_t bit = 1; bit != 0; bit <<= 1) {
      OPENSSL_memcpy(copy, buf, sizeof(buf));
      copy[i] ^= bit;
      EXPECT_NE(0, CRYPTO_memcmp(buf, copy, sizeof(buf)));
    }
  }
}

TEST(ConstantTimeTest, ValueBarrier) {
  for (int i = 0; i < 10; i++) {
    crypto_word_t word;
    RAND_bytes(reinterpret_cast<uint8_t *>(&word), sizeof(word));
    EXPECT_EQ(word, value_barrier_w(word));

    uint32_t u32;
    RAND_bytes(reinterpret_cast<uint8_t *>(&u32), sizeof(u32));
    EXPECT_EQ(u32, value_barrier_u32(u32));

    uint64_t u64;
    RAND_bytes(reinterpret_cast<uint8_t *>(&u64), sizeof(u64));
    EXPECT_EQ(u64, value_barrier_u64(u64));
  }
}
