// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/macros.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"

std::string output_std_string;
std::wstring output_std_wstring;
base::string16 output_string16;

// Entry point for LibFuzzer.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  base::StringPiece string_piece_input(reinterpret_cast<const char*>(data),
                                       size);

  ignore_result(base::UTF8ToWide(string_piece_input));
  base::UTF8ToWide(reinterpret_cast<const char*>(data), size,
                   &output_std_wstring);
  ignore_result(base::UTF8ToUTF16(string_piece_input));
  base::UTF8ToUTF16(reinterpret_cast<const char*>(data), size,
                    &output_string16);

  // Test for char16.
  if (size % 2 == 0) {
    base::StringPiece16 string_piece_input16(
        reinterpret_cast<const base::char16*>(data), size / 2);
    ignore_result(base::UTF16ToWide(output_string16));
    base::UTF16ToWide(reinterpret_cast<const base::char16*>(data), size / 2,
                      &output_std_wstring);
    ignore_result(base::UTF16ToUTF8(string_piece_input16));
    base::UTF16ToUTF8(reinterpret_cast<const base::char16*>(data), size / 2,
                      &output_std_string);
  }

  // Test for wchar_t.
  size_t wchar_t_size = sizeof(wchar_t);
  if (size % wchar_t_size == 0) {
    ignore_result(base::WideToUTF8(output_std_wstring));
    base::WideToUTF8(reinterpret_cast<const wchar_t*>(data),
                     size / wchar_t_size, &output_std_string);
    ignore_result(base::WideToUTF16(output_std_wstring));
    base::WideToUTF16(reinterpret_cast<const wchar_t*>(data),
                      size / wchar_t_size, &output_string16);
  }

  // Test for ASCII. This condition is needed to avoid hitting instant CHECK
  // failures.
  if (base::IsStringASCII(string_piece_input)) {
    output_string16 = base::ASCIIToUTF16(string_piece_input);
    base::StringPiece16 string_piece_input16(output_string16);
    ignore_result(base::UTF16ToASCII(string_piece_input16));
  }

  return 0;
}
