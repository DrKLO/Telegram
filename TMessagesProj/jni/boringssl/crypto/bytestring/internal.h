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


// CBS_asn1_ber_to_der reads a BER element from |in|. If it finds
// indefinite-length elements or constructed strings then it converts the BER
// data to DER and sets |*out| and |*out_length| to describe a malloced buffer
// containing the DER data. Additionally, |*in| will be advanced over the BER
// element.
//
// If it doesn't find any indefinite-length elements or constructed strings then
// it sets |*out| to NULL and |*in| is unmodified.
//
// This function should successfully process any valid BER input, however it
// will not convert all of BER's deviations from DER. BER is ambiguous between
// implicitly-tagged SEQUENCEs of strings and implicitly-tagged constructed
// strings. Implicitly-tagged strings must be parsed with
// |CBS_get_ber_implicitly_tagged_string| instead of |CBS_get_asn1|. The caller
// must also account for BER variations in the contents of a primitive.
//
// It returns one on success and zero otherwise.
OPENSSL_EXPORT int CBS_asn1_ber_to_der(CBS *in, uint8_t **out, size_t *out_len);

// CBS_get_asn1_implicit_string parses a BER string of primitive type
// |inner_tag| implicitly-tagged with |outer_tag|. It sets |out| to the
// contents. If concatenation was needed, it sets |*out_storage| to a buffer
// which the caller must release with |OPENSSL_free|. Otherwise, it sets
// |*out_storage| to NULL.
//
// This function does not parse all of BER. It requires the string be
// definite-length. Constructed strings are allowed, but all children of the
// outermost element must be primitive. The caller should use
// |CBS_asn1_ber_to_der| before running this function.
//
// It returns one on success and zero otherwise.
OPENSSL_EXPORT int CBS_get_asn1_implicit_string(CBS *in, CBS *out,
                                                uint8_t **out_storage,
                                                unsigned outer_tag,
                                                unsigned inner_tag);

// CBB_finish_i2d calls |CBB_finish| on |cbb| which must have been initialized
// with |CBB_init|. If |outp| is not NULL then the result is written to |*outp|
// and |*outp| is advanced just past the output. It returns the number of bytes
// in the result, whether written or not, or a negative value on error. On
// error, it calls |CBB_cleanup| on |cbb|.
//
// This function may be used to help implement legacy i2d ASN.1 functions.
int CBB_finish_i2d(CBB *cbb, uint8_t **outp);


#if defined(__cplusplus)
}  // extern C
#endif

#endif  // OPENSSL_HEADER_BYTESTRING_INTERNAL_H
