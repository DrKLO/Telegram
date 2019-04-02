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

// This code is mostly taken from the ref10 version of Ed25519 in SUPERCOP
// 20141124 (http://bench.cr.yp.to/supercop.html). That code is released as
// public domain but this file has the ISC license just to keep licencing
// simple.
//
// The field functions are shared by Ed25519 and X25519 where possible.

#include <openssl/curve25519.h>

#include <string.h>

#include "../internal.h"
#include "../../third_party/fiat/internal.h"


#if defined(BORINGSSL_X25519_X86_64)

typedef struct { uint64_t v[5]; } fe25519;

// These functions are defined in asm/x25519-x86_64.S
void x25519_x86_64_work_cswap(fe25519 *, uint64_t);
void x25519_x86_64_mul(fe25519 *out, const fe25519 *a, const fe25519 *b);
void x25519_x86_64_square(fe25519 *out, const fe25519 *a);
void x25519_x86_64_freeze(fe25519 *);
void x25519_x86_64_ladderstep(fe25519 *work);

static void fe25519_setint(fe25519 *r, unsigned v) {
  r->v[0] = v;
  r->v[1] = 0;
  r->v[2] = 0;
  r->v[3] = 0;
  r->v[4] = 0;
}

// Assumes input x being reduced below 2^255
static void fe25519_pack(unsigned char r[32], const fe25519 *x) {
  fe25519 t;
  t = *x;
  x25519_x86_64_freeze(&t);

  r[0] = (uint8_t)(t.v[0] & 0xff);
  r[1] = (uint8_t)((t.v[0] >> 8) & 0xff);
  r[2] = (uint8_t)((t.v[0] >> 16) & 0xff);
  r[3] = (uint8_t)((t.v[0] >> 24) & 0xff);
  r[4] = (uint8_t)((t.v[0] >> 32) & 0xff);
  r[5] = (uint8_t)((t.v[0] >> 40) & 0xff);
  r[6] = (uint8_t)((t.v[0] >> 48));

  r[6] ^= (uint8_t)((t.v[1] << 3) & 0xf8);
  r[7] = (uint8_t)((t.v[1] >> 5) & 0xff);
  r[8] = (uint8_t)((t.v[1] >> 13) & 0xff);
  r[9] = (uint8_t)((t.v[1] >> 21) & 0xff);
  r[10] = (uint8_t)((t.v[1] >> 29) & 0xff);
  r[11] = (uint8_t)((t.v[1] >> 37) & 0xff);
  r[12] = (uint8_t)((t.v[1] >> 45));

  r[12] ^= (uint8_t)((t.v[2] << 6) & 0xc0);
  r[13] = (uint8_t)((t.v[2] >> 2) & 0xff);
  r[14] = (uint8_t)((t.v[2] >> 10) & 0xff);
  r[15] = (uint8_t)((t.v[2] >> 18) & 0xff);
  r[16] = (uint8_t)((t.v[2] >> 26) & 0xff);
  r[17] = (uint8_t)((t.v[2] >> 34) & 0xff);
  r[18] = (uint8_t)((t.v[2] >> 42) & 0xff);
  r[19] = (uint8_t)((t.v[2] >> 50));

  r[19] ^= (uint8_t)((t.v[3] << 1) & 0xfe);
  r[20] = (uint8_t)((t.v[3] >> 7) & 0xff);
  r[21] = (uint8_t)((t.v[3] >> 15) & 0xff);
  r[22] = (uint8_t)((t.v[3] >> 23) & 0xff);
  r[23] = (uint8_t)((t.v[3] >> 31) & 0xff);
  r[24] = (uint8_t)((t.v[3] >> 39) & 0xff);
  r[25] = (uint8_t)((t.v[3] >> 47));

  r[25] ^= (uint8_t)((t.v[4] << 4) & 0xf0);
  r[26] = (uint8_t)((t.v[4] >> 4) & 0xff);
  r[27] = (uint8_t)((t.v[4] >> 12) & 0xff);
  r[28] = (uint8_t)((t.v[4] >> 20) & 0xff);
  r[29] = (uint8_t)((t.v[4] >> 28) & 0xff);
  r[30] = (uint8_t)((t.v[4] >> 36) & 0xff);
  r[31] = (uint8_t)((t.v[4] >> 44));
}

