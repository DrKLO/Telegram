// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_STRINGS_SYS_STRING_CONVERSIONS_H_
#define BASE_STRINGS_SYS_STRING_CONVERSIONS_H_

// Provides system-dependent string type conversions for cases where it's
// necessary to not use ICU. Generally, you should not need this in Chrome,
// but it is used in some shared code. Dependencies should be minimal.

#include <stdint.h>

#include <string>

#include "base/base_export.h"
#include "base/strings/string16.h"
#include "base/strings/string_piece.h"
#include "build/build_config.h"

#if defined(OS_MACOSX)
#include <CoreFoundation/CoreFoundation.h>
#ifdef __OBJC__
@class NSString;
#else
class NSString;
#endif
#endif  // OS_MACOSX

namespace base {

// Converts between wide and UTF-8 representations of a string. On error, the
// result is system-dependent.
BASE_EXPORT std::string SysWideToUTF8(const std::wstring& wide)
    WARN_UNUSED_RESULT;
BASE_EXPORT std::wstring SysUTF8ToWide(StringPiece utf8) WARN_UNUSED_RESULT;

// Converts between wide and the system multi-byte representations of a string.
// DANGER: This will lose information and can change (on Windows, this can
// change between reboots).
BASE_EXPORT std::string SysWideToNativeMB(const std::wstring& wide)
    WARN_UNUSED_RESULT;
BASE_EXPORT std::wstring SysNativeMBToWide(StringPiece native_mb)
    WARN_UNUSED_RESULT;

// Windows-specific ------------------------------------------------------------

#if defined(OS_WIN)

// Converts between 8-bit and wide strings, using the given code page. The
// code page identifier is one accepted by the Windows function
// MultiByteToWideChar().
BASE_EXPORT std::wstring SysMultiByteToWide(StringPiece mb, uint32_t code_page)
    WARN_UNUSED_RESULT;
BASE_EXPORT std::string SysWideToMultiByte(const std::wstring& wide,
                                           uint32_t code_page)
    WARN_UNUSED_RESULT;

#endif  // defined(OS_WIN)

// Mac-specific ----------------------------------------------------------------

#if defined(OS_MACOSX)

// Converts between STL strings and CFStringRefs/NSStrings.

// Creates a string, and returns it with a refcount of 1. You are responsible
// for releasing it. Returns NULL on failure.
BASE_EXPORT CFStringRef SysUTF8ToCFStringRef(StringPiece utf8)
    WARN_UNUSED_RESULT;
BASE_EXPORT CFStringRef SysUTF16ToCFStringRef(StringPiece16 utf16)
    WARN_UNUSED_RESULT;

// Same, but returns an autoreleased NSString.
BASE_EXPORT NSString* SysUTF8ToNSString(StringPiece utf8) WARN_UNUSED_RESULT;
BASE_EXPORT NSString* SysUTF16ToNSString(StringPiece16 utf16)
    WARN_UNUSED_RESULT;

// Converts a CFStringRef to an STL string. Returns an empty string on failure.
BASE_EXPORT std::string SysCFStringRefToUTF8(CFStringRef ref)
    WARN_UNUSED_RESULT;
BASE_EXPORT string16 SysCFStringRefToUTF16(CFStringRef ref) WARN_UNUSED_RESULT;

// Same, but accepts NSString input. Converts nil NSString* to the appropriate
// string type of length 0.
BASE_EXPORT std::string SysNSStringToUTF8(NSString* ref) WARN_UNUSED_RESULT;
BASE_EXPORT string16 SysNSStringToUTF16(NSString* ref) WARN_UNUSED_RESULT;

#endif  // defined(OS_MACOSX)

}  // namespace base

#endif  // BASE_STRINGS_SYS_STRING_CONVERSIONS_H_
