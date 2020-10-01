// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_STRINGS_STRINGPRINTF_H_
#define BASE_STRINGS_STRINGPRINTF_H_

#include <stdarg.h>   // va_list

#include <string>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "build/build_config.h"

namespace base {

// Return a C++ string given printf-like input.
BASE_EXPORT std::string StringPrintf(const char* format, ...)
    PRINTF_FORMAT(1, 2) WARN_UNUSED_RESULT;
#if defined(OS_WIN)
// Note: Unfortunately compile time checking of the format string for UTF-16
// strings is not supported by any compiler, thus these functions should be used
// carefully and sparingly. Also applies to SStringPrintf and StringAppendV
// below.
BASE_EXPORT std::wstring StringPrintf(const wchar_t* format, ...)
    WPRINTF_FORMAT(1, 2) WARN_UNUSED_RESULT;
BASE_EXPORT std::u16string StringPrintf(const char16_t* format, ...)
    WPRINTF_FORMAT(1, 2) WARN_UNUSED_RESULT;
#endif

// Return a C++ string given vprintf-like input.
BASE_EXPORT std::string StringPrintV(const char* format, va_list ap)
    PRINTF_FORMAT(1, 0) WARN_UNUSED_RESULT;

// Store result into a supplied string and return it.
BASE_EXPORT const std::string& SStringPrintf(std::string* dst,
                                             const char* format,
                                             ...) PRINTF_FORMAT(2, 3);
#if defined(OS_WIN)
BASE_EXPORT const std::wstring& SStringPrintf(std::wstring* dst,
                                              const wchar_t* format,
                                              ...) WPRINTF_FORMAT(2, 3);
BASE_EXPORT const std::u16string& SStringPrintf(std::u16string* dst,
                                                const char16_t* format,
                                                ...) WPRINTF_FORMAT(2, 3);
#endif

// Append result to a supplied string.
BASE_EXPORT void StringAppendF(std::string* dst, const char* format, ...)
    PRINTF_FORMAT(2, 3);
#if defined(OS_WIN)
BASE_EXPORT void StringAppendF(std::wstring* dst, const wchar_t* format, ...)
    WPRINTF_FORMAT(2, 3);
BASE_EXPORT void StringAppendF(std::u16string* dst, const char16_t* format, ...)
    WPRINTF_FORMAT(2, 3);
#endif

// Lower-level routine that takes a va_list and appends to a specified
// string.  All other routines are just convenience wrappers around it.
BASE_EXPORT void StringAppendV(std::string* dst, const char* format, va_list ap)
    PRINTF_FORMAT(2, 0);
#if defined(OS_WIN)
BASE_EXPORT void StringAppendV(std::wstring* dst,
                               const wchar_t* format,
                               va_list ap) WPRINTF_FORMAT(2, 0);
BASE_EXPORT void StringAppendV(std::u16string* dst,
                               const char16_t* format,
                               va_list ap) WPRINTF_FORMAT(2, 0);
#endif

}  // namespace base

#endif  // BASE_STRINGS_STRINGPRINTF_H_
