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

#include <openssl/pkcs7.h>

#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/pool.h>
#include <openssl/stack.h>

#include "internal.h"
#include "../bytestring/internal.h"


// 1.2.840.113549.1.7.1
static const uint8_t kPKCS7Data[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                     0x0d, 0x01, 0x07, 0x01};

// 1.2.840.113549.1.7.2
static const uint8_t kPKCS7SignedData[] = {0x2a, 0x86, 0x48, 0x86, 0xf7,
                                           0x0d, 0x01, 0x07, 0x02};

// pkcs7_parse_header reads the non-certificate/non-CRL prefix of a PKCS#7
// SignedData blob from |cbs| and sets |*out| to point to the rest of the
// input. If the input is in BER format, then |*der_bytes| will be set to a
// pointer that needs to be freed by the caller once they have finished
// processing |*out| (which will be pointing into |*der_bytes|).
//
// It returns one on success or zero on error. On error, |*der_bytes| is
// NULL.
int pkcs7_parse_header(uint8_t **der_bytes, CBS *out, CBS *cbs) {
  size_t der_len;
  CBS in, content_info, content_type, wrapped_signed_data, signed_data;
  uint64_t version;

  // The input may be in BER format.
  *der_bytes = NULL;
  if (!CBS_asn1_ber_to_der(cbs, der_bytes, &der_len)) {
    return 0;
  }
  if (*der_bytes != NULL) {
    CBS_init(&in, *der_bytes, der_len);
  } else {
    CBS_init(&in, CBS_data(cbs), CBS_len(cbs));
  }

  // See https://tools.ietf.org/html/rfc2315#section-7
  if (!CBS_get_asn1(&in, &content_info, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1(&content_info, &content_type, CBS_ASN1_OBJECT)) {
    goto err;
  }

  if (!CBS_mem_equal(&content_type, kPKCS7SignedData,
                     sizeof(kPKCS7SignedData))) {
    OPENSSL_PUT_ERROR(PKCS7, PKCS7_R_NOT_PKCS7_SIGNED_DATA);
    goto err;
  }

  // See https://tools.ietf.org/html/rfc2315#section-9.1
  if (!CBS_get_asn1(&content_info, &wrapped_signed_data,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      !CBS_get_asn1(&wrapped_signed_data, &signed_data, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&signed_data, &version) ||
      !CBS_get_asn1(&signed_data, NULL /* digests */, CBS_ASN1_SET) ||
      !CBS_get_asn1(&signed_data, NULL /* content */, CBS_ASN1_SEQUENCE)) {
    goto err;
  }

  if (version < 1) {
    OPENSSL_PUT_ERROR(PKCS7, PKCS7_R_BAD_PKCS7_VERSION);
    goto err;
  }

  CBS_init(out, CBS_data(&signed_data), CBS_len(&signed_data));
  return 1;

err:
  OPENSSL_free(*der_bytes);
  *der_bytes = NULL;
  return 0;
}

int PKCS7_get_raw_certificates(STACK_OF(CRYPTO_BUFFER) *out_certs, CBS *cbs,
                               CRYPTO_BUFFER_POOL *pool) {
  CBS signed_data, certificates;
  uint8_t *der_bytes = NULL;
  int ret = 0;
  const size_t initial_certs_len = sk_CRYPTO_BUFFER_num(out_certs);

  if (!pkcs7_parse_header(&der_bytes, &signed_data, cbs)) {
    return 0;
  }

  // See https://tools.ietf.org/html/rfc2315#section-9.1
  if (!CBS_get_asn1(&signed_data, &certificates,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    OPENSSL_PUT_ERROR(PKCS7, PKCS7_R_NO_CERTIFICATES_INCLUDED);
    goto err;
  }

  while (CBS_len(&certificates) > 0) {
    CBS cert;
    if (!CBS_get_asn1_element(&certificates, &cert, CBS_ASN1_SEQUENCE)) {
      goto err;
    }

    CRYPTO_BUFFER *buf = CRYPTO_BUFFER_new_from_CBS(&cert, pool);
    if (buf == NULL ||
        !sk_CRYPTO_BUFFER_push(out_certs, buf)) {
      CRYPTO_BUFFER_free(buf);
      goto err;
    }
  }

  ret = 1;

err:
  OPENSSL_free(der_bytes);

  if (!ret) {
    while (sk_CRYPTO_BUFFER_num(out_certs) != initial_certs_len) {
      CRYPTO_BUFFER *buf = sk_CRYPTO_BUFFER_pop(out_certs);
      CRYPTO_BUFFER_free(buf);
    }
  }

  return ret;
}

int pkcs7_bundle(CBB *out, int (*cb)(CBB *out, const void *arg),
                 const void *arg) {
  CBB outer_seq, oid, wrapped_seq, seq, version_bytes, digest_algos_set,
      content_info;

  // See https://tools.ietf.org/html/rfc2315#section-7
  if (!CBB_add_asn1(out, &outer_seq, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&outer_seq, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, kPKCS7SignedData, sizeof(kPKCS7SignedData)) ||
      !CBB_add_asn1(&outer_seq, &wrapped_seq,
                    CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
      // See https://tools.ietf.org/html/rfc2315#section-9.1
      !CBB_add_asn1(&wrapped_seq, &seq, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&seq, &version_bytes, CBS_ASN1_INTEGER) ||
      !CBB_add_u8(&version_bytes, 1) ||
      !CBB_add_asn1(&seq, &digest_algos_set, CBS_ASN1_SET) ||
      !CBB_add_asn1(&seq, &content_info, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&content_info, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, kPKCS7Data, sizeof(kPKCS7Data)) ||
      !cb(&seq, arg)) {
    return 0;
  }

  return CBB_flush(out);
}
