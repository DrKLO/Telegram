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

#include <openssl/bytestring.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/buf.h>
#include <openssl/mem.h>

#include "../internal.h"


void CBB_zero(CBB *cbb) {
  OPENSSL_memset(cbb, 0, sizeof(CBB));
}

static int cbb_init(CBB *cbb, uint8_t *buf, size_t cap) {
  // This assumes that |cbb| has already been zeroed.
  struct cbb_buffer_st *base;

  base = OPENSSL_malloc(sizeof(struct cbb_buffer_st));
  if (base == NULL) {
    return 0;
  }

  base->buf = buf;
  base->len = 0;
  base->cap = cap;
  base->can_resize = 1;
  base->error = 0;

  cbb->base = base;
  cbb->is_child = 0;
  return 1;
}

int CBB_init(CBB *cbb, size_t initial_capacity) {
  CBB_zero(cbb);

  uint8_t *buf = OPENSSL_malloc(initial_capacity);
  if (initial_capacity > 0 && buf == NULL) {
    return 0;
  }

  if (!cbb_init(cbb, buf, initial_capacity)) {
    OPENSSL_free(buf);
    return 0;
  }

  return 1;
}

int CBB_init_fixed(CBB *cbb, uint8_t *buf, size_t len) {
  CBB_zero(cbb);

  if (!cbb_init(cbb, buf, len)) {
    return 0;
  }

  cbb->base->can_resize = 0;
  return 1;
}

void CBB_cleanup(CBB *cbb) {
  // Child |CBB|s are non-owning. They are implicitly discarded and should not
  // be used with |CBB_cleanup| or |ScopedCBB|.
  assert(!cbb->is_child);
  if (cbb->is_child) {
    return;
  }

  if (cbb->base) {
    if (cbb->base->can_resize) {
      OPENSSL_free(cbb->base->buf);
    }
    OPENSSL_free(cbb->base);
  }
  cbb->base = NULL;
}

static int cbb_buffer_reserve(struct cbb_buffer_st *base, uint8_t **out,
                              size_t len) {
  size_t newlen;

  if (base == NULL) {
    return 0;
  }

  newlen = base->len + len;
  if (newlen < base->len) {
    // Overflow
    goto err;
  }

  if (newlen > base->cap) {
    size_t newcap = base->cap * 2;
    uint8_t *newbuf;

    if (!base->can_resize) {
      goto err;
    }

    if (newcap < base->cap || newcap < newlen) {
      newcap = newlen;
    }
    newbuf = OPENSSL_realloc(base->buf, newcap);
    if (newbuf == NULL) {
      goto err;
    }

    base->buf = newbuf;
    base->cap = newcap;
  }

  if (out) {
    *out = base->buf + base->len;
  }

  return 1;

err:
  base->error = 1;
  return 0;
}

static int cbb_buffer_add(struct cbb_buffer_st *base, uint8_t **out,
                          size_t len) {
  if (!cbb_buffer_reserve(base, out, len)) {
    return 0;
  }
  // This will not overflow or |cbb_buffer_reserve| would have failed.
  base->len += len;
  return 1;
}

static int cbb_buffer_add_u(struct cbb_buffer_st *base, uint64_t v,
                            size_t len_len) {
  if (len_len == 0) {
    return 1;
  }

  uint8_t *buf;
  if (!cbb_buffer_add(base, &buf, len_len)) {
    return 0;
  }

  for (size_t i = len_len - 1; i < len_len; i--) {
    buf[i] = v;
    v >>= 8;
  }

  if (v != 0) {
    base->error = 1;
    return 0;
  }

  return 1;
}

int CBB_finish(CBB *cbb, uint8_t **out_data, size_t *out_len) {
  if (cbb->is_child) {
    return 0;
  }

  if (!CBB_flush(cbb)) {
    return 0;
  }

  if (cbb->base->can_resize && (out_data == NULL || out_len == NULL)) {
    // |out_data| and |out_len| can only be NULL if the CBB is fixed.
    return 0;
  }

  if (out_data != NULL) {
    *out_data = cbb->base->buf;
  }
  if (out_len != NULL) {
    *out_len = cbb->base->len;
  }
  cbb->base->buf = NULL;
  CBB_cleanup(cbb);
  return 1;
}

