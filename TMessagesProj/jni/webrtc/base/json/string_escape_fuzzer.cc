// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/json/string_escape.h"

#include <memory>

// Entry point for LibFuzzer.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < 2)
    return 0;

  const bool put_in_quotes = data[size - 1];

  // Create a copy of input buffer, as otherwise we don't catch
  // overflow that touches the last byte (which is used in put_in_quotes).
  size_t actual_size_char8 = size - 1;
  std::unique_ptr<char[]> input(new char[actual_size_char8]);
  memcpy(input.get(), data, actual_size_char8);

  base::StringPiece input_string(input.get(), actual_size_char8);
  std::string escaped_string;
  base::EscapeJSONString(input_string, put_in_quotes, &escaped_string);

  // Test for wide-strings if available size is even.
  if (actual_size_char8 & 1)
    return 0;

  size_t actual_size_char16 = actual_size_char8 / 2;
  base::StringPiece16 input_string16(
      reinterpret_cast<base::char16*>(input.get()), actual_size_char16);
  escaped_string.clear();
  base::EscapeJSONString(input_string16, put_in_quotes, &escaped_string);

  return 0;
}
