/* ====================================================================
 * Copyright (c) 2008 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ==================================================================== */

#include <openssl/base.h>

#include <assert.h>
#include <string.h>

#include <openssl/mem.h>
#include <openssl/cpu.h>

#include "internal.h"
#include "../../internal.h"


#define PACK(s) ((size_t)(s) << (sizeof(size_t) * 8 - 16))
#define REDUCE1BIT(V)                                                 \
  do {                                                                \
    if (sizeof(size_t) == 8) {                                        \
      uint64_t T = UINT64_C(0xe100000000000000) & (0 - ((V).lo & 1)); \
      (V).lo = ((V).hi << 63) | ((V).lo >> 1);                        \
      (V).hi = ((V).hi >> 1) ^ T;                                     \
    } else {                                                          \
      uint32_t T = 0xe1000000U & (0 - (uint32_t)((V).lo & 1));        \
      (V).lo = ((V).hi << 63) | ((V).lo >> 1);                        \
      (V).hi = ((V).hi >> 1) ^ ((uint64_t)T << 32);                   \
    }                                                                 \
  } while (0)

// kSizeTWithoutLower4Bits is a mask that can be used to zero the lower four
// bits of a |size_t|.
static const size_t kSizeTWithoutLower4Bits = (size_t) -16;

void gcm_init_4bit(u128 Htable[16], const uint64_t H[2]) {
  u128 V;

  Htable[0].hi = 0;
  Htable[0].lo = 0;
  V.hi = H[0];
  V.lo = H[1];

  Htable[8] = V;
  REDUCE1BIT(V);
  Htable[4] = V;
  REDUCE1BIT(V);
  Htable[2] = V;
  REDUCE1BIT(V);
  Htable[1] = V;
  Htable[3].hi = V.hi ^ Htable[2].hi, Htable[3].lo = V.lo ^ Htable[2].lo;
  V = Htable[4];
  Htable[5].hi = V.hi ^ Htable[1].hi, Htable[5].lo = V.lo ^ Htable[1].lo;
  Htable[6].hi = V.hi ^ Htable[2].hi, Htable[6].lo = V.lo ^ Htable[2].lo;
  Htable[7].hi = V.hi ^ Htable[3].hi, Htable[7].lo = V.lo ^ Htable[3].lo;
  V = Htable[8];
  Htable[9].hi = V.hi ^ Htable[1].hi, Htable[9].lo = V.lo ^ Htable[1].lo;
  Htable[10].hi = V.hi ^ Htable[2].hi, Htable[10].lo = V.lo ^ Htable[2].lo;
  Htable[11].hi = V.hi ^ Htable[3].hi, Htable[11].lo = V.lo ^ Htable[3].lo;
  Htable[12].hi = V.hi ^ Htable[4].hi, Htable[12].lo = V.lo ^ Htable[4].lo;
  Htable[13].hi = V.hi ^ Htable[5].hi, Htable[13].lo = V.lo ^ Htable[5].lo;
  Htable[14].hi = V.hi ^ Htable[6].hi, Htable[14].lo = V.lo ^ Htable[6].lo;
  Htable[15].hi = V.hi ^ Htable[7].hi, Htable[15].lo = V.lo ^ Htable[7].lo;

#if defined(GHASH_ASM) && defined(OPENSSL_ARM)
  for (int j = 0; j < 16; ++j) {
    V = Htable[j];
    Htable[j].hi = V.lo;
    Htable[j].lo = V.hi;
  }
#endif
}

#if !defined(GHASH_ASM) || defined(OPENSSL_AARCH64) || defined(OPENSSL_PPC64LE)
static const size_t rem_4bit[16] = {
    PACK(0x0000), PACK(0x1C20), PACK(0x3840), PACK(0x2460),
    PACK(0x7080), PACK(0x6CA0), PACK(0x48C0), PACK(0x54E0),
    PACK(0xE100), PACK(0xFD20), PACK(0xD940), PACK(0xC560),
    PACK(0x9180), PACK(0x8DA0), PACK(0xA9C0), PACK(0xB5E0)};

