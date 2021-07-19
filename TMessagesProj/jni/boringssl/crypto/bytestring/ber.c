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

#include "internal.h"
#include "../internal.h"


// kMaxDepth is a just a sanity limit. The code should be such that the length
// of the input being processes always decreases. None the less, a very large
// input could otherwise cause the stack to overflow.
static const unsigned kMaxDepth = 2048;

// is_string_type returns one if |tag| is a string type and zero otherwise. It
// ignores the constructed bit.
static int is_string_type(unsigned tag) {
  switch (tag & ~CBS_ASN1_CONSTRUCTED) {
    case CBS_ASN1_BITSTRING:
    case CBS_ASN1_OCTETSTRING:
    case CBS_ASN1_UTF8STRING:
    case CBS_ASN1_NUMERICSTRING:
    case CBS_ASN1_PRINTABLESTRING:
    case CBS_ASN1_T61STRING:
    case CBS_ASN1_VIDEOTEXSTRING:
    case CBS_ASN1_IA5STRING:
    case CBS_ASN1_GRAPHICSTRING:
    case CBS_ASN1_VISIBLESTRING:
    case CBS_ASN1_GENERALSTRING:
    case CBS_ASN1_UNIVERSALSTRING:
    case CBS_ASN1_BMPSTRING:
      return 1;
    default:
      return 0;
  }
}

// cbs_find_ber walks an ASN.1 structure in |orig_in| and sets |*ber_found|
// depending on whether an indefinite length element or constructed string was
// found. The value of |orig_in| is not changed. It returns one on success (i.e.
// |*ber_found| was set) and zero on error.
static int cbs_find_ber(const CBS *orig_in, char *ber_found, unsigned depth) {
  CBS in;

  if (depth > kMaxDepth) {
    return 0;
  }

  CBS_init(&in, CBS_data(orig_in), CBS_len(orig_in));
  *ber_found = 0;

  while (CBS_len(&in) > 0) {
    CBS contents;
    unsigned tag;
    size_t header_len;

    if (!CBS_get_any_ber_asn1_element(&in, &contents, &tag, &header_len)) {
      return 0;
    }
    if (CBS_len(&contents) == header_len &&
        header_len > 0 &&
        CBS_data(&contents)[header_len-1] == 0x80) {
      // Found an indefinite-length element.
      *ber_found = 1;
      return 1;
    }
    if (tag & CBS_ASN1_CONSTRUCTED) {
      if (is_string_type(tag)) {
        // Constructed strings are only legal in BER and require conversion.
        *ber_found = 1;
        return 1;
      }
      if (!CBS_skip(&contents, header_len) ||
          !cbs_find_ber(&contents, ber_found, depth + 1)) {
        return 0;
      }
    }
  }

  return 1;
}

// is_eoc returns true if |header_len| and |contents|, as returned by
// |CBS_get_any_ber_asn1_element|, indicate an "end of contents" (EOC) value.
static char is_eoc(size_t header_len, CBS *contents) {
  return header_len == 2 && CBS_len(contents) == 2 &&
         OPENSSL_memcmp(CBS_data(contents), "\x00\x00", 2) == 0;
}

