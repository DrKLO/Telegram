// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include "base/base64.h"
#include "base/strings/string_piece.h"

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  std::string decode_output;
  base::StringPiece data_piece(reinterpret_cast<const char*>(data), size);
  base::Base64Decode(data_piece, &decode_output);
  return 0;
}