void gcm_gmult_4bit(uint64_t Xi[2], const u128 Htable[16]) {
  u128 Z;
  int cnt = 15;
  size_t rem, nlo, nhi;

  nlo = ((const uint8_t *)Xi)[15];
  nhi = nlo >> 4;
  nlo &= 0xf;

  Z.hi = Htable[nlo].hi;
  Z.lo = Htable[nlo].lo;

  while (1) {
    rem = (size_t)Z.lo & 0xf;
    Z.lo = (Z.hi << 60) | (Z.lo >> 4);
    Z.hi = (Z.hi >> 4);
    if (sizeof(size_t) == 8) {
      Z.hi ^= rem_4bit[rem];
    } else {
      Z.hi ^= (uint64_t)rem_4bit[rem] << 32;
    }

    Z.hi ^= Htable[nhi].hi;
    Z.lo ^= Htable[nhi].lo;

    if (--cnt < 0) {
      break;
    }

    nlo = ((const uint8_t *)Xi)[cnt];
    nhi = nlo >> 4;
    nlo &= 0xf;

    rem = (size_t)Z.lo & 0xf;
    Z.lo = (Z.hi << 60) | (Z.lo >> 4);
    Z.hi = (Z.hi >> 4);
    if (sizeof(size_t) == 8) {
      Z.hi ^= rem_4bit[rem];
    } else {
      Z.hi ^= (uint64_t)rem_4bit[rem] << 32;
    }

    Z.hi ^= Htable[nlo].hi;
    Z.lo ^= Htable[nlo].lo;
  }

  Xi[0] = CRYPTO_bswap8(Z.hi);
  Xi[1] = CRYPTO_bswap8(Z.lo);
}

// Streamed gcm_mult_4bit, see CRYPTO_gcm128_[en|de]crypt for
// details... Compiler-generated code doesn't seem to give any
// performance improvement, at least not on x86[_64]. It's here
// mostly as reference and a placeholder for possible future
// non-trivial optimization[s]...
void gcm_ghash_4bit(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                    size_t len) {
  u128 Z;
  int cnt;
  size_t rem, nlo, nhi;

  do {
    cnt = 15;
    nlo = ((const uint8_t *)Xi)[15];
    nlo ^= inp[15];
    nhi = nlo >> 4;
    nlo &= 0xf;

    Z.hi = Htable[nlo].hi;
    Z.lo = Htable[nlo].lo;

    while (1) {
      rem = (size_t)Z.lo & 0xf;
      Z.lo = (Z.hi << 60) | (Z.lo >> 4);
      Z.hi = (Z.hi >> 4);
      if (sizeof(size_t) == 8) {
        Z.hi ^= rem_4bit[rem];
      } else {
        Z.hi ^= (uint64_t)rem_4bit[rem] << 32;
      }

      Z.hi ^= Htable[nhi].hi;
      Z.lo ^= Htable[nhi].lo;

      if (--cnt < 0) {
        break;
      }

      nlo = ((const uint8_t *)Xi)[cnt];
      nlo ^= inp[cnt];
      nhi = nlo >> 4;
      nlo &= 0xf;

      rem = (size_t)Z.lo & 0xf;
      Z.lo = (Z.hi << 60) | (Z.lo >> 4);
      Z.hi = (Z.hi >> 4);
      if (sizeof(size_t) == 8) {
        Z.hi ^= rem_4bit[rem];
      } else {
        Z.hi ^= (uint64_t)rem_4bit[rem] << 32;
      }

      Z.hi ^= Htable[nlo].hi;
      Z.lo ^= Htable[nlo].lo;
    }

    Xi[0] = CRYPTO_bswap8(Z.hi);
    Xi[1] = CRYPTO_bswap8(Z.lo);
  } while (inp += 16, len -= 16);
}
#endif   // !GHASH_ASM || AARCH64 || PPC64LE

#define GCM_MUL(ctx, Xi) gcm_gmult_4bit((ctx)->Xi.u, (ctx)->gcm_key.Htable)
#define GHASH(ctx, in, len) \
  gcm_ghash_4bit((ctx)->Xi.u, (ctx)->gcm_key.Htable, in, len)
// GHASH_CHUNK is "stride parameter" missioned to mitigate cache
// trashing effect. In other words idea is to hash data while it's
// still in L1 cache after encryption pass...
#define GHASH_CHUNK (3 * 1024)