// CBB_flush recurses and then writes out any pending length prefix. The
// current length of the underlying base is taken to be the length of the
// length-prefixed data.
int CBB_flush(CBB *cbb) {
  size_t child_start, i, len;

  // If |cbb->base| has hit an error, the buffer is in an undefined state, so
  // fail all following calls. In particular, |cbb->child| may point to invalid
  // memory.
  if (cbb->base == NULL || cbb->base->error) {
    return 0;
  }

  if (cbb->child == NULL || cbb->child->pending_len_len == 0) {
    return 1;
  }

  child_start = cbb->child->offset + cbb->child->pending_len_len;

  if (!CBB_flush(cbb->child) ||
      child_start < cbb->child->offset ||
      cbb->base->len < child_start) {
    goto err;
  }

  len = cbb->base->len - child_start;

  if (cbb->child->pending_is_asn1) {
    // For ASN.1 we assume that we'll only need a single byte for the length.
    // If that turned out to be incorrect, we have to move the contents along
    // in order to make space.
    uint8_t len_len;
    uint8_t initial_length_byte;

    assert (cbb->child->pending_len_len == 1);

    if (len > 0xfffffffe) {
      // Too large.
      goto err;
    } else if (len > 0xffffff) {
      len_len = 5;
      initial_length_byte = 0x80 | 4;
    } else if (len > 0xffff) {
      len_len = 4;
      initial_length_byte = 0x80 | 3;
    } else if (len > 0xff) {
      len_len = 3;
      initial_length_byte = 0x80 | 2;
    } else if (len > 0x7f) {
      len_len = 2;
      initial_length_byte = 0x80 | 1;
    } else {
      len_len = 1;
      initial_length_byte = (uint8_t)len;
      len = 0;
    }

    if (len_len != 1) {
      // We need to move the contents along in order to make space.
      size_t extra_bytes = len_len - 1;
      if (!cbb_buffer_add(cbb->base, NULL, extra_bytes)) {
        goto err;
      }
      OPENSSL_memmove(cbb->base->buf + child_start + extra_bytes,
                      cbb->base->buf + child_start, len);
    }
    cbb->base->buf[cbb->child->offset++] = initial_length_byte;
    cbb->child->pending_len_len = len_len - 1;
  }

  for (i = cbb->child->pending_len_len - 1; i < cbb->child->pending_len_len;
       i--) {
    cbb->base->buf[cbb->child->offset + i] = (uint8_t)len;
    len >>= 8;
  }
  if (len != 0) {
    goto err;
  }

  cbb->child->base = NULL;
  cbb->child = NULL;

  return 1;

err:
  cbb->base->error = 1;
  return 0;
}

const uint8_t *CBB_data(const CBB *cbb) {
  assert(cbb->child == NULL);
  return cbb->base->buf + cbb->offset + cbb->pending_len_len;
}

size_t CBB_len(const CBB *cbb) {
  assert(cbb->child == NULL);
  assert(cbb->offset + cbb->pending_len_len <= cbb->base->len);

  return cbb->base->len - cbb->offset - cbb->pending_len_len;
}

static int cbb_add_length_prefixed(CBB *cbb, CBB *out_contents,
                                   uint8_t len_len) {
  uint8_t *prefix_bytes;

  if (!CBB_flush(cbb)) {
    return 0;
  }

  size_t offset = cbb->base->len;
  if (!cbb_buffer_add(cbb->base, &prefix_bytes, len_len)) {
    return 0;
  }

  OPENSSL_memset(prefix_bytes, 0, len_len);
  OPENSSL_memset(out_contents, 0, sizeof(CBB));
  out_contents->base = cbb->base;
  out_contents->is_child = 1;
  cbb->child = out_contents;
  cbb->child->offset = offset;
  cbb->child->pending_len_len = len_len;
  cbb->child->pending_is_asn1 = 0;

  return 1;
}

int CBB_add_u8_length_prefixed(CBB *cbb, CBB *out_contents) {
  return cbb_add_length_prefixed(cbb, out_contents, 1);
}

int CBB_add_u16_length_prefixed(CBB *cbb, CBB *out_contents) {
  return cbb_add_length_prefixed(cbb, out_contents, 2);
}

int CBB_add_u24_length_prefixed(CBB *cbb, CBB *out_contents) {
  return cbb_add_length_prefixed(cbb, out_contents, 3);
}

// add_base128_integer encodes |v| as a big-endian base-128 integer where the
// high bit of each byte indicates where there is more data. This is the
// encoding used in DER for both high tag number form and OID components.
static int add_base128_integer(CBB *cbb, uint64_t v) {
  unsigned len_len = 0;
  uint64_t copy = v;
  while (copy > 0) {
    len_len++;
    copy >>= 7;
  }
  if (len_len == 0) {
    len_len = 1;  // Zero is encoded with one byte.
  }
  for (unsigned i = len_len - 1; i < len_len; i--) {
    uint8_t byte = (v >> (7 * i)) & 0x7f;
    if (i != 0) {
      // The high bit denotes whether there is more data.
      byte |= 0x80;
    }
    if (!CBB_add_u8(cbb, byte)) {
      return 0;
    }
  }
  return 1;
}

