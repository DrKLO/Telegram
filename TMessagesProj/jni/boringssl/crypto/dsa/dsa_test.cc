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
 * [including the GNU Public Licence.]
 *
 * The DSS routines are based on patches supplied by
 * Steven Schoch <schoch@sheba.arc.nasa.gov>. */

#include <openssl/dsa.h>

#include <stdio.h>
#include <string.h>

#include <vector>

#include <gtest/gtest.h>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/err.h>

#include "../internal.h"


// The following values are taken from the updated Appendix 5 to FIPS PUB 186
// and also appear in Appendix 5 to FIPS PUB 186-1.

static const uint8_t seed[20] = {
    0xd5, 0x01, 0x4e, 0x4b, 0x60, 0xef, 0x2b, 0xa8, 0xb6, 0x21, 0x1b,
    0x40, 0x62, 0xba, 0x32, 0x24, 0xe0, 0x42, 0x7d, 0xd3,
};

static const uint8_t fips_p[] = {
    0x8d, 0xf2, 0xa4, 0x94, 0x49, 0x22, 0x76, 0xaa, 0x3d, 0x25, 0x75,
    0x9b, 0xb0, 0x68, 0x69, 0xcb, 0xea, 0xc0, 0xd8, 0x3a, 0xfb, 0x8d,
    0x0c, 0xf7, 0xcb, 0xb8, 0x32, 0x4f, 0x0d, 0x78, 0x82, 0xe5, 0xd0,
    0x76, 0x2f, 0xc5, 0xb7, 0x21, 0x0e, 0xaf, 0xc2, 0xe9, 0xad, 0xac,
    0x32, 0xab, 0x7a, 0xac, 0x49, 0x69, 0x3d, 0xfb, 0xf8, 0x37, 0x24,
    0xc2, 0xec, 0x07, 0x36, 0xee, 0x31, 0xc8, 0x02, 0x91,
};

static const uint8_t fips_q[] = {
    0xc7, 0x73, 0x21, 0x8c, 0x73, 0x7e, 0xc8, 0xee, 0x99, 0x3b, 0x4f,
    0x2d, 0xed, 0x30, 0xf4, 0x8e, 0xda, 0xce, 0x91, 0x5f,
};

static const uint8_t fips_g[] = {
    0x62, 0x6d, 0x02, 0x78, 0x39, 0xea, 0x0a, 0x13, 0x41, 0x31, 0x63,
    0xa5, 0x5b, 0x4c, 0xb5, 0x00, 0x29, 0x9d, 0x55, 0x22, 0x95, 0x6c,
    0xef, 0xcb, 0x3b, 0xff, 0x10, 0xf3, 0x99, 0xce, 0x2c, 0x2e, 0x71,
    0xcb, 0x9d, 0xe5, 0xfa, 0x24, 0xba, 0xbf, 0x58, 0xe5, 0xb7, 0x95,
    0x21, 0x92, 0x5c, 0x9c, 0xc4, 0x2e, 0x9f, 0x6f, 0x46, 0x4b, 0x08,
    0x8c, 0xc5, 0x72, 0xaf, 0x53, 0xe6, 0xd7, 0x88, 0x02,
};

static const uint8_t fips_x[] = {
    0x20, 0x70, 0xb3, 0x22, 0x3d, 0xba, 0x37, 0x2f, 0xde, 0x1c, 0x0f,
    0xfc, 0x7b, 0x2e, 0x3b, 0x49, 0x8b, 0x26, 0x06, 0x14,
};

static const uint8_t fips_y[] = {
    0x19, 0x13, 0x18, 0x71, 0xd7, 0x5b, 0x16, 0x12, 0xa8, 0x19, 0xf2,
    0x9d, 0x78, 0xd1, 0xb0, 0xd7, 0x34, 0x6f, 0x7a, 0xa7, 0x7b, 0xb6,
    0x2a, 0x85, 0x9b, 0xfd, 0x6c, 0x56, 0x75, 0xda, 0x9d, 0x21, 0x2d,
    0x3a, 0x36, 0xef, 0x16, 0x72, 0xef, 0x66, 0x0b, 0x8c, 0x7c, 0x25,
    0x5c, 0xc0, 0xec, 0x74, 0x85, 0x8f, 0xba, 0x33, 0xf4, 0x4c, 0x06,
    0x69, 0x96, 0x30, 0xa7, 0x6b, 0x03, 0x0e, 0xe3, 0x33,
};

static const uint8_t fips_digest[] = {
    0xa9, 0x99, 0x3e, 0x36, 0x47, 0x06, 0x81, 0x6a, 0xba, 0x3e, 0x25,
    0x71, 0x78, 0x50, 0xc2, 0x6c, 0x9c, 0xd0, 0xd8, 0x9d,
};