#if defined(GHASH_ASM_X86_64) || defined(GHASH_ASM_X86)
void gcm_init_ssse3(u128 Htable[16], const uint64_t Xi[2]) {
  // Run the existing 4-bit version.
  gcm_init_4bit(Htable, Xi);

  // First, swap hi and lo. The "4bit" version places hi first. It treats the
  // two fields separately, so the order does not matter, but ghash-ssse3 reads
  // the entire state into one 128-bit register.
  for (int i = 0; i < 16; i++) {
    uint64_t tmp = Htable[i].hi;
    Htable[i].hi = Htable[i].lo;
    Htable[i].lo = tmp;
  }

  // Treat |Htable| as a 16x16 byte table and transpose it. Thus, Htable[i]
  // contains the i'th byte of j*H for all j.
  uint8_t *Hbytes = (uint8_t *)Htable;
  for (int i = 0; i < 16; i++) {
    for (int j = 0; j < i; j++) {
      uint8_t tmp = Hbytes[16*i + j];
      Hbytes[16*i + j] = Hbytes[16*j + i];
      Hbytes[16*j + i] = tmp;
    }
  }
}
#endif  // GHASH_ASM_X86_64 || GHASH_ASM_X86

#ifdef GCM_FUNCREF_4BIT
#undef GCM_MUL
#define GCM_MUL(ctx, Xi) (*gcm_gmult_p)((ctx)->Xi.u, (ctx)->gcm_key.Htable)
#undef GHASH
#define GHASH(ctx, in, len) \
  (*gcm_ghash_p)((ctx)->Xi.u, (ctx)->gcm_key.Htable, in, len)
#endif  // GCM_FUNCREF_4BIT

void CRYPTO_ghash_init(gmult_func *out_mult, ghash_func *out_hash,
                       u128 *out_key, u128 out_table[16], int *out_is_avx,
                       const uint8_t gcm_key[16]) {
  *out_is_avx = 0;

  union {
    uint64_t u[2];
    uint8_t c[16];
  } H;

  OPENSSL_memcpy(H.c, gcm_key, 16);

  // H is stored in host byte order
  H.u[0] = CRYPTO_bswap8(H.u[0]);
  H.u[1] = CRYPTO_bswap8(H.u[1]);

  OPENSSL_memcpy(out_key, H.c, 16);

#if defined(GHASH_ASM_X86_64)
  if (crypto_gcm_clmul_enabled()) {
    if (((OPENSSL_ia32cap_get()[1] >> 22) & 0x41) == 0x41) {  // AVX+MOVBE
      gcm_init_avx(out_table, H.u);
      *out_mult = gcm_gmult_avx;
      *out_hash = gcm_ghash_avx;
      *out_is_avx = 1;
      return;
    }
    gcm_init_clmul(out_table, H.u);
    *out_mult = gcm_gmult_clmul;
    *out_hash = gcm_ghash_clmul;
    return;
  }
  if (gcm_ssse3_capable()) {
    gcm_init_ssse3(out_table, H.u);
    *out_mult = gcm_gmult_ssse3;
    *out_hash = gcm_ghash_ssse3;
    return;
  }
#elif defined(GHASH_ASM_X86)
  if (crypto_gcm_clmul_enabled()) {
    gcm_init_clmul(out_table, H.u);
    *out_mult = gcm_gmult_clmul;
    *out_hash = gcm_ghash_clmul;
    return;
  }
  if (gcm_ssse3_capable()) {
    gcm_init_ssse3(out_table, H.u);
    *out_mult = gcm_gmult_ssse3;
    *out_hash = gcm_ghash_ssse3;
    return;
  }
#elif defined(GHASH_ASM_ARM)
  if (gcm_pmull_capable()) {
    gcm_init_v8(out_table, H.u);
    *out_mult = gcm_gmult_v8;
    *out_hash = gcm_ghash_v8;
    return;
  }

  if (gcm_neon_capable()) {
    gcm_init_neon(out_table, H.u);
    *out_mult = gcm_gmult_neon;
    *out_hash = gcm_ghash_neon;
    return;
  }
#elif defined(GHASH_ASM_PPC64LE)
  if (CRYPTO_is_PPC64LE_vcrypto_capable()) {
    gcm_init_p8(out_table, H.u);
    *out_mult = gcm_gmult_p8;
    *out_hash = gcm_ghash_p8;
    return;
  }
#endif

  gcm_init_4bit(out_table, H.u);
#if defined(GHASH_ASM_X86)
  *out_mult = gcm_gmult_4bit_mmx;
  *out_hash = gcm_ghash_4bit_mmx;
#else
  *out_mult = gcm_gmult_4bit;
  *out_hash = gcm_ghash_4bit;
#endif
}