static void fe25519_unpack(fe25519 *r, const uint8_t x[32]) {
  r->v[0] = x[0];
  r->v[0] += (uint64_t)x[1] << 8;
  r->v[0] += (uint64_t)x[2] << 16;
  r->v[0] += (uint64_t)x[3] << 24;
  r->v[0] += (uint64_t)x[4] << 32;
  r->v[0] += (uint64_t)x[5] << 40;
  r->v[0] += ((uint64_t)x[6] & 7) << 48;

  r->v[1] = x[6] >> 3;
  r->v[1] += (uint64_t)x[7] << 5;
  r->v[1] += (uint64_t)x[8] << 13;
  r->v[1] += (uint64_t)x[9] << 21;
  r->v[1] += (uint64_t)x[10] << 29;
  r->v[1] += (uint64_t)x[11] << 37;
  r->v[1] += ((uint64_t)x[12] & 63) << 45;

  r->v[2] = x[12] >> 6;
  r->v[2] += (uint64_t)x[13] << 2;
  r->v[2] += (uint64_t)x[14] << 10;
  r->v[2] += (uint64_t)x[15] << 18;
  r->v[2] += (uint64_t)x[16] << 26;
  r->v[2] += (uint64_t)x[17] << 34;
  r->v[2] += (uint64_t)x[18] << 42;
  r->v[2] += ((uint64_t)x[19] & 1) << 50;

  r->v[3] = x[19] >> 1;
  r->v[3] += (uint64_t)x[20] << 7;
  r->v[3] += (uint64_t)x[21] << 15;
  r->v[3] += (uint64_t)x[22] << 23;
  r->v[3] += (uint64_t)x[23] << 31;
  r->v[3] += (uint64_t)x[24] << 39;
  r->v[3] += ((uint64_t)x[25] & 15) << 47;

  r->v[4] = x[25] >> 4;
  r->v[4] += (uint64_t)x[26] << 4;
  r->v[4] += (uint64_t)x[27] << 12;
  r->v[4] += (uint64_t)x[28] << 20;
  r->v[4] += (uint64_t)x[29] << 28;
  r->v[4] += (uint64_t)x[30] << 36;
  r->v[4] += ((uint64_t)x[31] & 127) << 44;
}

