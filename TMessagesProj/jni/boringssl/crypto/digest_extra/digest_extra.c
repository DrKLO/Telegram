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

#include <string.h>

#include <openssl/asn1.h>
#include <openssl/bytestring.h>
#include <openssl/nid.h>

#include "../internal.h"


struct nid_to_digest {
  int nid;
  const EVP_MD* (*md_func)(void);
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

const EVP_MD* EVP_get_digestbynid(int nid) {
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
  { {0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x02, 0x04}, 8, NID_md4 },
  // 1.2.840.113549.2.5
  { {0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x02, 0x05}, 8, NID_md5 },
  // 1.3.14.3.2.26
  { {0x2b, 0x0e, 0x03, 0x02, 0x1a}, 5, NID_sha1 },
  // 2.16.840.1.101.3.4.2.1
  { {0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01}, 9, NID_sha256 },
  // 2.16.840.1.101.3.4.2.2
  { {0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x02}, 9, NID_sha384 },
  // 2.16.840.1.101.3.4.2.3
  { {0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03}, 9, NID_sha512 },
  // 2.16.840.1.101.3.4.2.4
  { {0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x04}, 9, NID_sha224 },
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
  // Handle objects with no corresponding OID.
  if (obj->nid != NID_undef) {
    return EVP_get_digestbynid(obj->nid);
  }

  CBS cbs;
  CBS_init(&cbs, obj->data, obj->length);
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
        CBS_len(&param) != 0 ||
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
    OPENSSL_PUT_ERROR(DIGEST, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  int found = 0;
  int nid = EVP_MD_type(md);
  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(kMDOIDs); i++) {
    if (nid == kMDOIDs[i].nid) {
      if (!CBB_add_bytes(&oid, kMDOIDs[i].oid, kMDOIDs[i].oid_len)) {
        OPENSSL_PUT_ERROR(DIGEST, ERR_R_MALLOC_FAILURE);
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

  if (!CBB_add_asn1(&algorithm, &null, CBS_ASN1_NULL) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(DIGEST, ERR_R_MALLOC_FAILURE);
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
