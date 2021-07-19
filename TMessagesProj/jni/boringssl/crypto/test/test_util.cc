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

#include "test_util.h"

#include <ostream>

#include "../internal.h"


void hexdump(FILE *fp, const char *msg, const void *in, size_t len) {
  const uint8_t *data = reinterpret_cast<const uint8_t*>(in);

  fputs(msg, fp);
  for (size_t i = 0; i < len; i++) {
    fprintf(fp, "%02x", data[i]);
  }
  fputs("\n", fp);
}

std::ostream &operator<<(std::ostream &os, const Bytes &in) {
  if (in.span_.empty()) {
    return os << "<empty Bytes>";
  }

  // Print a byte slice as hex.
  static const char hex[] = "0123456789abcdef";
  for (uint8_t b : in.span_) {
    os << hex[b >> 4];
    os << hex[b & 0xf];
  }
  return os;
}
