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

#include <openssl/buf.h>
#include <openssl/mem.h>
#include <openssl/bytestring.h>

#include <assert.h>
#include <inttypes.h>
#include <string.h>

#include "internal.h"
#include "../internal.h"


void CBS_init(CBS *cbs, const uint8_t *data, size_t len) {
  cbs->data = data;
  cbs->len = len;
}

static int cbs_get(CBS *cbs, const uint8_t **p, size_t n) {
  if (cbs->len < n) {
    return 0;
  }

  *p = cbs->data;
  cbs->data += n;
  cbs->len -= n;
  return 1;
}

int CBS_skip(CBS *cbs, size_t len) {
  const uint8_t *dummy;
  return cbs_get(cbs, &dummy, len);
}

const uint8_t *CBS_data(const CBS *cbs) {
  return cbs->data;
}

size_t CBS_len(const CBS *cbs) {
  return cbs->len;
}

int CBS_stow(const CBS *cbs, uint8_t **out_ptr, size_t *out_len) {
  OPENSSL_free(*out_ptr);
  *out_ptr = NULL;
  *out_len = 0;

  if (cbs->len == 0) {
    return 1;
  }
  *out_ptr = BUF_memdup(cbs->data, cbs->len);
  if (*out_ptr == NULL) {
    return 0;
  }
  *out_len = cbs->len;
  return 1;
}

int CBS_strdup(const CBS *cbs, char **out_ptr) {
  if (*out_ptr != NULL) {
    OPENSSL_free(*out_ptr);
  }
  *out_ptr = BUF_strndup((const char*)cbs->data, cbs->len);
  return (*out_ptr != NULL);
}

int CBS_contains_zero_byte(const CBS *cbs) {
  return OPENSSL_memchr(cbs->data, 0, cbs->len) != NULL;
}

int CBS_mem_equal(const CBS *cbs, const uint8_t *data, size_t len) {
  if (len != cbs->len) {
    return 0;
  }
  return CRYPTO_memcmp(cbs->data, data, len) == 0;
}

static int cbs_get_u(CBS *cbs, uint64_t *out, size_t len) {
  uint64_t result = 0;
  const uint8_t *data;

  if (!cbs_get(cbs, &data, len)) {
    return 0;
  }
  for (size_t i = 0; i < len; i++) {
    result <<= 8;
    result |= data[i];
  }
  *out = result;
  return 1;
}

int CBS_get_u8(CBS *cbs, uint8_t *out) {
  const uint8_t *v;
  if (!cbs_get(cbs, &v, 1)) {
    return 0;
  }
  *out = *v;
  return 1;
}

int CBS_get_u16(CBS *cbs, uint16_t *out) {
  uint64_t v;
  if (!cbs_get_u(cbs, &v, 2)) {
    return 0;
  }
  *out = v;
  return 1;
}

int CBS_get_u24(CBS *cbs, uint32_t *out) {
  uint64_t v;
  if (!cbs_get_u(cbs, &v, 3)) {
    return 0;
  }
  *out = v;
  return 1;
}

int CBS_get_u32(CBS *cbs, uint32_t *out) {
  uint64_t v;
  if (!cbs_get_u(cbs, &v, 4)) {
    return 0;
  }
  *out = v;
  return 1;
}

int CBS_get_u64(CBS *cbs, uint64_t *out) {
  return cbs_get_u(cbs, out, 8);
}

int CBS_get_last_u8(CBS *cbs, uint8_t *out) {
  if (cbs->len == 0) {
    return 0;
  }
  *out = cbs->data[cbs->len - 1];
  cbs->len--;
  return 1;
}

int CBS_get_bytes(CBS *cbs, CBS *out, size_t len) {
  const uint8_t *v;
  if (!cbs_get(cbs, &v, len)) {
    return 0;
  }
  CBS_init(out, v, len);
  return 1;
}

int CBS_copy_bytes(CBS *cbs, uint8_t *out, size_t len) {
  const uint8_t *v;
  if (!cbs_get(cbs, &v, len)) {
    return 0;
  }
  OPENSSL_memcpy(out, v, len);
  return 1;
}

static int cbs_get_length_prefixed(CBS *cbs, CBS *out, size_t len_len) {
  uint64_t len;
  if (!cbs_get_u(cbs, &len, len_len)) {
    return 0;
  }
  // If |len_len| <= 3 then we know that |len| will fit into a |size_t|, even on
  // 32-bit systems.
  assert(len_len <= 3);
  return CBS_get_bytes(cbs, out, len);
}

