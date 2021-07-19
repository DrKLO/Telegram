// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_DEBUG_ALIAS_H_
#define BASE_DEBUG_ALIAS_H_

#include "base/base_export.h"
#include "base/stl_util.h"
#include "base/strings/string_util.h"

namespace base {
namespace debug {

// Make the optimizer think that var is aliased. This is to prevent it from
// optimizing out local variables that would not otherwise be live at the point
// of a potential crash.
// base::debug::Alias should only be used for local variables, not globals,
// object members, or function return values - these must be copied to locals if
// you want to ensure they are recorded in crash dumps.
// Note that if the local variable is a pointer then its value will be retained
// but the memory that it points to will probably not be saved in the crash
// dump - by default only stack memory is saved. Therefore the aliasing
// technique is usually only worthwhile with non-pointer variables. If you have
// a pointer to an object and you want to retain the object's state you need to
// copy the object or its fields to local variables. Example usage:
//   int last_error = err_;
//   base::debug::Alias(&last_error);
//   DEBUG_ALIAS_FOR_CSTR(name_copy, p->name, 16);
//   CHECK(false);
void BASE_EXPORT Alias(const void* var);

}  // namespace debug
}  // namespace base

// Convenience macro that copies the null-terminated string from |c_str| into a
// stack-allocated char array named |var_name| that holds up to |char_count|
// characters and should be preserved in memory dumps.
#define DEBUG_ALIAS_FOR_CSTR(var_name, c_str, char_count)   \
  char var_name[char_count];                                \
  ::base::strlcpy(var_name, (c_str), base::size(var_name)); \
  ::base::debug::Alias(var_name);

#endif  // BASE_DEBUG_ALIAS_H_
