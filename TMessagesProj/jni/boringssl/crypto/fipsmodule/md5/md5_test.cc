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

#include <openssl/md5.h>

#include <gtest/gtest.h>

#include "internal.h"
#include "../../test/abi_test.h"


#if defined(MD5_ASM) && defined(SUPPORTS_ABI_TEST)
TEST(MD5Test, ABI) {
  MD5_CTX ctx;
  MD5_Init(&ctx);

  static const uint8_t kBuf[MD5_CBLOCK * 8] = {0};
  CHECK_ABI(md5_block_asm_data_order, ctx.h, kBuf, 1);
  CHECK_ABI(md5_block_asm_data_order, ctx.h, kBuf, 2);
  CHECK_ABI(md5_block_asm_data_order, ctx.h, kBuf, 4);
  CHECK_ABI(md5_block_asm_data_order, ctx.h, kBuf, 8);
}
#endif  // MD5_ASM && SUPPORTS_ABI_TEST