int CBS_get_u8_length_prefixed(CBS *cbs, CBS *out) {
  return cbs_get_length_prefixed(cbs, out, 1);
}

int CBS_get_u16_length_prefixed(CBS *cbs, CBS *out) {
  return cbs_get_length_prefixed(cbs, out, 2);
}

int CBS_get_u24_length_prefixed(CBS *cbs, CBS *out) {
  return cbs_get_length_prefixed(cbs, out, 3);
}

// parse_base128_integer reads a big-endian base-128 integer from |cbs| and sets
// |*out| to the result. This is the encoding used in DER for both high tag
// number form and OID components.
static int parse_base128_integer(CBS *cbs, uint64_t *out) {
  uint64_t v = 0;
  uint8_t b;
  do {
    if (!CBS_get_u8(cbs, &b)) {
      return 0;
    }
    if ((v >> (64 - 7)) != 0) {
      // The value is too large.
      return 0;
    }
    if (v == 0 && b == 0x80) {
      // The value must be minimally encoded.
      return 0;
    }
    v = (v << 7) | (b & 0x7f);

    // Values end at an octet with the high bit cleared.
  } while (b & 0x80);

  *out = v;
  return 1;
}

static int parse_asn1_tag(CBS *cbs, unsigned *out) {
  uint8_t tag_byte;
  if (!CBS_get_u8(cbs, &tag_byte)) {
    return 0;
  }

  // ITU-T X.690 section 8.1.2.3 specifies the format for identifiers with a tag
  // number no greater than 30.
  //
  // If the number portion is 31 (0x1f, the largest value that fits in the
  // allotted bits), then the tag is more than one byte long and the
  // continuation bytes contain the tag number. This parser only supports tag
  // numbers less than 31 (and thus single-byte tags).
  unsigned tag = ((unsigned)tag_byte & 0xe0) << CBS_ASN1_TAG_SHIFT;
  unsigned tag_number = tag_byte & 0x1f;
  if (tag_number == 0x1f) {
    uint64_t v;
    if (!parse_base128_integer(cbs, &v) ||
        // Check the tag number is within our supported bounds.
        v > CBS_ASN1_TAG_NUMBER_MASK ||
        // Small tag numbers should have used low tag number form.
        v < 0x1f) {
      return 0;
    }
    tag_number = (unsigned)v;
  }

  tag |= tag_number;

  *out = tag;
  return 1;
}

static int cbs_get_any_asn1_element(CBS *cbs, CBS *out, unsigned *out_tag,
                                    size_t *out_header_len, int ber_ok) {
  CBS header = *cbs;
  CBS throwaway;

  if (out == NULL) {
    out = &throwaway;
  }

  unsigned tag;
  if (!parse_asn1_tag(&header, &tag)) {
    return 0;
  }
  if (out_tag != NULL) {
    *out_tag = tag;
  }

  uint8_t length_byte;
  if (!CBS_get_u8(&header, &length_byte)) {
    return 0;
  }

  size_t header_len = CBS_len(cbs) - CBS_len(&header);

  size_t len;
  // The format for the length encoding is specified in ITU-T X.690 section
  // 8.1.3.
  if ((length_byte & 0x80) == 0) {
    // Short form length.
    len = ((size_t) length_byte) + header_len;
    if (out_header_len != NULL) {
      *out_header_len = header_len;
    }
  } else {
    // The high bit indicate that this is the long form, while the next 7 bits
    // encode the number of subsequent octets used to encode the length (ITU-T
    // X.690 clause 8.1.3.5.b).
    const size_t num_bytes = length_byte & 0x7f;
    uint64_t len64;

    if (ber_ok && (tag & CBS_ASN1_CONSTRUCTED) != 0 && num_bytes == 0) {
      // indefinite length
      if (out_header_len != NULL) {
        *out_header_len = header_len;
      }
      return CBS_get_bytes(cbs, out, header_len);
    }

    // ITU-T X.690 clause 8.1.3.5.c specifies that the value 0xff shall not be
    // used as the first byte of the length. If this parser encounters that
    // value, num_bytes will be parsed as 127, which will fail the check below.
    if (num_bytes == 0 || num_bytes > 4) {
      return 0;
    }
    if (!cbs_get_u(&header, &len64, num_bytes)) {
      return 0;
    }
    // ITU-T X.690 section 10.1 (DER length forms) requires encoding the length
    // with the minimum number of octets.
    if (len64 < 128) {
      // Length should have used short-form encoding.
      return 0;
    }
    if ((len64 >> ((num_bytes-1)*8)) == 0) {
      // Length should have been at least one byte shorter.
      return 0;
    }
    len = len64;
    if (len + header_len + num_bytes < len) {
      // Overflow.
      return 0;
    }
    len += header_len + num_bytes;
    if (out_header_len != NULL) {
      *out_header_len = header_len + num_bytes;
    }
  }

  return CBS_get_bytes(cbs, out, len);
}