int CBB_add_asn1(CBB *cbb, CBB *out_contents, unsigned tag) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  // Split the tag into leading bits and tag number.
  uint8_t tag_bits = (tag >> CBS_ASN1_TAG_SHIFT) & 0xe0;
  unsigned tag_number = tag & CBS_ASN1_TAG_NUMBER_MASK;
  if (tag_number >= 0x1f) {
    // Set all the bits in the tag number to signal high tag number form.
    if (!CBB_add_u8(cbb, tag_bits | 0x1f) ||
        !add_base128_integer(cbb, tag_number)) {
      return 0;
    }
  } else if (!CBB_add_u8(cbb, tag_bits | tag_number)) {
    return 0;
  }

  size_t offset = cbb->base->len;
  if (!CBB_add_u8(cbb, 0)) {
    return 0;
  }

  OPENSSL_memset(out_contents, 0, sizeof(CBB));
  out_contents->base = cbb->base;
  out_contents->is_child = 1;
  cbb->child = out_contents;
  cbb->child->offset = offset;
  cbb->child->pending_len_len = 1;
  cbb->child->pending_is_asn1 = 1;

  return 1;
}

int CBB_add_bytes(CBB *cbb, const uint8_t *data, size_t len) {
  uint8_t *dest;

  if (!CBB_flush(cbb) ||
      !cbb_buffer_add(cbb->base, &dest, len)) {
    return 0;
  }
  OPENSSL_memcpy(dest, data, len);
  return 1;
}

int CBB_add_space(CBB *cbb, uint8_t **out_data, size_t len) {
  if (!CBB_flush(cbb) ||
      !cbb_buffer_add(cbb->base, out_data, len)) {
    return 0;
  }
  return 1;
}

int CBB_reserve(CBB *cbb, uint8_t **out_data, size_t len) {
  if (!CBB_flush(cbb) ||
      !cbb_buffer_reserve(cbb->base, out_data, len)) {
    return 0;
  }
  return 1;
}

int CBB_did_write(CBB *cbb, size_t len) {
  size_t newlen = cbb->base->len + len;
  if (cbb->child != NULL ||
      newlen < cbb->base->len ||
      newlen > cbb->base->cap) {
    return 0;
  }
  cbb->base->len = newlen;
  return 1;
}

int CBB_add_u8(CBB *cbb, uint8_t value) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  return cbb_buffer_add_u(cbb->base, value, 1);
}

int CBB_add_u16(CBB *cbb, uint16_t value) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  return cbb_buffer_add_u(cbb->base, value, 2);
}

int CBB_add_u24(CBB *cbb, uint32_t value) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  return cbb_buffer_add_u(cbb->base, value, 3);
}

int CBB_add_u32(CBB *cbb, uint32_t value) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  return cbb_buffer_add_u(cbb->base, value, 4);
}

int CBB_add_u64(CBB *cbb, uint64_t value) {
  if (!CBB_flush(cbb)) {
    return 0;
  }
  return cbb_buffer_add_u(cbb->base, value, 8);
}

void CBB_discard_child(CBB *cbb) {
  if (cbb->child == NULL) {
    return;
  }

  cbb->base->len = cbb->child->offset;

  cbb->child->base = NULL;
  cbb->child = NULL;
}

int CBB_add_asn1_uint64(CBB *cbb, uint64_t value) {
  CBB child;
  int started = 0;

  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_INTEGER)) {
    return 0;
  }

  for (size_t i = 0; i < 8; i++) {
    uint8_t byte = (value >> 8*(7-i)) & 0xff;
    if (!started) {
      if (byte == 0) {
        // Don't encode leading zeros.
        continue;
      }
      // If the high bit is set, add a padding byte to make it
      // unsigned.
      if ((byte & 0x80) && !CBB_add_u8(&child, 0)) {
        return 0;
      }
      started = 1;
    }
    if (!CBB_add_u8(&child, byte)) {
      return 0;
    }
  }

  // 0 is encoded as a single 0, not the empty string.
  if (!started && !CBB_add_u8(&child, 0)) {
    return 0;
  }

  return CBB_flush(cbb);
}

int CBB_add_asn1_octet_string(CBB *cbb, const uint8_t *data, size_t data_len) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_OCTETSTRING) ||
      !CBB_add_bytes(&child, data, data_len) ||
      !CBB_flush(cbb)) {
    return 0;
  }

  return 1;
}

int CBB_add_asn1_bool(CBB *cbb, int value) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_BOOLEAN) ||
      !CBB_add_u8(&child, value != 0 ? 0xff : 0) ||
      !CBB_flush(cbb)) {
    return 0;
  }

  return 1;
}