// fips_sig is a DER-encoded version of the r and s values in FIPS PUB 186-1.
static const uint8_t fips_sig[] = {
    0x30, 0x2d, 0x02, 0x15, 0x00, 0x8b, 0xac, 0x1a, 0xb6, 0x64, 0x10,
    0x43, 0x5c, 0xb7, 0x18, 0x1f, 0x95, 0xb1, 0x6a, 0xb9, 0x7c, 0x92,
    0xb3, 0x41, 0xc0, 0x02, 0x14, 0x41, 0xe2, 0x34, 0x5f, 0x1f, 0x56,
    0xdf, 0x24, 0x58, 0xf4, 0x26, 0xd1, 0x55, 0xb4, 0xba, 0x2d, 0xb6,
    0xdc, 0xd8, 0xc8,
};

// fips_sig_negative is fips_sig with r encoded as a negative number.
static const uint8_t fips_sig_negative[] = {
    0x30, 0x2c, 0x02, 0x14, 0x8b, 0xac, 0x1a, 0xb6, 0x64, 0x10, 0x43,
    0x5c, 0xb7, 0x18, 0x1f, 0x95, 0xb1, 0x6a, 0xb9, 0x7c, 0x92, 0xb3,
    0x41, 0xc0, 0x02, 0x14, 0x41, 0xe2, 0x34, 0x5f, 0x1f, 0x56, 0xdf,
    0x24, 0x58, 0xf4, 0x26, 0xd1, 0x55, 0xb4, 0xba, 0x2d, 0xb6, 0xdc,
    0xd8, 0xc8,
};

// fip_sig_extra is fips_sig with trailing data.
static const uint8_t fips_sig_extra[] = {
    0x30, 0x2d, 0x02, 0x15, 0x00, 0x8b, 0xac, 0x1a, 0xb6, 0x64, 0x10,
    0x43, 0x5c, 0xb7, 0x18, 0x1f, 0x95, 0xb1, 0x6a, 0xb9, 0x7c, 0x92,
    0xb3, 0x41, 0xc0, 0x02, 0x14, 0x41, 0xe2, 0x34, 0x5f, 0x1f, 0x56,
    0xdf, 0x24, 0x58, 0xf4, 0x26, 0xd1, 0x55, 0xb4, 0xba, 0x2d, 0xb6,
    0xdc, 0xd8, 0xc8, 0x00,
};

// fips_sig_lengths is fips_sig with a non-minimally encoded length.
static const uint8_t fips_sig_bad_length[] = {
    0x30, 0x81, 0x2d, 0x02, 0x15, 0x00, 0x8b, 0xac, 0x1a, 0xb6, 0x64,
    0x10, 0x43, 0x5c, 0xb7, 0x18, 0x1f, 0x95, 0xb1, 0x6a, 0xb9, 0x7c,
    0x92, 0xb3, 0x41, 0xc0, 0x02, 0x14, 0x41, 0xe2, 0x34, 0x5f, 0x1f,
    0x56, 0xdf, 0x24, 0x58, 0xf4, 0x26, 0xd1, 0x55, 0xb4, 0xba, 0x2d,
    0xb6, 0xdc, 0xd8, 0xc8, 0x00,
};

// fips_sig_bad_r is fips_sig with a bad r value.
static const uint8_t fips_sig_bad_r[] = {
    0x30, 0x2d, 0x02, 0x15, 0x00, 0x8c, 0xac, 0x1a, 0xb6, 0x64, 0x10,
    0x43, 0x5c, 0xb7, 0x18, 0x1f, 0x95, 0xb1, 0x6a, 0xb9, 0x7c, 0x92,
    0xb3, 0x41, 0xc0, 0x02, 0x14, 0x41, 0xe2, 0x34, 0x5f, 0x1f, 0x56,
    0xdf, 0x24, 0x58, 0xf4, 0x26, 0xd1, 0x55, 0xb4, 0xba, 0x2d, 0xb6,
    0xdc, 0xd8, 0xc8,
};

static bssl::UniquePtr<DSA> GetFIPSDSA(void) {
  bssl::UniquePtr<DSA>  dsa(DSA_new());
  if (!dsa) {
    return nullptr;
  }
  dsa->p = BN_bin2bn(fips_p, sizeof(fips_p), nullptr);
  dsa->q = BN_bin2bn(fips_q, sizeof(fips_q), nullptr);
  dsa->g = BN_bin2bn(fips_g, sizeof(fips_g), nullptr);
  dsa->pub_key = BN_bin2bn(fips_y, sizeof(fips_y), nullptr);
  dsa->priv_key = BN_bin2bn(fips_x, sizeof(fips_x), nullptr);
  if (dsa->p == nullptr || dsa->q == nullptr || dsa->g == nullptr ||
      dsa->pub_key == nullptr || dsa->priv_key == nullptr) {
    return nullptr;
  }
  return dsa;
}

struct GenerateContext {
  FILE *out = nullptr;
  int ok = 0;
  int num = 0;
};

