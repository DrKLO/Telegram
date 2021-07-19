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

#ifndef OPENSSL_HEADER_CRYPTO_TEST_TEST_UTIL_H
#define OPENSSL_HEADER_CRYPTO_TEST_TEST_UTIL_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <iosfwd>
#include <string>
#include <vector>

#include <openssl/span.h>

#include "../internal.h"


// hexdump writes |msg| to |fp| followed by the hex encoding of |len| bytes
// from |in|.
void hexdump(FILE *fp, const char *msg, const void *in, size_t len);

// Bytes is a wrapper over a byte slice which may be compared for equality. This
// allows it to be used in EXPECT_EQ macros.
struct Bytes {
  Bytes(const uint8_t *data_arg, size_t len_arg)
      : span_(data_arg, len_arg) {}
  Bytes(const char *data_arg, size_t len_arg)
      : span_(reinterpret_cast<const uint8_t *>(data_arg), len_arg) {}

  explicit Bytes(const char *str)
      : span_(reinterpret_cast<const uint8_t *>(str), strlen(str)) {}
  explicit Bytes(const std::string &str)
      : span_(reinterpret_cast<const uint8_t *>(str.data()), str.size()) {}
  explicit Bytes(bssl::Span<const uint8_t> span)
      : span_(span) {}

  bssl::Span<const uint8_t> span_;
};

inline bool operator==(const Bytes &a, const Bytes &b) {
  return a.span_ == b.span_;
}

inline bool operator!=(const Bytes &a, const Bytes &b) { return !(a == b); }

std::ostream &operator<<(std::ostream &os, const Bytes &in);


#endif  // OPENSSL_HEADER_CRYPTO_TEST_TEST_UTIL_H
