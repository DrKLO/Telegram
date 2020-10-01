// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/i18n/icu_string_conversions.h"

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <vector>

#include "base/logging.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "third_party/icu/source/common/unicode/normalizer2.h"
#include "third_party/icu/source/common/unicode/ucnv.h"
#include "third_party/icu/source/common/unicode/ucnv_cb.h"
#include "third_party/icu/source/common/unicode/ucnv_err.h"
#include "third_party/icu/source/common/unicode/ustring.h"

namespace base {

namespace {
// ToUnicodeCallbackSubstitute() is based on UCNV_TO_U_CALLBACK_SUBSTITUTE
// in source/common/ucnv_err.c.

// Copyright (c) 1995-2006 International Business Machines Corporation
// and others
//
// All rights reserved.
//

// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, and/or
// sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, provided that the above copyright notice(s) and
// this permission notice appear in all copies of the Software and that
// both the above copyright notice(s) and this permission notice appear in
// supporting documentation.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
// OF THIRD PARTY RIGHTS. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS
// INCLUDED IN THIS NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT
// OR CONSEQUENTIAL DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
// OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE
// OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE
// OR PERFORMANCE OF THIS SOFTWARE.
//
// Except as contained in this notice, the name of a copyright holder
// shall not be used in advertising or otherwise to promote the sale, use
// or other dealings in this Software without prior written authorization
// of the copyright holder.

//  ___________________________________________________________________________
//
// All trademarks and registered trademarks mentioned herein are the property
// of their respective owners.

void ToUnicodeCallbackSubstitute(const void* context,
                                 UConverterToUnicodeArgs *to_args,
                                 const char* code_units,
                                 int32_t length,
                                 UConverterCallbackReason reason,
                                 UErrorCode * err) {
  static const UChar kReplacementChar = 0xFFFD;
  if (reason <= UCNV_IRREGULAR) {
    if (context == nullptr ||
        (*(reinterpret_cast<const char*>(context)) == 'i' &&
         reason == UCNV_UNASSIGNED)) {
      *err = U_ZERO_ERROR;
      ucnv_cbToUWriteUChars(to_args, &kReplacementChar, 1, 0, err);
      }
      // else the caller must have set the error code accordingly.
  }
  // else ignore the reset, close and clone calls.
}

bool ConvertFromUTF16(UConverter* converter,
                      base::StringPiece16 src,
                      OnStringConversionError::Type on_error,
                      std::string* encoded) {
  int encoded_max_length = UCNV_GET_MAX_BYTES_FOR_STRING(
      src.length(), ucnv_getMaxCharSize(converter));
  encoded->resize(encoded_max_length);

  UErrorCode status = U_ZERO_ERROR;

  // Setup our error handler.
  switch (on_error) {
    case OnStringConversionError::FAIL:
      ucnv_setFromUCallBack(converter, UCNV_FROM_U_CALLBACK_STOP, nullptr,
                            nullptr, nullptr, &status);
      break;
    case OnStringConversionError::SKIP:
    case OnStringConversionError::SUBSTITUTE:
      ucnv_setFromUCallBack(converter, UCNV_FROM_U_CALLBACK_SKIP, nullptr,
                            nullptr, nullptr, &status);
      break;
    default:
      NOTREACHED();
  }

  // ucnv_fromUChars returns size not including terminating null
  int actual_size =
      ucnv_fromUChars(converter, &(*encoded)[0], encoded_max_length, src.data(),
                      src.length(), &status);
  encoded->resize(actual_size);
  ucnv_close(converter);
  if (U_SUCCESS(status))
    return true;
  encoded->clear();  // Make sure the output is empty on error.
  return false;
}

// Set up our error handler for ToUTF-16 converters
void SetUpErrorHandlerForToUChars(OnStringConversionError::Type on_error,
                                  UConverter* converter, UErrorCode* status) {
  switch (on_error) {
    case OnStringConversionError::FAIL:
      ucnv_setToUCallBack(converter, UCNV_TO_U_CALLBACK_STOP, nullptr, nullptr,
                          nullptr, status);
      break;
    case OnStringConversionError::SKIP:
      ucnv_setToUCallBack(converter, UCNV_TO_U_CALLBACK_SKIP, nullptr, nullptr,
                          nullptr, status);
      break;
    case OnStringConversionError::SUBSTITUTE:
      ucnv_setToUCallBack(converter, ToUnicodeCallbackSubstitute, nullptr,
                          nullptr, nullptr, status);
      break;
    default:
      NOTREACHED();
  }
}

}  // namespace

// Codepage <-> Wide/UTF-16  ---------------------------------------------------

bool UTF16ToCodepage(base::StringPiece16 utf16,
                     const char* codepage_name,
                     OnStringConversionError::Type on_error,
                     std::string* encoded) {
  encoded->clear();

  UErrorCode status = U_ZERO_ERROR;
  UConverter* converter = ucnv_open(codepage_name, &status);
  if (!U_SUCCESS(status))
    return false;

  return ConvertFromUTF16(converter, utf16, on_error, encoded);
}

bool CodepageToUTF16(base::StringPiece encoded,
                     const char* codepage_name,
                     OnStringConversionError::Type on_error,
                     string16* utf16) {
  utf16->clear();

  UErrorCode status = U_ZERO_ERROR;
  UConverter* converter = ucnv_open(codepage_name, &status);
  if (!U_SUCCESS(status))
    return false;

  // Even in the worst case, the maximum length in 2-byte units of UTF-16
  // output would be at most the same as the number of bytes in input. There
  // is no single-byte encoding in which a character is mapped to a
  // non-BMP character requiring two 2-byte units.
  //
  // Moreover, non-BMP characters in legacy multibyte encodings
  // (e.g. EUC-JP, GB18030) take at least 2 bytes. The only exceptions are
  // BOCU and SCSU, but we don't care about them.
  size_t uchar_max_length = encoded.length() + 1;

  SetUpErrorHandlerForToUChars(on_error, converter, &status);
  std::unique_ptr<char16[]> buffer(new char16[uchar_max_length]);
  int actual_size = ucnv_toUChars(converter, buffer.get(),
      static_cast<int>(uchar_max_length), encoded.data(),
      static_cast<int>(encoded.length()), &status);
  ucnv_close(converter);
  if (!U_SUCCESS(status)) {
    utf16->clear();  // Make sure the output is empty on error.
    return false;
  }

  utf16->assign(buffer.get(), actual_size);
  return true;
}

bool ConvertToUtf8AndNormalize(base::StringPiece text,
                               const std::string& charset,
                               std::string* result) {
  result->clear();
  string16 utf16;
  if (!CodepageToUTF16(text, charset.c_str(), OnStringConversionError::FAIL,
                       &utf16))
    return false;

  UErrorCode status = U_ZERO_ERROR;
  const icu::Normalizer2* normalizer = icu::Normalizer2::getNFCInstance(status);
  DCHECK(U_SUCCESS(status));
  if (U_FAILURE(status))
    return false;
  int32_t utf16_length = static_cast<int32_t>(utf16.length());
  icu::UnicodeString normalized(utf16.data(), utf16_length);
  int32_t normalized_prefix_length =
      normalizer->spanQuickCheckYes(normalized, status);
  if (normalized_prefix_length < utf16_length) {
    icu::UnicodeString un_normalized(normalized, normalized_prefix_length);
    normalized.truncate(normalized_prefix_length);
    normalizer->normalizeSecondAndAppend(normalized, un_normalized, status);
  }
  if (U_FAILURE(status))
    return false;
  normalized.toUTF8String(*result);
  return true;
}

}  // namespace base
