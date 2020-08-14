// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_TYPE_SAFETY_STRONG_ALIAS_H_
#define BASE_UTIL_TYPE_SAFETY_STRONG_ALIAS_H_

#include <ostream>
#include <utility>

namespace util {

// A type-safe alternative for a typedef or a 'using' directive.
//
// C++ currently does not support type-safe typedefs, despite multiple proposals
// (ex. http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2013/n3515.pdf). The
// next best thing is to try and emulate them in library code.
//
// The motivation is to disallow several classes of errors:
//
// using Orange = int;
// using Apple = int;
// Apple apple(2);
// Orange orange = apple;  // Orange should not be able to become an Apple.
// Orange x = orange + apple;  // Shouldn't add Oranges and Apples.
// if (orange > apple);  // Shouldn't compare Apples to Oranges.
// void foo(Orange);
// void foo(Apple);  // Redefinition.
// etc.
//
// StrongAlias may instead be used as follows:
//
// using Orange = StrongAlias<class OrangeTag, int>;
// using Apple = StrongAlias<class AppleTag, int>;
// Apple apple(2);
// Orange orange = apple;  // Does not compile.
// Orange other_orange = orange;  // Compiles, types match.
// Orange x = orange + apple;  // Does not compile.
// Orange y = Orange(orange.value() + apple.value());  // Compiles.
// if (orange > apple);  // Does not compile.
// if (orange > other_orange);  // Compiles.
// void foo(Orange);
// void foo(Apple);  // Compiles into separate overload.
//
// StrongAlias is a zero-cost abstraction, it's compiled away.
//
// TagType is an empty tag class (also called "phantom type") that only serves
// the type system to differentiate between different instantiations of the
// template.
// UnderlyingType may be almost any value type. Note that some methods of the
// StrongAlias may be unavailable (ie. produce elaborate compilation errors when
// used) if UnderlyingType doesn't support them.
//
// StrongAlias only directly exposes comparison operators (for convenient use in
// ordered containers) and a hash function (for unordered_map/set). It's
// impossible, without reflection, to expose all methods of the UnderlyingType
// in StrongAlias's interface. It's also potentially unwanted (ex. you don't
// want to be able to add two StrongAliases that represent socket handles).
// A getter is provided in case you need to access the UnderlyingType.
//
// See also
// - //styleguide/c++/blink-c++.md which provides recommendation and examples of
//   using StrongAlias<Tag, bool> instead of a bare bool.
// - util::IdType<...> which provides helpers for specializing
//   StrongAlias to be used as an id.
template <typename TagType, typename UnderlyingType>
class StrongAlias {
 public:
  StrongAlias() = default;
  explicit StrongAlias(const UnderlyingType& v) : value_(v) {}
  explicit StrongAlias(UnderlyingType&& v) : value_(std::move(v)) {}
  ~StrongAlias() = default;

  StrongAlias(const StrongAlias& other) = default;
  StrongAlias& operator=(const StrongAlias& other) = default;
  StrongAlias(StrongAlias&& other) = default;
  StrongAlias& operator=(StrongAlias&& other) = default;

  const UnderlyingType& value() const { return value_; }
  explicit operator UnderlyingType() const { return value_; }

  bool operator==(const StrongAlias& other) const {
    return value_ == other.value_;
  }
  bool operator!=(const StrongAlias& other) const {
    return value_ != other.value_;
  }
  bool operator<(const StrongAlias& other) const {
    return value_ < other.value_;
  }
  bool operator<=(const StrongAlias& other) const {
    return value_ <= other.value_;
  }
  bool operator>(const StrongAlias& other) const {
    return value_ > other.value_;
  }
  bool operator>=(const StrongAlias& other) const {
    return value_ >= other.value_;
  }

  // Hasher to use in std::unordered_map, std::unordered_set, etc.
  struct Hasher {
    using argument_type = StrongAlias;
    using result_type = std::size_t;
    result_type operator()(const argument_type& id) const {
      return std::hash<UnderlyingType>()(id.value());
    }
  };

 protected:
  UnderlyingType value_;
};

// Stream operator for convenience, streams the UnderlyingType.
template <typename TagType, typename UnderlyingType>
std::ostream& operator<<(std::ostream& stream,
                         const StrongAlias<TagType, UnderlyingType>& alias) {
  return stream << alias.value();
}

}  // namespace util

#endif  // BASE_UTIL_TYPE_SAFETY_STRONG_ALIAS_H_
