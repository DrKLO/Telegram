/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2006.
 */
/* ====================================================================
 * Copyright (c) 2006 The OpenSSL Project.  All rights reserved.
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
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    licensing@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
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
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/evp.h>

#include <openssl/asn1.h>
#include <openssl/asn1t.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/rsa.h>
#include <openssl/x509.h>

#include "../rsa/internal.h"
#include "internal.h"


static int rsa_pub_encode(X509_PUBKEY *pk, const EVP_PKEY *pkey) {
  uint8_t *encoded;
  size_t encoded_len;
  if (!RSA_public_key_to_bytes(&encoded, &encoded_len, pkey->pkey.rsa)) {
    return 0;
  }

  if (!X509_PUBKEY_set0_param(pk, OBJ_nid2obj(EVP_PKEY_RSA), V_ASN1_NULL, NULL,
                              encoded, encoded_len)) {
    OPENSSL_free(encoded);
    return 0;
  }

  return 1;
}

static int rsa_pub_decode(EVP_PKEY *pkey, X509_PUBKEY *pubkey) {
  const uint8_t *p;
  int pklen;
  RSA *rsa;

  if (!X509_PUBKEY_get0_param(NULL, &p, &pklen, NULL, pubkey)) {
    return 0;
  }
  rsa = RSA_public_key_from_bytes(p, pklen);
  if (rsa == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_RSA_LIB);
    return 0;
  }
  EVP_PKEY_assign_RSA(pkey, rsa);
  return 1;
}

static int rsa_pub_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  return BN_cmp(b->pkey.rsa->n, a->pkey.rsa->n) == 0 &&
         BN_cmp(b->pkey.rsa->e, a->pkey.rsa->e) == 0;
}

static int rsa_priv_encode(PKCS8_PRIV_KEY_INFO *p8, const EVP_PKEY *pkey) {
  uint8_t *encoded;
  size_t encoded_len;
  if (!RSA_private_key_to_bytes(&encoded, &encoded_len, pkey->pkey.rsa)) {
    return 0;
  }

  /* TODO(fork): const correctness in next line. */
  if (!PKCS8_pkey_set0(p8, (ASN1_OBJECT *)OBJ_nid2obj(NID_rsaEncryption), 0,
                       V_ASN1_NULL, NULL, encoded, encoded_len)) {
    OPENSSL_free(encoded);
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  return 1;
}

static int rsa_priv_decode(EVP_PKEY *pkey, PKCS8_PRIV_KEY_INFO *p8) {
  const uint8_t *p;
  int pklen;
  if (!PKCS8_pkey_get0(NULL, &p, &pklen, NULL, p8)) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  RSA *rsa = RSA_private_key_from_bytes(p, pklen);
  if (rsa == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_RSA_LIB);
    return 0;
  }

  EVP_PKEY_assign_RSA(pkey, rsa);
  return 1;
}

static int rsa_opaque(const EVP_PKEY *pkey) {
  return RSA_is_opaque(pkey->pkey.rsa);
}

static int rsa_supports_digest(const EVP_PKEY *pkey, const EVP_MD *md) {
  return RSA_supports_digest(pkey->pkey.rsa, md);
}

static int int_rsa_size(const EVP_PKEY *pkey) {
  return RSA_size(pkey->pkey.rsa);
}

static int rsa_bits(const EVP_PKEY *pkey) {
  return BN_num_bits(pkey->pkey.rsa->n);
}

static void int_rsa_free(EVP_PKEY *pkey) { RSA_free(pkey->pkey.rsa); }

static void update_buflen(const BIGNUM *b, size_t *pbuflen) {
  size_t i;

  if (!b) {
    return;
  }

  i = BN_num_bytes(b);
  if (*pbuflen < i) {
    *pbuflen = i;
  }
}

