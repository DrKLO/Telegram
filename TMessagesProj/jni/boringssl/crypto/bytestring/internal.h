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

#ifndef OPENSSL_HEADER_BYTESTRING_INTERNAL_H
#define OPENSSL_HEADER_BYTESTRING_INTERNAL_H

#include <openssl/base.h>

#if defined(__cplusplus)
extern "C" {
#endif


/* CBS_asn1_ber_to_der reads an ASN.1 structure from |in|. If it finds
 * indefinite-length elements then it attempts to convert the BER data to DER
 * and sets |*out| and |*out_length| to describe a malloced buffer containing
 * the DER data. Additionally, |*in| will be advanced over the ASN.1 data.
 *
 * If it doesn't find any indefinite-length elements then it sets |*out| to
 * NULL and |*in| is unmodified.
 *
 * A sufficiently complex ASN.1 structure will break this function because it's
 * not possible to generically convert BER to DER without knowledge of the
 * structure itself. However, this sufficies to handle the PKCS#7 and #12 output
 * from NSS.
 *
 * It returns one on success and zero otherwise. */
OPENSSL_EXPORT int CBS_asn1_ber_to_der(CBS *in, uint8_t **out, size_t *out_len);


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_BYTESTRING_INTERNAL_H */
