/* Copyright (c) 2017, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <openssl/pkcs7.h>

#include <assert.h>
#include <limits.h>

#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/pem.h>
#include <openssl/pool.h>
#include <openssl/stack.h>
#include <openssl/x509.h>

#include "internal.h"
#include "../internal.h"


int PKCS7_get_certificates(STACK_OF(X509) *out_certs, CBS *cbs) {
  int ret = 0;
  const size_t initial_certs_len = sk_X509_num(out_certs);
  STACK_OF(CRYPTO_BUFFER) *raw = sk_CRYPTO_BUFFER_new_null();
  if (raw == NULL ||
      !PKCS7_get_raw_certificates(raw, cbs, NULL)) {
    goto err;
  }

  for (size_t i = 0; i < sk_CRYPTO_BUFFER_num(raw); i++) {
    CRYPTO_BUFFER *buf = sk_CRYPTO_BUFFER_value(raw, i);
    X509 *x509 = X509_parse_from_buffer(buf);
    if (x509 == NULL ||
        !sk_X509_push(out_certs, x509)) {
      X509_free(x509);
      goto err;
    }
  }

  ret = 1;

err:
  sk_CRYPTO_BUFFER_pop_free(raw, CRYPTO_BUFFER_free);
  if (!ret) {
    while (sk_X509_num(out_certs) != initial_certs_len) {
      X509 *x509 = sk_X509_pop(out_certs);
      X509_free(x509);
    }
  }

  return ret;
}

int PKCS7_get_CRLs(STACK_OF(X509_CRL) *out_crls, CBS *cbs) {
  CBS signed_data, crls;
  uint8_t *der_bytes = NULL;
  int ret = 0, has_crls;
  const size_t initial_crls_len = sk_X509_CRL_num(out_crls);

  // See https://tools.ietf.org/html/rfc2315#section-9.1
  if (!pkcs7_parse_header(&der_bytes, &signed_data, cbs) ||
      // Even if only CRLs are included, there may be an empty certificates
      // block. OpenSSL does this, for example.
      !CBS_get_optional_asn1(
          &signed_data, NULL, NULL,
          CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      !CBS_get_optional_asn1(
          &signed_data, &crls, &has_crls,
          CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 1)) {
    goto err;
  }

  if (!has_crls) {
    CBS_init(&crls, NULL, 0);
  }

  while (CBS_len(&crls) > 0) {
    CBS crl_data;
    X509_CRL *crl;
    const uint8_t *inp;

    if (!CBS_get_asn1_element(&crls, &crl_data, CBS_ASN1_SEQUENCE)) {
      goto err;
    }

    if (CBS_len(&crl_data) > LONG_MAX) {
      goto err;
    }
    inp = CBS_data(&crl_data);
    crl = d2i_X509_CRL(NULL, &inp, (long)CBS_len(&crl_data));
    if (!crl) {
      goto err;
    }

    assert(inp == CBS_data(&crl_data) + CBS_len(&crl_data));

    if (sk_X509_CRL_push(out_crls, crl) == 0) {
      X509_CRL_free(crl);
      goto err;
    }
  }

  ret = 1;

err:
  OPENSSL_free(der_bytes);

  if (!ret) {
    while (sk_X509_CRL_num(out_crls) != initial_crls_len) {
      X509_CRL_free(sk_X509_CRL_pop(out_crls));
    }
  }

  return ret;
}

int PKCS7_get_PEM_certificates(STACK_OF(X509) *out_certs, BIO *pem_bio) {
  uint8_t *data;
  long len;
  int ret;

  // Even though we pass PEM_STRING_PKCS7 as the expected PEM type here, PEM
  // internally will actually allow several other values too, including
  // "CERTIFICATE".
  if (!PEM_bytes_read_bio(&data, &len, NULL /* PEM type output */,
                          PEM_STRING_PKCS7, pem_bio,
                          NULL /* password callback */,
                          NULL /* password callback argument */)) {
    return 0;
  }

  CBS cbs;
  CBS_init(&cbs, data, len);
  ret = PKCS7_get_certificates(out_certs, &cbs);
  OPENSSL_free(data);
  return ret;
}