void CRYPTO_gcm128_init_key(GCM128_KEY *gcm_key, const AES_KEY *aes_key,
                            block128_f block, int block_is_hwaes) {
  OPENSSL_memset(gcm_key, 0, sizeof(*gcm_key));
  gcm_key->block = block;

  uint8_t ghash_key[16];
  OPENSSL_memset(ghash_key, 0, sizeof(ghash_key));
  (*block)(ghash_key, ghash_key, aes_key);

  int is_avx;
  CRYPTO_ghash_init(&gcm_key->gmult, &gcm_key->ghash, &gcm_key->H,
                    gcm_key->Htable, &is_avx, ghash_key);

  gcm_key->use_aesni_gcm_crypt = (is_avx && block_is_hwaes) ? 1 : 0;
}

void CRYPTO_gcm128_setiv(GCM128_CONTEXT *ctx, const AES_KEY *key,
                         const uint8_t *iv, size_t len) {
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
#endif

  ctx->Yi.u[0] = 0;
  ctx->Yi.u[1] = 0;
  ctx->Xi.u[0] = 0;
  ctx->Xi.u[1] = 0;
  ctx->len.u[0] = 0;  // AAD length
  ctx->len.u[1] = 0;  // message length
  ctx->ares = 0;
  ctx->mres = 0;

  uint32_t ctr;
  if (len == 12) {
    OPENSSL_memcpy(ctx->Yi.c, iv, 12);
    ctx->Yi.c[15] = 1;
    ctr = 1;
  } else {
    uint64_t len0 = len;

    while (len >= 16) {
      for (size_t i = 0; i < 16; ++i) {
        ctx->Yi.c[i] ^= iv[i];
      }
      GCM_MUL(ctx, Yi);
      iv += 16;
      len -= 16;
    }
    if (len) {
      for (size_t i = 0; i < len; ++i) {
        ctx->Yi.c[i] ^= iv[i];
      }
      GCM_MUL(ctx, Yi);
    }
    len0 <<= 3;
    ctx->Yi.u[1] ^= CRYPTO_bswap8(len0);

    GCM_MUL(ctx, Yi);
    ctr = CRYPTO_bswap4(ctx->Yi.d[3]);
  }

  (*ctx->gcm_key.block)(ctx->Yi.c, ctx->EK0.c, key);
  ++ctr;
  ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
}

int CRYPTO_gcm128_aad(GCM128_CONTEXT *ctx, const uint8_t *aad, size_t len) {
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
#ifdef GHASH
  void (*gcm_ghash_p)(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                      size_t len) = ctx->gcm_key.ghash;
#endif
#endif

  if (ctx->len.u[1]) {
    return 0;
  }

  uint64_t alen = ctx->len.u[0] + len;
  if (alen > (UINT64_C(1) << 61) || (sizeof(len) == 8 && alen < len)) {
    return 0;
  }
  ctx->len.u[0] = alen;

  unsigned n = ctx->ares;
  if (n) {
    while (n && len) {
      ctx->Xi.c[n] ^= *(aad++);
      --len;
      n = (n + 1) % 16;
    }
    if (n == 0) {
      GCM_MUL(ctx, Xi);
    } else {
      ctx->ares = n;
      return 1;
    }
  }

  // Process a whole number of blocks.
  size_t len_blocks = len & kSizeTWithoutLower4Bits;
  if (len_blocks != 0) {
    GHASH(ctx, aad, len_blocks);
    aad += len_blocks;
    len -= len_blocks;
  }

  // Process the remainder.
  if (len != 0) {
    n = (unsigned int)len;
    for (size_t i = 0; i < len; ++i) {
      ctx->Xi.c[i] ^= aad[i];
    }
  }

  ctx->ares = n;
  return 1;
}

