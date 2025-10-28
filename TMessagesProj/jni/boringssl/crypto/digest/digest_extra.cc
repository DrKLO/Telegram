// Copyright 1995-2016 The OpenSSL Project Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <openssl/digest.h>

#include <string.h>

#include <openssl/blake2.h>
#include <openssl/bytestring.h>
#include <openssl/md4.h>
#include <openssl/md5.h>
#include <openssl/nid.h>
#include <openssl/obj.h>

#include "../asn1/internal.h"
#include "../fipsmodule/digest/internal.h"
#include "../internal.h"


struct nid_to_digest {
  int nid;
  const EVP_MD *(*md_func)(void);
  const char *short_name;
  const char *long_name;
};

static const struct nid_to_digest nid_to_digest_mapping[] = {
    {NID_md4, EVP_md4, SN_md4, LN_md4},
    {NID_md5, EVP_md5, SN_md5, LN_md5},
    {NID_sha1, EVP_sha1, SN_sha1, LN_sha1},
    {NID_sha224, EVP_sha224, SN_sha224, LN_sha224},
    {NID_sha256, EVP_sha256, SN_sha256, LN_sha256},
    {NID_sha384, EVP_sha384, SN_sha384, LN_sha384},
    {NID_sha512, EVP_sha512, SN_sha512, LN_sha512},
    {NID_sha512_256, EVP_sha512_256, SN_sha512_256, LN_sha512_256},
    {NID_md5_sha1, EVP_md5_sha1, SN_md5_sha1, LN_md5_sha1},
    // As a remnant of signing |EVP_MD|s, OpenSSL returned the corresponding
    // hash function when given a signature OID. To avoid unintended lax parsing
    // of hash OIDs, this is no longer supported for lookup by OID or NID.
    // Node.js, however, exposes |EVP_get_digestbyname|'s full behavior to
    // consumers so we retain it there.
    {NID_undef, EVP_sha1, SN_dsaWithSHA, LN_dsaWithSHA},
    {NID_undef, EVP_sha1, SN_dsaWithSHA1, LN_dsaWithSHA1},
    {NID_undef, EVP_sha1, SN_ecdsa_with_SHA1, NULL},
    {NID_undef, EVP_md5, SN_md5WithRSAEncryption, LN_md5WithRSAEncryption},
    {NID_undef, EVP_sha1, SN_sha1WithRSAEncryption, LN_sha1WithRSAEncryption},
    {NID_undef, EVP_sha224, SN_sha224WithRSAEncryption,
     LN_sha224WithRSAEncryption},
    {NID_undef, EVP_sha256, SN_sha256WithRSAEncryption,
     LN_sha256WithRSAEncryption},
    {NID_undef, EVP_sha384, SN_sha384WithRSAEncryption,
     LN_sha384WithRSAEncryption},
    {NID_undef, EVP_sha512, SN_sha512WithRSAEncryption,
     LN_sha512WithRSAEncryption},
};

const EVP_MD *EVP_get_digestbynid(int nid) {
  if (nid == NID_undef) {
    // Skip the |NID_undef| entries in |nid_to_digest_mapping|.
    return NULL;
  }

  for (unsigned i = 0; i < OPENSSL_ARRAY_SIZE(nid_to_digest_mapping); i++) {
    if (nid_to_digest_mapping[i].nid == nid) {
      return nid_to_digest_mapping[i].md_func();
    }
  }

  return NULL;
}

static const struct {
  uint8_t oid[9];
  uint8_t oid_len;
  int nid;
} kMDOIDs[] = {
    // 1.2.840.113549.2.4
    {{0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x02, 0x04}, 8, NID_md4},
    // 1.2.840.113549.2.5
    {{0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x02, 0x05}, 8, NID_md5},
    // 1.3.14.3.2.26
    {{0x2b, 0x0e, 0x03, 0x02, 0x1a}, 5, NID_sha1},
    // 2.16.840.1.101.3.4.2.1
    {{0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01}, 9, NID_sha256},
    // 2.16.840.1.101.3.4.2.2
    {{0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x02}, 9, NID_sha384},
    // 2.16.840.1.101.3.4.2.3
    {{0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03}, 9, NID_sha512},
    // 2.16.840.1.101.3.4.2.4
    {{0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x04}, 9, NID_sha224},
};

