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

#ifndef HEADER_PACKETED_BIO
#define HEADER_PACKETED_BIO

#include <openssl/base.h>
#include <openssl/bio.h>

#if defined(OPENSSL_WINDOWS)
OPENSSL_MSVC_PRAGMA(warning(push, 3))
#include <winsock2.h>
OPENSSL_MSVC_PRAGMA(warning(pop))
#else
#include <sys/time.h>
#endif


// PacketedBioCreate creates a filter BIO which implements a reliable in-order
// blocking datagram socket. It uses the value of |*clock| as the clock.
//
// During a |BIO_read|, the peer may signal the filter BIO to simulate a
// timeout. The operation will fail immediately. The caller must then call
// |PacketedBioAdvanceClock| before retrying |BIO_read|.
bssl::UniquePtr<BIO> PacketedBioCreate(timeval *clock);

// PacketedBioAdvanceClock advances |bio|'s clock and returns true if there is a
// pending timeout. Otherwise, it returns false.
bool PacketedBioAdvanceClock(BIO *bio);


#endif  // HEADER_PACKETED_BIO
