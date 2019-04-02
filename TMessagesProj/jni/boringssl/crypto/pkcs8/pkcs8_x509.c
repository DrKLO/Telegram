/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 1999.
 */
/* ====================================================================
 * Copyright (c) 1999 The OpenSSL Project.  All rights reserved.
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

#include <openssl/pkcs8.h>

#include <limits.h>

#include <openssl/asn1t.h>
#include <openssl/asn1.h>
#include <openssl/bio.h>
#include <openssl/buf.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/digest.h>
#include <openssl/hmac.h>
#include <openssl/mem.h>
#include <openssl/x509.h>

#include "internal.h"
#include "../bytestring/internal.h"
#include "../internal.h"


// Minor tweak to operation: zero private key data
static int pkey_cb(int operation, ASN1_VALUE **pval, const ASN1_ITEM *it,
                   void *exarg) {
  // Since the structure must still be valid use ASN1_OP_FREE_PRE
  if (operation == ASN1_OP_FREE_PRE) {
    PKCS8_PRIV_KEY_INFO *key = (PKCS8_PRIV_KEY_INFO *)*pval;
    if (key->pkey && key->pkey->type == V_ASN1_OCTET_STRING &&
        key->pkey->value.octet_string) {
      OPENSSL_cleanse(key->pkey->value.octet_string->data,
                      key->pkey->value.octet_string->length);
    }
  }
  return 1;
}

ASN1_SEQUENCE_cb(PKCS8_PRIV_KEY_INFO, pkey_cb) = {
  ASN1_SIMPLE(PKCS8_PRIV_KEY_INFO, version, ASN1_INTEGER),
  ASN1_SIMPLE(PKCS8_PRIV_KEY_INFO, pkeyalg, X509_ALGOR),
  ASN1_SIMPLE(PKCS8_PRIV_KEY_INFO, pkey, ASN1_ANY),
  ASN1_IMP_SET_OF_OPT(PKCS8_PRIV_KEY_INFO, attributes, X509_ATTRIBUTE, 0)
} ASN1_SEQUENCE_END_cb(PKCS8_PRIV_KEY_INFO, PKCS8_PRIV_KEY_INFO)

IMPLEMENT_ASN1_FUNCTIONS(PKCS8_PRIV_KEY_INFO)

EVP_PKEY *EVP_PKCS82PKEY(PKCS8_PRIV_KEY_INFO *p8) {
  uint8_t *der = NULL;
  int der_len = i2d_PKCS8_PRIV_KEY_INFO(p8, &der);
  if (der_len < 0) {
    return NULL;
  }

  CBS cbs;
  CBS_init(&cbs, der, (size_t)der_len);
  EVP_PKEY *ret = EVP_parse_private_key(&cbs);
  if (ret == NULL || CBS_len(&cbs) != 0) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
    EVP_PKEY_free(ret);
    OPENSSL_free(der);
    return NULL;
  }

  OPENSSL_free(der);
  return ret;
}

PKCS8_PRIV_KEY_INFO *EVP_PKEY2PKCS8(EVP_PKEY *pkey) {
  CBB cbb;
  uint8_t *der = NULL;
  size_t der_len;
  if (!CBB_init(&cbb, 0) ||
      !EVP_marshal_private_key(&cbb, pkey) ||
      !CBB_finish(&cbb, &der, &der_len) ||
      der_len > LONG_MAX) {
    CBB_cleanup(&cbb);
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_ENCODE_ERROR);
    goto err;
  }

  const uint8_t *p = der;
  PKCS8_PRIV_KEY_INFO *p8 = d2i_PKCS8_PRIV_KEY_INFO(NULL, &p, (long)der_len);
  if (p8 == NULL || p != der + der_len) {
    PKCS8_PRIV_KEY_INFO_free(p8);
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
    goto err;
  }

  OPENSSL_free(der);
  return p8;

err:
  OPENSSL_free(der);
  return NULL;
}

PKCS8_PRIV_KEY_INFO *PKCS8_decrypt(X509_SIG *pkcs8, const char *pass,
                                   int pass_len_in) {
  size_t pass_len;
  if (pass_len_in == -1 && pass != NULL) {
    pass_len = strlen(pass);
  } else {
    pass_len = (size_t)pass_len_in;
  }

  PKCS8_PRIV_KEY_INFO *ret = NULL;
  EVP_PKEY *pkey = NULL;
  uint8_t *in = NULL;

  // Convert the legacy ASN.1 object to a byte string.
  int in_len = i2d_X509_SIG(pkcs8, &in);
  if (in_len < 0) {
    goto err;
  }

  CBS cbs;
  CBS_init(&cbs, in, in_len);
  pkey = PKCS8_parse_encrypted_private_key(&cbs, pass, pass_len);
  if (pkey == NULL || CBS_len(&cbs) != 0) {
    goto err;
  }

  ret = EVP_PKEY2PKCS8(pkey);

err:
  OPENSSL_free(in);
  EVP_PKEY_free(pkey);
  return ret;
}

X509_SIG *PKCS8_encrypt(int pbe_nid, const EVP_CIPHER *cipher, const char *pass,
                        int pass_len_in, const uint8_t *salt, size_t salt_len,
                        int iterations, PKCS8_PRIV_KEY_INFO *p8inf) {
  size_t pass_len;
  if (pass_len_in == -1 && pass != NULL) {
    pass_len = strlen(pass);
  } else {
    pass_len = (size_t)pass_len_in;
  }

  // Parse out the private key.
  EVP_PKEY *pkey = EVP_PKCS82PKEY(p8inf);
  if (pkey == NULL) {
    return NULL;
  }

  X509_SIG *ret = NULL;
  uint8_t *der = NULL;
  size_t der_len;
  CBB cbb;
  if (!CBB_init(&cbb, 128) ||
      !PKCS8_marshal_encrypted_private_key(&cbb, pbe_nid, cipher, pass,
                                           pass_len, salt, salt_len, iterations,
                                           pkey) ||
      !CBB_finish(&cbb, &der, &der_len)) {
    CBB_cleanup(&cbb);
    goto err;
  }

  // Convert back to legacy ASN.1 objects.
  const uint8_t *ptr = der;
  ret = d2i_X509_SIG(NULL, &ptr, der_len);
  if (ret == NULL || ptr != der + der_len) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_INTERNAL_ERROR);
    X509_SIG_free(ret);
    ret = NULL;
  }

err:
  OPENSSL_free(der);
  EVP_PKEY_free(pkey);
  return ret;
}

struct pkcs12_context {
  EVP_PKEY **out_key;
  STACK_OF(X509) *out_certs;
  const char *password;
  size_t password_len;
};

// PKCS12_handle_sequence parses a BER-encoded SEQUENCE of elements in a PKCS#12
// structure.
static int PKCS12_handle_sequence(
    CBS *sequence, struct pkcs12_context *ctx,
    int (*handle_element)(CBS *cbs, struct pkcs12_context *ctx)) {
  uint8_t *der_bytes = NULL;
  size_t der_len;
  CBS in;
  int ret = 0;

  // Although a BER->DER conversion is done at the beginning of |PKCS12_parse|,
  // the ASN.1 data gets wrapped in OCTETSTRINGs and/or encrypted and the
  // conversion cannot see through those wrappings. So each time we step
  // through one we need to convert to DER again.
  if (!CBS_asn1_ber_to_der(sequence, &der_bytes, &der_len)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    return 0;
  }

  if (der_bytes != NULL) {
    CBS_init(&in, der_bytes, der_len);
  } else {
    CBS_init(&in, CBS_data(sequence), CBS_len(sequence));
  }

  CBS child;
  if (!CBS_get_asn1(&in, &child, CBS_ASN1_SEQUENCE) ||
      CBS_len(&in) != 0) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  while (CBS_len(&child) > 0) {
    CBS element;
    if (!CBS_get_asn1(&child, &element, CBS_ASN1_SEQUENCE)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (!handle_element(&element, ctx)) {
      goto err;
    }
  }

  ret = 1;

err:
  OPENSSL_free(der_bytes);
  return ret;
}

// 1.2.840.113549.1.12.10.1.2
static const uint8_t kPKCS8ShroudedKeyBag[] = {
    0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x0c, 0x0a, 0x01, 0x02};

// 1.2.840.113549.1.12.10.1.3
static const uint8_t kCertBag[] = {0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d,
                                   0x01, 0x0c, 0x0a, 0x01, 0x03};

// 1.2.840.113549.1.9.22.1
static const uint8_t kX509Certificate[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                           0x0d, 0x01, 0x09, 0x16, 0x01};

// PKCS12_handle_safe_bag parses a single SafeBag element in a PKCS#12
// structure.
static int PKCS12_handle_safe_bag(CBS *safe_bag, struct pkcs12_context *ctx) {
  CBS bag_id, wrapped_value;
  if (!CBS_get_asn1(safe_bag, &bag_id, CBS_ASN1_OBJECT) ||
      !CBS_get_asn1(safe_bag, &wrapped_value,
                        CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)
      /* Ignore the bagAttributes field. */) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    return 0;
  }

  if (CBS_mem_equal(&bag_id, kPKCS8ShroudedKeyBag,
                    sizeof(kPKCS8ShroudedKeyBag))) {
    // See RFC 7292, section 4.2.2.
    if (*ctx->out_key) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_MULTIPLE_PRIVATE_KEYS_IN_PKCS12);
      return 0;
    }

    EVP_PKEY *pkey = PKCS8_parse_encrypted_private_key(
        &wrapped_value, ctx->password, ctx->password_len);
    if (pkey == NULL) {
      return 0;
    }

    if (CBS_len(&wrapped_value) != 0) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      EVP_PKEY_free(pkey);
      return 0;
    }

    *ctx->out_key = pkey;
    return 1;
  }

  if (CBS_mem_equal(&bag_id, kCertBag, sizeof(kCertBag))) {
    // See RFC 7292, section 4.2.3.
    CBS cert_bag, cert_type, wrapped_cert, cert;
    if (!CBS_get_asn1(&wrapped_value, &cert_bag, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&cert_bag, &cert_type, CBS_ASN1_OBJECT) ||
        !CBS_get_asn1(&cert_bag, &wrapped_cert,
                      CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
        !CBS_get_asn1(&wrapped_cert, &cert, CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      return 0;
    }

    // Skip unknown certificate types.
    if (!CBS_mem_equal(&cert_type, kX509Certificate,
                       sizeof(kX509Certificate))) {
      return 1;
    }

    if (CBS_len(&cert) > LONG_MAX) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      return 0;
    }

    const uint8_t *inp = CBS_data(&cert);
    X509 *x509 = d2i_X509(NULL, &inp, (long)CBS_len(&cert));
    if (!x509) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      return 0;
    }

    if (inp != CBS_data(&cert) + CBS_len(&cert)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      X509_free(x509);
      return 0;
    }

    if (0 == sk_X509_push(ctx->out_certs, x509)) {
      X509_free(x509);
      return 0;
    }

    return 1;
  }

  // Unknown element type - ignore it.
  return 1;
}

