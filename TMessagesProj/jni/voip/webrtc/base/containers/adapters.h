// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_ADAPTERS_H_
#define BASE_CONTAINERS_ADAPTERS_H_

#include <stddef.h>

#include <iterator>
#include <utility>

#include "base/macros.h"

namespace base {

namespace internal {

// Internal adapter class for implementing base::Reversed.
template <typename T>
class ReversedAdapter {
 public:
  using Iterator = decltype(std::rbegin(std::declval<T&>()));

  explicit ReversedAdapter(T& t) : t_(t) {}
  ReversedAdapter(const ReversedAdapter& ra) : t_(ra.t_) {}

  Iterator begin() const { return std::rbegin(t_); }
  Iterator end() const { return std::rend(t_); }

 private:
  T& t_;

  DISALLOW_ASSIGN(ReversedAdapter);
};

}  // namespace internal

// Reversed returns a container adapter usable in a range-based "for" statement
// for iterating a reversible container in reverse order.
//
// Example:
//
//   std::vector<int> v = ...;
//   for (int i : base::Reversed(v)) {
//     // iterates through v from back to front
//   }
template <typename T>
internal::ReversedAdapter<T> Reversed(T& t) {
  return internal::ReversedAdapter<T>(t);
}

}  // namespace base

#endif  // BASE_CONTAINERS_ADAPTERS_H_