// parse_dotted_decimal parses one decimal component from |cbs|, where |cbs| is
// an OID literal, e.g., "1.2.840.113554.4.1.72585". It consumes both the
// component and the dot, so |cbs| may be passed into the function again for the
// next value.
static int parse_dotted_decimal(CBS *cbs, uint64_t *out) {
  *out = 0;
  int seen_digit = 0;
  for (;;) {
    // Valid terminators for a component are the end of the string or a
    // non-terminal dot. If the string ends with a dot, this is not a valid OID
    // string.
    uint8_t u;
    if (!CBS_get_u8(cbs, &u) ||
        (u == '.' && CBS_len(cbs) > 0)) {
      break;
    }
    if (u < '0' || u > '9' ||
        // Forbid stray leading zeros.
        (seen_digit && *out == 0) ||
        // Check for overflow.
        *out > UINT64_MAX / 10 ||
        *out * 10 > UINT64_MAX - (u - '0')) {
      return 0;
    }
    *out = *out * 10 + (u - '0');
    seen_digit = 1;
  }
  // The empty string is not a legal OID component.
  return seen_digit;
}

int CBB_add_asn1_oid_from_text(CBB *cbb, const char *text, size_t len) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  CBS cbs;
  CBS_init(&cbs, (const uint8_t *)text, len);

  // OIDs must have at least two components.
  uint64_t a, b;
  if (!parse_dotted_decimal(&cbs, &a) ||
      !parse_dotted_decimal(&cbs, &b)) {
    return 0;
  }

  // The first component is encoded as 40 * |a| + |b|. This assumes that |a| is
  // 0, 1, or 2 and that, when it is 0 or 1, |b| is at most 39.
  if (a > 2 ||
      (a < 2 && b > 39) ||
      b > UINT64_MAX - 80 ||
      !add_base128_integer(cbb, 40u * a + b)) {
    return 0;
  }

  // The remaining components are encoded unmodified.
  while (CBS_len(&cbs) > 0) {
    if (!parse_dotted_decimal(&cbs, &a) ||
        !add_base128_integer(cbb, a)) {
      return 0;
    }
  }

  return 1;
}

static int compare_set_of_element(const void *a_ptr, const void *b_ptr) {
  // See X.690, section 11.6 for the ordering. They are sorted in ascending
  // order by their DER encoding.
  const CBS *a = a_ptr, *b = b_ptr;
  size_t a_len = CBS_len(a), b_len = CBS_len(b);
  size_t min_len = a_len < b_len ? a_len : b_len;
  int ret = OPENSSL_memcmp(CBS_data(a), CBS_data(b), min_len);
  if (ret != 0) {
    return ret;
  }
  if (a_len == b_len) {
    return 0;
  }
  // If one is a prefix of the other, the shorter one sorts first. (This is not
  // actually reachable. No DER encoding is a prefix of another DER encoding.)
  return a_len < b_len ? -1 : 1;
}

int CBB_flush_asn1_set_of(CBB *cbb) {
  if (!CBB_flush(cbb)) {
    return 0;
  }

  CBS cbs;
  size_t num_children = 0;
  CBS_init(&cbs, CBB_data(cbb), CBB_len(cbb));
  while (CBS_len(&cbs) != 0) {
    if (!CBS_get_any_asn1_element(&cbs, NULL, NULL, NULL)) {
      return 0;
    }
    num_children++;
  }

  if (num_children < 2) {
    return 1;  // Nothing to do. This is the common case for X.509.
  }
  if (num_children > ((size_t)-1) / sizeof(CBS)) {
    return 0;  // Overflow.
  }

  // Parse out the children and sort. We alias them into a copy of so they
  // remain valid as we rewrite |cbb|.
  int ret = 0;
  size_t buf_len = CBB_len(cbb);
  uint8_t *buf = BUF_memdup(CBB_data(cbb), buf_len);
  CBS *children = OPENSSL_malloc(num_children * sizeof(CBS));
  if (buf == NULL || children == NULL) {
    goto err;
  }
  CBS_init(&cbs, buf, buf_len);
  for (size_t i = 0; i < num_children; i++) {
    if (!CBS_get_any_asn1_element(&cbs, &children[i], NULL, NULL)) {
      goto err;
    }
  }
  qsort(children, num_children, sizeof(CBS), compare_set_of_element);

  // Rewind |cbb| and write the contents back in the new order.
  cbb->base->len = cbb->offset + cbb->pending_len_len;
  for (size_t i = 0; i < num_children; i++) {
    if (!CBB_add_bytes(cbb, CBS_data(&children[i]), CBS_len(&children[i]))) {
      goto err;
    }
  }
  assert(CBB_len(cbb) == buf_len);

  ret = 1;

err:
  OPENSSL_free(buf);
  OPENSSL_free(children);
  return ret;
}
