// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/string_util.h"

namespace base {

#define WHITESPACE_ASCII_NO_CR_LF      \
  0x09,     /* CHARACTER TABULATION */ \
      0x0B, /* LINE TABULATION */      \
      0x0C, /* FORM FEED (FF) */       \
      0x20  /* SPACE */

#define WHITESPACE_ASCII                                                  \
  WHITESPACE_ASCII_NO_CR_LF, /* Comment to make clang-format linebreak */ \
      0x0A,                  /* LINE FEED (LF) */                         \
      0x0D                   /* CARRIAGE RETURN (CR) */

#define WHITESPACE_UNICODE_NON_ASCII          \
  0x0085,     /* NEXT LINE (NEL) */           \
      0x00A0, /* NO-BREAK SPACE */            \
      0x1680, /* OGHAM SPACE MARK */          \
      0x2000, /* EN QUAD */                   \
      0x2001, /* EM QUAD */                   \
      0x2002, /* EN SPACE */                  \
      0x2003, /* EM SPACE */                  \
      0x2004, /* THREE-PER-EM SPACE */        \
      0x2005, /* FOUR-PER-EM SPACE */         \
      0x2006, /* SIX-PER-EM SPACE */          \
      0x2007, /* FIGURE SPACE */              \
      0x2008, /* PUNCTUATION SPACE */         \
      0x2009, /* THIN SPACE */                \
      0x200A, /* HAIR SPACE */                \
      0x2028, /* LINE SEPARATOR */            \
      0x2029, /* PARAGRAPH SEPARATOR */       \
      0x202F, /* NARROW NO-BREAK SPACE */     \
      0x205F, /* MEDIUM MATHEMATICAL SPACE */ \
      0x3000  /* IDEOGRAPHIC SPACE */

#define WHITESPACE_UNICODE_NO_CR_LF \
  WHITESPACE_ASCII_NO_CR_LF, WHITESPACE_UNICODE_NON_ASCII

#define WHITESPACE_UNICODE WHITESPACE_ASCII, WHITESPACE_UNICODE_NON_ASCII

const wchar_t kWhitespaceWide[] = {WHITESPACE_UNICODE, 0};
const char16 kWhitespaceUTF16[] = {WHITESPACE_UNICODE, 0};
const char16 kWhitespaceNoCrLfUTF16[] = {WHITESPACE_UNICODE_NO_CR_LF, 0};
const char kWhitespaceASCII[] = {WHITESPACE_ASCII, 0};
const char16 kWhitespaceASCIIAs16[] = {WHITESPACE_ASCII, 0};

const char kUtf8ByteOrderMark[] = "\xEF\xBB\xBF";

}  // namespace base
