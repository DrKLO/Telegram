// Copyright (c) 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_I18N_UNICODESTRING_H_
#define BASE_I18N_UNICODESTRING_H_

#include "base/strings/string16.h"
#include "third_party/icu/source/common/unicode/unistr.h"
#include "third_party/icu/source/common/unicode/uvernum.h"

#if U_ICU_VERSION_MAJOR_NUM >= 59
#include "third_party/icu/source/common/unicode/char16ptr.h"
#endif

namespace base {
namespace i18n {

inline string16 UnicodeStringToString16(const icu::UnicodeString& unistr) {
#if U_ICU_VERSION_MAJOR_NUM >= 59
  return base::string16(icu::toUCharPtr(unistr.getBuffer()),
                        static_cast<size_t>(unistr.length()));
#else
  return base::string16(unistr.getBuffer(),
                        static_cast<size_t>(unistr.length()));
#endif
}

}  // namespace i18n
}  // namespace base

#endif  // BASE_UNICODESTRING_H_
