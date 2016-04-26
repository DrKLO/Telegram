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

#include <openssl/digest.h>

#include <assert.h>
#include <string.h>

#include <openssl/md5.h>
#include <openssl/obj.h>
#include <openssl/sha.h>

#include "internal.h"

#if defined(NDEBUG)
#define CHECK(x) (void) (x)
#else
#define CHECK(x) assert(x)
#endif

static void md5_init(EVP_MD_CTX *ctx) {
  CHECK(MD5_Init(ctx->md_data));
}

static void md5_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  CHECK(MD5_Update(ctx->md_data, data, count));
}

static void md5_final(EVP_MD_CTX *ctx, uint8_t *out) {
  CHECK(MD5_Final(out, ctx->md_data));
}

static const EVP_MD md5_md = {
    NID_md5,    MD5_DIGEST_LENGTH, 0 /* flags */,       md5_init,
    md5_update, md5_final,         64 /* block size */, sizeof(MD5_CTX),
};

const EVP_MD *EVP_md5(void) { return &md5_md; }


static void sha1_init(EVP_MD_CTX *ctx) {
  CHECK(SHA1_Init(ctx->md_data));
}

static void sha1_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  CHECK(SHA1_Update(ctx->md_data, data, count));
}

static void sha1_final(EVP_MD_CTX *ctx, uint8_t *md) {
  CHECK(SHA1_Final(md, ctx->md_data));
}

static const EVP_MD sha1_md = {
    NID_sha1,    SHA_DIGEST_LENGTH, 0 /* flags */,       sha1_init,
    sha1_update, sha1_final,        64 /* block size */, sizeof(SHA_CTX),
};

const EVP_MD *EVP_sha1(void) { return &sha1_md; }


static void sha224_init(EVP_MD_CTX *ctx) {
  CHECK(SHA224_Init(ctx->md_data));
}

static void sha224_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  CHECK(SHA224_Update(ctx->md_data, data, count));
}

static void sha224_final(EVP_MD_CTX *ctx, uint8_t *md) {
  CHECK(SHA224_Final(md, ctx->md_data));
}

static const EVP_MD sha224_md = {
    NID_sha224,          SHA224_DIGEST_LENGTH, 0 /* flags */,
    sha224_init,         sha224_update,        sha224_final,
    64 /* block size */, sizeof(SHA256_CTX),
};

const EVP_MD *EVP_sha224(void) { return &sha224_md; }


static void sha256_init(EVP_MD_CTX *ctx) {
  CHECK(SHA256_Init(ctx->md_data));
}

static void sha256_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  CHECK(SHA256_Update(ctx->md_data, data, count));
}

static void sha256_final(EVP_MD_CTX *ctx, uint8_t *md) {
  CHECK(SHA256_Final(md, ctx->md_data));
}

static const EVP_MD sha256_md = {
    NID_sha256,          SHA256_DIGEST_LENGTH, 0 /* flags */,
    sha256_init,         sha256_update,        sha256_final,
    64 /* block size */, sizeof(SHA256_CTX),
};

const EVP_MD *EVP_sha256(void) { return &sha256_md; }


static void sha384_init(EVP_MD_CTX *ctx) {
  CHECK(SHA384_Init(ctx->md_data));
}

static void sha384_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  CHECK(SHA384_Update(ctx->md_data, data, count));
}

static void sha384_final(EVP_MD_CTX *ctx, uint8_t *md) {
  CHECK(SHA384_Final(md, ctx->md_data));
}

static const EVP_MD sha384_md = {
    NID_sha384,           SHA384_DIGEST_LENGTH, 0 /* flags */,
    sha384_init,          sha384_update,        sha384_final,
    128 /* block size */, sizeof(SHA512_CTX),
};

const EVP_MD *EVP_sha384(void) { return &sha384_md; }


static void sha512_init(EVP_MD_CTX *ctx) {
  CHECK(SHA512_Init(ctx->md_data));
}

static void sha512_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  CHECK(SHA512_Update(ctx->md_data, data, count));
}

static void sha512_final(EVP_MD_CTX *ctx, uint8_t *md) {
  CHECK(SHA512_Final(md, ctx->md_data));
}

static const EVP_MD sha512_md = {
    NID_sha512,           SHA512_DIGEST_LENGTH, 0 /* flags */,
    sha512_init,          sha512_update,        sha512_final,
    128 /* block size */, sizeof(SHA512_CTX),
};

const EVP_MD *EVP_sha512(void) { return &sha512_md; }


typedef struct {
  MD5_CTX md5;
  SHA_CTX sha1;
} MD5_SHA1_CTX;

