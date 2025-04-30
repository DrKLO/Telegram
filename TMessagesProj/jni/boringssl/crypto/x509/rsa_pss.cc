// Copyright 2006-2016 The OpenSSL Project Authors. All Rights Reserved.
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

#include <openssl/x509.h>

#include <assert.h>
#include <limits.h>

#include <openssl/asn1.h>
#include <openssl/asn1t.h>
#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/obj.h>

#include "internal.h"


static int rsa_pss_cb(int operation, ASN1_VALUE **pval, const ASN1_ITEM *it,
                      void *exarg) {
  if (operation == ASN1_OP_FREE_PRE) {
    RSA_PSS_PARAMS *pss = (RSA_PSS_PARAMS *)*pval;
    X509_ALGOR_free(pss->maskHash);
  }
  return 1;
}

ASN1_SEQUENCE_cb(RSA_PSS_PARAMS, rsa_pss_cb) = {
    ASN1_EXP_OPT(RSA_PSS_PARAMS, hashAlgorithm, X509_ALGOR, 0),
    ASN1_EXP_OPT(RSA_PSS_PARAMS, maskGenAlgorithm, X509_ALGOR, 1),
    ASN1_EXP_OPT(RSA_PSS_PARAMS, saltLength, ASN1_INTEGER, 2),
    ASN1_EXP_OPT(RSA_PSS_PARAMS, trailerField, ASN1_INTEGER, 3),
} ASN1_SEQUENCE_END_cb(RSA_PSS_PARAMS, RSA_PSS_PARAMS)

IMPLEMENT_ASN1_FUNCTIONS_const(RSA_PSS_PARAMS)


// Given an MGF1 Algorithm ID decode to an Algorithm Identifier
static X509_ALGOR *rsa_mgf1_decode(const X509_ALGOR *alg) {
  if (OBJ_obj2nid(alg->algorithm) != NID_mgf1 || alg->parameter == NULL ||
      alg->parameter->type != V_ASN1_SEQUENCE) {
    return NULL;
  }

  const uint8_t *p = alg->parameter->value.sequence->data;
  int plen = alg->parameter->value.sequence->length;
  return d2i_X509_ALGOR(NULL, &p, plen);
}

static RSA_PSS_PARAMS *rsa_pss_decode(const X509_ALGOR *alg) {
  if (alg->parameter == NULL || alg->parameter->type != V_ASN1_SEQUENCE) {
    return NULL;
  }

  const uint8_t *p = alg->parameter->value.sequence->data;
  int plen = alg->parameter->value.sequence->length;
  return d2i_RSA_PSS_PARAMS(NULL, &p, plen);
}

static int is_allowed_pss_md(const EVP_MD *md) {
  int md_type = EVP_MD_type(md);
  return md_type == NID_sha256 || md_type == NID_sha384 ||
         md_type == NID_sha512;
}

// rsa_md_to_algor sets |*palg| to an |X509_ALGOR| describing the digest |md|,
// which must be an allowed PSS digest.
static int rsa_md_to_algor(X509_ALGOR **palg, const EVP_MD *md) {
  // SHA-1 should be omitted (DEFAULT), but we do not allow SHA-1.
  assert(is_allowed_pss_md(md));
  *palg = X509_ALGOR_new();
  if (*palg == NULL) {
    return 0;
  }
  if (!X509_ALGOR_set_md(*palg, md)) {
    X509_ALGOR_free(*palg);
    *palg = NULL;
    return 0;
  }
  return 1;
}

// rsa_md_to_mgf1 sets |*palg| to an |X509_ALGOR| describing MGF-1 with the
// digest |mgf1md|, which must be an allowed PSS digest.
static int rsa_md_to_mgf1(X509_ALGOR **palg, const EVP_MD *mgf1md) {
  // SHA-1 should be omitted (DEFAULT), but we do not allow SHA-1.
  assert(is_allowed_pss_md(mgf1md));
  X509_ALGOR *algtmp = NULL;
  ASN1_STRING *stmp = NULL;
  // need to embed algorithm ID inside another
  if (!rsa_md_to_algor(&algtmp, mgf1md) ||
      !ASN1_item_pack(algtmp, ASN1_ITEM_rptr(X509_ALGOR), &stmp)) {
    goto err;
  }
  *palg = X509_ALGOR_new();
  if (!*palg) {
    goto err;
  }
  if (!X509_ALGOR_set0(*palg, OBJ_nid2obj(NID_mgf1), V_ASN1_SEQUENCE, stmp)) {
    goto err;
  }
  stmp = NULL;

err:
  ASN1_STRING_free(stmp);
  X509_ALGOR_free(algtmp);
  if (*palg) {
    return 1;
  }

  return 0;
}

