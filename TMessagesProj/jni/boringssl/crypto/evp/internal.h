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

#ifndef OPENSSL_HEADER_EVP_INTERNAL_H
#define OPENSSL_HEADER_EVP_INTERNAL_H

#include <openssl/base.h>

#if defined(__cplusplus)
extern "C" {
#endif


/* These values are flags for EVP_PKEY_ASN1_METHOD.flags. */

/* ASN1_PKEY_SIGPARAM_NULL controls whether the default behavior of
 * EVP_DigestSignAlgorithm writes an explicit NULL parameter in the
 * AlgorithmIdentifier. */
#define ASN1_PKEY_SIGPARAM_NULL 0x1

/* evp_digest_sign_algorithm_result_t is the return value of the
 * digest_sign_algorithm function in EVP_PKEY_ASN1_METHOD. */
typedef enum {
  /* EVP_DIGEST_SIGN_ALGORITHM_ERROR signals an error. */
  EVP_DIGEST_SIGN_ALGORITHM_ERROR = 0,
  /* EVP_DIGEST_SIGN_ALGORITHM_SUCCESS signals that the parameters were
   * serialized in the AlgorithmIdentifier. */
  EVP_DIGEST_SIGN_ALGORITHM_SUCCESS = 1,
  /* EVP_DIGEST_SIGN_ALGORITHM_DEFAULT signals that the parameters are
   * serialized using the default behavior. */
  EVP_DIGEST_SIGN_ALGORITHM_DEFAULT = 2,
} evp_digest_sign_algorithm_result_t;

struct evp_pkey_asn1_method_st {
  int pkey_id;
  int pkey_base_id;
  unsigned long pkey_flags;

  const char *pem_str;

  int (*pub_decode)(EVP_PKEY *pk, X509_PUBKEY *pub);
  int (*pub_encode)(X509_PUBKEY *pub, const EVP_PKEY *pk);
  int (*pub_cmp)(const EVP_PKEY *a, const EVP_PKEY *b);
  int (*pub_print)(BIO *out, const EVP_PKEY *pkey, int indent, ASN1_PCTX *pctx);

  int (*priv_decode)(EVP_PKEY *pk, PKCS8_PRIV_KEY_INFO *p8inf);
  int (*priv_encode)(PKCS8_PRIV_KEY_INFO *p8, const EVP_PKEY *pk);
  int (*priv_print)(BIO *out, const EVP_PKEY *pkey, int indent,
                    ASN1_PCTX *pctx);

  /* pkey_opaque returns 1 if the |pk| is opaque. Opaque keys are backed by
   * custom implementations which do not expose key material and parameters.*/
  int (*pkey_opaque)(const EVP_PKEY *pk);

  /* pkey_supports_digest returns one if |pkey| supports digests of
   * type |md|. This is intended for use with EVP_PKEYs backing custom
   * implementations which can't sign all digests. If null, it is
   * assumed that all digests are supported. */
  int (*pkey_supports_digest)(const EVP_PKEY *pkey, const EVP_MD *md);

  int (*pkey_size)(const EVP_PKEY *pk);
  int (*pkey_bits)(const EVP_PKEY *pk);

  int (*param_decode)(EVP_PKEY *pkey, const uint8_t **pder, int derlen);
  int (*param_encode)(const EVP_PKEY *pkey, uint8_t **pder);
  int (*param_missing)(const EVP_PKEY *pk);
  int (*param_copy)(EVP_PKEY *to, const EVP_PKEY *from);
  int (*param_cmp)(const EVP_PKEY *a, const EVP_PKEY *b);
  int (*param_print)(BIO *out, const EVP_PKEY *pkey, int indent,
                     ASN1_PCTX *pctx);
  int (*sig_print)(BIO *out, const X509_ALGOR *sigalg, const ASN1_STRING *sig,
                   int indent, ASN1_PCTX *pctx);


  void (*pkey_free)(EVP_PKEY *pkey);

  /* Legacy functions for old PEM */

  int (*old_priv_decode)(EVP_PKEY *pkey, const uint8_t **pder,
                         int derlen);
  int (*old_priv_encode)(const EVP_PKEY *pkey, uint8_t **pder);

  /* Converting parameters to/from AlgorithmIdentifier (X509_ALGOR). */
  int (*digest_verify_init_from_algorithm)(EVP_MD_CTX *ctx,
                                           X509_ALGOR *algor,
                                           EVP_PKEY *pkey);
  evp_digest_sign_algorithm_result_t (*digest_sign_algorithm)(
      EVP_MD_CTX *ctx,
      X509_ALGOR *algor);

} /* EVP_PKEY_ASN1_METHOD */;


typedef int EVP_PKEY_gen_cb(EVP_PKEY_CTX *ctx);

#define EVP_PKEY_OP_UNDEFINED 0
#define EVP_PKEY_OP_PARAMGEN (1 << 1)
#define EVP_PKEY_OP_KEYGEN (1 << 2)
#define EVP_PKEY_OP_SIGN (1 << 3)
#define EVP_PKEY_OP_VERIFY (1 << 4)
#define EVP_PKEY_OP_VERIFYRECOVER (1 << 5)
#define EVP_PKEY_OP_ENCRYPT (1 << 6)
#define EVP_PKEY_OP_DECRYPT (1 << 7)
#define EVP_PKEY_OP_DERIVE (1 << 8)

#define EVP_PKEY_OP_TYPE_SIG                                           \
  (EVP_PKEY_OP_SIGN | EVP_PKEY_OP_VERIFY | EVP_PKEY_OP_VERIFYRECOVER)

#define EVP_PKEY_OP_TYPE_CRYPT (EVP_PKEY_OP_ENCRYPT | EVP_PKEY_OP_DECRYPT)

#define EVP_PKEY_OP_TYPE_NOGEN \
  (EVP_PKEY_OP_SIG | EVP_PKEY_OP_CRYPT | EVP_PKEY_OP_DERIVE)

#define EVP_PKEY_OP_TYPE_GEN (EVP_PKEY_OP_PARAMGEN | EVP_PKEY_OP_KEYGEN)

/* EVP_PKEY_CTX_ctrl performs |cmd| on |ctx|. The |keytype| and |optype|
 * arguments can be -1 to specify that any type and operation are acceptable,
 * otherwise |keytype| must match the type of |ctx| and the bits of |optype|
 * must intersect the operation flags set on |ctx|.
 *
 * The |p1| and |p2| arguments depend on the value of |cmd|.
 *
 * It returns one on success and zero on error. */
OPENSSL_EXPORT int EVP_PKEY_CTX_ctrl(EVP_PKEY_CTX *ctx, int keytype, int optype,
                                     int cmd, int p1, void *p2);

#define EVP_PKEY_CTRL_MD 1
#define EVP_PKEY_CTRL_GET_MD 2

/* EVP_PKEY_CTRL_PEER_KEY is called with different values of |p1|:
 *   0: Is called from |EVP_PKEY_derive_set_peer| and |p2| contains a peer key.
 *      If the return value is <= 0, the key is rejected.
 *   1: Is called at the end of |EVP_PKEY_derive_set_peer| and |p2| contains a
 *      peer key. If the return value is <= 0, the key is rejected.
 *   2: Is called with |p2| == NULL to test whether the peer's key was used.
 *      (EC)DH always return one in this case.
 *   3: Is called with |p2| == NULL to set whether the peer's key was used.
 *      (EC)DH always return one in this case. This was only used for GOST. */
#define EVP_PKEY_CTRL_PEER_KEY 3

/* EVP_PKEY_ALG_CTRL is the base value from which key-type specific ctrl
 * commands are numbered. */
#define EVP_PKEY_ALG_CTRL 0x1000

#define EVP_PKEY_CTRL_RSA_PADDING (EVP_PKEY_ALG_CTRL + 1)
#define EVP_PKEY_CTRL_GET_RSA_PADDING (EVP_PKEY_ALG_CTRL + 2)
#define EVP_PKEY_CTRL_RSA_PSS_SALTLEN (EVP_PKEY_ALG_CTRL + 3)
#define EVP_PKEY_CTRL_GET_RSA_PSS_SALTLEN (EVP_PKEY_ALG_CTRL + 4)
#define EVP_PKEY_CTRL_RSA_KEYGEN_BITS (EVP_PKEY_ALG_CTRL + 5)
#define EVP_PKEY_CTRL_RSA_KEYGEN_PUBEXP	(EVP_PKEY_ALG_CTRL + 6)
#define EVP_PKEY_CTRL_RSA_OAEP_MD (EVP_PKEY_ALG_CTRL + 7)
#define EVP_PKEY_CTRL_GET_RSA_OAEP_MD (EVP_PKEY_ALG_CTRL + 8)
#define EVP_PKEY_CTRL_RSA_MGF1_MD (EVP_PKEY_ALG_CTRL + 9)
#define EVP_PKEY_CTRL_GET_RSA_MGF1_MD (EVP_PKEY_ALG_CTRL + 10)
#define EVP_PKEY_CTRL_RSA_OAEP_LABEL (EVP_PKEY_ALG_CTRL + 11)
#define EVP_PKEY_CTRL_GET_RSA_OAEP_LABEL (EVP_PKEY_ALG_CTRL + 12)

#define EVP_PKEY_CTRL_EC_PARAMGEN_CURVE_NID (EVP_PKEY_ALG_CTRL + 1)

struct evp_pkey_ctx_st {
  /* Method associated with this operation */
  const EVP_PKEY_METHOD *pmeth;
  /* Engine that implements this method or NULL if builtin */
  ENGINE *engine;
  /* Key: may be NULL */
  EVP_PKEY *pkey;
  /* Peer key for key agreement, may be NULL */
  EVP_PKEY *peerkey;
  /* operation contains one of the |EVP_PKEY_OP_*| values. */
  int operation;
  /* Algorithm specific data */
  void *data;
  /* Application specific data */
  void *app_data;
} /* EVP_PKEY_CTX */;

struct evp_pkey_method_st {
  int pkey_id;
  int flags;

  int (*init)(EVP_PKEY_CTX *ctx);
  int (*copy)(EVP_PKEY_CTX *dst, EVP_PKEY_CTX *src);
  void (*cleanup)(EVP_PKEY_CTX *ctx);

  int (*paramgen_init)(EVP_PKEY_CTX *ctx);
  int (*paramgen)(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey);

  int (*keygen_init)(EVP_PKEY_CTX *ctx);
  int (*keygen)(EVP_PKEY_CTX *ctx, EVP_PKEY *pkey);

  int (*sign_init)(EVP_PKEY_CTX *ctx);
  int (*sign)(EVP_PKEY_CTX *ctx, uint8_t *sig, size_t *siglen,
              const uint8_t *tbs, size_t tbslen);

  int (*verify_init)(EVP_PKEY_CTX *ctx);
  int (*verify)(EVP_PKEY_CTX *ctx, const uint8_t *sig, size_t siglen,
                const uint8_t *tbs, size_t tbslen);

  int (*encrypt_init)(EVP_PKEY_CTX *ctx);
  int (*encrypt)(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                 const uint8_t *in, size_t inlen);

  int (*decrypt_init)(EVP_PKEY_CTX *ctx);
  int (*decrypt)(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *outlen,
                 const uint8_t *in, size_t inlen);

  int (*derive_init)(EVP_PKEY_CTX *ctx);
  int (*derive)(EVP_PKEY_CTX *ctx, uint8_t *key, size_t *keylen);

  int (*ctrl)(EVP_PKEY_CTX *ctx, int type, int p1, void *p2);
} /* EVP_PKEY_METHOD */;


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_EVP_INTERNAL_H */