static int do_rsa_print(BIO *out, const RSA *rsa, int off,
                        int include_private) {
  char *str;
  const char *s;
  uint8_t *m = NULL;
  int ret = 0, mod_len = 0;
  size_t buf_len = 0;

  update_buflen(rsa->n, &buf_len);
  update_buflen(rsa->e, &buf_len);

  if (include_private) {
    update_buflen(rsa->d, &buf_len);
    update_buflen(rsa->p, &buf_len);
    update_buflen(rsa->q, &buf_len);
    update_buflen(rsa->dmp1, &buf_len);
    update_buflen(rsa->dmq1, &buf_len);
    update_buflen(rsa->iqmp, &buf_len);

    if (rsa->additional_primes != NULL) {
      size_t i;

      for (i = 0; i < sk_RSA_additional_prime_num(rsa->additional_primes);
           i++) {
        const RSA_additional_prime *ap =
            sk_RSA_additional_prime_value(rsa->additional_primes, i);
        update_buflen(ap->prime, &buf_len);
        update_buflen(ap->exp, &buf_len);
        update_buflen(ap->coeff, &buf_len);
      }
    }
  }

  m = (uint8_t *)OPENSSL_malloc(buf_len + 10);
  if (m == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (rsa->n != NULL) {
    mod_len = BN_num_bits(rsa->n);
  }

  if (!BIO_indent(out, off, 128)) {
    goto err;
  }

  if (include_private && rsa->d) {
    if (BIO_printf(out, "Private-Key: (%d bit)\n", mod_len) <= 0) {
      goto err;
    }
    str = "modulus:";
    s = "publicExponent:";
  } else {
    if (BIO_printf(out, "Public-Key: (%d bit)\n", mod_len) <= 0) {
      goto err;
    }
    str = "Modulus:";
    s = "Exponent:";
  }
  if (!ASN1_bn_print(out, str, rsa->n, m, off) ||
      !ASN1_bn_print(out, s, rsa->e, m, off)) {
    goto err;
  }

  if (include_private) {
    if (!ASN1_bn_print(out, "privateExponent:", rsa->d, m, off) ||
        !ASN1_bn_print(out, "prime1:", rsa->p, m, off) ||
        !ASN1_bn_print(out, "prime2:", rsa->q, m, off) ||
        !ASN1_bn_print(out, "exponent1:", rsa->dmp1, m, off) ||
        !ASN1_bn_print(out, "exponent2:", rsa->dmq1, m, off) ||
        !ASN1_bn_print(out, "coefficient:", rsa->iqmp, m, off)) {
      goto err;
    }

    if (rsa->additional_primes != NULL &&
        sk_RSA_additional_prime_num(rsa->additional_primes) > 0) {
      size_t i;

      if (BIO_printf(out, "otherPrimeInfos:\n") <= 0) {
        goto err;
      }
      for (i = 0; i < sk_RSA_additional_prime_num(rsa->additional_primes);
           i++) {
        const RSA_additional_prime *ap =
            sk_RSA_additional_prime_value(rsa->additional_primes, i);

        if (BIO_printf(out, "otherPrimeInfo (prime %u):\n",
                       (unsigned)(i + 3)) <= 0 ||
            !ASN1_bn_print(out, "prime:", ap->prime, m, off) ||
            !ASN1_bn_print(out, "exponent:", ap->exp, m, off) ||
            !ASN1_bn_print(out, "coeff:", ap->coeff, m, off)) {
          goto err;
        }
      }
    }
  }
  ret = 1;

err:
  OPENSSL_free(m);
  return ret;
}

static int rsa_pub_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                         ASN1_PCTX *ctx) {
  return do_rsa_print(bp, pkey->pkey.rsa, indent, 0);
}


static int rsa_priv_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *ctx) {
  return do_rsa_print(bp, pkey->pkey.rsa, indent, 1);
}

/* Given an MGF1 Algorithm ID decode to an Algorithm Identifier */
static X509_ALGOR *rsa_mgf1_decode(X509_ALGOR *alg) {
  const uint8_t *p;
  int plen;

  if (alg == NULL ||
      OBJ_obj2nid(alg->algorithm) != NID_mgf1 ||
      alg->parameter->type != V_ASN1_SEQUENCE) {
    return NULL;
  }

  p = alg->parameter->value.sequence->data;
  plen = alg->parameter->value.sequence->length;
  return d2i_X509_ALGOR(NULL, &p, plen);
}

static RSA_PSS_PARAMS *rsa_pss_decode(const X509_ALGOR *alg,
                                      X509_ALGOR **pmaskHash) {
  const uint8_t *p;
  int plen;
  RSA_PSS_PARAMS *pss;

  *pmaskHash = NULL;

  if (!alg->parameter || alg->parameter->type != V_ASN1_SEQUENCE) {
    return NULL;
  }
  p = alg->parameter->value.sequence->data;
  plen = alg->parameter->value.sequence->length;
  pss = d2i_RSA_PSS_PARAMS(NULL, &p, plen);

  if (!pss) {
    return NULL;
  }

  *pmaskHash = rsa_mgf1_decode(pss->maskGenAlgorithm);

  return pss;
}

