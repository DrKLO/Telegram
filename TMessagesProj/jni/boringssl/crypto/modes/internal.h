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

#ifndef OPENSSL_HEADER_MODES_INTERNAL_H
#define OPENSSL_HEADER_MODES_INTERNAL_H

#include <openssl/base.h>

#if defined(__cplusplus)
extern "C" {
#endif


#define asm __asm__

#define STRICT_ALIGNMENT 1
#if defined(OPENSSL_X86_64) || defined(OPENSSL_X86) || defined(OPENSSL_AARCH64)
#undef STRICT_ALIGNMENT
#define STRICT_ALIGNMENT 0
#endif

#if !defined(PEDANTIC) && !defined(OPENSSL_NO_ASM)
#if defined(__GNUC__) && __GNUC__ >= 2
#if defined(OPENSSL_X86_64)
#define BSWAP8(x)                 \
  ({                              \
    uint64_t ret = (x);           \
    asm("bswapq %0" : "+r"(ret)); \
    ret;                          \
  })
#define BSWAP4(x)                 \
  ({                              \
    uint32_t ret = (x);           \
    asm("bswapl %0" : "+r"(ret)); \
    ret;                          \
  })
#elif defined(OPENSSL_X86)
#define BSWAP8(x)                                     \
  ({                                                  \
    uint32_t lo = (uint64_t)(x) >> 32, hi = (x);      \
    asm("bswapl %0; bswapl %1" : "+r"(hi), "+r"(lo)); \
    (uint64_t) hi << 32 | lo;                         \
  })
#define BSWAP4(x)                 \
  ({                              \
    uint32_t ret = (x);           \
    asm("bswapl %0" : "+r"(ret)); \
    ret;                          \
  })
#elif defined(OPENSSL_AARCH64)
#define BSWAP8(x)                          \
  ({                                       \
    uint64_t ret;                          \
    asm("rev %0,%1" : "=r"(ret) : "r"(x)); \
    ret;                                   \
  })
#define BSWAP4(x)                            \
  ({                                         \
    uint32_t ret;                            \
    asm("rev %w0,%w1" : "=r"(ret) : "r"(x)); \
    ret;                                     \
  })
#elif defined(OPENSSL_ARM) && !defined(STRICT_ALIGNMENT)
#define BSWAP8(x)                                     \
  ({                                                  \
    uint32_t lo = (uint64_t)(x) >> 32, hi = (x);      \
    asm("rev %0,%0; rev %1,%1" : "+r"(hi), "+r"(lo)); \
    (uint64_t) hi << 32 | lo;                         \
  })
#define BSWAP4(x)                                      \
  ({                                                   \
    uint32_t ret;                                      \
    asm("rev %0,%1" : "=r"(ret) : "r"((uint32_t)(x))); \
    ret;                                               \
  })
#endif
#elif defined(_MSC_VER)
#if _MSC_VER >= 1300
#pragma warning(push, 3)
#include <intrin.h>
#pragma warning(pop)
#pragma intrinsic(_byteswap_uint64, _byteswap_ulong)
#define BSWAP8(x) _byteswap_uint64((uint64_t)(x))
#define BSWAP4(x) _byteswap_ulong((uint32_t)(x))
#elif defined(OPENSSL_X86)
__inline uint32_t _bswap4(uint32_t val) {
  _asm mov eax, val
  _asm bswap eax
}
#define BSWAP4(x) _bswap4(x)
#endif
#endif
#endif

#if defined(BSWAP4) && !defined(STRICT_ALIGNMENT)
#define GETU32(p) BSWAP4(*(const uint32_t *)(p))
#define PUTU32(p, v) *(uint32_t *)(p) = BSWAP4(v)
#else
#define GETU32(p) \
  ((uint32_t)(p)[0] << 24 | (uint32_t)(p)[1] << 16 | (uint32_t)(p)[2] << 8 | (uint32_t)(p)[3])
#define PUTU32(p, v)                                   \
  ((p)[0] = (uint8_t)((v) >> 24), (p)[1] = (uint8_t)((v) >> 16), \
   (p)[2] = (uint8_t)((v) >> 8), (p)[3] = (uint8_t)(v))
#endif


/* GCM definitions */
typedef struct { uint64_t hi,lo; } u128;

struct gcm128_context {
  /* Following 6 names follow names in GCM specification */
  union {
    uint64_t u[2];
    uint32_t d[4];
    uint8_t c[16];
    size_t t[16 / sizeof(size_t)];
  } Yi, EKi, EK0, len, Xi, H;

  /* Relative position of Xi, H and pre-computed Htable is used in some
   * assembler modules, i.e. don't change the order! */
  u128 Htable[16];
  void (*gmult)(uint64_t Xi[2], const u128 Htable[16]);
  void (*ghash)(uint64_t Xi[2], const u128 Htable[16], const uint8_t *inp,
                size_t len);

  unsigned int mres, ares;
  block128_f block;
  void *key;
};

struct xts128_context {
  void *key1, *key2;
  block128_f block1, block2;
};

struct ccm128_context {
  union {
    uint64_t u[2];
    uint8_t c[16];
  } nonce, cmac;
  uint64_t blocks;
  block128_f block;
  void *key;
};

#if defined(OPENSSL_X86) || defined(OPENSSL_X86_64)
/* crypto_gcm_clmul_enabled returns one if the CLMUL implementation of GCM is
 * used. */
int crypto_gcm_clmul_enabled(void);
#endif


#if defined(__cplusplus)
} /* extern C */
#endif

#endif /* OPENSSL_HEADER_MODES_INTERNAL_H */