static void md5_sha1_init(EVP_MD_CTX *md_ctx) {
  MD5_SHA1_CTX *ctx = md_ctx->md_data;
  CHECK(MD5_Init(&ctx->md5) && SHA1_Init(&ctx->sha1));
}

static void md5_sha1_update(EVP_MD_CTX *md_ctx, const void *data,
                            size_t count) {
  MD5_SHA1_CTX *ctx = md_ctx->md_data;
  CHECK(MD5_Update(&ctx->md5, data, count) &&
        SHA1_Update(&ctx->sha1, data, count));
}

static void md5_sha1_final(EVP_MD_CTX *md_ctx, uint8_t *out) {
  MD5_SHA1_CTX *ctx = md_ctx->md_data;
  CHECK(MD5_Final(out, &ctx->md5) &&
        SHA1_Final(out + MD5_DIGEST_LENGTH, &ctx->sha1));
}

static const EVP_MD md5_sha1_md = {
    NID_md5_sha1,
    MD5_DIGEST_LENGTH + SHA_DIGEST_LENGTH,
    0 /* flags */,
    md5_sha1_init,
    md5_sha1_update,
    md5_sha1_final,
    64 /* block size */,
    sizeof(MD5_SHA1_CTX),
};

const EVP_MD *EVP_md5_sha1(void) { return &md5_sha1_md; }


struct nid_to_digest {
  int nid;
  const EVP_MD* (*md_func)(void);
  const char *short_name;
  const char *long_name;
};

static const struct nid_to_digest nid_to_digest_mapping[] = {
  { NID_md5, EVP_md5, SN_md5, LN_md5 },
  { NID_sha1, EVP_sha1, SN_sha1, LN_sha1 },
  { NID_sha224, EVP_sha224, SN_sha224, LN_sha224 },
  { NID_sha256, EVP_sha256, SN_sha256, LN_sha256 },
  { NID_sha384, EVP_sha384, SN_sha384, LN_sha384 },
  { NID_sha512, EVP_sha512, SN_sha512, LN_sha512 },
  { NID_md5_sha1, EVP_md5_sha1, SN_md5_sha1, LN_md5_sha1 },
  { NID_dsaWithSHA, EVP_sha1, SN_dsaWithSHA, LN_dsaWithSHA },
  { NID_dsaWithSHA1, EVP_sha1, SN_dsaWithSHA1, LN_dsaWithSHA1 },
  { NID_ecdsa_with_SHA1, EVP_sha1, SN_ecdsa_with_SHA1, NULL },
  { NID_md5WithRSAEncryption, EVP_md5, SN_md5WithRSAEncryption,
    LN_md5WithRSAEncryption },
  { NID_sha1WithRSAEncryption, EVP_sha1, SN_sha1WithRSAEncryption,
    LN_sha1WithRSAEncryption },
  { NID_sha224WithRSAEncryption, EVP_sha224, SN_sha224WithRSAEncryption,
    LN_sha224WithRSAEncryption },
  { NID_sha256WithRSAEncryption, EVP_sha256, SN_sha256WithRSAEncryption,
    LN_sha256WithRSAEncryption },
  { NID_sha384WithRSAEncryption, EVP_sha384, SN_sha384WithRSAEncryption,
    LN_sha384WithRSAEncryption },
  { NID_sha512WithRSAEncryption, EVP_sha512, SN_sha512WithRSAEncryption,
    LN_sha512WithRSAEncryption },
};

const EVP_MD* EVP_get_digestbynid(int nid) {
  unsigned i;

  for (i = 0; i < sizeof(nid_to_digest_mapping) / sizeof(struct nid_to_digest);
       i++) {
    if (nid_to_digest_mapping[i].nid == nid) {
      return nid_to_digest_mapping[i].md_func();
    }
  }

  return NULL;
}

const EVP_MD* EVP_get_digestbyobj(const ASN1_OBJECT *obj) {
  return EVP_get_digestbynid(OBJ_obj2nid(obj));
}

const EVP_MD *EVP_get_digestbyname(const char *name) {
  unsigned i;

  for (i = 0; i < sizeof(nid_to_digest_mapping) / sizeof(struct nid_to_digest);
       i++) {
    const char *short_name = nid_to_digest_mapping[i].short_name;
    const char *long_name = nid_to_digest_mapping[i].long_name;
    if ((short_name && strcmp(short_name, name) == 0) ||
        (long_name && strcmp(long_name, name) == 0)) {
      return nid_to_digest_mapping[i].md_func();
    }
  }

  return NULL;
}
