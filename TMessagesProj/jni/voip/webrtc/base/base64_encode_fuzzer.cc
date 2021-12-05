// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include "base/base64.h"
#include "base/logging.h"
#include "base/strings/string_piece.h"

// Encode some random data, and then decode it.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  base::span<const uint8_t> data_span(data, size);
  base::StringPiece data_piece(reinterpret_cast<const char*>(data), size);

  const std::string encode_output = base::Base64Encode(data_span);
  std::string decode_output;
  CHECK(base::Base64Decode(encode_output, &decode_output));
  CHECK_EQ(data_piece, decode_output);

  // Also run the StringPiece variant and check that it gives the same results.
  std::string string_piece_encode_output;
  base::Base64Encode(data_piece, &string_piece_encode_output);
  CHECK_EQ(encode_output, string_piece_encode_output);

  return 0;
}