int CRYPTO_gcm128_encrypt(GCM128_CONTEXT *ctx, const AES_KEY *key,
                          const uint8_t *in, uint8_t *out, size_t len) {
  block128_f block = ctx->gcm_key.block;
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
  void (*gcm_ghash_p)(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                      size_t len) = ctx->gcm_key.ghash;
#endif

  uint64_t mlen = ctx->len.u[1] + len;
  if (mlen > ((UINT64_C(1) << 36) - 32) ||
      (sizeof(len) == 8 && mlen < len)) {
    return 0;
  }
  ctx->len.u[1] = mlen;

  if (ctx->ares) {
    // First call to encrypt finalizes GHASH(AAD)
    GCM_MUL(ctx, Xi);
    ctx->ares = 0;
  }

  unsigned n = ctx->mres;
  if (n) {
    while (n && len) {
      ctx->Xi.c[n] ^= *(out++) = *(in++) ^ ctx->EKi.c[n];
      --len;
      n = (n + 1) % 16;
    }
    if (n == 0) {
      GCM_MUL(ctx, Xi);
    } else {
      ctx->mres = n;
      return 1;
    }
  }

  uint32_t ctr = CRYPTO_bswap4(ctx->Yi.d[3]);
  while (len >= GHASH_CHUNK) {
    size_t j = GHASH_CHUNK;

    while (j) {
      (*block)(ctx->Yi.c, ctx->EKi.c, key);
      ++ctr;
      ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
      for (size_t i = 0; i < 16; i += sizeof(size_t)) {
        store_word_le(out + i,
                      load_word_le(in + i) ^ ctx->EKi.t[i / sizeof(size_t)]);
      }
      out += 16;
      in += 16;
      j -= 16;
    }
    GHASH(ctx, out - GHASH_CHUNK, GHASH_CHUNK);
    len -= GHASH_CHUNK;
  }
  size_t len_blocks = len & kSizeTWithoutLower4Bits;
  if (len_blocks != 0) {
    while (len >= 16) {
      (*block)(ctx->Yi.c, ctx->EKi.c, key);
      ++ctr;
      ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
      for (size_t i = 0; i < 16; i += sizeof(size_t)) {
        store_word_le(out + i,
                      load_word_le(in + i) ^ ctx->EKi.t[i / sizeof(size_t)]);
      }
      out += 16;
      in += 16;
      len -= 16;
    }
    GHASH(ctx, out - len_blocks, len_blocks);
  }
  if (len) {
    (*block)(ctx->Yi.c, ctx->EKi.c, key);
    ++ctr;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    while (len--) {
      ctx->Xi.c[n] ^= out[n] = in[n] ^ ctx->EKi.c[n];
      ++n;
    }
  }

  ctx->mres = n;
  return 1;
}

int CRYPTO_gcm128_decrypt(GCM128_CONTEXT *ctx, const AES_KEY *key,
                          const unsigned char *in, unsigned char *out,
                          size_t len) {
  block128_f block = ctx->gcm_key.block;
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
  void (*gcm_ghash_p)(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                      size_t len) = ctx->gcm_key.ghash;
#endif

  uint64_t mlen = ctx->len.u[1] + len;
  if (mlen > ((UINT64_C(1) << 36) - 32) ||
      (sizeof(len) == 8 && mlen < len)) {
    return 0;
  }
  ctx->len.u[1] = mlen;

  if (ctx->ares) {
    // First call to decrypt finalizes GHASH(AAD)
    GCM_MUL(ctx, Xi);
    ctx->ares = 0;
  }

  unsigned n = ctx->mres;
  if (n) {
    while (n && len) {
      uint8_t c = *(in++);
      *(out++) = c ^ ctx->EKi.c[n];
      ctx->Xi.c[n] ^= c;
      --len;
      n = (n + 1) % 16;
    }
    if (n == 0) {
      GCM_MUL(ctx, Xi);
    } else {
      ctx->mres = n;
      return 1;
    }
  }

  uint32_t ctr = CRYPTO_bswap4(ctx->Yi.d[3]);
  while (len >= GHASH_CHUNK) {
    size_t j = GHASH_CHUNK;

    GHASH(ctx, in, GHASH_CHUNK);
    while (j) {
      (*block)(ctx->Yi.c, ctx->EKi.c, key);
      ++ctr;
      ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
      for (size_t i = 0; i < 16; i += sizeof(size_t)) {
        store_word_le(out + i,
                      load_word_le(in + i) ^ ctx->EKi.t[i / sizeof(size_t)]);
      }
      out += 16;
      in += 16;
      j -= 16;
    }
    len -= GHASH_CHUNK;
  }
  size_t len_blocks = len & kSizeTWithoutLower4Bits;
  if (len_blocks != 0) {
    GHASH(ctx, in, len_blocks);
    while (len >= 16) {
      (*block)(ctx->Yi.c, ctx->EKi.c, key);
      ++ctr;
      ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
      for (size_t i = 0; i < 16; i += sizeof(size_t)) {
        store_word_le(out + i,
                      load_word_le(in + i) ^ ctx->EKi.t[i / sizeof(size_t)]);
      }
      out += 16;
      in += 16;
      len -= 16;
    }
  }
  if (len) {
    (*block)(ctx->Yi.c, ctx->EKi.c, key);
    ++ctr;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    while (len--) {
      uint8_t c = in[n];
      ctx->Xi.c[n] ^= c;
      out[n] = c ^ ctx->EKi.c[n];
      ++n;
    }
  }

  ctx->mres = n;
  return 1;
}