static const EVP_MD *cbs_to_md(const CBS *cbs) {
  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(kMDOIDs); i++) {
    if (CBS_len(cbs) == kMDOIDs[i].oid_len &&
        OPENSSL_memcmp(CBS_data(cbs), kMDOIDs[i].oid, kMDOIDs[i].oid_len) ==
            0) {
      return EVP_get_digestbynid(kMDOIDs[i].nid);
    }
  }

  return NULL;
}

const EVP_MD *EVP_get_digestbyobj(const ASN1_OBJECT *obj) {
  // Handle objects with no corresponding OID. Note we don't use |OBJ_obj2nid|
  // here to avoid pulling in the OID table.
  if (obj->nid != NID_undef) {
    return EVP_get_digestbynid(obj->nid);
  }

  CBS cbs;
  CBS_init(&cbs, OBJ_get0_data(obj), OBJ_length(obj));
  return cbs_to_md(&cbs);
}

const EVP_MD *EVP_parse_digest_algorithm(CBS *cbs) {
  CBS algorithm, oid;
  if (!CBS_get_asn1(cbs, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1(&algorithm, &oid, CBS_ASN1_OBJECT)) {
    OPENSSL_PUT_ERROR(DIGEST, DIGEST_R_DECODE_ERROR);
    return NULL;
  }

  const EVP_MD *ret = cbs_to_md(&oid);
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(DIGEST, DIGEST_R_UNKNOWN_HASH);
    return NULL;
  }

  // The parameters, if present, must be NULL. Historically, whether the NULL
  // was included or omitted was not well-specified. When parsing an
  // AlgorithmIdentifier, we allow both. (Note this code is not used when
  // verifying RSASSA-PKCS1-v1_5 signatures.)
  if (CBS_len(&algorithm) > 0) {
    CBS param;
    if (!CBS_get_asn1(&algorithm, &param, CBS_ASN1_NULL) ||
        CBS_len(&param) != 0 ||  //
        CBS_len(&algorithm) != 0) {
      OPENSSL_PUT_ERROR(DIGEST, DIGEST_R_DECODE_ERROR);
      return NULL;
    }
  }

  return ret;
}

int EVP_marshal_digest_algorithm(CBB *cbb, const EVP_MD *md) {
  CBB algorithm, oid, null;
  if (!CBB_add_asn1(cbb, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT)) {
    return 0;
  }

  int found = 0;
  int nid = EVP_MD_type(md);
  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(kMDOIDs); i++) {
    if (nid == kMDOIDs[i].nid) {
      if (!CBB_add_bytes(&oid, kMDOIDs[i].oid, kMDOIDs[i].oid_len)) {
        return 0;
      }
      found = 1;
      break;
    }
  }

  if (!found) {
    OPENSSL_PUT_ERROR(DIGEST, DIGEST_R_UNKNOWN_HASH);
    return 0;
  }

  // TODO(crbug.com/boringssl/710): Is this correct? See RFC 4055, section 2.1.
  if (!CBB_add_asn1(&algorithm, &null, CBS_ASN1_NULL) ||  //
      !CBB_flush(cbb)) {
    return 0;
  }

  return 1;
}

const EVP_MD *EVP_get_digestbyname(const char *name) {
  for (unsigned i = 0; i < OPENSSL_ARRAY_SIZE(nid_to_digest_mapping); i++) {
    const char *short_name = nid_to_digest_mapping[i].short_name;
    const char *long_name = nid_to_digest_mapping[i].long_name;
    if ((short_name && strcmp(short_name, name) == 0) ||
        (long_name && strcmp(long_name, name) == 0)) {
      return nid_to_digest_mapping[i].md_func();
    }
  }

  return NULL;
}

static void blake2b256_init(EVP_MD_CTX *ctx) {
  BLAKE2B256_Init(reinterpret_cast<BLAKE2B_CTX *>(ctx->md_data));
}

