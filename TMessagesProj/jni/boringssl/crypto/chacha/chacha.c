/* Copyright (c) 2014, Google Inc.
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

// Adapted from the public domain, estream code by D. Bernstein.

#include <openssl/chacha.h>

#include <assert.h>
#include <string.h>

#include <openssl/cpu.h>

#include "../internal.h"


#define U8TO32_LITTLE(p)                              \
  (((uint32_t)((p)[0])) | ((uint32_t)((p)[1]) << 8) | \
   ((uint32_t)((p)[2]) << 16) | ((uint32_t)((p)[3]) << 24))

#if !defined(OPENSSL_NO_ASM) &&                         \
    (defined(OPENSSL_X86) || defined(OPENSSL_X86_64) || \
     defined(OPENSSL_ARM) || defined(OPENSSL_AARCH64))

// ChaCha20_ctr32 is defined in asm/chacha-*.pl.
void ChaCha20_ctr32(uint8_t *out, const uint8_t *in, size_t in_len,
                    const uint32_t key[8], const uint32_t counter[4]);

void CRYPTO_chacha_20(uint8_t *out, const uint8_t *in, size_t in_len,
                      const uint8_t key[32], const uint8_t nonce[12],
                      uint32_t counter) {
  assert(!buffers_alias(out, in_len, in, in_len) || in == out);

  uint32_t counter_nonce[4];  counter_nonce[0] = counter;
  counter_nonce[1] = U8TO32_LITTLE(nonce + 0);
  counter_nonce[2] = U8TO32_LITTLE(nonce + 4);
  counter_nonce[3] = U8TO32_LITTLE(nonce + 8);

  const uint32_t *key_ptr = (const uint32_t *)key;
#if !defined(OPENSSL_X86) && !defined(OPENSSL_X86_64)
  // The assembly expects the key to be four-byte aligned.
  uint32_t key_u32[8];
  if ((((uintptr_t)key) & 3) != 0) {
    key_u32[0] = U8TO32_LITTLE(key + 0);
    key_u32[1] = U8TO32_LITTLE(key + 4);
    key_u32[2] = U8TO32_LITTLE(key + 8);
    key_u32[3] = U8TO32_LITTLE(key + 12);
    key_u32[4] = U8TO32_LITTLE(key + 16);
    key_u32[5] = U8TO32_LITTLE(key + 20);
    key_u32[6] = U8TO32_LITTLE(key + 24);
    key_u32[7] = U8TO32_LITTLE(key + 28);

    key_ptr = key_u32;
  }
#endif

  ChaCha20_ctr32(out, in, in_len, key_ptr, counter_nonce);
}

#else

// sigma contains the ChaCha constants, which happen to be an ASCII string.
static const uint8_t sigma[16] = { 'e', 'x', 'p', 'a', 'n', 'd', ' ', '3',
                                   '2', '-', 'b', 'y', 't', 'e', ' ', 'k' };

#define ROTATE(v, n) (((v) << (n)) | ((v) >> (32 - (n))))

#define U32TO8_LITTLE(p, v)    \
  {                            \
    (p)[0] = (v >> 0) & 0xff;  \
    (p)[1] = (v >> 8) & 0xff;  \
    (p)[2] = (v >> 16) & 0xff; \
    (p)[3] = (v >> 24) & 0xff; \
  }

// QUARTERROUND updates a, b, c, d with a ChaCha "quarter" round.
#define QUARTERROUND(a, b, c, d)                \
  x[a] += x[b]; x[d] = ROTATE(x[d] ^ x[a], 16); \
  x[c] += x[d]; x[b] = ROTATE(x[b] ^ x[c], 12); \
  x[a] += x[b]; x[d] = ROTATE(x[d] ^ x[a],  8); \
  x[c] += x[d]; x[b] = ROTATE(x[b] ^ x[c],  7);

// chacha_core performs 20 rounds of ChaCha on the input words in
// |input| and writes the 64 output bytes to |output|.
static void chacha_core(uint8_t output[64], const uint32_t input[16]) {
  uint32_t x[16];
  int i;

  OPENSSL_memcpy(x, input, sizeof(uint32_t) * 16);
  for (i = 20; i > 0; i -= 2) {
    QUARTERROUND(0, 4, 8, 12)
    QUARTERROUND(1, 5, 9, 13)
    QUARTERROUND(2, 6, 10, 14)
    QUARTERROUND(3, 7, 11, 15)
    QUARTERROUND(0, 5, 10, 15)
    QUARTERROUND(1, 6, 11, 12)
    QUARTERROUND(2, 7, 8, 13)
    QUARTERROUND(3, 4, 9, 14)
  }

  for (i = 0; i < 16; ++i) {
    x[i] += input[i];
  }
  for (i = 0; i < 16; ++i) {
    U32TO8_LITTLE(output + 4 * i, x[i]);
  }
}

void CRYPTO_chacha_20(uint8_t *out, const uint8_t *in, size_t in_len,
                      const uint8_t key[32], const uint8_t nonce[12],
                      uint32_t counter) {
  assert(!buffers_alias(out, in_len, in, in_len) || in == out);

  uint32_t input[16];
  uint8_t buf[64];
  size_t todo, i;

  input[0] = U8TO32_LITTLE(sigma + 0);
  input[1] = U8TO32_LITTLE(sigma + 4);
  input[2] = U8TO32_LITTLE(sigma + 8);
  input[3] = U8TO32_LITTLE(sigma + 12);

  input[4] = U8TO32_LITTLE(key + 0);
  input[5] = U8TO32_LITTLE(key + 4);
  input[6] = U8TO32_LITTLE(key + 8);
  input[7] = U8TO32_LITTLE(key + 12);

  input[8] = U8TO32_LITTLE(key + 16);
  input[9] = U8TO32_LITTLE(key + 20);
  input[10] = U8TO32_LITTLE(key + 24);
  input[11] = U8TO32_LITTLE(key + 28);

  input[12] = counter;
  input[13] = U8TO32_LITTLE(nonce + 0);
  input[14] = U8TO32_LITTLE(nonce + 4);
  input[15] = U8TO32_LITTLE(nonce + 8);

  while (in_len > 0) {
    todo = sizeof(buf);
    if (in_len < todo) {
      todo = in_len;
    }

    chacha_core(buf, input);
    for (i = 0; i < todo; i++) {
      out[i] = in[i] ^ buf[i];
    }

    out += todo;
    in += todo;
    in_len -= todo;

    input[12]++;
  }
}

#endif
