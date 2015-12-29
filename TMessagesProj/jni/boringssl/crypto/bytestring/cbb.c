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
#include <string.h>

#include <openssl/mem.h>


void CBB_zero(CBB *cbb) {
  memset(cbb, 0, sizeof(CBB));
}

static int cbb_init(CBB *cbb, uint8_t *buf, size_t cap) {
  struct cbb_buffer_st *base;

  base = OPENSSL_malloc(sizeof(struct cbb_buffer_st));
  if (base == NULL) {
    return 0;
  }

  base->buf = buf;
  base->len = 0;
  base->cap = cap;
  base->can_resize = 1;

  memset(cbb, 0, sizeof(CBB));
  cbb->base = base;
  cbb->is_top_level = 1;
  return 1;
}

int CBB_init(CBB *cbb, size_t initial_capacity) {
  uint8_t *buf;

  buf = OPENSSL_malloc(initial_capacity);
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
  if (!cbb_init(cbb, buf, len)) {
    return 0;
  }

  cbb->base->can_resize = 0;
  return 1;
}

void CBB_cleanup(CBB *cbb) {
  if (cbb->base) {
    if (cbb->base->can_resize) {
      OPENSSL_free(cbb->base->buf);
    }
    OPENSSL_free(cbb->base);
  }
  cbb->base = NULL;
}

static int cbb_buffer_add(struct cbb_buffer_st *base, uint8_t **out,
                          size_t len) {
  size_t newlen;

  if (base == NULL) {
    return 0;
  }

  newlen = base->len + len;
  if (newlen < base->len) {
    /* Overflow */
    return 0;
  }

  if (newlen > base->cap) {
    size_t newcap = base->cap * 2;
    uint8_t *newbuf;

    if (!base->can_resize) {
      return 0;
    }

    if (newcap < base->cap || newcap < newlen) {
      newcap = newlen;
    }
    newbuf = OPENSSL_realloc(base->buf, newcap);
    if (newbuf == NULL) {
      return 0;
    }

    base->buf = newbuf;
    base->cap = newcap;
  }

  if (out) {
    *out = base->buf + base->len;
  }
  base->len = newlen;
  return 1;
}

static int cbb_buffer_add_u(struct cbb_buffer_st *base, uint32_t v,
                            size_t len_len) {
  uint8_t *buf;
  size_t i;

  if (len_len == 0) {
    return 1;
  }
  if (!cbb_buffer_add(base, &buf, len_len)) {
    return 0;
  }

  for (i = len_len - 1; i < len_len; i--) {
    buf[i] = v;
    v >>= 8;
  }
  return 1;
}