static int rsa_pss_param_print(BIO *bp, RSA_PSS_PARAMS *pss,
                               X509_ALGOR *maskHash, int indent) {
  int rv = 0;

  if (!pss) {
    if (BIO_puts(bp, " (INVALID PSS PARAMETERS)\n") <= 0) {
      return 0;
    }
    return 1;
  }

  if (BIO_puts(bp, "\n") <= 0 ||
      !BIO_indent(bp, indent, 128) ||
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

  if (BIO_puts(bp, "\n") <= 0 ||
      !BIO_indent(bp, indent, 128) ||
      BIO_puts(bp, "Mask Algorithm: ") <= 0) {
    goto err;
  }

  if (pss->maskGenAlgorithm) {
    if (i2a_ASN1_OBJECT(bp, pss->maskGenAlgorithm->algorithm) <= 0 ||
        BIO_puts(bp, " with ") <= 0) {
      goto err;
    }

    if (maskHash) {
      if (i2a_ASN1_OBJECT(bp, maskHash->algorithm) <= 0) {
        goto err;
      }
    } else if (BIO_puts(bp, "INVALID") <= 0) {
      goto err;
    }
  } else if (BIO_puts(bp, "mgf1 with sha1 (default)") <= 0) {
    goto err;
  }
  BIO_puts(bp, "\n");

  if (!BIO_indent(bp, indent, 128) ||
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

  if (!BIO_indent(bp, indent, 128) ||
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
  return rv;
}

static int rsa_sig_print(BIO *bp, const X509_ALGOR *sigalg,
                         const ASN1_STRING *sig, int indent, ASN1_PCTX *pctx) {
  if (OBJ_obj2nid(sigalg->algorithm) == NID_rsassaPss) {
    int rv;
    RSA_PSS_PARAMS *pss;
    X509_ALGOR *maskHash;

    pss = rsa_pss_decode(sigalg, &maskHash);
    rv = rsa_pss_param_print(bp, pss, maskHash, indent);
    RSA_PSS_PARAMS_free(pss);
    X509_ALGOR_free(maskHash);
    if (!rv) {
      return 0;
    }
  } else if (!sig && BIO_puts(bp, "\n") <= 0) {
    return 0;
  }

  if (sig) {
    return X509_signature_dump(bp, sig, indent);
  }
  return 1;
}

static int old_rsa_priv_decode(EVP_PKEY *pkey, const uint8_t **pder,
                               int derlen) {
  RSA *rsa = d2i_RSAPrivateKey(NULL, pder, derlen);
  if (rsa == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_RSA_LIB);
    return 0;
  }
  EVP_PKEY_assign_RSA(pkey, rsa);
  return 1;
}

static int old_rsa_priv_encode(const EVP_PKEY *pkey, uint8_t **pder) {
  return i2d_RSAPrivateKey(pkey->pkey.rsa, pder);
}

/* allocate and set algorithm ID from EVP_MD, default SHA1 */
static int rsa_md_to_algor(X509_ALGOR **palg, const EVP_MD *md) {
  if (EVP_MD_type(md) == NID_sha1) {
    return 1;
  }
  *palg = X509_ALGOR_new();
  if (!*palg) {
    return 0;
  }
  X509_ALGOR_set_md(*palg, md);
  return 1;
}

/* Allocate and set MGF1 algorithm ID from EVP_MD */
static int rsa_md_to_mgf1(X509_ALGOR **palg, const EVP_MD *mgf1md) {
  X509_ALGOR *algtmp = NULL;
  ASN1_STRING *stmp = NULL;
  *palg = NULL;

  if (EVP_MD_type(mgf1md) == NID_sha1) {
    return 1;
  }
  /* need to embed algorithm ID inside another */
  if (!rsa_md_to_algor(&algtmp, mgf1md) ||
      !ASN1_item_pack(algtmp, ASN1_ITEM_rptr(X509_ALGOR), &stmp)) {
    goto err;
  }
  *palg = X509_ALGOR_new();
  if (!*palg) {
    goto err;
  }
  X509_ALGOR_set0(*palg, OBJ_nid2obj(NID_mgf1), V_ASN1_SEQUENCE, stmp);
  stmp = NULL;

err:
  ASN1_STRING_free(stmp);
  X509_ALGOR_free(algtmp);
  if (*palg) {
    return 1;
  }

  return 0;
}

/* convert algorithm ID to EVP_MD, default SHA1 */
static const EVP_MD *rsa_algor_to_md(X509_ALGOR *alg) {
  const EVP_MD *md;
  if (!alg) {
    return EVP_sha1();
  }
  md = EVP_get_digestbyobj(alg->algorithm);
  if (md == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNKNOWN_DIGEST);
  }
  return md;
}

/* convert MGF1 algorithm ID to EVP_MD, default SHA1 */
static const EVP_MD *rsa_mgf1_to_md(X509_ALGOR *alg, X509_ALGOR *maskHash) {
  const EVP_MD *md;
  if (!alg) {
    return EVP_sha1();
  }
  /* Check mask and lookup mask hash algorithm */
  if (OBJ_obj2nid(alg->algorithm) != NID_mgf1) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_MASK_ALGORITHM);
    return NULL;
  }
  if (!maskHash) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_MASK_PARAMETER);
    return NULL;
  }
  md = EVP_get_digestbyobj(maskHash->algorithm);
  if (md == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNKNOWN_MASK_DIGEST);
    return NULL;
  }
  return md;
}

