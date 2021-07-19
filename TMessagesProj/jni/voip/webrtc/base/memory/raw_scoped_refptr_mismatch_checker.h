// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MEMORY_RAW_SCOPED_REFPTR_MISMATCH_CHECKER_H_
#define BASE_MEMORY_RAW_SCOPED_REFPTR_MISMATCH_CHECKER_H_

#include <type_traits>

#include "base/template_util.h"

// It is dangerous to post a task with a T* argument where T is a subtype of
// RefCounted(Base|ThreadSafeBase), since by the time the parameter is used, the
// object may already have been deleted since it was not held with a
// scoped_refptr. Example: http://crbug.com/27191
// The following set of traits are designed to generate a compile error
// whenever this antipattern is attempted.

namespace base {

// This is a base internal implementation file used by task.h and callback.h.
// Not for public consumption, so we wrap it in namespace internal.
namespace internal {

template <typename T, typename = void>
struct IsRefCountedType : std::false_type {};

template <typename T>
struct IsRefCountedType<T,
                        void_t<decltype(std::declval<T*>()->AddRef()),
                               decltype(std::declval<T*>()->Release())>>
    : std::true_type {};

// Human readable translation: you needed to be a scoped_refptr if you are a raw
// pointer type and are convertible to a RefCounted(Base|ThreadSafeBase) type.
template <typename T>
struct NeedsScopedRefptrButGetsRawPtr
    : conjunction<std::is_pointer<T>,
                  IsRefCountedType<std::remove_pointer_t<T>>> {
  static_assert(!std::is_reference<T>::value,
                "NeedsScopedRefptrButGetsRawPtr requires non-reference type.");
};

}  // namespace internal

}  // namespace base

#endif  // BASE_MEMORY_RAW_SCOPED_REFPTR_MISMATCH_CHECKER_H_
