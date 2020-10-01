// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_TYPE_SAFETY_ID_TYPE_H_
#define BASE_UTIL_TYPE_SAFETY_ID_TYPE_H_

#include <cstdint>

#include "base/util/type_safety/strong_alias.h"

namespace util {

// A specialization of StrongAlias for integer-based identifiers.
//
// IdType32<>, IdType64<>, etc. wrap an integer id in a custom, type-safe type.
//
// IdType32<Foo> is an alternative to int, for a class Foo with methods like:
//
//    int GetId() { return id_; };
//    static Foo* FromId(int id) { return g_all_foos_by_id[id]; }
//
// Such methods are a standard means of safely referring to objects across
// thread and process boundaries.  But if a nearby class Bar also represents
// its IDs as a bare int, horrific mixups are possible -- one example, of many,
// is http://crrev.com/365437.  IdType<> offers compile-time protection against
// such mishaps, since IdType32<Foo> is incompatible with IdType32<Bar>, even
// though both just compile down to an int32_t.
//
// Templates in this file:
//   IdType32<T> / IdTypeU32<T>: Signed / unsigned 32-bit IDs
//   IdType64<T> / IdTypeU64<T>: Signed / unsigned 64-bit IDs
//   IdType<>: For when you need a different underlying type or
//             a default/null value other than zero.
//
// IdType32<Foo> behaves just like an int32_t in the following aspects:
// - it can be used as a key in std::map and/or std::unordered_map;
// - it can be used as an argument to DCHECK_EQ or streamed to LOG(ERROR);
// - it has the same memory footprint and runtime overhead as int32_t;
// - it can be copied by memcpy.
// - it can be used in IPC messages.
//
// IdType32<Foo> has the following differences from a bare int32_t:
// - it forces coercions to go through the explicit constructor and value()
//   getter;
// - it restricts the set of available operations (i.e. no multiplication);
// - it default-constructs to a null value and allows checking against the null
//   value via is_null method.
template <typename TypeMarker, typename WrappedType, WrappedType kInvalidValue>
class IdType : public StrongAlias<TypeMarker, WrappedType> {
 public:
  static_assert(kInvalidValue <= 0,
                "The invalid value should be negative or equal to zero to "
                "avoid overflow issues.");

  using StrongAlias<TypeMarker, WrappedType>::StrongAlias;

  // This class can be used to generate unique IdTypes. It keeps an internal
  // counter that is continually increased by one every time an ID is generated.
  class Generator {
   public:
    Generator() = default;
    ~Generator() = default;

    // Generates the next unique ID.
    IdType GenerateNextId() { return FromUnsafeValue(next_id_++); }

    // Non-copyable.
    Generator(const Generator&) = delete;
    Generator& operator=(const Generator&) = delete;

   private:
    WrappedType next_id_ = kInvalidValue + 1;
  };

  // Default-construct in the null state.
  IdType() : StrongAlias<TypeMarker, WrappedType>::StrongAlias(kInvalidValue) {}

  bool is_null() const { return this->value() == kInvalidValue; }

  // TODO(mpawlowski) Replace these with constructor/value() getter. The
  // conversions are safe as long as they're explicit (which is taken care of by
  // StrongAlias).
  static IdType FromUnsafeValue(WrappedType value) { return IdType(value); }
  WrappedType GetUnsafeValue() const { return this->value(); }
};

// Type aliases for convenience:
template <typename TypeMarker>
using IdType32 = IdType<TypeMarker, std::int32_t, 0>;
template <typename TypeMarker>
using IdTypeU32 = IdType<TypeMarker, std::uint32_t, 0>;
template <typename TypeMarker>
using IdType64 = IdType<TypeMarker, std::int64_t, 0>;
template <typename TypeMarker>
using IdTypeU64 = IdType<TypeMarker, std::uint64_t, 0>;
}  // namespace util

#endif  // BASE_UTIL_TYPE_SAFETY_ID_TYPE_H_