static void blake2b256_update(EVP_MD_CTX *ctx, const void *data, size_t len) {
  BLAKE2B256_Update(reinterpret_cast<BLAKE2B_CTX *>(ctx->md_data), data, len);
}

static void blake2b256_final(EVP_MD_CTX *ctx, uint8_t *md) {
  BLAKE2B256_Final(md, reinterpret_cast<BLAKE2B_CTX *>(ctx->md_data));
}

static const EVP_MD evp_md_blake2b256 = {
    NID_undef,       BLAKE2B256_DIGEST_LENGTH, 0,
    blake2b256_init, blake2b256_update,        blake2b256_final,
    BLAKE2B_CBLOCK,  sizeof(BLAKE2B_CTX),
};

const EVP_MD *EVP_blake2b256(void) { return &evp_md_blake2b256; }


static void md4_init(EVP_MD_CTX *ctx) {
  BSSL_CHECK(MD4_Init(reinterpret_cast<MD4_CTX *>(ctx->md_data)));
}

static void md4_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  BSSL_CHECK(
      MD4_Update(reinterpret_cast<MD4_CTX *>(ctx->md_data), data, count));
}

static void md4_final(EVP_MD_CTX *ctx, uint8_t *out) {
  BSSL_CHECK(MD4_Final(out, reinterpret_cast<MD4_CTX *>(ctx->md_data)));
}

static const EVP_MD evp_md_md4 = {
    NID_md4,            //
    MD4_DIGEST_LENGTH,  //
    0,
    md4_init,
    md4_update,
    md4_final,
    64,
    sizeof(MD4_CTX),
};

const EVP_MD *EVP_md4(void) { return &evp_md_md4; }

static void md5_init(EVP_MD_CTX *ctx) {
  BSSL_CHECK(MD5_Init(reinterpret_cast<MD5_CTX *>(ctx->md_data)));
}

static void md5_update(EVP_MD_CTX *ctx, const void *data, size_t count) {
  BSSL_CHECK(
      MD5_Update(reinterpret_cast<MD5_CTX *>(ctx->md_data), data, count));
}

static void md5_final(EVP_MD_CTX *ctx, uint8_t *out) {
  BSSL_CHECK(MD5_Final(out, reinterpret_cast<MD5_CTX *>(ctx->md_data)));
}

static const EVP_MD evp_md_md5 = {
    NID_md5,    MD5_DIGEST_LENGTH, 0,  md5_init,
    md5_update, md5_final,         64, sizeof(MD5_CTX),
};

const EVP_MD *EVP_md5(void) { return &evp_md_md5; }

typedef struct {
  MD5_CTX md5;
  SHA_CTX sha1;
} MD5_SHA1_CTX;

static void md5_sha1_init(EVP_MD_CTX *md_ctx) {
  MD5_SHA1_CTX *ctx = reinterpret_cast<MD5_SHA1_CTX *>(md_ctx->md_data);
  BSSL_CHECK(MD5_Init(&ctx->md5) && SHA1_Init(&ctx->sha1));
}

static void md5_sha1_update(EVP_MD_CTX *md_ctx, const void *data,
                            size_t count) {
  MD5_SHA1_CTX *ctx = reinterpret_cast<MD5_SHA1_CTX *>(md_ctx->md_data);
  BSSL_CHECK(MD5_Update(&ctx->md5, data, count) &&
             SHA1_Update(&ctx->sha1, data, count));
}

static void md5_sha1_final(EVP_MD_CTX *md_ctx, uint8_t *out) {
  MD5_SHA1_CTX *ctx = reinterpret_cast<MD5_SHA1_CTX *>(md_ctx->md_data);
  BSSL_CHECK(MD5_Final(out, &ctx->md5) &&
             SHA1_Final(out + MD5_DIGEST_LENGTH, &ctx->sha1));
}

const EVP_MD evp_md_md5_sha1 = {
    NID_md5_sha1,
    MD5_DIGEST_LENGTH + SHA_DIGEST_LENGTH,
    0,
    md5_sha1_init,
    md5_sha1_update,
    md5_sha1_final,
    64,
    sizeof(MD5_SHA1_CTX),
};

const EVP_MD *EVP_md5_sha1(void) { return &evp_md_md5_sha1; }
