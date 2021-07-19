// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Forward declaration of StringPiece types from base/strings/string_piece.h

#ifndef BASE_STRINGS_STRING_PIECE_FORWARD_H_
#define BASE_STRINGS_STRING_PIECE_FORWARD_H_

#include <string>

#include "base/strings/string16.h"

namespace base {

template <typename STRING_TYPE>
class BasicStringPiece;
typedef BasicStringPiece<std::string> StringPiece;
typedef BasicStringPiece<string16> StringPiece16;
typedef BasicStringPiece<std::wstring> WStringPiece;

}  // namespace base

#endif  // BASE_STRINGS_STRING_PIECE_FORWARD_H_