int CBS_get_any_asn1(CBS *cbs, CBS *out, unsigned *out_tag) {
  size_t header_len;
  if (!CBS_get_any_asn1_element(cbs, out, out_tag, &header_len)) {
    return 0;
  }

  if (!CBS_skip(out, header_len)) {
    assert(0);
    return 0;
  }

  return 1;
}

int CBS_get_any_asn1_element(CBS *cbs, CBS *out, unsigned *out_tag,
                                    size_t *out_header_len) {
  return cbs_get_any_asn1_element(cbs, out, out_tag, out_header_len,
                                  0 /* DER only */);
}

int CBS_get_any_ber_asn1_element(CBS *cbs, CBS *out, unsigned *out_tag,
                                 size_t *out_header_len) {
  return cbs_get_any_asn1_element(cbs, out, out_tag, out_header_len,
                                  1 /* BER allowed */);
}

static int cbs_get_asn1(CBS *cbs, CBS *out, unsigned tag_value,
                        int skip_header) {
  size_t header_len;
  unsigned tag;
  CBS throwaway;

  if (out == NULL) {
    out = &throwaway;
  }

  if (!CBS_get_any_asn1_element(cbs, out, &tag, &header_len) ||
      tag != tag_value) {
    return 0;
  }

  if (skip_header && !CBS_skip(out, header_len)) {
    assert(0);
    return 0;
  }

  return 1;
}

int CBS_get_asn1(CBS *cbs, CBS *out, unsigned tag_value) {
  return cbs_get_asn1(cbs, out, tag_value, 1 /* skip header */);
}

int CBS_get_asn1_element(CBS *cbs, CBS *out, unsigned tag_value) {
  return cbs_get_asn1(cbs, out, tag_value, 0 /* include header */);
}

int CBS_peek_asn1_tag(const CBS *cbs, unsigned tag_value) {
  if (CBS_len(cbs) < 1) {
    return 0;
  }

  CBS copy = *cbs;
  unsigned actual_tag;
  return parse_asn1_tag(&copy, &actual_tag) && tag_value == actual_tag;
}

int CBS_get_asn1_uint64(CBS *cbs, uint64_t *out) {
  CBS bytes;
  if (!CBS_get_asn1(cbs, &bytes, CBS_ASN1_INTEGER)) {
    return 0;
  }

  *out = 0;
  const uint8_t *data = CBS_data(&bytes);
  size_t len = CBS_len(&bytes);

  if (len == 0) {
    // An INTEGER is encoded with at least one octet.
    return 0;
  }

  if ((data[0] & 0x80) != 0) {
    // Negative number.
    return 0;
  }

  if (data[0] == 0 && len > 1 && (data[1] & 0x80) == 0) {
    // Extra leading zeros.
    return 0;
  }

  for (size_t i = 0; i < len; i++) {
    if ((*out >> 56) != 0) {
      // Too large to represent as a uint64_t.
      return 0;
    }
    *out <<= 8;
    *out |= data[i];
  }

  return 1;
}

int CBS_get_asn1_bool(CBS *cbs, int *out) {
  CBS bytes;
  if (!CBS_get_asn1(cbs, &bytes, CBS_ASN1_BOOLEAN) ||
      CBS_len(&bytes) != 1) {
    return 0;
  }

  const uint8_t value = *CBS_data(&bytes);
  if (value != 0 && value != 0xff) {
    return 0;
  }

  *out = !!value;
  return 1;
}

int CBS_get_optional_asn1(CBS *cbs, CBS *out, int *out_present, unsigned tag) {
  int present = 0;

  if (CBS_peek_asn1_tag(cbs, tag)) {
    if (!CBS_get_asn1(cbs, out, tag)) {
      return 0;
    }
    present = 1;
  }

  if (out_present != NULL) {
    *out_present = present;
  }

  return 1;
}