// cbs_convert_ber reads BER data from |in| and writes DER data to |out|. If
// |string_tag| is non-zero, then all elements must match |string_tag| up to the
// constructed bit and primitive element bodies are written to |out| without
// element headers. This is used when concatenating the fragments of a
// constructed string. If |looking_for_eoc| is set then any EOC elements found
// will cause the function to return after consuming it. It returns one on
// success and zero on error.
static int cbs_convert_ber(CBS *in, CBB *out, unsigned string_tag,
                           char looking_for_eoc, unsigned depth) {
  assert(!(string_tag & CBS_ASN1_CONSTRUCTED));

  if (depth > kMaxDepth) {
    return 0;
  }

  while (CBS_len(in) > 0) {
    CBS contents;
    unsigned tag, child_string_tag = string_tag;
    size_t header_len;
    CBB *out_contents, out_contents_storage;

    if (!CBS_get_any_ber_asn1_element(in, &contents, &tag, &header_len)) {
      return 0;
    }

    if (is_eoc(header_len, &contents)) {
      return looking_for_eoc;
    }

    if (string_tag != 0) {
      // This is part of a constructed string. All elements must match
      // |string_tag| up to the constructed bit and get appended to |out|
      // without a child element.
      if ((tag & ~CBS_ASN1_CONSTRUCTED) != string_tag) {
        return 0;
      }
      out_contents = out;
    } else {
      unsigned out_tag = tag;
      if ((tag & CBS_ASN1_CONSTRUCTED) && is_string_type(tag)) {
        // If a constructed string, clear the constructed bit and inform
        // children to concatenate bodies.
        out_tag &= ~CBS_ASN1_CONSTRUCTED;
        child_string_tag = out_tag;
      }
      if (!CBB_add_asn1(out, &out_contents_storage, out_tag)) {
        return 0;
      }
      out_contents = &out_contents_storage;
    }

    if (CBS_len(&contents) == header_len && header_len > 0 &&
        CBS_data(&contents)[header_len - 1] == 0x80) {
      // This is an indefinite length element.
      if (!cbs_convert_ber(in, out_contents, child_string_tag,
                           1 /* looking for eoc */, depth + 1) ||
          !CBB_flush(out)) {
        return 0;
      }
      continue;
    }

    if (!CBS_skip(&contents, header_len)) {
      return 0;
    }

    if (tag & CBS_ASN1_CONSTRUCTED) {
      // Recurse into children.
      if (!cbs_convert_ber(&contents, out_contents, child_string_tag,
                           0 /* not looking for eoc */, depth + 1)) {
        return 0;
      }
    } else {
      // Copy primitive contents as-is.
      if (!CBB_add_bytes(out_contents, CBS_data(&contents),
                         CBS_len(&contents))) {
        return 0;
      }
    }

    if (!CBB_flush(out)) {
      return 0;
    }
  }

  return looking_for_eoc == 0;
}

int CBS_asn1_ber_to_der(CBS *in, CBS *out, uint8_t **out_storage) {
  CBB cbb;

  // First, do a quick walk to find any indefinite-length elements. Most of the
  // time we hope that there aren't any and thus we can quickly return.
  char conversion_needed;
  if (!cbs_find_ber(in, &conversion_needed, 0)) {
    return 0;
  }

  if (!conversion_needed) {
    if (!CBS_get_any_asn1_element(in, out, NULL, NULL)) {
      return 0;
    }
    *out_storage = NULL;
    return 1;
  }

  size_t len;
  if (!CBB_init(&cbb, CBS_len(in)) ||
      !cbs_convert_ber(in, &cbb, 0, 0, 0) ||
      !CBB_finish(&cbb, out_storage, &len)) {
    CBB_cleanup(&cbb);
    return 0;
  }

  CBS_init(out, *out_storage, len);
  return 1;
}

int CBS_get_asn1_implicit_string(CBS *in, CBS *out, uint8_t **out_storage,
                                 unsigned outer_tag, unsigned inner_tag) {
  assert(!(outer_tag & CBS_ASN1_CONSTRUCTED));
  assert(!(inner_tag & CBS_ASN1_CONSTRUCTED));
  assert(is_string_type(inner_tag));

  if (CBS_peek_asn1_tag(in, outer_tag)) {
    // Normal implicitly-tagged string.
    *out_storage = NULL;
    return CBS_get_asn1(in, out, outer_tag);
  }

  // Otherwise, try to parse an implicitly-tagged constructed string.
  // |CBS_asn1_ber_to_der| is assumed to have run, so only allow one level deep
  // of nesting.
  CBB result;
  CBS child;
  if (!CBB_init(&result, CBS_len(in)) ||
      !CBS_get_asn1(in, &child, outer_tag | CBS_ASN1_CONSTRUCTED)) {
    goto err;
  }

  while (CBS_len(&child) > 0) {
    CBS chunk;
    if (!CBS_get_asn1(&child, &chunk, inner_tag) ||
        !CBB_add_bytes(&result, CBS_data(&chunk), CBS_len(&chunk))) {
      goto err;
    }
  }

  uint8_t *data;
  size_t len;
  if (!CBB_finish(&result, &data, &len)) {
    goto err;
  }

  CBS_init(out, data, len);
  *out_storage = data;
  return 1;

err:
  CBB_cleanup(&result);
  return 0;
}
