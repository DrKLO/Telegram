/* Copyright (c) 2018, Google Inc.
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

#ifndef OPENSSL_HEADER_X509V3_INTERNAL_H
#define OPENSSL_HEADER_X509V3_INTERNAL_H

#include <openssl/base.h>

#if defined(__cplusplus)
extern "C" {
#endif


// x509v3_bytes_to_hex encodes |len| bytes from |buffer| to hex and returns a
// newly-allocated NUL-terminated string containing the result, or NULL on
// allocation error.
//
// Note this function was historically named |hex_to_string| in OpenSSL, not
// |string_to_hex|.
char *x509v3_bytes_to_hex(const unsigned char *buffer, long len);

// x509v3_hex_string_to_bytes decodes |str| in hex and returns a newly-allocated
// array containing the result, or NULL on error. On success, it sets |*len| to
// the length of the result. Colon separators between bytes in the input are
// allowed and ignored.
//
// Note this function was historically named |string_to_hex| in OpenSSL, not
// |hex_to_string|.
unsigned char *x509v3_hex_to_bytes(const char *str, long *len);

// x509v3_name_cmp returns zero if |name| is equal to |cmp| or begins with |cmp|
// followed by '.'. Otherwise, it returns a non-zero number.
int x509v3_name_cmp(const char *name, const char *cmp);

// x509v3_looks_like_dns_name returns one if |in| looks like a DNS name and zero
// otherwise.
OPENSSL_EXPORT int x509v3_looks_like_dns_name(const unsigned char *in,
                                              size_t len);


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_X509V3_INTERNAL_H */
