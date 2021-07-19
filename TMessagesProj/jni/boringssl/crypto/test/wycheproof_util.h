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

#ifndef OPENSSL_HEADER_CRYPTO_TEST_WYCHEPROOF_UTIL_H
#define OPENSSL_HEADER_CRYPTO_TEST_WYCHEPROOF_UTIL_H

#include <openssl/base.h>


// This header contains convenience functions for Wycheproof tests.

class FileTest;

enum class WycheproofResult {
  kValid,
  kInvalid,
  kAcceptable,
};

// GetWycheproofResult sets |*out| to the parsed "result" key of |t|.
bool GetWycheproofResult(FileTest *t, WycheproofResult *out);

// GetWycheproofDigest returns a digest function using the Wycheproof name, or
// nullptr on error.
const EVP_MD *GetWycheproofDigest(FileTest *t, const char *key,
                                  bool instruction);

// GetWycheproofCurve returns a curve using the Wycheproof name, or nullptr on
// error.
bssl::UniquePtr<EC_GROUP> GetWycheproofCurve(FileTest *t, const char *key,
                                             bool instruction);

// GetWycheproofBIGNUM returns a BIGNUM in the Wycheproof format, or nullptr on
// error.
bssl::UniquePtr<BIGNUM> GetWycheproofBIGNUM(FileTest *t, const char *key,
                                            bool instruction);


#endif  // OPENSSL_HEADER_CRYPTO_TEST_WYCHEPROOF_UTIL_H
