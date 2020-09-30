// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <stddef.h>
#include <stdint.h>

#include <string>

#include "base/strings/string_tokenizer.h"

void GetAllTokens(base::StringTokenizer& t) {
  while (t.GetNext()) {
    (void)t.token();
  }
}

// Entry point for LibFuzzer.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  uint8_t size_t_bytes = sizeof(size_t);
  if (size < size_t_bytes + 1) {
    return 0;
  }

  // Calculate pattern size based on remaining bytes, otherwise fuzzing is
  // inefficient with bailouts in most cases.
  size_t pattern_size =
      *reinterpret_cast<const size_t*>(data) % (size - size_t_bytes);

  std::string pattern(reinterpret_cast<const char*>(data + size_t_bytes),
                      pattern_size);
  std::string input(
      reinterpret_cast<const char*>(data + size_t_bytes + pattern_size),
      size - pattern_size - size_t_bytes);

  // Allow quote_chars and options to be set. Otherwise full coverage
  // won't be possible since IsQuote, FullGetNext and other functions
  // won't be called.
  for (bool return_delims : {false, true}) {
    for (bool return_empty_strings : {false, true}) {
      int options = 0;
      if (return_delims)
        options |= base::StringTokenizer::RETURN_DELIMS;
      if (return_empty_strings)
        options |= base::StringTokenizer::RETURN_EMPTY_TOKENS;

      base::StringTokenizer t(input, pattern);
      t.set_options(options);
      GetAllTokens(t);

      base::StringTokenizer t_quote(input, pattern);
      t_quote.set_quote_chars("\"");
      t_quote.set_options(options);
      GetAllTokens(t_quote);
    }
  }

  return 0;
}