int PKCS7_get_PEM_CRLs(STACK_OF(X509_CRL) *out_crls, BIO *pem_bio) {
  uint8_t *data;
  long len;
  int ret;

  // Even though we pass PEM_STRING_PKCS7 as the expected PEM type here, PEM
  // internally will actually allow several other values too, including
  // "CERTIFICATE".
  if (!PEM_bytes_read_bio(&data, &len, NULL /* PEM type output */,
                          PEM_STRING_PKCS7, pem_bio,
                          NULL /* password callback */,
                          NULL /* password callback argument */)) {
    return 0;
  }

  CBS cbs;
  CBS_init(&cbs, data, len);
  ret = PKCS7_get_CRLs(out_crls, &cbs);
  OPENSSL_free(data);
  return ret;
}

static int pkcs7_bundle_certificates_cb(CBB *out, const void *arg) {
  const STACK_OF(X509) *certs = arg;
  size_t i;
  CBB certificates;

  // See https://tools.ietf.org/html/rfc2315#section-9.1
  if (!CBB_add_asn1(out, &certificates,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    return 0;
  }

  for (i = 0; i < sk_X509_num(certs); i++) {
    X509 *x509 = sk_X509_value(certs, i);
    uint8_t *buf;
    int len = i2d_X509(x509, NULL);

    if (len < 0 ||
        !CBB_add_space(&certificates, &buf, len) ||
        i2d_X509(x509, &buf) < 0) {
      return 0;
    }
  }

  return CBB_flush(out);
}

int PKCS7_bundle_certificates(CBB *out, const STACK_OF(X509) *certs) {
  return pkcs7_bundle(out, pkcs7_bundle_certificates_cb, certs);
}

static int pkcs7_bundle_crls_cb(CBB *out, const void *arg) {
  const STACK_OF(X509_CRL) *crls = arg;
  size_t i;
  CBB crl_data;

  // See https://tools.ietf.org/html/rfc2315#section-9.1
  if (!CBB_add_asn1(out, &crl_data,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 1)) {
    return 0;
  }

  for (i = 0; i < sk_X509_CRL_num(crls); i++) {
    X509_CRL *crl = sk_X509_CRL_value(crls, i);
    uint8_t *buf;
    int len = i2d_X509_CRL(crl, NULL);

    if (len < 0 ||
        !CBB_add_space(&crl_data, &buf, len) ||
        i2d_X509_CRL(crl, &buf) < 0) {
      return 0;
    }
  }

  return CBB_flush(out);
}

int PKCS7_bundle_CRLs(CBB *out, const STACK_OF(X509_CRL) *crls) {
  return pkcs7_bundle(out, pkcs7_bundle_crls_cb, crls);
}

static PKCS7 *pkcs7_new(CBS *cbs) {
  PKCS7 *ret = OPENSSL_malloc(sizeof(PKCS7));
  if (ret == NULL) {
    return NULL;
  }
  OPENSSL_memset(ret, 0, sizeof(PKCS7));
  ret->type = (ASN1_OBJECT *)OBJ_nid2obj(NID_pkcs7_signed);
  ret->d.sign = OPENSSL_malloc(sizeof(PKCS7_SIGNED));
  if (ret->d.sign == NULL) {
    goto err;
  }
  ret->d.sign->cert = sk_X509_new_null();
  ret->d.sign->crl = sk_X509_CRL_new_null();
  CBS copy = *cbs, copy2 = *cbs;
  if (ret->d.sign->cert == NULL || ret->d.sign->crl == NULL ||
      !PKCS7_get_certificates(ret->d.sign->cert, &copy) ||
      !PKCS7_get_CRLs(ret->d.sign->crl, cbs)) {
    goto err;
  }

  if (sk_X509_num(ret->d.sign->cert) == 0) {
    sk_X509_free(ret->d.sign->cert);
    ret->d.sign->cert = NULL;
  }

  if (sk_X509_CRL_num(ret->d.sign->crl) == 0) {
    sk_X509_CRL_free(ret->d.sign->crl);
    ret->d.sign->crl = NULL;
  }

  ret->ber_len = CBS_len(&copy2) - CBS_len(cbs);
  ret->ber_bytes = BUF_memdup(CBS_data(&copy2), ret->ber_len);
  if (ret->ber_bytes == NULL) {
    goto err;
  }

  return ret;

err:
  PKCS7_free(ret);
  return NULL;
}

PKCS7 *d2i_PKCS7(PKCS7 **out, const uint8_t **inp,
                 size_t len) {
  CBS cbs;
  CBS_init(&cbs, *inp, len);
  PKCS7 *ret = pkcs7_new(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  *inp = CBS_data(&cbs);
  if (out != NULL) {
    PKCS7_free(*out);
    *out = ret;
  }
  return ret;
}

PKCS7 *d2i_PKCS7_bio(BIO *bio, PKCS7 **out) {
  // Use a generous bound, to allow for PKCS#7 files containing large root sets.
  static const size_t kMaxSize = 4 * 1024 * 1024;
  uint8_t *data;
  size_t len;
  if (!BIO_read_asn1(bio, &data, &len, kMaxSize)) {
    return NULL;
  }

  CBS cbs;
  CBS_init(&cbs, data, len);
  PKCS7 *ret = pkcs7_new(&cbs);
  OPENSSL_free(data);
  if (out != NULL && ret != NULL) {
    PKCS7_free(*out);
    *out = ret;
  }
  return ret;
}

int i2d_PKCS7(const PKCS7 *p7, uint8_t **out) {
  if (p7->ber_len > INT_MAX) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_OVERFLOW);
    return -1;
  }

  if (out == NULL) {
    return (int)p7->ber_len;
  }

  if (*out == NULL) {
    *out = OPENSSL_malloc(p7->ber_len);
    if (*out == NULL) {
      OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
      return -1;
    }
    OPENSSL_memcpy(*out, p7->ber_bytes, p7->ber_len);
  } else {
    OPENSSL_memcpy(*out, p7->ber_bytes, p7->ber_len);
    *out += p7->ber_len;
  }
  return (int)p7->ber_len;
}

