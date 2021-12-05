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

#ifndef HEADER_TEST_HANDSHAKE
#define HEADER_TEST_HANDSHAKE

#include <functional>

#include <openssl/base.h>

#include "settings_writer.h"

// RetryAsync is called after a failed operation on |ssl| with return code
// |ret|. If the operation should be retried, it simulates one asynchronous
// event and returns true. Otherwise it returns false.
bool RetryAsync(SSL *ssl, int ret);

// CheckIdempotentError runs |func|, an operation on |ssl|, ensuring that
// errors are idempotent.
int CheckIdempotentError(const char *name, SSL *ssl, std::function<int()> func);

// DoSplitHandshake delegates the SSL handshake to a separate process, called
// the handshaker.  This process proxies I/O between the handshaker and the
// client, using the |BIO| from |ssl|.  After a successful handshake, |ssl| is
// replaced with a new |SSL| object, in a way that is intended to be invisible
// to the caller.
bool DoSplitHandshake(bssl::UniquePtr<SSL> *ssl, SettingsWriter *writer,
                      bool is_resume);

// The protocol between the proxy and the handshaker is defined by these
// single-character prefixes.
constexpr char kControlMsgWantRead = 'R';        // Handshaker wants data
constexpr char kControlMsgWriteCompleted = 'W';  // Proxy has sent data
constexpr char kControlMsgHandback = 'H';        // Proxy should resume control
constexpr char kControlMsgError = 'E';           // Handshaker hit an error

// The protocol between the proxy and handshaker uses these file descriptors.
constexpr int kFdControl = 3;                    // Bi-directional dgram socket.
constexpr int kFdProxyToHandshaker = 4;          // Uni-directional pipe.
constexpr int kFdHandshakerToProxy = 5;          // Uni-directional pipe.

#endif  // HEADER_TEST_HANDSHAKE
