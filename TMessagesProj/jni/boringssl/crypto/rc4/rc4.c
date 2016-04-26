/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
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
 * [including the GNU Public Licence.] */

#include <openssl/rc4.h>

#if defined(OPENSSL_NO_ASM) || \
    (!defined(OPENSSL_X86_64) && !defined(OPENSSL_X86))

#if defined(OPENSSL_64_BIT)
#define RC4_CHUNK uint64_t
#elif defined(OPENSSL_32_BIT)
#define RC4_CHUNK uint32_t
#else
#error "Unknown word size"
#endif


/* RC4 as implemented from a posting from
 * Newsgroups: sci.crypt
 * From: sterndark@netcom.com (David Sterndark)
 * Subject: RC4 Algorithm revealed.
 * Message-ID: <sternCvKL4B.Hyy@netcom.com>
 * Date: Wed, 14 Sep 1994 06:35:31 GMT */

void RC4(RC4_KEY *key, size_t len, const uint8_t *in, uint8_t *out) {
  uint32_t *d;
  uint32_t x, y, tx, ty;
  size_t i;

  x = key->x;
  y = key->y;
  d = key->data;

#define RC4_STEP                                                             \
  (x = (x + 1) & 0xff, tx = d[x], y = (tx + y) & 0xff, ty = d[y], d[y] = tx, \
   d[x] = ty, (RC4_CHUNK)d[(tx + ty) & 0xff])

  if ((((size_t)in & (sizeof(RC4_CHUNK) - 1)) |
       ((size_t)out & (sizeof(RC4_CHUNK) - 1))) == 0) {
    RC4_CHUNK ichunk, otp;
    const union {
      long one;
      char little;
    } is_endian = {1};

    /* I reckon we can afford to implement both endian
     * cases and to decide which way to take at run-time
     * because the machine code appears to be very compact
     * and redundant 1-2KB is perfectly tolerable (i.e.
     * in case the compiler fails to eliminate it:-). By
     * suggestion from Terrel Larson <terr@terralogic.net>
     * who also stands for the is_endian union:-)
     *
     * Special notes.
     *
     * - is_endian is declared automatic as doing otherwise
     *   (declaring static) prevents gcc from eliminating
     *   the redundant code;
     * - compilers (those I've tried) don't seem to have
     *   problems eliminating either the operators guarded
     *   by "if (sizeof(RC4_CHUNK)==8)" or the condition
     *   expressions themselves so I've got 'em to replace
     *   corresponding #ifdefs from the previous version;
     * - I chose to let the redundant switch cases when
     *   sizeof(RC4_CHUNK)!=8 be (were also #ifdefed
     *   before);
     * - in case you wonder "&(sizeof(RC4_CHUNK)*8-1)" in
     *   [LB]ESHFT guards against "shift is out of range"
     *   warnings when sizeof(RC4_CHUNK)!=8
     *
     *			<appro@fy.chalmers.se> */
    if (!is_endian.little) { /* BIG-ENDIAN CASE */
#define BESHFT(c) \
  (((sizeof(RC4_CHUNK) - (c) - 1) * 8) & (sizeof(RC4_CHUNK) * 8 - 1))
      for (; len & (0 - sizeof(RC4_CHUNK)); len -= sizeof(RC4_CHUNK)) {
        ichunk = *(RC4_CHUNK *)in;
        otp = RC4_STEP << BESHFT(0);
        otp |= RC4_STEP << BESHFT(1);
        otp |= RC4_STEP << BESHFT(2);
        otp |= RC4_STEP << BESHFT(3);
#if defined(OPENSSL_64_BIT)
        otp |= RC4_STEP << BESHFT(4);
        otp |= RC4_STEP << BESHFT(5);
        otp |= RC4_STEP << BESHFT(6);
        otp |= RC4_STEP << BESHFT(7);
#endif
        *(RC4_CHUNK *)out = otp ^ ichunk;
        in += sizeof(RC4_CHUNK);
        out += sizeof(RC4_CHUNK);
      }
    } else { /* LITTLE-ENDIAN CASE */
#define LESHFT(c) (((c) * 8) & (sizeof(RC4_CHUNK) * 8 - 1))
      for (; len & (0 - sizeof(RC4_CHUNK)); len -= sizeof(RC4_CHUNK)) {
        ichunk = *(RC4_CHUNK *)in;
        otp = RC4_STEP;
        otp |= RC4_STEP << 8;
        otp |= RC4_STEP << 16;
        otp |= RC4_STEP << 24;
#if defined(OPENSSL_64_BIT)
        otp |= RC4_STEP << LESHFT(4);
        otp |= RC4_STEP << LESHFT(5);
        otp |= RC4_STEP << LESHFT(6);
        otp |= RC4_STEP << LESHFT(7);
#endif
        *(RC4_CHUNK *)out = otp ^ ichunk;
        in += sizeof(RC4_CHUNK);
        out += sizeof(RC4_CHUNK);
      }
    }
  }
#define LOOP(in, out)   \
  x = ((x + 1) & 0xff); \
  tx = d[x];            \
  y = (tx + y) & 0xff;  \
  d[x] = ty = d[y];     \
  d[y] = tx;            \
  (out) = d[(tx + ty) & 0xff] ^ (in);

#ifndef RC4_INDEX
#define RC4_LOOP(a, b, i) LOOP(*((a)++), *((b)++))
#else
#define RC4_LOOP(a, b, i) LOOP(a[i], b[i])
#endif

  i = len >> 3;
  if (i) {
    for (;;) {
      RC4_LOOP(in, out, 0);
      RC4_LOOP(in, out, 1);
      RC4_LOOP(in, out, 2);
      RC4_LOOP(in, out, 3);
      RC4_LOOP(in, out, 4);
      RC4_LOOP(in, out, 5);
      RC4_LOOP(in, out, 6);
      RC4_LOOP(in, out, 7);
#ifdef RC4_INDEX
      in += 8;
      out += 8;
#endif
      if (--i == 0) {
        break;
      }
    }
  }
  i = len & 0x07;
  if (i) {
    for (;;) {
      RC4_LOOP(in, out, 0);
      if (--i == 0) {
        break;
      }
      RC4_LOOP(in, out, 1);
      if (--i == 0) {
        break;
      }
      RC4_LOOP(in, out, 2);
      if (--i == 0) {
        break;
      }
      RC4_LOOP(in, out, 3);
      if (--i == 0) {
        break;
      }
      RC4_LOOP(in, out, 4);
      if (--i == 0) {
        break;
      }
      RC4_LOOP(in, out, 5);
      if (--i == 0) {
        break;
      }
      RC4_LOOP(in, out, 6);
      if (--i == 0) {
        break;
      }
    }
  }
  key->x = x;
  key->y = y;
}

