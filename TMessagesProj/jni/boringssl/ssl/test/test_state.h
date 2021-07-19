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

#ifndef HEADER_TEST_STATE
#define HEADER_TEST_STATE

#include <memory>
#include <string>
#include <vector>

#include <openssl/base.h>

struct TestState {
  // Serialize writes |pending_session| and |msg_callback_text| to |out|, for
  // use in split-handshake tests.  We don't try to serialize every bit of test
  // state, but serializing |pending_session| is necessary to exercise session
  // resumption, and |msg_callback_text| is especially useful.  In the general
  // case, checks of state updated during the handshake can be skipped when
  // |config->handoff|.
  bool Serialize(CBB *out) const;

  // Deserialize returns a new |TestState| from data written by |Serialize|.
  static std::unique_ptr<TestState> Deserialize(CBS *cbs, SSL_CTX *ctx);

  // async_bio is async BIO which pauses reads and writes.
  BIO *async_bio = nullptr;
  // packeted_bio is the packeted BIO which simulates read timeouts.
  BIO *packeted_bio = nullptr;
  bssl::UniquePtr<EVP_PKEY> channel_id;
  bool cert_ready = false;
  bssl::UniquePtr<SSL_SESSION> session;
  bssl::UniquePtr<SSL_SESSION> pending_session;
  bool early_callback_called = false;
  bool handshake_done = false;
  // private_key is the underlying private key used when testing custom keys.
  bssl::UniquePtr<EVP_PKEY> private_key;
  std::vector<uint8_t> private_key_result;
  // private_key_retries is the number of times an asynchronous private key
  // operation has been retried.
  unsigned private_key_retries = 0;
  bool got_new_session = false;
  bssl::UniquePtr<SSL_SESSION> new_session;
  bool ticket_decrypt_done = false;
  bool alpn_select_done = false;
  bool is_resume = false;
  bool early_callback_ready = false;
  bool custom_verify_ready = false;
  std::string msg_callback_text;
  bool msg_callback_ok = true;
  // cert_verified is true if certificate verification has been driven to
  // completion. This tests that the callback is not called again after this.
  bool cert_verified = false;
};

bool SetTestState(SSL *ssl, std::unique_ptr<TestState> state);

TestState *GetTestState(const SSL *ssl);

struct timeval *GetClock();

void AdvanceClock(unsigned seconds);

void CopySessions(SSL_CTX *dest, const SSL_CTX *src);

// SerializeContextState writes session material (sessions and ticket keys) from
// |ctx| into |cbb|.
bool SerializeContextState(SSL_CTX *ctx, CBB *cbb);

// DeserializeContextState updates |out| with material previously serialized by
// SerializeContextState.
bool DeserializeContextState(CBS *in, SSL_CTX *out);

#endif  // HEADER_TEST_STATE