static void fe25519_invert(fe25519 *r, const fe25519 *x) {
  fe25519 z2;
  fe25519 z9;
  fe25519 z11;
  fe25519 z2_5_0;
  fe25519 z2_10_0;
  fe25519 z2_20_0;
  fe25519 z2_50_0;
  fe25519 z2_100_0;
  fe25519 t;
  int i;

  /* 2 */ x25519_x86_64_square(&z2, x);
  /* 4 */ x25519_x86_64_square(&t, &z2);
  /* 8 */ x25519_x86_64_square(&t, &t);
  /* 9 */ x25519_x86_64_mul(&z9, &t, x);
  /* 11 */ x25519_x86_64_mul(&z11, &z9, &z2);
  /* 22 */ x25519_x86_64_square(&t, &z11);
  /* 2^5 - 2^0 = 31 */ x25519_x86_64_mul(&z2_5_0, &t, &z9);

  /* 2^6 - 2^1 */ x25519_x86_64_square(&t, &z2_5_0);
  /* 2^20 - 2^10 */ for (i = 1; i < 5; i++) { x25519_x86_64_square(&t, &t); }
  /* 2^10 - 2^0 */ x25519_x86_64_mul(&z2_10_0, &t, &z2_5_0);

  /* 2^11 - 2^1 */ x25519_x86_64_square(&t, &z2_10_0);
  /* 2^20 - 2^10 */ for (i = 1; i < 10; i++) { x25519_x86_64_square(&t, &t); }
  /* 2^20 - 2^0 */ x25519_x86_64_mul(&z2_20_0, &t, &z2_10_0);

  /* 2^21 - 2^1 */ x25519_x86_64_square(&t, &z2_20_0);
  /* 2^40 - 2^20 */ for (i = 1; i < 20; i++) { x25519_x86_64_square(&t, &t); }
  /* 2^40 - 2^0 */ x25519_x86_64_mul(&t, &t, &z2_20_0);

  /* 2^41 - 2^1 */ x25519_x86_64_square(&t, &t);
  /* 2^50 - 2^10 */ for (i = 1; i < 10; i++) { x25519_x86_64_square(&t, &t); }
  /* 2^50 - 2^0 */ x25519_x86_64_mul(&z2_50_0, &t, &z2_10_0);

  /* 2^51 - 2^1 */ x25519_x86_64_square(&t, &z2_50_0);
  /* 2^100 - 2^50 */ for (i = 1; i < 50; i++) { x25519_x86_64_square(&t, &t); }
  /* 2^100 - 2^0 */ x25519_x86_64_mul(&z2_100_0, &t, &z2_50_0);

  /* 2^101 - 2^1 */ x25519_x86_64_square(&t, &z2_100_0);
  /* 2^200 - 2^100 */ for (i = 1; i < 100; i++) {
    x25519_x86_64_square(&t, &t);
  }
  /* 2^200 - 2^0 */ x25519_x86_64_mul(&t, &t, &z2_100_0);

  /* 2^201 - 2^1 */ x25519_x86_64_square(&t, &t);
  /* 2^250 - 2^50 */ for (i = 1; i < 50; i++) { x25519_x86_64_square(&t, &t); }
  /* 2^250 - 2^0 */ x25519_x86_64_mul(&t, &t, &z2_50_0);

  /* 2^251 - 2^1 */ x25519_x86_64_square(&t, &t);
  /* 2^252 - 2^2 */ x25519_x86_64_square(&t, &t);
  /* 2^253 - 2^3 */ x25519_x86_64_square(&t, &t);

  /* 2^254 - 2^4 */ x25519_x86_64_square(&t, &t);

  /* 2^255 - 2^5 */ x25519_x86_64_square(&t, &t);
  /* 2^255 - 21 */ x25519_x86_64_mul(r, &t, &z11);
}

static void mladder(fe25519 *xr, fe25519 *zr, const uint8_t s[32]) {
  fe25519 work[5];

  work[0] = *xr;
  fe25519_setint(work + 1, 1);
  fe25519_setint(work + 2, 0);
  work[3] = *xr;
  fe25519_setint(work + 4, 1);

  int i, j;
  uint8_t prevbit = 0;

  j = 6;
  for (i = 31; i >= 0; i--) {
    while (j >= 0) {
      const uint8_t bit = 1 & (s[i] >> j);
      const uint64_t swap = bit ^ prevbit;
      prevbit = bit;
      x25519_x86_64_work_cswap(work + 1, swap);
      x25519_x86_64_ladderstep(work);
      j -= 1;
    }
    j = 7;
  }

  *xr = work[1];
  *zr = work[2];
}

void x25519_x86_64(uint8_t out[32], const uint8_t scalar[32],
                  const uint8_t point[32]) {
  uint8_t e[32];
  OPENSSL_memcpy(e, scalar, sizeof(e));

  e[0] &= 248;
  e[31] &= 127;
  e[31] |= 64;

  fe25519 t;
  fe25519 z;
  fe25519_unpack(&t, point);
  mladder(&t, &z, e);
  fe25519_invert(&z, &z);
  x25519_x86_64_mul(&t, &t, &z);
  fe25519_pack(out, &t);
}

#endif  // BORINGSSL_X25519_X86_64
