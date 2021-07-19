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

#include "settings_writer.h"

#include <stdio.h>

#include <openssl/ssl.h>

#include "fuzzer_tags.h"
#include "test_config.h"


SettingsWriter::SettingsWriter() {}

bool SettingsWriter::Init(int i, const TestConfig *config,
                          SSL_SESSION *session) {
  if (config->write_settings.empty()) {
    return true;
  }
  // Treat write_settings as a path prefix for each connection in the run.
  char buf[DECIMAL_SIZE(int)];
  snprintf(buf, sizeof(buf), "%d", i);
  path_ = config->write_settings + buf;

  if (!CBB_init(cbb_.get(), 64)) {
    return false;
  }

  if (session != nullptr) {
    uint8_t *data;
    size_t len;
    if (!SSL_SESSION_to_bytes(session, &data, &len)) {
      return false;
    }
    bssl::UniquePtr<uint8_t> free_data(data);
    CBB child;
    if (!CBB_add_u16(cbb_.get(), kSessionTag) ||
        !CBB_add_u24_length_prefixed(cbb_.get(), &child) ||
        !CBB_add_bytes(&child, data, len) || !CBB_flush(cbb_.get())) {
      return false;
    }
  }

  if (config->is_server &&
      (config->require_any_client_certificate || config->verify_peer) &&
      !CBB_add_u16(cbb_.get(), kRequestClientCert)) {
    return false;
  }

  return true;
}

bool SettingsWriter::Commit() {
  if (path_.empty()) {
    return true;
  }

  uint8_t *settings;
  size_t settings_len;
  if (!CBB_add_u16(cbb_.get(), kDataTag) ||
      !CBB_finish(cbb_.get(), &settings, &settings_len)) {
    return false;
  }
  bssl::UniquePtr<uint8_t> free_settings(settings);

  using ScopedFILE = std::unique_ptr<FILE, decltype(&fclose)>;
  ScopedFILE file(fopen(path_.c_str(), "w"), fclose);
  if (!file) {
    return false;
  }

  return fwrite(settings, settings_len, 1, file.get()) == 1;
}

bool SettingsWriter::WriteHandoff(bssl::Span<const uint8_t> handoff) {
  if (path_.empty()) {
    return true;
  }

  CBB child;
  if (!CBB_add_u16(cbb_.get(), kHandoffTag) ||
      !CBB_add_u24_length_prefixed(cbb_.get(), &child) ||
      !CBB_add_bytes(&child, handoff.data(), handoff.size()) ||
      !CBB_flush(cbb_.get())) {
    return false;
  }
  return true;
}

bool SettingsWriter::WriteHandback(bssl::Span<const uint8_t> handback) {
  if (path_.empty()) {
    return true;
  }

  CBB child;
  if (!CBB_add_u16(cbb_.get(), kHandbackTag) ||
      !CBB_add_u24_length_prefixed(cbb_.get(), &child) ||
      !CBB_add_bytes(&child, handback.data(), handback.size()) ||
      !CBB_flush(cbb_.get())) {
    return false;
  }
  return true;
}