int CBS_get_optional_asn1_octet_string(CBS *cbs, CBS *out, int *out_present,
                                       unsigned tag) {
  CBS child;
  int present;
  if (!CBS_get_optional_asn1(cbs, &child, &present, tag)) {
    return 0;
  }
  if (present) {
    assert(out);
    if (!CBS_get_asn1(&child, out, CBS_ASN1_OCTETSTRING) ||
        CBS_len(&child) != 0) {
      return 0;
    }
  } else {
    CBS_init(out, NULL, 0);
  }
  if (out_present) {
    *out_present = present;
  }
  return 1;
}

int CBS_get_optional_asn1_uint64(CBS *cbs, uint64_t *out, unsigned tag,
                                 uint64_t default_value) {
  CBS child;
  int present;
  if (!CBS_get_optional_asn1(cbs, &child, &present, tag)) {
    return 0;
  }
  if (present) {
    if (!CBS_get_asn1_uint64(&child, out) ||
        CBS_len(&child) != 0) {
      return 0;
    }
  } else {
    *out = default_value;
  }
  return 1;
}

int CBS_get_optional_asn1_bool(CBS *cbs, int *out, unsigned tag,
                               int default_value) {
  CBS child, child2;
  int present;
  if (!CBS_get_optional_asn1(cbs, &child, &present, tag)) {
    return 0;
  }
  if (present) {
    uint8_t boolean;

    if (!CBS_get_asn1(&child, &child2, CBS_ASN1_BOOLEAN) ||
        CBS_len(&child2) != 1 ||
        CBS_len(&child) != 0) {
      return 0;
    }

    boolean = CBS_data(&child2)[0];
    if (boolean == 0) {
      *out = 0;
    } else if (boolean == 0xff) {
      *out = 1;
    } else {
      return 0;
    }
  } else {
    *out = default_value;
  }
  return 1;
}

int CBS_is_valid_asn1_bitstring(const CBS *cbs) {
  CBS in = *cbs;
  uint8_t num_unused_bits;
  if (!CBS_get_u8(&in, &num_unused_bits) ||
      num_unused_bits > 7) {
    return 0;
  }

  if (num_unused_bits == 0) {
    return 1;
  }

  // All num_unused_bits bits must exist and be zeros.
  uint8_t last;
  if (!CBS_get_last_u8(&in, &last) ||
      (last & ((1 << num_unused_bits) - 1)) != 0) {
    return 0;
  }

  return 1;
}

int CBS_asn1_bitstring_has_bit(const CBS *cbs, unsigned bit) {
  if (!CBS_is_valid_asn1_bitstring(cbs)) {
    return 0;
  }

  const unsigned byte_num = (bit >> 3) + 1;
  const unsigned bit_num = 7 - (bit & 7);

  // Unused bits are zero, and this function does not distinguish between
  // missing and unset bits. Thus it is sufficient to do a byte-level length
  // check.
  return byte_num < CBS_len(cbs) &&
         (CBS_data(cbs)[byte_num] & (1 << bit_num)) != 0;
}

static int add_decimal(CBB *out, uint64_t v) {
  char buf[DECIMAL_SIZE(uint64_t) + 1];
  BIO_snprintf(buf, sizeof(buf), "%" PRIu64, v);
  return CBB_add_bytes(out, (const uint8_t *)buf, strlen(buf));
}

char *CBS_asn1_oid_to_text(const CBS *cbs) {
  CBB cbb;
  if (!CBB_init(&cbb, 32)) {
    goto err;
  }

  CBS copy = *cbs;
  // The first component is 40 * value1 + value2, where value1 is 0, 1, or 2.
  uint64_t v;
  if (!parse_base128_integer(&copy, &v)) {
    goto err;
  }

  if (v >= 80) {
    if (!CBB_add_bytes(&cbb, (const uint8_t *)"2.", 2) ||
        !add_decimal(&cbb, v - 80)) {
      goto err;
    }
  } else if (!add_decimal(&cbb, v / 40) ||
             !CBB_add_u8(&cbb, '.') ||
             !add_decimal(&cbb, v % 40)) {
    goto err;
  }

  while (CBS_len(&copy) != 0) {
    if (!parse_base128_integer(&copy, &v) ||
        !CBB_add_u8(&cbb, '.') ||
        !add_decimal(&cbb, v)) {
      goto err;
    }
  }

  uint8_t *txt;
  size_t txt_len;
  if (!CBB_add_u8(&cbb, '\0') ||
      !CBB_finish(&cbb, &txt, &txt_len)) {
    goto err;
  }

  return (char *)txt;

err:
  CBB_cleanup(&cbb);
  return NULL;
}
