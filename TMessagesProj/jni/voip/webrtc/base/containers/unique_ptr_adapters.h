// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_UNIQUE_PTR_ADAPTERS_H_
#define BASE_CONTAINERS_UNIQUE_PTR_ADAPTERS_H_

#include <memory>

namespace base {

// This transparent comparator allows to lookup by raw pointer in
// a container of unique pointers. This functionality is based on C++14
// extensions to std::set/std::map interface, and can also be used
// with base::flat_set/base::flat_map.
//
// Example usage:
//   Foo* foo = ...
//   std::set<std::unique_ptr<Foo>, base::UniquePtrComparator> set;
//   set.insert(std::unique_ptr<Foo>(foo));
//   ...
//   auto it = set.find(foo);
//   EXPECT_EQ(foo, it->get());
//
// You can find more information about transparent comparisons here:
// http://en.cppreference.com/w/cpp/utility/functional/less_void
struct UniquePtrComparator {
  using is_transparent = int;

  template <typename T, class Deleter = std::default_delete<T>>
  bool operator()(const std::unique_ptr<T, Deleter>& lhs,
                  const std::unique_ptr<T, Deleter>& rhs) const {
    return lhs < rhs;
  }

  template <typename T, class Deleter = std::default_delete<T>>
  bool operator()(const T* lhs, const std::unique_ptr<T, Deleter>& rhs) const {
    return lhs < rhs.get();
  }

  template <typename T, class Deleter = std::default_delete<T>>
  bool operator()(const std::unique_ptr<T, Deleter>& lhs, const T* rhs) const {
    return lhs.get() < rhs;
  }
};

// UniquePtrMatcher is useful for finding an element in a container of
// unique_ptrs when you have the raw pointer.
//
// Example usage:
//   std::vector<std::unique_ptr<Foo>> vector;
//   Foo* element = ...
//   auto iter = std::find_if(vector.begin(), vector.end(),
//                            MatchesUniquePtr(element));
//
// Example of erasing from container:
//   EraseIf(v, MatchesUniquePtr(element));
//
template <class T, class Deleter = std::default_delete<T>>
struct UniquePtrMatcher {
  explicit UniquePtrMatcher(T* t) : t_(t) {}

  bool operator()(const std::unique_ptr<T, Deleter>& o) {
    return o.get() == t_;
  }

 private:
  T* const t_;
};

template <class T, class Deleter = std::default_delete<T>>
UniquePtrMatcher<T, Deleter> MatchesUniquePtr(T* t) {
  return UniquePtrMatcher<T, Deleter>(t);
}

}  // namespace base

#endif  // BASE_CONTAINERS_UNIQUE_PTR_ADAPTERS_H_