void RC4_set_key(RC4_KEY *rc4key, unsigned len, const uint8_t *key) {
  uint32_t tmp;
  int id1, id2;
  uint32_t *d;
  unsigned int i;

  d = &rc4key->data[0];
  rc4key->x = 0;
  rc4key->y = 0;
  id1 = id2 = 0;

#define SK_LOOP(d, n)                     \
  {                                       \
    tmp = d[(n)];                         \
    id2 = (key[id1] + tmp + id2) & 0xff; \
    if (++id1 == len)                     \
      id1 = 0;                            \
    d[(n)] = d[id2];                      \
    d[id2] = tmp;                         \
  }

  for (i = 0; i < 256; i++) {
    d[i] = i;
  }
  for (i = 0; i < 256; i += 4) {
    SK_LOOP(d, i + 0);
    SK_LOOP(d, i + 1);
    SK_LOOP(d, i + 2);
    SK_LOOP(d, i + 3);
  }
}

#else

/* In this case several functions are provided by asm code. However, one cannot
 * control asm symbol visibility with command line flags and such so they are
 * always hidden and wrapped by these C functions, which can be so
 * controlled. */

void asm_RC4(RC4_KEY *key, size_t len, const uint8_t *in, uint8_t *out);
void RC4(RC4_KEY *key, size_t len, const uint8_t *in, uint8_t *out) {
  asm_RC4(key, len, in, out);
}

void asm_RC4_set_key(RC4_KEY *rc4key, unsigned len, const uint8_t *key);
void RC4_set_key(RC4_KEY *rc4key, unsigned len, const uint8_t *key) {
  asm_RC4_set_key(rc4key, len, key);
}

#endif  /* OPENSSL_NO_ASM || (!OPENSSL_X86_64 && !OPENSSL_X86) */