/* rsa_ctx_to_pss converts EVP_PKEY_CTX in PSS mode into corresponding
 * algorithm parameter, suitable for setting as an AlgorithmIdentifier. */
static ASN1_STRING *rsa_ctx_to_pss(EVP_PKEY_CTX *pkctx) {
  const EVP_MD *sigmd, *mgf1md;
  RSA_PSS_PARAMS *pss = NULL;
  ASN1_STRING *os = NULL;
  EVP_PKEY *pk = EVP_PKEY_CTX_get0_pkey(pkctx);
  int saltlen, rv = 0;

  if (!EVP_PKEY_CTX_get_signature_md(pkctx, &sigmd) ||
      !EVP_PKEY_CTX_get_rsa_mgf1_md(pkctx, &mgf1md) ||
      !EVP_PKEY_CTX_get_rsa_pss_saltlen(pkctx, &saltlen)) {
    goto err;
  }

  if (saltlen == -1) {
    saltlen = EVP_MD_size(sigmd);
  } else if (saltlen == -2) {
    saltlen = EVP_PKEY_size(pk) - EVP_MD_size(sigmd) - 2;
    if (((EVP_PKEY_bits(pk) - 1) & 0x7) == 0) {
      saltlen--;
    }
  } else {
    goto err;
  }

  pss = RSA_PSS_PARAMS_new();
  if (!pss) {
    goto err;
  }

  if (saltlen != 20) {
    pss->saltLength = ASN1_INTEGER_new();
    if (!pss->saltLength ||
        !ASN1_INTEGER_set(pss->saltLength, saltlen)) {
      goto err;
    }
  }

  if (!rsa_md_to_algor(&pss->hashAlgorithm, sigmd) ||
      !rsa_md_to_mgf1(&pss->maskGenAlgorithm, mgf1md)) {
    goto err;
  }

  /* Finally create string with pss parameter encoding. */
  if (!ASN1_item_pack(pss, ASN1_ITEM_rptr(RSA_PSS_PARAMS), &os)) {
    goto err;
  }
  rv = 1;

err:
  if (pss) {
    RSA_PSS_PARAMS_free(pss);
  }
  if (rv) {
    return os;
  }
  if (os) {
    ASN1_STRING_free(os);
  }
  return NULL;
}