static const EVP_MD *rsa_algor_to_md(const X509_ALGOR *alg) {
  if (!alg) {
    // If omitted, PSS defaults to SHA-1, which we do not allow.
    OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
    return NULL;
  }
  const EVP_MD *md = EVP_get_digestbyobj(alg->algorithm);
  if (md == NULL || !is_allowed_pss_md(md)) {
    OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
    return NULL;
  }
  return md;
}

static const EVP_MD *rsa_mgf1_to_md(const X509_ALGOR *alg) {
  if (!alg) {
    // If omitted, PSS defaults to MGF-1 with SHA-1, which we do not allow.
    OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
    return NULL;
  }
  // Check mask and lookup mask hash algorithm.
  X509_ALGOR *maskHash = rsa_mgf1_decode(alg);
  if (maskHash == NULL) {
    OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
    return NULL;
  }
  const EVP_MD *ret = rsa_algor_to_md(maskHash);
  X509_ALGOR_free(maskHash);
  return ret;
}

int x509_rsa_ctx_to_pss(EVP_MD_CTX *ctx, X509_ALGOR *algor) {
  const EVP_MD *sigmd, *mgf1md;
  int saltlen;
  if (!EVP_PKEY_CTX_get_signature_md(ctx->pctx, &sigmd) ||
      !EVP_PKEY_CTX_get_rsa_mgf1_md(ctx->pctx, &mgf1md) ||
      !EVP_PKEY_CTX_get_rsa_pss_saltlen(ctx->pctx, &saltlen)) {
    return 0;
  }

  if (sigmd != mgf1md || !is_allowed_pss_md(sigmd)) {
    OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
    return 0;
  }
  int md_len = (int)EVP_MD_size(sigmd);
  if (saltlen == -1) {
    saltlen = md_len;
  } else if (saltlen != md_len) {
    OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
    return 0;
  }

  int ret = 0;
  ASN1_STRING *os = NULL;
  RSA_PSS_PARAMS *pss = RSA_PSS_PARAMS_new();
  if (!pss) {
    goto err;
  }

  // The DEFAULT value is 20, but this does not match any supported digest.
  assert(saltlen != 20);
  pss->saltLength = ASN1_INTEGER_new();
  if (!pss->saltLength ||  //
      !ASN1_INTEGER_set_int64(pss->saltLength, saltlen)) {
    goto err;
  }

  if (!rsa_md_to_algor(&pss->hashAlgorithm, sigmd) ||
      !rsa_md_to_mgf1(&pss->maskGenAlgorithm, mgf1md)) {
    goto err;
  }

  // Finally create string with pss parameter encoding.
  if (!ASN1_item_pack(pss, ASN1_ITEM_rptr(RSA_PSS_PARAMS), &os)) {
    goto err;
  }

  if (!X509_ALGOR_set0(algor, OBJ_nid2obj(NID_rsassaPss), V_ASN1_SEQUENCE,
                       os)) {
    goto err;
  }
  os = NULL;
  ret = 1;

err:
  RSA_PSS_PARAMS_free(pss);
  ASN1_STRING_free(os);
  return ret;
}

