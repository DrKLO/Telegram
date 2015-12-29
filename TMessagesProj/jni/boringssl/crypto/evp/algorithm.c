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

#include <openssl/evp.h>

#include <assert.h>

#include <openssl/asn1.h>
#include <openssl/err.h>
#include <openssl/obj.h>
#include <openssl/x509.h>

#include "internal.h"


int EVP_DigestSignAlgorithm(EVP_MD_CTX *ctx, X509_ALGOR *algor) {
  const EVP_MD *digest;
  EVP_PKEY *pkey;
  int sign_nid, paramtype;

  digest = EVP_MD_CTX_md(ctx);
  pkey = EVP_PKEY_CTX_get0_pkey(ctx->pctx);
  if (!digest || !pkey) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_CONTEXT_NOT_INITIALISED);
    return 0;
  }

  if (pkey->ameth->digest_sign_algorithm) {
    switch (pkey->ameth->digest_sign_algorithm(ctx, algor)) {
      case EVP_DIGEST_SIGN_ALGORITHM_ERROR:
        return 0;
      case EVP_DIGEST_SIGN_ALGORITHM_SUCCESS:
        return 1;
      case EVP_DIGEST_SIGN_ALGORITHM_DEFAULT:
        /* Use default behavior. */
        break;
      default:
        assert(0);
    }
  }

  /* Default behavior: look up the OID for the algorithm/hash pair and encode
   * that. */
  if (!OBJ_find_sigid_by_algs(&sign_nid, EVP_MD_type(digest),
                              pkey->ameth->pkey_id)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DIGEST_AND_KEY_TYPE_NOT_SUPPORTED);
    return 0;
  }

  if (pkey->ameth->pkey_flags & ASN1_PKEY_SIGPARAM_NULL) {
    paramtype = V_ASN1_NULL;
  } else {
    paramtype = V_ASN1_UNDEF;
  }

  X509_ALGOR_set0(algor, OBJ_nid2obj(sign_nid), paramtype, NULL);
  return 1;
}

int EVP_DigestVerifyInitFromAlgorithm(EVP_MD_CTX *ctx,
                                      X509_ALGOR *algor,
                                      EVP_PKEY *pkey) {
  int digest_nid, pkey_nid;
  const EVP_PKEY_ASN1_METHOD *ameth;
  const EVP_MD *digest;

  /* Convert signature OID into digest and public key OIDs */
  if (!OBJ_find_sigid_algs(OBJ_obj2nid(algor->algorithm), &digest_nid,
                           &pkey_nid)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNKNOWN_SIGNATURE_ALGORITHM);
    return 0;
  }

  /* Check public key OID matches public key type */
  ameth = EVP_PKEY_asn1_find(NULL, pkey_nid);
  if (ameth == NULL || ameth->pkey_id != pkey->ameth->pkey_id) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_WRONG_PUBLIC_KEY_TYPE);
    return 0;
  }

  /* NID_undef signals that there are custom parameters to set. */
  if (digest_nid == NID_undef) {
    if (!pkey->ameth || !pkey->ameth->digest_verify_init_from_algorithm) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_UNKNOWN_SIGNATURE_ALGORITHM);
      return 0;
    }

    return pkey->ameth->digest_verify_init_from_algorithm(ctx, algor, pkey);
  }

  /* Otherwise, initialize with the digest from the OID. */
  digest = EVP_get_digestbynid(digest_nid);
  if (digest == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNKNOWN_MESSAGE_DIGEST_ALGORITHM);
    return 0;
  }

  return EVP_DigestVerifyInit(ctx, NULL, digest, NULL, pkey);
}