int i2d_PKCS7_bio(BIO *bio, const PKCS7 *p7) {
  return BIO_write_all(bio, p7->ber_bytes, p7->ber_len);
}

void PKCS7_free(PKCS7 *p7) {
  if (p7 == NULL) {
    return;
  }

  OPENSSL_free(p7->ber_bytes);
  ASN1_OBJECT_free(p7->type);
  // We only supported signed data.
  if (p7->d.sign != NULL) {
    sk_X509_pop_free(p7->d.sign->cert, X509_free);
    sk_X509_CRL_pop_free(p7->d.sign->crl, X509_CRL_free);
    OPENSSL_free(p7->d.sign);
  }
  OPENSSL_free(p7);
}

// We only support signed data, so these getters are no-ops.
int PKCS7_type_is_data(const PKCS7 *p7) { return 0; }
int PKCS7_type_is_digest(const PKCS7 *p7) { return 0; }
int PKCS7_type_is_encrypted(const PKCS7 *p7) { return 0; }
int PKCS7_type_is_enveloped(const PKCS7 *p7) { return 0; }
int PKCS7_type_is_signed(const PKCS7 *p7) { return 1; }
int PKCS7_type_is_signedAndEnveloped(const PKCS7 *p7) { return 0; }

PKCS7 *PKCS7_sign(X509 *sign_cert, EVP_PKEY *pkey, STACK_OF(X509) *certs,
                  BIO *data, int flags) {
  if (sign_cert != NULL || pkey != NULL || flags != PKCS7_DETACHED) {
    OPENSSL_PUT_ERROR(PKCS7, ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED);
    return NULL;
  }

  uint8_t *der;
  size_t len;
  CBB cbb;
  if (!CBB_init(&cbb, 2048) ||
      !PKCS7_bundle_certificates(&cbb, certs) ||
      !CBB_finish(&cbb, &der, &len)) {
    CBB_cleanup(&cbb);
    return NULL;
  }

  CBS cbs;
  CBS_init(&cbs, der, len);
  PKCS7 *ret = pkcs7_new(&cbs);
  OPENSSL_free(der);
  return ret;
}
