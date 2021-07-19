/* Copyright (c) 2015, Google Inc.
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

#include <openssl/bn.h>

#include <openssl/bytestring.h>
#include <openssl/err.h>


int BN_parse_asn1_unsigned(CBS *cbs, BIGNUM *ret) {
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_INTEGER) ||
      CBS_len(&child) == 0) {
    OPENSSL_PUT_ERROR(BN, BN_R_BAD_ENCODING);
    return 0;
  }

  if (CBS_data(&child)[0] & 0x80) {
    OPENSSL_PUT_ERROR(BN, BN_R_NEGATIVE_NUMBER);
    return 0;
  }

  // INTEGERs must be minimal.
  if (CBS_data(&child)[0] == 0x00 &&
      CBS_len(&child) > 1 &&
      !(CBS_data(&child)[1] & 0x80)) {
    OPENSSL_PUT_ERROR(BN, BN_R_BAD_ENCODING);
    return 0;
  }

  return BN_bin2bn(CBS_data(&child), CBS_len(&child), ret) != NULL;
}

int BN_marshal_asn1(CBB *cbb, const BIGNUM *bn) {
  // Negative numbers are unsupported.
  if (BN_is_negative(bn)) {
    OPENSSL_PUT_ERROR(BN, BN_R_NEGATIVE_NUMBER);
    return 0;
  }

  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_INTEGER) ||
      // The number must be padded with a leading zero if the high bit would
      // otherwise be set or if |bn| is zero.
      (BN_num_bits(bn) % 8 == 0 && !CBB_add_u8(&child, 0x00)) ||
      !BN_bn2cbb_padded(&child, BN_num_bytes(bn), bn) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(BN, BN_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}