// 1.2.840.113549.1.7.1
static const uint8_t kPKCS7Data[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                     0x0d, 0x01, 0x07, 0x01};

// 1.2.840.113549.1.7.6
static const uint8_t kPKCS7EncryptedData[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                              0x0d, 0x01, 0x07, 0x06};

// PKCS12_handle_content_info parses a single PKCS#7 ContentInfo element in a
// PKCS#12 structure.
static int PKCS12_handle_content_info(CBS *content_info,
                                      struct pkcs12_context *ctx) {
  CBS content_type, wrapped_contents, contents;
  int ret = 0;
  uint8_t *storage = NULL;

  if (!CBS_get_asn1(content_info, &content_type, CBS_ASN1_OBJECT) ||
      !CBS_get_asn1(content_info, &wrapped_contents,
                        CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      CBS_len(content_info) != 0) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  if (CBS_mem_equal(&content_type, kPKCS7EncryptedData,
                    sizeof(kPKCS7EncryptedData))) {
    // See https://tools.ietf.org/html/rfc2315#section-13.
    //
    // PKCS#7 encrypted data inside a PKCS#12 structure is generally an
    // encrypted certificate bag and it's generally encrypted with 40-bit
    // RC2-CBC.
    CBS version_bytes, eci, contents_type, ai, encrypted_contents;
    uint8_t *out;
    size_t out_len;

    if (!CBS_get_asn1(&wrapped_contents, &contents, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&contents, &version_bytes, CBS_ASN1_INTEGER) ||
        // EncryptedContentInfo, see
        // https://tools.ietf.org/html/rfc2315#section-10.1
        !CBS_get_asn1(&contents, &eci, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&eci, &contents_type, CBS_ASN1_OBJECT) ||
        // AlgorithmIdentifier, see
        // https://tools.ietf.org/html/rfc5280#section-4.1.1.2
        !CBS_get_asn1(&eci, &ai, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1_implicit_string(
            &eci, &encrypted_contents, &storage,
            CBS_ASN1_CONTEXT_SPECIFIC | 0, CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (!CBS_mem_equal(&contents_type, kPKCS7Data, sizeof(kPKCS7Data))) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (!pkcs8_pbe_decrypt(&out, &out_len, &ai, ctx->password,
                           ctx->password_len, CBS_data(&encrypted_contents),
                           CBS_len(&encrypted_contents))) {
      goto err;
    }

    CBS safe_contents;
    CBS_init(&safe_contents, out, out_len);
    ret = PKCS12_handle_sequence(&safe_contents, ctx, PKCS12_handle_safe_bag);
    OPENSSL_free(out);
  } else if (CBS_mem_equal(&content_type, kPKCS7Data, sizeof(kPKCS7Data))) {
    CBS octet_string_contents;

    if (!CBS_get_asn1(&wrapped_contents, &octet_string_contents,
                      CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    ret = PKCS12_handle_sequence(&octet_string_contents, ctx,
                                 PKCS12_handle_safe_bag);
  } else {
    // Unknown element type - ignore it.
    ret = 1;
  }

err:
  OPENSSL_free(storage);
  return ret;
}

int PKCS12_get_key_and_certs(EVP_PKEY **out_key, STACK_OF(X509) *out_certs,
                             CBS *ber_in, const char *password) {
  uint8_t *der_bytes = NULL;
  size_t der_len;
  CBS in, pfx, mac_data, authsafe, content_type, wrapped_authsafes, authsafes;
  uint64_t version;
  int ret = 0;
  struct pkcs12_context ctx;
  const size_t original_out_certs_len = sk_X509_num(out_certs);

  // The input may be in BER format.
  if (!CBS_asn1_ber_to_der(ber_in, &der_bytes, &der_len)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    return 0;
  }
  if (der_bytes != NULL) {
    CBS_init(&in, der_bytes, der_len);
  } else {
    CBS_init(&in, CBS_data(ber_in), CBS_len(ber_in));
  }

  *out_key = NULL;
  OPENSSL_memset(&ctx, 0, sizeof(ctx));

  // See ftp://ftp.rsasecurity.com/pub/pkcs/pkcs-12/pkcs-12v1.pdf, section
  // four.
  if (!CBS_get_asn1(&in, &pfx, CBS_ASN1_SEQUENCE) ||
      CBS_len(&in) != 0 ||
      !CBS_get_asn1_uint64(&pfx, &version)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  if (version < 3) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_VERSION);
    goto err;
  }

  if (!CBS_get_asn1(&pfx, &authsafe, CBS_ASN1_SEQUENCE)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  if (CBS_len(&pfx) == 0) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_MISSING_MAC);
    goto err;
  }

  if (!CBS_get_asn1(&pfx, &mac_data, CBS_ASN1_SEQUENCE)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  // authsafe is a PKCS#7 ContentInfo. See
  // https://tools.ietf.org/html/rfc2315#section-7.
  if (!CBS_get_asn1(&authsafe, &content_type, CBS_ASN1_OBJECT) ||
      !CBS_get_asn1(&authsafe, &wrapped_authsafes,
                        CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  // The content type can either be data or signedData. The latter indicates
  // that it's signed by a public key, which isn't supported.
  if (!CBS_mem_equal(&content_type, kPKCS7Data, sizeof(kPKCS7Data))) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_PKCS12_PUBLIC_KEY_INTEGRITY_NOT_SUPPORTED);
    goto err;
  }

  if (!CBS_get_asn1(&wrapped_authsafes, &authsafes, CBS_ASN1_OCTETSTRING)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  ctx.out_key = out_key;
  ctx.out_certs = out_certs;
  ctx.password = password;
  ctx.password_len = password != NULL ? strlen(password) : 0;

  // Verify the MAC.
  {
    CBS mac, salt, expected_mac;
    if (!CBS_get_asn1(&mac_data, &mac, CBS_ASN1_SEQUENCE)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    const EVP_MD *md = EVP_parse_digest_algorithm(&mac);
    if (md == NULL) {
      goto err;
    }

    if (!CBS_get_asn1(&mac, &expected_mac, CBS_ASN1_OCTETSTRING) ||
        !CBS_get_asn1(&mac_data, &salt, CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    // The iteration count is optional and the default is one.
    uint64_t iterations = 1;
    if (CBS_len(&mac_data) > 0) {
      if (!CBS_get_asn1_uint64(&mac_data, &iterations) ||
          iterations > UINT_MAX) {
        OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
        goto err;
      }
    }

    uint8_t hmac_key[EVP_MAX_MD_SIZE];
    if (!pkcs12_key_gen(ctx.password, ctx.password_len, CBS_data(&salt),
                        CBS_len(&salt), PKCS12_MAC_ID, iterations,
                        EVP_MD_size(md), hmac_key, md)) {
      goto err;
    }

    uint8_t hmac[EVP_MAX_MD_SIZE];
    unsigned hmac_len;
    if (NULL == HMAC(md, hmac_key, EVP_MD_size(md), CBS_data(&authsafes),
                     CBS_len(&authsafes), hmac, &hmac_len)) {
      goto err;
    }

    if (!CBS_mem_equal(&expected_mac, hmac, hmac_len)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_INCORRECT_PASSWORD);
      goto err;
    }
  }

  // authsafes contains a series of PKCS#7 ContentInfos.
  if (!PKCS12_handle_sequence(&authsafes, &ctx, PKCS12_handle_content_info)) {
    goto err;
  }

  ret = 1;

err:
  OPENSSL_free(der_bytes);
  if (!ret) {
    EVP_PKEY_free(*out_key);
    *out_key = NULL;
    while (sk_X509_num(out_certs) > original_out_certs_len) {
      X509 *x509 = sk_X509_pop(out_certs);
      X509_free(x509);
    }
  }

  return ret;
}

void PKCS12_PBE_add(void) {}

struct pkcs12_st {
  uint8_t *ber_bytes;
  size_t ber_len;
};

PKCS12 *d2i_PKCS12(PKCS12 **out_p12, const uint8_t **ber_bytes,
                   size_t ber_len) {
  PKCS12 *p12;

  p12 = OPENSSL_malloc(sizeof(PKCS12));
  if (!p12) {
    return NULL;
  }

  p12->ber_bytes = OPENSSL_malloc(ber_len);
  if (!p12->ber_bytes) {
    OPENSSL_free(p12);
    return NULL;
  }

  OPENSSL_memcpy(p12->ber_bytes, *ber_bytes, ber_len);
  p12->ber_len = ber_len;
  *ber_bytes += ber_len;

  if (out_p12) {
    PKCS12_free(*out_p12);

    *out_p12 = p12;
  }

  return p12;
}

PKCS12* d2i_PKCS12_bio(BIO *bio, PKCS12 **out_p12) {
  size_t used = 0;
  BUF_MEM *buf;
  const uint8_t *dummy;
  static const size_t kMaxSize = 256 * 1024;
  PKCS12 *ret = NULL;

  buf = BUF_MEM_new();
  if (buf == NULL) {
    return NULL;
  }
  if (BUF_MEM_grow(buf, 8192) == 0) {
    goto out;
  }

  for (;;) {
    int n = BIO_read(bio, &buf->data[used], buf->length - used);
    if (n < 0) {
      if (used == 0) {
        goto out;
      }
      // Workaround a bug in node.js. It uses a memory BIO for this in the wrong
      // mode.
      n = 0;
    }

    if (n == 0) {
      break;
    }
    used += n;

    if (used < buf->length) {
      continue;
    }

    if (buf->length > kMaxSize ||
        BUF_MEM_grow(buf, buf->length * 2) == 0) {
      goto out;
    }
  }

  dummy = (uint8_t*) buf->data;
  ret = d2i_PKCS12(out_p12, &dummy, used);

out:
  BUF_MEM_free(buf);
  return ret;
}

PKCS12* d2i_PKCS12_fp(FILE *fp, PKCS12 **out_p12) {
  BIO *bio;
  PKCS12 *ret;

  bio = BIO_new_fp(fp, 0 /* don't take ownership */);
  if (!bio) {
    return NULL;
  }

  ret = d2i_PKCS12_bio(bio, out_p12);
  BIO_free(bio);
  return ret;
}

int PKCS12_parse(const PKCS12 *p12, const char *password, EVP_PKEY **out_pkey,
                 X509 **out_cert, STACK_OF(X509) **out_ca_certs) {
  CBS ber_bytes;
  STACK_OF(X509) *ca_certs = NULL;
  char ca_certs_alloced = 0;

  if (out_ca_certs != NULL && *out_ca_certs != NULL) {
    ca_certs = *out_ca_certs;
  }

  if (!ca_certs) {
    ca_certs = sk_X509_new_null();
    if (ca_certs == NULL) {
      OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    ca_certs_alloced = 1;
  }

  CBS_init(&ber_bytes, p12->ber_bytes, p12->ber_len);
  if (!PKCS12_get_key_and_certs(out_pkey, ca_certs, &ber_bytes, password)) {
    if (ca_certs_alloced) {
      sk_X509_free(ca_certs);
    }
    return 0;
  }

  *out_cert = NULL;
  if (sk_X509_num(ca_certs) > 0) {
    *out_cert = sk_X509_shift(ca_certs);
  }

  if (out_ca_certs) {
    *out_ca_certs = ca_certs;
  } else {
    sk_X509_pop_free(ca_certs, X509_free);
  }

  return 1;
}

int PKCS12_verify_mac(const PKCS12 *p12, const char *password,
                      int password_len) {
  if (password == NULL) {
    if (password_len != 0) {
      return 0;
    }
  } else if (password_len != -1 &&
             (password[password_len] != 0 ||
              OPENSSL_memchr(password, 0, password_len) != NULL)) {
    return 0;
  }

  EVP_PKEY *pkey = NULL;
  X509 *cert = NULL;
  if (!PKCS12_parse(p12, password, &pkey, &cert, NULL)) {
    ERR_clear_error();
    return 0;
  }

  EVP_PKEY_free(pkey);
  X509_free(cert);

  return 1;
}

void PKCS12_free(PKCS12 *p12) {
  if (p12 == NULL) {
    return;
  }
  OPENSSL_free(p12->ber_bytes);
  OPENSSL_free(p12);
}
