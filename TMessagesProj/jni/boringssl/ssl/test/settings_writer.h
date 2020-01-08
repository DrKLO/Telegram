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

#ifndef HEADER_SETTINGS_WRITER
#define HEADER_SETTINGS_WRITER

#include <string>

#include <openssl/bytestring.h>
#include <openssl/ssl.h>

#include "../internal.h"
#include "test_config.h"

struct SettingsWriter {
 public:
  SettingsWriter();

  // Init initializes the writer for a new connection, given by |i|.  Each
  // connection gets a unique output file.
  bool Init(int i, const TestConfig *config, SSL_SESSION *session);

  // Commit writes the buffered data to disk.
  bool Commit();

  bool WriteHandoff(bssl::Span<const uint8_t> handoff);

  bool WriteHandback(bssl::Span<const uint8_t> handback);

 private:
  std::string path_;
  bssl::ScopedCBB cbb_;
};

#endif  // HEADER_SETTINGS_WRITER
