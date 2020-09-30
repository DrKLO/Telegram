// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_I18N_STRING_COMPARE_H_
#define BASE_I18N_STRING_COMPARE_H_

#include <algorithm>
#include <string>
#include <vector>

#include "base/i18n/base_i18n_export.h"
#include "base/strings/string_piece.h"
#include "third_party/icu/source/i18n/unicode/coll.h"

namespace base {
namespace i18n {

// Compares the two strings using the specified collator.
BASE_I18N_EXPORT UCollationResult
CompareString16WithCollator(const icu::Collator& collator,
                            const StringPiece16 lhs,
                            const StringPiece16 rhs);

}  // namespace i18n
}  // namespace base

#endif  // BASE_I18N_STRING_COMPARE_H_