/* From PSS AlgorithmIdentifier set public key parameters. */
static int rsa_pss_to_ctx(EVP_MD_CTX *ctx, X509_ALGOR *sigalg, EVP_PKEY *pkey) {
  int ret = 0;
  int saltlen;
  const EVP_MD *mgf1md = NULL, *md = NULL;
  RSA_PSS_PARAMS *pss;
  X509_ALGOR *maskHash;
  EVP_PKEY_CTX *pkctx;

  /* Sanity check: make sure it is PSS */
  if (OBJ_obj2nid(sigalg->algorithm) != NID_rsassaPss) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_SIGNATURE_TYPE);
    return 0;
  }
  /* Decode PSS parameters */
  pss = rsa_pss_decode(sigalg, &maskHash);
  if (pss == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_PSS_PARAMETERS);
    goto err;
  }

  mgf1md = rsa_mgf1_to_md(pss->maskGenAlgorithm, maskHash);
  if (!mgf1md) {
    goto err;
  }
  md = rsa_algor_to_md(pss->hashAlgorithm);
  if (!md) {
    goto err;
  }

  saltlen = 20;
  if (pss->saltLength) {
    saltlen = ASN1_INTEGER_get(pss->saltLength);

    /* Could perform more salt length sanity checks but the main
     * RSA routines will trap other invalid values anyway. */
    if (saltlen < 0) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_SALT_LENGTH);
      goto err;
    }
  }

  /* low-level routines support only trailer field 0xbc (value 1)
   * and PKCS#1 says we should reject any other value anyway. */
  if (pss->trailerField && ASN1_INTEGER_get(pss->trailerField) != 1) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_INVALID_TRAILER);
    goto err;
  }

  if (!EVP_DigestVerifyInit(ctx, &pkctx, md, NULL, pkey) ||
      !EVP_PKEY_CTX_set_rsa_padding(pkctx, RSA_PKCS1_PSS_PADDING) ||
      !EVP_PKEY_CTX_set_rsa_pss_saltlen(pkctx, saltlen) ||
      !EVP_PKEY_CTX_set_rsa_mgf1_md(pkctx, mgf1md)) {
    goto err;
  }

  ret = 1;

err:
  RSA_PSS_PARAMS_free(pss);
  if (maskHash) {
    X509_ALGOR_free(maskHash);
  }
  return ret;
}

/* Customised RSA AlgorithmIdentifier handling. This is called when a signature
 * is encountered requiring special handling. We currently only handle PSS. */
static int rsa_digest_verify_init_from_algorithm(EVP_MD_CTX *ctx,
                                                 X509_ALGOR *sigalg,
                                                 EVP_PKEY *pkey) {
  /* Sanity check: make sure it is PSS */
  if (OBJ_obj2nid(sigalg->algorithm) != NID_rsassaPss) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_SIGNATURE_TYPE);
    return 0;
  }
  return rsa_pss_to_ctx(ctx, sigalg, pkey);
}

static evp_digest_sign_algorithm_result_t rsa_digest_sign_algorithm(
    EVP_MD_CTX *ctx, X509_ALGOR *sigalg) {
  int pad_mode;
  EVP_PKEY_CTX *pkctx = ctx->pctx;
  if (!EVP_PKEY_CTX_get_rsa_padding(pkctx, &pad_mode)) {
    return EVP_DIGEST_SIGN_ALGORITHM_ERROR;
  }
  if (pad_mode == RSA_PKCS1_PSS_PADDING) {
    ASN1_STRING *os1 = rsa_ctx_to_pss(pkctx);
    if (!os1) {
      return EVP_DIGEST_SIGN_ALGORITHM_ERROR;
    }
    X509_ALGOR_set0(sigalg, OBJ_nid2obj(NID_rsassaPss), V_ASN1_SEQUENCE, os1);
    return EVP_DIGEST_SIGN_ALGORITHM_SUCCESS;
  }

  /* Other padding schemes use the default behavior. */
  return EVP_DIGEST_SIGN_ALGORITHM_DEFAULT;
}

const EVP_PKEY_ASN1_METHOD rsa_asn1_meth = {
  EVP_PKEY_RSA,
  EVP_PKEY_RSA,
  ASN1_PKEY_SIGPARAM_NULL,

  "RSA",

  rsa_pub_decode,
  rsa_pub_encode,
  rsa_pub_cmp,
  rsa_pub_print,

  rsa_priv_decode,
  rsa_priv_encode,
  rsa_priv_print,

  rsa_opaque,
  rsa_supports_digest,

  int_rsa_size,
  rsa_bits,

  0,0,0,0,0,0,

  rsa_sig_print,
  int_rsa_free,

  old_rsa_priv_decode,
  old_rsa_priv_encode,

  rsa_digest_verify_init_from_algorithm,
  rsa_digest_sign_algorithm,
};