static int GenerateCallback(int p, int n, BN_GENCB *arg) {
  GenerateContext *ctx = reinterpret_cast<GenerateContext *>(arg->arg);
  char c = '*';
  switch (p) {
    case 0:
      c = '.';
      ctx->num++;
      break;
    case 1:
      c = '+';
      break;
    case 2:
      c = '*';
      ctx->ok++;
      break;
    case 3:
      c = '\n';
  }
  fputc(c, ctx->out);
  fflush(ctx->out);
  if (!ctx->ok && p == 0 && ctx->num > 1) {
    fprintf(stderr, "error in dsatest\n");
    return 0;
  }
  return 1;
}

static int TestGenerate(FILE *out) {
  BN_GENCB cb;
  int counter, i, j;
  uint8_t buf[256];
  unsigned long h;
  uint8_t sig[256];
  unsigned int siglen;

  fprintf(out, "test generation of DSA parameters\n");

  GenerateContext ctx;
  ctx.out = out;
  BN_GENCB_set(&cb, GenerateCallback, &ctx);
  bssl::UniquePtr<DSA> dsa(DSA_new());
  if (!dsa ||
      !DSA_generate_parameters_ex(dsa.get(), 512, seed, 20, &counter, &h,
                                  &cb)) {
    return false;
  }

  fprintf(out, "seed\n");
  for (i = 0; i < 20; i += 4) {
    fprintf(out, "%02X%02X%02X%02X ", seed[i], seed[i + 1], seed[i + 2],
            seed[i + 3]);
  }
  fprintf(out, "\ncounter=%d h=%ld\n", counter, h);

  if (counter != 105) {
    fprintf(stderr, "counter should be 105\n");
    return false;
  }
  if (h != 2) {
    fprintf(stderr, "h should be 2\n");
    return false;
  }

  i = BN_bn2bin(dsa->q, buf);
  j = sizeof(fips_q);
  if (i != j || OPENSSL_memcmp(buf, fips_q, i) != 0) {
    fprintf(stderr, "q value is wrong\n");
    return false;
  }

  i = BN_bn2bin(dsa->p, buf);
  j = sizeof(fips_p);
  if (i != j || OPENSSL_memcmp(buf, fips_p, i) != 0) {
    fprintf(stderr, "p value is wrong\n");
    return false;
  }

  i = BN_bn2bin(dsa->g, buf);
  j = sizeof(fips_g);
  if (i != j || OPENSSL_memcmp(buf, fips_g, i) != 0) {
    fprintf(stderr, "g value is wrong\n");
    return false;
  }

  if (!DSA_generate_key(dsa.get()) ||
      !DSA_sign(0, fips_digest, sizeof(fips_digest), sig, &siglen, dsa.get())) {
    return false;
  }
  if (DSA_verify(0, fips_digest, sizeof(fips_digest), sig, siglen, dsa.get()) !=
      1) {
    fprintf(stderr, "verification failure\n");
    return false;
  }

  return true;
}

static bool TestVerify(const uint8_t *sig, size_t sig_len, int expect) {
  bssl::UniquePtr<DSA> dsa = GetFIPSDSA();
  if (!dsa) {
    return false;
  }

  int ret =
      DSA_verify(0, fips_digest, sizeof(fips_digest), sig, sig_len, dsa.get());
  if (ret != expect) {
    fprintf(stderr, "DSA_verify returned %d, want %d\n", ret, expect);
    return false;
  }

  // Clear any errors from a test with expected failure.
  ERR_clear_error();
  return true;
}

// TODO(davidben): Convert this file to GTest properly.
TEST(DSATest, AllTests) {
  if (!TestGenerate(stdout) ||
      !TestVerify(fips_sig, sizeof(fips_sig), 1) ||
      !TestVerify(fips_sig_negative, sizeof(fips_sig_negative), -1) ||
      !TestVerify(fips_sig_extra, sizeof(fips_sig_extra), -1) ||
      !TestVerify(fips_sig_bad_length, sizeof(fips_sig_bad_length), -1) ||
      !TestVerify(fips_sig_bad_r, sizeof(fips_sig_bad_r), 0)) {
    ADD_FAILURE() << "Tests failed";
  }
}

TEST(DSATest, InvalidGroup) {
  bssl::UniquePtr<DSA> dsa = GetFIPSDSA();
  ASSERT_TRUE(dsa);
  BN_zero(dsa->g);

  std::vector<uint8_t> sig(DSA_size(dsa.get()));
  unsigned sig_len;
  static const uint8_t kDigest[32] = {0};
  EXPECT_FALSE(
      DSA_sign(0, kDigest, sizeof(kDigest), sig.data(), &sig_len, dsa.get()));
  uint32_t err = ERR_get_error();
  EXPECT_EQ(ERR_LIB_DSA, ERR_GET_LIB(err));
  EXPECT_EQ(DSA_R_INVALID_PARAMETERS, ERR_GET_REASON(err));
}
