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

#include <openssl/sha.h>

#include <gtest/gtest.h>

#include "internal.h"
#include "../../test/abi_test.h"


#if defined(SHA1_ASM) && defined(SUPPORTS_ABI_TEST)
TEST(SHATest, SHA1ABI) {
  SHA_CTX ctx;
  SHA1_Init(&ctx);

  static const uint8_t kBuf[SHA_CBLOCK * 8] = {0};
  CHECK_ABI(sha1_block_data_order, ctx.h, kBuf, 1);
  CHECK_ABI(sha1_block_data_order, ctx.h, kBuf, 2);
  CHECK_ABI(sha1_block_data_order, ctx.h, kBuf, 4);
  CHECK_ABI(sha1_block_data_order, ctx.h, kBuf, 8);
}
#endif  // SHA1_ASM && SUPPORTS_ABI_TEST

#if defined(SHA256_ASM) && defined(SUPPORTS_ABI_TEST)
TEST(SHATest, SHA256ABI) {
  SHA256_CTX ctx;
  SHA256_Init(&ctx);

  static const uint8_t kBuf[SHA256_CBLOCK * 8] = {0};
  CHECK_ABI(sha256_block_data_order, ctx.h, kBuf, 1);
  CHECK_ABI(sha256_block_data_order, ctx.h, kBuf, 2);
  CHECK_ABI(sha256_block_data_order, ctx.h, kBuf, 4);
  CHECK_ABI(sha256_block_data_order, ctx.h, kBuf, 8);
}
#endif  // SHA256_ASM && SUPPORTS_ABI_TEST

#if defined(SHA512_ASM) && defined(SUPPORTS_ABI_TEST)
TEST(SHATest, SHA512ABI) {
  SHA512_CTX ctx;
  SHA512_Init(&ctx);

  static const uint8_t kBuf[SHA512_CBLOCK * 4] = {0};
  CHECK_ABI(sha512_block_data_order, ctx.h, kBuf, 1);
  CHECK_ABI(sha512_block_data_order, ctx.h, kBuf, 2);
  CHECK_ABI(sha512_block_data_order, ctx.h, kBuf, 3);
  CHECK_ABI(sha512_block_data_order, ctx.h, kBuf, 4);
}
#endif  // SHA512_ASM && SUPPORTS_ABI_TEST
