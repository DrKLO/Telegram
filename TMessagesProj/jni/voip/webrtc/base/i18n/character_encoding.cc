// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/i18n/character_encoding.h"

#include "base/macros.h"
#include "third_party/icu/source/common/unicode/ucnv.h"

namespace base {
namespace {

// An array of all supported canonical encoding names.
const char* const kCanonicalEncodingNames[] = {
    "Big5",         "EUC-JP",       "EUC-KR",       "gb18030",
    "GBK",          "IBM866",       "ISO-2022-JP",  "ISO-8859-10",
    "ISO-8859-13",  "ISO-8859-14",  "ISO-8859-15",  "ISO-8859-16",
    "ISO-8859-2",   "ISO-8859-3",   "ISO-8859-4",   "ISO-8859-5",
    "ISO-8859-6",   "ISO-8859-7",   "ISO-8859-8",   "ISO-8859-8-I",
    "KOI8-R",       "KOI8-U",       "macintosh",    "Shift_JIS",
    "UTF-16LE",     "UTF-8",        "windows-1250", "windows-1251",
    "windows-1252", "windows-1253", "windows-1254", "windows-1255",
    "windows-1256", "windows-1257", "windows-1258", "windows-874"};

}  // namespace

std::string GetCanonicalEncodingNameByAliasName(const std::string& alias_name) {
  for (auto* encoding_name : kCanonicalEncodingNames) {
    if (alias_name == encoding_name)
      return alias_name;
  }
  static const char* kStandards[3] = {"HTML", "MIME", "IANA"};
  for (auto* standard : kStandards) {
    UErrorCode error_code = U_ZERO_ERROR;
    const char* canonical_name =
        ucnv_getStandardName(alias_name.c_str(), standard, &error_code);
    if (U_SUCCESS(error_code) && canonical_name)
      return canonical_name;
  }
  return std::string();
}
}  // namespace base
