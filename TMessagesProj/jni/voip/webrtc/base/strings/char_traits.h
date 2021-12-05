// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_STRINGS_CHAR_TRAITS_H_
#define BASE_STRINGS_CHAR_TRAITS_H_

#include <stddef.h>

#include "base/compiler_specific.h"

namespace base {

// constexpr version of http://en.cppreference.com/w/cpp/string/char_traits.
// This currently just implements the bits needed to support a (mostly)
// constexpr StringPiece.
//
// TODO(dcheng): Once we switch to C++17, most methods will become constexpr and
// we can switch over to using the one in the standard library.
template <typename T>
struct CharTraits {
  // Performs a lexographical comparison of the first N characters of |s1| and
  // |s2|. Returns 0 if equal, -1 if |s1| is less than |s2|, and 1 if |s1| is
  // greater than |s2|.
  static constexpr int compare(const T* s1, const T* s2, size_t n) noexcept;

  // Returns the length of |s|, assuming null termination (and not including the
  // terminating null).
  static constexpr size_t length(const T* s) noexcept;
};

template <typename T>
constexpr int CharTraits<T>::compare(const T* s1,
                                     const T* s2,
                                     size_t n) noexcept {
  for (; n; --n, ++s1, ++s2) {
    if (*s1 < *s2)
      return -1;
    if (*s1 > *s2)
      return 1;
  }
  return 0;
}

template <typename T>
constexpr size_t CharTraits<T>::length(const T* s) noexcept {
  size_t i = 0;
  for (; *s; ++s)
    ++i;
  return i;
}

// char specialization of CharTraits that can use clang's constexpr instrinsics,
// where available.
template <>
struct CharTraits<char> {
  static constexpr int compare(const char* s1,
                               const char* s2,
                               size_t n) noexcept;
  static constexpr size_t length(const char* s) noexcept;
};

constexpr int CharTraits<char>::compare(const char* s1,
                                        const char* s2,
                                        size_t n) noexcept {
#if HAS_FEATURE(cxx_constexpr_string_builtins)
  return __builtin_memcmp(s1, s2, n);
#else
  for (; n; --n, ++s1, ++s2) {
    if (*s1 < *s2)
      return -1;
    if (*s1 > *s2)
      return 1;
  }
  return 0;
#endif
}

constexpr size_t CharTraits<char>::length(const char* s) noexcept {
#if defined(__clang__)
  return __builtin_strlen(s);
#else
  size_t i = 0;
  for (; *s; ++s)
    ++i;
  return i;
#endif
}

}  // namespace base

#endif  // BASE_STRINGS_CHAR_TRAITS_H_
