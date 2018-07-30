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

#ifndef OPENSSL_HEADER_PKCS7_H
#define OPENSSL_HEADER_PKCS7_H

#include <openssl/base.h>

#include <openssl/stack.h>

#if defined(__cplusplus)
extern "C" {
#endif


// PKCS#7.
//
// This library contains functions for extracting information from PKCS#7
// structures (RFC 2315).

DECLARE_STACK_OF(CRYPTO_BUFFER)
DECLARE_STACK_OF(X509)
DECLARE_STACK_OF(X509_CRL)

// PKCS7_get_raw_certificates parses a PKCS#7, SignedData structure from |cbs|
// and appends the included certificates to |out_certs|. It returns one on
// success and zero on error.
OPENSSL_EXPORT int PKCS7_get_raw_certificates(
    STACK_OF(CRYPTO_BUFFER) *out_certs, CBS *cbs, CRYPTO_BUFFER_POOL *pool);

// PKCS7_get_certificates behaves like |PKCS7_get_raw_certificates| but parses
// them into |X509| objects.
OPENSSL_EXPORT int PKCS7_get_certificates(STACK_OF(X509) *out_certs, CBS *cbs);

// PKCS7_bundle_certificates appends a PKCS#7, SignedData structure containing
// |certs| to |out|. It returns one on success and zero on error.
OPENSSL_EXPORT int PKCS7_bundle_certificates(
    CBB *out, const STACK_OF(X509) *certs);

// PKCS7_get_CRLs parses a PKCS#7, SignedData structure from |cbs| and appends
// the included CRLs to |out_crls|. It returns one on success and zero on
// error.
OPENSSL_EXPORT int PKCS7_get_CRLs(STACK_OF(X509_CRL) *out_crls, CBS *cbs);

// PKCS7_bundle_CRLs appends a PKCS#7, SignedData structure containing
// |crls| to |out|. It returns one on success and zero on error.
OPENSSL_EXPORT int PKCS7_bundle_CRLs(CBB *out, const STACK_OF(X509_CRL) *crls);

// PKCS7_get_PEM_certificates reads a PEM-encoded, PKCS#7, SignedData structure
// from |pem_bio| and appends the included certificates to |out_certs|. It
// returns one on success and zero on error.
OPENSSL_EXPORT int PKCS7_get_PEM_certificates(STACK_OF(X509) *out_certs,
                                              BIO *pem_bio);

// PKCS7_get_PEM_CRLs reads a PEM-encoded, PKCS#7, SignedData structure from
// |pem_bio| and appends the included CRLs to |out_crls|. It returns one on
// success and zero on error.
OPENSSL_EXPORT int PKCS7_get_PEM_CRLs(STACK_OF(X509_CRL) *out_crls,
                                      BIO *pem_bio);


#if defined(__cplusplus)
}  // extern C
#endif

#define PKCS7_R_BAD_PKCS7_VERSION 100
#define PKCS7_R_NOT_PKCS7_SIGNED_DATA 101
#define PKCS7_R_NO_CERTIFICATES_INCLUDED 102
#define PKCS7_R_NO_CRLS_INCLUDED 103

#endif  // OPENSSL_HEADER_PKCS7_H