int CBB_finish(CBB *cbb, uint8_t **out_data, size_t *out_len) {
  if (!cbb->is_top_level) {
    return 0;
  }

  if (!CBB_flush(cbb)) {
    return 0;
  }

  if (cbb->base->can_resize && (out_data == NULL || out_len == NULL)) {
    /* |out_data| and |out_len| can only be NULL if the CBB is fixed. */
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

/* CBB_flush recurses and then writes out any pending length prefix. The
 * current length of the underlying base is taken to be the length of the
 * length-prefixed data. */
int CBB_flush(CBB *cbb) {
  size_t child_start, i, len;

  if (cbb->base == NULL) {
    return 0;
  }

  if (cbb->child == NULL || cbb->pending_len_len == 0) {
    return 1;
  }

  child_start = cbb->offset + cbb->pending_len_len;

  if (!CBB_flush(cbb->child) ||
      child_start < cbb->offset ||
      cbb->base->len < child_start) {
    return 0;
  }

  len = cbb->base->len - child_start;

  if (cbb->pending_is_asn1) {
    /* For ASN.1 we assume that we'll only need a single byte for the length.
     * If that turned out to be incorrect, we have to move the contents along
     * in order to make space. */
    size_t len_len;
    uint8_t initial_length_byte;

    assert (cbb->pending_len_len == 1);

    if (len > 0xfffffffe) {
      /* Too large. */
      return 0;
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
      initial_length_byte = len;
      len = 0;
    }

    if (len_len != 1) {
      /* We need to move the contents along in order to make space. */
      size_t extra_bytes = len_len - 1;
      if (!cbb_buffer_add(cbb->base, NULL, extra_bytes)) {
        return 0;
      }
      memmove(cbb->base->buf + child_start + extra_bytes,
              cbb->base->buf + child_start, len);
    }
    cbb->base->buf[cbb->offset++] = initial_length_byte;
    cbb->pending_len_len = len_len - 1;
  }

  for (i = cbb->pending_len_len - 1; i < cbb->pending_len_len; i--) {
    cbb->base->buf[cbb->offset + i] = len;
    len >>= 8;
  }
  if (len != 0) {
    return 0;
  }

  cbb->child->base = NULL;
  cbb->child = NULL;
  cbb->pending_len_len = 0;
  cbb->pending_is_asn1 = 0;
  cbb->offset = 0;

  return 1;
}

size_t CBB_len(const CBB *cbb) {
  assert(cbb->child == NULL);

  return cbb->base->len;
}

static int cbb_add_length_prefixed(CBB *cbb, CBB *out_contents,
                                   size_t len_len) {
  uint8_t *prefix_bytes;

  if (!CBB_flush(cbb)) {
    return 0;
  }

  cbb->offset = cbb->base->len;
  if (!cbb_buffer_add(cbb->base, &prefix_bytes, len_len)) {
    return 0;
  }

  memset(prefix_bytes, 0, len_len);
  memset(out_contents, 0, sizeof(CBB));
  out_contents->base = cbb->base;
  cbb->child = out_contents;
  cbb->pending_len_len = len_len;
  cbb->pending_is_asn1 = 0;

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

int CBB_add_asn1(CBB *cbb, CBB *out_contents, uint8_t tag) {
  if ((tag & 0x1f) == 0x1f) {
    /* Long form identifier octets are not supported. */
    return 0;
  }

  if (!CBB_flush(cbb) ||
      !CBB_add_u8(cbb, tag)) {
    return 0;
  }

  cbb->offset = cbb->base->len;
  if (!CBB_add_u8(cbb, 0)) {
    return 0;
  }

  memset(out_contents, 0, sizeof(CBB));
  out_contents->base = cbb->base;
  cbb->child = out_contents;
  cbb->pending_len_len = 1;
  cbb->pending_is_asn1 = 1;

  return 1;
}

int CBB_add_bytes(CBB *cbb, const uint8_t *data, size_t len) {
  uint8_t *dest;

  if (!CBB_flush(cbb) ||
      !cbb_buffer_add(cbb->base, &dest, len)) {
    return 0;
  }
  memcpy(dest, data, len);
  return 1;
}

int CBB_add_space(CBB *cbb, uint8_t **out_data, size_t len) {
  if (!CBB_flush(cbb) ||
      !cbb_buffer_add(cbb->base, out_data, len)) {
    return 0;
  }
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

int CBB_add_asn1_uint64(CBB *cbb, uint64_t value) {
  CBB child;
  size_t i;
  int started = 0;

  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_INTEGER)) {
    return 0;
  }

  for (i = 0; i < 8; i++) {
    uint8_t byte = (value >> 8*(7-i)) & 0xff;
    if (!started) {
      if (byte == 0) {
        /* Don't encode leading zeros. */
        continue;
      }
      /* If the high bit is set, add a padding byte to make it
       * unsigned. */
      if ((byte & 0x80) && !CBB_add_u8(&child, 0)) {
        return 0;
      }
      started = 1;
    }
    if (!CBB_add_u8(&child, byte)) {
      return 0;
    }
  }

  /* 0 is encoded as a single 0, not the empty string. */
  if (!started && !CBB_add_u8(&child, 0)) {
    return 0;
  }

  return CBB_flush(cbb);
}