int CRYPTO_gcm128_encrypt_ctr32(GCM128_CONTEXT *ctx, const AES_KEY *key,
                                const uint8_t *in, uint8_t *out, size_t len,
                                ctr128_f stream) {
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
  void (*gcm_ghash_p)(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                      size_t len) = ctx->gcm_key.ghash;
#endif

  uint64_t mlen = ctx->len.u[1] + len;
  if (mlen > ((UINT64_C(1) << 36) - 32) ||
      (sizeof(len) == 8 && mlen < len)) {
    return 0;
  }
  ctx->len.u[1] = mlen;

  if (ctx->ares) {
    // First call to encrypt finalizes GHASH(AAD)
    GCM_MUL(ctx, Xi);
    ctx->ares = 0;
  }

  unsigned n = ctx->mres;
  if (n) {
    while (n && len) {
      ctx->Xi.c[n] ^= *(out++) = *(in++) ^ ctx->EKi.c[n];
      --len;
      n = (n + 1) % 16;
    }
    if (n == 0) {
      GCM_MUL(ctx, Xi);
    } else {
      ctx->mres = n;
      return 1;
    }
  }

#if defined(AESNI_GCM)
  if (ctx->gcm_key.use_aesni_gcm_crypt) {
    // |aesni_gcm_encrypt| may not process all the input given to it. It may
    // not process *any* of its input if it is deemed too small.
    size_t bulk = aesni_gcm_encrypt(in, out, len, key, ctx->Yi.c, ctx->Xi.u);
    in += bulk;
    out += bulk;
    len -= bulk;
  }
#endif

  uint32_t ctr = CRYPTO_bswap4(ctx->Yi.d[3]);
  while (len >= GHASH_CHUNK) {
    (*stream)(in, out, GHASH_CHUNK / 16, key, ctx->Yi.c);
    ctr += GHASH_CHUNK / 16;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    GHASH(ctx, out, GHASH_CHUNK);
    out += GHASH_CHUNK;
    in += GHASH_CHUNK;
    len -= GHASH_CHUNK;
  }
  size_t len_blocks = len & kSizeTWithoutLower4Bits;
  if (len_blocks != 0) {
    size_t j = len_blocks / 16;

    (*stream)(in, out, j, key, ctx->Yi.c);
    ctr += (unsigned int)j;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    in += len_blocks;
    len -= len_blocks;
    GHASH(ctx, out, len_blocks);
    out += len_blocks;
  }
  if (len) {
    (*ctx->gcm_key.block)(ctx->Yi.c, ctx->EKi.c, key);
    ++ctr;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    while (len--) {
      ctx->Xi.c[n] ^= out[n] = in[n] ^ ctx->EKi.c[n];
      ++n;
    }
  }

  ctx->mres = n;
  return 1;
}

