/* Copyright (c) 2014, Google Inc.
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

#include <openssl/x509.h>

#include <assert.h>

#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/pem.h>
#include <openssl/stack.h>

#include "../bytestring/internal.h"


/* pkcs7_parse_header reads the non-certificate/non-CRL prefix of a PKCS#7
 * SignedData blob from |cbs| and sets |*out| to point to the rest of the
 * input. If the input is in BER format, then |*der_bytes| will be set to a
 * pointer that needs to be freed by the caller once they have finished
 * processing |*out| (which will be pointing into |*der_bytes|).
 *
 * It returns one on success or zero on error. On error, |*der_bytes| is
 * NULL. */
static int pkcs7_parse_header(uint8_t **der_bytes, CBS *out, CBS *cbs) {
  size_t der_len;
  CBS in, content_info, content_type, wrapped_signed_data, signed_data;
  uint64_t version;

  /* The input may be in BER format. */
  *der_bytes = NULL;
  if (!CBS_asn1_ber_to_der(cbs, der_bytes, &der_len)) {
    return 0;
  }
  if (*der_bytes != NULL) {
    CBS_init(&in, *der_bytes, der_len);
  } else {
    CBS_init(&in, CBS_data(cbs), CBS_len(cbs));
  }

  /* See https://tools.ietf.org/html/rfc2315#section-7 */
  if (!CBS_get_asn1(&in, &content_info, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1(&content_info, &content_type, CBS_ASN1_OBJECT)) {
    goto err;
  }

  if (OBJ_cbs2nid(&content_type) != NID_pkcs7_signed) {
    OPENSSL_PUT_ERROR(X509, X509_R_NOT_PKCS7_SIGNED_DATA);
    goto err;
  }

  /* See https://tools.ietf.org/html/rfc2315#section-9.1 */
  if (!CBS_get_asn1(&content_info, &wrapped_signed_data,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      !CBS_get_asn1(&wrapped_signed_data, &signed_data, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&signed_data, &version) ||
      !CBS_get_asn1(&signed_data, NULL /* digests */, CBS_ASN1_SET) ||
      !CBS_get_asn1(&signed_data, NULL /* content */, CBS_ASN1_SEQUENCE)) {
    goto err;
  }

  if (version < 1) {
    OPENSSL_PUT_ERROR(X509, X509_R_BAD_PKCS7_VERSION);
    goto err;
  }

  CBS_init(out, CBS_data(&signed_data), CBS_len(&signed_data));
  return 1;

err:
  if (*der_bytes) {
    OPENSSL_free(*der_bytes);
    *der_bytes = NULL;
  }

  return 0;
}

int PKCS7_get_certificates(STACK_OF(X509) *out_certs, CBS *cbs) {
  CBS signed_data, certificates;
  uint8_t *der_bytes = NULL;
  int ret = 0;
  const size_t initial_certs_len = sk_X509_num(out_certs);

  if (!pkcs7_parse_header(&der_bytes, &signed_data, cbs)) {
    return 0;
  }

  /* See https://tools.ietf.org/html/rfc2315#section-9.1 */
  if (!CBS_get_asn1(&signed_data, &certificates,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    OPENSSL_PUT_ERROR(X509, X509_R_NO_CERTIFICATES_INCLUDED);
    goto err;
  }

  while (CBS_len(&certificates) > 0) {
    CBS cert;
    X509 *x509;
    const uint8_t *inp;

    if (!CBS_get_asn1_element(&certificates, &cert, CBS_ASN1_SEQUENCE)) {
      goto err;
    }

    inp = CBS_data(&cert);
    x509 = d2i_X509(NULL, &inp, CBS_len(&cert));
    if (!x509) {
      goto err;
    }

    assert(inp == CBS_data(&cert) + CBS_len(&cert));

    if (sk_X509_push(out_certs, x509) == 0) {
      X509_free(x509);
      goto err;
    }
  }

  ret = 1;

err:
  if (der_bytes) {
    OPENSSL_free(der_bytes);
  }

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
  int ret = 0;
  const size_t initial_crls_len = sk_X509_CRL_num(out_crls);

  if (!pkcs7_parse_header(&der_bytes, &signed_data, cbs)) {
    return 0;
  }

  /* See https://tools.ietf.org/html/rfc2315#section-9.1 */

  /* Even if only CRLs are included, there may be an empty certificates block.
   * OpenSSL does this, for example. */
  if (CBS_peek_asn1_tag(&signed_data,
                        CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) &&
      !CBS_get_asn1(&signed_data, NULL /* certificates */,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    goto err;
  }

  if (!CBS_get_asn1(&signed_data, &crls,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 1)) {
    OPENSSL_PUT_ERROR(X509, X509_R_NO_CRLS_INCLUDED);
    goto err;
  }

  while (CBS_len(&crls) > 0) {
    CBS crl_data;
    X509_CRL *crl;
    const uint8_t *inp;

    if (!CBS_get_asn1_element(&crls, &crl_data, CBS_ASN1_SEQUENCE)) {
      goto err;
    }

    inp = CBS_data(&crl_data);
    crl = d2i_X509_CRL(NULL, &inp, CBS_len(&crl_data));
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
  if (der_bytes) {
    OPENSSL_free(der_bytes);
  }

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

  /* Even though we pass PEM_STRING_PKCS7 as the expected PEM type here, PEM
   * internally will actually allow several other values too, including
   * "CERTIFICATE". */
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

  /* Even though we pass PEM_STRING_PKCS7 as the expected PEM type here, PEM
   * internally will actually allow several other values too, including
   * "CERTIFICATE". */
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

/* pkcs7_bundle writes a PKCS#7, SignedData structure to |out| and then calls
 * |cb| with a CBB to which certificate or CRL data can be written, and the
 * opaque context pointer, |arg|. The callback can return zero to indicate an
 * error.
 *
 * pkcs7_bundle returns one on success or zero on error. */
static int pkcs7_bundle(CBB *out, int (*cb)(CBB *out, const void *arg),
                        const void *arg) {
  CBB outer_seq, wrapped_seq, seq, version_bytes, digest_algos_set,
      content_info;

  /* See https://tools.ietf.org/html/rfc2315#section-7 */
  if (!CBB_add_asn1(out, &outer_seq, CBS_ASN1_SEQUENCE) ||
      !OBJ_nid2cbb(&outer_seq, NID_pkcs7_signed) ||
      !CBB_add_asn1(&outer_seq, &wrapped_seq,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      /* See https://tools.ietf.org/html/rfc2315#section-9.1 */
      !CBB_add_asn1(&wrapped_seq, &seq, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&seq, &version_bytes, CBS_ASN1_INTEGER) ||
      !CBB_add_u8(&version_bytes, 1) ||
      !CBB_add_asn1(&seq, &digest_algos_set, CBS_ASN1_SET) ||
      !CBB_add_asn1(&seq, &content_info, CBS_ASN1_SEQUENCE) ||
      !OBJ_nid2cbb(&content_info, NID_pkcs7_data) ||
      !cb(&seq, arg)) {
    return 0;
  }

  return CBB_flush(out);
}

static int pkcs7_bundle_certificates_cb(CBB *out, const void *arg) {
  const STACK_OF(X509) *certs = arg;
  size_t i;
  CBB certificates;

  /* See https://tools.ietf.org/html/rfc2315#section-9.1 */
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

  /* See https://tools.ietf.org/html/rfc2315#section-9.1 */
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
