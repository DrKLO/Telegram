/* Copyright (c) 2017, Google Inc.
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

#ifndef HEADER_SSL_TEST_FUZZER_TAGS
#define HEADER_SSL_TEST_FUZZER_TAGS

#include <stdint.h>


// SSL fuzzer tag constants.
//
// The TLS client and server fuzzers coordinate with bssl_shim on a common
// format to encode configuration parameters in a fuzzer file. To add a new
// configuration, define a tag, update |SetupTest| in fuzzer.h to parse it, and
// update |WriteSettings| in bssl_shim to serialize it. Finally, record
// transcripts from a test run, and use the BORINGSSL_FUZZER_DEBUG environment
// variable to confirm the transcripts are compatible.

// kDataTag denotes that the remainder of the input should be passed to the TLS
// stack.
static const uint16_t kDataTag = 0;

// kSessionTag is followed by a u24-length-prefixed serialized SSL_SESSION to
// resume.
static const uint16_t kSessionTag = 1;

// kRequestClientCert denotes that the server should request client
// certificates.
static const uint16_t kRequestClientCert = 2;

// kHandoffTag is followed by the output of |SSL_serialize_handoff|.
static const uint16_t kHandoffTag = 3;

// kHandbackTag is followed by te output of |SSL_serialize_handback|.
static const uint16_t kHandbackTag = 4;

#endif  // HEADER_SSL_TEST_FUZZER_TAGS