int CRYPTO_gcm128_decrypt_ctr32(GCM128_CONTEXT *ctx, const AES_KEY *key,
                                const uint8_t *in, uint8_t *out, size_t len,
                                ctr128_f stream) {
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
  void (*gcm_ghash_p)(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                      size_t len) = ctx->gcm_key.ghash;
#endif

  uint64_t mlen = ctx->len.u[1] + len;
  if (mlen > ((UINT64_C(1) << 36) - 32) ||
      (sizeof(len) == 8 && mlen < len)) {
    return 0;
  }
  ctx->len.u[1] = mlen;

  if (ctx->ares) {
    // First call to decrypt finalizes GHASH(AAD)
    GCM_MUL(ctx, Xi);
    ctx->ares = 0;
  }

  unsigned n = ctx->mres;
  if (n) {
    while (n && len) {
      uint8_t c = *(in++);
      *(out++) = c ^ ctx->EKi.c[n];
      ctx->Xi.c[n] ^= c;
      --len;
      n = (n + 1) % 16;
    }
    if (n == 0) {
      GCM_MUL(ctx, Xi);
    } else {
      ctx->mres = n;
      return 1;
    }
  }

#if defined(AESNI_GCM)
  if (ctx->gcm_key.use_aesni_gcm_crypt) {
    // |aesni_gcm_decrypt| may not process all the input given to it. It may
    // not process *any* of its input if it is deemed too small.
    size_t bulk = aesni_gcm_decrypt(in, out, len, key, ctx->Yi.c, ctx->Xi.u);
    in += bulk;
    out += bulk;
    len -= bulk;
  }
#endif

  uint32_t ctr = CRYPTO_bswap4(ctx->Yi.d[3]);
  while (len >= GHASH_CHUNK) {
    GHASH(ctx, in, GHASH_CHUNK);
    (*stream)(in, out, GHASH_CHUNK / 16, key, ctx->Yi.c);
    ctr += GHASH_CHUNK / 16;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    out += GHASH_CHUNK;
    in += GHASH_CHUNK;
    len -= GHASH_CHUNK;
  }
  size_t len_blocks = len & kSizeTWithoutLower4Bits;
  if (len_blocks != 0) {
    size_t j = len_blocks / 16;

    GHASH(ctx, in, len_blocks);
    (*stream)(in, out, j, key, ctx->Yi.c);
    ctr += (unsigned int)j;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    out += len_blocks;
    in += len_blocks;
    len -= len_blocks;
  }
  if (len) {
    (*ctx->gcm_key.block)(ctx->Yi.c, ctx->EKi.c, key);
    ++ctr;
    ctx->Yi.d[3] = CRYPTO_bswap4(ctr);
    while (len--) {
      uint8_t c = in[n];
      ctx->Xi.c[n] ^= c;
      out[n] = c ^ ctx->EKi.c[n];
      ++n;
    }
  }

  ctx->mres = n;
  return 1;
}

int CRYPTO_gcm128_finish(GCM128_CONTEXT *ctx, const uint8_t *tag, size_t len) {
#ifdef GCM_FUNCREF_4BIT
  void (*gcm_gmult_p)(uint64_t Xi[2], const u128 Htable[16]) =
      ctx->gcm_key.gmult;
#endif

  if (ctx->mres || ctx->ares) {
    GCM_MUL(ctx, Xi);
  }

  ctx->Xi.u[0] ^= CRYPTO_bswap8(ctx->len.u[0] << 3);
  ctx->Xi.u[1] ^= CRYPTO_bswap8(ctx->len.u[1] << 3);
  GCM_MUL(ctx, Xi);

  ctx->Xi.u[0] ^= ctx->EK0.u[0];
  ctx->Xi.u[1] ^= ctx->EK0.u[1];

  if (tag && len <= sizeof(ctx->Xi)) {
    return CRYPTO_memcmp(ctx->Xi.c, tag, len) == 0;
  } else {
    return 0;
  }
}

void CRYPTO_gcm128_tag(GCM128_CONTEXT *ctx, unsigned char *tag, size_t len) {
  CRYPTO_gcm128_finish(ctx, NULL, 0);
  OPENSSL_memcpy(tag, ctx->Xi.c,
                 len <= sizeof(ctx->Xi.c) ? len : sizeof(ctx->Xi.c));
}

#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64)
int crypto_gcm_clmul_enabled(void) {
#ifdef GHASH_ASM
  const uint32_t *ia32cap = OPENSSL_ia32cap_get();
  return (ia32cap[0] & (1 << 24)) &&  // check FXSR bit
         (ia32cap[1] & (1 << 1));     // check PCLMULQDQ bit
#else
  return 0;
#endif
}
#endif