int x509_rsa_pss_to_ctx(EVP_MD_CTX *ctx, const X509_ALGOR *sigalg,
                        EVP_PKEY *pkey) {
  assert(OBJ_obj2nid(sigalg->algorithm) == NID_rsassaPss);

  // Decode PSS parameters
  int ret = 0;
  RSA_PSS_PARAMS *pss = rsa_pss_decode(sigalg);

  {
    if (pss == NULL) {
      OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
      goto err;
    }

    const EVP_MD *mgf1md = rsa_mgf1_to_md(pss->maskGenAlgorithm);
    const EVP_MD *md = rsa_algor_to_md(pss->hashAlgorithm);
    if (mgf1md == NULL || md == NULL) {
      goto err;
    }

    // We require the MGF-1 and signing hashes to match.
    if (mgf1md != md) {
      OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
      goto err;
    }

    // We require the salt length be the hash length. The DEFAULT value is 20,
    // but this does not match any supported salt length.
    uint64_t salt_len = 0;
    if (pss->saltLength == NULL ||
        !ASN1_INTEGER_get_uint64(&salt_len, pss->saltLength) ||
        salt_len != EVP_MD_size(md)) {
      OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
      goto err;
    }
    assert(salt_len <= INT_MAX);

    // The trailer field must be 1 (0xbc). This value is DEFAULT, so the
    // structure is required to omit it in DER. Although a syntax error, we also
    // tolerate an explicitly-encoded value. See the certificates in
    // cl/362617931.
    if (pss->trailerField != NULL && ASN1_INTEGER_get(pss->trailerField) != 1) {
      OPENSSL_PUT_ERROR(X509, X509_R_INVALID_PSS_PARAMETERS);
      goto err;
    }

    EVP_PKEY_CTX *pctx;
    if (!EVP_DigestVerifyInit(ctx, &pctx, md, NULL, pkey) ||
        !EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING) ||
        !EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, (int)salt_len) ||
        !EVP_PKEY_CTX_set_rsa_mgf1_md(pctx, mgf1md)) {
      goto err;
    }

    ret = 1;
  }

err:
  RSA_PSS_PARAMS_free(pss);
  return ret;
}

int x509_print_rsa_pss_params(BIO *bp, const X509_ALGOR *sigalg, int indent,
                              ASN1_PCTX *pctx) {
  assert(OBJ_obj2nid(sigalg->algorithm) == NID_rsassaPss);

  int rv = 0;
  X509_ALGOR *maskHash = NULL;
  RSA_PSS_PARAMS *pss = rsa_pss_decode(sigalg);
  if (!pss) {
    if (BIO_puts(bp, " (INVALID PSS PARAMETERS)\n") <= 0) {
      goto err;
    }
    rv = 1;
    goto err;
  }

  if (BIO_puts(bp, "\n") <= 0 ||       //
      !BIO_indent(bp, indent, 128) ||  //
      BIO_puts(bp, "Hash Algorithm: ") <= 0) {
    goto err;
  }

  if (pss->hashAlgorithm) {
    if (i2a_ASN1_OBJECT(bp, pss->hashAlgorithm->algorithm) <= 0) {
      goto err;
    }
  } else if (BIO_puts(bp, "sha1 (default)") <= 0) {
    goto err;
  }

  if (BIO_puts(bp, "\n") <= 0 ||       //
      !BIO_indent(bp, indent, 128) ||  //
      BIO_puts(bp, "Mask Algorithm: ") <= 0) {
    goto err;
  }

  if (pss->maskGenAlgorithm) {
    maskHash = rsa_mgf1_decode(pss->maskGenAlgorithm);
    if (maskHash == NULL) {
      if (BIO_puts(bp, "INVALID") <= 0) {
        goto err;
      }
    } else {
      if (i2a_ASN1_OBJECT(bp, pss->maskGenAlgorithm->algorithm) <= 0 ||
          BIO_puts(bp, " with ") <= 0 ||
          i2a_ASN1_OBJECT(bp, maskHash->algorithm) <= 0) {
        goto err;
      }
    }
  } else if (BIO_puts(bp, "mgf1 with sha1 (default)") <= 0) {
    goto err;
  }
  BIO_puts(bp, "\n");

  if (!BIO_indent(bp, indent, 128) ||  //
      BIO_puts(bp, "Salt Length: 0x") <= 0) {
    goto err;
  }

  if (pss->saltLength) {
    if (i2a_ASN1_INTEGER(bp, pss->saltLength) <= 0) {
      goto err;
    }
  } else if (BIO_puts(bp, "14 (default)") <= 0) {
    goto err;
  }
  BIO_puts(bp, "\n");

  if (!BIO_indent(bp, indent, 128) ||  //
      BIO_puts(bp, "Trailer Field: 0x") <= 0) {
    goto err;
  }

  if (pss->trailerField) {
    if (i2a_ASN1_INTEGER(bp, pss->trailerField) <= 0) {
      goto err;
    }
  } else if (BIO_puts(bp, "BC (default)") <= 0) {
    goto err;
  }
  BIO_puts(bp, "\n");

  rv = 1;

err:
  RSA_PSS_PARAMS_free(pss);
  X509_ALGOR_free(maskHash);
  return rv;
}
